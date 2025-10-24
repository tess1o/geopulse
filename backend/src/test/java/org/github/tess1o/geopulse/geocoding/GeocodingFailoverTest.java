package org.github.tess1o.geopulse.geocoding;

import io.smallrye.mutiny.Uni;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.service.*;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for geocoding failover mechanism.
 * Tests that the fallback provider is invoked when the primary provider fails or returns empty results.
 */
@ExtendWith(MockitoExtension.class)
class GeocodingFailoverTest {

    @Mock
    private NominatimGeocodingService nominatimService;

    @Mock
    private GoogleMapsGeocodingService googleMapsService;

    @Mock
    private MapboxGeocodingService mapboxService;

    @Mock
    private PhotonGeocodingService photonService;

    @Mock
    private GeocodingConfig geocodingConfig;

    @Mock
    private GeocodingConfig.Provider provider;

    @Mock
    private GeocodingConfig.Provider.Nominatim nominatimConfig;

    @Mock
    private GeocodingConfig.Provider.GoogleMaps googleMapsConfig;

    @Mock
    private GeocodingConfig.Provider.Mapbox mapboxConfig;

    @Mock
    private GeocodingConfig.Provider.Photon photonConfig;

    private GeocodingProviderFactory providerFactory;

    private Point testCoordinates;

    @BeforeEach
    void setUp() {
        // Setup test coordinates
        testCoordinates = GeoUtils.createPoint(-73.9857, 40.7484); // Times Square

        // Setup config mocks with lenient() to avoid unnecessary stubbing errors
        lenient().when(geocodingConfig.provider()).thenReturn(provider);
        lenient().when(provider.nominatim()).thenReturn(nominatimConfig);
        lenient().when(provider.googlemaps()).thenReturn(googleMapsConfig);
        lenient().when(provider.mapbox()).thenReturn(mapboxConfig);
        lenient().when(provider.photon()).thenReturn(photonConfig);

        // Create factory with mocked services
        providerFactory = new GeocodingProviderFactory(
                nominatimService,
                googleMapsService,
                mapboxService,
                photonService,
                geocodingConfig
        );
    }

    @Test
    void testFailoverTriggersWhenPrimaryProviderFails() {
        // Given: Photon as primary (fails), Nominatim as fallback (succeeds)
        when(provider.primary()).thenReturn("photon");
        when(provider.fallback()).thenReturn(Optional.of("nominatim"));

        when(photonService.isEnabled()).thenReturn(true);
        when(nominatimService.isEnabled()).thenReturn(true);

        // Primary provider fails with exception
        when(photonService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().failure(new GeocodingException("Photon API error")));

        // Fallback provider succeeds
        FormattableGeocodingResult fallbackResult = createMockResult("Times Square", "Nominatim");
        when(nominatimService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().item(fallbackResult));

        // When: reverse geocode is called
        FormattableGeocodingResult result = providerFactory.reverseGeocode(testCoordinates)
                .await().indefinitely();

        // Then: fallback provider should be called and return result
        assertNotNull(result);
        assertEquals("Nominatim", result.getProviderName());
        assertEquals("Times Square", result.getFormattedDisplayName());

        verify(photonService, times(1)).reverseGeocode(testCoordinates);
        verify(nominatimService, times(1)).reverseGeocode(testCoordinates);
    }

    @Test
    void testFailoverTriggersWhenPrimaryProviderReturnsEmptyResults() {
        // Given: GoogleMaps as primary (returns empty results), Nominatim as fallback
        when(provider.primary()).thenReturn("googlemaps");
        when(provider.fallback()).thenReturn(Optional.of("nominatim"));

        when(googleMapsService.isEnabled()).thenReturn(true);
        when(nominatimService.isEnabled()).thenReturn(true);

        // Primary provider returns null (which gets converted to failure)
        when(googleMapsService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().failure(new GeocodingException("Google Maps returned empty response")));

        // Fallback provider succeeds
        FormattableGeocodingResult fallbackResult = createMockResult("Times Square", "Nominatim");
        when(nominatimService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().item(fallbackResult));

        // When: reverse geocode is called
        FormattableGeocodingResult result = providerFactory.reverseGeocode(testCoordinates)
                .await().indefinitely();

        // Then: fallback provider should be called and return result
        assertNotNull(result);
        assertEquals("Nominatim", result.getProviderName());
        assertEquals("Times Square", result.getFormattedDisplayName());

        verify(googleMapsService, times(1)).reverseGeocode(testCoordinates);
        verify(nominatimService, times(1)).reverseGeocode(testCoordinates);
    }

