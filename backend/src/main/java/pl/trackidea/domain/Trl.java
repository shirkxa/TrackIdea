package pl.trackidea.domain;

import java.util.List;

/**
 * Poziomy gotowości technologicznej (TRL 1–9) według rozporządzenia MNiSW / Horyzont Europa.
 * Dla innowacji nietechnicznych (edukacja, administracja) stosujemy tę samą skalę jako miarę gotowości rozwiązania.
 */
public final class Trl {

    public static final List<String> LABELS = List.of(
            "Zaobserwowano podstawowe zasady",
            "Sformułowano koncepcję rozwiązania",
            "Potwierdzono koncepcję (badania, eksperyment)",
            "Zweryfikowano w warunkach laboratoryjnych",
            "Zweryfikowano w środowisku zbliżonym do rzeczywistego",
            "Demonstracja w środowisku zbliżonym do rzeczywistego",
            "Demonstracja w środowisku rzeczywistym (pilotaż)",
            "Rozwiązanie ukończone i sprawdzone",
            "Rozwiązanie sprawdzone w działaniu, stosowane");

    private Trl() {
    }

    public static String label(int level) {
        return LABELS.get(Math.max(1, Math.min(9, level)) - 1);
    }

    /** TRL 1–9 → ocena 1–5 w kryterium „Gotowość technologiczna”. */
    public static int toScore(int level) {
        if (level <= 2) return 1;
        if (level <= 4) return 2;
        if (level <= 6) return 3;
        if (level == 7) return 4;
        return 5;
    }

    /** Szacunek TRL z etapu wdrożenia i sygnałów w opisie (zawsze oznaczany jako szacunek). */
    public static int estimate(Maturity maturity, String normalizedText) {
        String t = normalizedText == null ? "" : normalizedText;
        int base = switch (maturity == null ? Maturity.POMYSL : maturity) {
            case POMYSL -> has(t, "proof of concept", "eksperyment", "experiment", "potwierdz") ? 3 : 2;
            case PROTOTYP -> has(t, "laborator", "laboratory", "symulac", "simulat") ? 4 : 5;
            case PILOTAZ -> has(t, "real environment", "rzeczywist", "operational", "w praktyce", "wdrozon") ? 7 : 6;
            case DZIALA_W_JEDNYM -> 8;
            case DZIALA_W_WIELU -> 9;
        };
        return base;
    }

    private static boolean has(String text, String... cues) {
        for (String c : cues) {
            if (text.contains(c)) return true;
        }
        return false;
    }
}
