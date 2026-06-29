package io.translab.tantor.server.governance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
public class IdempotencyKey {
    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "response_ref")
    private String responseRef;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
