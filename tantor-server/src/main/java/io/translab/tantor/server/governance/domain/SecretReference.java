package io.translab.tantor.server.governance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Stores ONLY a pointer to a secret in an external vault. Never the secret value. */
@Entity
@Table(name = "secret_references")
@Getter
@Setter
public class SecretReference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "secret_name", nullable = false)
    private String secretName;

    @Column(name = "secret_type", nullable = false)
    private String secretType;

    @Column(nullable = false)
    private String provider;

    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    @Column(name = "cluster_id")
    private UUID clusterId;

    private String environment;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "rotated_at")
    private OffsetDateTime rotatedAt;
}
