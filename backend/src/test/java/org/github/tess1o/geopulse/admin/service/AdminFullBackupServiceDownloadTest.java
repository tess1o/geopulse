package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.backup.BackupPhase;
import org.github.tess1o.geopulse.admin.backup.NativeBackupContext;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupFileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class AdminFullBackupServiceDownloadTest {
    @TempDir Path root;
    Path workPath;
    Path localPath;
    SystemSettingsService settings;
    BackupMaintenanceService maintenance;
    AdminBackupStorageService storage;
    TestableBackupService service;

    @BeforeEach
    void setUp() throws Exception {
        workPath = Files.createDirectories(root.resolve("work"));
        localPath = Files.createDirectories(root.resolve("local"));
        settings = mock(SystemSettingsService.class);
        when(settings.getString("backup.password")).thenReturn("twelve-chars!");
        when(settings.getString("backup.local.path")).thenReturn(localPath.toString());
        maintenance = mock(BackupMaintenanceService.class);
        when(maintenance.context()).thenReturn(new NativeBackupContext(null, workPath, "", "", "test"));
        storage = new AdminBackupStorageService();
        storage.settingsService = settings;
        service = serviceWith(writer());
    }

    @Test
    void downloadBackupStaysOutOfTheLocalBackupFolder() throws Exception {
        PreparedDownloadBackup prepared = service.writeDownloadBackup("backup-operation");

        assertThat(prepared.path()).startsWith(workPath.resolve("downloads"));
        assertThat(prepared.fileName()).endsWith(".gpb").doesNotContain(".part");
        assertThat(prepared.sizeBytes()).isEqualTo(Files.size(prepared.path()));
        assertThat(Files.readString(prepared.path())).isEqualTo("part-onepart-two");
        try (var files = Files.list(localPath)) {
            assertThat(files).isEmpty();
        }
        assertThat(service.listLocalBackups()).isEmpty();
        verify(maintenance).updateBackupOutput(prepared.path());
        verify(maintenance).updateBackupPhase(BackupPhase.SNAPSHOT);
        verify(maintenance).updateBackupPhase(BackupPhase.DUMPING);
        verify(maintenance).updateBackupPhase(BackupPhase.FINALIZING);
    }

    @Test
    void failedDownloadLeavesNoTemporaryFile() throws Exception {
        service = serviceWith((output, password, deadline, operationId, listener) -> {
            listener.onPhase(BackupPhase.DUMPING);
            Files.writeString(output, "partial");
            throw new IOException("pg_dump failed");
        });

        assertThatThrownBy(() -> service.writeDownloadBackup("backup-operation")).hasMessage("pg_dump failed");

        Path downloads = workPath.resolve("downloads");
        try (var files = Files.list(downloads)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void localBackupStillPublishesIntoTheBackupFolder() throws Exception {
        when(settings.getInteger("backup.retention.count")).thenReturn(7);
        when(settings.getInteger("backup.operation.timeout-minutes")).thenReturn(120);
        when(maintenance.currentOperationId()).thenReturn("backup-operation");

        String fileName = service.writeLocalBackup();

        assertThat(localPath.resolve(fileName)).exists();
        assertThat(service.listLocalBackups()).extracting(AdminBackupFileDto::getFileName)
                .containsExactly(fileName);
        assertThat(workPath.resolve("downloads")).doesNotExist();
    }

    private TestableBackupService serviceWith(AdminFullBackupService.ArchiveWriter writer) {
        TestableBackupService testable = new TestableBackupService(writer);
        testable.settingsService = settings;
        testable.maintenanceService = maintenance;
        testable.storageService = storage;
        return testable;
    }

    private static AdminFullBackupService.ArchiveWriter writer() {
        return (output, password, deadline, operationId, listener) -> {
            listener.onPhase(BackupPhase.SNAPSHOT);
            Files.writeString(output, "part-one");
            listener.onPhase(BackupPhase.DUMPING);
            Files.writeString(output, "part-two", StandardOpenOption.APPEND);
            listener.onPhase(BackupPhase.FINALIZING);
        };
    }

    private static final class TestableBackupService extends AdminFullBackupService {
        private final ArchiveWriter writer;

        private TestableBackupService(ArchiveWriter writer) {
            this.writer = writer;
        }

        @Override
        ArchiveWriter archiveWriter() {
            return writer;
        }
    }
}
