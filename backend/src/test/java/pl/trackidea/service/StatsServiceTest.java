package pl.trackidea.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.domain.Person;
import pl.trackidea.repo.InnovationRepository;
import pl.trackidea.repo.NeedRepository;
import pl.trackidea.repo.PersonRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Mierniki panelu PCI liczone z danych demo. */
@SpringBootTest(properties = "trackidea.radar.live=false")
@Transactional
class StatsServiceTest {

    @Autowired
    StatsService stats;
    @Autowired
    InnovationService innovationService;
    @Autowired
    InnovationRepository innovations;
    @Autowired
    NeedRepository needs;
    @Autowired
    PersonRepository people;

    private static String value(StatsService.Stats s, String key) {
        return s.kpis().stream().filter(k -> k.key().equals(key)).findFirst().orElseThrow().value();
    }

    private static long sum(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    @Test
    void countsCatalogAndBreakdowns() {
        StatsService.Stats s = stats.stats();
        List<Innovation> all = innovations.findAll();
        long visible = all.stream().filter(i -> i.getStatus().isPubliclyVisible()).count();
        long verified = all.stream().filter(i -> i.getStatus() == InnovationStatus.OCENIONA).count();

        assertThat(s.kpis()).extracting(StatsService.Kpi::key)
                .containsExactly("found", "verified", "time", "ai", "duplicates", "rejected", "needs");
        assertThat(s.kpis()).allSatisfy(k -> {
            assertThat(k.label()).isNotBlank();
            assertThat(k.value()).isNotBlank();
        });
        assertThat(value(s, "found")).isEqualTo(String.valueOf(visible));
        assertThat(value(s, "verified")).isEqualTo(Math.round(100.0 * verified / visible) + "%");
        assertThat(value(s, "needs")).isEqualTo(String.valueOf(needs.findByOpenTrue().size()));
        // dane demo mają zatwierdzone oceny, więc czas i zgodność propozycji są policzone
        assertThat(value(s, "time")).endsWith(" dni");
        assertThat(value(s, "ai")).endsWith("%");
        // każda widoczna innowacja ma status, obszar i źródło – rozbicia sumują się do całego katalogu
        assertThat(sum(s.byStatus())).isEqualTo(all.size());
        assertThat(sum(s.byArea())).isEqualTo(visible);
        assertThat(sum(s.bySource())).isEqualTo(visible);
        assertThat(sum(s.bySector())).isLessThanOrEqualTo(visible);
        assertThat(sum(s.byInstitutionType())).isLessThanOrEqualTo(visible);
        assertThat(s.byStatus()).containsKey(InnovationStatus.OCENIONA.getLabel());
    }

    @Test
    void rejectedRecordMovesFromCatalogToRejectedCounter() {
        StatsService.Stats before = stats.stats();
        Person analyst = people.findByEmailIgnoreCase("k.nowak@pci.example.org").orElseThrow();
        Innovation detected = innovations.findByStatusIn(List.of(InnovationStatus.WYKRYTA)).getFirst();

        innovationService.reject(detected.getId(), "Zwykły zakup sprzętu.", analyst);
        StatsService.Stats after = stats.stats();

        assertThat(Integer.parseInt(value(after, "found"))).isEqualTo(Integer.parseInt(value(before, "found")) - 1);
        assertThat(Integer.parseInt(value(after, "rejected"))).isEqualTo(Integer.parseInt(value(before, "rejected")) + 1);
        assertThat(after.byStatus()).containsKey(InnovationStatus.ODRZUCONA.getLabel());
    }
}