    @Test
    void testNoFailoverWhenPrimaryProviderSucceeds() {
        // Given: Nominatim as primary (succeeds), GoogleMaps as fallback
        when(provider.primary()).thenReturn("nominatim");
        when(provider.fallback()).thenReturn(Optional.of("googlemaps"));

        when(nominatimService.isEnabled()).thenReturn(true);
        // Don't stub googleMapsService.isEnabled() since it won't be called when primary succeeds

        // Primary provider succeeds
        FormattableGeocodingResult primaryResult = createMockResult("Times Square", "Nominatim");
        when(nominatimService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().item(primaryResult));

        // When: reverse geocode is called
        FormattableGeocodingResult result = providerFactory.reverseGeocode(testCoordinates)
                .await().indefinitely();

        // Then: only primary provider should be called
        assertNotNull(result);
        assertEquals("Nominatim", result.getProviderName());
        assertEquals("Times Square", result.getFormattedDisplayName());

        verify(nominatimService, times(1)).reverseGeocode(testCoordinates);
        verify(googleMapsService, never()).reverseGeocode(any());
    }

    @Test
    void testFailsWhenBothPrimaryAndFallbackFail() {
        // Given: Photon as primary (fails), Nominatim as fallback (also fails)
        when(provider.primary()).thenReturn("photon");
        when(provider.fallback()).thenReturn(Optional.of("nominatim"));

        when(photonService.isEnabled()).thenReturn(true);
        when(nominatimService.isEnabled()).thenReturn(true);

        // Both providers fail
        when(photonService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().failure(new GeocodingException("Photon API error")));
        when(nominatimService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().failure(new GeocodingException("Nominatim API error")));

        // When/Then: exception should be thrown (GeocodingException)
        Exception exception = assertThrows(Exception.class, () -> {
            providerFactory.reverseGeocode(testCoordinates)
                    .await().indefinitely();
        });

        // Verify the exception is a GeocodingException (might be wrapped in CompositeException)
        assertTrue(exception instanceof GeocodingException ||
                   (exception.getCause() != null && exception.getCause() instanceof GeocodingException),
                   "Expected GeocodingException but got: " + exception.getClass());

        verify(photonService, times(1)).reverseGeocode(testCoordinates);
        verify(nominatimService, times(1)).reverseGeocode(testCoordinates);
    }

    @Test
    void testNoFailoverWhenFallbackNotConfigured() {
        // Given: Nominatim as primary (fails), no fallback configured
        when(provider.primary()).thenReturn("nominatim");
        when(provider.fallback()).thenReturn(Optional.empty());

        when(nominatimService.isEnabled()).thenReturn(true);

        // Primary provider fails
        when(nominatimService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().failure(new GeocodingException("Nominatim API error")));

        // When/Then: exception should be thrown without trying fallback
        Exception exception = assertThrows(Exception.class, () -> {
            providerFactory.reverseGeocode(testCoordinates)
                    .await().indefinitely();
        });

        // Verify the exception is a GeocodingException
        assertTrue(exception instanceof GeocodingException ||
                   (exception.getCause() != null && exception.getCause() instanceof GeocodingException),
                   "Expected GeocodingException but got: " + exception.getClass());

        verify(nominatimService, times(1)).reverseGeocode(testCoordinates);
        verify(googleMapsService, never()).reverseGeocode(any());
        verify(mapboxService, never()).reverseGeocode(any());
        verify(photonService, never()).reverseGeocode(any());
    }

