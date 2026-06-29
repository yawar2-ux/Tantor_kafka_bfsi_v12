package io.translab.tantor.server.secrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default, self-contained secrets backend for non-production / initial deployments.
 * Encrypts values with AES-256-GCM using a master key derived from
 * {@code tantor.secrets.master-key}. The encrypted blob is stored in-memory keyed
 * by an opaque reference id.
 *
 * BFSI NOTE: replace with HashiCorp Vault / CyberArk / cloud KMS for PROD by setting
 * tantor.secrets.provider accordingly. This impl is the fallback when no external
 * provider is configured.
 */
@Component
@ConditionalOnProperty(name = "tantor.secrets.provider", havingValue = "LOCAL_VAULT", matchIfMissing = true)
public class LocalEncryptedVaultSecretsManager implements SecretsManager {

    private static final Logger log = LoggerFactory.getLogger(LocalEncryptedVaultSecretsManager.class);
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final SecretKey key;

    public LocalEncryptedVaultSecretsManager(
            @Value("${tantor.secrets.master-key:CHANGE_ME_LOCAL_VAULT_MASTER_KEY}") String masterKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
            if ("CHANGE_ME_LOCAL_VAULT_MASTER_KEY".equals(masterKey)) {
                log.warn("LocalEncryptedVault is using the DEFAULT master key. " +
                        "Set tantor.secrets.master-key before any non-dev use.");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialise local vault", e);
        }
    }

    @Override public String provider() { return "LOCAL_VAULT"; }

    @Override
    public String store(String secretName, String secretValue) {
        String ref = "local-vault://" + secretName + "/" + java.util.UUID.randomUUID();
        store.put(ref, encrypt(secretValue));
        return ref;
    }

    @Override
    public String resolve(String referenceId) {
        String enc = store.get(referenceId);
        if (enc == null) throw new IllegalArgumentException("Unknown secret reference");
        return decrypt(enc);
    }

    @Override
    public String rotate(String referenceId, String newValue) {
        if (!store.containsKey(referenceId)) throw new IllegalArgumentException("Unknown secret reference");
        store.put(referenceId, encrypt(newValue));
        return referenceId;
    }

    @Override
    public boolean delete(String referenceId) {
        return store.remove(referenceId) != null;
    }

    private String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encrypt failed", e);
        }
    }

    private String decrypt(String b64) {
        try {
            byte[] all = Base64.getDecoder().decode(b64);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decrypt failed", e);
        }
    }
}
