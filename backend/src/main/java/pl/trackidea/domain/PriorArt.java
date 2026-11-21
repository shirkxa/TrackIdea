package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Patent lub publikacja używane do oceny nowości (EPO, UPRP, PBN). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PriorArt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PATENT albo PUBLIKACJA. */
    private String kind;

    private String source;
    private String ref;
    private String title;

    @Column(length = 3000)
    private String abstractText;

    private String holder;
    @Column(name = "pub_year")
    private Integer year;

    public String searchText() {
        return String.join(" ", title, title, abstractText == null ? "" : abstractText);
    }
}
