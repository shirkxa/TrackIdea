package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.*;

import java.time.Instant;
import java.util.*;

/**
 * Cykl życia innowacji: zgłoszenie, potwierdzenie, uzupełnienie, ocena, ścieżka, transfer, wdrożenie.
 * Każde przejście sprawdza uprawnienia i zapisuje zdarzenie na osi czasu.
 */
@Service
@Transactional
public class InnovationService {

    private final InnovationRepository innovations;
    private final InnovationEventRepository events;
    private final InstitutionRepository institutions;
    private final AssessmentRepository assessments;
    private final AssessmentService assessmentService;
    private final VoteRepository votes;
    private final FollowRepository follows;
    private final ContactRequestRepository contacts;
    private final PersonRepository people;

    public InnovationService(InnovationRepository innovations, InnovationEventRepository events,
                             InstitutionRepository institutions, AssessmentRepository assessments,
                             AssessmentService assessmentService, VoteRepository votes, FollowRepository follows,
                             ContactRequestRepository contacts, PersonRepository people) {
        this.innovations = innovations;
        this.events = events;
        this.institutions = institutions;
        this.assessments = assessments;
        this.assessmentService = assessmentService;
        this.votes = votes;
        this.follows = follows;
        this.contacts = contacts;
        this.people = people;
    }

    // ------------------------------------------------------------------ odczyt

    @Transactional(readOnly = true)
    public Innovation get(Long id) {
        return innovations.findById(id).orElseThrow(() -> DomainException.notFound("Nie ma innowacji o id " + id));
    }

    /** Szkice widzi tylko autor, zwiadowca, opiekun instytucji i PCI. */
    @Transactional(readOnly = true)
    public boolean canView(Innovation inn, Person viewer) {
        if (inn.getStatus().isPubliclyVisible() || inn.getStatus() == InnovationStatus.POLACZONA) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        return canEdit(inn, viewer) || (inn.getReportedBy() != null && inn.getReportedBy().getId().equals(viewer.getId()))
                || (viewer.getRole() == Role.TWORCA && inn.getSource() == Source.RADAR);
    }

    public boolean canEdit(Innovation inn, Person p) {
        if (p == null) {
            return false;
        }
        if (p.hasRole(Role.EKSPERT, Role.ADMIN) || inn.isAuthor(p)) {
            return true;
        }
        return p.getRole() == Role.OPIEKUN && p.getInstitution() != null && inn.getInstitution() != null
                && p.getInstitution().getId().equals(inn.getInstitution().getId());
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
        fields.put("Etap dojrzałości", inn.getMaturity() != null);
        fields.put("Model transferu", inn.getTransferModel() != null);
        fields.put("Instytucja", inn.getInstitution() != null);
        fields.put("Autor z kontem", !inn.getAuthors().isEmpty());
        List<String> missing = fields.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();
        int pct = (int) Math.round(100.0 * (fields.size() - missing.size()) / fields.size());
        return new Completeness(pct, missing);
    }

    // ------------------------------------------------------------------ zgłoszenie i edycja

    public Innovation create(InnovationForm form, Person actor) {
        requireActor(actor);
        if (!notBlank(form.title())) {
            throw DomainException.badRequest("Podaj tytuł innowacji.");
        }
        Innovation inn = new Innovation();
        apply(inn, form);
        if (form.institutionId() == null && actor.getInstitution() != null) {
            inn.setInstitution(actor.getInstitution());
        }
        if (Boolean.TRUE.equals(form.onBehalf())) {
            inn.setSource(Source.ZWIADOWCA);
            inn.setReportedBy(actor);
            inn.setSourceAuthors(form.authorInfo());
            inn.setStatus(InnovationStatus.ZGLOSZONA);
        } else {
            inn.getAuthors().add(actor);
            inn.setSource(Source.ZGLOSZENIE_WLASNE);
            // autor zgłaszający własny pomysł od razu wyraża zgodę na publikację
            inn.setStatus(InnovationStatus.POTWIERDZONA);
        }
        innovations.save(inn);
        log(inn, EventType.UTWORZONO, Boolean.TRUE.equals(form.onBehalf())
                ? "Zgłoszone przez zwiadowcę (" + actor.getName() + "). Czeka na potwierdzenie autora."
                : "Zgłoszone przez autora.", actor);
        autoComplete(inn, actor);
        return inn;
    }

