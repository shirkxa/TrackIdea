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
@Tag(name = "Innowacje", description = "Katalog, karta innowacji, cykl życia (statusy), ocena, ścieżki, dopasowania")
public class InnovationController {

    private final InnovationRepository innovations;
    private final InnovationService service;
    private final SimilarityService similarity;
    private final RecommendationService recommendations;
    private final MatchingService matching;
    private final AssessmentService assessmentService;
    private final Views views;
    private final CurrentUser currentUser;
    private final String publicUrl;

    public InnovationController(InnovationRepository innovations, InnovationService service,
                                SimilarityService similarity, RecommendationService recommendations,
                                MatchingService matching, AssessmentService assessmentService, Views views,
                                CurrentUser currentUser,
                                @Value("${trackidea.public-url:http://localhost:5173}") String publicUrl) {
        this.publicUrl = publicUrl;
        this.innovations = innovations;
        this.service = service;
        this.similarity = similarity;
        this.recommendations = recommendations;
        this.matching = matching;
        this.assessmentService = assessmentService;
        this.views = views;
        this.currentUser = currentUser;
    }

    /**
     * Katalog: wyszukiwanie pełnotekstowe i semantyczne, filtry i sortowanie.
     * scope=public (domyślnie) – tylko potwierdzone; scope=mine – moje i mojej instytucji (też szkice).
     */
    @GetMapping
    @Operation(summary = "Katalog innowacji", description = "Wyszukiwanie semantyczne (q), filtry i sortowanie: newest, score, popular, forks, relevance.")
    public List<Dto.InnovationCard> list(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) InnovationType type,
                                         @RequestParam(required = false) Sector sector,
                                         @RequestParam(required = false) Maturity maturity,
                                         @RequestParam(required = false) TransferModel transferModel,
                                         @RequestParam(required = false) InstitutionType institutionType,
                                         @RequestParam(required = false) InnovationStatus status,
                                         @RequestParam(required = false) Long institutionId,
                                         @RequestParam(defaultValue = "newest") String sort,
                                         @RequestParam(defaultValue = "public") String scope,
                                         @RequestParam(defaultValue = "false") boolean includeForks,
                                         @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person viewer = currentUser.resolve(userId);
        Stream<Innovation> stream = innovations.findAll().stream();
        if ("mine".equals(scope)) {
            stream = stream.filter(i -> viewer != null && (i.isAuthor(viewer)
                    || (i.getReportedBy() != null && i.getReportedBy().getId().equals(viewer.getId()))
                    || (viewer.getInstitution() != null && i.getInstitution() != null
                    && viewer.getInstitution().getId().equals(i.getInstitution().getId()))));
        } else {
            stream = stream.filter(i -> i.getStatus().isPubliclyVisible());
        }
        if (!includeForks && !"mine".equals(scope)) stream = stream.filter(i -> i.getForkedFrom() == null);
        if (type != null) stream = stream.filter(i -> i.getType() == type);
        if (sector != null) stream = stream.filter(i -> i.getSector() == sector);
        if (maturity != null) stream = stream.filter(i -> i.getMaturity() == maturity);
        if (transferModel != null) stream = stream.filter(i -> i.getTransferModel() == transferModel);
        if (status != null) stream = stream.filter(i -> i.getStatus() == status);
        if (institutionId != null) stream = stream.filter(i -> i.getInstitution() != null && i.getInstitution().getId().equals(institutionId));
        if (institutionType != null) stream = stream.filter(i -> i.getInstitution() != null && i.getInstitution().getType() == institutionType);
        List<Innovation> found = stream.toList();

        Map<Long, Double> relevance = new HashMap<>();
        if (q != null && !q.isBlank()) {
            SimilarityService.Index idx = similarity.index();
            Map<String, Double> query = idx.vector(q);
            String nq = TextVectorizer.normalize(q);
            List<Innovation> matched = new ArrayList<>();
            for (Innovation i : found) {
                double score = TextVectorizer.cosine(query, idx.vector(i.searchText()));
                if (TextVectorizer.normalize(i.searchText()).contains(nq)) score += 0.5;
                if (score >= 0.06) {
                    relevance.put(i.getId(), score);
                    matched.add(i);
                }
            }
            found = matched;
        }

