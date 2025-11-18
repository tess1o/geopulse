package org.github.tess1o.geopulse.geocoding.mapper;

import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GeocodingEntityMapper.
 * Tests all conversion methods with mocked EntityManager.
 *
 * Coverage: 20 test cases across 6 mapping methods
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GeocodingEntityMapper Unit Tests")
class GeocodingEntityMapperTest {

    @Mock
    private EntityManager entityManager;

    private GeocodingEntityMapper mapper;

    private static final double TEST_LAT = 40.7589;
    private static final double TEST_LON = -73.9851;
    private static final String TEST_DISPLAY_NAME = "Times Square";
    private static final String TEST_CITY = "New York";
    private static final String TEST_COUNTRY = "USA";
    private static final String TEST_PROVIDER = "nominatim";

    @BeforeEach
    void setUp() {
        mapper = new GeocodingEntityMapper(entityManager);
    }

    // ==================== Test Suite 1: toResult() ====================

    @Nested
    @DisplayName("toResult() - Entity to FormattableGeocodingResult Conversion")
    class ToResultTests {

        @Test
        @DisplayName("Should convert valid entity with all fields to result")
        void testConvertValidEntityWithAllFields() {
            // Given
            Point requestCoords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
            Point resultCoords = GeoUtils.createPoint(TEST_LON + 0.001, TEST_LAT + 0.001);
            Polygon boundingBox = createTestBoundingBox();

            ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
            entity.setRequestCoordinates(requestCoords);
            entity.setResultCoordinates(resultCoords);
            entity.setBoundingBox(boundingBox);
            entity.setDisplayName(TEST_DISPLAY_NAME);
            entity.setProviderName(TEST_PROVIDER);
            entity.setCity(TEST_CITY);
            entity.setCountry(TEST_COUNTRY);

            // When
            FormattableGeocodingResult result = mapper.toResult(entity);

            // Then
            assertNotNull(result);
            assertEquals(requestCoords, result.getRequestCoordinates());
            assertEquals(resultCoords, result.getResultCoordinates());
            assertEquals(boundingBox, result.getBoundingBox());
            assertEquals(TEST_DISPLAY_NAME, result.getFormattedDisplayName());
            assertEquals(TEST_PROVIDER, result.getProviderName());
            assertEquals(TEST_CITY, result.getCity());
            assertEquals(TEST_COUNTRY, result.getCountry());
        }

        @Test
        @DisplayName("Should convert entity with null optional fields")
        void testConvertEntityWithNullOptionalFields() {
            // Given
            Point requestCoords = GeoUtils.createPoint(TEST_LON, TEST_LAT);

            ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
            entity.setRequestCoordinates(requestCoords);
            entity.setResultCoordinates(requestCoords);
            entity.setDisplayName(TEST_DISPLAY_NAME);
            entity.setProviderName(TEST_PROVIDER);
            entity.setBoundingBox(null);
            entity.setCity(null);
            entity.setCountry(null);

            // When
            FormattableGeocodingResult result = mapper.toResult(entity);

            // Then
            assertNotNull(result);
            assertEquals(requestCoords, result.getRequestCoordinates());
            assertEquals(TEST_DISPLAY_NAME, result.getFormattedDisplayName());
            assertNull(result.getBoundingBox());
            assertNull(result.getCity());
            assertNull(result.getCountry());
        }

        @Test
        @DisplayName("Should return null when entity is null")
        void testConvertNullEntity() {
            // When
            FormattableGeocodingResult result = mapper.toResult(null);

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should correctly copy coordinates")
        void testCoordinatesAreCopiedCorrectly() {
            // Given
            Point requestCoords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
            Point resultCoords = GeoUtils.createPoint(TEST_LON + 0.01, TEST_LAT + 0.01);

            ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
            entity.setRequestCoordinates(requestCoords);
            entity.setResultCoordinates(resultCoords);
            entity.setDisplayName(TEST_DISPLAY_NAME);
            entity.setProviderName(TEST_PROVIDER);

            // When
            FormattableGeocodingResult result = mapper.toResult(entity);

            // Then
            assertNotNull(result);
            assertEquals(TEST_LON, result.getRequestCoordinates().getX(), 0.0001);
            assertEquals(TEST_LAT, result.getRequestCoordinates().getY(), 0.0001);
            assertEquals(TEST_LON + 0.01, result.getResultCoordinates().getX(), 0.0001);
            assertEquals(TEST_LAT + 0.01, result.getResultCoordinates().getY(), 0.0001);
        }
    }

