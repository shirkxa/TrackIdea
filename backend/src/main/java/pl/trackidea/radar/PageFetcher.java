package pl.trackidea.radar;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pobiera stronę wskazaną przez analityka (np. opis projektu na stronie gminy lub szkoły) i wyciąga z niej tekst.
 * Chroni serwer: tylko http/https, bez adresów wewnętrznych (także po przekierowaniu), limit rozmiaru i czasu.
 */
@Component
public class PageFetcher {

    public record Page(String url, String title, String text) {
    }

    static final int MAX_BYTES = 2_000_000;
    static final int MAX_TEXT = 15_000;
    private static final int MAX_REDIRECTS = 3;

    private final HttpClient client = HttpClient.newBuilder()
            .sslContext(Tls.context())
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Page fetch(String url) throws IOException, InterruptedException {
        URI uri = checked(url);
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "TrackIdea-prototype (radar innowacji)")
                    .header("Accept", "text/html,text/plain;q=0.9")
                    .GET().build();
            HttpResponse<InputStream> res;
            try {
                res = client.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new IOException("Strona nie odpowiedziała w ciągu 30 s");
            } catch (ExecutionException e) {
                throw e.getCause() instanceof IOException io ? io : new IOException("Nie udało się pobrać strony");
            }
            int status = res.statusCode();
            if (status >= 300 && status < 400) {
                res.body().close();
                String location = res.headers().firstValue("Location")
                        .orElseThrow(() -> new IOException("Przekierowanie bez adresu"));
                uri = checked(uri.resolve(location).toString());
                continue;
            }
            if (status != 200) {
                res.body().close();
                throw new IOException("Strona zwróciła HTTP " + status);
            }
            String type = res.headers().firstValue("Content-Type").orElse("text/html").toLowerCase(Locale.ROOT);
            if (!type.contains("text/html") && !type.contains("text/plain")) {
                res.body().close();
                throw new IOException("To nie jest strona HTML (" + type.split(";")[0]
                        + "). Treść dokumentu (np. PDF) wklej w pole tekstowe.");
            }
            byte[] body;
            try (InputStream in = res.body()) {
                body = in.readNBytes(MAX_BYTES);
            }
            String html = new String(body, charset(type));
            return type.contains("text/plain")
                    ? new Page(uri.toString(), null, shorten(html.replaceAll("\\s+", " ").trim()))
                    : new Page(uri.toString(), title(html), text(html));
        }
        throw new IOException("Za dużo przekierowań");
    }

    /** Tylko publiczne adresy http/https – bez localhost, sieci wewnętrznych i adresów chmury (169.254.x.x). */
    static URI checked(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            throw new IOException("Nieprawidłowy adres strony");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
            throw new IOException("Podaj adres zaczynający się od http:// lub https://");
        }
        for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
            if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()
                    || a.isMulticastAddress() || isUniqueLocalV6(a)) {
                throw new IOException("Adres wewnętrzny jest niedozwolony");
            }
        }
        return uri;
    }

    private static boolean isUniqueLocalV6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    private static Charset charset(String contentType) {
        Matcher m = Pattern.compile("charset=([\\w-]+)").matcher(contentType);
        try {
            return m.find() ? Charset.forName(m.group(1)) : StandardCharsets.UTF_8;
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    static String title(String html) {
        for (String regex : new String[]{
                "<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)",
                "<h1[^>]*>(.*?)</h1>",
                "<title[^>]*>(.*?)</title>"}) {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
            if (m.find()) {
                String t = entities(m.group(1).replaceAll("<[^>]+>", " ")).replaceAll("\\s+", " ").trim();
                if (!t.isBlank()) return t;
            }
        }
        return null;
    }

    /** Tekst treści strony: bez skryptów, stylów, menu i stopki. */
    static String text(String html) {
        String s = html.replaceAll("(?is)<(script|style|noscript|nav|header|footer|svg|form)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<!--.*?-->", " ");
        Matcher main = Pattern.compile("(?is)<(main|article)[^>]*>(.*?)</\\1>").matcher(s);
        if (main.find() && main.group(2).length() > 500) {
            s = main.group(2);
        }
        s = s.replaceAll("(?i)<(br|/p|/div|/li|/h\\d)[^>]*>", "\n").replaceAll("<[^>]+>", " ");
        s = entities(s).replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\s*\\n\\s*", "\n").trim();
        return shorten(s);
    }

    private static String shorten(String s) {
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    private static String entities(String s) {
        Matcher m = Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int cp;
            try {
                cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                cp = ' ';
            }
            m.appendReplacement(out, Matcher.quoteReplacement(Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : " "));
        }
        m.appendTail(out);
        return out.toString().replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&oacute;", "ó").replace("&amp;", "&");
    }
}
