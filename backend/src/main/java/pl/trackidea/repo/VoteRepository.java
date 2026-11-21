package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.Person;
import pl.trackidea.domain.Vote;

import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    long countByInnovation(Innovation innovation);

    Optional<Vote> findByInnovationAndPerson(Innovation innovation, Person person);
}
