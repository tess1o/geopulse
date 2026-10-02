package org.github.tess1o.geopulse.version.service;

import java.time.Instant;

public record GitHubReleaseInfo(
        String tagName,
        String htmlUrl,
        Instant publishedAt
) {
}
