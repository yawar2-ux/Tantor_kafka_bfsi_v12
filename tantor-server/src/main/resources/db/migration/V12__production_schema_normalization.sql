-- =====================================================================
-- V12 : Production schema normalization
-- Purpose: V10 introduced broad production tables and V11 introduced the
-- BFSI governance JPA model. On databases migrated through both versions,
-- IF NOT EXISTS can leave pre-existing tables without the columns expected
-- by the runtime entities. This migration normalizes those schemas safely.
-- =====================================================================

-- Required extension for gen_random_uuid() used by seed scripts.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- Cluster/host environment and safety metadata
-- ---------------------------------------------------------------------
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS environment VARCHAR(20) DEFAULT 'DEV';
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS management_level VARCHAR(50) DEFAULT 'INTERNAL_MANAGED';
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS health_score INT DEFAULT 0;
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS last_health_status VARCHAR(50);
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS last_health_check_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS security_mode VARCHAR(50) DEFAULT 'PLAINTEXT';
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS approval_required BOOLEAN DEFAULT FALSE;

ALTER TABLE hosts ADD COLUMN IF NOT EXISTS environment VARCHAR(20) DEFAULT 'DEV';
ALTER TABLE hosts ADD COLUMN IF NOT EXISTS maintenance_mode BOOLEAN DEFAULT FALSE;
ALTER TABLE hosts ADD COLUMN IF NOT EXISTS maintenance_reason TEXT;
ALTER TABLE hosts ADD COLUMN IF NOT EXISTS maintenance_started_at TIMESTAMP WITH TIME ZONE;

-- ---------------------------------------------------------------------
-- Approval requests: support both legacy V10 columns and V11 entity columns.
-- ---------------------------------------------------------------------
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS job_id UUID REFERENCES job_master(id) ON DELETE SET NULL;
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS action_type VARCHAR(60);
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS request_type VARCHAR(100);
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS environment VARCHAR(20);
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS payload_json TEXT;
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS payload JSONB;
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS decided_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS requested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS approved_by VARCHAR(150);
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'PENDING';
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS resource_type VARCHAR(100);
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS resource_id VARCHAR(150);
ALTER TABLE approval_requests ADD COLUMN IF NOT EXISTS requested_by VARCHAR(150);
UPDATE approval_requests SET action_type = COALESCE(action_type, request_type, 'CHANGE') WHERE action_type IS NULL;
UPDATE approval_requests SET request_type = COALESCE(request_type, action_type, 'CHANGE') WHERE request_type IS NULL;
UPDATE approval_requests SET payload_json = COALESCE(payload_json, payload::TEXT, '{}') WHERE payload_json IS NULL;
CREATE INDEX IF NOT EXISTS idx_approval_status_v12 ON approval_requests(status);
CREATE INDEX IF NOT EXISTS idx_approval_job_v12 ON approval_requests(job_id);
CREATE INDEX IF NOT EXISTS idx_approval_idempotency_v12 ON approval_requests(idempotency_key);

-- ---------------------------------------------------------------------
-- Operation locks: normalize resource_* and lock_scope/scope_id models.
-- ---------------------------------------------------------------------
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS lock_scope VARCHAR(20);
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS scope_id VARCHAR(120);
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS operation VARCHAR(60);
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS job_id UUID;
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS resource_type VARCHAR(100);
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS resource_id VARCHAR(150);
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS operation_type VARCHAR(100);
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS locked_by VARCHAR(150);
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'ACTIVE';
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS acquired_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE operation_locks ADD COLUMN IF NOT EXISTS released_at TIMESTAMP WITH TIME ZONE;
UPDATE operation_locks SET lock_scope = COALESCE(lock_scope, resource_type, 'UNKNOWN') WHERE lock_scope IS NULL;
UPDATE operation_locks SET scope_id = COALESCE(scope_id, resource_id, id::TEXT) WHERE scope_id IS NULL;
UPDATE operation_locks SET operation = COALESCE(operation, operation_type, 'UNKNOWN') WHERE operation IS NULL;
UPDATE operation_locks SET resource_type = COALESCE(resource_type, lock_scope) WHERE resource_type IS NULL;
UPDATE operation_locks SET resource_id = COALESCE(resource_id, scope_id) WHERE resource_id IS NULL;
UPDATE operation_locks SET operation_type = COALESCE(operation_type, operation) WHERE operation_type IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_operation_locks_scope_active_v12
    ON operation_locks(lock_scope, scope_id)
    WHERE COALESCE(status, 'ACTIVE') = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_operation_locks_job_v12 ON operation_locks(job_id);

