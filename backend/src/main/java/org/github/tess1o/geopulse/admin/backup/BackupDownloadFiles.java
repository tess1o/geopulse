package org.github.tess1o.geopulse.admin.backup;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Ephemeral backup downloads live under the backup work path and are removed after the response is streamed.
 */
@Slf4j
public final class BackupDownloadFiles {
    private static final String DIRECTORY_NAME = "downloads";

    private BackupDownloadFiles() {
    }

    public static Path directory(Path workPath) throws IOException {
        Path directory = workPath.resolve(DIRECTORY_NAME).toAbsolutePath().normalize();
        RestoreJournal.secureDirectory(directory);
        return directory;
    }

    public static Path temporaryFile(Path workPath, String backupFileName) throws IOException {
        if (backupFileName == null || Path.of(backupFileName).getNameCount() != 1) {
            throw new IOException("Invalid backup file name");
        }
        return directory(workPath).resolve(backupFileName + ".part");
    }

    public static void deleteQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete temporary backup download file {}", file, e);
        }
    }

    public static int deleteStale(Path workPath, Duration maxAge) {
        Path directory = workPath.resolve(DIRECTORY_NAME);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return 0;
        Instant cutoff = Instant.now().minus(maxAge);
        int removed = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    if (Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        removed++;
                    }
                } catch (IOException e) {
                    log.warn("Could not inspect or delete stale temporary backup download file {}", file, e);
                }
            }
        } catch (IOException e) {
            log.warn("Could not enumerate temporary backup download files in {}", directory, e);
        }
        return removed;
    }
}
