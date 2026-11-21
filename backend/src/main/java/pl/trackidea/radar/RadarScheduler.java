package pl.trackidea.radar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.trackidea.service.RadarService;

/** Cykliczna aktualizacja bazy: pierwszy skan chwilę po starcie, potem co `trackidea.radar.refresh-interval`. */
@Component
public class RadarScheduler {

    private final RadarService radar;
    private final boolean liveEnabled;

    public RadarScheduler(RadarService radar, @Value("${trackidea.radar.live:true}") boolean liveEnabled) {
        this.radar = radar;
        this.liveEnabled = liveEnabled;
    }

    /** Gdy źródło skończy pobierać dane w tle, skan jest robiony od razu, bez czekania 6 godzin. */
    @org.springframework.context.event.EventListener
    public void onSourceReady(SourceReadyEvent event) {
        if (liveEnabled) {
            Thread.ofVirtual().name("radar-" + event.source()).start(radar::scan);
        }
    }

    @Scheduled(initialDelayString = "${trackidea.radar.initial-delay:PT5S}",
            fixedDelayString = "${trackidea.radar.refresh-interval:PT6H}")
    public void refresh() {
        if (liveEnabled) {
            radar.scan();
        }
    }
}
