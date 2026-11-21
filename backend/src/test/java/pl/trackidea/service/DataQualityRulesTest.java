package pl.trackidea.service;

import org.junit.jupiter.api.Test;
import pl.trackidea.domain.Assessment;
import pl.trackidea.domain.Criterion;
import pl.trackidea.domain.CriterionScore;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.Institution;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.Sector;

import static org.assertj.core.api.Assertions.assertThat;

/** Reguły offline po poprawkach jakości: granice wyrazów, skróty, brak zgadywania i wynik bez kryterium TRL. */
class DataQualityRulesTest {

    private final AssistantService assistant = new AssistantService("model", false);

    @Test
    void keywordsMatchWordStartsNotSubstrings() {
        // „polega” to nie „pole”, „synergia” to nie „energia”, „urządzenie” to nie „urząd”
        assertThat(AssistantService.countOccurrences("rozwiazanie polega na synergii", "energ")).isZero();
        assertThat(AssistantService.countOccurrences("urzadzenie do pomiaru", "urzad|")).isZero();
        assertThat(AssistantService.countOccurrences("urzad miasta i urzad gminy", "urzad|")).isEqualTo(2);
        assertThat(AssistantService.countOccurrences("energia i energetyka", "energ")).isEqualTo(2);

        AssistantService.Draft device = assistant.heuristic("Urządzenie do zdalnego podpisywania dokumentów",
                "Urządzenie charakteryzuje się tym, że jego korpus zawiera parę poziomych prowadnic, a rozwiązanie polega na synergii napędów.");
        assertThat(device.sector()).isNull();
    }

    @Test
    void sectorNeedsMoreThanOneCueAndStageIsNotGuessed() {
        AssistantService.Draft vaccine = assistant.heuristic("Szczepionka przeciw kleszczowemu zapaleniu mózgu",
                "Antygen szczepionkowy do zapobiegania chorobie. Dane potwierdzają odpowiedź u pacjentów.");
        assertThat(vaccine.sector()).isEqualTo(Sector.ZDROWIE);
        assertThat(vaccine.maturity()).isNull();
        assertThat(vaccine.notes()).anyMatch(n -> n.contains("etapie wdrożenia"));

        AssistantService.Draft prototype = assistant.heuristic("Czujnik", "Zbudowano prototyp czujnika wilgotności.");
        assertThat(prototype.maturity()).isEqualTo(Maturity.PROTOTYP);
        assertThat(prototype.type()).isEqualTo(InnovationType.TECHNICZNA);
    }

    @Test
    void summaryComesFromDescriptionAndDoesNotBreakOnAbbreviations() {
        AssistantService.Draft d = assistant.heuristic("Wzrost konkurencyjności firmy GOSYMAR Sp. z o.o.",
                "Celem projektu jest opracowanie przez Ultratech Sp. z o.o. nowej powłoki ochronnej im. prof. Kowalskiego. "
                        + "Powłoka trafi do produkcji w 2026 r. Efektem będzie dłuższa praca turbin.");

        assertThat(d.title()).isEqualTo("Wzrost konkurencyjności firmy GOSYMAR Sp. z o.o.");
        assertThat(d.summary()).startsWith("Celem projektu jest opracowanie przez Ultratech Sp. z o.o. nowej powłoki");
        assertThat(d.summary()).doesNotContain("GOSYMAR");
        assertThat(AssistantService.sentences("Zakres: 1. Analiza. 2. Wdrożenie np. w urzędzie. Koniec."))
                .containsExactly("Zakres: 1. Analiza.", "2. Wdrożenie np. w urzędzie.", "Koniec.");
    }

    @Test
    void descriptionWithoutTitleStillWorks() {
        AssistantService.Draft d = assistant.heuristic(null, "Mentoring rówieśniczy w szkole. Uczniowie pomagają uczniom.");
        assertThat(d.title()).isEqualTo("Mentoring rówieśniczy w szkole");
        assertThat(assistant.heuristic("Sam tytuł", "  ").summary()).isEqualTo("Sam tytuł");
    }

    @Test
    void scoreIsNormalisedWhenTrlDoesNotApply() {
        Assessment a = new Assessment();
        for (Criterion c : Criterion.values()) {
            if (c != Criterion.TRL) {
                a.getScores().add(new CriterionScore(c, 5, "x"));
            }
        }
        // cztery kryteria na maksimum to 100, a nie 85
        assertThat(a.proposedTotal()).isEqualTo(100.0);
        a.getScores().getFirst().setFinalScore(1);
        assertThat(a.total()).isEqualTo(Math.round((1 - 20.0 / 85) * 1000) / 10.0);
        assertThat(new Assessment().total()).isZero();
    }

    @Test
    void institutionKeyIgnoresCaseAndPolishLetters() {
        assertThat(Institution.keyOf("Politechnika Poznańska")).isEqualTo(Institution.keyOf("POLITECHNIKA  POZNANSKA"));
        assertThat(Institution.keyOf("Urząd Miasta Łodzi")).isEqualTo("urzad miasta lodzi");
        assertThat(Institution.keyOf(null)).isNull();
    }
}
