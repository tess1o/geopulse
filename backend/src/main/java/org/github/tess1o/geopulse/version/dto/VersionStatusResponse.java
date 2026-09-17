package org.github.tess1o.geopulse.version.dto;

public record VersionStatusResponse(
        String currentVersion,
        String latestVersion,
        boolean updateAvailable,
        String releaseUrl,
        String publishedAt,
        String sourceStatus
) {
}
