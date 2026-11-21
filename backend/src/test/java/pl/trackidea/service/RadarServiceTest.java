package pl.trackidea.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.RadarRecord;
import pl.trackidea.domain.Sector;
import pl.trackidea.domain.Source;
import pl.trackidea.radar.SourceImporter;
import pl.trackidea.radar.SourceNotReady;
import pl.trackidea.radar.SourceRecord;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.InstitutionRepository;
import pl.trackidea.repo.RadarRecordRepository;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Skan Radaru na podstawionym źródle: nowa karta, odświeżenie, duplikat, błąd źródła. */
@SpringBootTest(properties = "trackidea.radar.live=false")
@Transactional
class RadarServiceTest {

    private static final String SOURCE = "Źródło testowe";
    private static final String ABSTRACT = "Wspólnota mieszkaniowa ustawiła kompostownik sąsiedzki z czujnikiem wilgotności. "
            + "Aplikacja przypomina mieszkańcom o przerzucaniu kompostu i pokazuje, kiedy można go odebrać. "
            + "Rozwiązanie działa od 2025 roku na trzech osiedlach.";

    /** Źródło, któremu test podstawia rekordy albo błąd. */
    private static final class FakeImporter implements SourceImporter {
        final String name;
        final boolean live;
        List<SourceRecord> records = new ArrayList<>();
        Exception failure;
        int fetches;

        FakeImporter(String name, boolean live) {
            this.name = name;
            this.live = live;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "Źródło podstawione w teście";
        }

        @Override
        public String homepage() {
            return "https://example.org";
        }

        @Override
        public boolean live() {
            return live;
        }

        @Override
        public List<SourceRecord> fetch() throws Exception {
            fetches++;
            if (failure != null) {
                throw failure;
            }
            return records;
        }
    }

    @Autowired
    RadarRecordRepository records;
    @Autowired
    InnovationRepository innovations;
    @Autowired
    InstitutionRepository institutions;
    @Autowired
    SimilarityService similarity;
    @Autowired
    AssistantService assistant;
    @Autowired
    InnovationService innovationService;
    @Autowired
    AssessmentService assessmentService;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    InstitutionDirectory directory;
    @Autowired
    RecordClassifier classifier;

    private FakeImporter importer;
    private RadarService radar;

    @BeforeEach
    void setUp() {
        importer = new FakeImporter(SOURCE, true);
        radar = radar(true, importer);
    }

    private RadarService radar(boolean live, SourceImporter... importers) {
        return new RadarService(List.of(importers), records, innovations, directory, similarity, assistant,
                classifier, innovationService, assessmentService, tx, live, Duration.ofHours(6));
    }

    private static SourceRecord record(String ref, String title, String abstractText, String fact) {
        return new SourceRecord(SOURCE, ref, title, abstractText, "Wspólnota Radarowa", InstitutionType.NGO, "Radarowo",
                "radarowski", "podkarpackie", "Zarząd wspólnoty", 2025, List.of("Kompost", " "), "https://example.org/" + ref,
                InnovationType.SPOLECZNA, Sector.SRODOWISKO, Maturity.STOSOWANE, List.of(fact));
    }

    private RadarService.SourceStatus status() {
        return radar.sources().getFirst();
    }

    @Test
    void newRecordBecomesDetectedCardWithEstimatedScore() {
        importer.records = List.of(record("R-1", "Kompostownik sąsiedzki z czujnikiem wilgotności", ABSTRACT, "Fakt ze źródła"));

        RadarService.ScanResult result = radar.scan();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.duplicates() + result.updated() + result.skipped()).isZero();
        assertThat(result.errors()).isEmpty();
        RadarRecord rec = result.records().getFirst();
        assertThat(rec.getOutcome()).isEqualTo(RadarRecord.Outcome.NOWA_INNOWACJA);
        assertThat(rec.getInstitutionName()).isEqualTo("Wspólnota Radarowa");
        Innovation inn = rec.getInnovation();
        assertThat(inn.getTitle()).isEqualTo("Kompostownik sąsiedzki z czujnikiem wilgotności");
        assertThat(inn.getStatus()).isEqualTo(InnovationStatus.WYKRYTA);
        assertThat(inn.getSource()).isEqualTo(Source.RADAR);
        assertThat(inn.getSourceName()).isEqualTo(SOURCE);
        assertThat(inn.getSourceRef()).isEqualTo("R-1");
        assertThat(inn.getSourceYear()).isEqualTo(2025);
        assertThat(inn.getSourceAuthors()).isEqualTo("Zarząd wspólnoty");
        // wskazówki ze źródła mają pierwszeństwo przed rozpoznaniem z tekstu
        assertThat(inn.getType()).isEqualTo(InnovationType.SPOLECZNA);
        assertThat(inn.getSector()).isEqualTo(Sector.SRODOWISKO);
        assertThat(inn.getMaturity()).isEqualTo(Maturity.STOSOWANE);
        assertThat(inn.getTrl()).isBetween(1, 9);
        assertThat(inn.getArea()).isNotNull();
        assertThat(inn.getEstimated()).containsExactlyInAnyOrder("area", "type", "sector", "maturity", "trl", "content");
        assertThat(inn.getAnalysisEngine()).isEqualTo("REGUŁY");
        assertThat(inn.getTags()).contains("kompost");
        assertThat(inn.getLinks()).containsExactly("https://example.org/R-1");
        assertThat(inn.getSourceFacts()).containsExactly("Fakt ze źródła");
        assertThat(inn.getInstitution().getName()).isEqualTo("Wspólnota Radarowa");
        assertThat(inn.getInstitution().getCounty()).isEqualTo("radarowski");
        // karta od razu ma ocenę automatyczną (szacunek), jeszcze niezatwierdzoną
        assertThat(assessmentService.latest(inn)).get().satisfies(a -> assertThat(a.isApproved()).isFalse());

