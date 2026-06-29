package io.translab.tantor.server.governance.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateSecretRequest {
    private String secretName;
    private String secretType;   // KEYSTORE_PASSWORD/SASL/AGENT_TOKEN/DB/LDAP
    private String secretValue;  // accepted over TLS, stored only in vault, never returned
    private UUID clusterId;
    private String environment;
}
