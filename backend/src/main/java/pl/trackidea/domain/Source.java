package pl.trackidea.domain;

public enum Source implements Labeled {
    RADAR("Radar (automatyczny import)"),
    ANALITYK("Dodana przez analityka PCI");

    private final String label;

    Source(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
