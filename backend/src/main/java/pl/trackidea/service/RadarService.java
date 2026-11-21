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
import pl.trackidea.repo.InstitutionRepository;
import pl.trackidea.repo.RadarRecordRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Radar: cykliczne wyszukiwanie istniejących innowacji w otwartych źródłach.
 * Dla każdego rekordu: pobranie → rozpoznanie obszaru i sektora → wykrycie duplikatu → karta „Wykryta”.
 * Rekord już znany jest odświeżany (aktualność, zmiany w źródle).
 */
@Service
public class RadarService {

    private static final Logger log = LoggerFactory.getLogger(RadarService.class);

    /** Powyżej tego podobieństwa rekord uznajemy za duplikat istniejącej innowacji. */
    static final int DUPLICATE_THRESHOLD = 75;

    public record ScanResult(int scanned, int created, int duplicates, int updated, int skipped,
                             List<RadarRecord> records, List<String> errors) {
    }

    /** Stan jednego źródła pokazywany w panelu PCI. */
    public record SourceStatus(String name, String description, String homepage, boolean live, boolean active,
                               Instant lastRunAt, int lastFetched, int lastCreated, int lastDuplicates,
                               int lastUpdated, long totalRecords, String lastError) {
    }

    private final List<SourceImporter> importers;
    private final RadarRecordRepository records;
    private final InnovationRepository innovations;
    private final InstitutionRepository institutions;
    private final SimilarityService similarity;
    private final AssistantService assistant;
    private final InnovationService innovationService;
    private final AssessmentService assessmentService;
    private final TransactionTemplate tx;
    private final boolean liveEnabled;
    private final Duration refreshInterval;
    private final Map<String, SourceStatus> status = new ConcurrentHashMap<>();
    private volatile Instant lastScanAt;

