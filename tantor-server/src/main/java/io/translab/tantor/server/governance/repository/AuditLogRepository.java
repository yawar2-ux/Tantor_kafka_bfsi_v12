package io.translab.tantor.server.governance.repository;

import io.translab.tantor.server.governance.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import java.util.UUID;

/**
 * Append-only repository. Intentionally extends {@link Repository} (not JpaRepository)
 * so that delete()/deleteAll() are not even available on the API surface. The DB also
 * enforces immutability via triggers (see V11).
 */
public interface AuditLogRepository extends Repository<AuditLog, UUID> {
    AuditLog save(AuditLog auditLog);
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuditLog> findByActorOrderByCreatedAtDesc(String actor, Pageable pageable);
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
}
