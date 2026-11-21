package pl.trackidea.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.*;

import java.util.*;

/**
 * Rekomendacja ścieżki rozwoju na podstawie jawnych reguł (tabela z rozdziału 7 planu).
 * Każda rekomendacja ma uzasadnienie, odbiorców i checklistę następnych kroków.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private final AssessmentService assessmentService;
    private final MatchingService matching;
    private final SimilarityService similarity;

    public RecommendationService(AssessmentService assessmentService, MatchingService matching,
                                 SimilarityService similarity) {
        this.assessmentService = assessmentService;
        this.matching = matching;
        this.similarity = similarity;
    }

    public record PathRecommendation(PathType path, String label, int fit, String rule, List<String> reasons,
                                     String audience, List<String> nextSteps,
                                     List<MatchingService.Match> targets, boolean chosen) {
    }

    public List<PathRecommendation> recommend(Innovation inn) {
        Assessment a = assessmentService.currentOrPreview(inn);
        int przen = a.score(Criterion.POTENCJAL);
        int dojr = inn.getMaturity() == null ? 1 : inn.getMaturity().getLevel();
        int nowosc = a.score(Criterion.INNOWACYJNOSC);
        InnovationType type = inn.getType() == null ? InnovationType.TECHNICZNA : inn.getType();

        List<MatchingService.Match> needs = matching.needsFor(inn);
        List<PathRecommendation> out = new ArrayList<>();

        if (type.isNonTechnical() && przen >= 4) {
            List<MatchingService.Match> publicNeeds = needs.stream().filter(m -> isPublic(m.institutionType())).toList();
            out.add(rec(inn, PathType.OTWARTA_REPLIKACJA, 70 + przen * 5 + Math.min(10, publicNeeds.size() * 3),
                    "Typ nietechniczny i potencjał zastosowania ≥ 4",
                    List.of("typ " + type.getLabel().toLowerCase(), "potencjał " + przen + "/5",
                            publicNeeds.isEmpty() ? "brak jeszcze zgłoszonych potrzeb – warto ogłosić w sieci szkół/JST"
                                    : "instytucje z podobną potrzebą: " + publicNeeds.size()),
                    "Instytucje z podobną potrzebą, sieci szkół, związki gmin",
                    List.of("Przygotować opis wdrożenia krok po kroku", "Wskazać materiały źródłowe",
                            "Przekazać informację instytucjom z podobną potrzebą"),
                    publicNeeds));
        }
        if (type == InnovationType.TECHNICZNA && dojr <= 2) {
            out.add(rec(inn, PathType.ROZWOJ_PCI, 75 + (3 - dojr) * 5,
                    "Innowacja techniczna, etap wdrożenia ≤ 2",
                    List.of("etap: " + label(inn.getMaturity()), "potrzebne prototypowanie i testy"),
                    "Laboratoria PCI, program akceleracyjny, uczelnie",
                    List.of("Wskazać program akceleracyjny lub laboratorium PCI", "Wrócić do oceny po nowych wynikach"),
                    List.of()));
        }
        if (type == InnovationType.TECHNICZNA && dojr >= 4 && nowosc >= 4) {
            out.add(rec(inn, PathType.KOMERCJALIZACJA, 80 + nowosc * 3,
                    "Innowacja techniczna, etap wdrożenia ≥ 4 i innowacyjność ≥ 4",
                    List.of("etap wdrożenia " + dojr + "/5", "innowacyjność " + nowosc + "/5",
                            "ochrona IP: " + (inn.getIpStatus() == null ? "brak informacji" : inn.getIpStatus())),
                    "Firmy z sektora, centra transferu technologii",
                    List.of("Sprawdzić status ochrony (UPRP / EPO)", "Wskazać branże, w których może być zastosowana",
                            "Przekazać do centrum transferu technologii"),
                    List.of()));
        }
        if (type == InnovationType.TECHNICZNA && dojr == 3) {
            out.add(rec(inn, PathType.PILOTAZ_BR, 70 + nowosc * 2,
                    "Innowacja techniczna w pilotażu – potrzebny partner do wdrożenia",
                    List.of("etap: " + label(inn.getMaturity()), "innowacyjność " + nowosc + "/5",
                            "potrzebny partner do wdrożenia pilotażowego"),
                    "Firmy z branży, uczelnie, PCI",
                    List.of("Wskazać instytucje, które mogą przetestować rozwiązanie", "Ustalić zakres pilotażu"),
                    List.of()));
        }
        List<MatchingService.Match> businessNeeds = needs.stream()
                .filter(m -> !isPublic(m.institutionType()) && m.score() >= 50).toList();
        if (!businessNeeds.isEmpty()) {
            out.add(rec(inn, PathType.PILOTAZ_BR, 85,
                    "Pasująca potrzeba firmy lub instytucji na tablicy potrzeb",
                    List.of("najlepsze dopasowanie: „" + businessNeeds.getFirst().title() + "” ("
                            + businessNeeds.getFirst().score() + "%)"),
                    businessNeeds.getFirst().subtitle(),
                    List.of("Przekazać informację instytucji z potrzebą", "Ustalić zakres pilotażu", "Mierzyć efekty pilotażu"),
                    businessNeeds));
        }
        List<SimilarItem> twins = similarity.similarInnovations(inn.searchText(), inn.getId(), 1);
        if (!twins.isEmpty() && twins.getFirst().similarityPercent() >= 80) {
            SimilarItem twin = twins.getFirst();
            out.add(rec(inn, PathType.POLACZENIE_SIL, 90,
                    "Podobieństwo ≥ 80% do istniejącej innowacji",
                    List.of("„" + twin.title() + "” – " + twin.subtitle() + " (" + twin.similarityPercent() + "%)"),
                    "Autor i instytucja podobnej innowacji",
                    List.of("Porównać oba rozwiązania", "Połączyć karty, jeśli to ten sam projekt"),
                    List.of(new MatchingService.Match("INNOWACJA", twin.id(), twin.title(), twin.subtitle(),
                            twin.similarityPercent(), List.of("prawie identyczny opis"), null, null))));
        }
        if (out.isEmpty()) {
            out.add(rec(inn, type.isNonTechnical() ? PathType.OTWARTA_REPLIKACJA : PathType.ROZWOJ_PCI, 50,
                    "Żadna reguła nie dała silnej rekomendacji – ścieżka domyślna",
                    List.of("potencjał " + przen + "/5", "etap wdrożenia " + dojr + "/5"),
                    "Ekspert PCI", List.of("Uzupełnić kartę (efekty, wymagania)", "Ponowić ocenę"), List.of()));
        }

        Map<PathType, PathRecommendation> best = new LinkedHashMap<>();
        out.stream().sorted(Comparator.comparingInt(PathRecommendation::fit).reversed())
                .forEach(r -> best.putIfAbsent(r.path(), r));
        return new ArrayList<>(best.values());
    }

    private PathRecommendation rec(Innovation inn, PathType path, int fit, String rule, List<String> reasons,
                                   String audience, List<String> steps, List<MatchingService.Match> targets) {
        return new PathRecommendation(path, path.getLabel(), Math.min(99, fit), rule, reasons, audience, steps,
                targets.size() > 4 ? targets.subList(0, 4) : targets, inn.getChosenPaths().contains(path));
    }

    private static boolean isPublic(String institutionType) {
        return institutionType != null && InstitutionType.valueOf(institutionType).isPublicSector();
    }

    private static String label(Maturity m) {
        return m == null ? "nieznany" : m.getLabel();
    }
}
