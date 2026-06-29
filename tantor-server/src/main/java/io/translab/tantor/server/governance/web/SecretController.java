package io.translab.tantor.server.governance.web;

import io.translab.tantor.server.governance.domain.SecretReference;
import io.translab.tantor.server.governance.dto.CreateSecretRequest;
import io.translab.tantor.server.governance.dto.RotateSecretRequest;
import io.translab.tantor.server.governance.service.PrincipalUtil;
import io.translab.tantor.server.governance.service.SecretService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Note: secret VALUES are never returned by any GET. Only reference metadata is listed. */
@RestController
@RequestMapping("/api/v1/secrets")
@RequiredArgsConstructor
public class SecretController {

    private final SecretService secretService;

    @GetMapping
    @PreAuthorize("hasAuthority('SECRET_MANAGE')")
    public List<SecretReference> list() { return secretService.list(); }

    @PostMapping
    @PreAuthorize("hasAuthority('SECRET_MANAGE')")
    public SecretReference create(@RequestBody CreateSecretRequest req) {
        return secretService.create(req.getSecretName(), req.getSecretType(), req.getSecretValue(),
                req.getClusterId(), req.getEnvironment(), PrincipalUtil.currentUser());
    }

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasAuthority('SECRET_MANAGE')")
    public SecretReference rotate(@PathVariable UUID id, @RequestBody RotateSecretRequest req) {
        return secretService.rotate(id, req.getNewValue(), PrincipalUtil.currentUser());
    }
}
