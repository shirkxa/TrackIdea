package pl.trackidea.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.trackidea.domain.*;
import pl.trackidea.radar.SourceImporter;
import pl.trackidea.radar.SourceRecord;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.RadarRecordRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Radar: cykliczne wyszukiwanie istniejących innowacji w otwartych źródłach.
 * Dla każdego rekordu: pobranie → wykrycie duplikatu → analiza (model AI, a bez klucza reguły) → karta „Wykryta”.
 * Rekord, który według analizy AI nie jest innowacją, trafia do odrzuconych – analityk może go przywrócić.
 * Rekord już znany jest odświeżany: fakty ze źródła i zmiana etapu wdrożenia.
 */
@Service
public class RadarService {

    private static final Logger log = LoggerFactory.getLogger(RadarService.class);

    /** Powyżej tego podobieństwa rekord uznajemy za duplikat istniejącej innowacji. */
    static final int DUPLICATE_THRESHOLD = 75;

    public record ScanResult(int scanned, int created, int duplicates, int updated, int skipped, int rejected,
                             List<RadarRecord> records, List<String> errors) {
    }

    /** Stan jednego źródła pokazywany w panelu PCI. */
    public record SourceStatus(String name, String description, String homepage, boolean live, boolean active,
                               Instant lastRunAt, int lastFetched, int lastCreated, int lastDuplicates,
                               int lastUpdated, long totalRecords, String lastError, String preparing) {
    }

    private final List<SourceImporter> importers;
    private final RadarRecordRepository records;
    private final InnovationRepository innovations;
    private final InstitutionDirectory institutions;
    private final SimilarityService similarity;
    private final AssistantService assistant;
    private final RecordClassifier classifier;
    private final InnovationService innovationService;
    private final AssessmentService assessmentService;
    private final TransactionTemplate tx;
    private final boolean liveEnabled;
    private final Duration refreshInterval;
    private final Map<String, SourceStatus> status = new ConcurrentHashMap<>();
    private volatile Instant lastScanAt;

    public RadarService(List<SourceImporter> importers, RadarRecordRepository records, InnovationRepository innovations,
                        InstitutionDirectory institutions, SimilarityService similarity, AssistantService assistant,
                        RecordClassifier classifier,
                        InnovationService innovationService, AssessmentService assessmentService,
                        TransactionTemplate tx,
                        @Value("${trackidea.radar.live:true}") boolean liveEnabled,
                        @Value("${trackidea.radar.refresh-interval:PT6H}") Duration refreshInterval) {
        this.importers = importers;
        this.records = records;
        this.innovations = innovations;
        this.institutions = institutions;
        this.similarity = similarity;
        this.assistant = assistant;
        this.classifier = classifier;
        this.innovationService = innovationService;
        this.assessmentService = assessmentService;
        this.tx = tx;
        this.liveEnabled = liveEnabled;
        this.refreshInterval = refreshInterval;
    }

    /** Czy nowe rekordy analizuje model AI (inaczej reguły offline). */
    public boolean usesAi() {
        return classifier.enabled();
    }

    public boolean isLive() {
        return liveEnabled;
    }

    public Instant nextScanAt() {
        return lastScanAt == null ? null : lastScanAt.plus(refreshInterval);
    }

    public Instant lastScanAt() {
        return lastScanAt;
    }

    public List<SourceStatus> sources() {
        List<SourceStatus> out = new ArrayList<>();
        for (SourceImporter imp : importers) {
            SourceStatus s = status.get(imp.name());
            long total = records.countBySource(imp.name());
            out.add(s == null
                    ? new SourceStatus(imp.name(), imp.description(), imp.homepage(), imp.live(), isActive(imp),
                    null, 0, 0, 0, 0, total, null, null)
                    : new SourceStatus(s.name(), s.description(), s.homepage(), s.live(), s.active(), s.lastRunAt(),
                    s.lastFetched(), s.lastCreated(), s.lastDuplicates(), s.lastUpdated(), total, s.lastError(),
                    s.preparing()));
        }
        return out;
    }

    private boolean isActive(SourceImporter imp) {
        return imp.live() == liveEnabled;
    }

    /** Dane startowe: bez internetu Radar od razu wczytuje próbki; w trybie online robi to harmonogram. */
    public void scanInitial() {
        if (!liveEnabled) {
            scan();
        }
    }

