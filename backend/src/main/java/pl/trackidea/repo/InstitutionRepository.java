package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Institution;

import java.util.Optional;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {
    Optional<Institution> findFirstByNameIgnoreCase(String name);

    Optional<Institution> findFirstByNameKey(String nameKey);
}
