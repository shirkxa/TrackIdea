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

    /** Konto zespołu PCI (bez danych kontaktowych). */
    public record PersonView(Long id, String name, EnumValue role, InstitutionView institution, String headline) {
        public static PersonView of(Person p) {
            return p == null ? null : new PersonView(p.getId(), p.getName(), EnumValue.of(p.getRole()),
                    InstitutionView.of(p.getInstitution()), p.getHeadline());
        }
    }

    public record StatusView(String value, String label, int step, String description) {
        public static StatusView of(InnovationStatus s) {
            return new StatusView(s.name(), s.getLabel(), s.getStep(), s.getDescription());
        }
    }

    public record InnovationCard(Long id, String title, String summary, EnumValue type, EnumValue sector,
                                 EnumValue maturity, EnumValue transferModel, StatusView status,
                                 InstitutionView institution, String authors, Set<String> tags, Double score,
                                 Instant createdAt, Instant updatedAt, EnumValue source, String sourceName) {
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

    /** Co zalogowany analityk może zrobić z tą kartą. */
    public record Permissions(boolean canEdit, boolean canPropose, boolean canApprove, boolean canChoosePaths,
                              boolean canReject) {
    }

    public record InnovationDetail(InnovationCard card, String problem, String solution, String requirements,
                                   String results, String ipStatus, Set<String> links, String sourceRef,
                                   InnovationService.Completeness completeness, AssessmentView assessment,
                                   List<EnumValue> chosenPaths, List<EventView> events, Permissions viewer,
                                   Ref mergedInto) {
    }

    public record NeedView(Long id, String title, String description, InstitutionView institution,
                           EnumValue sector, Set<String> tags, boolean wantsReadySolution, boolean open,
                           Instant createdAt) {
        public static NeedView of(Need n) {
            return new NeedView(n.getId(), n.getTitle(), n.getDescription(), InstitutionView.of(n.getInstitution()),
                    EnumValue.of(n.getSector()), n.getTags(), n.isWantsReadySolution(), n.isOpen(), n.getCreatedAt());
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
