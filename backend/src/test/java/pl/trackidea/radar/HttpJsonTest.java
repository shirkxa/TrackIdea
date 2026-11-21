package pl.trackidea.radar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pobieranie JSON-a i dużych plików – na lokalnym serwerze HTTP uruchamianym na czas testu. */
class HttpJsonTest {

    private static final byte[] FILE = "0123456789".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String base;
    private final List<String> ranges = new ArrayList<>();
    private final HttpJson http = new HttpJson();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", ex -> respond(ex, 200, "{\"name\":\"Radar\",\"count\":2}".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/fail", ex -> respond(ex, 503, new byte[0]));
        server.createContext("/file", ex -> {
            String range = ex.getRequestHeaders().getFirst("Range");
            ranges.add(range);
            if (range == null) {
                respond(ex, 200, FILE);
            } else {
                int from = Integer.parseInt(range.replaceAll("\\D", ""));
                respond(ex, 206, Arrays.copyOfRange(FILE, from, FILE.length));
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange ex, int status, byte[] body) throws IOException {
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            ex.getResponseBody().write(body);
        }
        ex.close();
    }

    @Test
    void readsJson() throws Exception {
        assertThat(http.get(base + "/ok").path("name").asString("")).isEqualTo("Radar");
        assertThat(http.get(base + "/ok").path("count").asInt()).isEqualTo(2);
    }

    @Test
    void reportsHttpErrorWithHostName() {
        assertThatThrownBy(() -> http.get(base + "/fail")).isInstanceOf(IOException.class).hasMessage("HTTP 503 z 127.0.0.1");
    }

    @Test
    void downloadsFileThroughPartFile(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("lista.xlsx");

        http.download(base + "/file", target, 10);

        assertThat(Files.readAllBytes(target)).isEqualTo(FILE);
        assertThat(Files.exists(HttpJson.partOf(target))).isFalse();
        assertThat(ranges).containsExactly((String) null);
    }

    @Test
    void resumesInterruptedDownload(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("lista.xlsx");
        // przerwane pobieranie zostawiło pierwsze 4 bajty w pliku „.part”
        Files.write(HttpJson.partOf(target), Arrays.copyOf(FILE, 4));

        http.download(base + "/file", target, 10);

        assertThat(ranges).containsExactly("bytes=4-");
        assertThat(Files.readAllBytes(target)).isEqualTo(FILE);
    }

    @Test
    void failedDownloadLeavesNoTargetFile(@TempDir Path dir) {
        Path target = dir.resolve("lista.xlsx");
        assertThatThrownBy(() -> http.download(base + "/fail", target, 10)).isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 503");
        assertThat(Files.exists(target)).isFalse();
    }

    @Test
    void namesPartFileNextToTarget() {
        assertThat(HttpJson.partOf(Path.of("/tmp/dane/lista.xlsx"))).isEqualTo(Path.of("/tmp/dane/lista.xlsx.part"));
    }
}
