package org.github.tess1o.geopulse.geocoding.service;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.geocoding.dto.GeocodingCandidateSelection;
import org.github.tess1o.geopulse.geocoding.mapper.ReverseGeocodingDTOMapper;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingCandidate;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class GeocodingCandidateSelectionTest {
    UUID user = UUID.randomUUID();
    ReverseGeocodingLocationRepository repository = mock(ReverseGeocodingLocationRepository.class);
    GeocodingProviderFactory factory = mock(GeocodingProviderFactory.class);
    GeocodingCopyOnWriteHandler handler = mock(GeocodingCopyOnWriteHandler.class);
    UserLocationNormalizationService normalization = mock(UserLocationNormalizationService.class);
    GeocodingReconciliationService service = new GeocodingReconciliationService(repository, factory,
            new ReverseGeocodingDTOMapper(), handler, normalization);
    ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
    SimpleFormattableResult selected;

    @BeforeEach
    void setup() {
        entity.setId(1L);
        entity.setRequestCoordinates(GeoUtils.createPoint(-1.0, 54.0));
        entity.setDisplayName("Original");
        entity.setProviderName("Photon");
        when(repository.findById(1L)).thenReturn(entity);
        when(repository.getEntityManager()).thenReturn(mock(EntityManager.class));
        when(factory.getEnabledProviders()).thenReturn(List.of("Photon"));
        selected = SimpleFormattableResult.builder().requestCoordinates(entity.getRequestCoordinates())
                .resultCoordinates(GeoUtils.createPoint(-1.001, 54.0)).formattedDisplayName("Second")
                .providerName("Photon").city("City").country("Country").build();
        var first = SimpleFormattableResult.builder().requestCoordinates(entity.getRequestCoordinates())
                .resultCoordinates(entity.getRequestCoordinates()).formattedDisplayName("First").providerName("Photon").build();
        when(factory.reconciliationCandidates("Photon", entity.getRequestCoordinates())).thenReturn(Uni.createFrom().item(
                List.of(new GeocodingCandidate(first, null, null), new GeocodingCandidate(selected, "cafe", "Street"))));
    }

    @Test
    void selectsExactPreviewedSecondCandidateAndNormalizesBeforeExistingSavePath() {
        var preview = service.previewCandidates(user, 1L, "Photon");
        assertEquals("Second", preview.candidates().get(1).displayName());
        assertTrue(preview.candidates().get(1).distanceMeters() > 0);
        verifyNoInteractions(handler);
        when(normalization.normalizeForUser(user, "City", "Country"))
                .thenReturn(new UserLocationNormalizationService.NormalizedLocation("Normalized", "Country", true));
        when(handler.handleReconciliation(eq(user), eq(entity), any())).thenAnswer(invocation -> {
            var result = invocation.getArgument(2, org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult.class);
            assertEquals("Normalized", result.getCity());
            assertEquals(selected.getResultCoordinates(), result.getResultCoordinates());
            assertEquals(entity.getRequestCoordinates(), result.getRequestCoordinates());
            return GeocodingCopyOnWriteHandler.ReconciliationResult.updated(entity);
        });
        assertEquals(1L, service.selectCandidate(user, 1L, new GeocodingCandidateSelection(preview.previewId(), 1)).getId());
        verify(factory, times(1)).reconciliationCandidates(anyString(), any());
        assertThrows(GeoPulseException.class, () -> service.selectCandidate(user, 1L, new GeocodingCandidateSelection(preview.previewId(), 1)));
    }

    @Test
    void previewsAreUserBoundAndOnlyLatestPreviewCanBeApplied() {
        var first = service.previewCandidates(user, 1L, "Photon");
        var second = service.previewCandidates(user, 1L, "Photon");
        assertThrows(GeoPulseException.class, () -> service.selectCandidate(user, 1L, new GeocodingCandidateSelection(first.previewId(), 0)));
        assertThrows(GeoPulseException.class, () -> service.selectCandidate(UUID.randomUUID(), 1L, new GeocodingCandidateSelection(second.previewId(), 0)));
        verifyNoInteractions(handler);
    }

    @Test
    void rejectsExpiredPreview() {
        service.clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC);
        var preview = service.previewCandidates(user, 1L, "Photon");
        service.clock = Clock.offset(service.clock, Duration.ofMinutes(10));
        assertThrows(GeoPulseException.class, () -> service.selectCandidate(user, 1L, new GeocodingCandidateSelection(preview.previewId(), 0)));
        service.expirePreviews();
        verifyNoInteractions(handler);
    }

    @Test
    void rejectsStaleTargetInvalidCandidateAndDisabledProvider() {
        var preview = service.previewCandidates(user, 1L, "Photon");
        assertThrows(GeoPulseException.class, () -> service.selectCandidate(user, 1L, new GeocodingCandidateSelection(preview.previewId(), 9)));
        entity.setCity("Changed");
        assertThrows(GeoPulseException.class, () -> service.selectCandidate(user, 1L, new GeocodingCandidateSelection(preview.previewId(), 0)));
        entity.setCity(null);
        when(factory.getEnabledProviders()).thenReturn(List.of());
        assertThrows(GeoPulseException.class, () -> service.selectCandidate(user, 1L, new GeocodingCandidateSelection(preview.previewId(), 0)));
        verifyNoInteractions(handler);
    }

    @Test
    void checksOwnershipBeforeCallingProviderAndAgainBeforeSaving() {
        var preview = service.previewCandidates(user, 1L, "Photon");
        var owner = new UserEntity();
        owner.setId(UUID.randomUUID());
        entity.setUser(owner);
        assertThrows(GeoPulseException.class, () -> service.previewCandidates(user, 1L, "Photon"));
        assertThrows(GeoPulseException.class, () -> service.selectCandidate(user, 1L, new GeocodingCandidateSelection(preview.previewId(), 0)));
        verify(factory, times(1)).reconciliationCandidates(anyString(), any());
        verifyNoInteractions(handler);
    }
}
