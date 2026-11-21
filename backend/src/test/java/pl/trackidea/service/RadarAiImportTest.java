package pl.trackidea.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pl.trackidea.domain.Area;
import pl.trackidea.domain.Assessment;
import pl.trackidea.domain.Criterion;
import pl.trackidea.domain.CriterionScore;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.Person;
import pl.trackidea.domain.RadarRecord;
import pl.trackidea.domain.Sector;
import pl.trackidea.radar.SourceImporter;
import pl.trackidea.radar.SourceRecord;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.PersonRepository;
import pl.trackidea.repo.RadarRecordRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Import z analizą AI (model podstawiony): opis po polsku, szacunki, automatyczne odrzucanie i przywracanie. */
@SpringBootTest(properties = "trackidea.radar.live=false")
@Transactional
class RadarAiImportTest {

    private static final String SOURCE = "Źródło AI";

    @Autowired
    RadarRecordRepository records;
    @Autowired
    InnovationRepository innovations;
    @Autowired
    InstitutionDirectory directory;
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
    PersonRepository people;

    @TempDir
    Path dir;

    private final FakeClaude claude = new FakeClaude();
    private final List<SourceRecord> incoming = new ArrayList<>();
    private RadarService radar;
    private Person analyst;

    @BeforeEach
    void setUp() {
        SourceImporter importer = new SourceImporter() {
            public String name() { return SOURCE; }
            public String description() { return "Źródło podstawione w teście"; }
            public String homepage() { return null; }
            public boolean live() { return true; }
            public List<SourceRecord> fetch() { return List.copyOf(incoming); }
        };
        RecordClassifier classifier = new RecordClassifier(claude, true, 10, dir.toString());
        radar = new RadarService(List.of(importer), records, innovations, directory, similarity, assistant, classifier,
                innovationService, assessmentService, tx, true, Duration.ofHours(6));
        analyst = people.findByEmailIgnoreCase("k.nowak@pci.example.org").orElseThrow();
    }

    private static SourceRecord patent(String ref, Maturity hint) {
        return new SourceRecord(SOURCE, ref, "AMINO ACID SEQUENCE OF RECOMBINANT TICK-BORNE ENCEPHALITIS VIRUS-LIKE PARTICLES",
                "The first aspect of the invention is a recombinant antigen. The data confirm immunogenicity in a mouse model.",
                "Uniwersytet Gdanski", InstitutionType.UCZELNIA, "Gdańsk", null, "pomorskie", "Anna Nowak", 2025, List.of(),
                "https://example.org/" + ref, null, null, hint, List.of("Rodzaj ochrony: Patent na wynalazek"));
    }

    @Test
    void aiAnalysisBecomesCardMarkedAsEstimate() {
        claude.answer = FakeClaude.answer(RecordAnalysis.Verdict.INNOWACJA, RecordClassifier.Stage.BRAK_DANYCH, 0, 3);
        incoming.add(patent("EP-1", null));

        RadarService.ScanResult result = radar.scan();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.rejected()).isZero();
        Innovation inn = result.records().getFirst().getInnovation();
        assertThat(inn.getStatus()).isEqualTo(InnovationStatus.WYKRYTA);
        assertThat(inn.getAnalysisEngine()).isEqualTo("CLAUDE");
        // opis po polsku od modelu; oryginalny tytuł zostaje jako fakt ze źródła
        assertThat(inn.getTitle()).isEqualTo("Szczepionka przeciw kleszczowemu zapaleniu mózgu");
        assertThat(inn.getSummary()).startsWith("Antygen szczepionkowy");
        assertThat(inn.getSourceFacts()).contains("Rodzaj ochrony: Patent na wynalazek",
                "Tytuł w źródle: AMINO ACID SEQUENCE OF RECOMBINANT TICK-BORNE ENCEPHALITIS VIRUS-LIKE PARTICLES");
        assertThat(inn.getSector()).isEqualTo(Sector.ZDROWIE);
        assertThat(inn.getArea()).isEqualTo(Area.TECHNOLOGICZNY);
        // model nie miał podstaw do oceny etapu – karta nie udaje, że to „koncepcja”
        assertThat(inn.getMaturity()).isNull();
        assertThat(inn.getTrl()).isNull();
        assertThat(inn.getEstimated()).containsExactlyInAnyOrder("area", "type", "sector", "content");
        assertThat(inn.getTags()).contains("szczepionka", "wirusologia");

