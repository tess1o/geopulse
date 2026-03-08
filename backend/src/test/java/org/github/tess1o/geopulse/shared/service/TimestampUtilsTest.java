package org.github.tess1o.geopulse.shared.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimestampUtilsTest {

    private final TimeZone originalTimeZone = TimeZone.getDefault();

    @AfterEach
    void restoreDefaultTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    @Tag("unit")
    void getInstantSafeShouldNotShiftSqlTimestampInNonUtcJvm() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"));

        Instant expected = Instant.parse("2026-03-06T17:05:34Z");
        Timestamp timestamp = Timestamp.from(expected);

        Instant actual = TimestampUtils.getInstantSafe(timestamp);

        assertEquals(expected, actual);
    }

    @Test
    @Tag("unit")
    void getInstantSafeShouldTreatLocalDateTimeAsUtc() {
        LocalDateTime localDateTime = LocalDateTime.of(2026, 3, 6, 17, 5, 34);

        Instant actual = TimestampUtils.getInstantSafe(localDateTime);

        assertEquals(Instant.parse("2026-03-06T17:05:34Z"), actual);
    }
}
