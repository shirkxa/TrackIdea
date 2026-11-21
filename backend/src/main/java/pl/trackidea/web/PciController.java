package pl.trackidea.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.domain.Person;
import pl.trackidea.radar.ManualSource;
import pl.trackidea.radar.SourceRecord;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.RadarRecordRepository;
import pl.trackidea.service.DomainException;
import pl.trackidea.service.NeedRadarService;
import pl.trackidea.service.RadarService;
import pl.trackidea.service.StatsService;

import java.util.*;

/** Panel PCI: mierniki, kolejka weryfikacji i Radar. */
@RestController
@Tag(name = "PCI i Radar", description = "Mierniki, kolejka weryfikacji analityka, skan Radaru")
@RequestMapping("/api")
public class PciController {

    private final StatsService stats;
    private final RadarService radar;
    private final NeedRadarService needRadar;
    private final RadarRecordRepository radarRecords;
    private final InnovationRepository innovations;
    private final Views views;
    private final CurrentUser currentUser;
    private final ManualSource manual;

    public PciController(StatsService stats, RadarService radar, NeedRadarService needRadar,
                         RadarRecordRepository radarRecords, InnovationRepository innovations, Views views,
                         CurrentUser currentUser, ManualSource manual) {
        this.manual = manual;
        this.needRadar = needRadar;
        this.stats = stats;
        this.radar = radar;
        this.radarRecords = radarRecords;
        this.innovations = innovations;
        this.views = views;
        this.currentUser = currentUser;
    }

    @GetMapping("/stats")
    public StatsService.Stats stats() {
        return stats.stats();
    }

    /** Kolejka pracy analityka: oceny do zatwierdzenia i nowe rekordy z Radaru do weryfikacji. */
    @GetMapping("/pci/queue")
    public Map<String, List<Dto.InnovationCard>> queue() {
        Map<String, List<Dto.InnovationCard>> out = new LinkedHashMap<>();
        out.put("toApprove", cards(InnovationStatus.W_OCENIE));
        out.put("toReview", cards(InnovationStatus.WYKRYTA));
        return out;
    }

    private List<Dto.InnovationCard> cards(InnovationStatus status) {
        return innovations.findByStatusIn(List.of(status)).stream().map(views::card).toList();
    }

    @GetMapping("/radar")
    public Map<String, Object> radarState() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", radar.isLive());
        out.put("lastScanAt", radar.lastScanAt());
        out.put("nextScanAt", radar.nextScanAt());
        List<RadarService.SourceStatus> sources = new ArrayList<>(radar.sources());
        sources.addAll(needRadar.sources());
        out.put("sources", sources);
        out.put("records", radarRecords.findAllByOrderByImportedAtDescIdDesc().stream().limit(100)
                .map(Dto.RadarRecordView::of).toList());
        return out;
    }

    /** Strona (adres) albo wklejona treść dokumentu do analizy. */
    public record AnalyzeRequest(String url, String text, String title, String institution) {
    }

    /**
     * Analityk wskazuje stronę samorządu, szkoły, uczelni lub treść raportu – Radar pobiera tekst,
     * wyodrębnia pola karty (AI, gdy dostępne), wykrywa duplikat i tworzy kartę „Wykryta” ze źródłem.
     */
    @PostMapping("/radar/analyze")
    public Dto.RadarRecordView analyze(@RequestBody AnalyzeRequest req) {
        requireStaff("Analizę strony lub dokumentu uruchamia analityk PCI.");
        SourceRecord record;
        try {
            record = req.text() != null && !req.text().isBlank()
                    ? manual.fromText(req.title(), req.text(), req.institution(), req.url())
                    : manual.fromPage(req.url(), req.institution());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DomainException.badRequest("Przerwano pobieranie strony.");
        } catch (Exception e) {
            throw DomainException.badRequest(e.getMessage() == null ? "Nie udało się pobrać strony." : e.getMessage());
        }
        return Dto.RadarRecordView.of(radar.analyze(record));
    }

    private void requireStaff(String message) {
        Person actor = currentUser.get();
        if (actor == null || !actor.hasRole(pl.trackidea.domain.Role.EKSPERT, pl.trackidea.domain.Role.ADMIN)) {
            throw DomainException.forbidden(message);
        }
    }

    @PostMapping("/radar/scan")
    public Map<String, Object> scan() {
        requireStaff("Skan Radaru uruchamia ekspert lub administrator PCI.");
        NeedRadarService.Result needs = needRadar.scan();
        RadarService.ScanResult r = radar.scan();
        List<String> errors = new ArrayList<>(needs.errors());
        errors.addAll(r.errors());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scanned", r.scanned());
        out.put("created", r.created());
        out.put("duplicates", r.duplicates());
        out.put("updated", r.updated());
        out.put("skipped", r.skipped());
        out.put("errors", errors);
        out.put("needsCreated", needs.created());
        out.put("needsClosed", needs.closed());
        out.put("records", r.records().stream().map(Dto.RadarRecordView::of).toList());
        return out;
    }
}
