package org.github.tess1o.geopulse.geocoding.service;

import jakarta.ws.rs.ForbiddenException;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingUpdateDTO;
import org.github.tess1o.geopulse.geocoding.mapper.GeocodingEntityMapper;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GeocodingCopyOnWriteHandler.
 * Tests business logic paths with mocked dependencies.
 *
 * Coverage: 15 test cases across copy-on-write operations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GeocodingCopyOnWriteHandler Unit Tests")
class GeocodingCopyOnWriteHandlerTest {

    @Mock
    private GeocodingEntityMapper entityMapper;

    @Mock
    private ReverseGeocodingLocationRepository repository;

    @Mock
    private TimelineGeocodingSyncService timelineSyncService;

    private GeocodingCopyOnWriteHandler handler;

    private static final double TEST_LAT = 40.7589;
    private static final double TEST_LON = -73.9851;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new GeocodingCopyOnWriteHandler(entityMapper, repository, timelineSyncService);
    }

    // ==================== Test Suite 1: handleUserUpdate() ====================

    @Nested
    @DisplayName("handleUserUpdate() - User Update with Copy-on-Write")
    class HandleUserUpdateTests {

        @Test
        @DisplayName("Should update own entity in-place")
        void testUserUpdatingOwnEntity() {
            // Given
            ReverseGeocodingLocationEntity entity = createUserOwnedEntity(USER_ID);
            entity.setId(123L);

            ReverseGeocodingUpdateDTO updateDTO = ReverseGeocodingUpdateDTO.builder()
                    .displayName("Updated Name")
                    .city("Updated City")
                    .country("Updated Country")
                    .build();

            // When
            GeocodingCopyOnWriteHandler.UpdateResult result = handler.handleUserUpdate(USER_ID, entity, updateDTO);

            // Then
            assertNotNull(result);
            assertFalse(result.wasCopied(), "Should be in-place update");
            assertNull(result.originalId());
            assertEquals(entity, result.entity());

            verify(entityMapper).updateEntityWithValues(entity, "Updated Name", "Updated City", "Updated Country");
            verify(repository).persist(entity);
            verify(timelineSyncService).updateLocationNameForUser(USER_ID, 123L, "Updated Name");
        }

        @Test
        @DisplayName("Should create copy when modifying original entity")
        void testUserModifyingOriginal() {
            // Given
            ReverseGeocodingLocationEntity original = createOriginalEntity();
            original.setId(100L);

            ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
            userCopy.setId(200L);

            ReverseGeocodingUpdateDTO updateDTO = ReverseGeocodingUpdateDTO.builder()
                    .displayName("Custom Name")
                    .city("Custom City")
                    .country("Custom Country")
                    .build();

            when(entityMapper.createUserCopyWithValues(USER_ID, original, "Custom Name", "Custom City", "Custom Country"))
                    .thenReturn(userCopy);

            // When
            GeocodingCopyOnWriteHandler.UpdateResult result = handler.handleUserUpdate(USER_ID, original, updateDTO);

            // Then
            assertNotNull(result);
            assertTrue(result.wasCopied(), "Should create copy");
            assertEquals(100L, result.originalId());
            assertEquals(userCopy, result.entity());

            verify(entityMapper).createUserCopyWithValues(USER_ID, original, "Custom Name", "Custom City", "Custom Country");
            verify(repository).persist(userCopy);
            verify(timelineSyncService).switchToNewGeocodingReference(USER_ID, 100L, 200L, "Custom Name");
        }

        @Test
        @DisplayName("Should throw ForbiddenException when modifying another user's entity")
        void testUserTryingToModifyAnotherUsersEntity() {
            // Given
            ReverseGeocodingLocationEntity otherUserEntity = createUserOwnedEntity(OTHER_USER_ID);

            ReverseGeocodingUpdateDTO updateDTO = ReverseGeocodingUpdateDTO.builder()
                    .displayName("Hacker Name")
                    .build();

            // When / Then
            assertThrows(ForbiddenException.class, () -> {
                handler.handleUserUpdate(USER_ID, otherUserEntity, updateDTO);
            });

            verify(entityMapper, never()).updateEntityWithValues(
                    any(ReverseGeocodingLocationEntity.class), any(), any(), any());
            verify(entityMapper, never()).createUserCopyWithValues(
                    any(UUID.class), any(ReverseGeocodingLocationEntity.class), any(), any(), any());
            verify(repository, never()).persist(any(ReverseGeocodingLocationEntity.class));
            verify(timelineSyncService, never()).updateLocationNameForUser(
                    any(UUID.class), any(Long.class), any());
            verify(timelineSyncService, never()).switchToNewGeocodingReference(
                    any(UUID.class), any(Long.class), any(Long.class), any());
        }

        @Test
        @DisplayName("Should verify timeline sync called with correct parameters")
        void testTimelineSyncCalledCorrectly() {
            // Given
            ReverseGeocodingLocationEntity entity = createUserOwnedEntity(USER_ID);
            entity.setId(999L);

            ReverseGeocodingUpdateDTO updateDTO = ReverseGeocodingUpdateDTO.builder()
                    .displayName("Test Location")
                    .city("Test City")
                    .country("Test Country")
                    .build();

            // When
            handler.handleUserUpdate(USER_ID, entity, updateDTO);

            // Then
            ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);

            verify(timelineSyncService).updateLocationNameForUser(
                    userIdCaptor.capture(), idCaptor.capture(), nameCaptor.capture());

            assertEquals(USER_ID, userIdCaptor.getValue());
            assertEquals(999L, idCaptor.getValue());
            assertEquals("Test Location", nameCaptor.getValue());
        }

        @Test
        @DisplayName("Should return UpdateResult.updated() for in-place update")
        void testUpdateResultForInPlaceUpdate() {
            // Given
            ReverseGeocodingLocationEntity entity = createUserOwnedEntity(USER_ID);
            ReverseGeocodingUpdateDTO updateDTO = createTestUpdateDTO();

            // When
            GeocodingCopyOnWriteHandler.UpdateResult result = handler.handleUserUpdate(USER_ID, entity, updateDTO);

            // Then
            assertNotNull(result);
            assertFalse(result.wasCopied());
            assertNull(result.originalId());
            assertEquals(entity, result.entity());
        }

        @Test
        @DisplayName("Should return UpdateResult.copied() for copy-on-write")
        void testUpdateResultForCopyOnWrite() {
            // Given
            ReverseGeocodingLocationEntity original = createOriginalEntity();
            original.setId(100L);

            ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
            userCopy.setId(200L);

            ReverseGeocodingUpdateDTO updateDTO = createTestUpdateDTO();

            when(entityMapper.createUserCopyWithValues(any(), any(), any(), any(), any()))
                    .thenReturn(userCopy);

            // When
            GeocodingCopyOnWriteHandler.UpdateResult result = handler.handleUserUpdate(USER_ID, original, updateDTO);

            // Then
            assertNotNull(result);
            assertTrue(result.wasCopied());
            assertEquals(100L, result.originalId());
            assertEquals(userCopy, result.entity());
        }
    }

    // ==================== Test Suite 2: handleReconciliation() ====================

    @Nested
    @DisplayName("handleReconciliation() - Reconciliation with Copy-on-Write")
    class HandleReconciliationTests {

        @Test
        @DisplayName("Should return noChange when data unchanged")
        void testReconciliationWithNoDataChanges() {
            // Given
            Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
            ReverseGeocodingLocationEntity entity = createOriginalEntity();
            entity.setDisplayName("Location Name");
            entity.setCity("City");
            entity.setCountry("Country");

            FormattableGeocodingResult freshResult = SimpleFormattableResult.builder()
                    .requestCoordinates(coords)
                    .resultCoordinates(coords)
                    .formattedDisplayName("Location Name") // Same
                    .city("City") // Same
                    .country("Country") // Same
                    .providerName("provider")
                    .build();

            // When
            GeocodingCopyOnWriteHandler.ReconciliationResult result =
                    handler.handleReconciliation(USER_ID, entity, freshResult);

            // Then
            assertNotNull(result);
            assertFalse(result.changed());
            assertFalse(result.wasCopied());
            assertNull(result.originalId());
            assertEquals(entity, result.entity());

            verify(entityMapper, never()).updateEntityFromResult(
                    any(ReverseGeocodingLocationEntity.class), any(FormattableGeocodingResult.class));
            verify(entityMapper, never()).createUserCopy(
                    any(UUID.class), any(ReverseGeocodingLocationEntity.class), any(FormattableGeocodingResult.class));
            verify(repository, never()).persist(any(ReverseGeocodingLocationEntity.class));
            verify(timelineSyncService, never()).updateLocationNameForUser(
                    any(UUID.class), any(Long.class), any());
        }

        @Test
        @DisplayName("Should create copy when original data changed")
        void testReconciliationCreatesUserCopy() {
            // Given
            ReverseGeocodingLocationEntity original = createOriginalEntity();
            original.setId(100L);
            original.setDisplayName("Old Name");
            original.setCity("Old City");

            ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
            userCopy.setId(200L);

            FormattableGeocodingResult freshResult = createTestResult("New Name", "New City", "New Country");

            when(entityMapper.createUserCopy(USER_ID, original, freshResult)).thenReturn(userCopy);

            // When
            GeocodingCopyOnWriteHandler.ReconciliationResult result =
                    handler.handleReconciliation(USER_ID, original, freshResult);

            // Then
            assertNotNull(result);
            assertTrue(result.changed());
            assertTrue(result.wasCopied());
            assertEquals(100L, result.originalId());
            assertEquals(userCopy, result.entity());

            verify(entityMapper).createUserCopy(USER_ID, original, freshResult);
            verify(repository).persist(userCopy);
            verify(timelineSyncService).switchToNewGeocodingReference(
                    USER_ID, 100L, 200L, "New Name");
        }

        @Test
        @DisplayName("Should update user copy in-place when data changed")
        void testReconciliationUpdatesUserCopy() {
            // Given
            ReverseGeocodingLocationEntity userCopy = createUserOwnedEntity(USER_ID);
            userCopy.setId(200L);
            userCopy.setDisplayName("Old Name");

            FormattableGeocodingResult freshResult = createTestResult("Updated Name", "City", "Country");

            // When
            GeocodingCopyOnWriteHandler.ReconciliationResult result =
                    handler.handleReconciliation(USER_ID, userCopy, freshResult);

            // Then
            assertNotNull(result);
            assertTrue(result.changed());
            assertFalse(result.wasCopied());
            assertNull(result.originalId());
            assertEquals(userCopy, result.entity());

            verify(entityMapper).updateEntityFromResult(userCopy, freshResult);
            verify(repository).persist(userCopy);
            verify(timelineSyncService).updateLocationNameForUser(USER_ID, 200L, "Updated Name");
        }

        @Test
        @DisplayName("Should return ReconciliationResult.noChange() correctly")
        void testReconciliationResultNoChange() {
            // Given
            ReverseGeocodingLocationEntity entity = createOriginalEntity();
            entity.setDisplayName("Name");
            entity.setCity("City");
            entity.setCountry("Country");

            FormattableGeocodingResult freshResult = createTestResult("Name", "City", "Country");

            // When
            GeocodingCopyOnWriteHandler.ReconciliationResult result =
                    handler.handleReconciliation(USER_ID, entity, freshResult);

            // Then
            assertFalse(result.changed());
            assertFalse(result.wasCopied());
            assertNull(result.originalId());
        }

        @Test
        @DisplayName("Should return ReconciliationResult.updated() correctly")
        void testReconciliationResultUpdated() {
            // Given
            ReverseGeocodingLocationEntity userCopy = createUserOwnedEntity(USER_ID);
            userCopy.setDisplayName("Old Name");

            FormattableGeocodingResult freshResult = createTestResult("New Name", "City", "Country");

            // When
            GeocodingCopyOnWriteHandler.ReconciliationResult result =
                    handler.handleReconciliation(USER_ID, userCopy, freshResult);

            // Then
            assertTrue(result.changed());
            assertFalse(result.wasCopied());
            assertNull(result.originalId());
        }

        @Test
        @DisplayName("Should return ReconciliationResult.copied() correctly")
        void testReconciliationResultCopied() {
            // Given
            ReverseGeocodingLocationEntity original = createOriginalEntity();
            original.setId(100L);
            original.setDisplayName("Old Name");

            ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
            userCopy.setId(200L);

            FormattableGeocodingResult freshResult = createTestResult("New Name", "City", "Country");

            when(entityMapper.createUserCopy(any(), any(), any())).thenReturn(userCopy);

            // When
            GeocodingCopyOnWriteHandler.ReconciliationResult result =
                    handler.handleReconciliation(USER_ID, original, freshResult);

            // Then
            assertTrue(result.changed());
            assertTrue(result.wasCopied());
            assertEquals(100L, result.originalId());
        }
    }

    // ==================== Test Suite 3: Data Change Detection ====================

    @Test
    @DisplayName("Data Change Detection: Should detect display name change")
    void testDetectDisplayNameChange() {
        // Given
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        entity.setId(100L);
        entity.setDisplayName("Old Display Name");
        entity.setCity("City");
        entity.setCountry("Country");

        FormattableGeocodingResult freshResult = createTestResult("New Display Name", "City", "Country");

        ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
        userCopy.setId(200L);
        when(entityMapper.createUserCopy(any(UUID.class), any(ReverseGeocodingLocationEntity.class), any(FormattableGeocodingResult.class)))
                .thenReturn(userCopy);

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(USER_ID, entity, freshResult);

        // Then
        assertTrue(result.changed(), "Should detect display name change");
    }

    @Test
    @DisplayName("Data Change Detection: Should detect city change")
    void testDetectCityChange() {
        // Given
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        entity.setId(100L);
        entity.setDisplayName("Name");
        entity.setCity("Old City");
        entity.setCountry("Country");

        FormattableGeocodingResult freshResult = createTestResult("Name", "New City", "Country");

        ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
        userCopy.setId(200L);
        when(entityMapper.createUserCopy(any(UUID.class), any(ReverseGeocodingLocationEntity.class), any(FormattableGeocodingResult.class)))
                .thenReturn(userCopy);

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(USER_ID, entity, freshResult);

        // Then
        assertTrue(result.changed(), "Should detect city change");
    }

    @Test
    @DisplayName("Data Change Detection: Should detect country change")
    void testDetectCountryChange() {
        // Given
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        entity.setId(100L);
        entity.setDisplayName("Name");
        entity.setCity("City");
        entity.setCountry("Old Country");

        FormattableGeocodingResult freshResult = createTestResult("Name", "City", "New Country");

        ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();
        userCopy.setId(200L);
        when(entityMapper.createUserCopy(any(UUID.class), any(ReverseGeocodingLocationEntity.class), any(FormattableGeocodingResult.class)))
                .thenReturn(userCopy);

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(USER_ID, entity, freshResult);

        // Then
        assertTrue(result.changed(), "Should detect country change");
    }

    @Test
    @DisplayName("Data Change Detection: Should not detect change when all fields match")
    void testNoChangeWhenAllFieldsMatch() {
        // Given
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        entity.setDisplayName("Same Name");
        entity.setCity("Same City");
        entity.setCountry("Same Country");

        FormattableGeocodingResult freshResult = createTestResult("Same Name", "Same City", "Same Country");

        // When
        GeocodingCopyOnWriteHandler.ReconciliationResult result =
                handler.handleReconciliation(USER_ID, entity, freshResult);

        // Then
        assertFalse(result.changed(), "Should not detect change when all fields match");
    }

    // ==================== Helper Methods ====================

    private ReverseGeocodingLocationEntity createOriginalEntity() {
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setRequestCoordinates(coords);
        entity.setResultCoordinates(coords);
        entity.setDisplayName("Test Location");
        entity.setCity("Test City");
        entity.setCountry("Test Country");
        entity.setProviderName("test_provider");
        entity.setCreatedAt(Instant.now());
        entity.setLastAccessedAt(Instant.now());
        entity.setUser(null); // Original entity
        return entity;
    }

    private ReverseGeocodingLocationEntity createUserOwnedEntity(UUID userId) {
        ReverseGeocodingLocationEntity entity = createOriginalEntity();
        UserEntity user = new UserEntity();
        user.setId(userId);
        entity.setUser(user);
        return entity;
    }

    private ReverseGeocodingUpdateDTO createTestUpdateDTO() {
        return ReverseGeocodingUpdateDTO.builder()
                .displayName("Test Name")
                .city("Test City")
                .country("Test Country")
                .build();
    }

    private FormattableGeocodingResult createTestResult(String displayName, String city, String country) {
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        return SimpleFormattableResult.builder()
                .requestCoordinates(coords)
                .resultCoordinates(coords)
                .formattedDisplayName(displayName)
                .city(city)
                .country(country)
                .providerName("test_provider")
                .build();
    }
}
