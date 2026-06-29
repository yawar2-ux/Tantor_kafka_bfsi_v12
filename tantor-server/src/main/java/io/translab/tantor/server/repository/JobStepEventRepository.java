package io.translab.tantor.server.repository;

import io.translab.tantor.server.domain.JobStepEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobStepEventRepository extends JpaRepository<JobStepEvent, UUID> {
    List<JobStepEvent> findByJobIdOrderByCreatedAtAsc(UUID jobId);
    List<JobStepEvent> findByStepIdOrderByCreatedAtAsc(UUID stepId);
}
