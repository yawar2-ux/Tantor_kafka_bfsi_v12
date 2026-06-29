package io.translab.tantor.server.secrets;

/**
 * Interface placeholders for enterprise secret backends used in BFSI deployments.
 * Each is activated by setting {@code tantor.secrets.provider} to the matching value
 * and providing a @Component implementation. These are intentionally TODO stubs so the
 * integration contract is explicit without shipping half-wired SDK calls.
 */
public final class ExternalVaultPlaceholders {
    private ExternalVaultPlaceholders() {}

    /** provider = HASHICORP. Use spring-vault / Vault Java driver; auth via AppRole or k8s. */
    public interface HashiCorpVault extends SecretsManager {
        // TODO: wire to https://<vault-addr>/v1/<kv-mount>/data/<path>
    }

    /** provider = CYBERARK. Use CyberArk Central Credential Provider (CCP) / Conjur. */
    public interface CyberArkVault extends SecretsManager {
        // TODO: wire to CCP AIMWebService or Conjur REST API.
    }

    /** provider = AWS. Use AWS Secrets Manager (SDK v2). referenceId = secret ARN. */
    public interface AwsSecretsManager extends SecretsManager {
        // TODO: software.amazon.awssdk.services.secretsmanager
    }

    /** provider = AZURE. Use Azure Key Vault. referenceId = secret URI. */
    public interface AzureKeyVault extends SecretsManager {
        // TODO: com.azure.security.keyvault.secrets.SecretClient
    }
}
