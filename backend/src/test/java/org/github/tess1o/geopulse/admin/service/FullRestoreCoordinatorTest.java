package org.github.tess1o.geopulse.admin.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class FullRestoreCoordinatorTest {
    @Test
    void sanitizesUploadedDisplayNamesBeforePersistingOrLoggingThem() {
        assertThat(FullRestoreCoordinator.safeDisplayName("C:\\uploads\\valid-backup.gpb"))
                .isEqualTo("valid-backup.gpb");
        assertThat(FullRestoreCoordinator.safeDisplayName("../../secret.gpb"))
                .isEqualTo("secret.gpb");
        assertThat(FullRestoreCoordinator.safeDisplayName("backup\nforged.gpb"))
                .isEqualTo("uploaded-backup.gpb");
        assertThat(FullRestoreCoordinator.safeDisplayName("bad\0path.gpb"))
                .isEqualTo("uploaded-backup.gpb");
    }
}