        List<Dto.InnovationCard> cards = new ArrayList<>(found.stream().map(i -> views.card(i, viewer)).toList());
        Comparator<Dto.InnovationCard> order = switch (sort) {
            case "score" -> Comparator.comparing((Dto.InnovationCard c) -> c.score() == null ? -1 : c.score()).reversed();
            case "popular" -> Comparator.comparingLong((Dto.InnovationCard c) -> c.votes() + c.followers()).reversed();
            case "forks" -> Comparator.comparingLong(Dto.InnovationCard::forks).reversed();
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
                                         @RequestParam(required = false) InnovationType type,
                                         @RequestParam(required = false) Sector sector,
                                         @RequestParam(required = false) Maturity maturity,
                                         @RequestParam(required = false) TransferModel transferModel,
                                         @RequestParam(required = false) InstitutionType institutionType,
                                         @RequestParam(required = false) InnovationStatus status,
                                         @RequestParam(required = false) Long institutionId,
                                         @RequestParam(defaultValue = "newest") String sort,
                                         @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        List<Dto.InnovationCard> cards = list(q, type, sector, maturity, transferModel, institutionType, status,
                institutionId, sort, "public", false, userId);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(";", "ID", "Tytuł", "Instytucja", "Typ instytucji", "Powiat", "Typ innowacji",
                "Sektor", "Etap", "Model transferu", "Status", "Wynik (0-100)", "Głosy", "Obserwujący", "Forki",
                "Wdrożenia", "Źródło", "Data dodania", "Tagi", "Link")).append("\r\n");
        for (Dto.InnovationCard c : cards) {
            csv.append(String.join(";",
                    String.valueOf(c.id()),
                    cell(c.title()),
                    cell(c.institution() == null ? "" : c.institution().name()),
                    cell(label(c.institution() == null ? null : c.institution().type())),
                    cell(c.institution() == null ? "" : c.institution().county()),
                    cell(label(c.type())),
                    cell(label(c.sector())),
                    cell(label(c.maturity())),
                    cell(label(c.transferModel())),
                    cell(c.status().label()),
                    c.score() == null ? "" : String.valueOf(Math.round(c.score())),
                    String.valueOf(c.votes()),
                    String.valueOf(c.followers()),
                    String.valueOf(c.forks()),
                    String.valueOf(c.deployments()),
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

    private static String label(Dto.EnumValue v) {
        return v == null ? "" : v.label();
    }

    /** Pole CSV w cudzysłowie, z podwojonymi cudzysłowami w treści. */
    private static String cell(String value) {
        String v = value == null ? "" : value.replace("\r", " ").replace("\n", " ");
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    @GetMapping("/{id}")
    public Dto.InnovationDetail get(@PathVariable Long id,
                                    @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person viewer = currentUser.resolve(userId);
        Innovation inn = service.get(id);
        if (!service.canView(inn, viewer)) {
            throw DomainException.forbidden("To zgłoszenie czeka na potwierdzenie autora i nie jest jeszcze publiczne.");
        }
        return views.detail(inn, viewer);
    }

    @GetMapping("/{id}/similar")
    public Map<String, List<SimilarItem>> similar(@PathVariable Long id) {
        Innovation inn = service.get(id);
        return Map.of(
                "innovations", similarity.similarInnovations(inn.searchText(), id, 5),
                "priorArt", similarity.similarPriorArt(inn.searchText(), 4));
    }

    @GetMapping("/{id}/recommendations")
    public List<RecommendationService.PathRecommendation> recommendations(@PathVariable Long id) {
        return recommendations.recommend(service.get(id));
    }

    @GetMapping("/{id}/matches")
    public Map<String, List<MatchingService.Match>> matches(@PathVariable Long id) {
        Innovation inn = service.get(id);
        return Map.of("needs", matching.needsFor(inn), "partners", matching.partnersFor(inn));
    }

    /** Podgląd propozycji oceny bez zapisu – np. dla autora przed wysłaniem do oceny. */
    @GetMapping("/{id}/assessment-preview")
    public Dto.AssessmentView preview(@PathVariable Long id) {
        return Dto.AssessmentView.of(assessmentService.propose(service.get(id)), true);
    }

    @PostMapping
    public Dto.InnovationDetail create(@RequestBody InnovationForm form,
                                       @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        return views.detail(service.create(form, actor), actor);
    }

    @PutMapping("/{id}")
    public Dto.InnovationDetail update(@PathVariable Long id, @RequestBody InnovationForm form,
                                       @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        return views.detail(service.update(id, form, actor), actor);
    }

    @PostMapping("/{id}/confirm")
    public Dto.InnovationDetail confirm(@PathVariable Long id,
                                        @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        return views.detail(service.confirm(id, actor), actor);
    }

    @PostMapping("/{id}/request-assessment")
    public Dto.InnovationDetail requestAssessment(@PathVariable Long id,
                                                  @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        return views.detail(service.requestAssessment(id, actor), actor);
    }

    public record ApproveRequest(List<InnovationService.ScoreInput> scores) {
    }

    @PostMapping("/{id}/assessment/approve")
    public Dto.InnovationDetail approve(@PathVariable Long id, @RequestBody ApproveRequest body,
                                        @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        return views.detail(service.approveAssessment(id, body.scores(), actor), actor);
    }

    public record PathsRequest(List<PathType> paths) {
    }

    @PostMapping("/{id}/paths")
    public Dto.InnovationDetail paths(@PathVariable Long id, @RequestBody PathsRequest body,
                                      @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        return views.detail(service.choosePaths(id, body.paths(), actor), actor);
    }

    @PostMapping("/{id}/fork")
    public Dto.InnovationDetail fork(@PathVariable Long id,
                                     @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        return views.detail(service.fork(id, actor), actor);
    }

    @PostMapping("/{id}/confirm-deployment")
    public Dto.InnovationDetail confirmDeployment(@PathVariable Long id,
                                                  @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        return views.detail(service.confirmDeployment(id, actor), actor);
    }

    @PostMapping("/{id}/vote")
    public Map<String, Object> vote(@PathVariable Long id,
                                    @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        boolean voted = service.toggleVote(id, currentUser.resolve(userId));
        return Map.of("voted", voted);
    }

    @PostMapping("/{id}/follow")
    public Map<String, Object> follow(@PathVariable Long id,
                                      @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        boolean following = service.toggleFollow(id, currentUser.resolve(userId));
        return Map.of("following", following);
    }

    public record ContactBody(String message) {
    }

    @PostMapping("/{id}/contact")
    public Dto.ContactView contact(@PathVariable Long id, @RequestBody ContactBody body,
                                   @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        return Dto.ContactView.of(service.requestContact(id, body.message(), currentUser.resolve(userId)));
    }
}
