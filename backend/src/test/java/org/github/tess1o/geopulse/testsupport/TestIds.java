package org.github.tess1o.geopulse.testsupport;

import java.util.UUID;

public final class TestIds {
    private TestIds() {
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@geopulse.test";
    }

    public static String uniqueValue(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
