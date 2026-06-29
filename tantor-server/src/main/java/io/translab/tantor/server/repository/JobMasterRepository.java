package io.translab.tantor.server.repository;

import io.translab.tantor.server.domain.JobMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobMasterRepository extends JpaRepository<JobMaster, UUID> {
    List<JobMaster> findByClusterIdOrderByCreatedAtDesc(UUID clusterId);
}
