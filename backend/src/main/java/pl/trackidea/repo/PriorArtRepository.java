package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.PriorArt;

public interface PriorArtRepository extends JpaRepository<PriorArt, Long> {
}
