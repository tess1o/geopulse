package org.github.tess1o.geopulse.geocoding.mapper;

import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingDTO;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReverseGeocodingDTOMapper.
 * Tests DTO conversion logic for API responses.
 *
 * Coverage: 8 test cases across 2 mapping methods
 */
@DisplayName("ReverseGeocodingDTOMapper Unit Tests")
class ReverseGeocodingDTOMapperTest {

    private ReverseGeocodingDTOMapper mapper;

    private static final double TEST_LAT = 40.7589;
    private static final double TEST_LON = -73.9851;
    private static final String TEST_DISPLAY_NAME = "Times Square";
    private static final String TEST_CITY = "New York";
    private static final String TEST_COUNTRY = "USA";
    private static final String TEST_PROVIDER = "nominatim";

    @BeforeEach
    void setUp() {
        mapper = new ReverseGeocodingDTOMapper();
    }

    // ==================== Test Suite 1: toDTO() ====================

    @Nested
    @DisplayName("toDTO() - Single Entity to DTO")
    class ToDTOTests {

        @Test
        @DisplayName("Should convert valid entity to DTO")
        void testConvertValidEntityToDTO() {
            // Given
            Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant lastAccessedAt = Instant.now();

            ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
            entity.setId(123L);
            entity.setRequestCoordinates(coords);
            entity.setDisplayName(TEST_DISPLAY_NAME);
            entity.setCity(TEST_CITY);
            entity.setCountry(TEST_COUNTRY);
            entity.setProviderName(TEST_PROVIDER);
            entity.setCreatedAt(createdAt);
            entity.setLastAccessedAt(lastAccessedAt);
            entity.setUser(null); // Original entity

            // When
            ReverseGeocodingDTO dto = mapper.toDTO(entity);

            // Then
            assertNotNull(dto);
            assertEquals(123L, dto.getId());
            assertEquals(TEST_LON, dto.getLongitude());
            assertEquals(TEST_LAT, dto.getLatitude());
            assertEquals(TEST_DISPLAY_NAME, dto.getDisplayName());
            assertEquals(TEST_CITY, dto.getCity());
            assertEquals(TEST_COUNTRY, dto.getCountry());
            assertEquals(TEST_PROVIDER, dto.getProviderName());
            assertEquals(createdAt, dto.getCreatedAt());
            assertEquals(lastAccessedAt, dto.getLastAccessedAt());
            assertFalse(dto.isUserSpecific(), "Should be false when user is null");
        }

        @Test
        @DisplayName("Should set isUserSpecific=false when user is null")
        void testIsUserSpecificFalseForOriginal() {
            // Given
            ReverseGeocodingLocationEntity entity = createTestEntity();
            entity.setUser(null); // Original entity

            // When
            ReverseGeocodingDTO dto = mapper.toDTO(entity);

            // Then
            assertNotNull(dto);
            assertFalse(dto.isUserSpecific(), "isUserSpecific should be false for original entities");
        }

        @Test
        @DisplayName("Should set isUserSpecific=true when user is set")
        void testIsUserSpecificTrueForUserCopy() {
            // Given
            ReverseGeocodingLocationEntity entity = createTestEntity();
            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            entity.setUser(user); // User-specific copy

            // When
            ReverseGeocodingDTO dto = mapper.toDTO(entity);

            // Then
            assertNotNull(dto);
            assertTrue(dto.isUserSpecific(), "isUserSpecific should be true for user-specific entities");
        }

        @Test
        @DisplayName("Should extract longitude from Point coordinates correctly")
        void testLongitudeExtraction() {
            // Given
            Point coords = GeoUtils.createPoint(-122.4194, 37.7749); // San Francisco
            ReverseGeocodingLocationEntity entity = createTestEntity();
            entity.setRequestCoordinates(coords);

            // When
            ReverseGeocodingDTO dto = mapper.toDTO(entity);

            // Then
            assertNotNull(dto);
            assertEquals(-122.4194, dto.getLongitude(), 0.0001);
        }

        @Test
        @DisplayName("Should extract latitude from Point coordinates correctly")
        void testLatitudeExtraction() {
            // Given
            Point coords = GeoUtils.createPoint(-122.4194, 37.7749); // San Francisco
            ReverseGeocodingLocationEntity entity = createTestEntity();
            entity.setRequestCoordinates(coords);

            // When
            ReverseGeocodingDTO dto = mapper.toDTO(entity);

            // Then
            assertNotNull(dto);
            assertEquals(37.7749, dto.getLatitude(), 0.0001);
        }

        @Test
        @DisplayName("Should return null when entity is null")
        void testConvertNullEntity() {
            // When
            ReverseGeocodingDTO dto = mapper.toDTO(null);

            // Then
            assertNull(dto);
        }

