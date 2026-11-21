package pl.trackidea.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import pl.trackidea.domain.Institution;
import pl.trackidea.domain.Person;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.InstitutionRepository;
import pl.trackidea.repo.NeedRepository;
import pl.trackidea.repo.PersonRepository;
import pl.trackidea.service.DomainException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Instytucje", description = "Instytucje, w których powstały innowacje, oraz konta zespołu PCI")
public class PeopleController {

    private final PersonRepository people;
    private final InstitutionRepository institutions;
    private final InnovationRepository innovations;
    private final NeedRepository needs;
    private final Views views;

    public PeopleController(PersonRepository people, InstitutionRepository institutions,
                            InnovationRepository innovations, NeedRepository needs, Views views) {
        this.people = people;
        this.institutions = institutions;
        this.innovations = innovations;
        this.needs = needs;
        this.views = views;
    }

    /** Konta zespołu PCI – używane przez szybkie przełączanie w trybie demo. */
    @GetMapping("/people")
    @Operation(summary = "Konta zespołu PCI")
    public List<Dto.PersonView> people() {
        return people.findAll().stream()
                .sorted(Comparator.comparing((Person p) -> p.getRole().ordinal()).thenComparing(Person::getId))
                .map(Dto.PersonView::of).toList();
    }

    @GetMapping("/institutions")
    public List<Dto.InstitutionView> institutions() {
        return institutions.findAll().stream().sorted(Comparator.comparing(Institution::getName))
                .map(Dto.InstitutionView::of).toList();
    }

    @GetMapping("/institutions/{id}")
    public Map<String, Object> institution(@PathVariable Long id) {
        Institution inst = institutions.findById(id).orElseThrow(() -> DomainException.notFound("Brak instytucji."));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("institution", Dto.InstitutionView.of(inst));
        out.put("innovations", innovations.findByInstitution(inst).stream()
                .filter(i -> i.getStatus().isPubliclyVisible()).map(views::card).toList());
        out.put("needs", needs.findAll().stream()
                .filter(n -> n.getInstitution() != null && n.getInstitution().getId().equals(id))
                .map(Dto.NeedView::of).toList());
        return out;
    }
}
