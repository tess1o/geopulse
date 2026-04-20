package org.github.tess1o.geopulse.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
@Slf4j
public class VersionStatusService {

    private static final String DEFAULT_GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/tess1o/geopulse/releases/latest";
    private static final String DEFAULT_RELEASES_URL = "https://github.com/tess1o/geopulse/releases";
    private static final String SOURCE_STATUS_FRESH = "fresh";
    private static final String SOURCE_STATUS_STALE = "stale";
    private static final String SOURCE_STATUS_UNAVAILABLE = "unavailable";

    private final Clock clock;
    private final Duration cacheTtl;
    private final ReleaseFetcher releaseFetcher;
    private final String currentVersion;
    private final String defaultReleaseUrl;
    private final Object lock = new Object();

    private volatile CachedRelease cachedRelease;

    @Inject
    public VersionStatusService(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "quarkus.application.version") String currentVersion,
            @ConfigProperty(name = "geopulse.version-check.github-api-url", defaultValue = DEFAULT_GITHUB_LATEST_RELEASE_URL) String githubLatestReleaseUrl,
            @ConfigProperty(name = "geopulse.version-check.release-url", defaultValue = DEFAULT_RELEASES_URL) String defaultReleaseUrl,
            @ConfigProperty(name = "geopulse.version-check.cache-ttl-minutes", defaultValue = "60") long cacheTtlMinutes,
            @ConfigProperty(name = "geopulse.version-check.connect-timeout-seconds", defaultValue = "5") long connectTimeoutSeconds,
            @ConfigProperty(name = "geopulse.version-check.read-timeout-seconds", defaultValue = "8") long readTimeoutSeconds
    ) {
        this(
                currentVersion,
                Clock.systemUTC(),
                Duration.ofMinutes(Math.max(1, cacheTtlMinutes)),
                defaultReleaseUrl,
                new GitHubReleaseHttpFetcher(
                        objectMapper,
                        githubLatestReleaseUrl,
                        Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)),
                        Duration.ofSeconds(Math.max(1, readTimeoutSeconds))
                )
        );
    }

    VersionStatusService(
            String currentVersion,
            Clock clock,
            Duration cacheTtl,
            String defaultReleaseUrl,
            ReleaseFetcher releaseFetcher
    ) {
        this.currentVersion = currentVersion;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
        this.defaultReleaseUrl = sanitizeReleaseUrl(defaultReleaseUrl);
        this.releaseFetcher = releaseFetcher;
    }

    public VersionStatusResponse getVersionStatus() {
        Instant now = clock.instant();
        CachedRelease snapshot = cachedRelease;

        if (isFresh(snapshot, now)) {
            return toResponse(snapshot, SOURCE_STATUS_FRESH);
        }

        synchronized (lock) {
            now = clock.instant();
            snapshot = cachedRelease;

            if (isFresh(snapshot, now)) {
                return toResponse(snapshot, SOURCE_STATUS_FRESH);
            }

            try {
                GitHubReleaseInfo latest = releaseFetcher.fetchLatestRelease();
                CachedRelease refreshed = CachedRelease.from(latest, now, defaultReleaseUrl);
                cachedRelease = refreshed;
                return toResponse(refreshed, SOURCE_STATUS_FRESH);
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (snapshot != null) {
                    log.warn("Failed to refresh latest GitHub release info. Serving stale cached result: {}", exception.getMessage());
                    return toResponse(snapshot, SOURCE_STATUS_STALE);
                }

                log.warn("Failed to load latest GitHub release info and no cache is available: {}", exception.getMessage());
                return new VersionStatusResponse(
                        safeCurrentVersion(),
                        null,
                        false,
                        defaultReleaseUrl,
                        null,
                        SOURCE_STATUS_UNAVAILABLE
                );
            }
        }
    }

    private boolean isFresh(CachedRelease snapshot, Instant now) {
        return snapshot != null && now.isBefore(snapshot.fetchedAt().plus(cacheTtl));
    }

    private VersionStatusResponse toResponse(CachedRelease snapshot, String sourceStatus) {
        String current = safeCurrentVersion();
        boolean updateAvailable = isNewerVersion(snapshot.latestVersion(), current);
        return new VersionStatusResponse(
                current,
                snapshot.latestVersion(),
                updateAvailable,
                snapshot.releaseUrl(),
                snapshot.publishedAt(),
                sourceStatus
        );
    }

    private String safeCurrentVersion() {
        if (currentVersion == null || currentVersion.isBlank()) {
            return "Unknown";
        }
        return currentVersion;
    }

    private boolean isNewerVersion(String latestVersion, String currentVersionValue) {
        Integer comparison = compareVersions(latestVersion, currentVersionValue);
        if (comparison == null) {
            return false;
        }
        return comparison > 0;
    }

    Integer compareVersions(String leftRaw, String rightRaw) {
        String left = normalizeVersion(leftRaw);
        String right = normalizeVersion(rightRaw);
        if (left == null || right == null) {
            log.warn("Malformed version value. latest='{}', current='{}'", leftRaw, rightRaw);
            return null;
        }

        List<Integer> leftParts = parseVersionParts(left);
        List<Integer> rightParts = parseVersionParts(right);
        if (leftParts == null || rightParts == null) {
            log.warn("Malformed semantic version. latest='{}', current='{}'", leftRaw, rightRaw);
            return null;
        }

        int maxLength = Math.max(leftParts.size(), rightParts.size());
        for (int index = 0; index < maxLength; index++) {
            int leftPart = index < leftParts.size() ? leftParts.get(index) : 0;
            int rightPart = index < rightParts.size() ? rightParts.get(index) : 0;

            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }

        return 0;
    }

    private String normalizeVersion(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.toLowerCase(Locale.ROOT).startsWith("v")) {
            normalized = normalized.substring(1);
        }

        return normalized.isBlank() ? null : normalized;
    }

    private List<Integer> parseVersionParts(String version) {
        String[] parts = version.split("\\.");
        if (parts.length == 0) {
            return null;
        }

        List<Integer> parsed = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                return null;
            }

            for (int index = 0; index < part.length(); index++) {
                if (!Character.isDigit(part.charAt(index))) {
                    return null;
                }
            }

            try {
                parsed.add(Integer.parseInt(part));
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        return parsed;
    }

    private static String sanitizeReleaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_RELEASES_URL;
        }
        return raw.trim();
    }

    private record CachedRelease(
            String latestVersion,
            String releaseUrl,
            String publishedAt,
            Instant fetchedAt
    ) {
        private static CachedRelease from(GitHubReleaseInfo releaseInfo, Instant fetchedAt, String defaultReleaseUrl) {
            String normalizedVersion = normalizeLatestVersion(releaseInfo.tagName());
            String resolvedReleaseUrl = releaseInfo.htmlUrl() == null || releaseInfo.htmlUrl().isBlank()
                    ? defaultReleaseUrl
                    : releaseInfo.htmlUrl();
            String publishedAt = releaseInfo.publishedAt() == null ? null : releaseInfo.publishedAt().toString();
            return new CachedRelease(normalizedVersion, resolvedReleaseUrl, publishedAt, fetchedAt);
        }

        private static String normalizeLatestVersion(String rawTagName) {
            if (rawTagName == null) {
                return null;
            }

            String trimmed = rawTagName.trim();
            if (trimmed.isEmpty()) {
                return null;
            }

            return trimmed.toLowerCase(Locale.ROOT).startsWith("v")
                    ? trimmed.substring(1)
                    : trimmed;
        }
    }

    private static final class GitHubReleaseHttpFetcher implements ReleaseFetcher {
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;
        private final String latestReleaseUrl;
        private final Duration readTimeout;

        private GitHubReleaseHttpFetcher(
                ObjectMapper objectMapper,
                String latestReleaseUrl,
                Duration connectTimeout,
                Duration readTimeout
        ) {
            this.objectMapper = objectMapper;
            this.latestReleaseUrl = latestReleaseUrl == null || latestReleaseUrl.isBlank()
                    ? DEFAULT_GITHUB_LATEST_RELEASE_URL
                    : latestReleaseUrl.trim();
            this.readTimeout = readTimeout;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
        }

        @Override
        public GitHubReleaseInfo fetchLatestRelease() throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(latestReleaseUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "GeoPulse-Version-Check")
                    .timeout(readTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub API returned status " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String tagName = textValue(root, "tag_name");
            String htmlUrl = textValue(root, "html_url");
            Instant publishedAt = parsePublishedAt(textValue(root, "published_at"));
            return new GitHubReleaseInfo(tagName, htmlUrl, publishedAt);
        }

        private static String textValue(JsonNode node, String field) {
            JsonNode valueNode = node.path(field);
            if (!valueNode.isTextual()) {
                return null;
            }

            String value = valueNode.asText().trim();
            return value.isEmpty() ? null : value;
        }

        private static Instant parsePublishedAt(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }

            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException exception) {
                return null;
            }
        }
    }
}
