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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstitutionType type;

    private String city;

    /** Powiat – używany w dopasowaniu „bliskość regionalna”. */
    private String county;

    private String region = "podkarpackie";

    public Institution(String name, InstitutionType type, String city, String county, String region) {
        this.name = name;
        this.type = type;
        this.city = city;
        this.county = county;
        if (region != null) {
            this.region = region;
        }
    }
}
