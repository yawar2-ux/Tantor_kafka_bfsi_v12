package io.translab.tantor.server.governance.repository;

import io.translab.tantor.server.governance.domain.OperationLock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationLockRepository extends JpaRepository<OperationLock, UUID> {
    Optional<OperationLock> findByLockScopeAndScopeId(String lockScope, String scopeId);
    List<OperationLock> findByJobId(UUID jobId);
}
