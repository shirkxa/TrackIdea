package pl.trackidea.radar;

import org.junit.jupiter.api.Test;
import pl.trackidea.domain.InstitutionType;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rekord wskazany przez analityka: strona albo wklejona treść dokumentu – bez łączenia z internetem. */
class ManualSourceTest {

    private static final String TEXT = "Gmina wdrożyła system zgłaszania usterek oświetlenia ulicznego przez aplikację mobilną. "
            + "Mieszkańcy dodają zdjęcie i lokalizację, a zgłoszenie trafia od razu do wykonawcy. "
            + "Rozwiązanie działa od 2024 roku, czas naprawy skrócił się z 9 do 3 dni.";

    /** Zamiast pobierania strony oddaje przygotowaną treść. */
    private static ManualSource withPage(PageFetcher.Page page) {
        return new ManualSource(new PageFetcher() {
            @Override
            public Page fetch(String url) {
                return page;
            }
        });
    }

    @Test
    void turnsPastedDocumentIntoRecord() throws Exception {
        SourceRecord r = withPage(null).fromText("  Zgłoś usterkę  ", "  " + TEXT + "  ", " Gmina Tyczyn ", null);

        assertThat(r.source()).isEqualTo(ManualSource.DOCUMENT);
        assertThat(r.ref()).matches("tekst-[0-9a-f]{12}");
        assertThat(r.title()).isEqualTo("Zgłoś usterkę");
        assertThat(r.abstractText()).isEqualTo(TEXT);
        assertThat(r.institution()).isEqualTo("Gmina Tyczyn");
        assertThat(r.institutionType()).isEqualTo(InstitutionType.JST);
        assertThat(r.url()).isNull();
        assertThat(r.facts()).first().isEqualTo("Treść dokumentu wkleił analityk PCI");
        // ten sam tekst daje ten sam identyfikator – ponowne wklejenie nie tworzy drugiej karty
        assertThat(withPage(null).fromText(null, TEXT, null, " ").ref()).isEqualTo(r.ref());
    }

    @Test
    void pastedDocumentKeepsLinkAndDefaults() throws Exception {
        // adres IP zamiast nazwy – sprawdzenie adresu nie pyta DNS
        SourceRecord r = withPage(null).fromText(null, TEXT, " ", "https://93.184.216.34/raport.pdf");

        assertThat(r.ref()).isEqualTo("https://93.184.216.34/raport.pdf");
        assertThat(r.url()).isEqualTo("https://93.184.216.34/raport.pdf");
        assertThat(r.title()).isEmpty();
        assertThat(r.institution()).isEqualTo("Instytucja nieznana");
        assertThat(r.institutionType()).isEqualTo(InstitutionType.INSTYTUCJA_PUBLICZNA);
    }

    @Test
    void cutsVeryLongDocuments() throws Exception {
        String longText = "Opis rozwiązania. ".repeat(2000);
        assertThat(withPage(null).fromText("Raport", longText, null, null).abstractText()).hasSize(PageFetcher.MAX_TEXT);
    }

    @Test
    void rejectsTooShortTextAndInternalLinks() {
        assertThatThrownBy(() -> withPage(null).fromText("Tytuł", "Za krótko.", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("min. 200 znaków");
        assertThatThrownBy(() -> withPage(null).fromText("Tytuł", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withPage(null).fromText("Tytuł", TEXT, null, "http://127.0.0.1/admin"))
                .isInstanceOf(IOException.class).hasMessageContaining("wewnętrzny");
    }

    @Test
    void turnsFetchedPageIntoRecord() throws Exception {
        PageFetcher.Page page = new PageFetcher.Page("https://www.gmina.example/projekt", "E-usługa „Zgłoś usterkę”", TEXT);

        SourceRecord r = withPage(page).fromPage("https://www.gmina.example/projekt", null);

        assertThat(r.source()).isEqualTo(ManualSource.PAGE);
        assertThat(r.ref()).isEqualTo("https://www.gmina.example/projekt");
        assertThat(r.url()).isEqualTo("https://www.gmina.example/projekt");
        assertThat(r.title()).isEqualTo("E-usługa „Zgłoś usterkę”");
        assertThat(r.abstractText()).isEqualTo(TEXT);
        // bez podanej instytucji używana jest domena strony (bez „www.”)
        assertThat(r.institution()).isEqualTo("gmina.example");
        assertThat(r.facts()).contains("Strona źródłowa: gmina.example", "Tytuł strony: E-usługa „Zgłoś usterkę”");
    }

    @Test
    void fetchedPageUsesGivenInstitutionAndToleratesMissingTitle() throws Exception {
        PageFetcher.Page page = new PageFetcher.Page("https://sp5.example/innowacja", null, TEXT);

        SourceRecord r = withPage(page).fromPage("https://sp5.example/innowacja", " Szkoła Podstawowa nr 5 ");

        assertThat(r.title()).isEmpty();
        assertThat(r.institution()).isEqualTo("Szkoła Podstawowa nr 5");
        assertThat(r.institutionType()).isEqualTo(InstitutionType.SZKOLA);
        assertThat(r.facts()).noneMatch(f -> f.startsWith("Tytuł strony"));
    }

    @Test
    void rejectsPageWithTooLittleText() {
        PageFetcher.Page page = new PageFetcher.Page("https://gmina.example/", "Start", "Witamy na stronie.");
        assertThatThrownBy(() -> withPage(page).fromPage("https://gmina.example/", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("za mało tekstu");
    }
}
