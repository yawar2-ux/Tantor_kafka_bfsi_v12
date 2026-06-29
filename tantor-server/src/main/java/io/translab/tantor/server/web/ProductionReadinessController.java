package io.translab.tantor.server.web;

import io.translab.tantor.server.service.ProductionReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ui/production")
@RequiredArgsConstructor
public class ProductionReadinessController {
    private final ProductionReadinessService productionReadinessService;

    @PostMapping("/validations/kraft")
    public ResponseEntity<Map<String, Object>> validateKraft(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.validateKraft(request));
    }

    @PostMapping("/validations/zookeeper")
    public ResponseEntity<Map<String, Object>> validateZookeeper(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.validateZookeeper(request));
    }

    @PostMapping("/approvals")
    public ResponseEntity<Map<String, Object>> requestApproval(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.requestApproval(request));
    }

    @PostMapping("/approvals/{approvalId}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable UUID approvalId, @RequestBody(required = false) Map<String, Object> request) {
        String approver = request == null ? "ui" : String.valueOf(request.getOrDefault("approvedBy", "ui"));
        return ResponseEntity.ok(productionReadinessService.decideApproval(approvalId, "APPROVED", approver));
    }

    @PostMapping("/approvals/{approvalId}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable UUID approvalId, @RequestBody(required = false) Map<String, Object> request) {
        String approver = request == null ? "ui" : String.valueOf(request.getOrDefault("approvedBy", "ui"));
        return ResponseEntity.ok(productionReadinessService.decideApproval(approvalId, "REJECTED", approver));
    }

    @PostMapping("/locks/acquire")
    public ResponseEntity<Map<String, Object>> acquireLock(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.acquireLock(
                String.valueOf(request.getOrDefault("resourceType", "CLUSTER")),
                String.valueOf(request.getOrDefault("resourceId", "")),
                String.valueOf(request.getOrDefault("operationType", "UNKNOWN")),
                String.valueOf(request.getOrDefault("lockedBy", "ui"))
        ));
    }

    @PostMapping("/locks/{lockId}/release")
    public ResponseEntity<Map<String, Object>> releaseLock(@PathVariable UUID lockId) {
        return ResponseEntity.ok(productionReadinessService.releaseLock(lockId));
    }

    @PostMapping("/review-plans")
    public ResponseEntity<Map<String, Object>> createReviewPlan(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.createReviewPlan(request));
    }

    @GetMapping("/jobs/{jobId}/timeline")
    public ResponseEntity<?> jobTimeline(@PathVariable UUID jobId) {
        return ResponseEntity.ok(productionReadinessService.jobTimeline(jobId));
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ResponseEntity<Map<String, Object>> retry(@PathVariable UUID jobId) {
        return ResponseEntity.ok(productionReadinessService.retryFailedStep(jobId));
    }

    @PostMapping("/jobs/{jobId}/resume")
    public ResponseEntity<Map<String, Object>> resume(@PathVariable UUID jobId) {
        return ResponseEntity.ok(productionReadinessService.resumeJob(jobId));
    }

    @PostMapping("/jobs/{jobId}/rollback-plan")
    public ResponseEntity<Map<String, Object>> rollbackPlan(@PathVariable UUID jobId, @RequestBody(required = false) Map<String, Object> request) {
        String requestedBy = request == null ? "ui" : String.valueOf(request.getOrDefault("requestedBy", "ui"));
        return ResponseEntity.ok(productionReadinessService.createRollbackPlan(jobId, requestedBy));
    }

    @PostMapping("/jobs/{jobId}/cleanup-plan")
    public ResponseEntity<Map<String, Object>> cleanupPlan(@PathVariable UUID jobId, @RequestBody(required = false) Map<String, Object> request) {
        String requestedBy = request == null ? "ui" : String.valueOf(request.getOrDefault("requestedBy", "ui"));
        return ResponseEntity.ok(productionReadinessService.createCleanupPlan(jobId, requestedBy));
    }

    @PostMapping("/packages/validate")
    public ResponseEntity<Map<String, Object>> validatePackage(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.savePackageValidation(request));
    }

    @PostMapping("/configs/versions")
    public ResponseEntity<Map<String, Object>> createConfigVersion(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.saveConfigVersion(request));
    }

    @PostMapping("/secrets/references")
    public ResponseEntity<Map<String, Object>> saveSecretReference(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.saveSecretReference(request));
    }

    @PostMapping("/hosts/{hostId}/maintenance")
    public ResponseEntity<Map<String, Object>> maintenance(@PathVariable String hostId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.markHostMaintenance(hostId, request));
    }

    @PostMapping("/clusters/{clusterId}/hosts/{hostId}/decommission-plan")
    public ResponseEntity<Map<String, Object>> decommission(@PathVariable UUID clusterId, @PathVariable String hostId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.createDecommissionPlan(clusterId, hostId, request));
    }

    @PostMapping("/clusters/{clusterId}/health")
    public ResponseEntity<Map<String, Object>> health(@PathVariable UUID clusterId, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.recordHealthSnapshot(clusterId, request));
    }

    @PostMapping("/clusters/{clusterId}/reconcile")
    public ResponseEntity<Map<String, Object>> reconcile(@PathVariable UUID clusterId) {
        return ResponseEntity.ok(productionReadinessService.reconcileCluster(clusterId));
    }

    @PostMapping("/backups")
    public ResponseEntity<Map<String, Object>> backup(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(productionReadinessService.recordBackup(request));
    }
}