    // ==================== Test Suite 2: toEntity() ====================

    @Nested
    @DisplayName("toEntity() - Result to Entity Conversion")
    class ToEntityTests {

        @Test
        @DisplayName("Should convert valid result to entity")
        void testConvertValidResultToEntity() {
            // Given
            Point requestCoords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
            Point resultCoords = GeoUtils.createPoint(TEST_LON + 0.001, TEST_LAT + 0.001);
            Polygon boundingBox = createTestBoundingBox();

            FormattableGeocodingResult result = SimpleFormattableResult.builder()
                    .requestCoordinates(requestCoords)
                    .resultCoordinates(resultCoords)
                    .boundingBox(boundingBox)
                    .formattedDisplayName(TEST_DISPLAY_NAME)
                    .providerName(TEST_PROVIDER)
                    .city(TEST_CITY)
                    .country(TEST_COUNTRY)
                    .build();

            // When
            ReverseGeocodingLocationEntity entity = mapper.toEntity(result);

            // Then
            assertNotNull(entity);
            assertEquals(requestCoords, entity.getRequestCoordinates());
            assertEquals(resultCoords, entity.getResultCoordinates());
            assertEquals(boundingBox, entity.getBoundingBox());
            assertEquals(TEST_DISPLAY_NAME, entity.getDisplayName());
            assertEquals(TEST_PROVIDER, entity.getProviderName());
            assertEquals(TEST_CITY, entity.getCity());
            assertEquals(TEST_COUNTRY, entity.getCountry());
        }

        @Test
        @DisplayName("Should return null when result is null")
        void testConvertNullResult() {
            // When
            ReverseGeocodingLocationEntity entity = mapper.toEntity(null);

            // Then
            assertNull(entity);
        }

        @Test
        @DisplayName("Should not set user - must be set by caller")
        void testUserIsNotSet() {
            // Given
            FormattableGeocodingResult result = createTestResult();

            // When
            ReverseGeocodingLocationEntity entity = mapper.toEntity(result);

            // Then
            assertNotNull(entity);
            assertNull(entity.getUser(), "User should not be set by mapper");
        }

        @Test
        @DisplayName("Should not set timestamps - must be set by caller")
        void testTimestampsAreNotSet() {
            // Given
            FormattableGeocodingResult result = createTestResult();

            // When
            ReverseGeocodingLocationEntity entity = mapper.toEntity(result);

            // Then
            assertNotNull(entity);
            assertNull(entity.getCreatedAt(), "CreatedAt should not be set by mapper");
            assertNull(entity.getLastAccessedAt(), "LastAccessedAt should not be set by mapper");
        }

        @Test
        @DisplayName("Should not set ID - must be auto-generated")
        void testIdIsNotSet() {
            // Given
            FormattableGeocodingResult result = createTestResult();

            // When
            ReverseGeocodingLocationEntity entity = mapper.toEntity(result);

            // Then
            assertNotNull(entity);
            assertNull(entity.getId(), "ID should not be set by mapper (auto-generated)");
        }
    }

    // ==================== Test Suite 3: createUserCopy() ====================

    @Nested
    @DisplayName("createUserCopy() - Create User-Specific Copy from Result")
    class CreateUserCopyTests {

