package io.translab.tantor.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.translab.tantor.server.domain.JobMaster;
import io.translab.tantor.server.domain.JobStep;
import io.translab.tantor.server.domain.JobStepEvent;
import io.translab.tantor.server.domain.Task;
import io.translab.tantor.server.dto.TaskStepReportDto;
import io.translab.tantor.server.repository.JobMasterRepository;
import io.translab.tantor.server.repository.JobStepEventRepository;
import io.translab.tantor.server.repository.JobStepRepository;
import io.translab.tantor.server.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobEngineService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PARTIAL = "PARTIAL_SUCCESS";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final JobMasterRepository jobMasterRepository;
    private final JobStepRepository jobStepRepository;
    private final JobStepEventRepository jobStepEventRepository;
    private final TaskRepository taskRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value
    public static class StepTemplate {
        String code;
        String name;
        String component;
        boolean rollbackAvailable;
    }

    public List<StepTemplate> kafkaInstallStepTemplates() {
        return List.of(
                new StepTemplate("VALIDATE_AGENT", "Validate agent", "agent", false),
                new StepTemplate("VALIDATE_HOST_PREREQUISITES", "Validate host prerequisites", "host", false),
                new StepTemplate("VALIDATE_PACKAGE", "Validate package metadata", "package", false),
                new StepTemplate("DOWNLOAD_PACKAGE", "Download Kafka package to agent", "package", true),
                new StepTemplate("VERIFY_CHECKSUM", "Verify package checksum", "package", false),
                new StepTemplate("EXTRACT_KAFKA", "Extract Kafka package", "filesystem", true),
                new StepTemplate("GENERATE_CONFIG", "Generate Kafka configuration", "config", true),
                new StepTemplate("BACKUP_OLD_CONFIG", "Backup old config if present", "config", true),
                new StepTemplate("FORMAT_STORAGE_OR_SETUP_ZK", "Format KRaft storage / setup ZooKeeper", "storage", true),
                new StepTemplate("CREATE_SYSTEMD_SERVICE", "Create systemd service", "systemd", true),
                new StepTemplate("START_SERVICE", "Start Kafka service", "service", true),
                new StepTemplate("VALIDATE_PORT", "Validate service ports", "network", false),
                new StepTemplate("VALIDATE_ADMIN_CLIENT", "Validate Kafka AdminClient connection", "kafka", false),
                new StepTemplate("VALIDATE_CLUSTER_HEALTH", "Validate cluster health", "kafka", false),
                new StepTemplate("MARK_DB_RUNNING", "Mark DB state running", "database", false)
        );
    }

    @Transactional
    public JobMaster createJob(String jobType, UUID clusterId, String requestedBy) {
        JobMaster job = new JobMaster();
        job.setJobType(jobType);
        job.setClusterId(clusterId);
        job.setRequestedBy(requestedBy == null || requestedBy.isBlank() ? "system" : requestedBy);
        job.setStatus(STATUS_PENDING);
        job.setRollbackAvailable(true);
        job.setRollbackStatus("NOT_STARTED");
        return jobMasterRepository.save(job);
    }

    @Transactional
    public List<JobStep> createHostSteps(JobMaster job, String hostId, String component, int baseOrder, List<StepTemplate> templates) {
        List<JobStep> created = new ArrayList<>();
        int index = 0;
        for (StepTemplate template : templates) {
            JobStep step = new JobStep();
            step.setJobId(job.getId());
            step.setHostId(hostId);
            step.setComponent(component == null || component.isBlank() ? template.getComponent() : component);
            step.setStepOrder(baseOrder + index);
            step.setStepCode(template.getCode());
            step.setStepName(template.getName());
            step.setStatus(STATUS_PENDING);
            step.setRollbackStepAvailable(template.isRollbackAvailable());
            created.add(jobStepRepository.save(step));
            index++;
        }
        refreshJobProgress(job.getId());
        return created;
    }

    @Transactional
    public void linkTaskToJob(Task task, UUID jobId) {
        if (task == null || jobId == null) {
            return;
        }
        task.setJobId(jobId);
        taskRepository.save(task);
        jobStepRepository.findByJobIdAndHostIdOrderByStepOrderAsc(jobId, task.getHostId()).forEach(step -> {
            if (step.getTaskId() == null) {
                step.setTaskId(task.getId());
                jobStepRepository.save(step);
            }
        });
        trackDefaultRollbackArtifacts(task);
    }

    @SuppressWarnings("unchecked")
    private void trackDefaultRollbackArtifacts(Task task) {
        if (task.getJobId() == null || task.getParameters() == null || !"INSTALL_KAFKA".equals(task.getCommand())) {
            return;
        }
        try {
            java.util.Map<String, Object> params = objectMapper.readValue(task.getParameters(), java.util.Map.class);
            String installDir = value(params.getOrDefault("kafka_install_dir", "/opt"));
            String dataDir = value(params.getOrDefault("kafka_data_dir", params.getOrDefault("log_dirs", "/data/kafka")));
            String configFile = value(params.get("config_file"));
            String serviceName = value(params.getOrDefault("service_name", params.getOrDefault("systemd_service", "kafka")));
            insertArtifact(task, "DOWNLOADED_PACKAGE", "/var/lib/tantor-agent/artifacts/kafka_" + task.getId() + ".tgz", "DELETE_TEMP_FILE");
            insertArtifact(task, "EXTRACTED_KAFKA_DIR", installDir, "QUARANTINE_DIRECTORY");
            if (!configFile.isBlank()) insertArtifact(task, "CONFIG_FILE", configFile, "MOVE_TO_FAILED_BACKUP");
            if (!serviceName.isBlank()) insertArtifact(task, "SYSTEMD_SERVICE", serviceName, "DISABLE_AND_REMOVE_SERVICE");
            if (!dataDir.isBlank()) insertArtifact(task, "DATA_DIR", dataDir, "QUARANTINE_WITH_APPROVAL");
        } catch (Exception e) {
            log.warn("Failed to register rollback artifacts for task {}", task.getId(), e);
        }
    }

    private void insertArtifact(Task task, String artifactType, String artifactPath, String rollbackAction) {
        jdbcTemplate.update("INSERT INTO job_step_artifacts(id, job_id, task_id, host_id, artifact_type, artifact_path, rollback_action) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), task.getJobId(), task.getId(), task.getHostId(), artifactType, artifactPath, rollbackAction);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Transactional
    public void markTaskDispatched(Task task) {
        if (task == null || task.getJobId() == null) {
            return;
        }
        jobMasterRepository.findById(task.getJobId()).ifPresent(job -> {
            job.setStatus(STATUS_RUNNING);
            if (job.getStartedAt() == null) {
                job.setStartedAt(OffsetDateTime.now());
            }
            job.setCurrentHostId(task.getHostId());
            jobMasterRepository.save(job);
        });
        reportSyntheticStep(task, "VALIDATE_AGENT", "Validate agent", STATUS_SUCCESS, "Agent picked up task and is online.", null);
    }

    @Transactional
    public void processStepReport(TaskStepReportDto dto) {
        Optional<JobStep> stepOpt = resolveStep(dto);
        if (stepOpt.isEmpty()) {
            log.warn("Step report ignored because no matching step was found. taskId={}, jobId={}, hostId={}, stepCode={}", dto.getTaskId(), dto.getJobId(), dto.getHostId(), dto.getStepCode());
            return;
        }

        JobStep step = stepOpt.get();
        String status = normalizeStatus(dto.getStatus());
        OffsetDateTime now = OffsetDateTime.now();
        if (STATUS_RUNNING.equals(status) && step.getStartedAt() == null) {
            step.setStartedAt(now);
        }
        if (STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status) || STATUS_CANCELLED.equals(status)) {
            if (step.getStartedAt() == null) {
                step.setStartedAt(now);
            }
            step.setFinishedAt(now);
            step.setDurationSeconds(Duration.between(step.getStartedAt(), now).getSeconds());
        }
        step.setStatus(status);
        if (dto.getStepName() != null && !dto.getStepName().isBlank()) {
            step.setStepName(dto.getStepName());
        }
        if (dto.getComponent() != null && !dto.getComponent().isBlank()) {
            step.setComponent(dto.getComponent());
        }
        step.setErrorCode(dto.getErrorCode());
        step.setErrorMessage(dto.getErrorMsg());
        step.setLogFilePath(dto.getLogFilePath());
        step.setLogExcerpt(trim(dto.getLogOutput(), 8000));
        jobStepRepository.save(step);

        if (dto.getTaskId() != null) {
            try {
                taskRepository.findById(UUID.fromString(dto.getTaskId())).ifPresent(task -> {
                    task.setCurrentStepCode(step.getStepCode());
                    task.setCurrentStepName(step.getStepName());
                    task.setLogFilePath(dto.getLogFilePath());
                    taskRepository.save(task);
                });
            } catch (IllegalArgumentException ignored) {
            }
        }

        saveEvent(step, dto, status);
        refreshJobProgress(step.getJobId());
    }

    @Transactional
    public void processTaskFinalResult(Task task) {
        if (task == null || task.getJobId() == null) {
            return;
        }
        if (STATUS_SUCCESS.equals(task.getStatus())) {
            reportSyntheticStep(task, "VALIDATE_CLUSTER_HEALTH", "Validate cluster health", STATUS_SUCCESS, task.getLogOutput(), task.getLogFilePath());
            reportSyntheticStep(task, "MARK_DB_RUNNING", "Mark DB state running", STATUS_SUCCESS, "Task completed successfully and DB state can be finalized.", task.getLogFilePath());
        } else if (STATUS_FAILED.equals(task.getStatus())) {
            JobStep failed = findCurrentOrFirstRunnableStep(task);
            failed.setStatus(STATUS_FAILED);
            failed.setErrorMessage(task.getErrorMsg());
            failed.setLogExcerpt(trim(task.getLogOutput(), 8000));
            failed.setLogFilePath(task.getLogFilePath());
            failed.setFinishedAt(OffsetDateTime.now());
            if (failed.getStartedAt() == null) {
                failed.setStartedAt(OffsetDateTime.now());
            }
            failed.setDurationSeconds(Duration.between(failed.getStartedAt(), failed.getFinishedAt()).getSeconds());
            jobStepRepository.save(failed);
        }
        refreshJobProgress(task.getJobId());
    }

    private void reportSyntheticStep(Task task, String stepCode, String stepName, String status, String message, String logPath) {
        TaskStepReportDto dto = new TaskStepReportDto();
        dto.setTaskId(task.getId().toString());
        dto.setJobId(task.getJobId().toString());
        dto.setHostId(task.getHostId());
        dto.setStepCode(stepCode);
        dto.setStepName(stepName);
        dto.setStatus(status);
        dto.setLogOutput(message);
        dto.setLogFilePath(logPath);
        processStepReport(dto);
    }

    private Optional<JobStep> resolveStep(TaskStepReportDto dto) {
        if (dto.getStepId() != null && !dto.getStepId().isBlank()) {
            try {
                return jobStepRepository.findById(UUID.fromString(dto.getStepId()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        UUID taskId = null;
        if (dto.getTaskId() != null && !dto.getTaskId().isBlank()) {
            try {
                taskId = UUID.fromString(dto.getTaskId());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (taskId != null && dto.getStepCode() != null) {
            Optional<JobStep> byTask = jobStepRepository.findFirstByTaskIdAndStepCodeOrderByStepOrderAsc(taskId, dto.getStepCode());
            if (byTask.isPresent()) return byTask;
        }
        if (dto.getJobId() != null && dto.getHostId() != null && dto.getStepCode() != null) {
            try {
                return jobStepRepository.findFirstByJobIdAndHostIdAndStepCodeOrderByStepOrderAsc(UUID.fromString(dto.getJobId()), dto.getHostId(), dto.getStepCode());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    private JobStep findCurrentOrFirstRunnableStep(Task task) {
        if (task.getCurrentStepCode() != null) {
            Optional<JobStep> step = jobStepRepository.findFirstByTaskIdAndStepCodeOrderByStepOrderAsc(task.getId(), task.getCurrentStepCode());
            if (step.isPresent()) return step.get();
        }
        return jobStepRepository.findByJobIdAndHostIdOrderByStepOrderAsc(task.getJobId(), task.getHostId()).stream()
                .filter(step -> STATUS_RUNNING.equals(step.getStatus()) || STATUS_PENDING.equals(step.getStatus()))
                .min(Comparator.comparing(JobStep::getStepOrder))
                .orElseGet(() -> jobStepRepository.findByJobIdAndHostIdOrderByStepOrderAsc(task.getJobId(), task.getHostId()).get(0));
    }

    private void saveEvent(JobStep step, TaskStepReportDto dto, String status) {
        JobStepEvent event = new JobStepEvent();
        event.setJobId(step.getJobId());
        event.setStepId(step.getId());
        event.setTaskId(step.getTaskId());
        event.setHostId(step.getHostId());
        event.setEventType("STEP_STATUS");
        event.setStatus(status);
        event.setMessage(trim(dto.getErrorMsg() != null ? dto.getErrorMsg() : dto.getLogOutput(), 4000));
        jobStepEventRepository.save(event);
    }

    @Transactional
    public void refreshJobProgress(UUID jobId) {
        List<JobStep> steps = jobStepRepository.findByJobIdOrderByStepOrderAsc(jobId);
        jobMasterRepository.findById(jobId).ifPresent(job -> {
            int total = steps.size();
            int completed = (int) steps.stream().filter(s -> STATUS_SUCCESS.equals(s.getStatus())).count();
            int failed = (int) steps.stream().filter(s -> STATUS_FAILED.equals(s.getStatus())).count();
            job.setTotalSteps(total);
            job.setCompletedSteps(completed);
            job.setFailedSteps(failed);
            job.setProgressPercentage(total == 0 ? 0 : (int) Math.round((completed * 100.0) / total));
            steps.stream().filter(s -> STATUS_RUNNING.equals(s.getStatus())).findFirst()
                    .or(() -> steps.stream().filter(s -> STATUS_PENDING.equals(s.getStatus())).findFirst())
                    .ifPresent(step -> {
                        job.setCurrentStep(step.getStepName());
                        job.setCurrentHostId(step.getHostId());
                    });
            if (failed > 0) {
                job.setStatus(STATUS_FAILED);
                steps.stream().filter(s -> STATUS_FAILED.equals(s.getStatus())).findFirst().ifPresent(step -> job.setFailureReason(step.getStepName() + " failed on host " + step.getHostId() + ": " + nullToEmpty(step.getErrorMessage())));
                job.setFinishedAt(OffsetDateTime.now());
            } else if (total > 0 && completed == total) {
                job.setStatus(STATUS_SUCCESS);
                job.setProgressPercentage(100);
                if (job.getFinishedAt() == null) job.setFinishedAt(OffsetDateTime.now());
            } else if (steps.stream().anyMatch(s -> STATUS_RUNNING.equals(s.getStatus()) || STATUS_SUCCESS.equals(s.getStatus()))) {
                job.setStatus(STATUS_RUNNING);
                if (job.getStartedAt() == null) job.setStartedAt(OffsetDateTime.now());
            }
            jobMasterRepository.save(job);
        });
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return STATUS_RUNNING;
        String normalized = status.trim().toUpperCase();
        if ("IN_PROGRESS".equals(normalized)) return STATUS_RUNNING;
        return normalized;
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(value.length() - max);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
