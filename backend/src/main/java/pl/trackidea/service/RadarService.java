package pl.trackidea.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.InstitutionRepository;
import pl.trackidea.repo.RadarRecordRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Radar: automatyczne wykrywanie innowacji w otwartych bazach (PBN, NCBiR, Interreg…).
 * Na hackathonie czyta próbki eksportów z plików JSON w resources/radar/ – w docelowej wersji
 * każdy importer to osobny moduł pobierający dane z API źródła.
 * Przetwarzanie: import → ekstrakcja pól → deduplikacja → przypisanie instytucji → status „Wykryta”.
 */
@Service
@Transactional
public class RadarService {

    /** Powyżej tego podobieństwa rekord uznajemy za duplikat istniejącej innowacji. */
    static final int DUPLICATE_THRESHOLD = 75;

    public record SourceRecord(String source, String ref, String title, String abstractText, String institution,
                               InstitutionType institutionType, String city, String county, String authors,
                               Integer year, List<String> keywords, String url) {
    }

    public record ScanResult(int scanned, int created, int duplicates, int skipped, List<RadarRecord> records) {
    }

    private final RadarRecordRepository records;
    private final InnovationRepository innovations;
    private final InstitutionRepository institutions;
    private final SimilarityService similarity;
    private final AssistantService assistant;
    private final InnovationService innovationService;
    private final JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    public RadarService(RadarRecordRepository records, InnovationRepository innovations,
                        InstitutionRepository institutions, SimilarityService similarity, AssistantService assistant,
                        InnovationService innovationService) {
        this.records = records;
        this.innovations = innovations;
        this.institutions = institutions;
        this.similarity = similarity;
        this.assistant = assistant;
        this.innovationService = innovationService;
    }

    public List<String> sources() {
        return List.of("PBN – publikacje naukowe", "NCBiR – wyszukiwarka sukcesów beneficjentów",
                "Interreg – lista projektów", "UPRP / EPO – patenty (do oceny nowości)");
    }

    /** Skan „na żywo” (przycisk w panelu PCI). */
    public ScanResult scan() {
        return scan("classpath:radar/source-*.json");
    }

    /** Rekordy zaimportowane przy starcie aplikacji (historia Radaru w danych demo). */
    public ScanResult scanInitial() {
        return scan("classpath:radar/initial-*.json");
    }

    private ScanResult scan(String pattern) {
        List<SourceRecord> incoming = loadSourceRecords(pattern);
        int created = 0, duplicates = 0, skipped = 0;
        List<RadarRecord> touched = new ArrayList<>();
        for (SourceRecord r : incoming) {
            if (records.existsBySourceAndRef(r.source(), r.ref())) {
                skipped++;
                continue;
            }
            String text = r.title() + " " + r.title() + " " + r.abstractText() + " " + String.join(" ", r.keywords());
            List<SimilarItem> similar = similarity.similarInnovations(text, null, 1);
            RadarRecord rec = new RadarRecord();
            rec.setSource(r.source());
            rec.setRef(r.ref());
            rec.setTitle(r.title());
            rec.setInstitutionName(r.institution());
            if (!similar.isEmpty() && similar.getFirst().similarityPercent() >= DUPLICATE_THRESHOLD) {
                rec.setOutcome(RadarRecord.Outcome.DUPLIKAT);
                rec.setDuplicateOf(innovations.getReferenceById(similar.getFirst().id()));
                rec.setDuplicateSimilarity(similar.getFirst().similarityPercent());
                duplicates++;
            } else {
                rec.setOutcome(RadarRecord.Outcome.NOWA_INNOWACJA);
                rec.setInnovation(createDetected(r));
                created++;
            }
            touched.add(records.save(rec));
        }
        return new ScanResult(incoming.size(), created, duplicates, skipped, touched);
    }

    private Innovation createDetected(SourceRecord r) {
        AssistantService.Draft d = assistant.heuristic(r.title() + ". " + r.abstractText());
        Innovation inn = new Innovation();
        inn.setTitle(r.title());
        inn.setSummary(d.summary());
        inn.setProblem(d.problem().isEmpty() ? null : d.problem());
        inn.setSolution(d.solution().isEmpty() ? r.abstractText() : d.solution());
        inn.setResults(d.results().isEmpty() ? null : d.results());
        inn.setType(d.type());
        inn.setSector(d.sector());
        inn.setMaturity(d.maturity());
        if (r.keywords() != null) {
            r.keywords().forEach(k -> inn.getTags().add(k.toLowerCase()));
        }
        if (r.url() != null) {
            inn.getLinks().add(r.url());
        }
        inn.setInstitution(institutions.findFirstByNameIgnoreCase(r.institution()).orElseGet(() ->
                institutions.save(new Institution(r.institution(),
                        r.institutionType() == null ? InstitutionType.UCZELNIA : r.institutionType(),
                        r.city(), r.county(), "podkarpackie"))));
        inn.setSource(Source.RADAR);
        inn.setSourceName(r.source());
        inn.setSourceRef(r.ref());
        inn.setSourceAuthors(r.authors());
        inn.setStatus(InnovationStatus.WYKRYTA);
        innovations.save(inn);
        innovationService.log(inn, EventType.WYKRYTO, "Wykryto w źródle " + r.source() + " (" + r.ref()
                + "). Autorzy w źródle: " + r.authors() + ". Czeka na potwierdzenie autora.", null);
        return inn;
    }

    private List<SourceRecord> loadSourceRecords(String pattern) {
        List<SourceRecord> out = new ArrayList<>();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources(pattern);
            for (Resource f : files) {
                try (InputStream in = f.getInputStream()) {
                    out.addAll(json.readValue(in, new TypeReference<List<SourceRecord>>() { }));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się wczytać danych Radaru", e);
        }
        return out;
    }
}
