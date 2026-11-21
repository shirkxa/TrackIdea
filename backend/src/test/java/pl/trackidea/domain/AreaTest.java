package pl.trackidea.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Trzy obszary z wyzwania: technologiczny, edukacyjny, administracyjny. */
class AreaTest {

    @Test
    void schoolsAndEducationGoToEducationalArea() {
        assertThat(Area.derive(InnovationType.TECHNICZNA, Sector.ICT, InstitutionType.SZKOLA)).isEqualTo(Area.EDUKACYJNY);
        assertThat(Area.derive(InnovationType.EDUKACYJNA, null, InstitutionType.UCZELNIA)).isEqualTo(Area.EDUKACYJNY);
    }

    @Test
    void municipalProcessesGoToAdministrativeArea() {
        assertThat(Area.derive(InnovationType.PROCESOWA, null, InstitutionType.JST)).isEqualTo(Area.ADMINISTRACYJNY);
        assertThat(Area.derive(InnovationType.TECHNICZNA, Sector.ADMINISTRACJA, InstitutionType.FIRMA)).isEqualTo(Area.ADMINISTRACYJNY);
    }

    @Test
    void technologyOfCompaniesAndUniversitiesIsTechnological() {
        assertThat(Area.derive(InnovationType.TECHNICZNA, Sector.LOTNICTWO, InstitutionType.UCZELNIA)).isEqualTo(Area.TECHNOLOGICZNY);
        assertThat(Area.derive(InnovationType.TECHNICZNA, Sector.ENERGETYKA, InstitutionType.JST)).isEqualTo(Area.TECHNOLOGICZNY);
    }
}
