package org.github.tess1o.geopulse.gps.integrations.owntracks.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.engines.XSalsa20Engine;
import org.bouncycastle.crypto.macs.Poly1305;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class OwnTracksPayloadDecryptionService {

    private static final String TYPE_FIELD = "_type";
    private static final String ENCRYPTED_TYPE = "encrypted";
    private static final String DATA_FIELD = "data";
    private static final int NONCE_BYTES = 24;
    private static final int MAC_BYTES = 16;
    private static final int POLY1305_KEY_BYTES = 32;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AIEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public OwnTracksPayloadDecryptionService(AIEncryptionService encryptionService, ObjectMapper objectMapper) {
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    public Optional<Map<String, Object>> decryptIfNeeded(Map<String, Object> payload, GpsSourceConfigEntity config) {
        if (!isEncryptedPayload(payload)) {
            return Optional.of(payload);
        }

        try {
            String encryptedSecret = config.getPayloadEncryptionSecretEncrypted();
            String keyId = config.getPayloadEncryptionSecretKeyId();
            if (!hasText(encryptedSecret) || !hasText(keyId)) {
                log.warn("Skipping encrypted OwnTracks payload for source {} because no payload encryption secret is configured", config.getId());
                return Optional.empty();
            }

            Object dataValue = payload.get(DATA_FIELD);
            if (!(dataValue instanceof String encryptedData) || encryptedData.isBlank()) {
                log.warn("Skipping encrypted OwnTracks payload for source {} because the encrypted data field is missing", config.getId());
                return Optional.empty();
            }

            String secret = encryptionService.decrypt(encryptedSecret, keyId);
            String decryptedJson = decryptSecretBox(encryptedData, secret);
            return Optional.of(objectMapper.readValue(decryptedJson, MAP_TYPE));
        } catch (Exception e) {
            log.warn("Skipping encrypted OwnTracks payload for source {} because decryption failed: {}", config.getId(), e.getMessage());
            log.debug("OwnTracks payload decryption failure details", e);
            return Optional.empty();
        }
    }

    public boolean isEncryptedPayload(Map<String, Object> payload) {
        return payload != null && ENCRYPTED_TYPE.equals(payload.get(TYPE_FIELD));
    }

    String decryptSecretBox(String base64Data, String secret) {
        byte[] combined = Base64.getDecoder().decode(base64Data);
        if (combined.length < NONCE_BYTES + MAC_BYTES) {
            throw new IllegalArgumentException("Encrypted OwnTracks payload is too short");
        }

        byte[] nonce = Arrays.copyOfRange(combined, 0, NONCE_BYTES);
        byte[] mac = Arrays.copyOfRange(combined, NONCE_BYTES, NONCE_BYTES + MAC_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(combined, NONCE_BYTES + MAC_BYTES, combined.length);
        byte[] key = OwnTracksEncryptionKeyUtil.toSecretBoxKey(secret);

        byte[] poly1305Key = firstSecretBoxBlock(key, nonce);
        byte[] expectedMac = computePoly1305(ciphertext, poly1305Key);
        if (!MessageDigest.isEqual(mac, expectedMac)) {
            throw new IllegalArgumentException("Encrypted OwnTracks payload MAC verification failed");
        }

        byte[] plaintext = cryptSecretBoxPayload(ciphertext, key, nonce);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    byte[] firstSecretBoxBlock(byte[] key, byte[] nonce) {
        XSalsa20Engine engine = new XSalsa20Engine();
        engine.init(true, new ParametersWithIV(new KeyParameter(key), nonce));

        byte[] zeros = new byte[POLY1305_KEY_BYTES];
        byte[] poly1305Key = new byte[POLY1305_KEY_BYTES];
        engine.processBytes(zeros, 0, zeros.length, poly1305Key, 0);
        return poly1305Key;
    }

    byte[] cryptSecretBoxPayload(byte[] input, byte[] key, byte[] nonce) {
        XSalsa20Engine engine = new XSalsa20Engine();
        engine.init(true, new ParametersWithIV(new KeyParameter(key), nonce));

        byte[] discarded = new byte[POLY1305_KEY_BYTES];
        engine.processBytes(discarded, 0, discarded.length, discarded, 0);

        byte[] output = new byte[input.length];
        engine.processBytes(input, 0, input.length, output, 0);
        return output;
    }

    byte[] computePoly1305(byte[] message, byte[] key) {
        Mac mac = new Poly1305();
        mac.init(new KeyParameter(key));
        mac.update(message, 0, message.length);
        byte[] output = new byte[MAC_BYTES];
        mac.doFinal(output, 0);
        return output;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
