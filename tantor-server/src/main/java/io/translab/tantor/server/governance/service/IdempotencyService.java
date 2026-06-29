package io.translab.tantor.server.governance.service;

import io.translab.tantor.server.governance.domain.IdempotencyKey;
import io.translab.tantor.server.governance.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Ensures a dangerous action submitted twice with the same idempotency key does not
 * create two jobs. First call records the key + resulting job; replays return the
 * original job reference instead of executing again.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    /** @return existing job id if this key was already used, else empty. */
    public Optional<UUID> existingJob(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return repository.findById(key).map(IdempotencyKey::getJobId);
    }

    @Transactional
    public void register(String key, String actionType, UUID jobId) {
        if (key == null || key.isBlank()) return;
        if (repository.existsById(key)) return;
        IdempotencyKey k = new IdempotencyKey();
        k.setIdempotencyKey(key);
        k.setActionType(actionType);
        k.setJobId(jobId);
        k.setExpiresAt(OffsetDateTime.now().plusDays(7));
        repository.save(k);
    }
}
