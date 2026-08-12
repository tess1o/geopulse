package org.github.tess1o.geopulse.gps.integrations.owntracks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class OwnTracksPayloadDecryptionServiceTest {

    private final AIEncryptionService encryptionService = mock(AIEncryptionService.class);
    private final OwnTracksPayloadDecryptionService service = new OwnTracksPayloadDecryptionService(
            encryptionService,
            new ObjectMapper()
    );

    @Test
    void padsSecretToThirtyTwoBytes() {
        byte[] key = OwnTracksEncryptionKeyUtil.toSecretBoxKey("secret");

        byte[] expected = new byte[32];
        System.arraycopy("secret".getBytes(StandardCharsets.UTF_8), 0, expected, 0, 6);
        assertArrayEquals(expected, key);
    }

    @Test
    void rejectsSecretLongerThanThirtyTwoUtf8Bytes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OwnTracksEncryptionKeyUtil.toSecretBoxKey("123456789012345678901234567890123")
        );
    }

    @Test
    void decryptsEncryptedPayloadEnvelope() {
        String secret = "my-owntracks-secret";
        String plaintext = "{\"_type\":\"location\",\"lat\":50.45,\"lon\":30.52,\"tst\":1786380000,\"topic\":\"owntracks/user/phone\"}";
        byte[] nonce = new byte[24];
        for (int i = 0; i < nonce.length; i++) {
            nonce[i] = (byte) i;
        }

        String encryptedData = encryptSecretBox(plaintext, secret, nonce);
        GpsSourceConfigEntity config = sourceConfig("encrypted-secret", "v1");
        when(encryptionService.decrypt("encrypted-secret", "v1")).thenReturn(secret);

        Optional<Map<String, Object>> decrypted = service.decryptIfNeeded(
                Map.of("_type", "encrypted", "data", encryptedData),
                config
        );

        assertTrue(decrypted.isPresent());
        assertEquals("location", decrypted.get().get("_type"));
        assertEquals(50.45, decrypted.get().get("lat"));
        assertEquals("owntracks/user/phone", decrypted.get().get("topic"));
    }

    @Test
    void returnsPlaintextPayloadUnchanged() {
        Map<String, Object> payload = Map.of("_type", "location", "lat", 50.45);

        Optional<Map<String, Object>> resolved = service.decryptIfNeeded(payload, sourceConfig(null, null));

        assertTrue(resolved.isPresent());
        assertEquals(payload, resolved.get());
    }

    @Test
    void skipsEncryptedPayloadWithoutConfiguredSecret() {
        Optional<Map<String, Object>> resolved = service.decryptIfNeeded(
                Map.of("_type", "encrypted", "data", "abc"),
                sourceConfig(null, null)
        );

        assertFalse(resolved.isPresent());
    }

    @Test
    void skipsPayloadWhenMacVerificationFails() {
        String secret = "correct-secret";
        String encryptedData = encryptSecretBox("{\"_type\":\"location\"}", secret, new byte[24]);
        GpsSourceConfigEntity config = sourceConfig("encrypted-secret", "v1");
        when(encryptionService.decrypt("encrypted-secret", "v1")).thenReturn("wrong-secret");

        Optional<Map<String, Object>> resolved = service.decryptIfNeeded(
                Map.of("_type", "encrypted", "data", encryptedData),
                config
        );

        assertFalse(resolved.isPresent());
    }

    private GpsSourceConfigEntity sourceConfig(String encryptedSecret, String keyId) {
        GpsSourceConfigEntity config = new GpsSourceConfigEntity();
        config.setPayloadEncryptionSecretEncrypted(encryptedSecret);
        config.setPayloadEncryptionSecretKeyId(keyId);
        return config;
    }

    private String encryptSecretBox(String plaintext, String secret, byte[] nonce) {
        byte[] key = OwnTracksEncryptionKeyUtil.toSecretBoxKey(secret);
        byte[] ciphertext = service.cryptSecretBoxPayload(plaintext.getBytes(StandardCharsets.UTF_8), key, nonce);
        byte[] mac = service.computePoly1305(ciphertext, service.firstSecretBoxBlock(key, nonce));

        byte[] combined = new byte[24 + 16 + ciphertext.length];
        System.arraycopy(nonce, 0, combined, 0, 24);
        System.arraycopy(mac, 0, combined, 24, 16);
        System.arraycopy(ciphertext, 0, combined, 40, ciphertext.length);
        return Base64.getEncoder().encodeToString(combined);
    }
}
