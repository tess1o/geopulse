package org.github.tess1o.geopulse.version;

interface ReleaseFetcher {
    GitHubReleaseInfo fetchLatestRelease() throws Exception;
}
