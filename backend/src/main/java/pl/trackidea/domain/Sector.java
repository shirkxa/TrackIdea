package pl.trackidea.domain;

public enum Sector implements Labeled {
    MOTORYZACJA("Motoryzacja"),
    JAKOSC_ZYCIA("Jakość życia"),
    ICT("ICT"),
    LOTNICTWO("Lotnictwo"),
    KOSMOS("Branża kosmiczna"),
    EDUKACJA("Edukacja"),
    ADMINISTRACJA("Administracja"),
    ROLNICTWO("Rolnictwo"),
    ENERGETYKA("Energetyka"),
    ZDROWIE("Zdrowie"),
    SRODOWISKO("Środowisko");

    private final String label;

    Sector(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
