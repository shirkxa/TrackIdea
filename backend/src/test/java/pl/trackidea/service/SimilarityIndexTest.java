package pl.trackidea.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityIndexTest {

    private final SimilarityService.Index index = new SimilarityService.Index(List.of(
            "Mapa zgłoszeń usterek dla mieszkańców gminy",
            "Dron z kamerą wielospektralną do wykrywania chorób upraw",
            "Tutoring rówieśniczy z matematyki w liceum"));

    @Test
    void similarDescriptionsScoreHigherThanUnrelatedOnes() {
        String query = "Aplikacja do zgłaszania usterek przez mieszkańców";
        double similar = index.similarity(query, "Mapa zgłoszeń usterek dla mieszkańców gminy");
        double unrelated = index.similarity(query, "Dron z kamerą wielospektralną do wykrywania chorób upraw");
        assertThat(similar).isGreaterThan(0.3);
        assertThat(unrelated).isLessThan(0.05);
    }

    @Test
    void percentIsCappedAtHundred() {
        assertThat(SimilarityService.percent(1.4)).isEqualTo(100);
        assertThat(SimilarityService.percent(0.874)).isEqualTo(87);
    }
}
