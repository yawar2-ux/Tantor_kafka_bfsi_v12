package io.translab.tantor.server.governance.web;

import io.translab.tantor.server.governance.domain.ReconciliationRecord;
import io.translab.tantor.server.governance.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @GetMapping("/drift")
    @PreAuthorize("hasAuthority('CLUSTER_VIEW')")
    public List<ReconciliationRecord> openDrift() { return reconciliationService.openDrift(); }

    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('RECONCILE_RUN')")
    public List<ReconciliationRecord> scan() { return reconciliationService.detectClusterHostDrift(); }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('RECONCILE_RUN')")
    public ReconciliationRecord resolve(@PathVariable UUID id) { return reconciliationService.resolve(id); }
}
