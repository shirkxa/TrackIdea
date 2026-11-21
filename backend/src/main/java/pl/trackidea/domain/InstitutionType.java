package pl.trackidea.domain;

public enum InstitutionType implements Labeled {
    SZKOLA("Szkoła", true),
    UCZELNIA("Uczelnia", true),
    JST("Samorząd (JST)", true),
    INSTYTUCJA_PUBLICZNA("Inna instytucja publiczna", true),
    FIRMA("Firma", false),
    FUNDUSZ("Fundusz / inwestor", false),
    NGO("Organizacja pozarządowa", false),
    PCI("Podkarpackie Centrum Innowacji", false);

    private final String label;
    private final boolean publicSector;

    InstitutionType(String label, boolean publicSector) {
        this.label = label;
        this.publicSector = publicSector;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPublicSector() {
        return publicSector;
    }
}