    /** Skan wszystkich aktywnych źródeł (przycisk w panelu PCI i harmonogram). */
    public synchronized ScanResult scan() {
        int scanned = 0, created = 0, duplicates = 0, updated = 0, skipped = 0, rejected = 0;
        classifier.newScan();
        List<RadarRecord> touched = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (SourceImporter imp : importers) {
            if (!isActive(imp)) {
                continue;
            }
            List<SourceRecord> incoming;
            try {
                incoming = imp.fetch();
            } catch (pl.trackidea.radar.SourceNotReady e) {
                log.info("Radar: {} – {}", imp.name(), e.getMessage());
                status.put(imp.name(), new SourceStatus(imp.name(), imp.description(), imp.homepage(), imp.live(), true,
                        Instant.now(), 0, 0, 0, 0, 0, null, e.getMessage()));
                continue;
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("Radar: źródło {} niedostępne: {}", imp.name(), msg);
                errors.add(imp.name() + ": " + msg);
                status.put(imp.name(), new SourceStatus(imp.name(), imp.description(), imp.homepage(), imp.live(), true,
                        Instant.now(), 0, 0, 0, 0, 0, "Źródło niedostępne: " + msg, null));
                continue;
            }
            int[] counts = new int[5]; // created, duplicates, updated, skipped, rejected
            for (SourceRecord r : incoming) {
                try {
                    RadarRecord rec = tx.execute(t -> ingest(r, counts, false));
                    if (rec != null) touched.add(rec);
                } catch (RuntimeException e) {
                    log.warn("Radar: pominięto rekord {} {}: {}", r.source(), r.ref(), e.getMessage());
                    counts[3]++;
                }
            }
            scanned += incoming.size();
            created += counts[0];
            duplicates += counts[1];
            updated += counts[2];
            skipped += counts[3];
            rejected += counts[4];
            status.put(imp.name(), new SourceStatus(imp.name(), imp.description(), imp.homepage(), imp.live(), true,
                    Instant.now(), incoming.size(), counts[0], counts[1], counts[2], 0, null, null));
            log.info("Radar: {} – pobrano {}, nowe {}, duplikaty {}, zaktualizowane {}, odrzucone automatycznie {}",
                    imp.name(), incoming.size(), counts[0], counts[1], counts[2], counts[4]);
        }
        lastScanAt = Instant.now();
        return new ScanResult(scanned, created, duplicates, updated, skipped, rejected, touched, errors);
    }

    /**
     * Rekord wskazany przez analityka (strona lub dokument): ta sama ścieżka co import,
     * ale analiza AI nie podlega limitowi na skan, a rekord nie jest odrzucany automatycznie.
     */
    public synchronized RadarRecord analyze(SourceRecord r) {
        int[] counts = new int[5];
        RadarRecord rec = tx.execute(t -> ingest(r, counts, true));
        return rec != null ? rec : records.findFirstBySourceAndRef(r.source(), r.ref()).orElseThrow();
    }

    /** Jeden rekord: nowy → karta, znany → odświeżenie, bardzo podobny do istniejącej innowacji → duplikat. */
    private RadarRecord ingest(SourceRecord r, int[] counts, boolean manual) {
        if (blank(r.title()) && blank(r.abstractText())) {
            throw new IllegalArgumentException("rekord bez tytułu i opisu");
        }
        // etap podany przez importer wchodzi do skrótu: zakończenie projektu w źródle to też zmiana rekordu
        int hash = Objects.hash(r.title(), r.abstractText(), r.facts(), r.maturityHint());
        Optional<RadarRecord> known = records.findFirstBySourceAndRef(r.source(), r.ref());
        if (known.isPresent()) {
            RadarRecord rec = known.get();
            rec.setLastSeenAt(Instant.now());
            if (rec.getContentHash() != hash && rec.getInnovation() != null) {
                refresh(rec.getInnovation(), r);
                rec.setContentHash(hash);
                counts[2]++;
                return rec;
            }
            counts[3]++;
            return null;
        }
        String text = r.title() + " " + r.title() + " " + r.abstractText() + " " + String.join(" ", r.keywords());
        List<SimilarItem> similar = similarity.similarInnovations(text, null, 1);
        RadarRecord rec = new RadarRecord();
        rec.setSource(r.source());
        rec.setRef(r.ref());
        rec.setTitle(r.title());
        rec.setInstitutionName(r.institution());
        rec.setContentHash(hash);
        if (!similar.isEmpty() && similar.getFirst().similarityPercent() >= DUPLICATE_THRESHOLD) {
            rec.setOutcome(RadarRecord.Outcome.DUPLIKAT);
            rec.setDuplicateOf(innovations.getReferenceById(similar.getFirst().id()));
            rec.setDuplicateSimilarity(similar.getFirst().similarityPercent());
            counts[1]++;
        } else {
            Innovation inn = createDetected(r, manual);
            rec.setInnovation(inn);
            rec.setTitle(inn.getTitle());
            if (inn.isAutoRejected()) {
                rec.setOutcome(RadarRecord.Outcome.ODRZUCONA_AUTOMATYCZNIE);
                counts[4]++;
            } else {
                rec.setOutcome(RadarRecord.Outcome.NOWA_INNOWACJA);
                counts[0]++;
            }
        }
        return records.save(rec);
    }

