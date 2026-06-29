package io.translab.tantor.server.governance.service;

import io.translab.tantor.server.governance.domain.AuditLog;
import io.translab.tantor.server.governance.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Writes immutable audit records. Every governed action MUST call {@link #record}.
 * Audit rows cannot be updated or deleted (DB trigger + restricted repository).
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    public AuditLog record(String actor, String actorRole, String action, String resourceType,
                           String resourceId, String environment, String oldValue, String newValue,
                           String status, UUID approvalId) {
        AuditLog log = new AuditLog();
        log.setActor(actor == null ? "system" : actor);
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setEnvironment(environment);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setStatus(status);
        log.setApprovalId(approvalId);
        HttpServletRequest req = currentRequest();
        if (req != null) {
            log.setIpAddress(clientIp(req));
            log.setUserAgent(req.getHeader("User-Agent"));
        }
        return repository.save(log);
    }

    /** Convenience overload for simple success events. */
    public AuditLog record(String actor, String action, String resourceType, String resourceId, String status) {
        return record(actor, null, action, resourceType, resourceId, null, null, null, status, null);
    }

    public Page<AuditLog> list(int page, int size, String actor, String action) {
        PageRequest pr = PageRequest.of(page, size);
        if (actor != null && !actor.isBlank()) return repository.findByActorOrderByCreatedAtDesc(actor, pr);
        if (action != null && !action.isBlank()) return repository.findByActionOrderByCreatedAtDesc(action, pr);
        return repository.findAllByOrderByCreatedAtDesc(pr);
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
