package org.github.tess1o.geopulse.version.service;

interface ReleaseFetcher {
    GitHubReleaseInfo fetchLatestRelease() throws Exception;
}