-- ---------------------------------------------------------------------
-- Idempotency keys: support entity primary key model plus legacy metadata.
-- ---------------------------------------------------------------------
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS action_type VARCHAR(60);
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS operation_type VARCHAR(100);
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS job_id UUID;
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS response_ref VARCHAR(255);
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS response_body JSONB;
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'IN_PROGRESS';
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS created_by VARCHAR(150);
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
UPDATE idempotency_keys SET action_type = COALESCE(action_type, operation_type) WHERE action_type IS NULL;
UPDATE idempotency_keys SET operation_type = COALESCE(operation_type, action_type) WHERE operation_type IS NULL;
CREATE INDEX IF NOT EXISTS idx_idempotency_job_v12 ON idempotency_keys(job_id);

-- ---------------------------------------------------------------------
-- Audit logs: normalize original user_id/details model and governance model.
-- ---------------------------------------------------------------------
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor VARCHAR(150);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_role VARCHAR(50);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS resource_type VARCHAR(40);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS resource_id VARCHAR(120);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS environment VARCHAR(20);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS old_value TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS new_value TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS approval_id UUID;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS user_agent VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_type VARCHAR(50);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_id VARCHAR(100);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS details JSONB;
-- Temporarily remove immutable triggers while normalizing legacy rows during migration.
DROP TRIGGER IF EXISTS audit_no_update ON audit_logs;
DROP TRIGGER IF EXISTS audit_no_delete ON audit_logs;
UPDATE audit_logs SET actor = COALESCE(actor, user_id::TEXT, 'system') WHERE actor IS NULL;
UPDATE audit_logs SET resource_type = COALESCE(resource_type, entity_type) WHERE resource_type IS NULL;
UPDATE audit_logs SET resource_id = COALESCE(resource_id, entity_id) WHERE resource_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_audit_actor_v12 ON audit_logs(actor);
CREATE INDEX IF NOT EXISTS idx_audit_action_v12 ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_resource_v12 ON audit_logs(resource_type, resource_id);

-- ---------------------------------------------------------------------
-- Secret references: normalize V10 external_ref model and V11 reference_id model.
-- ---------------------------------------------------------------------
ALTER TABLE secret_references ADD COLUMN IF NOT EXISTS reference_id VARCHAR(255);
ALTER TABLE secret_references ADD COLUMN IF NOT EXISTS external_ref TEXT;
ALTER TABLE secret_references ADD COLUMN IF NOT EXISTS resource_type VARCHAR(100);
ALTER TABLE secret_references ADD COLUMN IF NOT EXISTS resource_id VARCHAR(150);
ALTER TABLE secret_references ADD COLUMN IF NOT EXISTS cluster_id UUID REFERENCES clusters(id) ON DELETE CASCADE;
ALTER TABLE secret_references ADD COLUMN IF NOT EXISTS environment VARCHAR(20);
ALTER TABLE secret_references ADD COLUMN IF NOT EXISTS created_by VARCHAR(150);
ALTER TABLE secret_references ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE secret_references ADD COLUMN IF NOT EXISTS rotated_at TIMESTAMP WITH TIME ZONE;
UPDATE secret_references SET reference_id = COALESCE(reference_id, external_ref) WHERE reference_id IS NULL;
UPDATE secret_references SET external_ref = COALESCE(external_ref, reference_id) WHERE external_ref IS NULL;
CREATE INDEX IF NOT EXISTS idx_secret_refs_cluster_v12 ON secret_references(cluster_id);
CREATE INDEX IF NOT EXISTS idx_secret_refs_resource_v12 ON secret_references(resource_type, resource_id);

-- ---------------------------------------------------------------------
-- Agent authentication and lifecycle metadata.
-- DB stores only hashes/references, never raw bootstrap tokens.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    token_hint VARCHAR(40),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(host_id)
);

CREATE TABLE IF NOT EXISTS agent_registration_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id VARCHAR(100),
    hostname VARCHAR(255),
    ip_addresses JSONB,
    agent_version VARCHAR(50),
    requested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    requested_by VARCHAR(150),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    approved_by VARCHAR(150),
    decided_at TIMESTAMP WITH TIME ZONE,
    rejection_reason TEXT
);

