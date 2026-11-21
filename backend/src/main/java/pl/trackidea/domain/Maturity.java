package pl.trackidea.domain;

/** Poziom wdrożenia – pięć etapów z FAQ wyzwania „Radar innowacji”. */
public enum Maturity implements Labeled {
    KONCEPCJA("Koncepcja", 1),
    PROTOTYP("Prototyp", 2),
    TESTY("Testy", 3),
    PILOTAZ("Wdrożenie pilotażowe", 4),
    STOSOWANE("Stosowane w praktyce", 5);

    private final String label;
    private final int level;

    Maturity(String label, int level) {
        this.label = label;
        this.level = level;
    }

    public String getLabel() {
        return label;
    }

    public int getLevel() {
        return level;
    }
}
