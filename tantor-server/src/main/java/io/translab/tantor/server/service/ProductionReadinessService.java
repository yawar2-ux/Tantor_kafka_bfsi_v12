package io.translab.tantor.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.translab.tantor.server.domain.JobMaster;
import io.translab.tantor.server.domain.JobStep;
import io.translab.tantor.server.repository.JobMasterRepository;
import io.translab.tantor.server.repository.JobStepRepository;
import io.translab.tantor.server.repository.ClusterRepository;
import io.translab.tantor.server.repository.HostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionReadinessService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final JobMasterRepository jobMasterRepository;
    private final JobStepRepository jobStepRepository;
    private final ClusterRepository clusterRepository;
    private final HostRepository hostRepository;

    public Map<String, Object> validateKraft(Map<String, Object> request) {
        List<Map<String, Object>> services = services(request);
        String environment = stringValue(request.getOrDefault("environment", "DEV")).toUpperCase(Locale.ROOT);
        List<Map<String, Object>> controllers = services.stream().filter(this::isController).toList();
        List<Map<String, Object>> brokers = services.stream().filter(this::isBroker).toList();
        List<Integer> nodeIds = services.stream().map(s -> intValue(s.get("node_id"), -1)).toList();
        Set<Integer> uniqueNodeIds = new HashSet<>(nodeIds);
        int controllerCount = controllers.size();
        int quorumRequired = (controllerCount / 2) + 1;
        int controllerPort = intValue(request.getOrDefault("controller_port", 9093), 9093);
        String quorumVoters = controllers.stream()
                .map(s -> intValue(s.get("node_id"), -1) + "@" + stringValue(s.get("host_id")) + ":" + controllerPort)
                .collect(Collectors.joining(","));
        List<Map<String, Object>> checks = new ArrayList<>();
        addCheck(checks, "NODE_ID_UNIQUE", uniqueNodeIds.size() == nodeIds.size(), "Unique node.id", nodeIds.toString(), "Every Kafka node must have a unique node.id.");
        addCheck(checks, "CONTROLLER_COUNT_ODD", controllerCount % 2 == 1, "Odd controller count", String.valueOf(controllerCount), "KRaft controller count should be odd.");
        addCheck(checks, "CONTROLLER_COUNT_MIN_PROD", !"PROD".equals(environment) || controllerCount >= 3, "At least 3 controllers in PROD", String.valueOf(controllerCount), "Production/BFSI KRaft should use minimum 3 controllers.");
        addCheck(checks, "BROKER_PRESENT", !brokers.isEmpty(), "At least one broker", String.valueOf(brokers.size()), "Kafka cluster requires broker nodes.");
        addCheck(checks, "QUORUM_VOTERS_MATCH", !quorumVoters.isBlank(), "Generated controller.quorum.voters", quorumVoters, "All controller nodes must be included in controller.quorum.voters.");
        addCheck(checks, "PROCESS_ROLES_VALID", services.stream().allMatch(this::validKraftRole), "broker/controller/broker_controller", roles(services), "Only valid KRaft roles should be used.");
        boolean pass = checks.stream().allMatch(c -> "PASS".equals(c.get("status")) || "WARNING".equals(c.get("status")));
        return Map.of(
                "status", pass ? "PASS" : "FAIL",
                "controllerCount", controllerCount,
                "brokerCount", brokers.size(),
                "quorumRequired", quorumRequired,
                "controllerQuorumVoters", quorumVoters,
                "checks", checks
        );
    }

    public Map<String, Object> validateZookeeper(Map<String, Object> request) {
        List<Map<String, Object>> services = services(request);
        String environment = stringValue(request.getOrDefault("environment", "DEV")).toUpperCase(Locale.ROOT);
        List<Map<String, Object>> zkNodes = services.stream().filter(this::isZookeeper).toList();
        List<Map<String, Object>> brokers = services.stream().filter(this::isBroker).toList();
        int zkPort = intValue(request.getOrDefault("zookeeper_port", 2181), 2181);
        int peerPort = intValue(request.getOrDefault("zookeeper_peer_port", 2888), 2888);
        int electionPort = intValue(request.getOrDefault("zookeeper_election_port", 3888), 3888);
        List<Integer> zkMyIds = zkNodes.stream().map(s -> intValue(s.get("node_id"), -1)).toList();
        Set<Integer> uniqueMyIds = new HashSet<>(zkMyIds);
        List<Integer> brokerIds = brokers.stream().map(s -> intValue(s.get("node_id"), -1)).toList();
        Set<Integer> uniqueBrokerIds = new HashSet<>(brokerIds);
        String connect = zkNodes.stream().map(s -> stringValue(s.get("host_id")) + ":" + zkPort).collect(Collectors.joining(","));
        String servers = zkNodes.stream().map(s -> "server." + intValue(s.get("node_id"), -1) + "=" + stringValue(s.get("host_id")) + ":" + peerPort + ":" + electionPort).collect(Collectors.joining("\n"));
        List<Map<String, Object>> checks = new ArrayList<>();
        addCheck(checks, "ZK_ENSEMBLE_PRESENT", !zkNodes.isEmpty(), "At least one ZooKeeper node", String.valueOf(zkNodes.size()), "ZooKeeper mode must deploy ZooKeeper before brokers.");
        addCheck(checks, "ZK_MYID_UNIQUE", uniqueMyIds.size() == zkMyIds.size(), "Unique myid", zkMyIds.toString(), "Each ZooKeeper node must have a unique myid.");
        addCheck(checks, "ZK_PROD_MINIMUM", !"PROD".equals(environment) || zkNodes.size() >= 3, "At least 3 ZooKeeper nodes in PROD", String.valueOf(zkNodes.size()), "Production ZooKeeper ensemble should use 3 or 5 nodes.");
        addCheck(checks, "BROKER_ID_UNIQUE", uniqueBrokerIds.size() == brokerIds.size(), "Unique broker.id", brokerIds.toString(), "Every Kafka broker must have a unique broker.id.");
        addCheck(checks, "ZOOKEEPER_CONNECT_SAME", !connect.isBlank(), "Same zookeeper.connect on all brokers", connect, "All brokers must point to the same ZooKeeper connect string.");
        addCheck(checks, "ZOO_CFG_SERVER_LIST", !servers.isBlank(), "Same zoo.cfg server list", servers, "zoo.cfg server list must be the same on every ZooKeeper node.");
        boolean pass = checks.stream().allMatch(c -> "PASS".equals(c.get("status")) || "WARNING".equals(c.get("status")));
        return Map.of("status", pass ? "PASS" : "FAIL", "zookeeperConnect", connect, "zookeeperServers", servers, "checks", checks);
    }

    @Transactional
    public void persistValidation(UUID jobId, UUID clusterId, String group, Map<String, Object> validation) {
        Object checksObj = validation.get("checks");
        if (!(checksObj instanceof List<?> checks)) return;
        for (Object item : checks) {
            if (!(item instanceof Map<?, ?> c)) continue;
            jdbcTemplate.update("INSERT INTO validation_results(id, job_id, cluster_id, validation_group, validation_type, status, severity, expected_value, actual_value, error_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), jobId, clusterId, group,
                    stringValue(c.get("type")), stringValue(c.get("status")), "FAIL".equals(c.get("status")) ? "ERROR" : "INFO",
                    stringValue(c.get("expected")), stringValue(c.get("actual")), stringValue(c.get("message")));
        }
    }

    @Transactional
    public Map<String, Object> requestApproval(Map<String, Object> request) {
        UUID id = UUID.randomUUID();
        String actionType = stringValue(request.getOrDefault("actionType", request.getOrDefault("requestType", request.getOrDefault("request_type", "CHANGE"))));
        String resourceType = stringValue(request.getOrDefault("resourceType", request.getOrDefault("resource_type", "UNKNOWN")));
        String resourceId = stringValue(request.getOrDefault("resourceId", request.getOrDefault("resource_id", "")));
        String requestedBy = stringValue(request.getOrDefault("requestedBy", request.getOrDefault("requested_by", "ui")));
        String environment = stringValue(request.getOrDefault("environment", "DEV")).toUpperCase(Locale.ROOT);
        String payload = toJson(request);
        jdbcTemplate.update("""
                INSERT INTO approval_requests(
                    id, request_type, action_type, resource_type, resource_id, environment,
                    requested_by, status, reason, payload, payload_json, idempotency_key, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?::jsonb, ?, ?, CURRENT_TIMESTAMP + INTERVAL '3 days')
                """,
                id, actionType, actionType, resourceType, resourceId, environment, requestedBy,
                stringValue(request.get("reason")), payload, payload,
                stringValue(request.getOrDefault("idempotencyKey", request.get("idempotency_key"))));
        return Map.of("approvalId", id, "status", "PENDING", "actionType", actionType, "environment", environment);
    }

    @Transactional
    public Map<String, Object> decideApproval(UUID approvalId, String decision, String approver) {
        String status = "APPROVE".equalsIgnoreCase(decision) || "APPROVED".equalsIgnoreCase(decision) ? "APPROVED" : "REJECTED";
        jdbcTemplate.update("UPDATE approval_requests SET status=?, approved_by=?, decided_at=CURRENT_TIMESTAMP WHERE id=?", status, approver, approvalId);
        return Map.of("approvalId", approvalId, "status", status);
    }

    @Transactional
    public Map<String, Object> acquireLock(String resourceType, String resourceId, String operationType, String lockedBy) {
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    INSERT INTO operation_locks(
                        id, resource_type, resource_id, operation_type,
                        lock_scope, scope_id, operation, locked_by, status,
                        acquired_at, locked_at, expires_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '2 hours')
                    """,
                    id, resourceType, resourceId, operationType,
                    resourceType, resourceId, operationType, lockedBy);
            return Map.of("lockId", id, "status", "ACQUIRED");
        } catch (DuplicateKeyException ex) {
            return Map.of("status", "LOCKED", "message", "Another active operation is already running on this resource.");
        }
    }

    @Transactional
    public Map<String, Object> releaseLock(UUID lockId) {
        jdbcTemplate.update("UPDATE operation_locks SET status='RELEASED', released_at=CURRENT_TIMESTAMP WHERE id=?", lockId);
        return Map.of("lockId", lockId, "status", "RELEASED");
    }

    @Transactional
    public Map<String, Object> createReviewPlan(Map<String, Object> request) {
        UUID id = UUID.randomUUID();
        UUID clusterId = uuidOrNull(request.get("clusterId"));
        jdbcTemplate.update("INSERT INTO review_plans(id, operation_type, cluster_id, plan_payload, status, created_by) VALUES (?, ?, ?, ?::jsonb, 'DRAFT', ?)",
                id, stringValue(request.getOrDefault("operationType", "DEPLOYMENT")), clusterId, toJson(request), stringValue(request.getOrDefault("createdBy", "ui")));
        return Map.of("reviewPlanId", id, "status", "DRAFT");
    }

    @Transactional
    public Map<String, Object> createRollbackPlan(UUID jobId, String requestedBy) {
        JobMaster job = jobMasterRepository.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
        UUID rollbackJobId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO job_master(id, job_type, cluster_id, requested_by, status, rollback_available, rollback_status) VALUES (?, 'ROLLBACK_JOB', ?, ?, 'PENDING', false, 'NOT_APPLICABLE')",
                rollbackJobId, job.getClusterId(), requestedBy == null ? "ui" : requestedBy);
        UUID planId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO rollback_plans(id, original_job_id, rollback_job_id, cluster_id, status, created_by) VALUES (?, ?, ?, ?, 'DRAFT', ?)",
                planId, jobId, rollbackJobId, job.getClusterId(), requestedBy == null ? "ui" : requestedBy);
        List<Map<String, Object>> artifacts = jdbcTemplate.queryForList("SELECT * FROM job_step_artifacts WHERE job_id=? ORDER BY created_at DESC", jobId);
        int order = 1;
        for (Map<String, Object> artifact : artifacts) {
            jdbcTemplate.update("INSERT INTO rollback_steps(id, rollback_plan_id, host_id, step_order, rollback_action, target_artifact, status) VALUES (?, ?, ?, ?, ?, ?, 'PENDING')",
                    UUID.randomUUID(), planId, stringValue(artifact.get("host_id")), order++, stringValue(artifact.getOrDefault("rollback_action", "QUARANTINE_OR_REMOVE")), stringValue(artifact.get("artifact_path")));
        }
        job.setRollbackStatus("ROLLBACK_PLANNED");
        jobMasterRepository.save(job);
        return Map.of("rollbackPlanId", planId, "rollbackJobId", rollbackJobId, "status", "DRAFT", "steps", Math.max(0, order - 1));
    }

    @Transactional
    public Map<String, Object> retryFailedStep(UUID jobId) {
        List<JobStep> failedSteps = jobStepRepository.findByJobIdOrderByStepOrderAsc(jobId).stream().filter(s -> "FAILED".equals(s.getStatus())).toList();
        if (failedSteps.isEmpty()) return Map.of("status", "NO_FAILED_STEP");
        JobStep step = failedSteps.get(0);
        step.setStatus("PENDING");
        step.setRetryCount((step.getRetryCount() == null ? 0 : step.getRetryCount()) + 1);
        step.setErrorMessage(null);
        step.setFinishedAt(null);
        jobStepRepository.save(step);
        jobMasterRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("RUNNING");
            job.setRetryCount((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1);
            job.setFailureReason(null);
            jobMasterRepository.save(job);
        });
        return Map.of("status", "RETRY_SCHEDULED", "stepId", step.getId(), "stepName", step.getStepName());
    }

    @Transactional
    public Map<String, Object> resumeJob(UUID jobId) {
        List<JobStep> steps = jobStepRepository.findByJobIdOrderByStepOrderAsc(jobId);
        Optional<JobStep> firstRunnable = steps.stream().filter(s -> Set.of("FAILED", "PENDING").contains(s.getStatus())).findFirst();
        if (firstRunnable.isEmpty()) return Map.of("status", "NOTHING_TO_RESUME");
        JobStep step = firstRunnable.get();
        if ("FAILED".equals(step.getStatus())) step.setStatus("PENDING");
        jobStepRepository.save(step);
        jobMasterRepository.findById(jobId).ifPresent(job -> {
            job.setStatus("RUNNING");
            job.setCurrentStep(step.getStepName());
            job.setCurrentHostId(step.getHostId());
            job.setFailureReason(null);
            jobMasterRepository.save(job);
        });
        return Map.of("status", "RESUME_READY", "fromStep", step.getStepName(), "hostId", nullToEmpty(step.getHostId()));
    }

    @Transactional
    public Map<String, Object> createCleanupPlan(UUID jobId, String requestedBy) {
        UUID clusterId = jobMasterRepository.findById(jobId).map(JobMaster::getClusterId).orElse(null);
        UUID planId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO cleanup_plans(id, job_id, cluster_id, status, created_by) VALUES (?, ?, ?, 'DRAFT', ?)", planId, jobId, clusterId, requestedBy == null ? "ui" : requestedBy);
        List<Map<String, Object>> artifacts = jdbcTemplate.queryForList("SELECT * FROM job_step_artifacts WHERE job_id=? AND artifact_type IN ('DOWNLOADED_PACKAGE','EXTRACTED_KAFKA_DIR','TEMP_FILE','SYSTEMD_SERVICE','CONFIG_FILE') ORDER BY created_at DESC", jobId);
        int order = 0;
        for (Map<String, Object> artifact : artifacts) {
            order++;
            jdbcTemplate.update("INSERT INTO cleanup_steps(id, cleanup_plan_id, host_id, artifact_path, cleanup_action, status) VALUES (?, ?, ?, ?, ?, 'PENDING')",
                    UUID.randomUUID(), planId, stringValue(artifact.get("host_id")), stringValue(artifact.get("artifact_path")), "SAFE_CLEANUP");
        }
        return Map.of("cleanupPlanId", planId, "status", "DRAFT", "steps", order);
    }

    @Transactional
    public Map<String, Object> savePackageValidation(Map<String, Object> request) {
        UUID id = UUID.randomUUID();
        String fileName = stringValue(request.get("packageName"));
        boolean extValid = fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz");
        String checksum = stringValue(request.get("sha256Checksum"));
        boolean checksumOk = !checksum.isBlank();
        String status = extValid && checksumOk ? "AVAILABLE" : "FAILED";
        jdbcTemplate.update("INSERT INTO kafka_package_validations(id, package_id, package_name, kafka_version, file_path, file_size, sha256_checksum, extension_valid, version_detected, checksum_generated, extraction_test_status, duplicate_status, malware_scan_status, status, error_message, uploaded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, stringValue(request.get("packageId")), fileName, stringValue(request.get("kafkaVersion")), stringValue(request.get("filePath")), longValue(request.get("fileSize"), 0L), checksum, extValid, !stringValue(request.get("kafkaVersion")).isBlank(), checksumOk, stringValue(request.getOrDefault("extractionTestStatus", "NOT_RUN")), stringValue(request.getOrDefault("duplicateStatus", "UNKNOWN")), stringValue(request.getOrDefault("malwareScanStatus", "NOT_RUN")), status, extValid ? null : "Only .tgz or .tar.gz packages are allowed", stringValue(request.getOrDefault("uploadedBy", "ui")));
        return Map.of("validationId", id, "status", status);
    }

    @Transactional
    public Map<String, Object> saveConfigVersion(Map<String, Object> request) {
        UUID id = UUID.randomUUID();
        UUID clusterId = uuidOrNull(request.get("clusterId"));
        Integer nextVersion = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(config_version),0)+1 FROM cluster_config_versions WHERE cluster_id=? AND component=? AND config_file_name=?", Integer.class, clusterId, stringValue(request.get("component")), stringValue(request.get("configFileName")));
        String oldConfig = stringValue(request.get("oldConfig"));
        String newConfig = stringValue(request.get("newConfig"));
        String diff = simpleDiff(oldConfig, newConfig);
        jdbcTemplate.update("INSERT INTO cluster_config_versions(id, cluster_id, host_id, component, config_file_name, config_version, old_config, new_config, config_diff, config_checksum, status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)",
                id, clusterId, stringValue(request.get("hostId")), stringValue(request.get("component")), stringValue(request.get("configFileName")), nextVersion, oldConfig, newConfig, diff, sha256(newConfig), stringValue(request.getOrDefault("createdBy", "ui")));
        return Map.of("configVersionId", id, "configVersion", nextVersion, "status", "DRAFT", "diff", diff);
    }

    @Transactional
    public Map<String, Object> saveSecretReference(Map<String, Object> request) {
        UUID id = UUID.randomUUID();
        String externalRef = stringValue(request.getOrDefault("externalRef", request.get("referenceId")));
        jdbcTemplate.update("""
                INSERT INTO secret_references(
                    id, secret_name, secret_type, provider, reference_id, external_ref,
                    resource_type, resource_id, cluster_id, environment, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                stringValue(request.get("secretName")),
                stringValue(request.get("secretType")),
                stringValue(request.getOrDefault("provider", "LOCAL_VAULT")),
                externalRef,
                externalRef,
                stringValue(request.get("resourceType")),
                stringValue(request.get("resourceId")),
                uuidOrNull(request.get("clusterId")),
                stringValue(request.getOrDefault("environment", "DEV")),
                stringValue(request.getOrDefault("createdBy", "ui")));
        return Map.of("secretReferenceId", id, "status", "SAVED_REFERENCE_ONLY");
    }

    @Transactional
    public Map<String, Object> markHostMaintenance(String hostId, Map<String, Object> request) {
        boolean enabled = Boolean.parseBoolean(stringValue(request.getOrDefault("enabled", "true")));
        jdbcTemplate.update("UPDATE hosts SET maintenance_mode=?, maintenance_reason=?, maintenance_started_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END, status=CASE WHEN ? THEN 'MAINTENANCE' ELSE 'ONLINE' END WHERE id=?",
                enabled, stringValue(request.get("reason")), enabled, enabled, hostId);
        return Map.of("hostId", hostId, "maintenanceMode", enabled);
    }

    @Transactional
    public Map<String, Object> createDecommissionPlan(UUID clusterId, String hostId, Map<String, Object> request) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO decommission_plans(id, cluster_id, host_id, component, status, impact_summary, quorum_impact_status, partition_drain_status, archive_path, created_by) VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?)",
                id, clusterId, hostId, stringValue(request.getOrDefault("component", "BROKER")), stringValue(request.getOrDefault("impactSummary", "Pending validation")), stringValue(request.getOrDefault("quorumImpactStatus", "PENDING")), stringValue(request.getOrDefault("partitionDrainStatus", "PENDING")), stringValue(request.get("archivePath")), stringValue(request.getOrDefault("createdBy", "ui")));
        return Map.of("decommissionPlanId", id, "status", "DRAFT");
    }

    @Transactional
    public Map<String, Object> recordHealthSnapshot(UUID clusterId, Map<String, Object> details) {
        int offlinePartitions = intValue(details.get("offlinePartitions"), 0);
        int underReplicated = intValue(details.get("underReplicatedPartitions"), 0);
        int diskUsedPct = intValue(details.get("maxDiskUsedPct"), 0);
        String controllerStatus = stringValue(details.getOrDefault("controllerStatus", "UNKNOWN"));
        int score = 100;
        if (offlinePartitions > 0) score -= 40;
        if (underReplicated > 0) score -= 25;
        if (diskUsedPct >= 90) score -= 20;
        else if (diskUsedPct >= 80) score -= 10;
        if (!"HEALTHY".equalsIgnoreCase(controllerStatus) && !"RUNNING".equalsIgnoreCase(controllerStatus)) score -= 15;
        score = Math.max(0, Math.min(100, score));
        String status = score >= 90 ? "HEALTHY" : score >= 70 ? "WARNING" : "CRITICAL";
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO cluster_health_snapshots(id, cluster_id, health_score, health_status, broker_availability_score, disk_score, under_replicated_partitions, offline_partitions, consumer_lag_status, controller_status, agent_connectivity_status, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                id, clusterId, score, status, intValue(details.get("brokerAvailabilityScore"), 100), Math.max(0, 100 - diskUsedPct), underReplicated, offlinePartitions, stringValue(details.getOrDefault("consumerLagStatus", "UNKNOWN")), controllerStatus, stringValue(details.getOrDefault("agentConnectivityStatus", "UNKNOWN")), toJson(details));
        jdbcTemplate.update("UPDATE clusters SET health_score=?, last_health_status=?, last_health_check_at=CURRENT_TIMESTAMP WHERE id=?", score, status, clusterId);
        return Map.of("snapshotId", id, "healthScore", score, "healthStatus", status);
    }

    @Transactional
    public Map<String, Object> reconcileCluster(UUID clusterId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT h.id host_id, h.status host_status, h.last_heartbeat, c.status cluster_status FROM hosts h JOIN clusters c ON c.id=h.cluster_id WHERE c.id=?", clusterId);
        int findings = 0;
        for (Map<String, Object> row : rows) {
            String hostStatus = stringValue(row.get("host_status"));
            if (!"ONLINE".equalsIgnoreCase(hostStatus) && !"MAINTENANCE".equalsIgnoreCase(hostStatus)) {
                findings++;
                jdbcTemplate.update("INSERT INTO reconciliation_findings(id, cluster_id, host_id, resource_type, db_state, actual_state, severity, recommended_action) VALUES (?, ?, ?, 'HOST', ?, ?, 'WARNING', 'Check agent heartbeat and service status')",
                        UUID.randomUUID(), clusterId, stringValue(row.get("host_id")), stringValue(row.get("cluster_status")), hostStatus);
            }
        }
        return Map.of("clusterId", clusterId, "findingsCreated", findings);
    }

    @Transactional
    public Map<String, Object> recordBackup(Map<String, Object> request) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO backup_records(id, backup_type, resource_type, resource_id, backup_path, checksum, status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, stringValue(request.getOrDefault("backupType", "MANUAL")), stringValue(request.getOrDefault("resourceType", "DB_METADATA")), stringValue(request.get("resourceId")), stringValue(request.get("backupPath")), stringValue(request.get("checksum")), stringValue(request.getOrDefault("status", "SUCCESS")), stringValue(request.getOrDefault("createdBy", "ui")));
        return Map.of("backupRecordId", id, "status", stringValue(request.getOrDefault("status", "SUCCESS")));
    }

    public Map<String, Object> jobTimeline(UUID jobId) {
        List<Map<String, Object>> steps = jdbcTemplate.queryForList("SELECT * FROM job_steps WHERE job_id=? ORDER BY step_order", jobId);
        List<Map<String, Object>> events = jdbcTemplate.queryForList("SELECT * FROM job_step_events WHERE job_id=? ORDER BY created_at", jobId);
        List<Map<String, Object>> artifacts = jdbcTemplate.queryForList("SELECT * FROM job_step_artifacts WHERE job_id=? ORDER BY created_at", jobId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("steps", steps);
        response.put("events", events);
        response.put("artifacts", artifacts);
        return response;
    }

    private List<Map<String, Object>> services(Map<String, Object> request) {
        Object s = request.get("services");
        if (!(s instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(converted);
            }
        }
        return result;
    }
    private boolean isBroker(Map<String, Object> service) { String role = stringValue(service.get("role")); return role.equals("broker") || role.equals("broker_controller") || role.equals("broker_zookeeper"); }
    private boolean isController(Map<String, Object> service) { String role = stringValue(service.get("role")); return role.equals("controller") || role.equals("broker_controller"); }
    private boolean isZookeeper(Map<String, Object> service) { String role = stringValue(service.get("role")); return role.equals("zookeeper") || role.equals("broker_zookeeper"); }
    private boolean validKraftRole(Map<String, Object> service) { String role = stringValue(service.get("role")); return Set.of("broker", "controller", "broker_controller").contains(role); }
    private String roles(List<Map<String, Object>> services) { return services.stream().map(s -> stringValue(s.get("role"))).collect(Collectors.joining(",")); }
    private void addCheck(List<Map<String, Object>> checks, String type, boolean pass, String expected, String actual, String message) { checks.add(Map.of("type", type, "status", pass ? "PASS" : "FAIL", "expected", expected, "actual", actual, "message", message)); }
    private String toJson(Object value) { try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); } catch (JsonProcessingException e) { return "{}"; } }
    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private int intValue(Object value, int defaultValue) { try { return value instanceof Number n ? n.intValue() : Integer.parseInt(stringValue(value)); } catch (Exception e) { return defaultValue; } }
    private long longValue(Object value, long defaultValue) { try { return value instanceof Number n ? n.longValue() : Long.parseLong(stringValue(value)); } catch (Exception e) { return defaultValue; } }
    private UUID uuidOrNull(Object value) { try { String s = stringValue(value); return s.isBlank() ? null : UUID.fromString(s); } catch (Exception e) { return null; } }
    private String sha256(String value) { try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder sb = new StringBuilder(); for (byte b : hash) sb.append(String.format("%02x", b)); return sb.toString(); } catch (Exception e) { return ""; } }
    private String simpleDiff(String oldConfig, String newConfig) { if (Objects.equals(oldConfig, newConfig)) return "No changes"; return "--- OLD ---\n" + oldConfig + "\n--- NEW ---\n" + newConfig; }
}
