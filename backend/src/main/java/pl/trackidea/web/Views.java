package pl.trackidea.web;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.*;
import pl.trackidea.service.AssessmentService;
import pl.trackidea.service.InnovationService;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Składa widoki innowacji (karty, szczegóły) razem z licznikami i uprawnieniami oglądającego. */
@Component
@Transactional(readOnly = true)
public class Views {

    private final VoteRepository votes;
    private final FollowRepository follows;
    private final InnovationRepository innovations;
    private final InnovationEventRepository events;
    private final AssessmentService assessmentService;
    private final InnovationService innovationService;
    private final ContactRequestRepository contacts;

    public Views(VoteRepository votes, FollowRepository follows, InnovationRepository innovations,
                 InnovationEventRepository events, AssessmentService assessmentService,
                 InnovationService innovationService, ContactRequestRepository contacts) {
        this.votes = votes;
        this.follows = follows;
        this.innovations = innovations;
        this.events = events;
        this.assessmentService = assessmentService;
        this.innovationService = innovationService;
        this.contacts = contacts;
    }

    public Dto.InnovationCard card(Innovation i) {
        List<Innovation> forks = innovations.findByForkedFrom(i);
        Double score = assessmentService.latest(i).filter(Assessment::isApproved).map(Assessment::total).orElse(null);
        return new Dto.InnovationCard(i.getId(), i.getTitle(), i.getSummary(), Dto.EnumValue.of(i.getType()),
                Dto.EnumValue.of(i.getSector()), Dto.EnumValue.of(i.getMaturity()), Dto.EnumValue.of(i.getTransferModel()),
                Dto.StatusView.of(i.getStatus()), Dto.InstitutionView.of(i.getInstitution()),
                i.getAuthors().stream().sorted(Comparator.comparing(Person::getId)).map(Dto.Ref::of).toList(), i.getTags(),
                score, votes.countByInnovation(i), follows.countByInnovation(i), forks.size(),
                forks.stream().filter(f -> f.getStatus() == InnovationStatus.WDROZONA).count(), i.getCreatedAt(),
                Dto.EnumValue.of(i.getSource()), i.getSourceName(), Dto.Ref.of(i.getForkedFrom()));
    }

    public Dto.InnovationDetail detail(Innovation i, Person viewer) {
        Optional<Assessment> latest = assessmentService.latest(i);
        boolean canEdit = innovationService.canEdit(i, viewer);
        boolean expert = viewer != null && viewer.hasRole(Role.EKSPERT, Role.ADMIN);
        // Propozycja w toku widoczna dla autora i eksperta; po zatwierdzeniu – dla wszystkich.
        Dto.AssessmentView assessment = latest
                .filter(a -> a.isApproved() || canEdit || expert)
                .map(a -> Dto.AssessmentView.of(a, false))
                .orElse(null);

        List<Dto.ForkView> forks = innovations.findByForkedFrom(i).stream()
                .map(f -> new Dto.ForkView(f.getId(), Dto.InstitutionView.of(f.getInstitution()),
                        Dto.StatusView.of(f.getStatus()), f.getCreatedAt()))
                .toList();
        List<Dto.EventView> timeline = events.findByInnovationOrderByCreatedAtAscIdAsc(i).stream()
                .map(Dto.EventView::of).toList();

        boolean isDraft = i.getStatus().isDraft();
        boolean canConfirm = isDraft && viewer != null
                && (canEdit || (viewer.getRole() == Role.TWORCA && !i.isAuthor(viewer)));
        boolean ownInstitution = viewer != null && viewer.getInstitution() != null && i.getInstitution() != null
                && viewer.getInstitution().getId().equals(i.getInstitution().getId());
        boolean alreadyForked = viewer != null && viewer.getInstitution() != null && innovations.findByForkedFrom(i).stream()
                .anyMatch(f -> f.getInstitution() != null && f.getInstitution().getId().equals(viewer.getInstitution().getId()));
        boolean canFork = viewer != null && viewer.hasRole(Role.OPIEKUN, Role.ADMIN) && viewer.getInstitution() != null
                && i.getStatus().isPubliclyVisible() && i.getForkedFrom() == null && !ownInstitution && !alreadyForked;
        boolean hasAuthorOrCaretaker = !i.getAuthors().isEmpty() || i.getInstitution() != null;
        boolean canContact = viewer != null && !i.isAuthor(viewer) && i.getStatus().isPubliclyVisible()
                && hasAuthorOrCaretaker && !contacts.existsByInnovationAndFromPersonAndStatusNot(i, viewer, ContactStatus.ODRZUCONA);

        Dto.Permissions perms = new Dto.Permissions(
                canEdit,
                canConfirm,
                canEdit && (i.getStatus() == InnovationStatus.UZUPELNIONA || i.getStatus() == InnovationStatus.OCENIONA
                        || i.getStatus() == InnovationStatus.SCIEZKA_WSKAZANA),
                expert && i.getStatus() == InnovationStatus.W_OCENIE,
                expert && i.getStatus().getStep() >= InnovationStatus.OCENIONA.getStep(),
                canFork,
                i.getForkedFrom() != null && canEdit && i.getStatus() != InnovationStatus.WDROZONA,
                canContact,
                viewer != null && votes.findByInnovationAndPerson(i, viewer).isPresent(),
                viewer != null && follows.findByInnovationAndPerson(i, viewer).isPresent());

        return new Dto.InnovationDetail(card(i), i.getProblem(), i.getSolution(), i.getRequirements(), i.getResults(),
                i.getIpStatus(), i.getLinks(), Dto.Ref.of(i.getReportedBy()), i.getSourceAuthors(), i.getSourceRef(),
                innovationService.completeness(i), assessment,
                i.getChosenPaths().stream().map(Dto.EnumValue::of).toList(), forks, timeline, perms,
                Dto.Ref.of(i.getMergedInto()));
    }
}
