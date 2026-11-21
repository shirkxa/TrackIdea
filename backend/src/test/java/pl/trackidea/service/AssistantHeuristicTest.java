package pl.trackidea.service;

import org.junit.jupiter.api.Test;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.Sector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tryb offline asystenta (reguły słownikowe) – działa bez klucza API. */
class AssistantHeuristicTest {

    private final AssistantService assistant = new AssistantService("claude-opus-5-5", false);

    private static final String MENTORING = """
            Program mentoringu rówieśniczego dla klas pierwszych
            W naszym liceum uczniowie klas pierwszych często mają problem z adaptacją i rośnie liczba nieobecności. \
            Stworzyłam program, w którym uczniowie klas drugich zostają mentorami pierwszoklasistów. \
            Program działa od 2 lat. Liczba nieobecności spadła o 30%. \
            Wymaga tylko koordynatora i gotowych scenariuszy spotkań.""";

    @Test
    void offlineModeIsUsedWithoutApiKey() {
        assertThat(assistant.isAiEnabled()).isFalse();
        assertThat(assistant.draft(MENTORING).engine()).isEqualTo("REGUŁY");
    }

    @Test
    void splitsDescriptionIntoCardFields() {
        AssistantService.Draft d = assistant.heuristic(MENTORING);
        assertThat(d.title()).isEqualTo("Program mentoringu rówieśniczego dla klas pierwszych");
        assertThat(d.problem()).contains("problem z adaptacją");
        assertThat(d.solution()).contains("mentorami");
        assertThat(d.results()).contains("30%");
        assertThat(d.requirements()).contains("koordynatora");
        assertThat(d.type()).isEqualTo(InnovationType.EDUKACYJNA);
        assertThat(d.sector()).isEqualTo(Sector.EDUKACJA);
        assertThat(d.maturity()).isEqualTo(Maturity.DZIALA_W_JEDNYM);
    }

    @Test
    void recognisesTechnicalInnovationAndPilotStage() {
        AssistantService.Draft d = assistant.heuristic(
                "Dron z kamerą wielospektralną wykrywa choroby upraw. Prototyp testowano w gospodarstwach.");
        assertThat(d.type()).isEqualTo(InnovationType.TECHNICZNA);
        assertThat(d.sector()).isIn(Sector.LOTNICTWO, Sector.ROLNICTWO);
        assertThat(d.maturity()).isEqualTo(Maturity.PILOTAZ);
    }

    @Test
    void rejectsEmptyDescription() {
        assertThatThrownBy(() -> assistant.draft("  ")).isInstanceOf(DomainException.class);
    }
}
