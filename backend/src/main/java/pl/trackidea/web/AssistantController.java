package pl.trackidea.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import pl.trackidea.service.AssistantService;
import pl.trackidea.service.SimilarItem;
import pl.trackidea.service.SimilarityService;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Asystent i podobieństwo", description = "Wypełnianie karty z opisu, wyszukiwanie podobnych rozwiązań")
@RequestMapping("/api")
public class AssistantController {

    private final AssistantService assistant;
    private final SimilarityService similarity;

    public AssistantController(AssistantService assistant, SimilarityService similarity) {
        this.assistant = assistant;
        this.similarity = similarity;
    }

    public record TextBody(String text, Long excludeId) {
    }

    @PostMapping("/assistant/draft")
    public AssistantService.Draft draft(@RequestBody TextBody body) {
        return assistant.draft(body.text());
    }

    /** Podobne innowacje pokazywane już w trakcie pisania zgłoszenia. */
    @PostMapping("/similar")
    public Map<String, List<SimilarItem>> similar(@RequestBody TextBody body) {
        String text = body.text() == null ? "" : body.text();
        return Map.of(
                "innovations", similarity.similarInnovations(text, body.excludeId(), 5),
                "priorArt", similarity.similarPriorArt(text, 3));
    }
}
