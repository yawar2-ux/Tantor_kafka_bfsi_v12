package io.translab.tantor.server.repository;

import io.translab.tantor.server.domain.JobStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobStepRepository extends JpaRepository<JobStep, UUID> {
    List<JobStep> findByJobIdOrderByStepOrderAsc(UUID jobId);
    List<JobStep> findByJobIdAndHostIdOrderByStepOrderAsc(UUID jobId, String hostId);
    Optional<JobStep> findFirstByJobIdAndHostIdAndStepCodeOrderByStepOrderAsc(UUID jobId, String hostId, String stepCode);
    Optional<JobStep> findFirstByTaskIdAndStepCodeOrderByStepOrderAsc(UUID taskId, String stepCode);
}
