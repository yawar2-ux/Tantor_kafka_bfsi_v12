package io.translab.tantor.server.governance.service;

import io.translab.tantor.server.governance.domain.SecretReference;
import io.translab.tantor.server.governance.repository.SecretReferenceRepository;
import io.translab.tantor.server.secrets.SecretsManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Stores a secret VALUE in the active {@link SecretsManager} backend and persists only
 * the returned reference id in the DB. Reads never expose the raw value through list APIs.
 */
@Service
@RequiredArgsConstructor
public class SecretService {

    private final SecretReferenceRepository repository;
    private final SecretsManager secretsManager;
    private final AuditService auditService;

    @Transactional
    public SecretReference create(String secretName, String secretType, String secretValue,
                                  UUID clusterId, String environment, String createdBy) {
        String referenceId = secretsManager.store(secretName, secretValue);
        SecretReference ref = new SecretReference();
        ref.setSecretName(secretName);
        ref.setSecretType(secretType);
        ref.setProvider(secretsManager.provider());
        ref.setReferenceId(referenceId);
        ref.setClusterId(clusterId);
        ref.setEnvironment(environment);
        ref.setCreatedBy(createdBy);
        SecretReference saved = repository.save(ref);
        auditService.record(createdBy, null, "SECRET_CREATED", "SECRET", secretName,
                environment, null, "provider=" + secretsManager.provider(), "SUCCESS", null);
        return saved;
    }

    @Transactional
    public SecretReference rotate(UUID id, String newValue, String actor) {
        SecretReference ref = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Secret reference not found"));
        secretsManager.rotate(ref.getReferenceId(), newValue);
        ref.setRotatedAt(OffsetDateTime.now());
        SecretReference saved = repository.save(ref);
        auditService.record(actor, null, "SECRET_ROTATED", "SECRET", ref.getSecretName(),
                ref.getEnvironment(), null, null, "SUCCESS", null);
        return saved;
    }

    /** Internal use only (deployers). Never exposed through a controller. */
    public String resolveValue(UUID id) {
        SecretReference ref = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Secret reference not found"));
        return secretsManager.resolve(ref.getReferenceId());
    }

    public List<SecretReference> list() { return repository.findAll(); }
    public List<SecretReference> listForCluster(UUID clusterId) { return repository.findByClusterId(clusterId); }
}
