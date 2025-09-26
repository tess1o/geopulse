package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class AIEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // GCM recommended IV length
    private static final int TAG_LENGTH = 16; // GCM tag length

    private final SecretKeySpec secretKey;
    private final String currentKeyId;

    @Inject
    public AIEncryptionService(@ConfigProperty(name = "geopulse.ai.encryption.key.location") String keyLocation) {
        try {
            String masterKey = loadEncryptionKey(keyLocation);
            if (masterKey == null || masterKey.isBlank()) {
                throw new IllegalStateException("AI encryption key could not be loaded from: " + keyLocation);
            }
            this.secretKey = new SecretKeySpec(Base64.getDecoder().decode(masterKey), "AES");
            this.currentKeyId = "v1"; // For key rotation support
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize AI encryption service", e);
        }
    }

    private String loadEncryptionKey(String keyLocation) throws IOException {
        try {
            if (keyLocation.startsWith("classpath:")) {
                // Handle classpath resources
                String resourcePath = keyLocation.substring("classpath:".length());
                try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
                    if (inputStream == null) {
                        throw new IOException("Classpath resource not found: " + resourcePath);
                    }
                    return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
            } else {
                // Handle file:// URLs and regular file paths
                Path keyPath = Paths.get(URI.create(keyLocation));
                return Files.readString(keyPath, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            throw new IOException("Failed to load AI encryption key from: " + keyLocation, e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);

            // Generate random IV
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH * 8, iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext for storage
            byte[] encryptedWithIv = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, encryptedWithIv, IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(encryptedWithIv);

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedData, String keyId) {
        // In a real-world scenario with key rotation, you would use the keyId to select the correct key
        if (!"v1".equals(keyId)) {
            throw new RuntimeException("Unsupported key ID: " + keyId);
        }

        try {
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);

            // Extract IV and ciphertext
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[decodedData.length - IV_LENGTH];
            System.arraycopy(decodedData, 0, iv, 0, IV_LENGTH);
            System.arraycopy(decodedData, IV_LENGTH, cipherText, 0, cipherText.length);

            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public String getCurrentKeyId() {
        return currentKeyId;
    }
}
