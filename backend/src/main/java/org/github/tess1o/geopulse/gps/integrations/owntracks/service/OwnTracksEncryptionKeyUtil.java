package org.github.tess1o.geopulse.gps.integrations.owntracks.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class OwnTracksEncryptionKeyUtil {

    static final int KEY_BYTES = 32;

    private OwnTracksEncryptionKeyUtil() {
    }

    public static byte[] toSecretBoxKey(String secret) {
        if (secret == null) {
            throw new IllegalArgumentException("OwnTracks encryption secret is required");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length > KEY_BYTES) {
            throw new IllegalArgumentException("OwnTracks encryption secret must be 32 UTF-8 bytes or fewer");
        }

        return Arrays.copyOf(secretBytes, KEY_BYTES);
    }
}
