package pl.trackidea.domain;

public enum ContactStatus implements Labeled {
    WYSLANA("Wysłana"),
    ZAAKCEPTOWANA("Zaakceptowana"),
    ODRZUCONA("Odrzucona");

    private final String label;

    ContactStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
