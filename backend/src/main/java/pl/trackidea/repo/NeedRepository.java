package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Need;

import java.util.List;

public interface NeedRepository extends JpaRepository<Need, Long> {
    List<Need> findByOpenTrue();
}
