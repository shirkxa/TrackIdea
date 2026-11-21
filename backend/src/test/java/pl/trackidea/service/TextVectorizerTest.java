package pl.trackidea.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TextVectorizerTest {

    @Test
    void normalizeRemovesPolishDiacriticsAndLowercases() {
        assertThat(TextVectorizer.normalize("Źródło ŁĄKI Żółć")).isEqualTo("zrodlo laki zolc");
        assertThat(TextVectorizer.normalize(null)).isEmpty();
    }

    @Test
    void tokensDropStopWordsAndStemByPrefix() {
        assertThat(TextVectorizer.tokens("Program mentoringu dla uczniów i nauczycieli"))
                .containsExactly("progra", "mentor", "ucznio", "nauczy");
    }

    @Test
    void inflectedFormsShareTheSameStem() {
        assertThat(TextVectorizer.tokens("mentoringu")).isEqualTo(TextVectorizer.tokens("mentoring"));
        assertThat(TextVectorizer.tokens("uczniów")).isEqualTo(TextVectorizer.tokens("uczniowie"));
    }

    @Test
    void cosineIsOneForSameVectorAndZeroForDisjoint() {
        Map<String, Double> a = Map.of("dron", 1.0, "upraw", 2.0);
        Map<String, Double> b = Map.of("szkol", 1.0);
        assertThat(TextVectorizer.cosine(a, a)).isCloseTo(1.0, within(1e-9));
        assertThat(TextVectorizer.cosine(a, b)).isZero();
        assertThat(TextVectorizer.cosine(a, Map.of())).isZero();
    }
}
