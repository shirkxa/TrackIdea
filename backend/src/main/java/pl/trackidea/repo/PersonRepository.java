package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Institution;
import pl.trackidea.domain.Person;
import pl.trackidea.domain.Role;

import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {
    List<Person> findByRole(Role role);

    List<Person> findByInstitution(Institution institution);

    List<Person> findByInstitutionAndRole(Institution institution, Role role);
}
