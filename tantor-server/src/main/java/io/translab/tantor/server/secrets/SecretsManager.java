package io.translab.tantor.server.secrets;

/**
 * Abstraction over a secrets backend. The control-plane DB only ever stores a
 * reference id (path/ARN/key) -- never the raw secret. Implementations resolve
 * that reference to a value at runtime.
 *
 * Supported/extensible providers: LOCAL_VAULT (shipped), HASHICORP, CYBERARK,
 * AWS, AZURE (interface placeholders for BFSI integrations).
 */
public interface SecretsManager {

    /** Stable provider id, e.g. LOCAL_VAULT / HASHICORP / CYBERARK / AWS / AZURE. */
    String provider();

    /** Persist a secret value and return the opaque reference id stored in DB. */
    String store(String secretName, String secretValue);

    /** Resolve a previously stored reference id back to the secret value. */
    String resolve(String referenceId);

    /** Rotate the secret behind a reference, returning the (possibly new) reference id. */
    String rotate(String referenceId, String newValue);

    /** Remove the secret. Returns true if it existed. */
    boolean delete(String referenceId);
}
