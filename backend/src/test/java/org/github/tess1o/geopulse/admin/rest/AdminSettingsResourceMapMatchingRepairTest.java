package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import io.quarkiverse.httpproblem.HttpProblem;
import org.github.tess1o.geopulse.admin.dto.MapMatchingQueueRebuildResponse;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;
import org.github.tess1o.geopulse.mapmatching.service.MapMatchingWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AdminSettingsResourceMapMatchingRepairTest {

    @Mock
    MapMatchingConfiguration mapMatchingConfiguration;
    @Mock
    MapMatchingWorker mapMatchingWorker;
    @Mock
    CurrentUserService currentUserService;
    @Mock
    AuditLogService auditLogService;
    @Mock
    HttpServerRequest httpRequest;

    AdminSettingsResource resource;
    UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        resource = new AdminSettingsResource();
        resource.mapMatchingConfiguration = mapMatchingConfiguration;
        resource.mapMatchingWorker = mapMatchingWorker;
        resource.currentUserService = currentUserService;
        resource.auditLogService = auditLogService;
        resource.httpRequest = httpRequest;
    }

    @Test
    void rebuildMapMatchingHistoricalQueueRestartsQueueAndAuditsAction() {
        when(mapMatchingConfiguration.isEnabled()).thenReturn(true);
        when(mapMatchingConfiguration.backfillEnabled()).thenReturn(true);
        when(mapMatchingConfiguration.provider()).thenReturn("valhalla");
        when(mapMatchingConfiguration.valhallaConfigured()).thenReturn(true);
        when(mapMatchingWorker.rebuildHistoricalQueue()).thenReturn(2L);
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);

        MapMatchingQueueRebuildResponse response = resource.rebuildMapMatchingHistoricalQueue("203.0.113.7", null);

        assertThat(response.queuedUsers()).isEqualTo(2L);
        verify(mapMatchingWorker).rebuildHistoricalQueue();
        verify(auditLogService).logAction(
                adminId,
                ActionType.MAP_MATCHING_HISTORICAL_REBUILD,
                TargetType.SETTING,
                "map-matching.historical-rebuild",
                Map.of("queuedUsers", 2L),
                "203.0.113.7"
        );
    }

    @Test
    void rebuildMapMatchingHistoricalQueueRejectsDisabledMapMatching() {
        when(mapMatchingConfiguration.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> resource.rebuildMapMatchingHistoricalQueue(null, null))
                .isInstanceOf(HttpProblem.class)
                .satisfies(error -> assertThat(((HttpProblem) error).getDetail()).isEqualTo("Map matching is disabled"));
        verifyNoInteractions(mapMatchingWorker, auditLogService);
    }

    @Test
    void rebuildMapMatchingHistoricalQueueRejectsDisabledBackfill() {
        when(mapMatchingConfiguration.isEnabled()).thenReturn(true);
        when(mapMatchingConfiguration.backfillEnabled()).thenReturn(false);

        assertThatThrownBy(() -> resource.rebuildMapMatchingHistoricalQueue(null, null))
                .isInstanceOf(HttpProblem.class)
                .satisfies(error -> assertThat(((HttpProblem) error).getDetail()).isEqualTo("Historical backfill is disabled"));
        verifyNoInteractions(mapMatchingWorker, auditLogService);
    }
}
