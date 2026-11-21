package pl.trackidea.radar;

import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Sector;

import java.time.Instant;
import java.util.List;

/**
 * Ujednolicona potrzeba z zewnętrznego źródła (np. ogłoszenie o zamówieniu publicznym):
 * kto czego szuka, do kiedy można odpowiedzieć i gdzie jest ogłoszenie.
 */
public record NeedRecord(String source, String ref, String title, String description, String institution,
                         InstitutionType institutionType, String city, String region, Sector sector,
                         List<String> tags, String url, Instant publishedAt, Instant deadline,
                         boolean wantsReadySolution) {

    public NeedRecord {
        tags = tags == null ? List.of() : tags;
    }
}
