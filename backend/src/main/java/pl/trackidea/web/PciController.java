package pl.trackidea.web;

import org.springframework.web.bind.annotation.*;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.domain.Person;
import pl.trackidea.repo.ContactRequestRepository;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.RadarRecordRepository;
import pl.trackidea.service.DomainException;
import pl.trackidea.service.InnovationService;
import pl.trackidea.service.RadarService;
import pl.trackidea.service.StatsService;

import java.util.*;

/** Panel PCI (mierniki, kolejka ocen, Radar) oraz skrzynka kontaktów. */
@RestController
@RequestMapping("/api")
public class PciController {

    private final StatsService stats;
    private final RadarService radar;
    private final RadarRecordRepository radarRecords;
    private final InnovationRepository innovations;
    private final ContactRequestRepository contacts;
    private final InnovationService innovationService;
    private final Views views;
    private final CurrentUser currentUser;

    public PciController(StatsService stats, RadarService radar, RadarRecordRepository radarRecords,
                         InnovationRepository innovations, ContactRequestRepository contacts,
                         InnovationService innovationService, Views views, CurrentUser currentUser) {
        this.stats = stats;
        this.radar = radar;
        this.radarRecords = radarRecords;
        this.innovations = innovations;
        this.contacts = contacts;
        this.innovationService = innovationService;
        this.views = views;
        this.currentUser = currentUser;
    }

    @GetMapping("/stats")
    public StatsService.Stats stats() {
        return stats.stats();
    }

    /** Kolejka pracy PCI: szkice do potwierdzenia, oceny do zatwierdzenia, ocenione bez ścieżki. */
    @GetMapping("/pci/queue")
    public Map<String, List<Dto.InnovationCard>> queue() {
        Map<String, List<Dto.InnovationCard>> out = new LinkedHashMap<>();
        out.put("toApprove", cards(InnovationStatus.W_OCENIE));
        out.put("toChoosePath", cards(InnovationStatus.OCENIONA));
        out.put("drafts", innovations.findByStatusIn(List.of(InnovationStatus.WYKRYTA, InnovationStatus.ZGLOSZONA))
                .stream().map(views::card).toList());
        out.put("readyForAssessment", cards(InnovationStatus.UZUPELNIONA));
        return out;
    }

    private List<Dto.InnovationCard> cards(InnovationStatus status) {
        return innovations.findByStatusIn(List.of(status)).stream()
                .filter(i -> i.getForkedFrom() == null).map(views::card).toList();
    }

    @GetMapping("/radar")
    public Map<String, Object> radarState() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sources", radar.sources());
        out.put("records", radarRecords.findAllByOrderByImportedAtDescIdDesc().stream().map(Dto.RadarRecordView::of).toList());
        return out;
    }

    @PostMapping("/radar/scan")
    public Map<String, Object> scan(@RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person actor = currentUser.resolve(userId);
        if (actor == null || !actor.hasRole(pl.trackidea.domain.Role.EKSPERT, pl.trackidea.domain.Role.ADMIN)) {
            throw DomainException.forbidden("Skan Radaru uruchamia ekspert lub administrator PCI.");
        }
        RadarService.ScanResult r = radar.scan();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scanned", r.scanned());
        out.put("created", r.created());
        out.put("duplicates", r.duplicates());
        out.put("skipped", r.skipped());
        out.put("records", r.records().stream().map(Dto.RadarRecordView::of).toList());
        return out;
    }

    @GetMapping("/contacts")
    public List<Dto.ContactView> contacts(@RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        Person me = currentUser.resolve(userId);
        if (me == null) {
            return List.of();
        }
        return contacts.findByToPersonOrFromPersonOrderByCreatedAtDesc(me, me).stream().map(Dto.ContactView::of).toList();
    }

    @PostMapping("/contacts/{id}/{decision}")
    public Dto.ContactView answer(@PathVariable Long id, @PathVariable String decision,
                                  @RequestHeader(value = CurrentUser.HEADER, required = false) Long userId) {
        if (!decision.equals("accept") && !decision.equals("reject")) {
            throw DomainException.badRequest("Nieznana decyzja.");
        }
        return Dto.ContactView.of(innovationService.answerContact(id, decision.equals("accept"), currentUser.resolve(userId)));
    }
}
