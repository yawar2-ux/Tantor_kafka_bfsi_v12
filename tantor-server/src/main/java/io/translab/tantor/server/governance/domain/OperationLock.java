package io.translab.tantor.server.governance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One active operation per (lock_scope, scope_id). UNIQUE constraint enforces this. */
@Entity
@Table(name = "operation_locks")
@Getter
@Setter
public class OperationLock {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lock_scope", nullable = false)
    private String lockScope; // CLUSTER / HOST

    @Column(name = "scope_id", nullable = false)
    private String scopeId;

    @Column(nullable = false)
    private String operation;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "locked_at", insertable = false, updatable = false)
    private OffsetDateTime lockedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
