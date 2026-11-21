package pl.trackidea.domain;

/**
 * Trzy obszary innowacji z wyzwania „Radar innowacji”.
 * Innowacja w rozumieniu Podręcznika Oslo (OECD/Eurostat, 2018): nowy lub ulepszony produkt albo proces
 * (lub ich połączenie), który znacząco różni się od dotychczasowych i został udostępniony użytkownikom
 * albo wdrożony do stosowania.
 */
public enum Area implements Labeled {
    TECHNOLOGICZNY("Technologiczny", "Nowe lub ulepszone produkty, technologie i usługi (wyniki B+R, rozwiązania firm i uczelni)."),
    EDUKACYJNY("Edukacyjny", "Nowe metody nauczania, programy i narzędzia w szkołach, uczelniach i edukacji dorosłych."),
    ADMINISTRACYJNY("Administracyjny", "Nowe lub ulepszone procesy i usługi publiczne w samorządach i urzędach.");

    private final String label;
    private final String description;

    Area(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** Rozpoznanie obszaru z typu, sektora i instytucji – zapisywane jako szacunek do potwierdzenia. */
    public static Area derive(InnovationType type, Sector sector, InstitutionType institution) {
        if (type == InnovationType.EDUKACYJNA || sector == Sector.EDUKACJA || institution == InstitutionType.SZKOLA) {
            return EDUKACYJNY;
        }
        if (sector == Sector.ADMINISTRACJA || (institution == InstitutionType.JST
                && type != InnovationType.TECHNICZNA)) {
            return ADMINISTRACYJNY;
        }
        return TECHNOLOGICZNY;
    }
}
