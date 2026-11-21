package pl.trackidea.radar;

import org.springframework.stereotype.Component;
import pl.trackidea.domain.InstitutionType;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Rekord dodany przez analityka PCI: strona samorządu, szkoły, uczelni lub treść raportu/dokumentu.
 * To nie jest zgłaszanie pomysłów – analityk wskazuje opis istniejącego rozwiązania, Radar go analizuje.
 */
@Component
public class ManualSource {

    public static final String PAGE = "Analiza strony";
    public static final String DOCUMENT = "Analiza dokumentu";

    private final PageFetcher pages;

    public ManualSource(PageFetcher pages) {
        this.pages = pages;
    }

    public SourceRecord fromPage(String url, String institution) throws Exception {
        PageFetcher.Page page = pages.fetch(url);
        if (page.text().length() < 200) {
            throw new IllegalArgumentException("Na stronie jest za mało tekstu do analizy – wklej treść ręcznie.");
        }
        String host = URI.create(page.url()).getHost().replaceFirst("^www\\.", "");
        String inst = blank(institution) ? host : institution.trim();
        List<String> facts = new ArrayList<>();
        facts.add("Strona źródłowa: " + host);
        if (page.title() != null) facts.add("Tytuł strony: " + Names.shorten(page.title(), 200));
        facts.add("Pobrano: " + LocalDate.now());
        return new SourceRecord(PAGE, page.url(), page.title() == null ? "" : Names.shorten(page.title(), 240),
                page.text(), inst, Names.institutionType(inst, InstitutionType.INSTYTUCJA_PUBLICZNA),
                null, null, "Polska", null, null, List.of(), page.url(), null, null, null, facts);
    }

    public SourceRecord fromText(String title, String text, String institution, String link) throws Exception {
        if (blank(text) || text.trim().length() < 200) {
            throw new IllegalArgumentException("Wklej co najmniej kilka zdań opisu (min. 200 znaków).");
        }
        String clean = text.trim();
        String body = clean.length() > PageFetcher.MAX_TEXT ? clean.substring(0, PageFetcher.MAX_TEXT) : clean;
        String url = blank(link) ? null : PageFetcher.checked(link).toString();
        String inst = blank(institution) ? "Instytucja nieznana" : institution.trim();
        List<String> facts = new ArrayList<>();
        facts.add("Treść dokumentu wkleił analityk PCI");
        facts.add("Dodano: " + LocalDate.now());
        return new SourceRecord(DOCUMENT, url != null ? url : "tekst-" + hash(body), blank(title) ? "" : title.trim(),
                body, inst, Names.institutionType(inst, InstitutionType.INSTYTUCJA_PUBLICZNA),
                null, null, "Polska", null, null, List.of(), url, null, null, null, facts);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String hash(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(d, 0, 6);
    }
}
