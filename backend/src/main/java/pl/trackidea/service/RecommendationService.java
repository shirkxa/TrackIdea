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
        int przen = a.score(Criterion.PRZENASZALNOSC);
        int dojr = a.score(Criterion.DOJRZALOSC);
        int nowosc = a.score(Criterion.NOWOSC);
        int wplyw = a.score(Criterion.WPLYW);
        InnovationType type = inn.getType() == null ? InnovationType.TECHNICZNA : inn.getType();

        List<MatchingService.Match> needs = matching.needsFor(inn);
        List<MatchingService.Match> partners = matching.partnersFor(inn);
        List<PathRecommendation> out = new ArrayList<>();

        if (type.isNonTechnical() && przen >= 4) {
            List<MatchingService.Match> publicNeeds = needs.stream().filter(m -> isPublic(m.institutionType())).toList();
            out.add(rec(inn, PathType.OTWARTA_REPLIKACJA, 70 + przen * 5 + Math.min(10, publicNeeds.size() * 3),
                    "Typ nietechniczny i przenaszalność ≥ 4",
                    List.of("typ " + type.getLabel().toLowerCase(), "przenaszalność " + przen + "/5",
                            publicNeeds.isEmpty() ? "brak jeszcze zgłoszonych potrzeb – warto ogłosić w sieci szkół/JST"
                                    : "instytucje z podobną potrzebą: " + publicNeeds.size()),
                    "Instytucje z podobną potrzebą, sieci szkół, związki gmin",
                    List.of("Przygotować opis wdrożenia krok po kroku (README)", "Dodać materiały do pobrania",
                            "Wskazać osobę do kontaktu dla instytucji forkujących", "Zbierać potwierdzenia wdrożeń"),
                    publicNeeds));
        }
        if (type == InnovationType.TECHNICZNA && dojr <= 2) {
            out.add(rec(inn, PathType.ROZWOJ_PCI, 75 + (3 - dojr) * 5,
                    "Innowacja techniczna, dojrzałość ≤ 2",
                    List.of("etap: " + label(inn.getMaturity()), "potrzebne prototypowanie i testy"),
                    "Laboratoria PCI, mentorzy, program akceleracyjny (16–30 lat), uczelnie",
                    List.of("Zgłosić projekt do programu akceleracyjnego PCI", "Zarezerwować laboratorium do prototypowania",
                            "Ustalić plan testów i mierniki", "Wrócić do oceny po zbudowaniu prototypu"),
                    partners));
        }
        if (type == InnovationType.TECHNICZNA && dojr >= 4 && nowosc >= 4) {
            out.add(rec(inn, PathType.KOMERCJALIZACJA, 80 + nowosc * 3,
                    "Innowacja techniczna, dojrzałość ≥ 4 i nowość ≥ 4",
                    List.of("dojrzałość " + dojr + "/5", "nowość " + nowosc + "/5",
                            "ochrona IP: " + (inn.getIpStatus() == null ? "brak informacji" : inn.getIpStatus())),
                    "Firmy z sektora, centra transferu technologii, inwestorzy",
                    List.of("Sprawdzić zdolność patentową (UPRP / EPO)", "Skontaktować się z centrum transferu technologii",
                            "Przygotować pitch dla inwestorów", "Wybrać model: licencja lub spin-off"),
                    partners));
        }
        if (type == InnovationType.TECHNICZNA && dojr == 3) {
            out.add(rec(inn, PathType.PILOTAZ_BR, 70 + nowosc * 2,
                    "Innowacja techniczna w pilotażu – potrzebny partner do wdrożenia",
                    List.of("etap: " + label(inn.getMaturity()), "nowość " + nowosc + "/5",
                            partners.isEmpty() ? "brak dopasowanych partnerów" : "dopasowani partnerzy: " + partners.size()),
                    "Firmy z branży, uczelnie, PCI",
                    List.of("Wybrać partnera do pilotażu", "Ustalić zakres współpracy B+R i podział praw",
                            "Poszukać finansowania (granty B+R, programy regionalne)"),
                    partners));
        }
        if (wplyw >= 4 && type.isNonTechnical()) {
            out.add(rec(inn, PathType.GRANT, 60 + wplyw * 4,
                    "Wpływ ≥ 4 i niski potencjał rynkowy (innowacja nietechniczna)",
                    List.of("wpływ " + wplyw + "/5", "rozwiązanie służy dobru publicznemu"),
                    "Programy regionalne i UE, fundacje, samorządy",
                    List.of("Opisać efekty w formie wskaźników", "Wybrać nabór (np. programy regionalne, fundacje)",
                            "Znaleźć partnerów do wniosku wśród instytucji forkujących"),
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
                    List.of("Wysłać prośbę o kontakt", "Ustalić zakres pilotażu", "Mierzyć efekty pilotażu"),
                    businessNeeds));
        }
        List<SimilarItem> twins = similarity.similarInnovations(inn.searchText(), inn.getId(), 1);
        if (!twins.isEmpty() && twins.getFirst().similarityPercent() >= 80) {
            SimilarItem twin = twins.getFirst();
            out.add(rec(inn, PathType.POLACZENIE_SIL, 90,
                    "Podobieństwo ≥ 80% do istniejącej innowacji",
                    List.of("„" + twin.title() + "” – " + twin.subtitle() + " (" + twin.similarityPercent() + "%)"),
                    "Autor i instytucja podobnej innowacji",
                    List.of("Skontaktować się z autorem podobnego rozwiązania", "Rozważyć fork zamiast budowania od zera",
                            "Ekspert PCI może połączyć karty, zachowując wszystkich autorów"),
                    List.of(new MatchingService.Match("INNOWACJA", twin.id(), twin.title(), twin.subtitle(),
                            twin.similarityPercent(), List.of("prawie identyczny opis"), null, null, null))));
        }
        if (out.isEmpty()) {
            out.add(rec(inn, type.isNonTechnical() ? PathType.OTWARTA_REPLIKACJA : PathType.ROZWOJ_PCI, 50,
                    "Żadna reguła nie dała silnej rekomendacji – ścieżka domyślna",
                    List.of("przenaszalność " + przen + "/5", "dojrzałość " + dojr + "/5"),
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
