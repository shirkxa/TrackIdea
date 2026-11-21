package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Oś czasu innowacji – odpowiednik commitów. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class InnovationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Innovation innovation;

    @Enumerated(EnumType.STRING)
    private EventType type;

    @Column(length = 1000)
    private String message;

    @ManyToOne
    private Person actor;

    @Enumerated(EnumType.STRING)
    private InnovationStatus statusAfter;

    private Instant createdAt = Instant.now();

    public InnovationEvent(Innovation innovation, EventType type, String message, Person actor) {
        this.innovation = innovation;
        this.type = type;
        this.message = message;
        this.actor = actor;
        this.statusAfter = innovation.getStatus();
    }
}
