package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/** Profil użytkownika (jak na LinkedInie): twórca, opiekun instytucji, ekspert PCI albo partner. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne
    private Institution institution;

    private String headline;

    @Column(length = 2000)
    private String bio;

    /** Login. Widoczny dla innych dopiero po zaakceptowaniu prośby o kontakt. */
    @Column(unique = true)
    private String email;

    /** Hasło zapisane jako hash BCrypt – nigdy nie wychodzi poza backend. */
    private String passwordHash;

    private String github;
    private String youtube;
    private String linkedin;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<Sector> interestSectors = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> interestTags = new HashSet<>();

    /** Etapy dojrzałości, których szuka partner / inwestor. */
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<Maturity> seekingStages = new HashSet<>();

    public boolean hasRole(Role... roles) {
        for (Role r : roles) {
            if (role == r) {
                return true;
            }
        }
        return false;
    }
}
