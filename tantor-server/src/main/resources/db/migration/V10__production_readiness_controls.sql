-- Tantor Kafka Production Readiness Controls
-- Covers: rollback/resume, KRaft/ZooKeeper validation, package validation, config versioning,
-- rolling restart safety, onboarding mode, secrets references, audit, alerting hooks,
-- backup/DR, reconciliation, maintenance/decommission, environment/idempotency/locking,
-- centralized logs, health score, prerequisite checks, and review plans.

ALTER TABLE clusters ADD COLUMN IF NOT EXISTS management_level VARCHAR(50) DEFAULT 'INTERNAL_MANAGED';
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS health_score INT DEFAULT 0;
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS last_health_status VARCHAR(50);
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS last_health_check_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS security_mode VARCHAR(50) DEFAULT 'PLAINTEXT';
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS approval_required BOOLEAN DEFAULT FALSE;

ALTER TABLE hosts ADD COLUMN IF NOT EXISTS maintenance_mode BOOLEAN DEFAULT FALSE;
ALTER TABLE hosts ADD COLUMN IF NOT EXISTS maintenance_reason TEXT;
ALTER TABLE hosts ADD COLUMN IF NOT EXISTS maintenance_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE hosts ADD COLUMN IF NOT EXISTS environment VARCHAR(50);

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(150);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS operation_lock_id UUID;

