-- =====================================================================
-- V11 : BFSI Production Hardening & Governance Layer
-- Adds: RBAC permissions, Maker-Checker Approvals, Immutable Audit,
--       Secrets references, Operation locks, Idempotency keys,
--       Reconciliation records, Environment policies.
-- All statements are idempotent-safe for re-run on partially migrated DBs.
-- =====================================================================

-- Required by the UUID seed statements below on PostgreSQL versions where
-- gen_random_uuid() is provided by pgcrypto rather than core PostgreSQL.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- 1. RBAC : add APPROVER role + fine-grained permissions
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description) VALUES ('APPROVER', 'Checker who approves or rejects risky actions')
    ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name, description) VALUES
    ('CLUSTER_VIEW',        'View clusters, brokers, topics, metrics'),
    ('CLUSTER_DEPLOY',      'Create / deploy new Kafka clusters'),
    ('CLUSTER_ONBOARD',     'Onboard existing clusters'),
    ('CONFIG_CHANGE',       'Change cluster / broker configuration'),
    ('ROLLING_RESTART',     'Trigger rolling restart'),
    ('HOST_MANAGE',         'Add / remove / maintenance hosts'),
    ('DECOMMISSION',        'Decommission broker / controller / host'),
    ('PACKAGE_MANAGE',      'Upload / validate / deprecate packages'),
    ('MONITORING_MANAGE',   'Enable / disable monitoring'),
    ('SECRET_MANAGE',       'Manage secret references'),
    ('APPROVAL_DECIDE',     'Approve or reject maker-checker requests'),
    ('AUDIT_VIEW',          'View immutable audit trail'),
    ('RECONCILE_RUN',       'Run reconciliation / drift remediation'),
    ('RBAC_MANAGE',         'Manage users, roles and permissions')
    ON CONFLICT (name) DO NOTHING;

-- Map permissions to roles (idempotent)
-- ADMIN : everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- OPERATOR : operational actions but NOT approval / rbac
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.name IN ('CLUSTER_VIEW','CLUSTER_DEPLOY','CLUSTER_ONBOARD','CONFIG_CHANGE',
                'ROLLING_RESTART','HOST_MANAGE','PACKAGE_MANAGE','MONITORING_MANAGE',
                'RECONCILE_RUN','AUDIT_VIEW')
WHERE r.name = 'OPERATOR'
ON CONFLICT DO NOTHING;

-- APPROVER : view + approve + audit
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.name IN ('CLUSTER_VIEW','APPROVAL_DECIDE','AUDIT_VIEW')
WHERE r.name = 'APPROVER'
ON CONFLICT DO NOTHING;

-- VIEWER : read-only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.name IN ('CLUSTER_VIEW','AUDIT_VIEW')
WHERE r.name = 'VIEWER'
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Seed default users (BCrypt). CHANGE THESE PASSWORDS ON FIRST LOGIN.
--    admin    / Tantor@Admin#2026
--    approver / Tantor@Appr#2026
--    operator / Tantor@Oper#2026
--    viewer   / Tantor@View#2026
-- ---------------------------------------------------------------------
INSERT INTO users (id, username, password_hash, email, role_id, is_active)
SELECT gen_random_uuid(), 'admin',
       '$2b$10$lsz0jQC54UcQbIRUy8clTeusDasAOpzW9t2OhHAq89zvdZxraPmbC',
       'admin@tantor.local', r.id, TRUE
FROM roles r WHERE r.name='ADMIN'
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, password_hash, email, role_id, is_active)
SELECT gen_random_uuid(), 'approver',
       '$2b$10$WRc5yAZU8vmOtAo630PxAOQ9Sdsnm17oiwTuUA53meeqX/.PWnSWS',
       'approver@tantor.local', r.id, TRUE
FROM roles r WHERE r.name='APPROVER'
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, password_hash, email, role_id, is_active)
SELECT gen_random_uuid(), 'operator',
       '$2b$10$R3xVXUOv65oZmok/AQcKB.go3wF7eWTcuPCoz32jzmCwLBkrUPxnq',
       'operator@tantor.local', r.id, TRUE
FROM roles r WHERE r.name='OPERATOR'
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (id, username, password_hash, email, role_id, is_active)
SELECT gen_random_uuid(), 'viewer',
       '$2b$10$O.5TggTMr22OZ.bunEC9deoaaqL/yeUHKSnrmIqmOkAti7Kl91viK',
       'viewer@tantor.local', r.id, TRUE
FROM roles r WHERE r.name='VIEWER'
ON CONFLICT (username) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Environment policy : which environments need maker-checker + retention
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS environment_policies (
    environment            VARCHAR(20) PRIMARY KEY,           -- DEV/SIT/UAT/PROD/DR
    requires_approval      BOOLEAN NOT NULL DEFAULT TRUE,
    min_approvers          INT     NOT NULL DEFAULT 1,
    audit_retention_days   INT     NOT NULL DEFAULT 365,
    separate_credentials   BOOLEAN NOT NULL DEFAULT FALSE,
    description            VARCHAR(255)
);

INSERT INTO environment_policies (environment, requires_approval, min_approvers, audit_retention_days, separate_credentials, description) VALUES
    ('DEV',  FALSE, 0, 180,  FALSE, 'Development - approval optional'),
    ('SIT',  TRUE,  1, 365,  FALSE, 'System Integration Test'),
    ('UAT',  TRUE,  1, 730,  TRUE,  'User Acceptance Test'),
    ('PROD', TRUE,  1, 2555, TRUE,  'Production - dual control, 7y audit retention (RBI/DPDP)'),
    ('DR',   TRUE,  1, 2555, TRUE,  'Disaster Recovery - production-grade controls')
