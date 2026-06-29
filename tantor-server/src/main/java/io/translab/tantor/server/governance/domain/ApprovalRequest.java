package io.translab.tantor.server.governance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maker-Checker approval request. A "maker" raises a risky action; a "checker"
 * (APPROVER/ADMIN, different from the maker) approves or rejects it.
 */
@Entity
@Table(name = "approval_requests")
@Getter
@Setter
public class ApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    private String environment;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING/APPROVED/REJECTED/EXPIRED

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "requested_at", insertable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
}
