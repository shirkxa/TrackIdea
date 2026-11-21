package pl.trackidea.service;

import org.springframework.stereotype.Component;
import pl.trackidea.domain.Institution;
import pl.trackidea.domain.InstitutionType;
import pl.trackidea.repo.InstitutionRepository;

/**
 * Jedna instytucja = jeden rekord, niezależnie od źródła. Źródła zapisują tę samą nazwę różnie
 * („Politechnika Poznańska”, „POLITECHNIKA POZNANSKA”), więc szukamy po kluczu bez wielkości liter i polskich znaków.
 */
@Component
public class InstitutionDirectory {

    public static final String UNKNOWN = "Instytucja nieznana";

    private final InstitutionRepository institutions;

    public InstitutionDirectory(InstitutionRepository institutions) {
        this.institutions = institutions;
    }

    public Institution resolve(String name, InstitutionType type, String city, String county, String region) {
        String wanted = name == null || name.isBlank() ? UNKNOWN : name.trim();
        Institution found = institutions.findFirstByNameKey(Institution.keyOf(wanted)).orElse(null);
        if (found == null) {
            return institutions.save(new Institution(wanted,
                    type == null ? InstitutionType.INSTYTUCJA_PUBLICZNA : type, city, county,
                    region == null ? "Polska" : region));
        }
        // nazwa z polskimi znakami jest lepsza niż ta sama nazwa bez nich
        if (isAscii(found.getName()) && !isAscii(wanted)) {
            found.setName(wanted);
        }
        if (found.getCity() == null && city != null) {
            found.setCity(city);
        }
        if (found.getCounty() == null && county != null) {
            found.setCounty(county);
        }
        if (region != null && !"Polska".equals(region) && (found.getRegion() == null || "Polska".equals(found.getRegion()))) {
            found.setRegion(region);
        }
        return found;
    }

    private static boolean isAscii(String s) {
        return s.chars().allMatch(c -> c < 128);
    }
}
