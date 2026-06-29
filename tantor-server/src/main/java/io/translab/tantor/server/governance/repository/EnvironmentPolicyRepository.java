package io.translab.tantor.server.governance.repository;

import io.translab.tantor.server.governance.domain.EnvironmentPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentPolicyRepository extends JpaRepository<EnvironmentPolicy, String> {
}
