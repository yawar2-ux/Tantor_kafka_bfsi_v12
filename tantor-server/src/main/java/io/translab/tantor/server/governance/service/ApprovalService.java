package io.translab.tantor.server.governance.service;

import io.translab.tantor.server.governance.domain.ApprovalRequest;
import io.translab.tantor.server.governance.repository.ApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Maker-Checker engine. A "maker" raises an approval request for a risky action in an
 * environment whose policy requires approval. A different "checker" approves/rejects.
 * Segregation of duties is enforced: the requester cannot approve their own request.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String EXPIRED = "EXPIRED";

    private final ApprovalRequestRepository repository;
    private final EnvironmentPolicyService environmentPolicyService;
    private final AuditService auditService;

    public static class SelfApprovalException extends RuntimeException {
        public SelfApprovalException(String m) { super(m); }
    }

    /** Returns true if the given environment requires a maker-checker approval. */
    public boolean isApprovalRequired(String environment) {
        return environmentPolicyService.requiresApproval(environment);
    }

    @Transactional
    public ApprovalRequest raise(String actionType, String resourceType, String resourceId,
                                 String environment, String payloadJson, String requestedBy,
                                 UUID jobId, String idempotencyKey) {
        ApprovalRequest req = new ApprovalRequest();
        req.setActionType(actionType);
        req.setResourceType(resourceType);
        req.setResourceId(resourceId);
        req.setEnvironment(environment);
        req.setPayloadJson(payloadJson);
        req.setRequestedBy(requestedBy == null ? "system" : requestedBy);
        req.setJobId(jobId);
        req.setIdempotencyKey(idempotencyKey);
        req.setStatus(PENDING);
        req.setExpiresAt(OffsetDateTime.now().plusDays(3));
        ApprovalRequest saved = repository.save(req);
        auditService.record(requestedBy, null, "APPROVAL_REQUESTED", resourceType, resourceId,
                environment, null, payloadJson, "PENDING", saved.getId());
        return saved;
    }

    @Transactional
    public ApprovalRequest approve(UUID id, String approver, String approverRole) {
        ApprovalRequest req = get(id);
        guardDecidable(req);
        if (req.getRequestedBy().equalsIgnoreCase(approver)) {
            throw new SelfApprovalException("Segregation of duties: requester cannot approve own request");
        }
        req.setStatus(APPROVED);
        req.setApprovedBy(approver);
        req.setDecidedAt(OffsetDateTime.now());
        ApprovalRequest saved = repository.save(req);
        auditService.record(approver, approverRole, "APPROVAL_GRANTED", req.getResourceType(),
                req.getResourceId(), req.getEnvironment(), null, null, "SUCCESS", id);
        return saved;
    }

    @Transactional
    public ApprovalRequest reject(UUID id, String approver, String approverRole, String reason) {
        ApprovalRequest req = get(id);
        guardDecidable(req);
        req.setStatus(REJECTED);
        req.setApprovedBy(approver);
        req.setRejectionReason(reason);
        req.setDecidedAt(OffsetDateTime.now());
        ApprovalRequest saved = repository.save(req);
        auditService.record(approver, approverRole, "APPROVAL_REJECTED", req.getResourceType(),
                req.getResourceId(), req.getEnvironment(), null, reason, "REJECTED", id);
        return saved;
    }

    public List<ApprovalRequest> pending() { return repository.findByStatusOrderByRequestedAtDesc(PENDING); }
    public List<ApprovalRequest> all() { return repository.findAllByOrderByRequestedAtDesc(); }
    public ApprovalRequest get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Approval not found: " + id));
    }

    private void guardDecidable(ApprovalRequest req) {
        if (!PENDING.equals(req.getStatus())) {
            throw new IllegalStateException("Approval already " + req.getStatus());
        }
        if (req.getExpiresAt() != null && req.getExpiresAt().isBefore(OffsetDateTime.now())) {
            req.setStatus(EXPIRED);
            repository.save(req);
            throw new IllegalStateException("Approval request has expired");
        }
    }
}
