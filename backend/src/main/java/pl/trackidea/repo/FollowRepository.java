package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Follow;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.Person;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    long countByInnovation(Innovation innovation);

    Optional<Follow> findByInnovationAndPerson(Innovation innovation, Person person);

    List<Follow> findByPerson(Person person);
}
