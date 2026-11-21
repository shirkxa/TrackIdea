package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Innovation;
import pl.trackidea.domain.InnovationEvent;

import java.util.List;

public interface InnovationEventRepository extends JpaRepository<InnovationEvent, Long> {
    List<InnovationEvent> findByInnovationOrderByCreatedAtAscIdAsc(Innovation innovation);
}