ON CONFLICT (environment) DO NOTHING;

-- ---------------------------------------------------------------------
-- 4. Maker-Checker approval requests
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_requests (
    id               UUID PRIMARY KEY,
    job_id           UUID REFERENCES job_master(id) ON DELETE SET NULL,
    action_type      VARCHAR(60)  NOT NULL,   -- DEPLOYMENT/CONFIG_CHANGE/ROLLING_RESTART...
    resource_type    VARCHAR(40)  NOT NULL,   -- CLUSTER/HOST/PACKAGE/SECRET
    resource_id      VARCHAR(120),
    environment      VARCHAR(20),
    payload_json     TEXT,                     -- snapshot of the requested change
    requested_by     VARCHAR(150) NOT NULL,
    approved_by      VARCHAR(150),
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED/EXPIRED
    rejection_reason TEXT,
    idempotency_key  VARCHAR(120),
    requested_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    decided_at       TIMESTAMP WITH TIME ZONE,
    expires_at       TIMESTAMP WITH TIME ZONE
);
-- V10 creates approval_requests with the production-readiness shape. When
-- upgrading that schema, CREATE TABLE IF NOT EXISTS does not add V11 columns.
ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS job_id UUID REFERENCES job_master(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_approval_status ON approval_requests(status);
CREATE INDEX IF NOT EXISTS idx_approval_job ON approval_requests(job_id);

-- ---------------------------------------------------------------------
-- 5. Immutable audit trail (append-only, enforced by trigger)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id             UUID PRIMARY KEY,
    actor          VARCHAR(150) NOT NULL,
    actor_role     VARCHAR(50),
    action         VARCHAR(80)  NOT NULL,
    resource_type  VARCHAR(40),
    resource_id    VARCHAR(120),
    environment    VARCHAR(20),
    old_value      TEXT,
    new_value      TEXT,
    status         VARCHAR(30),               -- SUCCESS/FAILED/PENDING
    approval_id    UUID,
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
-- V1 creates audit_logs with user_id/entity_type/entity_id/details. Normalize
-- the legacy table before V11 creates indexes and immutability triggers.
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
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_logs(actor);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at);

-- Enforce immutability: block UPDATE and DELETE at the database level
CREATE OR REPLACE FUNCTION trg_audit_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only; % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS audit_no_update ON audit_logs;
CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE PROCEDURE trg_audit_immutable();

DROP TRIGGER IF EXISTS audit_no_delete ON audit_logs;
CREATE TRIGGER audit_no_delete BEFORE DELETE ON audit_logs
    FOR EACH ROW EXECUTE PROCEDURE trg_audit_immutable();

-- ---------------------------------------------------------------------
-- 6. Secret references (DB never stores raw secrets)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS secret_references (
    id              UUID PRIMARY KEY,
    secret_name     VARCHAR(150) NOT NULL,
    secret_type     VARCHAR(50)  NOT NULL,    -- KEYSTORE_PASSWORD/SASL/AGENT_TOKEN/DB/LDAP
    provider        VARCHAR(40)  NOT NULL,    -- LOCAL_VAULT/HASHICORP/CYBERARK/AWS/AZURE
    reference_id    VARCHAR(255) NOT NULL,    -- path / ARN / key id in the external vault
    cluster_id      UUID REFERENCES clusters(id) ON DELETE CASCADE,
    environment     VARCHAR(20),
    created_by      VARCHAR(150),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    rotated_at      TIMESTAMP WITH TIME ZONE,
    UNIQUE (secret_name, cluster_id)
);

-- ---------------------------------------------------------------------
-- 7. Operation locks (one active op per cluster / host)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS operation_locks (
    id            UUID PRIMARY KEY,
    lock_scope    VARCHAR(20) NOT NULL,       -- CLUSTER / HOST
    scope_id      VARCHAR(120) NOT NULL,
    operation     VARCHAR(60) NOT NULL,
    job_id        UUID,
    locked_by     VARCHAR(150),
    locked_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMP WITH TIME ZONE,
    UNIQUE (lock_scope, scope_id)
);

-- ---------------------------------------------------------------------
-- 8. Idempotency keys (dangerous actions de-dup)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key VARCHAR(120) PRIMARY KEY,
    action_type     VARCHAR(60),
    job_id          UUID,
    response_ref    VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP WITH TIME ZONE
);

-- ---------------------------------------------------------------------
-- 9. Reconciliation records (DB vs actual drift)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reconciliation_records (
    id              UUID PRIMARY KEY,
    cluster_id      UUID REFERENCES clusters(id) ON DELETE CASCADE,
    host_id         VARCHAR(120),
    component       VARCHAR(60),
    db_state        VARCHAR(40),
    actual_state    VARCHAR(40),
    drift_detected  BOOLEAN NOT NULL DEFAULT FALSE,
    recommended_action VARCHAR(120),
    resolved        BOOLEAN NOT NULL DEFAULT FALSE,
    detected_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_recon_cluster ON reconciliation_records(cluster_id);
CREATE INDEX IF NOT EXISTS idx_recon_drift ON reconciliation_records(drift_detected);

-- Default environment for existing clusters that have none
UPDATE clusters SET environment = 'DEV' WHERE environment IS NULL OR environment = '';
