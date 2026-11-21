package pl.trackidea.radar;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Tryb offline: próbki eksportów z plików JSON (resources/radar), gdy importery sieciowe są wyłączone. */
@Component
public class SampleFileImporter implements SourceImporter {

    private final JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    @Override
    public String name() {
        return "Próbki offline";
    }

    @Override
    public String description() {
        return "Przykładowe rekordy z plików (działa bez internetu)";
    }

    @Override
    public String homepage() {
        return null;
    }

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public List<SourceRecord> fetch() throws Exception {
        List<SourceRecord> out = new ArrayList<>();
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:radar/*.json");
        for (Resource f : files) {
            try (InputStream in = f.getInputStream()) {
                out.addAll(json.readValue(in, new TypeReference<List<SourceRecord>>() { }));
            }
        }
        return out;
    }
}
