package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.importdata.mapper.ImportDataMapper;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.IMPORT_JSON;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.defaultInstant;

/**
 * GeoPulse import of favorites and reverse geocoding results, matched against the user's existing ones.
 */
@ApplicationScoped
@Slf4j
public class GeoPulsePlacesImporter {

    @Inject
    UserRepository userRepository;

    @Inject
    ImportDataMapper importDataMapper;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    EntityManager entityManager;

    public void importReverseGeocodingData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        try {
            QuarkusTransaction.requiringNew()
                    .call(() -> {
                        importReverseGeocodingDataInTransaction(content, job, referenceMaps);
                        return null;
                    });
        } catch (QuarkusTransactionException e) {
            throw ImportTransactionExceptions.unwrapIOExceptionOrThrowRuntime(e);
        }
    }

    /**
     * Imports geocoding rows into the caller's transaction. The debug import uses it so the whole import stays one
     * all-or-nothing transaction.
     */
    void importReverseGeocodingDataInTransaction(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        ReverseGeocodingDataDto geocodingData = IMPORT_JSON.readValue(content, ReverseGeocodingDataDto.class);
        log.info("Importing {} reverse geocoding locations for user {} with user assignment logic",
                geocodingData.getLocations().size(), job.getUserId());

        UserEntity importingUser = userRepository.findById(job.getUserId());
        if (importingUser == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        int imported = 0;
        int skipped = 0;

        for (ReverseGeocodingDataDto.ReverseGeocodingLocationDto locationDto : geocodingData.getLocations()) {
            try {
                if (job.getOptions().isSnapshotRestore()) {
                    if (restoreReverseGeocodingSnapshotLocation(locationDto, job.getUserId(), referenceMaps)) {
                        imported++;
                    } else {
                        skipped++;
                    }
                    continue;
                }

                // Create geometry objects for coordinates and bounding box
                Point requestCoordinates = null;
                if (locationDto.getRequestLatitude() != null && locationDto.getRequestLongitude() != null) {
                    requestCoordinates = GeoUtils.createPoint(
                            locationDto.getRequestLongitude(), locationDto.getRequestLatitude());
                }

                Point resultCoordinates = null;
                if (locationDto.getResultLatitude() != null && locationDto.getResultLongitude() != null) {
                    resultCoordinates = GeoUtils.createPoint(
                            locationDto.getResultLongitude(), locationDto.getResultLatitude());
                }

                Polygon boundingBox = null;
                if (locationDto.getBoundingBoxNorthEastLatitude() != null &&
                        locationDto.getBoundingBoxNorthEastLongitude() != null &&
                        locationDto.getBoundingBoxSouthWestLatitude() != null &&
                        locationDto.getBoundingBoxSouthWestLongitude() != null) {
                    boundingBox = GeoUtils.buildBoundingBoxPolygon(
                            locationDto.getBoundingBoxSouthWestLatitude(),
                            locationDto.getBoundingBoxNorthEastLatitude(),
                            locationDto.getBoundingBoxSouthWestLongitude(),
                            locationDto.getBoundingBoxNorthEastLongitude()
                    );
                }

                ReverseGeocodingLocationEntity existing = findExistingUserGeocoding(job.getUserId(), requestCoordinates);
                ReverseGeocodingLocationEntity entity = existing == null ? new ReverseGeocodingLocationEntity() : existing;

                entity.setUser(importingUser);
                entity.setRequestCoordinates(requestCoordinates);
                entity.setResultCoordinates(resultCoordinates);
                entity.setBoundingBox(boundingBox);
                entity.setDisplayName(locationDto.getDisplayName());
                entity.setProviderName(locationDto.getProviderName());
                entity.setCreatedAt(defaultInstant(locationDto.getCreatedAt()));
                entity.setLastAccessedAt(defaultInstant(locationDto.getLastAccessedAt()));
                entity.setCity(locationDto.getCity());
                entity.setCountry(locationDto.getCountry());
                entityManager.persist(entity);
                entityManager.flush();
                referenceMaps.geocodingIds.put(locationDto.getId(), entity.getId());

                if (existing == null) {
                    imported++;
                    log.debug("Imported user-specific geocoding entity (assigned to user {})", job.getUserId());
                } else {
                    skipped++;
                    log.debug("Updated existing user-specific geocoding entity {}", existing.getId());
                }

            } catch (Exception e) {
                log.warn("Failed to import reverse geocoding location at ({}, {}): {}",
                        locationDto.getRequestLatitude(), locationDto.getRequestLongitude(), e.getMessage());
            }
        }

        log.info("Successfully imported {} reverse geocoding locations (skipped {} existing originals)",
                imported, skipped);
    }

    private boolean restoreReverseGeocodingSnapshotLocation(ReverseGeocodingDataDto.ReverseGeocodingLocationDto locationDto,
                                                            UUID userId,
                                                            ImportReferenceMaps referenceMaps) {
        if (locationDto.getId() <= 0
                || locationDto.getRequestLatitude() == null
                || locationDto.getRequestLongitude() == null
                || locationDto.getDisplayName() == null
                || locationDto.getProviderName() == null) {
            return false;
        }

        entityManager.createNativeQuery("""
                        INSERT INTO reverse_geocoding_location (
                            id, user_id, request_coordinates, result_coordinates, bounding_box,
                            display_name, provider_name, created_at, last_accessed_at, city, country
                        ) VALUES (
                            :id,
                            :userId,
                            ST_SetSRID(ST_MakePoint(
                                CAST(:requestLongitude AS double precision),
                                CAST(:requestLatitude AS double precision)
                            ), 4326),
                            CASE
                                WHEN CAST(:resultLongitude AS double precision) IS NULL
                                  OR CAST(:resultLatitude AS double precision) IS NULL
                                THEN NULL
                                ELSE ST_SetSRID(ST_MakePoint(
                                    CAST(:resultLongitude AS double precision),
                                    CAST(:resultLatitude AS double precision)
                                ), 4326)
                            END,
                            CASE
                                WHEN CAST(:southWestLongitude AS double precision) IS NULL
                                  OR CAST(:southWestLatitude AS double precision) IS NULL
                                  OR CAST(:northEastLongitude AS double precision) IS NULL
                                  OR CAST(:northEastLatitude AS double precision) IS NULL
                                THEN NULL
                                ELSE ST_MakeEnvelope(
                                    CAST(:southWestLongitude AS double precision),
                                    CAST(:southWestLatitude AS double precision),
                                    CAST(:northEastLongitude AS double precision),
                                    CAST(:northEastLatitude AS double precision),
                                    4326
                                )
                            END,
                            :displayName,
                            :providerName,
                            :createdAt,
                            :lastAccessedAt,
                            :city,
                            :country
                        )
                        ON CONFLICT (id) DO UPDATE SET
                            user_id = EXCLUDED.user_id,
                            request_coordinates = EXCLUDED.request_coordinates,
                            result_coordinates = EXCLUDED.result_coordinates,
                            bounding_box = EXCLUDED.bounding_box,
                            display_name = EXCLUDED.display_name,
                            provider_name = EXCLUDED.provider_name,
                            created_at = EXCLUDED.created_at,
                            last_accessed_at = EXCLUDED.last_accessed_at,
                            city = EXCLUDED.city,
                            country = EXCLUDED.country
                        """)
                .setParameter("id", locationDto.getId())
                .setParameter("userId", userId)
                .setParameter("requestLongitude", locationDto.getRequestLongitude())
                .setParameter("requestLatitude", locationDto.getRequestLatitude())
                .setParameter("resultLongitude", locationDto.getResultLongitude())
                .setParameter("resultLatitude", locationDto.getResultLatitude())
                .setParameter("southWestLongitude", locationDto.getBoundingBoxSouthWestLongitude())
                .setParameter("southWestLatitude", locationDto.getBoundingBoxSouthWestLatitude())
                .setParameter("northEastLongitude", locationDto.getBoundingBoxNorthEastLongitude())
                .setParameter("northEastLatitude", locationDto.getBoundingBoxNorthEastLatitude())
                .setParameter("displayName", locationDto.getDisplayName())
                .setParameter("providerName", locationDto.getProviderName())
                .setParameter("createdAt", defaultInstant(locationDto.getCreatedAt()))
                .setParameter("lastAccessedAt", defaultInstant(locationDto.getLastAccessedAt()))
                .setParameter("city", locationDto.getCity())
                .setParameter("country", locationDto.getCountry())
                .executeUpdate();
        referenceMaps.geocodingIds.put(locationDto.getId(), locationDto.getId());
        return true;
    }

    private ReverseGeocodingLocationEntity findExistingUserGeocoding(UUID userId, Point requestCoordinates) {
        if (requestCoordinates == null) {
            return null;
        }
        return entityManager.createQuery("""
                        SELECT location FROM ReverseGeocodingLocationEntity location
                        WHERE location.user.id = :userId
                          AND location.requestCoordinates = :requestCoordinates
                        """, ReverseGeocodingLocationEntity.class)
                .setParameter("userId", userId)
                .setParameter("requestCoordinates", requestCoordinates)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void importFavoritesData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        FavoritesDataDto favoritesData = IMPORT_JSON.readValue(content, FavoritesDataDto.class);
        log.info("Importing {} favorite points and {} favorite areas for user {} using duplicate detection",
                favoritesData.getPoints().size(), favoritesData.getAreas().size(), job.getUserId());

        UserEntity user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        int importedFavorites = 0;
        int skippedFavorites = 0;

        // Import favorite points with duplicate detection
        for (FavoritesDataDto.FavoritePointDto pointDto : favoritesData.getPoints()) {
            try {
                // Create Point geometry
                Point geometry = importDataMapper.createPointFromCoordinates(
                        pointDto.getLongitude(), pointDto.getLatitude());

                // Check for duplicates by user + name + location
                List<FavoritesEntity> duplicates = favoritesRepository.findByUserAndNameAndLocation(
                        job.getUserId(), pointDto.getName(), geometry);

                if (duplicates.isEmpty()) {
                    // Create new favorite entity
                    FavoritesEntity favorite = new FavoritesEntity();
                    favorite.setUser(user);
                    favorite.setName(pointDto.getName());
                    favorite.setCity(pointDto.getCity());
                    favorite.setCountry(pointDto.getCountry());
                    favorite.setType(FavoriteLocationType.POINT);
                    favorite.setGeometry(geometry);
                    favorite.setMergeImpact(false);

                    favoritesRepository.persist(favorite);
                    entityManager.flush();
                    referenceMaps.favoriteIds.put(pointDto.getId(), favorite.getId());
                    importedFavorites++;
                } else {
                    // Update existing favorite with potentially better data
                    FavoritesEntity existing = duplicates.get(0);
                    updateFavoriteIfNecessary(existing, pointDto.getCity(), pointDto.getCountry());
                    referenceMaps.favoriteIds.put(pointDto.getId(), existing.getId());
                    skippedFavorites++;
                }
            } catch (Exception e) {
                log.warn("Failed to import favorite point '{}': {}", pointDto.getName(), e.getMessage());
            }
        }

        // Import favorite areas with duplicate detection
        for (FavoritesDataDto.FavoriteAreaDto areaDto : favoritesData.getAreas()) {
            try {
                // Create Polygon geometry
                Polygon geometry = importDataMapper.createPolygonFromCoordinates(areaDto);
                // Convert to Point for duplicate detection (use centroid)
                Point centroid = geometry.getCentroid();

                // Check for duplicates by user + name + location (centroid)
                List<FavoritesEntity> duplicates = favoritesRepository.findByUserAndNameAndLocation(
                        job.getUserId(), areaDto.getName(), centroid);

                if (duplicates.isEmpty()) {
                    // Create new favorite entity
                    FavoritesEntity favorite = new FavoritesEntity();
                    favorite.setUser(user);
                    favorite.setName(areaDto.getName());
                    favorite.setCity(areaDto.getCity());
                    favorite.setCountry(areaDto.getCountry());
                    favorite.setType(FavoriteLocationType.AREA);
                    favorite.setGeometry(geometry);
                    favorite.setMergeImpact(false);

                    favoritesRepository.persist(favorite);
                    entityManager.flush();
                    referenceMaps.favoriteIds.put(areaDto.getId(), favorite.getId());
                    importedFavorites++;
                } else {
                    // Update existing favorite with potentially better data
                    FavoritesEntity existing = duplicates.get(0);
                    updateFavoriteIfNecessary(existing, areaDto.getCity(), areaDto.getCountry());
                    referenceMaps.favoriteIds.put(areaDto.getId(), existing.getId());
                    skippedFavorites++;
                }
            } catch (Exception e) {
                log.warn("Failed to import favorite area '{}': {}", areaDto.getName(), e.getMessage());
            }
        }

        log.info("Successfully imported {} favorites using duplicate detection (skipped {} duplicates)",
                importedFavorites, skippedFavorites);
    }

    /**
     * Update existing favorite with better data if available
     */
    private void updateFavoriteIfNecessary(FavoritesEntity existing, String newCity, String newCountry) {
        boolean updated = false;

        // Update city if current is null and new has value
        if (newCity != null && existing.getCity() == null) {
            existing.setCity(newCity);
            updated = true;
        }

        // Update country if current is null and new has value
        if (newCountry != null && existing.getCountry() == null) {
            existing.setCountry(newCountry);
            updated = true;
        }

        if (updated) {
            favoritesRepository.persist(existing);
        }
    }
}
