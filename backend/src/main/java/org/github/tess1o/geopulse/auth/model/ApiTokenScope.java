package org.github.tess1o.geopulse.auth.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a personal API token may do. Scopes are fixed when the token is created. {@link #ALL} is what tokens
 * created before scopes existed have, and it cannot be combined with other scopes.
 */
public enum ApiTokenScope {
    /** Everything the owning user can do, including managing API tokens and admin endpoints. */
    ALL("all"),
    /** Safe (read-only) requests on user endpoints, plus the read-only MCP tools. */
    READ("read"),
    /** Requests that change user data. */
    WRITE("write"),
    /** Sending GPS points with this token. */
    GPS_INGEST("gps:ingest"),
    /** Creating, polling and downloading exports. */
    EXPORT("export"),
    /** Uploading files and running imports that only add data. */
    IMPORT("import");

    private final String value;

    ApiTokenScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<ApiTokenScope> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(scope -> scope.value.equals(normalized)).findFirst();
    }

    /** Parses the stored comma-separated form; unknown entries are ignored. */
    public static Set<ApiTokenScope> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return EnumSet.noneOf(ApiTokenScope.class);
        }
        return Arrays.stream(stored.split(","))
                .map(ApiTokenScope::fromValue)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApiTokenScope.class)));
    }

    public static String format(Collection<ApiTokenScope> scopes) {
        return scopes.stream()
                .sorted()
                .map(ApiTokenScope::value)
                .collect(Collectors.joining(","));
    }
}
