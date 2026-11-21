package pl.trackidea.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.Area;
import pl.trackidea.domain.Assessment;
import pl.trackidea.domain.Criterion;
import pl.trackidea.domain.CriterionScore;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationEvent;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.Institution;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.PathType;
import pl.trackidea.domain.Person;
import pl.trackidea.domain.Sector;
import pl.trackidea.domain.TransferModel;
import pl.trackidea.repo.InnovationEventRepository;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.InstitutionRepository;
import pl.trackidea.repo.PersonRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Weryfikacja karty przez analityka: poprawki, propozycja i zatwierdzenie oceny, zastosowania, odrzucenie. */
@SpringBootTest(properties = "trackidea.radar.live=false")
@Transactional
class InnovationServiceTest {

    @Autowired
    InnovationService service;
    @Autowired
    AssessmentService assessmentService;
    @Autowired
    InnovationRepository innovations;
    @Autowired
    InnovationEventRepository events;
    @Autowired
    InstitutionRepository institutions;
    @Autowired
    PersonRepository people;

    private Person analyst;
    private Innovation detected;

    @BeforeEach
    void setUp() {
        analyst = people.findByEmailIgnoreCase("k.nowak@pci.example.org").orElseThrow();
        detected = new Innovation();
        detected.setTitle("Czujnik zajętości miejsc parkingowych dla małych miast");
        detected.setSummary("Czujniki w nawierzchni pokazują wolne miejsca na tablicach przy wjeździe do centrum.");
        detected.setSolution("Czujniki magnetyczne wysyłają stan miejsca do serwera miasta.");
        detected.setType(InnovationType.TECHNICZNA);
        detected.setSector(Sector.ICT);
        detected.setMaturity(Maturity.PILOTAZ);
        detected.setTrl(6);
        detected.setArea(Area.TECHNOLOGICZNY);
        detected.getEstimated().addAll(List.of("area", "type", "sector", "maturity", "trl"));
        detected.setSourceName("Źródło testowe");
        detected.setInstitution(institutions.save(new Institution("Miasto Testowe", InstitutionType.JST, "Testowo", "testowy", null)));
        innovations.save(detected);
    }

    private static InnovationService.CardEdit edit(String title, InnovationType type, Integer trl, Long institutionId) {
        return new InnovationService.CardEdit(title, "  Nowy opis  ", "Problem", "Rozwiązanie", "Wymagania", "Efekty", type,
                Sector.ICT, Maturity.PILOTAZ, TransferModel.values()[0], "zgłoszenie patentowe", List.of(" AI ", "", "Parkingi"),
                List.of(" https://example.org/opis ", " "), institutionId, "  Zespół miasta  ", trl, Area.ADMINISTRACYJNY);
    }

    private static HttpStatus status(Throwable t) {
        return ((DomainException) t).getStatus();
    }

    private String lastEvent(Innovation inn) {
        List<InnovationEvent> list = events.findByInnovationOrderByCreatedAtAscIdAsc(inn);
        return list.getLast().getMessage();
    }

    @Test
    void measuresCardCompleteness() {
        InnovationService.Completeness empty = service.completeness(new Innovation());
        assertThat(empty.percent()).isZero();
        assertThat(empty.missing()).containsExactly("Tytuł", "Krótki opis", "Problem", "Rozwiązanie", "Typ", "Sektor",
                "Etap wdrożenia", "Instytucja", "Źródło");

        InnovationService.Completeness partial = service.completeness(detected);
        assertThat(partial.missing()).containsExactly("Problem");
        assertThat(partial.percent()).isEqualTo(89);

        detected.setProblem("Kierowcy krążą w poszukiwaniu miejsca.");
        assertThat(service.completeness(detected).percent()).isEqualTo(100);
        // link do źródła wystarcza zamiast nazwy źródła
        detected.setSourceName(null);
        detected.getLinks().add("https://example.org");
        assertThat(service.completeness(detected).missing()).isEmpty();
    }

