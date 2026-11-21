package pl.trackidea.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.trackidea.domain.Need;
import pl.trackidea.radar.NeedRecord;
import pl.trackidea.radar.NeedSource;
import pl.trackidea.repo.NeedRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Radar potrzeb: cyklicznie pobiera z otwartych źródeł, kto czego szuka (np. ogłoszenia o zamówieniach),
 * i dopisuje to do tablicy potrzeb obok zapytań zgłoszonych w formularzu.
 * Potrzeba ze źródła jest odświeżana przy kolejnych skanach i zamykana po terminie.
 */
@Service
public class NeedRadarService {

    private static final Logger log = LoggerFactory.getLogger(NeedRadarService.class);

    public record Result(int fetched, int created, int updated, int closed, List<String> errors) {
    }

    private final List<NeedSource> sources;
    private final NeedRepository needs;
    private final InstitutionDirectory institutions;
    private final TransactionTemplate tx;
    private final boolean liveEnabled;
    private final Map<String, RadarService.SourceStatus> status = new ConcurrentHashMap<>();

    public NeedRadarService(List<NeedSource> sources, NeedRepository needs, InstitutionDirectory institutions,
                            TransactionTemplate tx, @Value("${trackidea.radar.live:true}") boolean liveEnabled) {
        this.sources = sources;
        this.needs = needs;
        this.institutions = institutions;
        this.tx = tx;
        this.liveEnabled = liveEnabled;
    }

    /** Stan źródeł potrzeb w tym samym kształcie co źródła innowacji – panel PCI pokazuje je razem. */
    public List<RadarService.SourceStatus> sources() {
        List<RadarService.SourceStatus> out = new ArrayList<>();
        for (NeedSource src : sources) {
            RadarService.SourceStatus s = status.get(src.name());
            long total = needs.countBySourceName(src.name());
            out.add(s == null
                    ? status(src, null, 0, 0, 0, total, null)
                    : new RadarService.SourceStatus(s.name(), s.description(), s.homepage(), s.live(), s.active(),
                    s.lastRunAt(), s.lastFetched(), s.lastCreated(), 0, s.lastUpdated(), total, s.lastError(), null));
        }
        return out;
    }

    /** Skan wszystkich aktywnych źródeł potrzeb (harmonogram i przycisk w panelu PCI). */
    public synchronized Result scan() {
        int fetched = 0, created = 0, updated = 0;
        List<String> errors = new ArrayList<>();
        for (NeedSource src : sources) {
            if (src.live() != liveEnabled) {
                continue;
            }
            List<NeedRecord> incoming;
            try {
                incoming = src.fetch();
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("Radar potrzeb: źródło {} niedostępne: {}", src.name(), msg);
                errors.add(src.name() + ": " + msg);
                status.put(src.name(), status(src, Instant.now(), 0, 0, 0, 0, "Źródło niedostępne: " + msg));
                continue;
            }
            int[] counts = new int[2]; // created, updated
            for (NeedRecord r : incoming) {
                try {
                    tx.executeWithoutResult(t -> ingest(r, counts));
                } catch (RuntimeException e) {
                    log.warn("Radar potrzeb: pominięto {} {}: {}", r.source(), r.ref(), e.getMessage());
                }
            }
            fetched += incoming.size();
            created += counts[0];
            updated += counts[1];
            status.put(src.name(), status(src, Instant.now(), incoming.size(), counts[0], counts[1], 0, null));
            log.info("Radar potrzeb: {} – pobrano {}, nowe {}, zaktualizowane {}", src.name(), incoming.size(),
                    counts[0], counts[1]);
        }
        Integer closed = tx.execute(t -> closeExpired(Instant.now()));
        return new Result(fetched, created, updated, closed == null ? 0 : closed, errors);
    }

    private RadarService.SourceStatus status(NeedSource src, Instant lastRunAt, int fetched, int created, int updated,
                                             long total, String error) {
        return new RadarService.SourceStatus(src.name(), src.description(), src.homepage(), src.live(),
                src.live() == liveEnabled, lastRunAt, fetched, created, 0, updated, total, error, null);
    }

    /** Jedna potrzeba ze źródła: nowa → wpis na tablicy, znana → odświeżenie treści i terminu. */
    void ingest(NeedRecord r, int[] counts) {
        boolean open = r.deadline() == null || r.deadline().isAfter(Instant.now());
        Optional<Need> known = needs.findFirstBySourceNameAndSourceRef(r.source(), r.ref());
        if (known.isPresent()) {
            Need n = known.get();
            if (!Objects.equals(n.getTitle(), r.title()) || !Objects.equals(n.getDescription(), r.description())
                    || !Objects.equals(n.getDeadline(), r.deadline()) || n.isOpen() != open) {
                n.setTitle(r.title());
                n.setDescription(r.description());
                n.setDeadline(r.deadline());
                n.setOpen(open);
                counts[1]++;
            }
            return;
        }
        Need n = new Need();
        n.setTitle(r.title());
        n.setDescription(r.description());
        n.setSector(r.sector());
        n.getTags().addAll(r.tags());
        n.setWantsReadySolution(r.wantsReadySolution());
        n.setSourceName(r.source());
        n.setSourceRef(r.ref());
        n.setSourceUrl(r.url());
        n.setDeadline(r.deadline());
        n.setOpen(open);
        if (r.publishedAt() != null) {
            n.setCreatedAt(r.publishedAt());
        }
        n.setInstitution(institutions.resolve(r.institution(), r.institutionType(), r.city(), null, r.region()));
        needs.save(n);
        counts[0]++;
    }

    /** Po terminie (np. składania ofert) potrzeba znika z tablicy, ale zostaje w bazie. */
    int closeExpired(Instant now) {
        int closed = 0;
        for (Need n : needs.findBySourceNameIsNotNullAndOpenTrue()) {
            if (n.getDeadline() != null && !n.getDeadline().isAfter(now)) {
                n.setOpen(false);
                closed++;
            }
        }
        return closed;
    }
}