        @Test
        @DisplayName("Should preserve timestamps")
        void testTimestampsPreserved() {
            // Given
            Instant createdAt = Instant.parse("2023-01-15T10:30:00Z");
            Instant lastAccessedAt = Instant.parse("2023-06-20T14:45:30Z");

            ReverseGeocodingLocationEntity entity = createTestEntity();
            entity.setCreatedAt(createdAt);
            entity.setLastAccessedAt(lastAccessedAt);

            // When
            ReverseGeocodingDTO dto = mapper.toDTO(entity);

            // Then
            assertNotNull(dto);
            assertEquals(createdAt, dto.getCreatedAt());
            assertEquals(lastAccessedAt, dto.getLastAccessedAt());
        }
    }

    // ==================== Test Suite 2: toDTOList() ====================

    @Nested
    @DisplayName("toDTOList() - Batch Conversion")
    class ToDTOListTests {

        @Test
        @DisplayName("Should convert list of entities to DTOs")
        void testConvertListOfEntities() {
            // Given
            List<ReverseGeocodingLocationEntity> entities = new ArrayList<>();

            ReverseGeocodingLocationEntity entity1 = createTestEntity();
            entity1.setId(1L);
            entity1.setDisplayName("Location 1");
            entities.add(entity1);

            ReverseGeocodingLocationEntity entity2 = createTestEntity();
            entity2.setId(2L);
            entity2.setDisplayName("Location 2");
            entities.add(entity2);

            ReverseGeocodingLocationEntity entity3 = createTestEntity();
            entity3.setId(3L);
            entity3.setDisplayName("Location 3");
            entities.add(entity3);

            // When
            List<ReverseGeocodingDTO> dtos = mapper.toDTOList(entities);

            // Then
            assertNotNull(dtos);
            assertEquals(3, dtos.size());
            assertEquals(1L, dtos.get(0).getId());
            assertEquals("Location 1", dtos.get(0).getDisplayName());
            assertEquals(2L, dtos.get(1).getId());
            assertEquals("Location 2", dtos.get(1).getDisplayName());
            assertEquals(3L, dtos.get(2).getId());
            assertEquals("Location 3", dtos.get(2).getDisplayName());
        }

        @Test
        @DisplayName("Should return empty list when input is empty")
        void testConvertEmptyList() {
            // Given
            List<ReverseGeocodingLocationEntity> entities = new ArrayList<>();

            // When
            List<ReverseGeocodingDTO> dtos = mapper.toDTOList(entities);

            // Then
            assertNotNull(dtos);
            assertTrue(dtos.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when input is null")
        void testConvertNullList() {
            // When
            List<ReverseGeocodingDTO> dtos = mapper.toDTOList(null);

            // Then
            assertNotNull(dtos);
            assertTrue(dtos.isEmpty());
        }

        @Test
        @DisplayName("Should preserve order")
        void testOrderPreserved() {
            // Given
            List<ReverseGeocodingLocationEntity> entities = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                ReverseGeocodingLocationEntity entity = createTestEntity();
                entity.setId((long) i);
                entity.setDisplayName("Location " + i);
                entities.add(entity);
            }

            // When
            List<ReverseGeocodingDTO> dtos = mapper.toDTOList(entities);

            // Then
            assertNotNull(dtos);
            assertEquals(10, dtos.size());
            for (int i = 0; i < 10; i++) {
                assertEquals((long) i, dtos.get(i).getId());
                assertEquals("Location " + i, dtos.get(i).getDisplayName());
            }
        }

        @Test
        @DisplayName("Should match count of input entities")
        void testCountMatches() {
            // Given
            List<ReverseGeocodingLocationEntity> entities = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                ReverseGeocodingLocationEntity entity = createTestEntity();
                entity.setId((long) i);
                entities.add(entity);
            }

            // When
            List<ReverseGeocodingDTO> dtos = mapper.toDTOList(entities);

            // Then
            assertEquals(entities.size(), dtos.size());
        }

        @Test
        @DisplayName("Should handle mixed user-specific and original entities")
        void testMixedEntities() {
            // Given
            List<ReverseGeocodingLocationEntity> entities = new ArrayList<>();

            ReverseGeocodingLocationEntity original = createTestEntity();
            original.setId(1L);
            original.setUser(null); // Original
            entities.add(original);

            ReverseGeocodingLocationEntity userCopy = createTestEntity();
            userCopy.setId(2L);
            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            userCopy.setUser(user); // User copy
            entities.add(userCopy);

            // When
            List<ReverseGeocodingDTO> dtos = mapper.toDTOList(entities);

            // Then
            assertNotNull(dtos);
            assertEquals(2, dtos.size());
            assertFalse(dtos.get(0).isUserSpecific(), "First should be original");
            assertTrue(dtos.get(1).isUserSpecific(), "Second should be user-specific");
        }
    }

    // ==================== Helper Methods ====================

    private ReverseGeocodingLocationEntity createTestEntity() {
        Point coords = GeoUtils.createPoint(TEST_LON, TEST_LAT);
        Instant now = Instant.now();

        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setRequestCoordinates(coords);
        entity.setDisplayName(TEST_DISPLAY_NAME);
        entity.setCity(TEST_CITY);
        entity.setCountry(TEST_COUNTRY);
        entity.setProviderName(TEST_PROVIDER);
        entity.setCreatedAt(now.minusSeconds(3600));
        entity.setLastAccessedAt(now);
        return entity;
    }
}
