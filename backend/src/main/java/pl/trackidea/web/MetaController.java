package pl.trackidea.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.trackidea.domain.*;
import pl.trackidea.service.AssistantService;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Słowniki z polskimi etykietami – frontend nie duplikuje enumów. */
@RestController
@Tag(name = "Słowniki", description = "Typy, sektory, etapy, statusy i kryteria oceny z wagami")
@RequestMapping("/api/meta")
public class MetaController {

    private final AssistantService assistant;

    public MetaController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @GetMapping
    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("types", values(InnovationType.values()));
        m.put("sectors", values(Sector.values()));
        m.put("maturities", values(Maturity.values()));
        m.put("transferModels", values(TransferModel.values()));
        m.put("institutionTypes", values(InstitutionType.values()));
        m.put("roles", values(Role.values()));
        m.put("paths", values(PathType.values()));
        m.put("statuses", Arrays.stream(InnovationStatus.values()).map(Dto.StatusView::of).toList());
        m.put("criteria", Arrays.stream(Criterion.values()).map(Dto.CriterionView::of).toList());
        m.put("trlLevels", java.util.stream.IntStream.rangeClosed(1, 9).mapToObj(Dto.TrlView::of).toList());
        m.put("aiEnabled", assistant.isAiEnabled());
        return m;
    }

    private static List<Dto.EnumValue> values(Labeled[] values) {
        return Arrays.stream(values).map(Dto.EnumValue::of).toList();
    }
}
