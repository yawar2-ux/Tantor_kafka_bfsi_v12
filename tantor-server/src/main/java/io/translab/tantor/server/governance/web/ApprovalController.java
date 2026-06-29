package io.translab.tantor.server.governance.web;

import io.translab.tantor.server.governance.domain.ApprovalRequest;
import io.translab.tantor.server.governance.dto.ApprovalDecisionRequest;
import io.translab.tantor.server.governance.dto.RaiseApprovalRequest;
import io.translab.tantor.server.governance.service.ApprovalService;
import io.translab.tantor.server.governance.service.PrincipalUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW') or hasAuthority('APPROVAL_DECIDE')")
    public List<ApprovalRequest> list(@RequestParam(defaultValue = "false") boolean pendingOnly) {
        return pendingOnly ? approvalService.pending() : approvalService.all();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AUDIT_VIEW') or hasAuthority('APPROVAL_DECIDE')")
    public ApprovalRequest get(@PathVariable UUID id) {
        return approvalService.get(id);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApprovalRequest raise(@RequestBody RaiseApprovalRequest req) {
        return approvalService.raise(req.getActionType(), req.getResourceType(), req.getResourceId(),
                req.getEnvironment(), req.getPayloadJson(), PrincipalUtil.currentUser(),
                req.getJobId(), req.getIdempotencyKey());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
    public ResponseEntity<?> approve(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(approvalService.approve(id, PrincipalUtil.currentUser(), PrincipalUtil.currentRole()));
        } catch (ApprovalService.SelfApprovalException e) {
            return ResponseEntity.status(403).body(error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('APPROVAL_DECIDE')")
    public ResponseEntity<?> reject(@PathVariable UUID id, @RequestBody ApprovalDecisionRequest body) {
        try {
            return ResponseEntity.ok(approvalService.reject(id, PrincipalUtil.currentUser(),
                    PrincipalUtil.currentRole(), body.getReason()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(error(e.getMessage()));
        }
    }

    private java.util.Map<String, String> error(String m) { return java.util.Map.of("error", m); }
}
