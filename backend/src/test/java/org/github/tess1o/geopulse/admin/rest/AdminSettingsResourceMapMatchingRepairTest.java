package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import io.quarkiverse.httpproblem.HttpProblem;
import org.github.tess1o.geopulse.admin.dto.MapMatchingRebuildResponse;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingRebuildMode;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingRebuildResult;
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

    private void givenMapMatchingEnabledAndConfigured() {
        when(mapMatchingConfiguration.isEnabled()).thenReturn(true);
        when(mapMatchingConfiguration.backfillEnabled()).thenReturn(true);
        when(mapMatchingConfiguration.provider()).thenReturn("valhalla");
        when(mapMatchingConfiguration.valhallaConfigured()).thenReturn(true);
    }

    private void givenAdminRequestContext() {
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        when(resource.httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7");
    }

    @Test
    void rebuildMapMatchingRequeuesUnsuccessfulTargetsByDefault() {
        givenMapMatchingEnabledAndConfigured();
        givenAdminRequestContext();
        when(mapMatchingWorker.rebuildMapMatching(MapMatchingRebuildMode.UNSUCCESSFUL))
                .thenReturn(new MapMatchingRebuildResult(MapMatchingRebuildMode.UNSUCCESSFUL, 2L, 5L, 3L));

        MapMatchingRebuildResponse response = resource.rebuildMapMatching(null);

        assertThat(response.mode()).isEqualTo(MapMatchingRebuildMode.UNSUCCESSFUL);
        assertThat(response.queuedUsers()).isEqualTo(2L);
        assertThat(response.affectedTargets()).isEqualTo(5L);
        assertThat(response.purgedDetachedTargets()).isEqualTo(3L);
        verify(mapMatchingWorker).rebuildMapMatching(MapMatchingRebuildMode.UNSUCCESSFUL);
        verify(auditLogService).logAction(
                adminId,
                ActionType.MAP_MATCHING_HISTORICAL_REBUILD,
                TargetType.SETTING,
                "map-matching.historical-rebuild",
                Map.of("mode", "UNSUCCESSFUL", "queuedUsers", 2L, "affectedTargets", 5L),
                "203.0.113.7"
        );
    }

    @Test
    void rebuildMapMatchingWithAllModeClearsEveryStoredResult() {
        givenMapMatchingEnabledAndConfigured();
        givenAdminRequestContext();
        when(mapMatchingWorker.rebuildMapMatching(MapMatchingRebuildMode.ALL))
                .thenReturn(new MapMatchingRebuildResult(MapMatchingRebuildMode.ALL, 3L, 466L, 0L));

        MapMatchingRebuildResponse response = resource.rebuildMapMatching("all");

        assertThat(response.mode()).isEqualTo(MapMatchingRebuildMode.ALL);
        assertThat(response.affectedTargets()).isEqualTo(466L);
        verify(auditLogService).logAction(
                adminId,
                ActionType.MAP_MATCHING_CACHE_CLEARED,
                TargetType.SETTING,
                "map-matching.cache",
                Map.of("mode", "ALL", "queuedUsers", 3L, "affectedTargets", 466L),
                "203.0.113.7"
        );
    }

    @Test
    void rebuildMapMatchingRejectsUnknownMode() {
        givenMapMatchingEnabledAndConfigured();

        assertThatThrownBy(() -> resource.rebuildMapMatching("EVERYTHING"))
                .isInstanceOf(HttpProblem.class)
                .satisfies(error -> assertThat(((HttpProblem) error).getDetail())
                        .isEqualTo("Unknown map matching mode: EVERYTHING"));
        verifyNoInteractions(mapMatchingWorker, auditLogService);
    }

    @Test
    void rebuildMapMatchingRejectsDisabledMapMatching() {
        when(mapMatchingConfiguration.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> resource.rebuildMapMatching(null))
                .isInstanceOf(HttpProblem.class)
                .satisfies(error -> assertThat(((HttpProblem) error).getDetail()).isEqualTo("Map matching is disabled"));
        verifyNoInteractions(mapMatchingWorker, auditLogService);
    }

    @Test
    void rebuildMapMatchingRejectsDisabledBackfill() {
        when(mapMatchingConfiguration.isEnabled()).thenReturn(true);
        when(mapMatchingConfiguration.backfillEnabled()).thenReturn(false);

        assertThatThrownBy(() -> resource.rebuildMapMatching(null))
                .isInstanceOf(HttpProblem.class)
                .satisfies(error -> assertThat(((HttpProblem) error).getDetail()).isEqualTo("Historical backfill is disabled"));
        verifyNoInteractions(mapMatchingWorker, auditLogService);
    }

    @Test
    void rebuildMapMatchingRejectsUnconfiguredValhalla() {
        when(mapMatchingConfiguration.isEnabled()).thenReturn(true);
        when(mapMatchingConfiguration.backfillEnabled()).thenReturn(true);
        when(mapMatchingConfiguration.provider()).thenReturn("valhalla");
        when(mapMatchingConfiguration.valhallaConfigured()).thenReturn(false);

        assertThatThrownBy(() -> resource.rebuildMapMatching(null))
                .isInstanceOf(HttpProblem.class)
                .satisfies(error -> assertThat(((HttpProblem) error).getDetail()).isEqualTo("Valhalla is not configured"));
        verifyNoInteractions(mapMatchingWorker, auditLogService);
    }
}
