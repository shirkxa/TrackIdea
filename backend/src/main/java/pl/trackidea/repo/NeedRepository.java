package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.Need;

import java.util.List;

public interface NeedRepository extends JpaRepository<Need, Long> {
    List<Need> findByOpenTrue();

    java.util.Optional<Need> findFirstBySourceNameAndSourceRef(String sourceName, String sourceRef);

    /** Otwarte potrzeby z zewnętrznych źródeł – do zamknięcia po terminie. */
    List<Need> findBySourceNameIsNotNullAndOpenTrue();

    long countBySourceName(String sourceName);
}