        @Test
        @DisplayName("Should create user copy with valid inputs")
        void testCreateUserCopyWithValidInputs() {
            // Given
            UUID userId = UUID.randomUUID();
            UserEntity mockUser = new UserEntity();
            when(entityManager.getReference(UserEntity.class, userId)).thenReturn(mockUser);

            Point requestCoords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
            Point resultCoords = GeoUtils.createPoint(TEST_LON + 0.001, TEST_LAT + 0.001);

            ReverseGeocodingLocationEntity original = new ReverseGeocodingLocationEntity();
            original.setRequestCoordinates(requestCoords);
            original.setResultCoordinates(requestCoords);
            original.setProviderName("old_provider");

            FormattableGeocodingResult freshResult = SimpleFormattableResult.builder()
                    .requestCoordinates(requestCoords)
                    .resultCoordinates(resultCoords)
                    .formattedDisplayName(TEST_DISPLAY_NAME)
                    .providerName(TEST_PROVIDER)
                    .city(TEST_CITY)
                    .country(TEST_COUNTRY)
                    .build();

            // When
            ReverseGeocodingLocationEntity userCopy = mapper.createUserCopy(userId, original, freshResult);

            // Then
            assertNotNull(userCopy);
            assertEquals(mockUser, userCopy.getUser());
            assertEquals(requestCoords, userCopy.getRequestCoordinates());
            assertEquals(resultCoords, userCopy.getResultCoordinates());
            assertEquals(TEST_DISPLAY_NAME, userCopy.getDisplayName());
            assertEquals(TEST_PROVIDER, userCopy.getProviderName());
            assertEquals(TEST_CITY, userCopy.getCity());
            assertEquals(TEST_COUNTRY, userCopy.getCountry());

            verify(entityManager).getReference(UserEntity.class, userId);
        }

        @Test
        @DisplayName("Should set user reference correctly")
        void testUserReferenceIsSet() {
            // Given
            UUID userId = UUID.randomUUID();
            UserEntity mockUser = new UserEntity();
            when(entityManager.getReference(UserEntity.class, userId)).thenReturn(mockUser);

            ReverseGeocodingLocationEntity original = createTestEntity();
            FormattableGeocodingResult freshResult = createTestResult();

            // When
            ReverseGeocodingLocationEntity userCopy = mapper.createUserCopy(userId, original, freshResult);

            // Then
            assertNotNull(userCopy.getUser());
            assertEquals(mockUser, userCopy.getUser());
        }

        @Test
        @DisplayName("Should auto-generate timestamps")
        void testTimestampsAutoGenerated() {
            // Given
            UUID userId = UUID.randomUUID();
            UserEntity mockUser = new UserEntity();
            when(entityManager.getReference(UserEntity.class, userId)).thenReturn(mockUser);

            ReverseGeocodingLocationEntity original = createTestEntity();
            FormattableGeocodingResult freshResult = createTestResult();

            Instant beforeCreation = Instant.now();

            // When
            ReverseGeocodingLocationEntity userCopy = mapper.createUserCopy(userId, original, freshResult);

            Instant afterCreation = Instant.now();

            // Then
            assertNotNull(userCopy.getCreatedAt());
            assertNotNull(userCopy.getLastAccessedAt());
            assertTrue(userCopy.getCreatedAt().isAfter(beforeCreation.minusSeconds(1)));
            assertTrue(userCopy.getCreatedAt().isBefore(afterCreation.plusSeconds(1)));
            assertEquals(userCopy.getCreatedAt(), userCopy.getLastAccessedAt());
        }

        @Test
        @DisplayName("Should copy spatial data from original")
        void testSpatialDataCopiedFromOriginal() {
            // Given
            UUID userId = UUID.randomUUID();
            UserEntity mockUser = new UserEntity();
            when(entityManager.getReference(UserEntity.class, userId)).thenReturn(mockUser);

            Point originalRequestCoords = GeoUtils.createPoint(TEST_LON, TEST_LAT);

            ReverseGeocodingLocationEntity original = new ReverseGeocodingLocationEntity();
            original.setRequestCoordinates(originalRequestCoords);
            original.setResultCoordinates(originalRequestCoords);

            Point newResultCoords = GeoUtils.createPoint(TEST_LON + 0.01, TEST_LAT + 0.01);
            FormattableGeocodingResult freshResult = SimpleFormattableResult.builder()
                    .requestCoordinates(GeoUtils.createPoint(11.0, 11.0)) // Different
                    .resultCoordinates(newResultCoords)
                    .formattedDisplayName(TEST_DISPLAY_NAME)
                    .providerName(TEST_PROVIDER)
                    .build();

            // When
            ReverseGeocodingLocationEntity userCopy = mapper.createUserCopy(userId, original, freshResult);

            // Then
            assertEquals(originalRequestCoords, userCopy.getRequestCoordinates(),
                    "Request coordinates should come from original");
            assertEquals(newResultCoords, userCopy.getResultCoordinates(),
                    "Result coordinates should come from fresh result");
        }
    }

