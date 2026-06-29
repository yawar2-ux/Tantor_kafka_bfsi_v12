package io.translab.tantor.server.governance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_records")
@Getter
@Setter
public class ReconciliationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cluster_id")
    private UUID clusterId;

    @Column(name = "host_id")
    private String hostId;

    private String component;

    @Column(name = "db_state")
    private String dbState;

    @Column(name = "actual_state")
    private String actualState;

    @Column(name = "drift_detected", nullable = false)
    private boolean driftDetected = false;

    @Column(name = "recommended_action")
    private String recommendedAction;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "detected_at", insertable = false, updatable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
