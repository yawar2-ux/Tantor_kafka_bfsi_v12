package io.translab.tantor.server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_steps")
@Getter
@Setter
public class JobStep {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_code", nullable = false)
    private String stepCode;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "host_id")
    private String hostId;

    @Column(name = "component")
    private String component;

    @Column(nullable = false)
    private String status;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "log_file_path")
    private String logFilePath;

    @Column(name = "log_excerpt", columnDefinition = "TEXT")
    private String logExcerpt;

    @Column(name = "rollback_step_available")
    private Boolean rollbackStepAvailable = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
