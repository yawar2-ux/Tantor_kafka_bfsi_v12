package io.translab.tantor.server.governance.web;

import io.translab.tantor.server.governance.domain.AuditLog;
import io.translab.tantor.server.governance.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public Page<AuditLog> list(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "50") int size,
                               @RequestParam(required = false) String actor,
                               @RequestParam(required = false) String action) {
        return auditService.list(page, size, actor, action);
    }
}
