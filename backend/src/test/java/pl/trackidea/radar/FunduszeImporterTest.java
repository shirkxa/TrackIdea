package pl.trackidea.radar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Maturity;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
