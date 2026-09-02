package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.backup.KeyCipher;

@ApplicationScoped
public class AIEncryptionService {
    private final KeyCipher cipher;

    @Inject
    public AIEncryptionService(@ConfigProperty(name = "geopulse.ai.encryption.key.location") String keyLocation) {
        cipher = KeyCipher.load(keyLocation);
    }

    public String encrypt(String plaintext) { return cipher.encrypt(plaintext); }
    public String decrypt(String encryptedData, String keyId) { return cipher.decrypt(encryptedData, keyId); }
    public String getCurrentKeyId() { return "v1"; }
    public KeyCipher cipher() { return cipher; }
}
