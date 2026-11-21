package pl.trackidea.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import pl.trackidea.domain.Need;
import pl.trackidea.domain.Person;
import pl.trackidea.domain.Sector;
import pl.trackidea.repo.NeedRepository;
import pl.trackidea.service.DomainException;
import pl.trackidea.service.MatchingService;

import java.util.*;

/** Tablica potrzeb: instytucje i firmy opisują problemy, system dopasowuje istniejące innowacje. */
@RestController
@Tag(name = "Tablica potrzeb", description = "Problemy instytucji i firm z dopasowanymi innowacjami")
@RequestMapping("/api/needs")
public class NeedController {

    private final NeedRepository needs;
    private final MatchingService matching;
    private final CurrentUser currentUser;

    public NeedController(NeedRepository needs, MatchingService matching, CurrentUser currentUser) {
        this.needs = needs;
        this.matching = matching;
        this.currentUser = currentUser;
    }

    public record NeedWithMatches(Dto.NeedView need, List<MatchingService.Match> matches) {
    }

    @GetMapping
    public List<NeedWithMatches> list() {
        return needs.findByOpenTrue().stream()
                .sorted(Comparator.comparing(Need::getCreatedAt).reversed())
                .map(n -> new NeedWithMatches(Dto.NeedView.of(n), matching.innovationsForNeed(n)))
                .toList();
    }

    @GetMapping("/{id}/matches")
    public List<MatchingService.Match> matches(@PathVariable Long id) {
        return matching.innovationsForNeed(needs.findById(id).orElseThrow(() -> DomainException.notFound("Brak potrzeby.")));
    }

    public record NeedForm(String title, String description, Sector sector, List<String> tags, Boolean wantsReadySolution) {
    }

    @PostMapping
    public NeedWithMatches create(@RequestBody NeedForm form,
                                  @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        if (actor == null || actor.getInstitution() == null) {
            throw DomainException.forbidden("Potrzebę publikuje osoba przypisana do instytucji lub firmy.");
        }
        if (form.title() == null || form.title().isBlank()) {
            throw DomainException.badRequest("Podaj tytuł potrzeby.");
        }
        Need n = new Need();
        n.setTitle(form.title().trim());
        n.setDescription(form.description());
        n.setSector(form.sector());
        if (form.tags() != null) {
            form.tags().stream().map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase).forEach(n.getTags()::add);
        }
        n.setWantsReadySolution(!Boolean.FALSE.equals(form.wantsReadySolution()));
        n.setInstitution(actor.getInstitution());
        n.setAuthor(actor);
        needs.save(n);
        return new NeedWithMatches(Dto.NeedView.of(n), matching.innovationsForNeed(n));
    }
}
