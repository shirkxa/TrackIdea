package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Person;
import pl.trackidea.domain.Role;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {
    List<Person> findByRole(Role role);

    Optional<Person> findByEmailIgnoreCase(String email);

}
