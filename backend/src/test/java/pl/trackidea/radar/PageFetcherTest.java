package pl.trackidea.radar;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pobieranie stron wskazanych przez analityka – bez łączenia z internetem. */
class PageFetcherTest {

    @Test
    void rejectsInternalAddressesAndOtherSchemes() {
        for (String url : new String[]{"http://localhost:8080/api", "http://127.0.0.1/", "http://10.0.0.5/",
                "http://192.168.1.1/", "http://169.254.169.254/latest/meta-data", "http://[::1]/",
                "file:///etc/passwd", "ftp://example.org/", "nie-adres"}) {
            assertThatThrownBy(() -> PageFetcher.checked(url)).as(url).isInstanceOf(IOException.class);
        }
    }

    @Test
    void extractsTitleAndMainTextWithoutScriptsAndMenus() {
        String html = """
                <html><head><title>Urząd – strona</title><script>alert('x')</script><style>p{}</style></head>
                <body><nav>Menu Start Kontakt</nav>
                <h1>E-usługa &quot;Zgłoś usterkę&quot; w&nbsp;Tyczynie</h1>
                <p>Mieszkańcy zgłaszają usterki przez aplikację.<br>W 2025 r. naprawiono 312 zgłoszeń.</p>
                <footer>© Gmina</footer></body></html>""";
        assertThat(PageFetcher.title(html)).isEqualTo("E-usługa \"Zgłoś usterkę\" w Tyczynie");
        String text = PageFetcher.text(html);
        assertThat(text).contains("Mieszkańcy zgłaszają usterki", "312 zgłoszeń");
        assertThat(text).doesNotContain("alert", "Menu Start", "© Gmina");
    }

    @Test
    void acceptsPublicAddressesAndRejectsMoreInternalOnes() throws Exception {
        // adresy IP – sprawdzenie nie pyta DNS
        assertThat(PageFetcher.checked(" https://93.184.216.34/strona ").toString()).isEqualTo("https://93.184.216.34/strona");
        for (String url : new String[]{null, "", "http://", "http://[fd00::1]/", "http://0.0.0.0/", "http://224.0.0.1/",
                "http://exa mple.org/"}) {
            assertThatThrownBy(() -> PageFetcher.checked(url)).as(String.valueOf(url)).isInstanceOf(IOException.class);
        }
    }

    @Test
    void picksTitleFromMetaHeadingOrTitleTag() {
        assertThat(PageFetcher.title("<head><meta property=\"og:title\" content=\"Projekt &amp; efekty\"><title>Inny</title></head>"))
                .isEqualTo("Projekt & efekty");
        assertThat(PageFetcher.title("<h1> </h1><title>Gmina  Tyczyn &#8211; aktualności</title>")).isEqualTo("Gmina Tyczyn – aktualności");
        assertThat(PageFetcher.title("<p>bez tytułu</p>")).isNull();
    }

    @Test
    void prefersMainContentAndDecodesEntities() {
        String article = "Szkoła prowadzi zajęcia z robotyki dla klas 4&ndash;6 &lt;od 2024&gt;. ".repeat(10);
        String html = "<body><div>Baner z reklamą</div><main><p>" + article + "</p><!-- komentarz --></main><form>Szukaj</form></body>";

        String text = PageFetcher.text(html);

        assertThat(text).contains("Szkoła prowadzi zajęcia z robotyki", "<od 2024>");
        assertThat(text).doesNotContain("Baner z reklamą", "komentarz", "Szukaj");
        // bez sekcji <main> brana jest cała strona, a encje liczbowe (dziesiętne i szesnastkowe) są dekodowane
        assertThat(PageFetcher.text("<p>Zażółć g&#281;&#x15b;l&#261; ja&#378;&#324;</p>")).isEqualTo("Zażółć gęślą jaźń");
        // encja spoza zakresu Unicode nie psuje tekstu
        assertThat(PageFetcher.text("<p>A&#99999999999;B&#x110000;C</p>")).isEqualTo("A B C");
        assertThat(PageFetcher.text("<p>" + "x".repeat(PageFetcher.MAX_TEXT + 100) + "</p>")).hasSize(PageFetcher.MAX_TEXT);
    }
}
