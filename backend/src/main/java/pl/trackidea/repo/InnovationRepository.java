package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationStatus;
import pl.trackidea.domain.Institution;

import java.util.Collection;
import java.util.List;

public interface InnovationRepository extends JpaRepository<Innovation, Long> {
    List<Innovation> findByStatusIn(Collection<InnovationStatus> statuses);

    List<Innovation> findByInstitution(Institution institution);

}
