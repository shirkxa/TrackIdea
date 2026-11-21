package pl.trackidea.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import pl.trackidea.service.SimilarItem;
import pl.trackidea.service.SimilarityService;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Podobieństwo", description = "Wyszukiwanie podobnych rozwiązań i patentów do dowolnego opisu")
@RequestMapping("/api")
public class AssistantController {

    private final SimilarityService similarity;

    public AssistantController(SimilarityService similarity) {
        this.similarity = similarity;
    }

    public record TextBody(String text, Long excludeId) {
    }

    /** Podobne innowacje do dowolnego opisu (np. potrzeby albo zapytania). */
    @PostMapping("/similar")
    public Map<String, List<SimilarItem>> similar(@RequestBody TextBody body) {
        String text = body.text() == null ? "" : body.text();
        return Map.of(
                "innovations", similarity.similarInnovations(text, body.excludeId(), 5),
                "priorArt", similarity.similarPriorArt(text, 3));
    }
}
