package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.*;

import java.time.Instant;
import java.util.*;

/**
 * Weryfikacja innowacji znalezionych przez Radar: analityk PCI poprawia kartę, zatwierdza ocenę,
 * wskazuje zastosowania albo odrzuca rekord. Każda zmiana trafia na oś czasu.
 */
@Service
@Transactional
public class InnovationService {

    private final InnovationRepository innovations;
    private final InnovationEventRepository events;
    private final InstitutionRepository institutions;
    private final AssessmentRepository assessments;
    private final AssessmentService assessmentService;

    public InnovationService(InnovationRepository innovations, InnovationEventRepository events,
                             InstitutionRepository institutions, AssessmentRepository assessments,
                             AssessmentService assessmentService) {
        this.innovations = innovations;
        this.events = events;
        this.institutions = institutions;
        this.assessments = assessments;
        this.assessmentService = assessmentService;
    }

    /** Pola karty, które analityk może poprawić po imporcie. */
    public record CardEdit(String title, String summary, String problem, String solution, String requirements,
                           String results, InnovationType type, Sector sector, Maturity maturity,
                           TransferModel transferModel, String ipStatus, List<String> tags, List<String> links,
                           Long institutionId, String sourceAuthors, Integer trl, Area area) {
    }

    @Transactional(readOnly = true)
    public Innovation get(Long id) {
        return innovations.findById(id).orElseThrow(() -> DomainException.notFound("Nie ma innowacji o id " + id));
    }

    /** Rekordy odrzucone, połączone i archiwalne widzi tylko zespół PCI. */
    @Transactional(readOnly = true)
    public boolean canView(Innovation inn, Person viewer) {
        return inn.getStatus().isPubliclyVisible() || isStaff(viewer);
    }

    public boolean isStaff(Person p) {
        return p != null && p.hasRole(Role.EKSPERT, Role.ADMIN);
    }

    public record Completeness(int percent, List<String> missing) {
    }

    public Completeness completeness(Innovation inn) {
        Map<String, Boolean> fields = new LinkedHashMap<>();
        fields.put("Tytuł", notBlank(inn.getTitle()));
        fields.put("Krótki opis", notBlank(inn.getSummary()));
        fields.put("Problem", notBlank(inn.getProblem()));
        fields.put("Rozwiązanie", notBlank(inn.getSolution()));
        fields.put("Typ", inn.getType() != null);
        fields.put("Sektor", inn.getSector() != null);
        fields.put("Etap wdrożenia", inn.getMaturity() != null);
        fields.put("Instytucja", inn.getInstitution() != null);
        fields.put("Źródło", notBlank(inn.getSourceName()) || !inn.getLinks().isEmpty());
        List<String> missing = fields.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();
        int pct = (int) Math.round(100.0 * (fields.size() - missing.size()) / fields.size());
        return new Completeness(pct, missing);
    }

