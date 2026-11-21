package pl.trackidea.radar;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import pl.trackidea.service.NeedRadarService;
import pl.trackidea.service.RadarService;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Harmonogram Radaru: kiedy uruchamia skany i w jakiej kolejności. */
class RadarSchedulerTest {

    private final RadarService radar = mock(RadarService.class);
    private final NeedRadarService needRadar = mock(NeedRadarService.class);

    @Test
    void scansNeedsBeforeInnovationsWhenOnline() {
        new RadarScheduler(radar, needRadar, true).refresh();

        // najpierw potrzeby: ocena nowych innowacji liczy pasujące do nich zapytania
        InOrder order = inOrder(needRadar, radar);
        order.verify(needRadar).scan();
        order.verify(radar).scan();
    }

    @Test
    void doesNothingOffline() {
        RadarScheduler scheduler = new RadarScheduler(radar, needRadar, false);

        scheduler.refresh();
        scheduler.onSourceReady(new SourceReadyEvent("Fundusze Europejskie"));

        verifyNoInteractions(radar, needRadar);
    }

    @Test
    void scansRightAwayWhenSourceFinishesPreparingData() {
        new RadarScheduler(radar, needRadar, true).onSourceReady(new SourceReadyEvent("Fundusze Europejskie"));

        // skan idzie w osobnym wątku, żeby nie blokować źródła, które zgłosiło gotowość
        verify(radar, timeout(2000)).scan();
    }
}
