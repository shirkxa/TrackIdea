package pl.trackidea.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.trackidea.domain.*;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.service.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/innovations")
@Tag(name = "Innowacje", description = "Katalog, karta innowacji, weryfikacja i ocena, zastosowania, dopasowania")
public class InnovationController {

    private final InnovationRepository innovations;
    private final InnovationService service;
    private final SimilarityService similarity;
    private final RecommendationService recommendations;
    private final MatchingService matching;
    private final Views views;
    private final CurrentUser currentUser;
    private final String publicUrl;

    public InnovationController(InnovationRepository innovations, InnovationService service,
                                SimilarityService similarity, RecommendationService recommendations,
                                MatchingService matching, Views views,
                                CurrentUser currentUser,
                                @Value("${trackidea.public-url:http://localhost:5173}") String publicUrl) {
        this.publicUrl = publicUrl;
        this.innovations = innovations;
        this.service = service;
        this.similarity = similarity;
        this.recommendations = recommendations;
        this.matching = matching;
        this.views = views;
        this.currentUser = currentUser;
    }

    /** Katalog: wyszukiwanie po treści (podobieństwo i początki wyrazów), filtry i sortowanie. */
    @GetMapping
    @Operation(summary = "Katalog innowacji", description = "Wyszukiwanie po treści (q), filtr po nazwie instytucji lub firmy (institution), pozostałe filtry i sortowanie: newest, updated, score, trl, maturity, relevance.")
    public List<Dto.InnovationCard> list(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Area area,
                                         @RequestParam(required = false) InnovationType type,
                                         @RequestParam(required = false) Sector sector,
                                         @RequestParam(required = false) Maturity maturity,
                                         @RequestParam(required = false) TransferModel transferModel,
                                         @RequestParam(required = false) InstitutionType institutionType,
                                         @RequestParam(required = false) InnovationStatus status,
                                         @RequestParam(required = false) Long institutionId,
                                         @RequestParam(required = false) String institution,
                                         @RequestParam(required = false) Integer minTrl,
                                         @RequestParam(defaultValue = "newest") String sort) {
        Stream<Innovation> stream = innovations.findAll().stream().filter(i -> i.getStatus().isPubliclyVisible());
        if (area != null) stream = stream.filter(i -> i.getArea() == area);
        if (type != null) stream = stream.filter(i -> i.getType() == type);
        if (sector != null) stream = stream.filter(i -> i.getSector() == sector);
        if (maturity != null) stream = stream.filter(i -> i.getMaturity() == maturity);
        if (transferModel != null) stream = stream.filter(i -> i.getTransferModel() == transferModel);
        if (status != null) stream = stream.filter(i -> i.getStatus() == status);
        if (minTrl != null) stream = stream.filter(i -> i.getTrl() != null && i.getTrl() >= minTrl);
        if (institutionId != null) stream = stream.filter(i -> i.getInstitution() != null && i.getInstitution().getId().equals(institutionId));
        if (institutionType != null) stream = stream.filter(i -> i.getInstitution() != null && i.getInstitution().getType() == institutionType);
        if (institution != null && !institution.isBlank()) {
            // wyszukiwanie po nazwie instytucji lub firmy (bez wielkości liter i polskich znaków)
            String needle = TextVectorizer.normalize(institution.trim());
            stream = stream.filter(i -> i.getInstitution() != null
                    && TextVectorizer.normalize(i.getInstitution().getName()).contains(needle));
        }
        List<Innovation> found = stream.toList();

        Map<Long, Double> relevance = new HashMap<>();
        if (q != null && !q.isBlank()) {
            SimilarityService.Index idx = similarity.index();
            Map<String, Double> query = idx.vector(q);
            String nq = TextVectorizer.normalize(q);
            List<String> words = Arrays.stream(nq.split("[^a-z0-9]+")).filter(w -> w.length() >= 4).toList();
            List<Innovation> matched = new ArrayList<>();
            for (Innovation i : found) {
                String text = TextVectorizer.normalize(i.searchText());
                double score = TextVectorizer.cosine(query, idx.vector(i.searchText()));
                if (text.contains(nq)) score += 0.5;
                // „dron” znajduje też „drona” i „drony”: wyraz zapytania jako początek wyrazu w karcie
                long prefixHits = words.stream().filter(w -> startsWord(text, w)).count();
                if (!words.isEmpty()) score += 0.3 * prefixHits / words.size();
                if (score >= 0.06) {
                    relevance.put(i.getId(), score);
                    matched.add(i);
                }
            }
            found = matched;
        }

        List<Dto.InnovationCard> cards = new ArrayList<>(found.stream().map(views::card).toList());
        Comparator<Dto.InnovationCard> order = switch (sort) {
            case "score" -> Comparator.comparing((Dto.InnovationCard c) -> c.score() == null ? -1 : c.score()).reversed();
            case "updated" -> Comparator.comparing(Dto.InnovationCard::updatedAt).reversed();
            case "trl" -> Comparator.comparing((Dto.InnovationCard c) -> c.trl() == null ? 0 : c.trl().level()).reversed();
            case "maturity" -> Comparator.comparing((Dto.InnovationCard c) ->
                    c.maturity() == null ? 0 : Maturity.valueOf(c.maturity().value()).getLevel()).reversed();
            case "relevance" -> Comparator.comparing((Dto.InnovationCard c) -> relevance.getOrDefault(c.id(), 0.0)).reversed();
            default -> Comparator.comparing(Dto.InnovationCard::createdAt).reversed();
        };
        if (!relevance.isEmpty() && "newest".equals(sort)) {
            order = Comparator.comparing((Dto.InnovationCard c) -> relevance.getOrDefault(c.id(), 0.0)).reversed();
        }
        cards.sort(order);
        return cards;
    }

