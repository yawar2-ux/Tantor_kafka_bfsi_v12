package io.translab.tantor.server.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tasks")
@Getter
@Setter
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "host_id", nullable = false)
    private String hostId;

    @Column(name = "cluster_id")
    private UUID clusterId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "job_step_id")
    private UUID jobStepId;

    @Column(nullable = false)
    private String command;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String parameters; // JSON string

    @Column(name = "artifact_url")
    private String artifactUrl;

    private String checksum;

    @Column(nullable = false)
    private String status; // PENDING, IN_PROGRESS, SUCCESS, FAILED

    @Column(name = "log_output")
    private String logOutput;

    @Column(name = "log_file_path")
    private String logFilePath;

    @Column(name = "current_step_code")
    private String currentStepCode;

    @Column(name = "current_step_name")
    private String currentStepName;

    @Column(name = "error_msg")
    private String errorMsg;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
