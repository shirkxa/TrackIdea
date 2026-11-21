package pl.trackidea.domain;

public enum Source implements Labeled {
    ZGLOSZENIE_WLASNE("Zgłoszenie autora"),
    ZWIADOWCA("Zgłoszenie zwiadowcy"),
    RADAR("Radar (automatyczne wykrycie)"),
    FORK("Fork (wdrożenie w innej instytucji)");

    private final String label;

    Source(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
