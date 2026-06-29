package io.translab.tantor.server.governance.repository;

import io.translab.tantor.server.governance.domain.SecretReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SecretReferenceRepository extends JpaRepository<SecretReference, UUID> {
    List<SecretReference> findByClusterId(UUID clusterId);
}
