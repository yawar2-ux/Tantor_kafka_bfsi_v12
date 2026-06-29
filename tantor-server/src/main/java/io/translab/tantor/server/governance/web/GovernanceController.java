package io.translab.tantor.server.governance.web;

import io.translab.tantor.server.governance.domain.EnvironmentPolicy;
import io.translab.tantor.server.governance.service.ApprovalService;
import io.translab.tantor.server.governance.service.EnvironmentPolicyService;
import io.translab.tantor.server.governance.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only BFSI compliance / governance posture for the dashboard. */
@RestController
@RequestMapping("/api/v1/governance")
@RequiredArgsConstructor
public class GovernanceController {

    private final EnvironmentPolicyService environmentPolicyService;
    private final ApprovalService approvalService;
    private final ReconciliationService reconciliationService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/environment-policies")
    @PreAuthorize("isAuthenticated()")
    public List<EnvironmentPolicy> environmentPolicies() {
        return environmentPolicyService.all();
    }

    @GetMapping("/compliance")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> compliance() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("makerChecker", Map.of(
                "enabled", true,
                "pendingApprovals", approvalService.pending().size(),
                "segregationOfDuties", "Requester cannot approve own request"));
        out.put("immutableAudit", Map.of(
                "enabled", true,
                "enforcedBy", "DB trigger (no UPDATE/DELETE) + restricted repository"));
        out.put("secretsManagement", Map.of(
                "rawSecretsInDb", false,
                "providersSupported", List.of("LOCAL_VAULT", "HASHICORP", "CYBERARK", "AWS", "AZURE")));
        out.put("rbac", Map.of(
                "roles", jdbcTemplate.queryForList("SELECT name FROM roles ORDER BY name", String.class),
                "permissionCount", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM permissions", Integer.class)));
        out.put("driftDetection", Map.of(
                "openDrift", reconciliationService.openDrift().size()));
        out.put("regulatoryMapping", List.of(
                "RBI Master Direction on IT Governance",
                "RBI FREE-AI guidance",
                "DPDP Act 2023 - data localisation & access control",
                "BCBS 239 - risk data aggregation lineage",
                "PCI-DSS - secrets & access segregation"));
        return out;
    }
}
