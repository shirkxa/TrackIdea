package pl.trackidea.domain;

/** Konta w Radarze to konta zespołu PCI – katalog jest publiczny i nie wymaga logowania. */
public enum Role implements Labeled {
    EKSPERT("Analityk PCI"),
    ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
