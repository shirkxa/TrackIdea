package pl.trackidea.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Wynik merytoryczny = Σ waga × (ocena − 1) / 4 × 100, wagi sumują się do 100%. */
class AssessmentTest {

    private static Assessment withAll(int score) {
        Assessment a = new Assessment();
        for (Criterion c : Criterion.values()) {
            a.getScores().add(new CriterionScore(c, score, "test"));
        }
        return a;
    }

    @Test
    void weightsSumToHundredPercent() {
        assertThat(Arrays.stream(Criterion.values()).mapToInt(Criterion::getWeight).sum()).isEqualTo(100);
    }

    @Test
    void totalRangesFromZeroToHundred() {
        assertThat(withAll(1).total()).isEqualTo(0.0);
        assertThat(withAll(3).total()).isEqualTo(50.0);
        assertThat(withAll(5).total()).isEqualTo(100.0);
    }

    @Test
    void expertScoreOverridesProposalButProposalIsKept() {
        Assessment a = withAll(3);
        CriterionScore impact = a.getScores().stream().filter(s -> s.getCriterion() == Criterion.WPLYW).findFirst().orElseThrow();
        impact.setFinalScore(5);

        assertThat(a.score(Criterion.WPLYW)).isEqualTo(5);
        assertThat(a.proposedTotal()).isEqualTo(50.0);
        // wpływ ma wagę 20%: (5−1)/4 zamiast (3−1)/4 → +10 pkt
        assertThat(a.total()).isEqualTo(60.0);
        assertThat(a.isApproved()).isFalse();
    }

    @Test
    void rejectedAndMergedRecordsLeaveTheCatalog() {
        assertThat(InnovationStatus.WYKRYTA.isPubliclyVisible()).isTrue();
        assertThat(InnovationStatus.OCENIONA.isPubliclyVisible()).isTrue();
        assertThat(InnovationStatus.ODRZUCONA.isPubliclyVisible()).isFalse();
        assertThat(InnovationStatus.POLACZONA.isPubliclyVisible()).isFalse();
    }
}
