package pl.trackidea.web;

import pl.trackidea.domain.*;
import pl.trackidea.service.InnovationService;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Obiekty zwracane przez REST API (encje nie wychodzą poza backend). */
public final class Dto {

    private Dto() {
    }

    public record EnumValue(String value, String label) {
        public static EnumValue of(Labeled e) {
            return e == null ? null : new EnumValue(e.name(), e.getLabel());
        }
    }

    public record Ref(Long id, String name) {
        public static Ref of(Person p) {
            return p == null ? null : new Ref(p.getId(), p.getName());
        }

        public static Ref of(Innovation i) {
            return i == null ? null : new Ref(i.getId(), i.getTitle());
        }
    }

    public record InstitutionView(Long id, String name, EnumValue type, String city, String county, String region,
                                  boolean publicSector) {
        public static InstitutionView of(Institution i) {
            return i == null ? null : new InstitutionView(i.getId(), i.getName(), EnumValue.of(i.getType()), i.getCity(),
                    i.getCounty(), i.getRegion(), i.getType().isPublicSector());
        }
    }

    public record PersonView(Long id, String name, EnumValue role, InstitutionView institution, String headline,
                             String bio, String github, String youtube, String linkedin, List<EnumValue> interestSectors,
                             Set<String> interestTags, List<EnumValue> seekingStages) {
        public static PersonView of(Person p) {
            return p == null ? null : new PersonView(p.getId(), p.getName(), EnumValue.of(p.getRole()),
                    InstitutionView.of(p.getInstitution()), p.getHeadline(), p.getBio(), p.getGithub(), p.getYoutube(),
                    p.getLinkedin(), p.getInterestSectors().stream().map(EnumValue::of).toList(), p.getInterestTags(),
                    p.getSeekingStages().stream().map(EnumValue::of).toList());
        }
    }

    public record StatusView(String value, String label, int step, String description, boolean draft) {
        public static StatusView of(InnovationStatus s) {
            return new StatusView(s.name(), s.getLabel(), s.getStep(), s.getDescription(), s.isDraft());
        }
    }

    public record InnovationCard(Long id, String title, String summary, EnumValue type, EnumValue sector,
                                 EnumValue maturity, EnumValue transferModel, StatusView status,
                                 InstitutionView institution, List<Ref> authors, Set<String> tags, Double score,
                                 long votes, long followers, long forks, long deployments, Instant createdAt,
                                 EnumValue source, String sourceName, Ref forkedFrom) {
    }

    public record CriterionView(String value, String label, int weight, String question, String low, String high) {
        public static CriterionView of(Criterion c) {
            return new CriterionView(c.name(), c.getLabel(), c.getWeight(), c.getQuestion(), c.getLow(), c.getHigh());
        }
    }

    public record ScoreView(CriterionView criterion, int proposedScore, String proposedJustification,
                            Integer finalScore, String finalJustification) {
    }

    public record AssessmentView(Long id, boolean approved, Ref approvedBy, Instant createdAt, Instant approvedAt,
                                 double total, double proposedTotal, List<ScoreView> scores, boolean preview) {
        public static AssessmentView of(Assessment a, boolean preview) {
            if (a == null) {
                return null;
            }
            return new AssessmentView(a.getId(), a.isApproved(), Ref.of(a.getApprovedBy()), a.getCreatedAt(),
                    a.getApprovedAt(), a.total(), a.proposedTotal(),
                    a.getScores().stream().map(s -> new ScoreView(CriterionView.of(s.getCriterion()), s.getProposedScore(),
                            s.getProposedJustification(), s.getFinalScore(), s.getFinalJustification())).toList(),
                    preview);
        }
    }

    public record EventView(Long id, EnumValue type, String message, Ref actor, StatusView statusAfter,
                            Instant createdAt) {
        public static EventView of(InnovationEvent e) {
            return new EventView(e.getId(), EnumValue.of(e.getType()), e.getMessage(), Ref.of(e.getActor()),
                    e.getStatusAfter() == null ? null : StatusView.of(e.getStatusAfter()), e.getCreatedAt());
        }
    }

    public record ForkView(Long id, InstitutionView institution, StatusView status, Instant createdAt) {
    }

    public record Permissions(boolean canEdit, boolean canConfirm, boolean canRequestAssessment, boolean canApprove,
                              boolean canChoosePaths, boolean canFork, boolean canConfirmDeployment,
                              boolean canContact, boolean voted, boolean following) {
    }

    public record InnovationDetail(InnovationCard card, String problem, String solution, String requirements,
                                   String results, String ipStatus, Set<String> links, Ref reportedBy,
                                   String sourceAuthors, String sourceRef, InnovationService.Completeness completeness,
                                   AssessmentView assessment, List<EnumValue> chosenPaths, List<ForkView> forks,
                                   List<EventView> events, Permissions viewer, Ref mergedInto) {
    }

    public record NeedView(Long id, String title, String description, InstitutionView institution, Ref author,
                           EnumValue sector, Set<String> tags, boolean wantsReadySolution, boolean open,
                           Instant createdAt) {
        public static NeedView of(Need n) {
            return new NeedView(n.getId(), n.getTitle(), n.getDescription(), InstitutionView.of(n.getInstitution()),
                    Ref.of(n.getAuthor()), EnumValue.of(n.getSector()), n.getTags(), n.isWantsReadySolution(), n.isOpen(),
                    n.getCreatedAt());
        }
    }

    public record ContactView(Long id, Ref innovation, PersonView from, PersonView to, String message,
                              EnumValue status, Instant createdAt, Instant answeredAt, String fromEmail,
                              String toEmail) {
        /** Adresy e-mail są widoczne dopiero po akceptacji prośby (RODO). */
        public static ContactView of(ContactRequest c) {
            boolean accepted = c.getStatus() == ContactStatus.ZAAKCEPTOWANA;
            return new ContactView(c.getId(), Ref.of(c.getInnovation()), PersonView.of(c.getFromPerson()),
                    PersonView.of(c.getToPerson()), c.getMessage(), EnumValue.of(c.getStatus()), c.getCreatedAt(),
                    c.getAnsweredAt(), accepted ? c.getFromPerson().getEmail() : null,
                    accepted ? c.getToPerson().getEmail() : null);
        }
    }

    public record RadarRecordView(Long id, String source, String ref, String title, String institutionName,
                                  String outcome, Ref innovation, Ref duplicateOf, double duplicateSimilarity,
                                  Instant importedAt) {
        public static RadarRecordView of(RadarRecord r) {
            return new RadarRecordView(r.getId(), r.getSource(), r.getRef(), r.getTitle(), r.getInstitutionName(),
                    r.getOutcome().name(), Ref.of(r.getInnovation()), Ref.of(r.getDuplicateOf()),
                    r.getDuplicateSimilarity(), r.getImportedAt());
        }
    }
}
