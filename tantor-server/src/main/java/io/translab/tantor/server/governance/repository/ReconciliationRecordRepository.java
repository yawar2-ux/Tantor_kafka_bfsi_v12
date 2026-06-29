package io.translab.tantor.server.governance.repository;

import io.translab.tantor.server.governance.domain.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, UUID> {
    List<ReconciliationRecord> findByDriftDetectedTrueAndResolvedFalse();
    List<ReconciliationRecord> findByClusterIdOrderByDetectedAtDesc(UUID clusterId);
}
