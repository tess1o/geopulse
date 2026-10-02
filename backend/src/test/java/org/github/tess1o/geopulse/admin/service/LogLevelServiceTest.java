package org.github.tess1o.geopulse.admin.service;

import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class LogLevelServiceTest {

    private static final String CATEGORY = "org.github.tess1o.geopulse";
    private final Logger categoryLogger = LogContext.getLogContext().getLogger(CATEGORY);
    private final Level originalLevel = categoryLogger.getLevel();

    @AfterEach
    void restoreLevel() {
        categoryLogger.setLevel(originalLevel);
    }

    @Test
    void appliesAdminLevelToChildCategories() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.isDefault(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn(false);
        when(settings.getString(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn("debug");
        LogLevelService service = new LogLevelService();
        service.settingsService = settings;

        service.reconcile();

        Logger child = LogContext.getLogContext().getLogger(CATEGORY + ".gps");
        child.setLevel(null);
        assertThat(child.isLoggable(Level.FINE)).isTrue();
        assertThat(service.getStatus()).isEqualTo(
                new LogLevelService.LoggingStatus("DEBUG", "DEBUG", LogLevelService.Source.ADMIN));
    }

    @Test
    void invalidEnvironmentValueLeavesLoggerUntouched() {
        categoryLogger.setLevel(Level.WARNING);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.isDefault(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn(true);
        when(settings.getString(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn("verbose");
        LogLevelService service = new LogLevelService();
        service.settingsService = settings;

        service.reconcile();

        assertThat(categoryLogger.getLevel()).isEqualTo(Level.WARNING);
    }

    @Test
    void resetRestoresInheritedLevel() {
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.isDefault(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn(false, true);
        when(settings.getString(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn("ERROR", "inherit");
        LogLevelService service = new LogLevelService();
        service.settingsService = settings;

        service.reconcile();
        service.reconcile();

        assertThat(categoryLogger.getLevel()).isNull();
        assertThat(service.getStatus().source()).isEqualTo(LogLevelService.Source.DEFAULT);
    }

    @Test
    void startupKeepsConfiguredBaseline() {
        categoryLogger.setLevel(Level.WARNING);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.isDefault(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn(true);
        when(settings.getString(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn("inherit");
        LogLevelService service = new LogLevelService();
        service.settingsService = settings;

        service.onStart(null);

        assertThat(categoryLogger.getLevel()).isEqualTo(Level.WARNING);
        assertThat(service.getStatus().source()).isEqualTo(LogLevelService.Source.DEFAULT);
    }

    @Test
    void defaultSourceRestoresConfiguredBaselineAfterOverride() {
        categoryLogger.setLevel(Level.WARNING);
        SystemSettingsService settings = mock(SystemSettingsService.class);
        when(settings.isDefault(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn(false, true);
        when(settings.getString(SystemSettingsService.APPLICATION_LOG_LEVEL_KEY)).thenReturn("debug", "inherit");
        LogLevelService service = new LogLevelService();
        service.settingsService = settings;

        service.onStart(null);
        assertThat(categoryLogger.getLevel()).isEqualTo(Level.FINE);

        service.reconcile();

        assertThat(categoryLogger.getLevel()).isEqualTo(Level.WARNING);
        assertThat(service.getStatus().source()).isEqualTo(LogLevelService.Source.DEFAULT);
    }
}
