package org.github.tess1o.geopulse.sharing.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.shared.security.CryptoUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Short-lived access tokens for share links: {@code base64url(linkId:expiresAt).base64url(hmac)}.
 *
 * <p>The HMAC key is derived from the installation encryption key with a purpose label, so share
 * tokens need no key of their own, stay valid across restarts, and verify on every backend instance.</p>
 */
@ApplicationScoped
public class SharedLinkAccessTokenService {
    private static final byte[] KEY_PURPOSE = "geopulse:shared-link-access-token:v1".getBytes(StandardCharsets.UTF_8);
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] signingKey;

    @Inject
    public SharedLinkAccessTokenService(AIEncryptionService encryptionService) {
        this.signingKey = CryptoUtils.hmacSha256(encryptionService.cipher().exportKey(), KEY_PURPOSE);
    }

    public String create(UUID linkId, long lifespanSeconds) {
        String payload = linkId + ":" + Instant.now().plusSeconds(lifespanSeconds).getEpochSecond();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return CryptoUtils.toUrlSafe(payloadBytes) + "." + CryptoUtils.toUrlSafe(CryptoUtils.hmacSha256(signingKey, payloadBytes));
    }

    /** True when the token was issued by this installation for {@code linkId} and has not expired. */
    public boolean isValid(String token, UUID linkId) {
        if (token == null || linkId == null) {
            return false;
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            return false;
        }
        try {
            byte[] payloadBytes = DECODER.decode(token.substring(0, separator));
            byte[] signature = DECODER.decode(token.substring(separator + 1));
            if (!MessageDigest.isEqual(signature, CryptoUtils.hmacSha256(signingKey, payloadBytes))) {
                return false;
            }
            String[] parts = new String(payloadBytes, StandardCharsets.UTF_8).split(":");
            return parts.length == 2
                    && linkId.toString().equals(parts[0])
                    && Instant.now().getEpochSecond() < Long.parseLong(parts[1]);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
