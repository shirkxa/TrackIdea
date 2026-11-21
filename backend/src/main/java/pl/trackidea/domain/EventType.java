package pl.trackidea.domain;

public enum EventType implements Labeled {
    UTWORZONO("Utworzono"),
    WYKRYTO("Wykryto przez Radar"),
    STATUS("Zmiana statusu"),
    EDYCJA("Aktualizacja karty"),
    OCENA("Ocena"),
    SCIEZKA("Ścieżka rozwoju"),
    WDROZENIE("Wdrożenie");

    private final String label;

    EventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