    public Innovation update(Long id, InnovationForm form, Person actor) {
        Innovation inn = get(id);
        if (!canEdit(inn, actor)) {
            throw DomainException.forbidden("Kartę może edytować autor, opiekun instytucji lub ekspert PCI.");
        }
        apply(inn, form);
        inn.touch();
        log(inn, EventType.EDYCJA, "Zaktualizowano kartę innowacji.", actor);
        autoComplete(inn, actor);
        return inn;
    }

    private void apply(Innovation inn, InnovationForm f) {
        if (f.title() != null) inn.setTitle(f.title().trim());
        inn.setSummary(trimTo(f.summary(), 500));
        inn.setProblem(trimTo(f.problem(), 4000));
        inn.setSolution(trimTo(f.solution(), 4000));
        inn.setRequirements(trimTo(f.requirements(), 2000));
        inn.setResults(trimTo(f.results(), 2000));
        inn.setType(f.type());
        inn.setSector(f.sector());
        inn.setMaturity(f.maturity());
        inn.setTransferModel(f.transferModel());
        inn.setIpStatus(f.ipStatus());
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
    }

    /** POTWIERDZONA → UZUPEŁNIONA, gdy karta ma komplet pól. */
    private void autoComplete(Innovation inn, Person actor) {
        if (inn.getStatus() == InnovationStatus.POTWIERDZONA && completeness(inn).percent() == 100) {
            changeStatus(inn, InnovationStatus.UZUPELNIONA, "Karta kompletna – można poprosić o ocenę.", actor);
        }
    }

    // ------------------------------------------------------------------ przejścia statusów

    public Innovation confirm(Long id, Person actor) {
        requireActor(actor);
        Innovation inn = get(id);
        if (!inn.getStatus().isDraft()) {
            throw DomainException.badRequest("Innowacja jest już potwierdzona.");
        }
        boolean claimsAuthorship = actor.getRole() == Role.TWORCA && !inn.isAuthor(actor);
        if (!claimsAuthorship && !canEdit(inn, actor)) {
            throw DomainException.forbidden("Potwierdzić może autor albo opiekun instytucji.");
        }
        if (claimsAuthorship) {
            inn.getAuthors().add(actor);
        }
        changeStatus(inn, InnovationStatus.POTWIERDZONA, actor.getName()
                + (claimsAuthorship ? " potwierdza autorstwo i zgodę na publikację." : " potwierdza zgłoszenie."), actor);
        autoComplete(inn, actor);
        return inn;
    }

    public Innovation requestAssessment(Long id, Person actor) {
        requireActor(actor);
        Innovation inn = get(id);
        if (!canEdit(inn, actor)) {
            throw DomainException.forbidden("O ocenę prosi autor lub opiekun instytucji.");
        }
        Completeness c = completeness(inn);
        if (c.percent() < 100) {
            throw DomainException.badRequest("Uzupełnij kartę przed oceną. Brakuje: " + String.join(", ", c.missing()));
        }
        if (inn.getStatus().getStep() < InnovationStatus.UZUPELNIONA.getStep() || inn.getStatus() == InnovationStatus.W_OCENIE) {
            throw DomainException.badRequest("Ocena jest możliwa dla uzupełnionej karty.");
        }
        Assessment proposal = assessmentService.propose(inn);
        assessments.save(proposal);
        changeStatus(inn, InnovationStatus.W_OCENIE,
                "Wygenerowano propozycję oceny: " + fmt(proposal.proposedTotal()) + "/100. Czeka na eksperta PCI.", actor);
        return inn;
    }

    public record ScoreInput(Criterion criterion, int score, String justification) {
    }

    public Innovation approveAssessment(Long id, List<ScoreInput> scores, Person actor) {
        requireRole(actor, Role.EKSPERT, Role.ADMIN);
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
        changeStatus(inn, InnovationStatus.OCENIONA, "Ekspert zatwierdził ocenę: " + fmt(a.total()) + "/100"
                + (changed > 0 ? " (poprawiono kryteria: " + changed + ")." : " (bez zmian względem propozycji)."), actor);
        return inn;
    }