    @Test
    void onlyPciStaffCanChangeCards() {
        assertThatThrownBy(() -> service.update(detected.getId(), edit("Tytuł", InnovationType.TECHNICZNA, 6, null), null))
                .isInstanceOf(DomainException.class).satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.FORBIDDEN));
        Person withoutRole = new Person();
        assertThatThrownBy(() -> service.reject(detected.getId(), "powód", withoutRole))
                .isInstanceOf(DomainException.class).hasMessageContaining("wymaga roli");
        assertThat(service.isStaff(analyst)).isTrue();
        assertThat(service.isStaff(null)).isFalse();
        assertThatThrownBy(() -> service.get(-1L)).isInstanceOf(DomainException.class)
                .satisfies(t -> assertThat(status(t)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void analystCorrectsCardAndConfirmsChangedFields() {
        Innovation updated = service.update(detected.getId(), edit("  Czujniki parkingowe  ", InnovationType.PROCESOWA, 7, null), analyst);

        assertThat(updated.getTitle()).isEqualTo("Czujniki parkingowe");
        assertThat(updated.getSummary()).isEqualTo("Nowy opis");
        assertThat(updated.getType()).isEqualTo(InnovationType.PROCESOWA);
        assertThat(updated.getTrl()).isEqualTo(7);
        assertThat(updated.getArea()).isEqualTo(Area.ADMINISTRACYJNY);
        assertThat(updated.getIpStatus()).isEqualTo("zgłoszenie patentowe");
        assertThat(updated.getSourceAuthors()).isEqualTo("Zespół miasta");
        assertThat(updated.getTags()).containsExactly("ai", "parkingi");
        assertThat(updated.getLinks()).containsExactly("https://example.org/opis");
        // zmienione pola przestają być szacunkiem; sektor i etap zostały bez zmian, więc nadal są szacunkiem
        assertThat(updated.getEstimated()).containsExactlyInAnyOrder("sector", "maturity");
        assertThat(lastEvent(updated)).isEqualTo("Analityk poprawił kartę innowacji.");
        // ocena automatyczna jest liczona od nowa po poprawce
        assertThat(assessmentService.latest(updated)).isPresent();
    }

    @Test
    void updateKeepsTitleWhenBlankAndCutsLongSummary() {
        InnovationService.CardEdit form = new InnovationService.CardEdit(" ", "x".repeat(700), null, null, null, null,
                InnovationType.TECHNICZNA, Sector.ICT, Maturity.PILOTAZ, null, null, null, null, null, null, 6, null);

        Innovation updated = service.update(detected.getId(), form, analyst);

        assertThat(updated.getTitle()).isEqualTo("Czujnik zajętości miejsc parkingowych dla małych miast");
        assertThat(updated.getSummary()).hasSize(500);
        assertThat(updated.getProblem()).isNull();
        // nic nie zmieniono w klasyfikacji – wszystko nadal jest szacunkiem, a obszar zostaje
        assertThat(updated.getEstimated()).hasSize(5);
        assertThat(updated.getArea()).isEqualTo(Area.TECHNOLOGICZNY);
    }

    @Test
    void updateValidatesTrlAndInstitution() {
        assertThatThrownBy(() -> service.update(detected.getId(), edit("T", InnovationType.TECHNICZNA, 10, null), analyst))
                .isInstanceOf(DomainException.class).hasMessageContaining("TRL");
        assertThatThrownBy(() -> service.update(detected.getId(), edit("T", InnovationType.TECHNICZNA, 0, null), analyst))
                .isInstanceOf(DomainException.class).hasMessageContaining("TRL");
        assertThatThrownBy(() -> service.update(detected.getId(), edit("T", InnovationType.TECHNICZNA, 6, -5L), analyst))
                .isInstanceOf(DomainException.class).hasMessage("Nieznana instytucja.");

        Institution other = institutions.save(new Institution("Gmina Druga", InstitutionType.JST, "Drugie", "drugi", null));
        assertThat(service.update(detected.getId(), edit("T", InnovationType.TECHNICZNA, 6, other.getId()), analyst).getInstitution())
                .isEqualTo(other);
    }

    @Test
    void assessmentIsProposedThenApprovedWithCorrections() {
        Innovation proposed = service.proposeAssessment(detected.getId(), analyst);
        assertThat(proposed.getStatus()).isEqualTo(InnovationStatus.W_OCENIE);
        assertThat(lastEvent(proposed)).startsWith("W ocenie: Wygenerowano propozycję oceny:");
        assertThatThrownBy(() -> service.proposeAssessment(detected.getId(), analyst))
                .isInstanceOf(DomainException.class).hasMessageContaining("już czeka");

        Assessment assessment = assessmentService.latest(detected).orElseThrow();
        CriterionScore first = assessment.getScores().getFirst();
        int corrected = first.getProposedScore() == 5 ? 4 : first.getProposedScore() + 1;
        assertThatThrownBy(() -> service.approveAssessment(detected.getId(),
                List.of(new InnovationService.ScoreInput(first.getCriterion(), 6, null)), analyst))
                .isInstanceOf(DomainException.class).hasMessageContaining("1–5");

        Innovation approved = service.approveAssessment(detected.getId(), List.of(
                new InnovationService.ScoreInput(first.getCriterion(), corrected, "Potwierdzone u wykonawcy"),
                new InnovationService.ScoreInput(Criterion.WIARYGODNOSC, assessment.score(Criterion.WIARYGODNOSC), " ")), analyst);

        assertThat(approved.getStatus()).isEqualTo(InnovationStatus.OCENIONA);
        // zatwierdzenie oceny potwierdza też klasyfikację karty
        assertThat(approved.getEstimated()).isEmpty();
        assertThat(assessment.isApproved()).isTrue();
        assertThat(assessment.getApprovedBy()).isEqualTo(analyst);
        assertThat(first.getFinalScore()).isEqualTo(corrected);
        assertThat(first.getFinalJustification()).isEqualTo("Potwierdzone u wykonawcy");
        // puste uzasadnienie analityka = zostaje uzasadnienie automatu
        CriterionScore credibility = assessment.getScores().stream()
                .filter(s -> s.getCriterion() == Criterion.WIARYGODNOSC).findFirst().orElseThrow();
        assertThat(credibility.getFinalJustification()).isEqualTo(credibility.getProposedJustification());
        assertThat(lastEvent(approved)).contains("Analityk zatwierdził ocenę", "poprawiono kryteria: 1");
    }

    @Test
    void approvalWithoutChangesKeepsProposedScores() {
        service.proposeAssessment(detected.getId(), analyst);

        Innovation approved = service.approveAssessment(detected.getId(), null, analyst);

        Assessment assessment = assessmentService.latest(approved).orElseThrow();
        assertThat(assessment.total()).isEqualTo(assessment.proposedTotal());
        assertThat(lastEvent(approved)).contains("bez zmian względem propozycji");
    }

    @Test
    void approvalRequiresProposal() {
        assertThatThrownBy(() -> service.approveAssessment(detected.getId(), List.of(), analyst))
                .isInstanceOf(DomainException.class).hasMessageContaining("tylko innowację w ocenie");
    }

    @Test
    void pathsAreChosenOnlyAfterApproval() {
        assertThatThrownBy(() -> service.choosePaths(detected.getId(), List.of(), analyst))
                .isInstanceOf(DomainException.class).hasMessageContaining("co najmniej jedno");
        assertThatThrownBy(() -> service.choosePaths(detected.getId(), null, analyst)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.choosePaths(detected.getId(), List.of(PathType.ROZWOJ_PCI), analyst))
                .isInstanceOf(DomainException.class).hasMessageContaining("po zatwierdzeniu oceny");

        service.proposeAssessment(detected.getId(), analyst);
        service.approveAssessment(detected.getId(), null, analyst);
        Innovation chosen = service.choosePaths(detected.getId(), List.of(PathType.PILOTAZ_BR, PathType.KOMERCJALIZACJA), analyst);

        assertThat(chosen.getChosenPaths()).containsExactly(PathType.PILOTAZ_BR, PathType.KOMERCJALIZACJA);
        assertThat(lastEvent(chosen)).isEqualTo("Wskazane zastosowania: " + PathType.PILOTAZ_BR.getLabel() + ", "
                + PathType.KOMERCJALIZACJA.getLabel() + ".");
    }

    @Test
    void rejectedRecordLeavesCatalog() {
        Innovation rejected = service.reject(detected.getId(), "  To zakup sprzętu, nie innowacja.  ", analyst);

        assertThat(rejected.getStatus()).isEqualTo(InnovationStatus.ODRZUCONA);
        assertThat(lastEvent(rejected)).isEqualTo("Odrzucona: To zakup sprzętu, nie innowacja.");
        // odrzucony rekord widzi tylko zespół PCI i nie można go już oceniać
        assertThat(service.canView(rejected, null)).isFalse();
        assertThat(service.canView(rejected, analyst)).isTrue();
        assertThatThrownBy(() -> service.proposeAssessment(detected.getId(), analyst))
                .isInstanceOf(DomainException.class).hasMessageContaining("wyłączony z katalogu");
    }

    @Test
    void rejectionWithoutReasonUsesDefaultOne() {
        assertThat(lastEvent(service.reject(detected.getId(), " ", analyst))).isEqualTo("Odrzucona: Nie spełnia definicji innowacji.");
    }

    @Test
    void automaticAssessmentDoesNotClaimWhatItCannotKnow() {
        Innovation lesson = new Innovation();
        lesson.setTitle("Lekcje ekonomii prowadzone metodą gry symulacyjnej o budżecie gminy");
        lesson.setSummary("Uczniowie liceum układają budżet fikcyjnej gminy i bronią go przed radą.");
        lesson.setSolution("Scenariusz zajęć z kartami wydatków i rolami radnych.");
        lesson.setResults("Opis w raporcie z 2024 roku.");
        lesson.setType(InnovationType.EDUKACYJNA);
        lesson.setSector(Sector.EDUKACJA);
        lesson.setArea(Area.EDUKACYJNY);
        innovations.save(lesson);

        Assessment a = assessmentService.propose(lesson);

        // TRL nie dotyczy innowacji edukacyjnej, więc nie wchodzi do oceny ani do wyniku
        assertThat(a.getScores()).extracting(CriterionScore::getCriterion)
                .containsExactly(Criterion.WDROZENIE, Criterion.INNOWACYJNOSC, Criterion.POTENCJAL, Criterion.WIARYGODNOSC);
        CriterionScore stage = a.getScores().getFirst();
        assertThat(stage.getProposedScore()).isEqualTo(1);
        assertThat(stage.getProposedJustification()).contains("nie podaje");
        // brak podobnych w bazie to nie dowód nowości: automat nie daje 5
        CriterionScore novelty = a.getScores().get(1);
        assertThat(novelty.getProposedScore()).isLessThanOrEqualTo(4);
        // rok w tekście to nie „efekt opisany liczbowo”
        assertThat(a.getScores().get(3).getProposedJustification()).doesNotContain("liczbowo");

        lesson.setResults("Średnia ocen wzrosła o 0,6, w zajęciach wzięło udział 48 uczniów.");
        assertThat(assessmentService.propose(lesson).getScores().get(3).getProposedJustification()).contains("liczbowo");
        assertThat(AssessmentService.trlApplies(lesson)).isFalse();
        assertThat(AssessmentService.trlApplies(detected)).isTrue();
    }

    @Test
    void analystEditConfirmsDescriptionAndProtectsCardFromRadar() {
        detected.getEstimated().add("content");

        Innovation edited = service.update(detected.getId(), edit(null, InnovationType.TECHNICZNA, 6, null), analyst);

        assertThat(edited.isEditedByAnalyst()).isTrue();
        assertThat(edited.getEstimated()).doesNotContain("content");
    }
}
