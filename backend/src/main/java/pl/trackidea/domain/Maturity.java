package pl.trackidea.domain;

public enum Maturity implements Labeled {
    POMYSL("Pomysł", 1),
    PROTOTYP("Prototyp", 2),
    PILOTAZ("Pilotaż", 3),
    DZIALA_W_JEDNYM("Działa w 1 miejscu", 4),
    DZIALA_W_WIELU("Działa w wielu miejscach", 5);

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
