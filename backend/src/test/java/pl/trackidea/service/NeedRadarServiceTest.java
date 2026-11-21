package pl.trackidea.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Need;
import pl.trackidea.domain.Sector;
import pl.trackidea.radar.NeedRecord;
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
}
