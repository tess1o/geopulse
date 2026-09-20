package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.model.AuditLogEntity;
import org.github.tess1o.geopulse.admin.repository.AuditLogRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("unit")
class AuditLogServiceTest {

    @Test
    void redactedSettingChangeNeverPersistsValues() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogService service = new AuditLogService(repository);

        service.logSettingChange(UUID.randomUUID(), "backup.password", "old-secret", "new-secret", true, "127.0.0.1");

        ArgumentCaptor<AuditLogEntity> entry = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repository).persist(entry.capture());
        assertThat(entry.getValue().getDetails()).isEqualTo(Map.of("redacted", true));
    }
}
