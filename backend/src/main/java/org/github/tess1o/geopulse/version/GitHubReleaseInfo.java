package org.github.tess1o.geopulse.version;

import java.time.Instant;

record GitHubReleaseInfo(
        String tagName,
        String htmlUrl,
        Instant publishedAt
) {
}
