# Production Job Engine + Step-Level Tracking Implementation

This update implements production-grade **Point 6: Job Engine** and **Point 7: Step-Level Deployment Tracking** for the Tantor Kafka control plane.

## What changed

### Backend: Job Engine

Added persistent job tracking tables and Java domain/repository/service classes:

- `job_master`
- `job_steps`
- `job_step_events`
- `tasks.job_id`
- `tasks.job_step_id`
- `tasks.current_step_code`
- `tasks.current_step_name`
- `tasks.log_file_path`

New Flyway migration:

- `tantor-server/src/main/resources/db/migration/V9__job_engine_step_tracking.sql`

New backend classes:

- `domain/JobMaster.java`
- `domain/JobStep.java`
- `domain/JobStepEvent.java`
- `repository/JobMasterRepository.java`
- `repository/JobStepRepository.java`
- `repository/JobStepEventRepository.java`
- `service/JobEngineService.java`
- `dto/TaskStepReportDto.java`

### Backend: APIs

Added agent step reporting endpoint:

```http
POST /api/v1/agents/tasks/step
```

Added UI job APIs:

```http
GET /api/v1/ui/clusters/{clusterId}/jobs
GET /api/v1/ui/clusters/{clusterId}/jobs/{jobId}
```

### Backend: Deployment integration

New cluster deployment now creates:

1. One `DEPLOYMENT_JOB` record in `job_master`.
2. Fifteen production deployment steps per selected host in `job_steps`.
3. Agent `INSTALL_KAFKA` tasks linked to the job.

Add-host flow now creates:

1. One `ADD_HOST_JOB` record.
2. Fifteen production deployment steps per added host.
3. Agent tasks linked to that job.

### Agent: Step-level reporting

Go agent now reports deployment progress step by step:

1. `VALIDATE_HOST_PREREQUISITES`
2. `VALIDATE_PACKAGE`
3. `DOWNLOAD_PACKAGE`
4. `VERIFY_CHECKSUM`
5. `EXTRACT_KAFKA`
6. `GENERATE_CONFIG`
7. `BACKUP_OLD_CONFIG`
8. `FORMAT_STORAGE_OR_SETUP_ZK`
9. `CREATE_SYSTEMD_SERVICE`
10. `START_SERVICE`
11. `VALIDATE_PORT`
12. `VALIDATE_ADMIN_CLIENT`
13. `VALIDATE_CLUSTER_HEALTH`
14. `MARK_DB_RUNNING`

The server also records `VALIDATE_AGENT` when the agent picks up a task.

### Agent: Log file reference

The agent writes task logs to:

```text
{agent.paths.log_dir}/tasks/{taskId}.log
```

and sends `log_file_path` to the server. DB stores the path/reference, not only raw logs.

### UI: Deployment logs page

`DeploymentLogs.tsx` now shows:

- Job timeline
- Job status
- Progress percentage
- Current step
- Current host
- Failed step
- Step-level tracking table
- Rollback availability marker
- Task logs and log file path

## Production design

DB is now used for:

- job state
- step state
- host-wise execution state
- failure reason
- progress percentage
- retry/rollback metadata fields
- log reference/path

Full raw logs should still be kept on file system or a centralized logging system.

## Important note

Actual retry/resume/rollback execution APIs are intentionally not implemented in this change because they belong to **Point 8**. However, the new data model includes fields required by Point 8:

- `retry_count`
- `rollback_available`
- `rollback_status`
- `rollback_step_available`
- `log_file_path`
- failed host + failed step tracking
