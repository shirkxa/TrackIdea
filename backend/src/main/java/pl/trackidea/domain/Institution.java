package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Nazwa bez wielkości liter i polskich znaków – ta sama instytucja z różnych źródeł to jeden rekord. */
    private String nameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstitutionType type;

    private String city;

    /** Powiat – używany w dopasowaniu „bliskość regionalna”. */
    private String county;

    private String region = "podkarpackie";

    public Institution(String name, InstitutionType type, String city, String county, String region) {
        this.name = name;
        this.nameKey = keyOf(name);
        this.type = type;
        this.city = city;
        this.county = county;
        if (region != null) {
            this.region = region;
        }
    }

    public void setName(String name) {
        this.name = name;
        this.nameKey = keyOf(name);
    }

    /** „Politechnika Poznańska” i „POLITECHNIKA POZNANSKA” dają ten sam klucz. */
    public static String keyOf(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT).replace('ł', 'l');
        return java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim();
    }
}