-- ---------------------------------------------------------------------
-- KRaft and ZooKeeper first-class topology metadata.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kraft_cluster_metadata (
    cluster_id UUID PRIMARY KEY REFERENCES clusters(id) ON DELETE CASCADE,
    kraft_cluster_id VARCHAR(120) NOT NULL,
    controller_count INT NOT NULL DEFAULT 0,
    broker_count INT NOT NULL DEFAULT 0,
    deployment_type VARCHAR(40),
    quorum_status VARCHAR(40) DEFAULT 'UNKNOWN',
    controller_quorum_voters TEXT,
    quorum_required INT,
    created_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kraft_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id UUID REFERENCES clusters(id) ON DELETE CASCADE,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE CASCADE,
    node_id INT NOT NULL,
    process_roles VARCHAR(80) NOT NULL,
    is_broker BOOLEAN DEFAULT FALSE,
    is_controller BOOLEAN DEFAULT FALSE,
    broker_port INT DEFAULT 9092,
    controller_port INT DEFAULT 9093,
    advertised_listener TEXT,
    controller_listener TEXT,
    log_dirs TEXT,
    config_path TEXT,
    service_name VARCHAR(150),
    service_status VARCHAR(50) DEFAULT 'UNKNOWN',
    storage_formatted BOOLEAN DEFAULT FALSE,
    status VARCHAR(50) DEFAULT 'PLANNED',
    UNIQUE(cluster_id, node_id),
    UNIQUE(cluster_id, host_id)
);

CREATE TABLE IF NOT EXISTS kraft_storage_formats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id UUID REFERENCES clusters(id) ON DELETE CASCADE,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE CASCADE,
    node_id INT,
    expected_cluster_id VARCHAR(120),
    detected_cluster_id VARCHAR(120),
    expected_node_id INT,
    detected_node_id INT,
    log_dirs TEXT,
    meta_properties_path TEXT,
    format_status VARCHAR(50) DEFAULT 'PENDING',
    error_message TEXT,
    formatted_at TIMESTAMP WITH TIME ZONE,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS zookeeper_ensembles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id UUID REFERENCES clusters(id) ON DELETE CASCADE,
    ensemble_name VARCHAR(120),
    connect_string TEXT NOT NULL,
    node_count INT NOT NULL DEFAULT 0,
    quorum_required INT NOT NULL DEFAULT 0,
    status VARCHAR(50) DEFAULT 'PLANNED',
    created_by VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS zookeeper_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ensemble_id UUID REFERENCES zookeeper_ensembles(id) ON DELETE CASCADE,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE CASCADE,
    myid INT NOT NULL,
    client_port INT DEFAULT 2181,
    peer_port INT DEFAULT 2888,
    election_port INT DEFAULT 3888,
    data_dir TEXT,
    log_dir TEXT,
    config_path TEXT,
    service_name VARCHAR(150),
    service_status VARCHAR(50) DEFAULT 'UNKNOWN',
    role VARCHAR(50) DEFAULT 'UNKNOWN',
    last_health_check_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) DEFAULT 'PLANNED',
    UNIQUE(ensemble_id, myid),
    UNIQUE(ensemble_id, host_id)
);

CREATE TABLE IF NOT EXISTS kafka_broker_zk_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cluster_id UUID REFERENCES clusters(id) ON DELETE CASCADE,
    broker_host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE CASCADE,
    broker_id INT NOT NULL,
    ensemble_id UUID REFERENCES zookeeper_ensembles(id) ON DELETE CASCADE,
    zookeeper_connect TEXT NOT NULL,
    broker_config_id UUID,
    status VARCHAR(50) DEFAULT 'PLANNED',
    validated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(cluster_id, broker_id),
    UNIQUE(cluster_id, broker_host_id)
);

-- Validation result aliases for KRaft/ZK while retaining generic validation_results.
CREATE TABLE IF NOT EXISTS topology_validation_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES job_master(id) ON DELETE SET NULL,
    cluster_id UUID REFERENCES clusters(id) ON DELETE SET NULL,
    host_id VARCHAR(100) REFERENCES hosts(id) ON DELETE SET NULL,
    topology_type VARCHAR(30) NOT NULL,
    validation_type VARCHAR(150) NOT NULL,
    status VARCHAR(50) NOT NULL,
    severity VARCHAR(50) DEFAULT 'ERROR',
    expected_value TEXT,
    actual_value TEXT,
    error_message TEXT,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_topology_validation_cluster_v12 ON topology_validation_results(cluster_id);
CREATE INDEX IF NOT EXISTS idx_topology_validation_job_v12 ON topology_validation_results(job_id);

-- Keep production audit immutable even on installations that had audit_logs from V1.
CREATE OR REPLACE FUNCTION trg_audit_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only; % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS audit_no_update ON audit_logs;
CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION trg_audit_immutable();

DROP TRIGGER IF EXISTS audit_no_delete ON audit_logs;
CREATE TRIGGER audit_no_delete BEFORE DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION trg_audit_immutable();
