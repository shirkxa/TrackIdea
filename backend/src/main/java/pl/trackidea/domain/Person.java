package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/** Konto zespołu PCI (analityk lub administrator) – katalog innowacji jest publiczny. */
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

    /** Login. */
    @Column(unique = true)
    private String email;

    /** Hasło zapisane jako hash BCrypt – nigdy nie wychodzi poza backend. */
    private String passwordHash;

    public boolean hasRole(Role... roles) {
        for (Role r : roles) {
            if (role == r) {
                return true;
            }
        }
        return false;
    }
}
