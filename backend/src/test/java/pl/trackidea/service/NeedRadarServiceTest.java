package pl.trackidea.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Need;
import pl.trackidea.domain.Sector;
import pl.trackidea.radar.NeedRecord;
import pl.trackidea.radar.NeedSource;
import pl.trackidea.repo.InstitutionRepository;
import pl.trackidea.repo.NeedRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Potrzeby z otwartych źródeł trafiają na tablicę potrzeb, są odświeżane i zamykane po terminie. */
@SpringBootTest(properties = "trackidea.radar.live=false")
@Transactional
class NeedRadarServiceTest {

    @Autowired
    NeedRadarService needRadar;
    @Autowired
    NeedRepository needs;

    private static NeedRecord record(String title, Instant deadline) {
        return new NeedRecord("BZP", "2026/BZP 00000001", title, "Gmina szuka wykonawcy platformy e-usług dla mieszkańców.",
                "Gmina Testowa Radaru", InstitutionType.JST, "Testowo", "podkarpackie", Sector.ADMINISTRACJA,
                List.of("pakiety oprogramowania"), "https://ezamowienia.gov.pl/mo-client-board/bzp/notice-details/id/abc",
                Instant.parse("2026-11-02T10:00:00Z"), deadline, true);
    }

    @Test
    void storesNeedFromSourceOnceAndRefreshesIt() {
        Instant deadline = Instant.now().plus(Duration.ofDays(10));
        int[] counts = new int[2];

        needRadar.ingest(record("Wdrożenie platformy e-usług", deadline), counts);
        needRadar.ingest(record("Wdrożenie platformy e-usług", deadline), counts);
        needRadar.ingest(record("Wdrożenie platformy e-usług publicznych", deadline), counts);

        assertThat(counts).containsExactly(1, 1);
        assertThat(needs.countBySourceName("BZP")).isEqualTo(1);
        Need n = needs.findFirstBySourceNameAndSourceRef("BZP", "2026/BZP 00000001").orElseThrow();
        assertThat(n.getTitle()).isEqualTo("Wdrożenie platformy e-usług publicznych");
        assertThat(n.isOpen()).isTrue();
        assertThat(n.getDeadline()).isEqualTo(deadline);
        assertThat(n.getSourceUrl()).endsWith("/notice-details/id/abc");
        assertThat(n.getCreatedAt()).isEqualTo(Instant.parse("2026-11-02T10:00:00Z"));
        assertThat(n.getInstitution().getName()).isEqualTo("Gmina Testowa Radaru");
        assertThat(n.getInstitution().getRegion()).isEqualTo("podkarpackie");
        assertThat(needs.findByOpenTrue()).contains(n);
    }

    @Test
    void closesNeedsAfterDeadlineButKeepsFormNeedsOpen() {
        Instant deadline = Instant.now().plus(Duration.ofDays(2));
        needRadar.ingest(record("Wdrożenie platformy e-usług", deadline), new int[2]);
        long openFromForm = needs.findByOpenTrue().stream().filter(n -> n.getSourceName() == null).count();

        assertThat(needRadar.closeExpired(Instant.now())).isZero();
        assertThat(needRadar.closeExpired(deadline.plusSeconds(1))).isEqualTo(1);

        assertThat(needs.findFirstBySourceNameAndSourceRef("BZP", "2026/BZP 00000001").orElseThrow().isOpen()).isFalse();
        assertThat(needs.findByOpenTrue().stream().filter(n -> n.getSourceName() == null).count()).isEqualTo(openFromForm);
    }

    @Test
    void offlineModeDoesNotCallLiveSources() {
        NeedRadarService.Result r = needRadar.scan();

        assertThat(r.fetched()).isZero();
        assertThat(r.errors()).isEmpty();
        assertThat(needRadar.sources()).anySatisfy(s -> {
            assertThat(s.name()).isEqualTo("BZP");
            assertThat(s.active()).isFalse();
        });
    }

    // ---------- pełny skan na podstawionym źródle ----------

    @Autowired
    InstitutionRepository institutions;
    @Autowired
    TransactionTemplate tx;