CREATE TABLE IF NOT EXISTS approval_requests (
    id UUID PRIMARY KEY,
    request_type VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(150),
    requested_by VARCHAR(150) NOT NULL,
    approved_by VARCHAR(150),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    payload JSONB,
    requested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS operation_locks (
    id UUID PRIMARY KEY,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(150) NOT NULL,
    operation_type VARCHAR(100) NOT NULL,
    locked_by VARCHAR(150),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    acquired_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_operation_locks_active
    ON operation_locks(resource_type, resource_id)
    WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(150) UNIQUE NOT NULL,
    operation_type VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(150),
    request_hash VARCHAR(128),
    response_body JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    created_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS validation_results (
    id UUID PRIMARY KEY,
    job_id UUID REFERENCES job_master(id) ON DELETE SET NULL,
    cluster_id UUID REFERENCES clusters(id) ON DELETE SET NULL,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE SET NULL,
    validation_group VARCHAR(100) NOT NULL,
    validation_type VARCHAR(150) NOT NULL,
    status VARCHAR(50) NOT NULL,
    severity VARCHAR(50) DEFAULT 'ERROR',
    expected_value TEXT,
    actual_value TEXT,
    error_message TEXT,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_step_artifacts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES job_master(id) ON DELETE CASCADE,
    step_id UUID REFERENCES job_steps(id) ON DELETE SET NULL,
    task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    host_id VARCHAR(100),
    artifact_type VARCHAR(100) NOT NULL,
    artifact_path TEXT NOT NULL,
    artifact_status VARCHAR(50) DEFAULT 'CREATED',
    rollback_action VARCHAR(150),
    checksum VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rollback_plans (
    id UUID PRIMARY KEY,
    original_job_id UUID REFERENCES job_master(id) ON DELETE CASCADE,
    rollback_job_id UUID REFERENCES job_master(id) ON DELETE SET NULL,
    cluster_id UUID REFERENCES clusters(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(150),
    approved_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS rollback_steps (
    id UUID PRIMARY KEY,
    rollback_plan_id UUID REFERENCES rollback_plans(id) ON DELETE CASCADE,
    host_id VARCHAR(100),
    step_order INT NOT NULL,
    rollback_action VARCHAR(150) NOT NULL,
    target_artifact TEXT,
    status VARCHAR(50) DEFAULT 'PENDING',
    error_message TEXT,
    log_file_path TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS cleanup_plans (
    id UUID PRIMARY KEY,
    job_id UUID REFERENCES job_master(id) ON DELETE CASCADE,
    cluster_id UUID REFERENCES clusters(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(150),
    approved_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS cleanup_steps (
    id UUID PRIMARY KEY,
    cleanup_plan_id UUID REFERENCES cleanup_plans(id) ON DELETE CASCADE,
    host_id VARCHAR(100),
    artifact_path TEXT,
    cleanup_action VARCHAR(150) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    error_message TEXT,
    log_file_path TEXT
);

CREATE TABLE IF NOT EXISTS kafka_package_validations (
    id UUID PRIMARY KEY,
    package_id VARCHAR(150),
    package_name VARCHAR(255),
    kafka_version VARCHAR(100),
    file_path TEXT,
    file_size BIGINT,
    sha256_checksum VARCHAR(128),
    extension_valid BOOLEAN DEFAULT FALSE,
    version_detected BOOLEAN DEFAULT FALSE,
    checksum_generated BOOLEAN DEFAULT FALSE,
    extraction_test_status VARCHAR(50),
    duplicate_status VARCHAR(50),
    malware_scan_status VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    uploaded_by VARCHAR(150),
    validated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cluster_config_versions (
    id UUID PRIMARY KEY,
    cluster_id UUID REFERENCES clusters(id) ON DELETE CASCADE,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE SET NULL,
    component VARCHAR(100) NOT NULL,
    config_file_name VARCHAR(255) NOT NULL,
    config_version INT NOT NULL,
    old_config TEXT,
    new_config TEXT,
    config_diff TEXT,
    config_checksum VARCHAR(128),
    rollback_version INT,
    status VARCHAR(50) DEFAULT 'DRAFT',
    created_by VARCHAR(150),
    approved_by VARCHAR(150),
    applied_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cluster_security_configs (
    id UUID PRIMARY KEY,
    cluster_id UUID REFERENCES clusters(id) ON DELETE CASCADE,
    security_mode VARCHAR(50) NOT NULL,
    tls_enabled BOOLEAN DEFAULT FALSE,
    sasl_enabled BOOLEAN DEFAULT FALSE,
    acl_enabled BOOLEAN DEFAULT FALSE,
    mtls_enabled BOOLEAN DEFAULT FALSE,
    inter_broker_protocol VARCHAR(50),
    super_users TEXT,
    status VARCHAR(50) DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS secret_references (
    id UUID PRIMARY KEY,
    secret_name VARCHAR(150) NOT NULL,
    secret_type VARCHAR(100) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    external_ref TEXT NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(150),
    created_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS host_prerequisite_checks (
    id UUID PRIMARY KEY,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE CASCADE,
    job_id UUID REFERENCES job_master(id) ON DELETE SET NULL,
    java_version VARCHAR(100),
    java_status VARCHAR(50),
    user_permission_status VARCHAR(50),
    sudo_systemd_status VARCHAR(50),
    disk_status VARCHAR(50),
    port_status VARCHAR(50),
    firewall_status VARCHAR(50),
    hostname_resolution_status VARCHAR(50),
    ntp_status VARCHAR(50),
    network_status VARCHAR(50),
    ulimit_status VARCHAR(50),
    file_descriptor_status VARCHAR(50),
    memory_status VARCHAR(50),
    cpu_status VARCHAR(50),
    mount_writable_status VARCHAR(50),
    selinux_apparmor_status VARCHAR(50),
    overall_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cluster_health_snapshots (
    id UUID PRIMARY KEY,
    cluster_id UUID REFERENCES clusters(id) ON DELETE CASCADE,
    health_score INT NOT NULL DEFAULT 0,
    health_status VARCHAR(50),
    broker_availability_score INT DEFAULT 0,
    disk_score INT DEFAULT 0,
    under_replicated_partitions INT DEFAULT 0,
    offline_partitions INT DEFAULT 0,
    consumer_lag_status VARCHAR(50),
    controller_status VARCHAR(50),
    agent_connectivity_status VARCHAR(50),
    details JSONB,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reconciliation_findings (
    id UUID PRIMARY KEY,
    cluster_id UUID REFERENCES clusters(id) ON DELETE SET NULL,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE SET NULL,
    resource_type VARCHAR(100) NOT NULL,
    db_state VARCHAR(100),
    actual_state VARCHAR(100),
    systemd_state VARCHAR(100),
    port_state VARCHAR(100),
    kafka_admin_state VARCHAR(100),
    severity VARCHAR(50) DEFAULT 'WARNING',
    recommended_action VARCHAR(255),
    status VARCHAR(50) DEFAULT 'OPEN',
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS backup_records (
    id UUID PRIMARY KEY,
    backup_type VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(150),
    backup_path TEXT NOT NULL,
    checksum VARCHAR(128),
    status VARCHAR(50) DEFAULT 'SUCCESS',
    created_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    restored_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS decommission_plans (
    id UUID PRIMARY KEY,
    cluster_id UUID REFERENCES clusters(id) ON DELETE SET NULL,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE SET NULL,
    component VARCHAR(100),
    status VARCHAR(50) DEFAULT 'DRAFT',
    impact_summary TEXT,
    quorum_impact_status VARCHAR(50),
    partition_drain_status VARCHAR(50),
    archive_path TEXT,
    created_by VARCHAR(150),
    approved_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS centralized_log_references (
    id UUID PRIMARY KEY,
    job_id UUID REFERENCES job_master(id) ON DELETE SET NULL,
    cluster_id UUID REFERENCES clusters(id) ON DELETE SET NULL,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE SET NULL,
    component VARCHAR(100),
    log_type VARCHAR(100),
    log_file_path TEXT,
    external_log_url TEXT,
    retention_days INT DEFAULT 30,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS review_plans (
    id UUID PRIMARY KEY,
    operation_type VARCHAR(100) NOT NULL,
    cluster_id UUID REFERENCES clusters(id) ON DELETE SET NULL,
    plan_payload JSONB NOT NULL,
    status VARCHAR(50) DEFAULT 'DRAFT',
    created_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_validation_results_cluster ON validation_results(cluster_id);
CREATE INDEX IF NOT EXISTS idx_validation_results_job ON validation_results(job_id);
CREATE INDEX IF NOT EXISTS idx_config_versions_cluster ON cluster_config_versions(cluster_id);
CREATE INDEX IF NOT EXISTS idx_health_snapshots_cluster ON cluster_health_snapshots(cluster_id);
CREATE INDEX IF NOT EXISTS idx_reconciliation_cluster ON reconciliation_findings(cluster_id);
CREATE INDEX IF NOT EXISTS idx_review_plans_cluster ON review_plans(cluster_id);
