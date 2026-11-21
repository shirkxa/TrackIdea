package pl.trackidea.domain;

/**
 * Cykl życia innowacji (jak w Banku Pomysłów): każdy status ma numer kroku w pipeline.
 * Statusy boczne mają krok 0.
 */
public enum InnovationStatus implements Labeled {
    WYKRYTA("Wykryta", 1, "Znaleziona przez Radar, czeka na potwierdzenie autora"),
    ZGLOSZONA("Zgłoszona", 1, "Zgłoszona, czeka na potwierdzenie autora lub opiekuna"),
    POTWIERDZONA("Potwierdzona", 2, "Autor zgodził się na publikację"),
    UZUPELNIONA("Uzupełniona", 3, "Karta ma komplet wymaganych pól"),
    W_OCENIE("W ocenie", 4, "Propozycja oceny czeka na eksperta PCI"),
    OCENIONA("Oceniona", 5, "Ekspert zatwierdził ocenę"),
    SCIEZKA_WSKAZANA("Ścieżka wskazana", 6, "Zatwierdzono ścieżkę rozwoju"),
    W_TRANSFERZE("W transferze", 7, "Trwa kontakt, pilotaż lub wdrożenie w innym miejscu"),
    WDROZONA("Wdrożona", 8, "Potwierdzone wdrożenie w innej instytucji"),
    WSTRZYMANA("Wstrzymana", 0, "Brak aktywności autora"),
    POLACZONA("Połączona", 0, "Scalona z bardzo podobną innowacją"),
    ARCHIWALNA("Archiwalna", 0, "Przeniesiona do archiwum");

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

    /** Szkice (przed potwierdzeniem) nie są publiczne – wymóg RODO z planu. */
    public boolean isDraft() {
        return this == WYKRYTA || this == ZGLOSZONA;
    }

    public boolean isPubliclyVisible() {
        return !isDraft() && this != POLACZONA && this != ARCHIWALNA;
    }
}
