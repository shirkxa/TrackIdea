package pl.trackidea.radar;

import pl.trackidea.domain.InnovationType;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.domain.Maturity;
import pl.trackidea.domain.Sector;

import java.util.List;

/**
 * Ujednolicony rekord z dowolnego źródła Radaru.
 * Pola „hint” to rozpoznanie importera (np. z klasyfikacji tematycznej źródła); puste uzupełnia analiza tekstu.
 */
public record SourceRecord(String source, String ref, String title, String abstractText, String institution,
                           InstitutionType institutionType, String city, String county, String region,
                           String authors, Integer year, List<String> keywords, String url,
                           InnovationType typeHint, Sector sectorHint, Maturity maturityHint) {

    public SourceRecord {
        keywords = keywords == null ? List.of() : keywords;
    }
}
