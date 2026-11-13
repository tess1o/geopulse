package org.github.tess1o.geopulse.admin.service;

import java.nio.file.Path;

/**
 * A temporary encrypted backup prepared for a single browser download. The caller must delete {@code path}.
 */
public record PreparedDownloadBackup(Path path, String fileName, long sizeBytes) {
}
