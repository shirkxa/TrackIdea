package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** Tablica potrzeb: instytucja lub firma opisuje problem, system dopasowuje innowacje. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Need {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 3000)
    private String description;

    @ManyToOne
    private Institution institution;

    @Enumerated(EnumType.STRING)
    private Sector sector;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> tags = new LinkedHashSet<>();

    /** Czy szuka rozwiązania gotowego do wdrożenia (pilotaż+), czy też partnera do rozwoju. */
    private boolean wantsReadySolution = true;

    @Column(name = "is_open")
    private boolean open = true;

    private Instant createdAt = Instant.now();

    /** Źródło zewnętrzne (np. „BZP”); puste, gdy potrzebę zgłoszono w formularzu. */
    private String sourceName;

    /** Identyfikator w źródle, np. numer ogłoszenia w BZP. */
    private String sourceRef;

    @Column(length = 1000)
    private String sourceUrl;

    /** Termin, do którego można odpowiedzieć (np. składanie ofert); po nim potrzeba jest zamykana. */
    private Instant deadline;

    public String searchText() {
        return String.join(" ", title, title, description == null ? "" : description, String.join(" ", tags));
    }
}