        Assessment a = assessmentService.latest(inn).orElseThrow();
        assertThat(score(a, Criterion.WDROZENIE).getProposedJustification()).contains("nie podaje");
        assertThat(score(a, Criterion.TRL).getProposedJustification()).contains("Nie ustalono");
        CriterionScore novelty = score(a, Criterion.INNOWACYJNOSC);
        assertThat(novelty.getProposedScore()).isEqualTo(3);
        assertThat(novelty.getProposedJustification()).contains("Szacunek modelu AI: 3/5", "Nowy antygen");
    }

    @Test
    void recordThatIsNotInnovationIsRejectedAutomaticallyAndCanBeRestored() {
        claude.answer = FakeClaude.answer(RecordAnalysis.Verdict.NIE_INNOWACJA, RecordClassifier.Stage.BRAK_DANYCH, 0, 0);
        incoming.add(patent("EP-2", Maturity.TESTY));

        RadarService.ScanResult result = radar.scan();

        assertThat(result.created()).isZero();
        assertThat(result.rejected()).isEqualTo(1);
        RadarRecord rec = result.records().getFirst();
        assertThat(rec.getOutcome()).isEqualTo(RadarRecord.Outcome.ODRZUCONA_AUTOMATYCZNIE);
        Innovation inn = rec.getInnovation();
        assertThat(inn.getStatus()).isEqualTo(InnovationStatus.ODRZUCONA);
        assertThat(inn.isAutoRejected()).isTrue();
        assertThat(innovationService.canView(inn, null)).isFalse();
        assertThat(innovations.findByStatusAndAutoRejectedTrue(InnovationStatus.ODRZUCONA)).contains(inn);

        // decyzja należy do człowieka: analityk może przywrócić rekord
        assertThatThrownBy(() -> innovationService.restore(inn.getId(), null)).isInstanceOf(DomainException.class);
        innovationService.restore(inn.getId(), analyst);

        assertThat(inn.getStatus()).isEqualTo(InnovationStatus.WYKRYTA);
        assertThat(inn.isAutoRejected()).isFalse();
        assertThat(assessmentService.latest(inn)).isPresent();
        assertThatThrownBy(() -> innovationService.restore(inn.getId(), analyst)).isInstanceOf(DomainException.class);
    }

    @Test
    void recordAddedByAnalystIsNeverRejectedAutomatically() {
        claude.answer = FakeClaude.answer(RecordAnalysis.Verdict.NIE_INNOWACJA, RecordClassifier.Stage.PROTOTYP, 4, 2);

        RadarRecord rec = radar.analyze(patent("EP-3", null));

        assertThat(rec.getOutcome()).isEqualTo(RadarRecord.Outcome.NOWA_INNOWACJA);
        assertThat(rec.getInnovation().getStatus()).isEqualTo(InnovationStatus.WYKRYTA);
        assertThat(rec.getInnovation().getMaturity()).isEqualTo(Maturity.PROTOTYP);
        assertThat(rec.getInnovation().getTrl()).isEqualTo(4);
    }

    @Test
    void sourceChangeUpdatesStageButKeepsTextOfCardEditedByAnalyst() {
        claude.failure = new IllegalStateException("brak sieci");
        incoming.add(patent("EP-4", Maturity.TESTY));
        Innovation inn = radar.scan().records().getFirst().getInnovation();
        assertThat(inn.getAnalysisEngine()).isEqualTo("REGUŁY");
        assertThat(inn.getMaturity()).isEqualTo(Maturity.TESTY);
        inn.setTitle("Tytuł poprawiony przez analityka");
        inn.setEditedByAnalyst(true);

        incoming.clear();
        incoming.add(patent("EP-4", Maturity.STOSOWANE));
        RadarService.ScanResult result = radar.scan();

        assertThat(result.updated()).isEqualTo(1);
        assertThat(inn.getTitle()).isEqualTo("Tytuł poprawiony przez analityka");
        assertThat(inn.getMaturity()).isEqualTo(Maturity.STOSOWANE);
        assertThat(inn.getEstimated()).contains("maturity");
    }

    @Test
    void sameInstitutionWrittenWithoutPolishLettersIsOneRecord() {
        var first = directory.resolve("POLITECHNIKA TESTOWA W LODZI", InstitutionType.UCZELNIA, null, null, null);
        var second = directory.resolve("Politechnika Testowa w Łodzi", InstitutionType.UCZELNIA, "Łódź", null, "łódzkie");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getName()).isEqualTo("Politechnika Testowa w Łodzi");
        assertThat(second.getCity()).isEqualTo("Łódź");
        assertThat(second.getRegion()).isEqualTo("łódzkie");
        assertThat(directory.resolve(" ", null, null, null, null).getName()).isEqualTo(InstitutionDirectory.UNKNOWN);
    }

    private static CriterionScore score(Assessment a, Criterion c) {
        return a.getScores().stream().filter(s -> s.getCriterion() == c).findFirst().orElseThrow();
    }
}
