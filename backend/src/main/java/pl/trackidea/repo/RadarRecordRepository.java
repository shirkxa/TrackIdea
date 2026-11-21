package pl.trackidea.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.trackidea.domain.RadarRecord;

import java.util.List;

public interface RadarRecordRepository extends JpaRepository<RadarRecord, Long> {
    boolean existsBySourceAndRef(String source, String ref);

    java.util.Optional<RadarRecord> findFirstBySourceAndRef(String source, String ref);

    long countBySource(String source);

    List<RadarRecord> findAllByOrderByImportedAtDescIdDesc();

    long countByOutcome(RadarRecord.Outcome outcome);
}
