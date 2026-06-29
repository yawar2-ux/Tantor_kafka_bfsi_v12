package io.translab.tantor.server.governance.repository;

import io.translab.tantor.server.governance.domain.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    List<ApprovalRequest> findByStatusOrderByRequestedAtDesc(String status);
    List<ApprovalRequest> findAllByOrderByRequestedAtDesc();
    Optional<ApprovalRequest> findByJobId(UUID jobId);
    Optional<ApprovalRequest> findByIdempotencyKey(String idempotencyKey);
}
