package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Surowy rekord pobrany przez Radar z zewnętrznego źródła i wynik jego przetworzenia. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RadarRecord {

    public enum Outcome { NOWA_INNOWACJA, DUPLIKAT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source;
    private String ref;
    private String title;
    private String institutionName;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    @ManyToOne
    private Innovation innovation;

    @ManyToOne
    private Innovation duplicateOf;

    private double duplicateSimilarity;

    private Instant importedAt = Instant.now();

    /** Ostatni skan, w którym rekord nadal był w źródle (cykliczna aktualizacja). */
    private Instant lastSeenAt = Instant.now();

    /** Skrót treści rekordu – zmiana oznacza aktualizację w źródle. */
    private int contentHash;
}