    // ==================== Test Suite 4: createUserCopyWithValues() ====================

    @Nested
    @DisplayName("createUserCopyWithValues() - Create User Copy with Manual Values")
    class CreateUserCopyWithValuesTests {

        @Test
        @DisplayName("Should create copy with all manual values")
        void testCreateCopyWithManualValues() {
            // Given
            UUID userId = UUID.randomUUID();
            UserEntity mockUser = new UserEntity();
            when(entityManager.getReference(UserEntity.class, userId)).thenReturn(mockUser);

            ReverseGeocodingLocationEntity original = createTestEntity();

            String manualDisplayName = "Custom Location Name";
            String manualCity = "Custom City";
            String manualCountry = "Custom Country";

            // When
            ReverseGeocodingLocationEntity userCopy = mapper.createUserCopyWithValues(
                    userId, original, manualDisplayName, manualCity, manualCountry);

            // Then
            assertNotNull(userCopy);
            assertEquals(mockUser, userCopy.getUser());
            assertEquals(manualDisplayName, userCopy.getDisplayName());
            assertEquals(manualCity, userCopy.getCity());
            assertEquals(manualCountry, userCopy.getCountry());
        }

        @Test
        @DisplayName("Should copy spatial data from original")
        void testSpatialDataFromOriginal() {
            // Given
            UUID userId = UUID.randomUUID();
            UserEntity mockUser = new UserEntity();
            when(entityManager.getReference(UserEntity.class, userId)).thenReturn(mockUser);

            Point requestCoords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
            Point resultCoords = GeoUtils.createPoint(TEST_LON + 0.001, TEST_LAT + 0.001);
            Polygon boundingBox = createTestBoundingBox();

            ReverseGeocodingLocationEntity original = new ReverseGeocodingLocationEntity();
            original.setRequestCoordinates(requestCoords);
            original.setResultCoordinates(resultCoords);
            original.setBoundingBox(boundingBox);
            original.setProviderName(TEST_PROVIDER);

            // When
            ReverseGeocodingLocationEntity userCopy = mapper.createUserCopyWithValues(
                    userId, original, TEST_DISPLAY_NAME, TEST_CITY, TEST_COUNTRY);

            // Then
            assertEquals(requestCoords, userCopy.getRequestCoordinates());
            assertEquals(resultCoords, userCopy.getResultCoordinates());
            assertEquals(boundingBox, userCopy.getBoundingBox());
            assertEquals(TEST_PROVIDER, userCopy.getProviderName());
        }

        @Test
        @DisplayName("Should auto-generate timestamps")
        void testTimestampsAutoGenerated() {
            // Given
            UUID userId = UUID.randomUUID();
            UserEntity mockUser = new UserEntity();
            when(entityManager.getReference(UserEntity.class, userId)).thenReturn(mockUser);

            ReverseGeocodingLocationEntity original = createTestEntity();

            Instant beforeCreation = Instant.now();

            // When
            ReverseGeocodingLocationEntity userCopy = mapper.createUserCopyWithValues(
                    userId, original, TEST_DISPLAY_NAME, TEST_CITY, TEST_COUNTRY);

            Instant afterCreation = Instant.now();

            // Then
            assertNotNull(userCopy.getCreatedAt());
            assertNotNull(userCopy.getLastAccessedAt());
            assertTrue(userCopy.getCreatedAt().isAfter(beforeCreation.minusSeconds(1)));
            assertTrue(userCopy.getCreatedAt().isBefore(afterCreation.plusSeconds(1)));
            assertEquals(userCopy.getCreatedAt(), userCopy.getLastAccessedAt());
        }

        @Test
        @DisplayName("Should handle null displayName")
        void testHandleNullDisplayName() {
            // Given
            UUID userId = UUID.randomUUID();
            UserEntity mockUser = new UserEntity();
            when(entityManager.getReference(UserEntity.class, userId)).thenReturn(mockUser);

            ReverseGeocodingLocationEntity original = createTestEntity();

            // When
            ReverseGeocodingLocationEntity userCopy = mapper.createUserCopyWithValues(
                    userId, original, null, TEST_CITY, TEST_COUNTRY);

            // Then
            assertNotNull(userCopy);
            assertNull(userCopy.getDisplayName());
            assertEquals(TEST_CITY, userCopy.getCity());
            assertEquals(TEST_COUNTRY, userCopy.getCountry());
        }
    }

