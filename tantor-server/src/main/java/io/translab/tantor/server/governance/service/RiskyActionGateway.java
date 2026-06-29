package io.translab.tantor.server.governance.service;

import io.translab.tantor.server.governance.domain.ApprovalRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Single entry point that existing operational flows (deploy, config change, rolling
 * restart, decommission) should call BEFORE doing anything dangerous. It enforces, in order:
 *   1. Idempotency  - same key won't run twice
 *   2. Operation lock - one active op per cluster/host
 *   3. Maker-checker - PROD-like envs need an approval before execution
 *   4. Audit        - the intent is always recorded
 *
 * Returns a decision telling the caller whether to proceed now, or wait for approval.
 */
@Service
@RequiredArgsConstructor
public class RiskyActionGateway {

    private final IdempotencyService idempotencyService;
    private final LockService lockService;
    private final ApprovalService approvalService;
    private final EnvironmentPolicyService environmentPolicyService;
    private final AuditService auditService;

    @Getter
    public static class Decision {
        private final boolean proceed;
        private final boolean awaitingApproval;
        private final UUID existingJobId;
        private final ApprovalRequest approvalRequest;
        private final String message;

        private Decision(boolean proceed, boolean awaitingApproval, UUID existingJobId,
                         ApprovalRequest approvalRequest, String message) {
            this.proceed = proceed;
            this.awaitingApproval = awaitingApproval;
            this.existingJobId = existingJobId;
            this.approvalRequest = approvalRequest;
            this.message = message;
        }
    }

    /**
     * @param lockScope LockService.SCOPE_CLUSTER or SCOPE_HOST
     * @param scopeId   cluster id or host id
     */
    public Decision begin(String actionType, String resourceType, String resourceId,
                          String environment, String payloadJson, String requestedBy,
                          UUID jobId, String idempotencyKey, String lockScope, String scopeId) {

        // 1. Idempotency replay
        var existing = idempotencyService.existingJob(idempotencyKey);
        if (existing.isPresent()) {
            return new Decision(false, false, existing.get(), null,
                    "Duplicate request - returning original job " + existing.get());
        }

        // 2. Operation lock (throws LockHeldException to caller if busy)
        lockService.acquire(lockScope, scopeId, actionType, requestedBy, jobId);

        // 3. Approval gate
        if (environmentPolicyService.requiresApproval(environment)) {
            ApprovalRequest req = approvalService.raise(actionType, resourceType, resourceId,
                    environment, payloadJson, requestedBy, jobId, idempotencyKey);
            return new Decision(false, true, null, req,
                    "Approval required for " + environment + " - request " + req.getId());
        }

        // 4. Auto-approved (e.g. DEV): record intent and proceed
        auditService.record(requestedBy, null, actionType, resourceType, resourceId,
                environment, null, payloadJson, "AUTO_APPROVED", null);
        idempotencyService.register(idempotencyKey, actionType, jobId);
        return new Decision(true, false, jobId, null, "Proceeding without approval (" + environment + ")");
    }

    /** Call when an operation finishes (success or failure) to free the lock. */
    public void complete(String lockScope, String scopeId) {
        lockService.release(lockScope, scopeId);
    }
}
