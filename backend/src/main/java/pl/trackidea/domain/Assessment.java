package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Innovation innovation;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn
    private List<CriterionScore> scores = new ArrayList<>();

    @ManyToOne
    private Person approvedBy;

    private Instant createdAt = Instant.now();
    private Instant approvedAt;

    public boolean isApproved() {
        return approvedAt != null;
    }

    /**
     * Wynik merytoryczny = Σ waga × (ocena − 1) / 4 × 100, podzielony przez sumę wag użytych kryteriów.
     * Gdy kryterium nie ma zastosowania (TRL poza technologiami), pozostałe wagi zachowują proporcje.
     */
    public double total() {
        return total(false);
    }

    public double proposedTotal() {
        return total(true);
    }

    private double total(boolean proposedOnly) {
        double sum = 0;
        double weights = 0;
        for (CriterionScore s : scores) {
            int score = proposedOnly ? s.getProposedScore() : s.effectiveScore();
            sum += s.getCriterion().getWeight() * (score - 1) / 4.0;
            weights += s.getCriterion().getWeight();
        }
        return weights == 0 ? 0 : Math.round(sum / weights * 1000) / 10.0;
    }

    public int score(Criterion c) {
        return scores.stream().filter(s -> s.getCriterion() == c).mapToInt(CriterionScore::effectiveScore).findFirst().orElse(3);
    }
}
