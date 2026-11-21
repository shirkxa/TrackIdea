package pl.trackidea.service;

import pl.trackidea.domain.Area;
import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.Sector;

import java.util.List;

/**
 * Wynik analizy rekordu ze źródła przez model AI: czy to innowacja, opis po polsku i klasyfikacja.
 * Wszystko tutaj jest szacunkiem – na karcie jest tak oznaczone, dopóki analityk tego nie potwierdzi.
 * Pola, których model nie potrafił ustalić, są puste ({@code null}).
 */
public record RecordAnalysis(Verdict verdict, String verdictReason, String title, String summary, String problem,
                             String solution, String requirements, String results, Area area, InnovationType type,
                             Sector sector, Maturity maturity, Integer trl, Integer novelty, String noveltyReason,
                             List<String> tags) {

    /** Czy rekord opisuje innowację w rozumieniu Podręcznika Oslo 2018. */
    public enum Verdict {
        INNOWACJA, NIEPEWNE, NIE_INNOWACJA
    }

    public RecordAnalysis {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