    public Innovation update(Long id, CardEdit f, Person actor) {
        requireStaff(actor);
        Innovation inn = get(id);
        // opis poprawiony przez analityka przestaje być szacunkiem automatu
        boolean contentChanged = !Objects.equals(nz(inn.getSummary()), nz(trimTo(f.summary(), 500)))
                || !Objects.equals(nz(inn.getProblem()), nz(trimTo(f.problem(), 4000)))
                || !Objects.equals(nz(inn.getSolution()), nz(trimTo(f.solution(), 4000)))
                || !Objects.equals(nz(inn.getResults()), nz(trimTo(f.results(), 2000)));
        if (contentChanged) {
            inn.getEstimated().remove("content");
        }
        // od teraz skan Radaru nie nadpisuje treści tej karty
        inn.setEditedByAnalyst(true);
        if (f.title() != null && !f.title().isBlank()) inn.setTitle(f.title().trim());
        inn.setSummary(trimTo(f.summary(), 500));
        inn.setProblem(trimTo(f.problem(), 4000));
        inn.setSolution(trimTo(f.solution(), 4000));
        inn.setRequirements(trimTo(f.requirements(), 2000));
        inn.setResults(trimTo(f.results(), 2000));
        if (f.trl() != null && (f.trl() < 1 || f.trl() > 9)) {
            throw DomainException.badRequest("TRL musi być w skali 1–9.");
        }
        // pole, które analityk zmienił, przestaje być szacunkiem automatu
        confirmIfChanged(inn, "type", inn.getType(), f.type());
        confirmIfChanged(inn, "sector", inn.getSector(), f.sector());
        confirmIfChanged(inn, "maturity", inn.getMaturity(), f.maturity());
        confirmIfChanged(inn, "trl", inn.getTrl(), f.trl());
        // brak obszaru w formularzu = bez zmian, więc nie potwierdza szacunku
        if (f.area() != null) {
            confirmIfChanged(inn, "area", inn.getArea(), f.area());
            inn.setArea(f.area());
        }
        inn.setType(f.type());
        inn.setSector(f.sector());
        inn.setMaturity(f.maturity());
        inn.setTrl(f.trl());
        inn.setTransferModel(f.transferModel());
        inn.setIpStatus(f.ipStatus());
        inn.setSourceAuthors(trimTo(f.sourceAuthors(), 255));
        if (f.tags() != null) {
            inn.getTags().clear();
            f.tags().stream().map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase).forEach(inn.getTags()::add);
        }
        if (f.links() != null) {
            inn.getLinks().clear();
            f.links().stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(inn.getLinks()::add);
        }
        if (f.institutionId() != null) {
            inn.setInstitution(institutions.findById(f.institutionId())
                    .orElseThrow(() -> DomainException.badRequest("Nieznana instytucja.")));
        }
        inn.touch();
        log(inn, EventType.EDYCJA, "Analityk poprawił kartę innowacji.", actor);
        // niezatwierdzona ocena automatyczna jest liczona od nowa z poprawionych danych
        assessmentService.autoAssess(inn);
        return inn;
    }

    /** System liczy propozycję oceny; analityk ją potem zatwierdza lub poprawia. */
    public Innovation proposeAssessment(Long id, Person actor) {
        requireStaff(actor);
        Innovation inn = get(id);
        if (inn.getStatus() == InnovationStatus.W_OCENIE) {
            throw DomainException.badRequest("Propozycja oceny już czeka na zatwierdzenie.");
        }
        if (!inn.getStatus().isPubliclyVisible()) {
            throw DomainException.badRequest("Rekord jest wyłączony z katalogu.");
        }
        Assessment proposal = assessmentService.propose(inn);
        assessments.save(proposal);
        changeStatus(inn, InnovationStatus.W_OCENIE,
                "Wygenerowano propozycję oceny: " + Math.round(proposal.proposedTotal()) + "/100.", actor);
        return inn;
    }

    public record ScoreInput(Criterion criterion, int score, String justification) {
    }

    public Innovation approveAssessment(Long id, List<ScoreInput> scores, Person actor) {
        requireStaff(actor);
        Innovation inn = get(id);
        if (inn.getStatus() != InnovationStatus.W_OCENIE) {
            throw DomainException.badRequest("Zatwierdzić można tylko innowację w ocenie.");
        }
        Assessment a = assessmentService.latest(inn).orElseThrow(() -> DomainException.badRequest("Brak propozycji oceny."));
        int changed = 0;
        for (CriterionScore cs : a.getScores()) {
            Optional<ScoreInput> in = scores == null ? Optional.empty()
                    : scores.stream().filter(s -> s.criterion() == cs.getCriterion()).findFirst();
            int value = in.map(ScoreInput::score).orElse(cs.getProposedScore());
            if (value < 1 || value > 5) {
                throw DomainException.badRequest("Ocena musi być w skali 1–5.");
            }
            if (value != cs.getProposedScore()) {
                changed++;
            }
            cs.setFinalScore(value);
            cs.setFinalJustification(in.map(ScoreInput::justification).filter(InnovationService::notBlank)
                    .orElse(cs.getProposedJustification()));
        }
        a.setApprovedBy(actor);
        a.setApprovedAt(Instant.now());
        // zatwierdzając ocenę analityk potwierdza też klasyfikację karty
        inn.getEstimated().clear();
        changeStatus(inn, InnovationStatus.OCENIONA, "Analityk zatwierdził ocenę: " + Math.round(a.total()) + "/100"
                + (changed > 0 ? " (poprawiono kryteria: " + changed + ")." : " (bez zmian względem propozycji)."), actor);
        return inn;
    }

    private static void confirmIfChanged(Innovation inn, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            inn.getEstimated().remove(field);
        }
    }

    public Innovation choosePaths(Long id, List<PathType> paths, Person actor) {
        requireStaff(actor);
        Innovation inn = get(id);
        if (paths == null || paths.isEmpty()) {
            throw DomainException.badRequest("Wybierz co najmniej jedno zastosowanie.");
        }
        if (inn.getStatus() != InnovationStatus.OCENIONA) {
            throw DomainException.badRequest("Zastosowania wskazuje się po zatwierdzeniu oceny.");
        }
        inn.getChosenPaths().clear();
        inn.getChosenPaths().addAll(paths);
        inn.touch();
        log(inn, EventType.SCIEZKA, "Wskazane zastosowania: "
                + String.join(", ", paths.stream().map(PathType::getLabel).toList()) + ".", actor);
        return inn;
    }

    /** Rekord, który nie jest innowacją w rozumieniu Oslo Manual, wypada z katalogu. */
    public Innovation reject(Long id, String reason, Person actor) {
        requireStaff(actor);
        Innovation inn = get(id);
        inn.setAutoRejected(false);
        changeStatus(inn, InnovationStatus.ODRZUCONA,
                notBlank(reason) ? reason.trim() : "Nie spełnia definicji innowacji.", actor);
        return inn;
    }

    /** Analityk nie zgadza się z odrzuceniem (np. automatycznym) – rekord wraca do katalogu jako „Wykryta”. */
    public Innovation restore(Long id, Person actor) {
        requireStaff(actor);
        Innovation inn = get(id);
        if (inn.getStatus() != InnovationStatus.ODRZUCONA) {
            throw DomainException.badRequest("Przywrócić można tylko odrzucony rekord.");
        }
        inn.setAutoRejected(false);
        changeStatus(inn, InnovationStatus.WYKRYTA, "Analityk przywrócił rekord do katalogu.", actor);
        assessmentService.autoAssess(inn);
        return inn;
    }

    public void changeStatus(Innovation inn, InnovationStatus status, String message, Person actor) {
        inn.setStatus(status);
        inn.touch();
        log(inn, EventType.STATUS, status.getLabel() + ": " + message, actor);
    }

    public void log(Innovation inn, EventType type, String message, Person actor) {
        events.save(new InnovationEvent(inn, type, message, actor));
    }

    private void requireStaff(Person actor) {
        if (actor == null) {
            throw DomainException.forbidden("Zaloguj się kontem zespołu PCI.");
        }
        if (!isStaff(actor)) {
            throw DomainException.forbidden("Ta akcja wymaga roli: Analityk PCI / Administrator.");
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimTo(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
