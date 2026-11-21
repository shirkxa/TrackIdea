package pl.trackidea.web;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;
import pl.trackidea.repo.InnovationEventRepository;
import pl.trackidea.service.AssessmentService;
import pl.trackidea.service.InnovationService;

import java.util.List;
import java.util.Optional;

/** Składa widoki innowacji (karty, szczegóły) razem z uprawnieniami oglądającego. */
@Component
@Transactional(readOnly = true)
public class Views {

    private final InnovationEventRepository events;
    private final AssessmentService assessmentService;
    private final InnovationService innovationService;

    public Views(InnovationEventRepository events, AssessmentService assessmentService,
                 InnovationService innovationService) {
        this.events = events;
        this.assessmentService = assessmentService;
        this.innovationService = innovationService;
    }

    public Dto.InnovationCard card(Innovation i) {
        // zatwierdzona ocena analityka albo ocena automatyczna (szacunek)
        Optional<Assessment> latest = assessmentService.latest(i);
        Double score = latest.map(a -> a.isApproved() ? a.total() : a.proposedTotal()).orElse(null);
        boolean scoreEstimated = latest.map(a -> !a.isApproved()).orElse(false);
        return new Dto.InnovationCard(i.getId(), i.getTitle(), i.getSummary(), Dto.EnumValue.of(i.getArea()),
                Dto.EnumValue.of(i.getType()),
                Dto.EnumValue.of(i.getSector()), Dto.EnumValue.of(i.getMaturity()), Dto.EnumValue.of(i.getTransferModel()),
                Dto.StatusView.of(i.getStatus()), Dto.InstitutionView.of(i.getInstitution()), i.getSourceAuthors(),
                i.getTags(), score, i.getCreatedAt(), i.getUpdatedAt(), Dto.EnumValue.of(i.getSource()), i.getSourceName(),
                Dto.TrlView.of(i.getTrl()), i.getEstimated(), scoreEstimated, i.isAutoRejected());
    }

    public Dto.InnovationDetail detail(Innovation i, Person viewer) {
        boolean staff = innovationService.isStaff(viewer);
        // ocena widoczna dla wszystkich; niezatwierdzona jest oznaczona jako szacunek automatyczny
        Dto.AssessmentView assessment = assessmentService.latest(i)
                .map(a -> Dto.AssessmentView.of(a, false))
                .orElse(null);
        List<Dto.EventView> timeline = events.findByInnovationOrderByCreatedAtAscIdAsc(i).stream()
                .map(Dto.EventView::of).toList();
        boolean active = i.getStatus().isPubliclyVisible();
        Dto.Permissions perms = new Dto.Permissions(
                staff,
                staff && active && i.getStatus() != InnovationStatus.W_OCENIE,
                staff && i.getStatus() == InnovationStatus.W_OCENIE,
                staff && i.getStatus() == InnovationStatus.OCENIONA,
                staff && active,
                staff && i.getStatus() == InnovationStatus.ODRZUCONA);
        return new Dto.InnovationDetail(card(i), i.getProblem(), i.getSolution(), i.getRequirements(), i.getResults(),
                i.getIpStatus(), i.getLinks(), i.getSourceRef(), innovationService.completeness(i), assessment,
                i.getChosenPaths().stream().map(Dto.EnumValue::of).toList(), timeline, perms,
                Dto.Ref.of(i.getMergedInto()), i.getSourceYear(), i.getSourceFacts(), i.getAnalysisEngine());
    }
}
