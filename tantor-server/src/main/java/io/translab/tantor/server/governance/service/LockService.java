package io.translab.tantor.server.governance.service;

import io.translab.tantor.server.governance.domain.OperationLock;
import io.translab.tantor.server.governance.repository.OperationLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Cluster/host operation locking. Guarantees one active operation per scope.
 * Relies on the UNIQUE (lock_scope, scope_id) constraint for correctness under
 * concurrency -- the DB, not the JVM, is the source of truth.
 */
@Service
@RequiredArgsConstructor
public class LockService {

    public static final String SCOPE_CLUSTER = "CLUSTER";
    public static final String SCOPE_HOST = "HOST";

    private final OperationLockRepository repository;

    public static class LockHeldException extends RuntimeException {
        public LockHeldException(String msg) { super(msg); }
    }

    @Transactional
    public OperationLock acquire(String scope, String scopeId, String operation, String lockedBy, UUID jobId) {
        repository.findByLockScopeAndScopeId(scope, scopeId).ifPresent(existing -> {
            throw new LockHeldException("Operation '" + existing.getOperation()
                    + "' already in progress on " + scope + " " + scopeId);
        });
        OperationLock lock = new OperationLock();
        lock.setLockScope(scope);
        lock.setScopeId(scopeId);
        lock.setOperation(operation);
        lock.setLockedBy(lockedBy);
        lock.setJobId(jobId);
        lock.setExpiresAt(OffsetDateTime.now().plusHours(6));
        try {
            return repository.save(lock);
        } catch (DataIntegrityViolationException e) {
            throw new LockHeldException("Concurrent operation detected on " + scope + " " + scopeId);
        }
    }

    @Transactional
    public void release(String scope, String scopeId) {
        repository.findByLockScopeAndScopeId(scope, scopeId).ifPresent(repository::delete);
    }

    @Transactional
    public void releaseByJob(UUID jobId) {
        repository.findByJobId(jobId).forEach(repository::delete);
    }

    public Optional<OperationLock> current(String scope, String scopeId) {
        return repository.findByLockScopeAndScopeId(scope, scopeId);
    }
}
