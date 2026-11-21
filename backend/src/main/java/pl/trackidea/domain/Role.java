package pl.trackidea.domain;

public enum Role implements Labeled {
    TWORCA("Twórca"),
    OPIEKUN("Opiekun instytucji"),
    EKSPERT("Ekspert PCI"),
    PARTNER("Partner / inwestor"),
    ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
