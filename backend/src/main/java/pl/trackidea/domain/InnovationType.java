package pl.trackidea.domain;

public enum InnovationType implements Labeled {
    TECHNICZNA("Techniczna"),
    ORGANIZACYJNA("Organizacyjna"),
    EDUKACYJNA("Edukacyjna"),
    SPOLECZNA("Społeczna"),
    PROCESOWA("Procesowa");

    private final String label;

    InnovationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Innowacje nietechniczne zwykle łatwiej przenieść, ale mają mniejszy potencjał rynkowy. */
    public boolean isNonTechnical() {
        return this != TECHNICZNA;
    }
}
