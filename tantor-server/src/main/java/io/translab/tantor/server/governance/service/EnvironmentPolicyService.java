package io.translab.tantor.server.governance.service;

import io.translab.tantor.server.governance.domain.EnvironmentPolicy;
import io.translab.tantor.server.governance.repository.EnvironmentPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnvironmentPolicyService {

    private final EnvironmentPolicyRepository repository;

    public List<EnvironmentPolicy> all() { return repository.findAll(); }

    public EnvironmentPolicy forEnvironment(String env) {
        if (env == null || env.isBlank()) return defaultProdLike();
        return repository.findById(env.toUpperCase()).orElseGet(this::defaultProdLike);
    }

    /** PROD requires approval; DEV may skip. */
    public boolean requiresApproval(String env) {
        return forEnvironment(env).isRequiresApproval();
    }

    /** Fail safe: unknown environment is treated as if it needs approval. */
    private EnvironmentPolicy defaultProdLike() {
        EnvironmentPolicy p = new EnvironmentPolicy();
        p.setEnvironment("UNKNOWN");
        p.setRequiresApproval(true);
        p.setMinApprovers(1);
        p.setAuditRetentionDays(2555);
        p.setSeparateCredentials(true);
        p.setDescription("Unknown environment -> production-grade controls applied");
        return p;
    }
}
