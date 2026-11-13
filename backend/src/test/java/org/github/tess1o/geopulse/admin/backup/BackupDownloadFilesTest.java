package org.github.tess1o.geopulse.admin.backup;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class BackupDownloadFilesTest {
    @TempDir Path root;

    @Test
    void createsDownloadDirectoryAndTemporaryPath() throws Exception {
        Path directory = BackupDownloadFiles.directory(root);
        Path temporary = BackupDownloadFiles.temporaryFile(root, "geopulse-full-backup-1-test.gpb");

        assertThat(directory).isDirectory().isEqualTo(root.resolve("downloads"));
        assertThat(temporary).isEqualTo(directory.resolve("geopulse-full-backup-1-test.gpb.part"));
        assertThat(temporary).doesNotExist();
    }

    @Test
    void rejectsFileNamesWithPathSegments() {
        assertThatThrownByInvalidName("subdir/backup.gpb");
        assertThatThrownByInvalidName("../backup.gpb");
        assertThatThrownByInvalidName(null);
    }

    @Test
    void deletesOnlyStaleRegularFiles() throws Exception {
        Path directory = BackupDownloadFiles.directory(root);
        Path stale = Files.writeString(directory.resolve("stale.gpb.part"), "stale");
        Path fresh = Files.writeString(directory.resolve("fresh.gpb.part"), "fresh");
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofDays(3))));

        int removed = BackupDownloadFiles.deleteStale(root, Duration.ofHours(48));

        assertThat(removed).isEqualTo(1);
        assertThat(stale).doesNotExist();
        assertThat(fresh).exists();
    }

    @Test
    void toleratesMissingDirectory() {
        assertThat(BackupDownloadFiles.deleteStale(root.resolve("missing"), Duration.ofHours(48))).isZero();
    }

    @Test
    void doesNotFollowSymlinksWhenDeletingStaleFiles() throws Exception {
        Path directory = BackupDownloadFiles.directory(root);
        Path target = Files.writeString(root.resolve("outside.gpb"), "outside");
        Files.setLastModifiedTime(target, FileTime.from(Instant.now().minus(Duration.ofDays(3))));
        Path link = directory.resolve("link.gpb.part");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return; // File system does not support symlinks; nothing to verify.
        }

        BackupDownloadFiles.deleteStale(root, Duration.ofHours(48));

        assertThat(target).exists();
    }

    private void assertThatThrownByInvalidName(String name) {
        assertThatThrownBy(() -> BackupDownloadFiles.temporaryFile(root, name))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Invalid backup file name");
    }
}
