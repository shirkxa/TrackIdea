package pl.trackidea.radar;

import org.junit.jupiter.api.Test;
import pl.trackidea.domain.Sector;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Parsowanie danych ze źródeł Radaru – bez łączenia z internetem. */
class ImporterParsingTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void rebuildsOpenAlexAbstractFromInvertedIndex() {
        var index = json.readTree("{\"Radar\":[0],\"finds\":[1],\"existing\":[2],\"innovations\":[3]}");
        assertThat(OpenAlexImporter.abstractFrom(index)).isEqualTo("Radar finds existing innovations");
        assertThat(OpenAlexImporter.abstractFrom(json.readTree("null"))).isEmpty();
    }

    @Test
    void mapsOpenAlexTopicToSector() {
        assertThat(OpenAlexImporter.sectorFrom(json.readTree(
                "{\"field\":{\"display_name\":\"Engineering\"},\"subfield\":{\"display_name\":\"Aerospace Engineering\"}}")))
                .isEqualTo(Sector.LOTNICTWO);
        assertThat(OpenAlexImporter.sectorFrom(json.readTree(
                "{\"field\":{\"display_name\":\"Energy\"},\"subfield\":{\"display_name\":\"Renewable Energy\"}}")))
                .isEqualTo(Sector.ENERGETYKA);
        assertThat(OpenAlexImporter.sectorFrom(json.readTree("{}"))).isNull();
    }

    @Test
    void tidiesUpperCaseNamesFromCordis() {
        assertThat(Names.titleCase("URZAD MIASTA RZESZOWA")).isEqualTo("Urzad Miasta Rzeszowa");
        assertThat(Names.titleCase("QSAR LAB SPOLKA Z OGRANICZONA ODPOWIEDZIALNOSCIA")).isEqualTo("Qsar Lab sp. z o.o.");
        assertThat(Names.titleCase("Gmina Ustka")).isEqualTo("Gmina Ustka");
    }

    @Test
    void describesSourceFactsInPolish() {
        assertThat(CordisImporter.statusLabel("CLOSED")).isEqualTo("zakończony");
        assertThat(CordisImporter.statusLabel("SIGNED")).isEqualTo("w realizacji");
        assertThat(CordisImporter.euro("4998765")).isEqualTo("4 998 765 €");
        assertThat(OpenAlexImporter.kindLabel("article")).isEqualTo("artykuł");
    }
}