    /**
     * Zmiana w źródle: fakty są zawsze odświeżane, zmiana etapu wdrożenia trafia na kartę i oś czasu.
     * Treści karty poprawionej lub zweryfikowanej przez analityka skan nie nadpisuje.
     */
    private void refresh(Innovation inn, SourceRecord r) {
        List<String> kept = inn.getSourceFacts().stream().filter(f -> f.startsWith(ORIGINAL_TITLE)).toList();
        inn.getSourceFacts().clear();
        inn.getSourceFacts().addAll(r.facts());
        inn.getSourceFacts().addAll(kept);
        boolean untouched = !inn.isEditedByAnalyst() && inn.getStatus() == InnovationStatus.WYKRYTA;
        if (untouched && !ENGINE_AI.equals(inn.getAnalysisEngine()) && !blank(r.title())) {
            inn.setTitle(r.title());
            inn.setSummary(assistant.heuristic(r.title(), r.abstractText()).summary());
        }
        String stage = "";
        Maturity now = r.maturityHint();
        boolean estimatedStage = inn.getMaturity() == null || inn.getEstimated().contains("maturity");
        if (now != null && now != inn.getMaturity() && estimatedStage) {
            stage = " Etap wdrożenia według źródła: " + (inn.getMaturity() == null ? "nieustalony" : inn.getMaturity().getLabel())
                    + " → " + now.getLabel() + ".";
            inn.setMaturity(now);
            inn.getEstimated().add("maturity");
            if (inn.getTrl() != null && inn.getEstimated().contains("trl")) {
                inn.setTrl(Trl.estimate(now, TextVectorizer.normalize(r.title() + " " + r.abstractText())));
            }
        }
        inn.touch();
        innovationService.log(inn, EventType.EDYCJA, "Zaktualizowano na podstawie zmian w źródle " + r.source() + "." + stage, null);
        assessmentService.autoAssess(inn);
    }

    static final String ENGINE_AI = "CLAUDE";
    static final String ENGINE_RULES = "REGUŁY";
    private static final String ORIGINAL_TITLE = "Tytuł w źródle: ";

