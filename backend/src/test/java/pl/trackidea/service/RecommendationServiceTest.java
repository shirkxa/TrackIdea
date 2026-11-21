package pl.trackidea.service;

import org.junit.jupiter.api.Test;
import pl.trackidea.domain.Assessment;
import pl.trackidea.domain.Criterion;
import pl.trackidea.domain.CriterionScore;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.PathType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Reguły rekomendacji zastosowań – każda reguła osobno, na podstawionych ocenach i dopasowaniach. */
class RecommendationServiceTest {

    private final AssessmentService assessments = mock(AssessmentService.class);
    private final MatchingService matching = mock(MatchingService.class);
    private final SimilarityService similarity = mock(SimilarityService.class);
    private final RecommendationService service = new RecommendationService(assessments, matching, similarity);

    private Innovation innovation(InnovationType type, Maturity maturity, int potential, int novelty) {
        Innovation inn = new Innovation();
        inn.setTitle("Innowacja testowa");
        inn.setType(type);
        inn.setMaturity(maturity);
        Assessment a = new Assessment();
        a.getScores().add(new CriterionScore(Criterion.POTENCJAL, potential, "potencjał"));
        a.getScores().add(new CriterionScore(Criterion.INNOWACYJNOSC, novelty, "innowacyjność"));
        when(assessments.currentOrPreview(inn)).thenReturn(a);
        when(matching.needsFor(inn)).thenReturn(new ArrayList<>());
        when(similarity.similarInnovations(anyString(), any(), anyInt())).thenReturn(new ArrayList<>());
        return inn;
    }

    private static MatchingService.Match need(String title, int score, String institutionType) {
        return new MatchingService.Match("POTRZEBA", 1L, title, "Instytucja z potrzebą", score, List.of("podobny problem"), 7L,
                institutionType);
    }

    @Test
    void nonTechnicalInnovationWithHighPotentialIsReplicatedInPublicInstitutions() {
        Innovation inn = innovation(InnovationType.EDUKACYJNA, Maturity.STOSOWANE, 5, 3);
        when(matching.needsFor(inn)).thenReturn(List.of(need("Adaptacja pierwszoklasistów", 70, "SZKOLA"),
                need("Program mentoringu", 60, "JST"), need("Szkolenia w firmie", 30, "FIRMA")));

        List<RecommendationService.PathRecommendation> recs = service.recommend(inn);

        RecommendationService.PathRecommendation r = recs.getFirst();
        assertThat(r.path()).isEqualTo(PathType.OTWARTA_REPLIKACJA);
        assertThat(r.label()).isEqualTo(PathType.OTWARTA_REPLIKACJA.getLabel());
        // 70 + 5×5 + 2 potrzeby × 3 = 101 → dopasowanie nigdy nie pokazuje 100%
        assertThat(r.fit()).isEqualTo(99);
        assertThat(r.reasons()).contains("typ edukacyjna", "potencjał 5/5", "instytucje z podobną potrzebą: 2");
        // cele to tylko instytucje publiczne
        assertThat(r.targets()).extracting(MatchingService.Match::title)
                .containsExactly("Adaptacja pierwszoklasistów", "Program mentoringu");
        assertThat(r.chosen()).isFalse();
    }

    @Test
    void replicationWithoutNeedsSuggestsAnnouncingIt() {
        Innovation inn = innovation(InnovationType.PROCESOWA, Maturity.PILOTAZ, 4, 3);

        RecommendationService.PathRecommendation r = service.recommend(inn).getFirst();

        assertThat(r.path()).isEqualTo(PathType.OTWARTA_REPLIKACJA);
        assertThat(r.fit()).isEqualTo(90);
        assertThat(r.reasons()).anyMatch(s -> s.startsWith("brak jeszcze zgłoszonych potrzeb"));
        assertThat(r.targets()).isEmpty();
    }

    @Test
    void earlyTechnicalInnovationGoesToPciLabs() {
        RecommendationService.PathRecommendation concept =
                service.recommend(innovation(InnovationType.TECHNICZNA, Maturity.KONCEPCJA, 3, 3)).getFirst();
        assertThat(concept.path()).isEqualTo(PathType.ROZWOJ_PCI);
        assertThat(concept.fit()).isEqualTo(85);
        assertThat(concept.reasons()).contains("etap: Koncepcja");

        // brak typu i etapu = innowacja techniczna na etapie koncepcji
        RecommendationService.PathRecommendation unknown = service.recommend(innovation(null, null, 3, 3)).getFirst();
        assertThat(unknown.path()).isEqualTo(PathType.ROZWOJ_PCI);
        assertThat(unknown.reasons()).contains("etap: nieznany");

        assertThat(service.recommend(innovation(InnovationType.TECHNICZNA, Maturity.PROTOTYP, 3, 3)).getFirst().fit()).isEqualTo(80);
    }

