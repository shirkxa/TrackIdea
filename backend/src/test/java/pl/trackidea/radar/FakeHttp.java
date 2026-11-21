package pl.trackidea.radar;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Klient HTTP do testów: zamiast internetu oddaje przygotowane odpowiedzi dopasowane po fragmencie adresu. */
class FakeHttp extends HttpJson {

    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, String> responses = new LinkedHashMap<>();
    final List<String> requested = new ArrayList<>();
    final List<String> downloads = new ArrayList<>();
    /** Gdy ustawione, każde zapytanie kończy się tym błędem (źródło niedostępne). */
    IOException failure;
    /** Treść „pobieranego” pliku; null = pobieranie się nie udaje. */
    byte[] downloadBody;

    FakeHttp on(String urlPart, String body) {
        responses.put(urlPart, body);
        return this;
    }

    @Override
    public JsonNode get(String url) throws IOException {
        requested.add(url);
        if (failure != null) {
            throw failure;
        }
        for (Map.Entry<String, String> e : responses.entrySet()) {
            if (url.contains(e.getKey())) {
                return json.readTree(e.getValue());
            }
        }
        throw new IOException("HTTP 404 z testu");
    }

    @Override
    public void download(String url, Path target, int timeoutSeconds) throws IOException {
        downloads.add(url);
        if (downloadBody == null) {
            throw new IOException("HTTP 503 z testu");
        }
        Files.write(target, downloadBody);
    }
}