    public RadarService(List<SourceImporter> importers, RadarRecordRepository records, InnovationRepository innovations,
                        InstitutionRepository institutions, SimilarityService similarity, AssistantService assistant,
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
        this.innovationService = innovationService;
        this.assessmentService = assessmentService;
        this.tx = tx;
        this.liveEnabled = liveEnabled;
        this.refreshInterval = refreshInterval;
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
                    null, 0, 0, 0, 0, total, null)
                    : new SourceStatus(s.name(), s.description(), s.homepage(), s.live(), s.active(), s.lastRunAt(),
                    s.lastFetched(), s.lastCreated(), s.lastDuplicates(), s.lastUpdated(), total, s.lastError()));
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
        int scanned = 0, created = 0, duplicates = 0, updated = 0, skipped = 0;
        List<RadarRecord> touched = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (SourceImporter imp : importers) {
            if (!isActive(imp)) {
                continue;
            }
            List<SourceRecord> incoming;
            try {
                incoming = imp.fetch();
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("Radar: źródło {} niedostępne: {}", imp.name(), msg);
                errors.add(imp.name() + ": " + msg);
                status.put(imp.name(), new SourceStatus(imp.name(), imp.description(), imp.homepage(), imp.live(), true,
                        Instant.now(), 0, 0, 0, 0, 0, "Źródło niedostępne: " + msg));
                continue;
            }
            int[] counts = new int[4]; // created, duplicates, updated, skipped
            for (SourceRecord r : incoming) {
                try {
                    RadarRecord rec = tx.execute(t -> ingest(r, counts));
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
            status.put(imp.name(), new SourceStatus(imp.name(), imp.description(), imp.homepage(), imp.live(), true,
                    Instant.now(), incoming.size(), counts[0], counts[1], counts[2], 0, null));
            log.info("Radar: {} – pobrano {}, nowe {}, duplikaty {}, zaktualizowane {}", imp.name(), incoming.size(),
                    counts[0], counts[1], counts[2]);
        }
        lastScanAt = Instant.now();
        return new ScanResult(scanned, created, duplicates, updated, skipped, touched, errors);
    }

    /** Jeden rekord: nowy → karta, znany → odświeżenie, bardzo podobny do istniejącej innowacji → duplikat. */
    private RadarRecord ingest(SourceRecord r, int[] counts) {
        int hash = Objects.hash(r.title(), r.abstractText(), r.facts());
        Optional<RadarRecord> known = records.findFirstBySourceAndRef(r.source(), r.ref());
        if (known.isPresent()) {
            RadarRecord rec = known.get();
            rec.setLastSeenAt(Instant.now());
            if (rec.getContentHash() != hash && rec.getInnovation() != null) {
                Innovation inn = rec.getInnovation();
                inn.setTitle(r.title());
                inn.setSummary(assistant.heuristic(r.title() + ". " + r.abstractText()).summary());
                inn.getSourceFacts().clear();
                inn.getSourceFacts().addAll(r.facts());
                inn.touch();
                innovationService.log(inn, EventType.EDYCJA, "Zaktualizowano na podstawie zmian w źródle " + r.source() + ".", null);
                assessmentService.autoAssess(inn);
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
            rec.setOutcome(RadarRecord.Outcome.NOWA_INNOWACJA);
            rec.setInnovation(createDetected(r));
            counts[0]++;
        }
        return records.save(rec);
    }

    private Innovation createDetected(SourceRecord r) {
        AssistantService.Draft d = assistant.heuristic(r.title() + ". " + r.abstractText());
        Innovation inn = new Innovation();
        inn.setTitle(r.title());
        inn.setSummary(d.summary());
        inn.setProblem(d.problem().isEmpty() ? null : d.problem());
        inn.setSolution(d.solution().isEmpty() ? r.abstractText() : d.solution());
        inn.setResults(d.results().isEmpty() ? null : d.results());
        inn.setType(r.typeHint() != null ? r.typeHint() : d.type());
        inn.setSector(r.sectorHint() != null ? r.sectorHint() : d.sector());
        inn.setMaturity(r.maturityHint() != null ? r.maturityHint() : d.maturity());
        inn.setTrl(Trl.estimate(inn.getMaturity(), TextVectorizer.normalize(r.title() + " " + r.abstractText())));
        inn.setArea(Area.derive(inn.getType(), inn.getSector(), r.institutionType()));
        // klasyfikacja pochodzi z automatu – to szacunek, dopóki analityk jej nie potwierdzi
        inn.getEstimated().addAll(List.of("area", "type", "sector", "maturity", "trl"));
        inn.setSourceYear(r.year());
        inn.getSourceFacts().addAll(r.facts());
        r.keywords().stream().filter(k -> k != null && !k.isBlank()).forEach(k -> inn.getTags().add(k.toLowerCase()));
        if (r.url() != null) {
            inn.getLinks().add(r.url());
        }
        String instName = r.institution() == null || r.institution().isBlank() ? "Instytucja nieznana" : r.institution();
        inn.setInstitution(institutions.findFirstByNameIgnoreCase(instName).orElseGet(() ->
                institutions.save(new Institution(instName,
                        r.institutionType() == null ? InstitutionType.INSTYTUCJA_PUBLICZNA : r.institutionType(),
                        r.city(), r.county(), r.region() == null ? "Polska" : r.region()))));
        inn.setSource(Source.RADAR);
        inn.setSourceName(r.source());
        inn.setSourceRef(r.ref());
        inn.setSourceAuthors(r.authors());
        inn.setStatus(InnovationStatus.WYKRYTA);
        innovations.save(inn);
        String auto = assessmentService.autoAssess(inn)
                .map(a -> " Ocena automatyczna: " + Math.round(a.proposedTotal()) + "/100 (szacunek).").orElse("");
        innovationService.log(inn, EventType.WYKRYTO, "Wykryto w źródle " + r.source() + " (" + r.ref()
                + (r.year() != null ? ", " + r.year() : "") + ")." + auto + " Czeka na weryfikację analityka PCI.", null);
        return inn;
    }
}