        assertThat(status().lastFetched()).isEqualTo(1);
        assertThat(status().lastCreated()).isEqualTo(1);
        assertThat(status().totalRecords()).isEqualTo(1);
        assertThat(status().active()).isTrue();
        assertThat(radar.lastScanAt()).isNotNull();
        assertThat(radar.nextScanAt()).isEqualTo(radar.lastScanAt().plus(Duration.ofHours(6)));
    }

    @Test
    void knownRecordIsSkippedOrRefreshedWhenSourceChanged() {
        importer.records = List.of(record("R-1", "Kompostownik sąsiedzki z czujnikiem wilgotności", ABSTRACT, "Status: pilotaż"));
        Innovation inn = radar.scan().records().getFirst().getInnovation();

        RadarService.ScanResult unchanged = radar.scan();
        assertThat(unchanged.skipped()).isEqualTo(1);
        assertThat(unchanged.created() + unchanged.updated()).isZero();
        assertThat(unchanged.records()).isEmpty();

        importer.records = List.of(record("R-1", "Kompostownik sąsiedzki – wersja miejska", ABSTRACT, "Status: wdrożony"));
        RadarService.ScanResult changed = radar.scan();

        assertThat(changed.updated()).isEqualTo(1);
        assertThat(changed.created()).isZero();
        assertThat(inn.getTitle()).isEqualTo("Kompostownik sąsiedzki – wersja miejska");
        assertThat(inn.getSourceFacts()).containsExactly("Status: wdrożony");
        assertThat(records.countBySource(SOURCE)).isEqualTo(1);
        assertThat(status().lastUpdated()).isEqualTo(1);
    }

    @Test
    void sameSolutionFromAnotherRecordIsMarkedAsDuplicate() {
        importer.records = List.of(
                record("R-1", "Kompostownik sąsiedzki z czujnikiem wilgotności", ABSTRACT, "Fakt"),
                record("R-2", "Kompostownik sąsiedzki z czujnikiem wilgotności", ABSTRACT, "Fakt"));

        RadarService.ScanResult result = radar.scan();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.duplicates()).isEqualTo(1);
        RadarRecord duplicate = records.findFirstBySourceAndRef(SOURCE, "R-2").orElseThrow();
        assertThat(duplicate.getOutcome()).isEqualTo(RadarRecord.Outcome.DUPLIKAT);
        assertThat(duplicate.getInnovation()).isNull();
        assertThat(duplicate.getDuplicateOf().getSourceRef()).isEqualTo("R-1");
        assertThat(duplicate.getDuplicateSimilarity()).isGreaterThanOrEqualTo(RadarService.DUPLICATE_THRESHOLD);
        assertThat(status().lastDuplicates()).isEqualTo(1);
    }

    @Test
    void unavailableSourceIsReportedAndDoesNotStopScan() {
        importer.failure = new IOException("HTTP 503 z example.org");

        RadarService.ScanResult result = radar.scan();

        assertThat(result.scanned()).isZero();
        assertThat(result.errors()).containsExactly(SOURCE + ": HTTP 503 z example.org");
        assertThat(status().lastError()).isEqualTo("Źródło niedostępne: HTTP 503 z example.org");

        // wyjątek bez komunikatu jest opisany nazwą klasy
        importer.failure = new IllegalStateException();
        assertThat(radar.scan().errors()).containsExactly(SOURCE + ": IllegalStateException");
    }

    @Test
    void sourceStillPreparingDataIsNotAnError() {
        importer.failure = new SourceNotReady("Pobieranie listy projektów: 3 z 41 MB");

        RadarService.ScanResult result = radar.scan();

        assertThat(result.errors()).isEmpty();
        assertThat(status().lastError()).isNull();
        assertThat(status().preparing()).isEqualTo("Pobieranie listy projektów: 3 z 41 MB");
    }

    @Test
    void recordThatCannotBeSavedIsSkippedWithoutStoppingOthers() {
        // rekord bez tytułu nie da się zapisać jako karta – skan liczy go jako pominięty i idzie dalej
        SourceRecord broken = new SourceRecord(SOURCE, "R-0", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        importer.records = List.of(broken);

        RadarService.ScanResult result = radar.scan();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void onlySourcesMatchingModeAreScanned() {
        FakeImporter offline = new FakeImporter("Źródło offline", false);
        RadarService online = radar(true, importer, offline);

        online.scan();

        assertThat(importer.fetches).isEqualTo(1);
        assertThat(offline.fetches).isZero();
        assertThat(online.isLive()).isTrue();
        assertThat(online.sources()).extracting(RadarService.SourceStatus::active).containsExactly(true, false);
        // przed pierwszym skanem źródło nie ma jeszcze stanu
        assertThat(online.sources().get(1).lastRunAt()).isNull();
    }

    @Test
    void initialScanRunsOnlyOffline() {
        radar.scanInitial();
        assertThat(importer.fetches).isZero();
        assertThat(radar.lastScanAt()).isNull();
        assertThat(radar.nextScanAt()).isNull();

        FakeImporter offline = new FakeImporter("Źródło offline", false);
        radar(false, offline).scanInitial();
        assertThat(offline.fetches).isEqualTo(1);
    }
}