    /**
     * Eksport katalogu do CSV (Excel: UTF-8 z BOM, separator „;”) – te same filtry co katalog.
     * Ułatwia integrację z arkuszami i systemami instytucji.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Eksport katalogu do CSV", description = "Przyjmuje te same parametry co katalog. Plik otwiera się bezpośrednio w Excelu.")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Area area,
                                         @RequestParam(required = false) InnovationType type,
                                         @RequestParam(required = false) Sector sector,
                                         @RequestParam(required = false) Maturity maturity,
                                         @RequestParam(required = false) TransferModel transferModel,
                                         @RequestParam(required = false) InstitutionType institutionType,
                                         @RequestParam(required = false) InnovationStatus status,
                                         @RequestParam(required = false) Long institutionId,
                                         @RequestParam(required = false) String institution,
                                         @RequestParam(required = false) Integer minTrl,
                                         @RequestParam(defaultValue = "newest") String sort) {
        List<Dto.InnovationCard> cards = list(q, area, type, sector, maturity, transferModel, institutionType, status,
                institutionId, institution, minTrl, sort);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(";", "ID", "Tytuł", "Instytucja", "Typ instytucji", "Powiat", "Obszar", "Typ innowacji",
                "Sektor", "Etap", "TRL", "Model transferu", "Status", "Wynik (0-100)", "Autorzy", "Źródło", "Data dodania",
                "Tagi", "Link")).append("\r\n");
        for (Dto.InnovationCard c : cards) {
            csv.append(String.join(";",
                    String.valueOf(c.id()),
                    cell(c.title()),
                    cell(c.institution() == null ? "" : c.institution().name()),
                    cell(label(c.institution() == null ? null : c.institution().type())),
                    cell(c.institution() == null ? "" : c.institution().county()),
                    cell(label(c.area())),
                    cell(label(c.type())),
                    cell(label(c.sector())),
                    cell(label(c.maturity())),
                    c.trl() == null ? "" : c.trl().level() + (c.estimated().contains("trl") ? " (szacunek)" : ""),
                    cell(label(c.transferModel())),
                    cell(c.status().label()),
                    c.score() == null ? "" : String.valueOf(Math.round(c.score())),
                    cell(c.authors()),
                    cell(c.sourceName() != null ? c.source().label() + " (" + c.sourceName() + ")" : label(c.source())),
                    LocalDate.ofInstant(c.createdAt(), ZoneId.of("Europe/Warsaw")).toString(),
                    cell(String.join(", ", c.tags())),
                    cell(publicUrl + "/innovations/" + c.id())))
                    .append("\r\n");
        }
        String filename = "trackidea-innowacje-" + LocalDate.now(ZoneId.of("Europe/Warsaw")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean startsWord(String text, String word) {
        for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + 1)) {
            if (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1))) {
                return true;
            }
        }
        return false;
    }

    /** Karta wyłączona z katalogu (odrzucona, połączona) jest dostępna tylko dla zespołu PCI – także jej powiązania. */
    private Innovation visible(Long id) {
        Innovation inn = service.get(id);
        if (!service.canView(inn, currentUser.get())) {
            throw DomainException.forbidden("Ten rekord jest wyłączony z katalogu.");
        }
        return inn;
    }

