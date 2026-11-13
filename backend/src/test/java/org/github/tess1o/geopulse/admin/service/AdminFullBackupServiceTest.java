package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupConfigDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class AdminFullBackupServiceTest {
    @TempDir Path directory;

    @Test
    void requiresTwelveToOneThousandTwentyFourCharactersForNewPasswords() {
        AdminFullBackupService service = service();

        assertThatThrownBy(() -> service.validateConfig(config("short")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12 and 1024");
        assertThatCode(() -> service.validateConfig(config("a".repeat(12)))).doesNotThrowAnyException();
        assertThatCode(() -> service.validateConfig(config("a".repeat(1024)))).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validateConfig(config("a".repeat(1025))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullConfiguration() {
        assertThatThrownBy(() -> service().validateConfig(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dedicatedPasswordUpdateWritesOnlyThePasswordSetting() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        AdminFullBackupService service = new AdminFullBackupService();
        service.settingsService = settings;
        UUID adminId = UUID.randomUUID();

        service.updatePassword("twelve-chars!", adminId);

        verify(settings).setValue("backup.password", "twelve-chars!", adminId);
        verifyNoMoreInteractions(settings);
    }

    @Test
    void dedicatedPasswordUpdateRejectsBlankAndOutOfBoundsPasswords() {
        AdminFullBackupService service = service();
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> service.updatePassword(null, adminId)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updatePassword("", adminId)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updatePassword("short", adminId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12 and 1024");
        assertThatThrownBy(() -> service.updatePassword("a".repeat(1025), adminId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12 and 1024");
    }

    private AdminFullBackupService service() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.getString("backup.password")).thenReturn("");
        AdminFullBackupService service = new AdminFullBackupService();
        service.settingsService = settings;
        return service;
    }

    private AdminBackupConfigDto config(String password) {
        return AdminBackupConfigDto.builder()
                .scheduledEnabled(false)
                .scheduledCron("0 0 3 * * ?")
                .localPath(directory.toString())
                .retentionCount(7)
                .operationTimeoutMinutes(120)
                .password(password)
                .build();
    }
}
