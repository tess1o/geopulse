package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.backup.NativeBackupContext;
import org.github.tess1o.geopulse.admin.backup.PostgresTarget;
import org.github.tess1o.geopulse.admin.backup.RestoreOperationState;
import org.github.tess1o.geopulse.admin.backup.RestoreState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class BackupMaintenanceServiceLockTest {
    @TempDir Path directory;

    @Test
    void failedExclusiveUpgradeRestoresSharedLockAndBecomesRetryable() throws Exception {
        BackupMaintenanceService service = serviceWithLockResults(true, false, true, true);

        assertThat(service.beginActivation()).isFalse();

        assertThat(service.restoreState().state).isEqualTo(RestoreOperationState.ACTIVATION_RETRYABLE);
        assertThat(field(service, "operationLocked")).isEqualTo(false);
    }

    @Test
    void failedSharedLockRecoveryFailsClosedAndKeepsOperationLock() throws Exception {
        BackupMaintenanceService service = serviceWithLockResults(true, false, false, false);

        assertThatThrownBy(service::beginActivation).isInstanceOf(IllegalStateException.class);

        assertThat(service.restoreState().state).isEqualTo(RestoreOperationState.ACTIVATION_FAILED);
        assertThat(service.isRestoreBlocked()).isTrue();
        assertThat(field(service, "operationLocked")).isEqualTo(true);
    }

    private BackupMaintenanceService serviceWithLockResults(Boolean... results) throws Exception {
        Deque<Boolean> values = new ArrayDeque<>(java.util.List.of(results));
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet result = mock(ResultSet.class);
            when(statement.executeQuery()).thenReturn(result);
            when(result.next()).thenReturn(true);
            when(result.getBoolean(1)).thenAnswer(ignored -> values.removeFirst());
            return statement;
        });

        PostgresTarget postgres = new PostgresTarget("jdbc:postgresql://localhost/geopulse",
                "app", "password", "admin", "admin-password", "postgres", "test-instance");
        NativeBackupContext context = new NativeBackupContext(postgres, directory, "", directory.resolve("key").toString(), "test");
        RestoreState state = new RestoreState();
        state.fileName = "backup.gpb";
        String suffix = state.operationId.replace("-", "");
        state.originalDatabase = "geopulse";
        state.stagingDatabase = "gp_restore_" + suffix;
        state.previousDatabase = "gp_previous_" + suffix;
        state.originalOid = 10;
        state.stagingOid = 11;

        BackupMaintenanceService service = new BackupMaintenanceService();
        set(service, "context", context);
        set(service, "coordination", connection);
        set(service, "restore", state);
        set(service, "operationLocked", true);
        return service;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = BackupMaintenanceService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = BackupMaintenanceService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
