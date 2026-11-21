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

    @Scheduled(initialDelayString = "${trackidea.radar.initial-delay:PT5S}",
            fixedDelayString = "${trackidea.radar.refresh-interval:PT6H}")
    public void refresh() {
        if (liveEnabled) {
            radar.scan();
        }
    }
}