    public Innovation choosePaths(Long id, List<PathType> paths, Person actor) {
        requireRole(actor, Role.EKSPERT, Role.ADMIN);
        Innovation inn = get(id);
        if (paths == null || paths.isEmpty()) {
            throw DomainException.badRequest("Wybierz co najmniej jedną ścieżkę.");
        }
        if (inn.getStatus().getStep() < InnovationStatus.OCENIONA.getStep()) {
            throw DomainException.badRequest("Ścieżkę wskazuje się po zatwierdzeniu oceny.");
        }
        inn.getChosenPaths().clear();
        inn.getChosenPaths().addAll(paths);
        String names = String.join(", ", paths.stream().map(PathType::getLabel).toList());
        if (inn.getStatus() == InnovationStatus.OCENIONA) {
            changeStatus(inn, InnovationStatus.SCIEZKA_WSKAZANA, "Zatwierdzona ścieżka: " + names + ".", actor);
        } else {
            log(inn, EventType.SCIEZKA, "Zmieniono ścieżkę: " + names + ".", actor);
        }
        return inn;
    }

    /** Fork = instytucja wdraża innowację u siebie. */
    public Innovation fork(Long id, Person actor) {
        requireActor(actor);
        Innovation parent = get(id);
        if (actor.getInstitution() == null || !actor.hasRole(Role.OPIEKUN, Role.ADMIN)) {
            throw DomainException.forbidden("Fork wykonuje opiekun instytucji, która chce wdrożyć rozwiązanie.");
        }
        if (!parent.getStatus().isPubliclyVisible()) {
            throw DomainException.badRequest("Nie można forkować niepotwierdzonej innowacji.");
        }
        if (parent.getInstitution() != null && parent.getInstitution().getId().equals(actor.getInstitution().getId())) {
            throw DomainException.badRequest("To innowacja Waszej instytucji.");
        }
        boolean exists = innovations.findByForkedFrom(parent).stream()
                .anyMatch(f -> f.getInstitution() != null && f.getInstitution().getId().equals(actor.getInstitution().getId()));
        if (exists) {
            throw DomainException.badRequest("Wasza instytucja już wdraża tę innowację.");
        }
        Innovation fork = new Innovation();
        fork.setTitle(parent.getTitle());
        fork.setSummary("Wdrożenie w: " + actor.getInstitution().getName() + ". " + nz(parent.getSummary()));
        fork.setProblem(parent.getProblem());
        fork.setSolution(parent.getSolution());
        fork.setRequirements(parent.getRequirements());
        fork.setType(parent.getType());
        fork.setSector(parent.getSector());
        fork.setMaturity(parent.getMaturity());
        fork.setTransferModel(parent.getTransferModel());
        fork.getTags().addAll(parent.getTags());
        fork.setInstitution(actor.getInstitution());
        fork.getAuthors().add(actor);
        fork.setSource(Source.FORK);
        fork.setForkedFrom(parent);
        fork.setStatus(InnovationStatus.W_TRANSFERZE);
        innovations.save(fork);
        log(fork, EventType.FORK, "Rozpoczęto wdrożenie na podstawie „" + parent.getTitle() + "” ("
                + instName(parent.getInstitution()) + ").", actor);
        log(parent, EventType.FORK, actor.getInstitution().getName() + " rozpoczyna wdrożenie u siebie (fork).", actor);
        if (parent.getStatus() == InnovationStatus.SCIEZKA_WSKAZANA || parent.getStatus() == InnovationStatus.OCENIONA) {
            changeStatus(parent, InnovationStatus.W_TRANSFERZE, "Trwa transfer do innej instytucji.", actor);
        }
        return fork;
    }

    public Innovation confirmDeployment(Long forkId, Person actor) {
        requireActor(actor);
        Innovation fork = get(forkId);
        if (fork.getForkedFrom() == null) {
            throw DomainException.badRequest("Potwierdzenie wdrożenia dotyczy forka.");
        }
        if (!canEdit(fork, actor)) {
            throw DomainException.forbidden("Wdrożenie potwierdza instytucja, która je przeprowadziła.");
        }
        if (fork.getStatus() == InnovationStatus.WDROZONA) {
            throw DomainException.badRequest("Wdrożenie jest już potwierdzone.");
        }
        changeStatus(fork, InnovationStatus.WDROZONA, "Potwierdzono wdrożenie w " + instName(fork.getInstitution()) + ".", actor);
        Innovation parent = fork.getForkedFrom();
        long deployed = innovations.countByForkedFromAndStatus(parent, InnovationStatus.WDROZONA);
        String msg = "Potwierdzone wdrożenie w " + instName(fork.getInstitution()) + ". Działa już w "
                + (deployed + 1) + " instytucjach.";
        if (parent.getStatus() != InnovationStatus.WDROZONA) {
            changeStatus(parent, InnovationStatus.WDROZONA, msg, actor);
        } else {
            log(parent, EventType.WDROZENIE, msg, actor);
        }
        return fork;
    }

