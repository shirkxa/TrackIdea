package pl.trackidea.radar;

import org.junit.jupiter.api.Test;
import pl.trackidea.domain.InstitutionType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Jakość danych ze źródeł: typ instytucji z nazwy, programy bez innowacji, linki do rejestrów. */
class SourceQualityTest {

    private static JsonNode json(String s) {
        return JsonMapper.builder().build().readTree(s);
    }

    @Test
    void recognisesUniversitiesWithSchoolInTheirName() {
        assertThat(Names.institutionType("Wyższa Szkoła Informatyki i Zarządzania z siedzibą w Rzeszowie", InstitutionType.FIRMA))
                .isEqualTo(InstitutionType.UCZELNIA);
        assertThat(Names.institutionType("Szkoła Główna Handlowa w Warszawie", InstitutionType.FIRMA)).isEqualTo(InstitutionType.UCZELNIA);
        assertThat(Names.institutionType("Państwowa Akademia Nauk Stosowanych w Krośnie", InstitutionType.FIRMA)).isEqualTo(InstitutionType.UCZELNIA);
        assertThat(Names.institutionType("Szkoła Podstawowa nr 3 w Mielcu", InstitutionType.FIRMA)).isEqualTo(InstitutionType.SZKOLA);
        assertThat(Names.institutionType("Gmina Tyczyn", InstitutionType.FIRMA)).isEqualTo(InstitutionType.JST);
    }

    @Test
    void cordisSkipsResearcherTrainingAndBasicResearchProgrammes() {
        JsonNode msca = json("""
                {"relations":{"associations":{"programme":[
                  {"frameworkProgramme":"H2020","title":"EXCELLENT SCIENCE - Marie Skłodowska-Curie Actions","code":"H2020-EU.1.3."}]}}}""");
        JsonNode erc = json("""
                {"relations":{"associations":{"programme":
                  {"frameworkProgramme":"HORIZON","title":"European Research Council (ERC)","code":"HORIZON.1.1"}}}}""");
        JsonNode innovation = json("""
                {"relations":{"associations":{"programme":[
                  {"frameworkProgramme":"HORIZON","title":"Climate, Energy and Mobility","code":"HORIZON.2.5"}]}}}""");

        assertThat(CordisImporter.researchCareerProgramme(msca)).isTrue();
        assertThat(CordisImporter.researchCareerProgramme(erc)).isTrue();
        assertThat(CordisImporter.researchCareerProgramme(innovation)).isFalse();
        assertThat(CordisImporter.researchCareerProgramme(json("{}"))).isFalse();
    }

    @Test
    void europeanPatentApplicationsLinkToEpoRegister() {
        JsonNode epo = json("""
                {"applicationNumber":"22173183","grantingInstitutionName":"Europejski Urząd Patentowy","protectionTypeCode":"1"}""");
        JsonNode other = json("""
                {"applicationNumber":"US 17/123","grantingInstitutionName":"Urząd Patentowy USA","protectionTypeCode":"1"}""");

        assertThat(RadonImporter.url(epo)).isEqualTo("https://register.epo.org/application?number=EP22173183");
        assertThat(RadonImporter.url(other)).isEqualTo(RadonImporter.PAGE);
    }
}
