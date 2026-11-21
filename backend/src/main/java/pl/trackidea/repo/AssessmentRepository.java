package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Assessment;
import pl.trackidea.domain.Innovation;

import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {
    Optional<Assessment> findTopByInnovationOrderByCreatedAtDescIdDesc(Innovation innovation);
}