    // ==================== Test Suite 5: updateEntityFromResult() ====================

    @Nested
    @DisplayName("updateEntityFromResult() - Update Entity with Result Data")
    class UpdateEntityFromResultTests {

        @Test
        @DisplayName("Should update existing entity with fresh result")
        void testUpdateEntityWithFreshResult() {
            // Given
            ReverseGeocodingLocationEntity entity = createTestEntity();
            entity.setId(1L);
            UUID userId = UUID.randomUUID();
            Instant originalCreatedAt = Instant.now().minusSeconds(3600);
            entity.setCreatedAt(originalCreatedAt);
            entity.setLastAccessedAt(originalCreatedAt);

            Point newResultCoords = GeoUtils.createPoint(TEST_LON + 0.01, TEST_LAT + 0.01);
            FormattableGeocodingResult freshResult = SimpleFormattableResult.builder()
                    .resultCoordinates(newResultCoords)
                    .formattedDisplayName("Updated Name")
                    .providerName("new_provider")
                    .city("Updated City")
                    .country("Updated Country")
                    .build();

            Instant beforeUpdate = Instant.now();

            // When
            mapper.updateEntityFromResult(entity, freshResult);

            Instant afterUpdate = Instant.now();

            // Then
            assertEquals(newResultCoords, entity.getResultCoordinates());
            assertEquals("Updated Name", entity.getDisplayName());
            assertEquals("new_provider", entity.getProviderName());
            assertEquals("Updated City", entity.getCity());
            assertEquals("Updated Country", entity.getCountry());
            assertTrue(entity.getLastAccessedAt().isAfter(beforeUpdate.minusSeconds(1)));
            assertTrue(entity.getLastAccessedAt().isBefore(afterUpdate.plusSeconds(1)));
        }

        @Test
        @DisplayName("Should update lastAccessedAt timestamp")
        void testLastAccessedAtUpdated() {
            // Given
            ReverseGeocodingLocationEntity entity = createTestEntity();
            Instant oldTimestamp = Instant.now().minusSeconds(3600);
            entity.setLastAccessedAt(oldTimestamp);

            FormattableGeocodingResult freshResult = createTestResult();

            // When
            mapper.updateEntityFromResult(entity, freshResult);

            // Then
            assertNotNull(entity.getLastAccessedAt());
            assertTrue(entity.getLastAccessedAt().isAfter(oldTimestamp));
        }

        @Test
        @DisplayName("Should not change immutable fields")
        void testImmutableFieldsNotChanged() {
            // Given
            ReverseGeocodingLocationEntity entity = createTestEntity();
            entity.setId(999L);
            Instant originalCreatedAt = Instant.now().minusSeconds(3600);
            entity.setCreatedAt(originalCreatedAt);

            FormattableGeocodingResult freshResult = createTestResult();

            // When
            mapper.updateEntityFromResult(entity, freshResult);

            // Then
            assertEquals(999L, entity.getId(), "ID should not change");
            assertEquals(originalCreatedAt, entity.getCreatedAt(), "CreatedAt should not change");
        }

        @Test
        @DisplayName("Should handle null entity gracefully")
        void testHandleNullEntity() {
            // Given
            FormattableGeocodingResult freshResult = createTestResult();

            // When / Then
            assertDoesNotThrow(() -> mapper.updateEntityFromResult(null, freshResult));
        }

        @Test
        @DisplayName("Should handle null result gracefully")
        void testHandleNullResult() {
            // Given
            ReverseGeocodingLocationEntity entity = createTestEntity();

            // When / Then
            assertDoesNotThrow(() -> mapper.updateEntityFromResult(entity, null));
        }
    }

    // ==================== Test Suite 6: updateEntityWithValues() ====================

    @Nested
    @DisplayName("updateEntityWithValues() - Update Entity with Manual Values")
    class UpdateEntityWithValuesTests {

