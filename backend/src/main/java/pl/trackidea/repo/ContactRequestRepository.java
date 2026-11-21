package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.ContactRequest;
import pl.trackidea.domain.ContactStatus;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.Person;

import java.util.List;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {
    List<ContactRequest> findByToPersonOrFromPersonOrderByCreatedAtDesc(Person to, Person from);

    List<ContactRequest> findByInnovation(Innovation innovation);

    boolean existsByInnovationAndFromPersonAndStatusNot(Innovation innovation, Person from, ContactStatus status);

    long countByStatus(ContactStatus status);
}
