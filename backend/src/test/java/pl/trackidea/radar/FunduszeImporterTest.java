package pl.trackidea.radar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.trackidea.domain.Area;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Maturity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lista projektów Funduszy Europejskich – parsowanie bez łączenia z internetem. */
class FunduszeImporterTest {

    @Test
    void readsLargeXlsxInStreamingMode(@TempDir Path dir) throws Exception {
        Path xlsx = dir.resolve("lista.xlsx");
        try (OutputStream out = Files.newOutputStream(xlsx); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "xl/sharedStrings.xml", "<sst><si><t>Platforma e-usług powiatu</t></si>"
                    + "<si><r><t>016 Rozwiązania TIK</t></r><r><t>, usługi elektroniczne</t></r></si></sst>");
            put(zip, "xl/worksheets/sheet1.xml", "<worksheet><sheetData>"
                    + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"R1\"><v>45869.08</v></c>"
                    + "<c r=\"S1\" t=\"s\"><v>1</v></c><c r=\"Z1\"><v>9</v></c></row></sheetData></worksheet>");
        }
        List<Map<String, XlsxStream.Cell>> rows = XlsxStream.rows(xlsx, Set.of("A", "R", "S"));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsKeys("A", "R", "S").doesNotContainKey("Z");
        assertThat(XlsxStream.sharedStrings(xlsx, Set.of(1)))
                .containsExactly(Map.entry(1, "016 Rozwiązania TIK, usługi elektroniczne"));
        assertThat(FunduszeImporter.excelDate(rows.getFirst().get("R").raw())).isEqualTo(LocalDate.of(2025, 7, 31));
    }

    @Test
    void picksInnovationProjectsByCategoryAndTitle() {
        FunduszeImporter.Query admin = FunduszeImporter.QUERIES.getFirst();
        assertThat(FunduszeImporter.matches(admin, "016 Rozwiązania TIK", "Platforma e-usług administracji")).isTrue();
        assertThat(FunduszeImporter.matches(admin, "016 Rozwiązania TIK", "Zakup mebli")).isFalse();
        assertThat(FunduszeImporter.matches(admin, "083 Infrastruktura rowerowa", "Cyfrowa ścieżka")).isFalse();
        assertThat(FunduszeImporter.maturity(admin, true)).isEqualTo(Maturity.STOSOWANE);
    }

    @Test
    void parsesBeneficiaryAndLocation() {
        assertThat(FunduszeImporter.beneficiary("Gmina Złoty Stok/Szkoła Podstawowa w Złotym Stoku"))
                .isEqualTo("Szkoła Podstawowa w Złotym Stoku");
        assertThat(FunduszeImporter.beneficiary("STIGAL AUTOMATION SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ"))
                .isEqualTo("Stigal Automation sp. z o.o.");
        assertThat(FunduszeImporter.institutionType("Powiat Wałbrzyski", FunduszeImporter.QUERIES.getFirst()))
                .isEqualTo(InstitutionType.JST);

        FunduszeImporter.Location loc = FunduszeImporter.location(
                "WOJ.: PODKARPACKIE, POW.: rzeszowski, GM.: Tyczyn | WOJ.: PODKARPACKIE, POW.: Rzeszów, GM.: Rzeszów");
        assertThat(loc.region()).isEqualTo("Podkarpackie");
        assertThat(loc.county()).isEqualTo("rzeszowski");
        assertThat(loc.city()).isEqualTo("Tyczyn");
        assertThat(loc.place()).isEqualTo("Tyczyn, pow. rzeszowski, woj. podkarpackie i 1 inne");
    }

    // ---------- pełna ścieżka: API dane.gov.pl → plik XLSX → rekordy ----------

    private static final String RESOURCE = """
            {"data":[{"id":"777","attributes":{"download_url":"https://api.dane.gov.pl/media/lista.xlsx","file_size":41000000}}]}""";

    /** Mały plik w układzie kolumn listy projektów FE: e-usługa powiatu, droga (odpada) i prace B+R bez nazwy beneficjenta. */
    private static byte[] projectList() throws Exception {
        String[] strings = {
                "Platforma e-usług powiatu rzeszowskiego", "Mieszkańcy załatwiają sprawy urzędowe przez internet.",
                "FEPK.01.02-IP.01-0001/23", "POWIAT RZESZOWSKI", "Fundusze Europejskie dla Podkarpacia 2021-2027",
                "1.2 Cyfryzacja", "WOJ.: PODKARPACKIE, POW.: rzeszowski, GM.: Tyczyn",
                "016 Rozwiązania TIK, usługi elektroniczne",
                "Budowa drogi gminnej", "083 Infrastruktura rowerowa",
                "Prototyp czujnika drgań – prace B+R", "Opis badań nad czujnikiem.", "FENG.01.01-IP.02-0002/24",
                "Fundusze Europejskie dla Nowoczesnej Gospodarki", "WOJ.: MAZOWIECKIE", "002 Inwestycje w środki trwałe B+R"};
        StringBuilder sst = new StringBuilder("<sst>");
        for (String t : strings) {
            sst.append("<si><t>").append(t.replace("&", "&amp;")).append("</t></si>");
        }
        String sheet = "<worksheet><sheetData>"
                + "<row r=\"1\">" + s("A1", 0) + s("B1", 1) + s("C1", 2) + s("D1", 3) + s("I1", 4) + s("K1", 5)
                + n("L1", "1234567.8") + n("N1", "1000000") + s("P1", 6) + n("Q1", "45000") + n("R1", "45500") + s("S1", 7) + "</row>"
                + "<row r=\"2\">" + s("A2", 8) + s("S2", 9) + "</row>"
                + "<row r=\"3\">" + s("A3", 10) + s("B3", 11) + s("C3", 12) + s("I3", 13) + s("P3", 14)
                + n("Q3", "45300") + n("R3", "90000") + s("S3", 15) + "</row>"
                + "</sheetData></worksheet>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "xl/sharedStrings.xml", sst + "</sst>");
            put(zip, "xl/worksheets/sheet1.xml", sheet);
        }
        return bytes.toByteArray();
    }

    private static String s(String ref, int index) {
        return "<c r=\"" + ref + "\" t=\"s\"><v>" + index + "</v></c>";
    }

    private static String n(String ref, String value) {
        return "<c r=\"" + ref + "\"><v>" + value + "</v></c>";
    }

    private static FunduszeImporter importer(FakeHttp http, Path dir, List<Object> events) {
        return new FunduszeImporter(http, events::add, 5, dir.toString());
    }

    @Test
    void turnsCachedProjectListIntoRecords(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("trackidea-fundusze-777.xlsx"), projectList());
        FakeHttp http = new FakeHttp().on("api.dane.gov.pl", RESOURCE);
        FunduszeImporter importer = importer(http, dir, new ArrayList<>());

        List<SourceRecord> records = importer.fetch();

        // droga gminna nie jest innowacją (inna kategoria wsparcia) – zostają e-usługa i prace B+R
        assertThat(records).hasSize(2);
        assertThat(http.downloads).isEmpty();
        SourceRecord admin = records.getFirst();
        assertThat(admin.source()).isEqualTo("Fundusze Europejskie");
        assertThat(admin.ref()).isEqualTo("FEPK.01.02-IP.01-0001/23");
        assertThat(admin.title()).isEqualTo("Platforma e-usług powiatu rzeszowskiego");
        assertThat(admin.abstractText()).isEqualTo("Mieszkańcy załatwiają sprawy urzędowe przez internet.");
        assertThat(admin.institution()).isEqualTo("Powiat Rzeszowski");
        assertThat(admin.institutionType()).isEqualTo(InstitutionType.JST);
        assertThat(admin.city()).isEqualTo("Tyczyn");
        assertThat(admin.county()).isEqualTo("rzeszowski");
        assertThat(admin.region()).isEqualTo("Podkarpackie");
        assertThat(admin.year()).isEqualTo(FunduszeImporter.excelDate("45000").getYear());
        assertThat(admin.maturityHint()).isEqualTo(Maturity.STOSOWANE);
        assertThat(admin.areaHint()).isEqualTo(Area.ADMINISTRACYJNY);
        assertThat(admin.url()).isEqualTo(FunduszeImporter.DATASET);
        assertThat(admin.facts()).containsExactly(
                "Program: Fundusze Europejskie dla Podkarpacia 2021-2027", "Działanie: 1.2 Cyfryzacja",
                "Kategoria wsparcia: Rozwiązania TIK, usługi elektroniczne", "Numer umowy: FEPK.01.02-IP.01-0001/23",
                "Okres realizacji: " + FunduszeImporter.excelDate("45000") + " – " + FunduszeImporter.excelDate("45500"),
                "Status projektu: zakończony", "Wartość projektu: 1 234 568 zł", "Dofinansowanie UE: 1 000 000 zł",
                "Miejsce realizacji: Tyczyn, pow. rzeszowski, woj. podkarpackie");

        SourceRecord research = records.get(1);
        assertThat(research.institution()).isEqualTo("Przedsiębiorca – nazwa nieujawniona (woj. mazowieckie)");
        assertThat(research.institutionType()).isEqualTo(InstitutionType.FIRMA);
        assertThat(research.region()).isEqualTo("Mazowieckie");
        assertThat(research.city()).isNull();
        assertThat(research.maturityHint()).isEqualTo(Maturity.PROTOTYP);
        assertThat(research.areaHint()).isEqualTo(Area.TECHNOLOGICZNY);
        assertThat(research.facts()).contains("Źródło nie podaje nazwy beneficjenta", "Status projektu: w realizacji",
                "Miejsce realizacji: woj. mazowieckie");
        assertThat(importer.name()).isEqualTo("Fundusze Europejskie");
        assertThat(importer.live()).isTrue();
        assertThat(importer.homepage()).isEqualTo(FunduszeImporter.DATASET);
        assertThat(importer.description()).isNotBlank();
    }

    @Test
    void downloadsProjectListInBackgroundAndAnnouncesWhenReady(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp().on("api.dane.gov.pl", RESOURCE);
        http.downloadBody = projectList();
        List<Object> events = new CopyOnWriteArrayList<>();
        FunduszeImporter importer = importer(http, dir, events);

        // pierwszy skan: pliku jeszcze nie ma, więc źródło zgłasza „w przygotowaniu”, a pobieranie idzie w tle
        assertThatThrownBy(importer::fetch).isInstanceOf(SourceNotReady.class).hasMessageContaining("z 41 MB");

        for (int i = 0; i < 200 && events.isEmpty(); i++) {
            Thread.sleep(25);
        }
        assertThat(events).containsExactly(new SourceReadyEvent("Fundusze Europejskie"));
        assertThat(http.downloads).containsExactly("https://api.dane.gov.pl/media/lista.xlsx");
        assertThat(importer.fetch()).hasSize(2);
    }

    @Test
    void usesLastDownloadedListWhenPortalIsDown(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        http.failure = new IOException("HTTP 503 z api.dane.gov.pl");
        FunduszeImporter importer = importer(http, dir, new ArrayList<>());

        // bez pliku w pamięci podręcznej błąd portalu jest błędem źródła
        assertThatThrownBy(importer::fetch).isInstanceOf(IOException.class).hasMessageContaining("503");

        Files.write(dir.resolve("trackidea-fundusze-555.xlsx"), projectList());
        assertThat(importer.fetch()).hasSize(2);
    }

    @Test
    void reportsPortalResponseWithoutFile(@TempDir Path dir) {
        FakeHttp http = new FakeHttp().on("api.dane.gov.pl", "{\"data\":[]}");
        assertThatThrownBy(() -> importer(http, dir, new ArrayList<>()).fetch())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nie zwróciło pliku");
    }

    @Test
    void estimatesMaturityAndParsesIncompleteData() {
        FunduszeImporter.Query research = FunduszeImporter.QUERIES.get(2);
        assertThat(FunduszeImporter.maturity(research, true)).isEqualTo(Maturity.TESTY);
        assertThat(FunduszeImporter.maturity(research, false)).isEqualTo(Maturity.PROTOTYP);
        assertThat(FunduszeImporter.maturity(FunduszeImporter.QUERIES.getFirst(), false)).isEqualTo(Maturity.PILOTAZ);
        assertThat(FunduszeImporter.institutionType("Centrum Usług", FunduszeImporter.QUERIES.get(1))).isEqualTo(InstitutionType.SZKOLA);
        assertThat(FunduszeImporter.institutionType("Centrum Usług", FunduszeImporter.QUERIES.getFirst()))
                .isEqualTo(InstitutionType.INSTYTUCJA_PUBLICZNA);
        assertThat(FunduszeImporter.excelDate("nie-data")).isNull();
        assertThat(FunduszeImporter.excelDate(null)).isNull();
        assertThat(FunduszeImporter.beneficiary(null)).isEmpty();
        assertThat(FunduszeImporter.location(" ")).isEqualTo(new FunduszeImporter.Location("Polska", null, null, null));
        assertThat(FunduszeImporter.location("cała Polska").place()).isEqualTo("cała Polska");
    }

    @Test
    void reportsXlsxWithoutSheet(@TempDir Path dir) throws Exception {
        Path xlsx = dir.resolve("pusty.xlsx");
        try (OutputStream out = Files.newOutputStream(xlsx); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "inny.xml", "<x/>");
        }
        assertThatThrownBy(() -> XlsxStream.rows(xlsx, Set.of("A"))).isInstanceOf(IOException.class)
                .hasMessageContaining("Brak xl/worksheets/sheet1.xml");
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