        @Test
        @DisplayName("Should update entity with manual values")
        void testUpdateWithManualValues() {
            // Given
            ReverseGeocodingLocationEntity entity = createTestEntity();
            entity.setDisplayName("Old Name");
            entity.setCity("Old City");
            entity.setCountry("Old Country");

            String newDisplayName = "New Name";
            String newCity = "New City";
            String newCountry = "New Country";

            Instant beforeUpdate = Instant.now();

            // When
            mapper.updateEntityWithValues(entity, newDisplayName, newCity, newCountry);

            Instant afterUpdate = Instant.now();

            // Then
            assertEquals(newDisplayName, entity.getDisplayName());
            assertEquals(newCity, entity.getCity());
            assertEquals(newCountry, entity.getCountry());
            assertTrue(entity.getLastAccessedAt().isAfter(beforeUpdate.minusSeconds(1)));
            assertTrue(entity.getLastAccessedAt().isBefore(afterUpdate.plusSeconds(1)));
        }

        @Test
        @DisplayName("Should update lastAccessedAt")
        void testLastAccessedAtUpdated() {
            // Given
            ReverseGeocodingLocationEntity entity = createTestEntity();
            Instant oldTimestamp = Instant.now().minusSeconds(3600);
            entity.setLastAccessedAt(oldTimestamp);

            // When
            mapper.updateEntityWithValues(entity, TEST_DISPLAY_NAME, TEST_CITY, TEST_COUNTRY);

            // Then
            assertNotNull(entity.getLastAccessedAt());
            assertTrue(entity.getLastAccessedAt().isAfter(oldTimestamp));
        }

        @Test
        @DisplayName("Should not change other fields")
        void testOtherFieldsUnchanged() {
            // Given
            Point originalCoords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
            ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
            entity.setRequestCoordinates(originalCoords);
            entity.setResultCoordinates(originalCoords);
            entity.setProviderName(TEST_PROVIDER);
            entity.setDisplayName("Old Name");

            // When
            mapper.updateEntityWithValues(entity, "New Name", "New City", "New Country");

            // Then
            assertEquals(originalCoords, entity.getRequestCoordinates(), "Request coords should not change");
            assertEquals(originalCoords, entity.getResultCoordinates(), "Result coords should not change");
            assertEquals(TEST_PROVIDER, entity.getProviderName(), "Provider should not change");
        }

        @Test
        @DisplayName("Should handle null entity gracefully")
        void testHandleNullEntity() {
            // When / Then
            assertDoesNotThrow(() -> mapper.updateEntityWithValues(null, TEST_DISPLAY_NAME, TEST_CITY, TEST_COUNTRY));
        }

        @Test
        @DisplayName("Should handle null values")
        void testHandleNullValues() {
            // Given
            ReverseGeocodingLocationEntity entity = createTestEntity();

            // When
            mapper.updateEntityWithValues(entity, null, null, null);

            // Then
            assertNull(entity.getDisplayName());
            assertNull(entity.getCity());
            assertNull(entity.getCountry());
        }
    }

    // ==================== Helper Methods ====================

    private ReverseGeocodingLocationEntity createTestEntity() {
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setRequestCoordinates(coords);
        entity.setResultCoordinates(coords);
        entity.setDisplayName(TEST_DISPLAY_NAME);
        entity.setProviderName(TEST_PROVIDER);
        entity.setCity(TEST_CITY);
        entity.setCountry(TEST_COUNTRY);
        return entity;
    }

    private FormattableGeocodingResult createTestResult() {
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        return SimpleFormattableResult.builder()
                .requestCoordinates(coords)
                .resultCoordinates(coords)
                .formattedDisplayName(TEST_DISPLAY_NAME)
                .providerName(TEST_PROVIDER)
                .city(TEST_CITY)
                .country(TEST_COUNTRY)
                .build();
    }

    private Polygon createTestBoundingBox() {
        return GeoUtils.createRectangleFromLeafletBounds(
                TEST_LAT + 0.01,  // north
                TEST_LON + 0.01,  // east
                TEST_LAT - 0.01,  // south
                TEST_LON - 0.01   // west
        );
    }
}
