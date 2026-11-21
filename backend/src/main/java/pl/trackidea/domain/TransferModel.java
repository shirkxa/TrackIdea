package pl.trackidea.domain;

public enum TransferModel implements Labeled {
    OTWARTA_REPLIKACJA("Otwarta replikacja (bezpłatnie)"),
    LICENCJA("Licencja"),
    WSPOLPRACA_BR("Współpraca B+R"),
    KOMERCJALIZACJA("Komercjalizacja");

    private final String label;

    TransferModel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
