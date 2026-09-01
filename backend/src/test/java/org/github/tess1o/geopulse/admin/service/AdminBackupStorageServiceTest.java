package org.github.tess1o.geopulse.admin.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class AdminBackupStorageServiceTest {
    @TempDir Path directory;

    @Test
    void resolvesOnlyStrictRegularBackupFilesInsideConfiguredDirectory() throws Exception {
        AdminBackupStorageService service = service();
        String validName = "geopulse-full-backup-1700000000000-" + UUID.randomUUID() + ".gpb";
        Path valid = Files.writeString(directory.resolve(validName), "encrypted");

        assertThat(service.resolve(validName)).isEqualTo(valid);
        assertThatThrownBy(() -> service.resolve("../" + validName)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolve(validName + "\n.log")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolve("other.gpb")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolve("geopulse-full-backup-1700000000000-------------------------------------.gpb"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void doesNotListSymlinksOrTemporaryArtifacts() throws Exception {
        AdminBackupStorageService service = service();
        String validName = "geopulse-full-backup-1700000000000-" + UUID.randomUUID() + ".gpb";
        Path target = Files.writeString(directory.resolve("target.bin"), "outside");
        Files.createSymbolicLink(directory.resolve(validName), target.getFileName());
        Files.writeString(directory.resolve(validName + ".tmp"), "partial");

        assertThat(service.list()).isEmpty();
    }

    @Test
    void retentionEnumerationFailureDoesNotFailACompletedPublication() throws Exception {
        Path notDirectory = Files.writeString(directory.resolve("not-a-directory"), "data");
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getString("backup.local.path")).thenReturn(notDirectory.toString());
        AdminBackupStorageService service = new AdminBackupStorageService();
        service.settingsService = settings;

        assertThatCode(() -> service.prune(7, "test-operation")).doesNotThrowAnyException();
    }

    private AdminBackupStorageService service() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getString("backup.local.path")).thenReturn(directory.toString());
        AdminBackupStorageService service = new AdminBackupStorageService();
        service.settingsService = settings;
        return service;
    }
}
