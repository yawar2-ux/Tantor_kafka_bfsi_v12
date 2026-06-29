package io.translab.tantor.server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_master")
@Getter
@Setter
public class JobMaster {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Column(name = "cluster_id")
    private UUID clusterId;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(nullable = false)
    private String status;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "current_host_id")
    private String currentHostId;

    @Column(name = "total_steps")
    private Integer totalSteps = 0;

    @Column(name = "completed_steps")
    private Integer completedSteps = 0;

    @Column(name = "failed_steps")
    private Integer failedSteps = 0;

    @Column(name = "progress_percentage")
    private Integer progressPercentage = 0;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "rollback_available")
    private Boolean rollbackAvailable = false;

    @Column(name = "rollback_status")
    private String rollbackStatus;

    @Column(name = "log_reference")
    private String logReference;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
