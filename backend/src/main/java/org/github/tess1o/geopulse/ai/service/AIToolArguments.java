package org.github.tess1o.geopulse.ai.service;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Argument parsing shared by the AI tools. Tools take dates as YYYY-MM-DD in the user's timezone; the services
 * behind them take instants.
 */
final class AIToolArguments {

    static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";

    private AIToolArguments() {
    }

    static LocalDate requireDate(String value, String name) {
        if (value == null || value.isBlank()) {
            throw invalid(name + " is required (YYYY-MM-DD)", name);
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw invalid(name + " must use YYYY-MM-DD format", name);
        }
    }

    static LocalDate optionalDate(String value, String name) {
        return value == null || value.isBlank() ? null : requireDate(value, name);
    }

    /** Start of {@code date} in the user's timezone. */
    static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** Last instant of {@code date} in the user's timezone, so an end date is inclusive. */
    static Instant endOfDay(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1);
    }

    static void requireOrdered(LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw invalid("startDate must not be after endDate", "startDate");
        }
    }

    static ZoneId zone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw invalid(name + " is required", name);
        }
        return value.trim();
    }

    /** Upper-cased {@code value} if it is one of {@code allowed}, {@code null} if absent. */
    static String optionalEnum(String value, String name, List<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new AIToolException(INVALID_ARGUMENT, name + " must be one of " + allowed,
                    Map.of("argument", name, "allowed", allowed));
        }
        return normalized;
    }

    static int limit(Integer value, int defaultValue, int max) {
        if (value == null) {
            return defaultValue;
        }
        return Math.min(Math.max(value, 1), max);
    }

    /** Validates an optional search circle: all of latitude, longitude and radius, or none. */
    static void validateCircle(Double latitude, Double longitude, Double radius, String radiusName) {
        boolean any = latitude != null || longitude != null || radius != null;
        boolean all = latitude != null && longitude != null && radius != null;
        if (any && !all) {
            throw invalid("latitude, longitude and " + radiusName + " must be given together", radiusName);
        }
        if (all && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 || radius <= 0)) {
            throw invalid("latitude must be -90..90, longitude -180..180 and " + radiusName + " positive", radiusName);
        }
    }

    /** Waits for an async service result on the tool's worker thread, unwrapping the original failure. */
    static <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof AIToolException toolException) {
                throw toolException;
            }
            if (cause instanceof GeoPulseException geoPulseException) {
                throw AIToolException.from(geoPulseException);
            }
            throw new AIToolException("TOOL_EXECUTION_ERROR", "The request to the connected service failed.", cause);
        }
    }

    static AIToolException invalid(String message, String argument) {
        return new AIToolException(INVALID_ARGUMENT, message, Map.of("argument", argument));
    }
}
