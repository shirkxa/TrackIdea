package pl.trackidea.service;

import org.junit.jupiter.api.Test;
import pl.trackidea.domain.Institution;
import pl.trackidea.domain.InstitutionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingRulesTest {

    @Test
    void commonTagsIgnoreInflectionAndCase() {
        assertThat(MatchingService.commonTags(Set.of("Drony", "rolnictwo precyzyjne"), Set.of("drony", "kompozyty")))
                .containsExactly("Drony");
        assertThat(MatchingService.commonTags(Set.of("seniorzy"), Set.of("recykling"))).isEmpty();
    }

    @Test
    void regionScorePrefersSameCountyThenSameRegion() {
        Institution krosnoA = new Institution("Szkoła A", InstitutionType.SZKOLA, "Krosno", "krośnieński", null);
        Institution krosnoB = new Institution("Szkoła B", InstitutionType.SZKOLA, "Krosno", "krośnieński", null);
        Institution mielec = new Institution("Technikum", InstitutionType.SZKOLA, "Mielec", "mielecki", null);
        Institution warszawa = new Institution("Firma", InstitutionType.FIRMA, "Warszawa", "warszawski", "mazowieckie");

        List<String> reasons = new ArrayList<>();
        assertThat(MatchingService.regionScore(krosnoA, krosnoB, reasons)).isEqualTo(1.0);
        assertThat(reasons).containsExactly("ten sam powiat (krośnieński)");
        assertThat(MatchingService.regionScore(krosnoA, mielec, new ArrayList<>())).isEqualTo(0.6);
        assertThat(MatchingService.regionScore(krosnoA, warszawa, new ArrayList<>())).isEqualTo(0.2);
        assertThat(MatchingService.regionScore(krosnoA, null, new ArrayList<>())).isEqualTo(0.3);
    }
}
