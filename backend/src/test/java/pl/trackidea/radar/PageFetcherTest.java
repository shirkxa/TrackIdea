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
}
