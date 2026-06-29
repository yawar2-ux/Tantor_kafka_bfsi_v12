-- Production-grade Job Engine + Step-Level Deployment Tracking

CREATE TABLE IF NOT EXISTS job_master (
    id UUID PRIMARY KEY,
    job_type VARCHAR(100) NOT NULL,
    cluster_id UUID REFERENCES clusters(id) ON DELETE SET NULL,
    requested_by VARCHAR(150),
    approved_by VARCHAR(150),
    status VARCHAR(50) NOT NULL,
    current_step VARCHAR(255),
    current_host_id VARCHAR(100),
    total_steps INT DEFAULT 0,
    completed_steps INT DEFAULT 0,
    failed_steps INT DEFAULT 0,
    progress_percentage INT DEFAULT 0,
    failure_reason TEXT,
    retry_count INT DEFAULT 0,
    rollback_available BOOLEAN DEFAULT FALSE,
    rollback_status VARCHAR(50),
    log_reference VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_steps (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES job_master(id) ON DELETE CASCADE,
    task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    step_order INT NOT NULL,
    step_code VARCHAR(100) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    host_id VARCHAR(100),
    component VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    duration_seconds BIGINT,
    retry_count INT DEFAULT 0,
    error_code VARCHAR(100),
    error_message TEXT,
    log_file_path VARCHAR(500),
    log_excerpt TEXT,
    rollback_step_available BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_step_events (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES job_master(id) ON DELETE CASCADE,
    step_id UUID REFERENCES job_steps(id) ON DELETE CASCADE,
    task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    host_id VARCHAR(100),
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS job_id UUID REFERENCES job_master(id) ON DELETE SET NULL;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS job_step_id UUID REFERENCES job_steps(id) ON DELETE SET NULL;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS log_file_path VARCHAR(500);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS current_step_code VARCHAR(100);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS current_step_name VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_job_master_cluster ON job_master(cluster_id);
CREATE INDEX IF NOT EXISTS idx_job_master_status ON job_master(status);
CREATE INDEX IF NOT EXISTS idx_job_steps_job ON job_steps(job_id);
CREATE INDEX IF NOT EXISTS idx_job_steps_task ON job_steps(task_id);
CREATE INDEX IF NOT EXISTS idx_job_steps_job_host_code ON job_steps(job_id, host_id, step_code);
CREATE INDEX IF NOT EXISTS idx_job_step_events_job ON job_step_events(job_id);
CREATE INDEX IF NOT EXISTS idx_tasks_job ON tasks(job_id);
