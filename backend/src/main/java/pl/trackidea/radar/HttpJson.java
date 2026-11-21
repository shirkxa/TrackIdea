package pl.trackidea.radar;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Proste pobieranie JSON-a z publicznych API (bez kluczy i bez danych użytkownika w zapytaniu). */
@Component
public class HttpJson {

    private final HttpClient client = HttpClient.newBuilder()
            .sslContext(Tls.context())
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final JsonMapper json = JsonMapper.builder().build();

    public JsonNode get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Accept", "application/json")
                .header("User-Agent", "TrackIdea-prototype (radar innowacji)")
                .GET().build();
        HttpResponse<String> res = await(client.sendAsync(req, HttpResponse.BodyHandlers.ofString()), 60, req);
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode() + " z " + req.uri().getHost());
        }
        return json.readTree(res.body());
    }

    /**
     * Pobiera duży plik (np. eksport otwartych danych) do {@code target} przez plik „.part”.
     * Przerwane pobieranie jest wznawiane od miejsca przerwania (nagłówek Range), gdy serwer to obsługuje.
     */
    public void download(String url, Path target, int timeoutSeconds) throws IOException, InterruptedException {
        Path part = partOf(target);
        long have = Files.exists(part) ? Files.size(part) : 0;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "TrackIdea-prototype (radar innowacji)")
                .GET();
        if (have > 0) {
            b.header("Range", "bytes=" + have + "-");
        }
        HttpRequest req = b.build();
        HttpResponse.BodyHandler<Path> handler = info -> switch (info.statusCode()) {
            case 206 -> HttpResponse.BodySubscribers.ofFile(part, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
            case 200 -> HttpResponse.BodySubscribers.ofFile(part, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            default -> HttpResponse.BodySubscribers.replacing(part);
        };
        HttpResponse<Path> res = await(client.sendAsync(req, handler), timeoutSeconds, req);
        if (res.statusCode() != 200 && res.statusCode() != 206) {
            throw new IOException("HTTP " + res.statusCode() + " z " + req.uri().getHost());
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Path partOf(Path target) {
        return target.resolveSibling(target.getFileName() + ".part");
    }

    /** Twardy limit czasu na całe zapytanie – zawieszone połączenie nie blokuje skanu Radaru. */
    private static <T> HttpResponse<T> await(java.util.concurrent.CompletableFuture<HttpResponse<T>> call, int seconds,
                                             HttpRequest req) throws IOException, InterruptedException {
        try {
            return call.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new IOException("Brak odpowiedzi z " + req.uri().getHost() + " w ciągu " + seconds + " s");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
        }
    }
}