    @Test
    void matureAndNovelTechnicalInnovationIsCommercialised() {
        Innovation inn = innovation(InnovationType.TECHNICZNA, Maturity.STOSOWANE, 3, 5);
        inn.getChosenPaths().add(PathType.KOMERCJALIZACJA);

        RecommendationService.PathRecommendation r = service.recommend(inn).getFirst();

        assertThat(r.path()).isEqualTo(PathType.KOMERCJALIZACJA);
        assertThat(r.fit()).isEqualTo(95);
        assertThat(r.reasons()).contains("etap wdrożenia 5/5", "innowacyjność 5/5", "ochrona IP: brak informacji");
        // ścieżka wskazana wcześniej przez analityka jest oznaczona
        assertThat(r.chosen()).isTrue();

        inn.setIpStatus("patent PL");
        assertThat(service.recommend(inn).getFirst().reasons()).contains("ochrona IP: patent PL");
    }

    @Test
    void technicalInnovationInTestsNeedsPilotPartner() {
        RecommendationService.PathRecommendation r =
                service.recommend(innovation(InnovationType.TECHNICZNA, Maturity.TESTY, 3, 4)).getFirst();

        assertThat(r.path()).isEqualTo(PathType.PILOTAZ_BR);
        assertThat(r.fit()).isEqualTo(78);
        assertThat(r.rule()).contains("potrzebny partner");
    }

    @Test
    void matchingBusinessNeedBeatsGenericPilotRule() {
        Innovation inn = innovation(InnovationType.TECHNICZNA, Maturity.TESTY, 3, 4);
        when(matching.needsFor(inn)).thenReturn(List.of(need("Kontrola jakości spoin", 64, "FIRMA"),
                need("Słabe dopasowanie", 45, "FIRMA"), need("Potrzeba urzędu", 80, "JST")));

        List<RecommendationService.PathRecommendation> recs = service.recommend(inn);

        // obie reguły wskazują pilotaż – zostaje jedna rekomendacja, ta z lepszym dopasowaniem
        assertThat(recs).hasSize(1);
        RecommendationService.PathRecommendation r = recs.getFirst();
        assertThat(r.path()).isEqualTo(PathType.PILOTAZ_BR);
        assertThat(r.fit()).isEqualTo(85);
        assertThat(r.audience()).isEqualTo("Instytucja z potrzebą");
        assertThat(r.reasons()).containsExactly("najlepsze dopasowanie: „Kontrola jakości spoin” (64%)");
        assertThat(r.targets()).extracting(MatchingService.Match::title).containsExactly("Kontrola jakości spoin");
    }

    @Test
    void nearlyIdenticalInnovationSuggestsJoiningForces() {
        Innovation inn = innovation(InnovationType.TECHNICZNA, Maturity.KONCEPCJA, 3, 3);
        when(similarity.similarInnovations(anyString(), any(), anyInt()))
                .thenReturn(List.of(new SimilarItem("INNOWACJA", 12L, "Dron do inspekcji mostów", "Politechnika", 86)));

        List<RecommendationService.PathRecommendation> recs = service.recommend(inn);

        // rekomendacje są posortowane od najlepiej dopasowanej
        assertThat(recs).extracting(RecommendationService.PathRecommendation::path)
                .containsExactly(PathType.POLACZENIE_SIL, PathType.ROZWOJ_PCI);
        assertThat(recs.getFirst().fit()).isEqualTo(90);
        assertThat(recs.getFirst().targets().getFirst().id()).isEqualTo(12L);
        assertThat(recs.getFirst().reasons()).containsExactly("„Dron do inspekcji mostów” – Politechnika (86%)");
    }

    @Test
    void similarButNotIdenticalInnovationIsNotATwin() {
        Innovation inn = innovation(InnovationType.TECHNICZNA, Maturity.KONCEPCJA, 3, 3);
        when(similarity.similarInnovations(anyString(), any(), anyInt()))
                .thenReturn(List.of(new SimilarItem("INNOWACJA", 12L, "Podobny dron", "Politechnika", 79)));

        assertThat(service.recommend(inn)).extracting(RecommendationService.PathRecommendation::path)
                .containsExactly(PathType.ROZWOJ_PCI);
    }

    @Test
    void fallsBackToDefaultPathWhenNoRuleApplies() {
        RecommendationService.PathRecommendation social =
                service.recommend(innovation(InnovationType.SPOLECZNA, Maturity.TESTY, 3, 3)).getFirst();
        assertThat(social.path()).isEqualTo(PathType.OTWARTA_REPLIKACJA);
        assertThat(social.fit()).isEqualTo(50);
        assertThat(social.rule()).contains("ścieżka domyślna");
        assertThat(social.reasons()).containsExactly("potencjał 3/5", "etap wdrożenia 3/5");

        // techniczna, wdrożona, ale mało nowatorska – żadna reguła nie pasuje
        RecommendationService.PathRecommendation technical =
                service.recommend(innovation(InnovationType.TECHNICZNA, Maturity.PILOTAZ, 3, 2)).getFirst();
        assertThat(technical.path()).isEqualTo(PathType.ROZWOJ_PCI);
        assertThat(technical.fit()).isEqualTo(50);
    }

    @Test
    void showsAtMostFourTargets() {
        Innovation inn = innovation(InnovationType.ORGANIZACYJNA, Maturity.STOSOWANE, 5, 3);
        List<MatchingService.Match> many = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            many.add(need("Potrzeba " + i, 60, "SZKOLA"));
        }
        when(matching.needsFor(inn)).thenReturn(many);

        assertThat(service.recommend(inn).getFirst().targets()).hasSize(4);
    }
}
