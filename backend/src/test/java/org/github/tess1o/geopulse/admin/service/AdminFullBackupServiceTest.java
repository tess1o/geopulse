package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupConfigDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