    @Test
    void testNoFailoverWhenFallbackSameAsPrimary() {
        // Given: Nominatim as both primary and fallback (same provider)
        when(provider.primary()).thenReturn("nominatim");
        when(provider.fallback()).thenReturn(Optional.of("nominatim"));

        when(nominatimService.isEnabled()).thenReturn(true);

        // Primary provider fails
        when(nominatimService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().failure(new GeocodingException("Nominatim API error")));

        // When/Then: exception should be thrown without trying fallback
        Exception exception = assertThrows(Exception.class, () -> {
            providerFactory.reverseGeocode(testCoordinates)
                    .await().indefinitely();
        });

        // Verify the exception is a GeocodingException
        assertTrue(exception instanceof GeocodingException ||
                   (exception.getCause() != null && exception.getCause() instanceof GeocodingException),
                   "Expected GeocodingException but got: " + exception.getClass());

        // Should only be called once (primary), not twice (primary + fallback)
        verify(nominatimService, times(1)).reverseGeocode(testCoordinates);
    }

    @Test
    void testFailoverWithDisabledFallbackProvider() {
        // Given: Photon as primary (fails), GoogleMaps as fallback (disabled)
        when(provider.primary()).thenReturn("photon");
        when(provider.fallback()).thenReturn(Optional.of("googlemaps"));

        when(photonService.isEnabled()).thenReturn(true);
        when(googleMapsService.isEnabled()).thenReturn(false); // Fallback disabled

        // Primary provider fails
        when(photonService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().failure(new GeocodingException("Photon API error")));

        // When/Then: exception should be thrown (fallback won't work because it's disabled)
        Exception exception = assertThrows(Exception.class, () -> {
            providerFactory.reverseGeocode(testCoordinates)
                    .await().indefinitely();
        });

        // Verify the exception is a GeocodingException
        assertTrue(exception instanceof GeocodingException ||
                   (exception.getCause() != null && exception.getCause() instanceof GeocodingException),
                   "Expected GeocodingException but got: " + exception.getClass());

        verify(photonService, times(1)).reverseGeocode(testCoordinates);
        // GoogleMaps reverseGeocode shouldn't be called, but isEnabled() is checked
        verify(googleMapsService, never()).reverseGeocode(any());
    }

    @Test
    void testMultipleProviderFailoverChain() {
        // Given: Mapbox as primary (fails), Nominatim as fallback (succeeds)
        when(provider.primary()).thenReturn("mapbox");
        when(provider.fallback()).thenReturn(Optional.of("nominatim"));

        when(mapboxService.isEnabled()).thenReturn(true);
        when(nominatimService.isEnabled()).thenReturn(true);

        // Primary fails with empty results
        when(mapboxService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().failure(new GeocodingException("Mapbox returned empty response")));

        // Fallback succeeds
        FormattableGeocodingResult fallbackResult = createMockResult("Times Square", "Nominatim");
        when(nominatimService.reverseGeocode(testCoordinates))
                .thenReturn(Uni.createFrom().item(fallbackResult));

        // When: reverse geocode is called
        FormattableGeocodingResult result = providerFactory.reverseGeocode(testCoordinates)
                .await().indefinitely();

        // Then: fallback provider should be used
        assertNotNull(result);
        assertEquals("Nominatim", result.getProviderName());
        assertEquals("Times Square", result.getFormattedDisplayName());

        verify(mapboxService, times(1)).reverseGeocode(testCoordinates);
        verify(nominatimService, times(1)).reverseGeocode(testCoordinates);
    }

    /**
     * Helper method to create a mock geocoding result
     */
    private FormattableGeocodingResult createMockResult(String displayName, String providerName) {
        return SimpleFormattableResult.builder()
                .requestCoordinates(testCoordinates)
                .resultCoordinates(testCoordinates)
                .formattedDisplayName(displayName)
                .providerName(providerName)
                .city("New York")
                .country("United States")
                .build();
    }
}
