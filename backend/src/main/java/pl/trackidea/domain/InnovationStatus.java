package pl.trackidea.domain;

/**
 * Status rekordu w Radarze: od wykrycia w źródle do weryfikacji przez analityka PCI.
 * Statusy boczne (krok 0) wyłączają rekord z katalogu.
 */
public enum InnovationStatus implements Labeled {
    WYKRYTA("Wykryta", 1, "Zaimportowana przez Radar – czeka na weryfikację analityka"),
    W_OCENIE("W ocenie", 2, "Propozycja oceny czeka na zatwierdzenie analityka PCI"),
    OCENIONA("Zweryfikowana", 3, "Analityk PCI zatwierdził ocenę"),
    ODRZUCONA("Odrzucona", 0, "Nie spełnia definicji innowacji (Oslo Manual 2018)"),
    POLACZONA("Połączona", 0, "Duplikat innej innowacji"),
    ARCHIWALNA("Archiwalna", 0, "Nieaktualna – przeniesiona do archiwum");

    private final String label;
    private final int step;
    private final String description;

    InnovationStatus(String label, int step, String description) {
        this.label = label;
        this.step = step;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public int getStep() {
        return step;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPubliclyVisible() {
        return step > 0;
    }
}