    // ------------------------------------------------------------------ społeczność i kontakt

    public boolean toggleVote(Long id, Person actor) {
        requireActor(actor);
        Innovation inn = get(id);
        Optional<Vote> v = votes.findByInnovationAndPerson(inn, actor);
        if (v.isPresent()) {
            votes.delete(v.get());
            return false;
        }
        votes.save(new Vote(inn, actor));
        return true;
    }

    public boolean toggleFollow(Long id, Person actor) {
        requireActor(actor);
        Innovation inn = get(id);
        Optional<Follow> f = follows.findByInnovationAndPerson(inn, actor);
        if (f.isPresent()) {
            follows.delete(f.get());
            return false;
        }
        follows.save(new Follow(inn, actor));
        return true;
    }

    public ContactRequest requestContact(Long id, String message, Person actor) {
        requireActor(actor);
        Innovation inn = get(id);
        Person to = inn.getAuthors().stream().findFirst()
                .or(() -> inn.getInstitution() == null ? Optional.empty()
                        : people.findByInstitutionAndRole(inn.getInstitution(), Role.OPIEKUN).stream().findFirst())
                .orElseThrow(() -> DomainException.badRequest("Innowacja nie ma jeszcze autora z kontem."));
        if (to.getId().equals(actor.getId())) {
            throw DomainException.badRequest("To Twoja innowacja.");
        }
        if (contacts.existsByInnovationAndFromPersonAndStatusNot(inn, actor, ContactStatus.ODRZUCONA)) {
            throw DomainException.badRequest("Prośba o kontakt została już wysłana.");
        }
        ContactRequest cr = new ContactRequest();
        cr.setInnovation(inn);
        cr.setFromPerson(actor);
        cr.setToPerson(to);
        cr.setMessage(notBlank(message) ? message.trim() : "Dzień dobry, chcielibyśmy porozmawiać o tej innowacji.");
        contacts.save(cr);
        log(inn, EventType.KONTAKT, actor.getName() + " (" + actor.getRole().getLabel() + ") prosi o kontakt.", actor);
        return cr;
    }

    public ContactRequest answerContact(Long contactId, boolean accept, Person actor) {
        requireActor(actor);
        ContactRequest cr = contacts.findById(contactId).orElseThrow(() -> DomainException.notFound("Brak prośby."));
        if (!cr.getToPerson().getId().equals(actor.getId())) {
            throw DomainException.forbidden("Na prośbę odpowiada jej adresat.");
        }
        if (cr.getStatus() != ContactStatus.WYSLANA) {
            throw DomainException.badRequest("Na tę prośbę już odpowiedziano.");
        }
        cr.setStatus(accept ? ContactStatus.ZAAKCEPTOWANA : ContactStatus.ODRZUCONA);
        cr.setAnsweredAt(Instant.now());
        Innovation inn = cr.getInnovation();
        if (accept) {
            String msg = "Nawiązano kontakt: " + cr.getFromPerson().getName() + ".";
            if (inn.getStatus() == InnovationStatus.SCIEZKA_WSKAZANA) {
                changeStatus(inn, InnovationStatus.W_TRANSFERZE, msg, actor);
            } else {
                log(inn, EventType.KONTAKT, msg, actor);
            }
        }
        return cr;
    }

    // ------------------------------------------------------------------ pomocnicze

    public void changeStatus(Innovation inn, InnovationStatus status, String message, Person actor) {
        inn.setStatus(status);
        inn.touch();
        log(inn, EventType.STATUS, status.getLabel() + ": " + message, actor);
    }

    public void log(Innovation inn, EventType type, String message, Person actor) {
        events.save(new InnovationEvent(inn, type, message, actor));
    }

    private static void requireActor(Person actor) {
        if (actor == null) {
            throw DomainException.forbidden("Wybierz użytkownika demo (przełącznik ról w nagłówku).");
        }
    }

    private static void requireRole(Person actor, Role... roles) {
        requireActor(actor);
        if (!actor.hasRole(roles)) {
            throw DomainException.forbidden("Ta akcja wymaga roli: "
                    + String.join(" / ", Arrays.stream(roles).map(Role::getLabel).toList()) + ".");
        }
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String instName(Institution i) {
        return i == null ? "?" : i.getName();
    }

    private static String fmt(double d) {
        return String.valueOf(Math.round(d));
    }
}
