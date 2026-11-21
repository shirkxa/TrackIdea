package pl.trackidea.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Głos „przydałoby się u nas” (jak w Banku Pomysłów) – jeden na osobę. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"innovation_id", "person_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Innovation innovation;

    @ManyToOne(optional = false)
    private Person person;

    public Vote(Innovation innovation, Person person) {
        this.innovation = innovation;
        this.person = person;
    }
}
