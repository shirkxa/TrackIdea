package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Prośba o kontakt – dane kontaktowe są widoczne dopiero po akceptacji. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ContactRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Innovation innovation;

    @ManyToOne(optional = false)
    private Person fromPerson;

    @ManyToOne(optional = false)
    private Person toPerson;

    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    private ContactStatus status = ContactStatus.WYSLANA;

    private Instant createdAt = Instant.now();
    private Instant answeredAt;
}
