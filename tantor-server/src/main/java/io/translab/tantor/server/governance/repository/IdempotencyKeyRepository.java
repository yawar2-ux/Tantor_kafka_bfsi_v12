package io.translab.tantor.server.governance.repository;

import io.translab.tantor.server.governance.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
}
