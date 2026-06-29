package io.translab.tantor.server.governance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "environment_policies")
@Getter
@Setter
public class EnvironmentPolicy {
    @Id
    private String environment; // DEV/SIT/UAT/PROD/DR

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Column(name = "min_approvers", nullable = false)
    private int minApprovers = 1;

    @Column(name = "audit_retention_days", nullable = false)
    private int auditRetentionDays = 365;

    @Column(name = "separate_credentials", nullable = false)
    private boolean separateCredentials = false;

    private String description;
}
