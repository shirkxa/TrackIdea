package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ocena cząstkowa: propozycja automatyczna + ocena zatwierdzona przez eksperta. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class CriterionScore {

    @Enumerated(EnumType.STRING)
    private Criterion criterion;

    private int proposedScore;

    @Column(length = 1000)
    private String proposedJustification;

    private Integer finalScore;

    @Column(length = 1000)
    private String finalJustification;

    public CriterionScore(Criterion criterion, int proposedScore, String proposedJustification) {
        this.criterion = criterion;
        this.proposedScore = proposedScore;
        this.proposedJustification = proposedJustification;
    }

    public int effectiveScore() {
        return finalScore != null ? finalScore : proposedScore;
    }

    public String effectiveJustification() {
        return finalJustification != null && !finalJustification.isBlank() ? finalJustification : proposedJustification;
    }
}
