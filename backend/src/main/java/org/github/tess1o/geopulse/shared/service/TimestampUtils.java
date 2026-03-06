package org.github.tess1o.geopulse.shared.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Utility class for handling timestamp conversions from various types.
 * Particularly useful for Hibernate query results that may return different timestamp types.
 */
public final class TimestampUtils {

    private TimestampUtils() {
        // Utility class
    }

    /**
     * Safely convert various timestamp types to Instant.
     * Handles common cases from Hibernate query results.
     * 
     * @param date Object that might be a timestamp in various formats
     * @return Instant representation or null if conversion fails
     */
    public static Instant getInstantSafe(Object date) {
        if (date == null) return null;
        if (date instanceof Instant) return (Instant) date;
        if (date instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) date).toInstant();
        }
        if (date instanceof OffsetDateTime) return ((OffsetDateTime) date).toInstant();
        if (date instanceof ZonedDateTime) return ((ZonedDateTime) date).toInstant();
        if (date instanceof java.util.Date) return ((java.util.Date) date).toInstant();
        if (date instanceof Long) return Instant.ofEpochMilli((Long) date);
        if (date instanceof LocalDateTime) return ((LocalDateTime) date).toInstant(ZoneOffset.UTC);
        if (date instanceof String) {
            try {
                return Instant.parse((String) date);
            } catch (Exception e) {
                // Return null for unparseable strings
                return null;
            }
        }
        return null;
    }
}
