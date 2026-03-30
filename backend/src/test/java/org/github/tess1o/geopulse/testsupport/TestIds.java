package org.github.tess1o.geopulse.testsupport;

import java.util.Locale;
import java.util.UUID;

public final class TestIds {
    private TestIds() {
    }

    public static String uniqueEmail(String prefix) {
        String normalizedPrefix = prefix == null ? "test" : prefix.toLowerCase(Locale.ROOT);
        normalizedPrefix = normalizedPrefix.replaceAll("[^a-z0-9._-]", "-");
        if (normalizedPrefix.isBlank()) {
            normalizedPrefix = "test";
        }
        normalizedPrefix = normalizedPrefix.replaceAll("^[^a-z0-9]+", "");
        normalizedPrefix = normalizedPrefix.replaceAll("[^a-z0-9]+$", "");
        if (normalizedPrefix.isBlank()) {
            normalizedPrefix = "test";
        }

        String compactUuid = UUID.randomUUID().toString().replace("-", "");
        int maxPrefixLength = Math.max(1, 64 - 1 - compactUuid.length());
        if (normalizedPrefix.length() > maxPrefixLength) {
            normalizedPrefix = normalizedPrefix.substring(0, maxPrefixLength);
        }

        return normalizedPrefix + "-" + compactUuid + "@geopulse.test";
    }

    public static String uniqueValue(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
