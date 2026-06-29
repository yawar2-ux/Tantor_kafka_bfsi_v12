package api

// HostRegistration Request for agent to register itself
type HostRegistration struct {
	HostID      string   `json:"host_id"`
	Hostname    string   `json:"hostname"`
	IPAddresses []string `json:"ip_addresses"`
	OSDetails   string   `json:"os_details"`
	AgentVer    string   `json:"agent_version"`
}

// HostHeartbeat Metrics sent periodically
type HostHeartbeat struct {
	HostID      string  `json:"host_id"`
	CPUUsagePct float64 `json:"cpu_usage_pct"`
	MemTotalMB  int64   `json:"mem_total_mb"`
	MemUsedMB   int64   `json:"mem_used_mb"`
	DiskTotalGB int64   `json:"disk_total_gb"`
	DiskUsedGB  int64   `json:"disk_used_gb"`
	JavaVersion string  `json:"java_version"`
}

// Task represents a deployment or management task from the server
type Task struct {
	TaskID      string            `json:"task_id"`
	ClusterID   string            `json:"cluster_id,omitempty"`
	JobID       string            `json:"job_id,omitempty"`
	Command     string            `json:"command"` // e.g. INSTALL_KAFKA, START_SERVICE
	Parameters  map[string]string `json:"parameters"`
	ArtifactURL string            `json:"artifact_url,omitempty"`
	Checksum    string            `json:"checksum,omitempty"`
}

// TaskResult reports the result of a task execution
type TaskResult struct {
	TaskID      string `json:"task_id"`
	HostID      string `json:"host_id"`
	Status      string `json:"status"` // SUCCESS, FAILED
	LogOutput   string `json:"log_output"`
	LogFilePath string `json:"log_file_path,omitempty"`
	ErrorMsg    string `json:"error_msg,omitempty"`
}

// TaskStepReport reports step-level task progress back to the server.
type TaskStepReport struct {
	TaskID      string `json:"task_id"`
	JobID       string `json:"job_id,omitempty"`
	StepID      string `json:"step_id,omitempty"`
	HostID      string `json:"host_id"`
	StepCode    string `json:"step_code"`
	StepName    string `json:"step_name"`
	Component   string `json:"component,omitempty"`
	Status      string `json:"status"`
	LogOutput   string `json:"log_output,omitempty"`
	LogFilePath string `json:"log_file_path,omitempty"`
	ErrorCode   string `json:"error_code,omitempty"`
	ErrorMsg    string `json:"error_msg,omitempty"`
}
