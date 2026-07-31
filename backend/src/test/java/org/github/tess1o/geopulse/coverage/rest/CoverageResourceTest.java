package org.github.tess1o.geopulse.coverage.rest;

import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.coverage.model.CoverageStatus;
import org.github.tess1o.geopulse.coverage.service.CoverageProcessingService;
import org.github.tess1o.geopulse.coverage.service.CoverageService;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CoverageResourceTest {

    @Mock
    CoverageService coverageService;

    @Mock
    CoverageProcessingService processingService;

    @Mock
    CurrentUserService currentUserService;

    @Mock
    ImportJobService importJobService;

    CoverageResource coverageResource;
    UUID userId;
    UserEntity user;

    @BeforeEach
    void setUp() {
        coverageResource = new CoverageResource(
                coverageService,
                processingService,
                currentUserService,
                importJobService
        );
        userId = UUID.randomUUID();
        user = new UserEntity();
        user.setId(userId);
        user.setCoverageEnabled(true);
    }

    @Test
    void recalculateCoverage_returnsConflictWhenImportIsActive() {
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(importJobService.hasActiveImportJob(userId)).thenReturn(true);

        Response response = coverageResource.recalculateCoverage();

        assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
        assertThat((ApiResponse<?>) response.getEntity())
                .extracting(ApiResponse::getStatus, ApiResponse::getMessage)
                .containsExactly("error", "Coverage recalculation is already managed by the active import job");
        verifyNoInteractions(processingService);
    }

    @Test
    void recalculateCoverage_startsFullRecalculationWhenNoImportIsActive() {
        CoverageStatus status = new CoverageStatus(true, true, false, null, null);
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(importJobService.hasActiveImportJob(userId)).thenReturn(false);
        when(coverageService.getCoverageStatus(userId)).thenReturn(status);

        Response response = coverageResource.recalculateCoverage();

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        verify(processingService).startFullRecalculationAsync(userId);
    }
}
