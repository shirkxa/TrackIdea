package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Karta innowacji – opis istniejącego rozwiązania ze źródłami. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Innovation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String summary;

    @Column(length = 4000)
    private String problem;

    @Column(length = 4000)
    private String solution;

    /** Czego wymaga wdrożenie (ludzie, sprzęt, koszt). */
    @Column(length = 2000)
    private String requirements;

    @Column(length = 2000)
    private String results;

    /** Obszar: technologiczny / edukacyjny / administracyjny. */
    @Enumerated(EnumType.STRING)
    private Area area;

    @Enumerated(EnumType.STRING)
    private InnovationType type;

    @Enumerated(EnumType.STRING)
    private Sector sector;

    @Enumerated(EnumType.STRING)
    private Maturity maturity;

    /** Gotowość technologiczna 1–9. */
    private Integer trl;

    /** Pola ustalone automatycznie (szacunek), jeszcze niepotwierdzone przez analityka: area, type, sector, maturity, trl. */
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> estimated = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    private TransferModel transferModel;

    private String ipStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> tags = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> links = new LinkedHashSet<>();

    @ManyToOne
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source = Source.RADAR;

    /** Np. „PBN”, „NCBiR” dla innowacji z Radaru. */
    private String sourceName;
    private String sourceRef;

    /** Autorzy według źródła (tekst – Radar nie zakłada kont twórcom). */
    private String sourceAuthors;

    private Integer sourceYear;

    /** Fakty podane wprost przez źródło (np. status projektu, finansowanie UE, liczba cytowań). */
    @ElementCollection(fetch = FetchType.EAGER)
    @OrderColumn
    @Column(length = 300)
    private List<String> sourceFacts = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InnovationStatus status = InnovationStatus.WYKRYTA;

    @ManyToOne
    private Innovation mergedInto;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<PathType> chosenPaths = new LinkedHashSet<>();

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    /** Tekst używany do wyszukiwania podobnych innowacji. */
    public String searchText() {
        return String.join(" ",
                nz(title), nz(title), nz(summary), nz(problem), nz(solution), String.join(" ", tags));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public void touch() {
        updatedAt = Instant.now();
    }
}
