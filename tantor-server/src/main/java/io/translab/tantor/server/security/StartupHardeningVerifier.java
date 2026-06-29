package io.translab.tantor.server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Startup guardrails for BFSI deployments.
 *
 * This component makes unsafe defaults visible during development and can block
 * startup in PROD/DR by setting TANTOR_FAIL_ON_DEFAULT_SECRETS=true.
 */
@Component
@Slf4j
public class StartupHardeningVerifier implements ApplicationRunner {

    private final Environment environment;
    private final String jwtSecret;
    private final String agentToken;
    private final String localVaultMasterKey;
    private final boolean failOnDefaultSecrets;

    public StartupHardeningVerifier(Environment environment,
                                    @Value("${tantor.security.jwt.secret:}") String jwtSecret,
                                    @Value("${tantor.agent-auth.token:}") String agentToken,
                                    @Value("${tantor.secrets.master-key:}") String localVaultMasterKey,
                                    @Value("${tantor.security.fail-on-default-secrets:false}") boolean failOnDefaultSecrets) {
        this.environment = environment;
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret;
        this.agentToken = agentToken == null ? "" : agentToken;
        this.localVaultMasterKey = localVaultMasterKey == null ? "" : localVaultMasterKey;
        this.failOnDefaultSecrets = failOnDefaultSecrets;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prodLike = Arrays.stream(environment.getActiveProfiles())
                .map(String::toUpperCase)
                .anyMatch(p -> p.equals("PROD") || p.equals("DR"));

        StringBuilder issues = new StringBuilder();
        checkSecret("tantor.security.jwt.secret / TANTOR_JWT_SECRET", jwtSecret, issues, 40);
        checkSecret("tantor.agent-auth.token / TANTOR_AGENT_TOKEN", agentToken, issues, 24);
        checkSecret("tantor.secrets.master-key / TANTOR_SECRETS_MASTER_KEY", localVaultMasterKey, issues, 32);

        if (!issues.isEmpty()) {
            String message = "Unsafe Tantor production secret configuration detected:\n" + issues;
            if (failOnDefaultSecrets || prodLike) {
                throw new IllegalStateException(message);
            }
            log.warn(message + "\nSet TANTOR_FAIL_ON_DEFAULT_SECRETS=true or use the prod/DR profile to fail fast.");
        }
    }

    private void checkSecret(String name, String value, StringBuilder issues, int minLength) {
        String normalized = value == null ? "" : value.trim();
        String upper = normalized.toUpperCase();
        if (normalized.length() < minLength || upper.contains("CHANGE_ME") || upper.contains("DEFAULT") || upper.contains("LOCAL")) {
            issues.append(" - ").append(name).append(" is missing, too short, or still uses a default marker\n");
        }
    }
}