    /** Źródło potrzeb, któremu test podstawia rekordy albo błąd. */
    private static final class FakeSource implements NeedSource {
        List<NeedRecord> records = List.of();
        Exception failure;

        @Override
        public String name() {
            return "BZP testowe";
        }

        @Override
        public String description() {
            return "Źródło podstawione w teście";
        }

        @Override
        public String homepage() {
            return "https://example.org";
        }

        @Override
        public boolean live() {
            return true;
        }

        @Override
        public List<NeedRecord> fetch() throws Exception {
            if (failure != null) {
                throw failure;
            }
            return records;
        }
    }

    private static NeedRecord fromSource(String ref, String title, Instant deadline) {
        return new NeedRecord("BZP testowe", ref, title, null, " ", null, null, null, Sector.ICT, null, null, null, deadline, false);
    }

    @Test
    void scanStoresNeedsClosesExpiredOnesAndReportsStatus() {
        FakeSource source = new FakeSource();
        NeedRadarService online = new NeedRadarService(List.of(source), needs, institutions, tx, true);
        Instant open = Instant.now().plus(Duration.ofDays(7));
        source.records = List.of(fromSource("N-1", "Wdrożenie systemu rezerwacji sal", open),
                fromSource("N-2", "Budowa portalu mieszkańca", null));
        // potrzeba z wcześniejszego skanu, której termin już minął
        Need expired = new Need();
        expired.setTitle("Wdrożenie e-dziennika");
        expired.setSourceName("BZP testowe");
        expired.setSourceRef("N-0");
        expired.setDeadline(Instant.now().minus(Duration.ofDays(1)));
        needs.save(expired);

        NeedRadarService.Result first = online.scan();

        assertThat(first.fetched()).isEqualTo(2);
        assertThat(first.created()).isEqualTo(2);
        assertThat(first.updated()).isZero();
        assertThat(first.closed()).isEqualTo(1);
        assertThat(first.errors()).isEmpty();
        assertThat(expired.isOpen()).isFalse();
        Need stored = needs.findFirstBySourceNameAndSourceRef("BZP testowe", "N-2").orElseThrow();
        // brak terminu = potrzeba otwarta bezterminowo; brak danych o zamawiającym nie blokuje zapisu
        assertThat(stored.isOpen()).isTrue();
        assertThat(stored.isWantsReadySolution()).isFalse();
        assertThat(stored.getInstitution().getName()).isEqualTo("Instytucja nieznana");
        assertThat(stored.getInstitution().getRegion()).isEqualTo("Polska");

        RadarService.SourceStatus status = online.sources().getFirst();
        assertThat(status.name()).isEqualTo("BZP testowe");
        assertThat(status.active()).isTrue();
        assertThat(status.lastRunAt()).isNotNull();
        assertThat(status.lastFetched()).isEqualTo(2);
        assertThat(status.lastCreated()).isEqualTo(2);
        assertThat(status.totalRecords()).isEqualTo(3);
        assertThat(status.lastError()).isNull();

        // drugi skan: termin przesunięty w źródle → odświeżenie zamiast nowego wpisu
        source.records = List.of(fromSource("N-1", "Wdrożenie systemu rezerwacji sal", open.plus(Duration.ofDays(3))));
        NeedRadarService.Result second = online.scan();
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(1);
        assertThat(needs.countBySourceName("BZP testowe")).isEqualTo(3);
    }

    @Test
    void unavailableNeedSourceIsReported() {
        FakeSource source = new FakeSource();
        source.failure = new java.io.IOException("HTTP 500 z ezamowienia.gov.pl");
        NeedRadarService online = new NeedRadarService(List.of(source), needs, institutions, tx, true);

        NeedRadarService.Result result = online.scan();

        assertThat(result.fetched()).isZero();
        assertThat(result.errors()).containsExactly("BZP testowe: HTTP 500 z ezamowienia.gov.pl");
        assertThat(online.sources().getFirst().lastError()).isEqualTo("Źródło niedostępne: HTTP 500 z ezamowienia.gov.pl");

        source.failure = new IllegalStateException();
        assertThat(online.scan().errors()).containsExactly("BZP testowe: IllegalStateException");
    }
}