    private static String label(Dto.EnumValue v) {
        return v == null ? "" : v.label();
    }

    /**
     * Pole CSV w cudzysłowie, z podwojonymi cudzysłowami w treści. Tekst ze źródeł zewnętrznych, który arkusz
     * mógłby potraktować jak formułę (zaczyna się od =, +, - lub @), dostaje apostrof na początku.
     */
    private static String cell(String value) {
        String v = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /**
     * Porównanie: do 4 innowacji w jednym zapytaniu (zamiast osobnego zapytania na każdą kartę).
     * Rekordy usunięte lub wyłączone z katalogu są pomijane.
     */
    @GetMapping("/compare")
    public List<Dto.InnovationDetail> compare(@RequestParam List<Long> ids) {
        Person viewer = currentUser.get();
        List<Long> wanted = ids.stream().distinct().limit(4).toList();
        Map<Long, Innovation> found = new HashMap<>();
        innovations.findAllById(wanted).forEach(i -> found.put(i.getId(), i));
        return wanted.stream()
                .map(found::get)
                .filter(i -> i != null && service.canView(i, viewer))
                .map(i -> views.detail(i, viewer))
                .toList();
    }

    @GetMapping("/{id}")
    public Dto.InnovationDetail get(@PathVariable Long id) {
        Person viewer = currentUser.get();
        Innovation inn = service.get(id);
        if (!service.canView(inn, viewer)) {
            throw DomainException.forbidden("Ten rekord jest wyłączony z katalogu.");
        }
        return views.detail(inn, viewer);
    }

    @GetMapping("/{id}/similar")
    public Map<String, List<SimilarItem>> similar(@PathVariable Long id) {
        Innovation inn = visible(id);
        return Map.of(
                "innovations", similarity.similarInnovations(inn.searchText(), id, 5),
                "priorArt", similarity.similarPriorArt(inn.searchText(), 4));
    }

    @GetMapping("/{id}/recommendations")
    public List<RecommendationService.PathRecommendation> recommendations(@PathVariable Long id) {
        return recommendations.recommend(visible(id));
    }

    @GetMapping("/{id}/matches")
    public Map<String, List<MatchingService.Match>> matches(@PathVariable Long id) {
        Innovation inn = visible(id);
        return Map.of("needs", matching.needsFor(inn));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Poprawa karty przez analityka PCI")
    public Dto.InnovationDetail update(@PathVariable Long id, @RequestBody InnovationService.CardEdit form) {
        Person actor = currentUser.get();
        return views.detail(service.update(id, form, actor), actor);
    }

    @PostMapping("/{id}/propose-assessment")
    @Operation(summary = "Wygeneruj propozycję oceny (analityk PCI)")
    public Dto.InnovationDetail proposeAssessment(@PathVariable Long id) {
        Person actor = currentUser.get();
        return views.detail(service.proposeAssessment(id, actor), actor);
    }

    public record ApproveRequest(List<InnovationService.ScoreInput> scores) {
    }

    @PostMapping("/{id}/assessment/approve")
    public Dto.InnovationDetail approve(@PathVariable Long id, @RequestBody ApproveRequest body) {
        Person actor = currentUser.get();
        return views.detail(service.approveAssessment(id, body.scores(), actor), actor);
    }

    public record PathsRequest(List<PathType> paths) {
    }

    @PostMapping("/{id}/paths")
    public Dto.InnovationDetail paths(@PathVariable Long id, @RequestBody PathsRequest body) {
        Person actor = currentUser.get();
        return views.detail(service.choosePaths(id, body.paths(), actor), actor);
    }

    public record RejectRequest(String reason) {
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Przywróć odrzucony rekord do katalogu (analityk PCI)")
    public Dto.InnovationDetail restore(@PathVariable Long id) {
        Person actor = currentUser.get();
        return views.detail(service.restore(id, actor), actor);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Odrzuć rekord, który nie jest innowacją (analityk PCI)")
    public Dto.InnovationDetail reject(@PathVariable Long id, @RequestBody RejectRequest body) {
        Person actor = currentUser.get();
        return views.detail(service.reject(id, body.reason(), actor), actor);
    }
}