    private Innovation createDetected(SourceRecord r, boolean manual) {
        RecordAnalysis ai = (manual ? classifier.classifyNow(r) : classifier.classify(r)).orElse(null);
        AssistantService.Draft rules = assistant.heuristic(r.title(), r.abstractText());
        Innovation inn = new Innovation();
        inn.setAnalysisEngine(ai != null ? ENGINE_AI : ENGINE_RULES);

        String sourceTitle = blank(r.title()) ? rules.title() : r.title();
        if (ai != null) {
            // model czyta treść rekordu, więc jego rozpoznanie ma pierwszeństwo przed podpowiedzią z zapytania importera
            inn.setTitle(first(ai.title(), sourceTitle));
            inn.setSummary(first(ai.summary(), rules.summary()));
            inn.setProblem(ai.problem());
            inn.setSolution(first(ai.solution(), r.abstractText()));
            inn.setResults(ai.results());
            inn.setRequirements(ai.requirements());
            inn.setType(first(ai.type(), r.typeHint(), rules.type()));
            inn.setSector(first(ai.sector(), r.sectorHint()));
            inn.setMaturity(first(ai.maturity(), r.maturityHint()));
            inn.setNoveltyEstimate(ai.novelty());
            inn.setNoveltyNote(ai.noveltyReason());
            ai.tags().stream().filter(t -> !blank(t)).limit(6).forEach(t -> inn.getTags().add(t.toLowerCase()));
        } else {
            inn.setTitle(sourceTitle);
            inn.setSummary(rules.summary());
            inn.setProblem(blank(rules.problem()) ? null : rules.problem());
            inn.setSolution(blank(rules.solution()) ? r.abstractText() : rules.solution());
            inn.setResults(blank(rules.results()) ? null : rules.results());
            inn.setRequirements(blank(rules.requirements()) ? null : rules.requirements());
            inn.setType(first(r.typeHint(), rules.type()));
            inn.setSector(first(r.sectorHint(), plausible(rules.sector(), r.institutionType())));
            inn.setMaturity(first(r.maturityHint(), rules.maturity()));
            if (manual) {
                rules.tags().stream().limit(6).forEach(t -> inn.getTags().add(t.toLowerCase()));
            }
        }
        Area area = ai != null && ai.area() != null ? ai.area()
                : r.areaHint() != null ? r.areaHint() : Area.derive(inn.getType(), inn.getSector(), r.institutionType());
        inn.setArea(area);
        // TRL ma sens tylko dla rozwiązań technologicznych i tylko wtedy, gdy znamy etap
        if (area == Area.TECHNOLOGICZNY) {
            inn.setTrl(ai != null && ai.trl() != null ? ai.trl()
                    : inn.getMaturity() == null ? null
                    : Trl.estimate(inn.getMaturity(), TextVectorizer.normalize(r.title() + " " + r.abstractText())));
        }
        // opis i klasyfikacja pochodzą z automatu – to szacunek, dopóki analityk ich nie potwierdzi
        inn.getEstimated().addAll(List.of("area", "type", "content"));
        if (inn.getSector() != null) inn.getEstimated().add("sector");
        if (inn.getMaturity() != null) inn.getEstimated().add("maturity");
        if (inn.getTrl() != null) inn.getEstimated().add("trl");

        inn.setSourceYear(r.year());
        inn.getSourceFacts().addAll(r.facts());
        if (!blank(r.title()) && !r.title().equals(inn.getTitle())) {
            inn.getSourceFacts().add(ORIGINAL_TITLE + r.title());
        }
        r.keywords().stream().filter(k -> !blank(k)).forEach(k -> inn.getTags().add(k.toLowerCase()));
        if (r.url() != null) {
            inn.getLinks().add(r.url());
        }
        inn.setInstitution(institutions.resolve(r.institution(), r.institutionType(), r.city(), r.county(), r.region()));
        inn.setSource(Source.RADAR);
        inn.setSourceName(r.source());
        inn.setSourceRef(r.ref());
        inn.setSourceAuthors(r.authors());
        inn.setStatus(InnovationStatus.WYKRYTA);
        innovations.save(inn);

        String engine = ai != null ? "model AI" : "reguły offline";
        String found = "Wykryto w źródle " + r.source() + " (" + r.ref() + (r.year() != null ? ", " + r.year() : "")
                + "). Opis i klasyfikację przygotował automat (" + engine + ") – to szacunek.";
        boolean reject = ai != null && !manual && ai.verdict() == RecordAnalysis.Verdict.NIE_INNOWACJA;
        if (reject) {
            innovationService.log(inn, EventType.WYKRYTO, found, null);
            inn.setAutoRejected(true);
            innovationService.changeStatus(inn, InnovationStatus.ODRZUCONA, "automatycznie – według analizy AI to nie jest innowacja"
                    + (ai.verdictReason() != null ? ": " + ai.verdictReason() : ".") + " Analityk PCI może przywrócić rekord.", null);
            return inn;
        }
        String doubt = ai == null || ai.verdict() == RecordAnalysis.Verdict.INNOWACJA ? ""
                : " Analiza AI: " + (ai.verdict() == RecordAnalysis.Verdict.NIEPEWNE ? "niepewne, czy to innowacja"
                : "to może nie być innowacja") + (ai.verdictReason() != null ? " – " + ai.verdictReason() : "") + ".";
        String auto = assessmentService.autoAssess(inn)
                .map(a -> " Ocena automatyczna: " + Math.round(a.proposedTotal()) + "/100 (szacunek).").orElse("");
        innovationService.log(inn, EventType.WYKRYTO, found + doubt + auto + " Czeka na weryfikację analityka PCI.", null);
        return inn;
    }

    /** Reguły łapią nazwy gmin i szkół w opisach projektów firm – firma nie działa w sektorze „Administracja” ani „Edukacja”. */
    private static Sector plausible(Sector fromRules, InstitutionType institution) {
        boolean publicSector = fromRules == Sector.ADMINISTRACJA || fromRules == Sector.EDUKACJA;
        return publicSector && institution == InstitutionType.FIRMA ? null : fromRules;
    }

    @SafeVarargs
    private static <T> T first(T... values) {
        for (T v : values) {
            if (v != null && !(v instanceof String s && s.isBlank())) {
                return v;
            }
        }
        return null;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
