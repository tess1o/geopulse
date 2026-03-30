package org.github.tess1o.geopulse.testsupport;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class TestIdsTest {

    @Test
    void uniqueEmailShouldKeepLocalPartWithinRfcLength() {
        String email = TestIds.uniqueEmail("gap-override-selected-location-with-extra-long-prefix");
        String localPart = email.substring(0, email.indexOf('@'));

        assertNotNull(email);
        assertTrue(email.contains("@geopulse.test"));
        assertTrue(localPart.length() <= 64, "Local part must not exceed 64 characters");
    }
}
