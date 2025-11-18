package org.github.tess1o.geopulse.geocoding.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapper for converting between ReverseGeocodingLocationEntity and FormattableGeocodingResult.
 * Centralizes all conversion logic for geocoding entities.
 */
@ApplicationScoped
public class GeocodingEntityMapper {

    private final EntityManager entityManager;

    @Inject
    public GeocodingEntityMapper(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Convert entity to FormattableGeocodingResult.
     */
    public FormattableGeocodingResult toResult(ReverseGeocodingLocationEntity entity) {
        if (entity == null) {
            return null;
        }

        return SimpleFormattableResult.builder()
                .requestCoordinates(entity.getRequestCoordinates())
                .resultCoordinates(entity.getResultCoordinates())
                .boundingBox(entity.getBoundingBox())
                .formattedDisplayName(entity.getDisplayName())
                .providerName(entity.getProviderName())
                .city(entity.getCity())
                .country(entity.getCountry())
                .build();
    }

    /**
     * Convert FormattableGeocodingResult to entity.
     * Note: Does not set user, timestamps, or ID - caller must set these.
     */
    public ReverseGeocodingLocationEntity toEntity(FormattableGeocodingResult result) {
        if (result == null) {
            return null;
        }

        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setRequestCoordinates(result.getRequestCoordinates());
        entity.setResultCoordinates(result.getResultCoordinates());
        entity.setBoundingBox(result.getBoundingBox());
        entity.setDisplayName(result.getFormattedDisplayName());
        entity.setProviderName(result.getProviderName());
        entity.setCity(result.getCity());
        entity.setCountry(result.getCountry());
        return entity;
    }

    /**
     * Create user-specific copy from original entity and fresh geocoding result.
     */
    public ReverseGeocodingLocationEntity createUserCopy(
            UUID userId,
            ReverseGeocodingLocationEntity original,
            FormattableGeocodingResult freshResult) {

        ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();

        UserEntity user = entityManager.getReference(UserEntity.class, userId);
        userCopy.setUser(user);

        // Copy spatial data from original
        userCopy.setRequestCoordinates(original.getRequestCoordinates());
        userCopy.setResultCoordinates(freshResult.getResultCoordinates());
        userCopy.setBoundingBox(freshResult.getBoundingBox());
        userCopy.setProviderName(freshResult.getProviderName());

        // Set new data from result
        userCopy.setDisplayName(freshResult.getFormattedDisplayName());
        userCopy.setCity(freshResult.getCity());
        userCopy.setCountry(freshResult.getCountry());

        // Set timestamps
        Instant now = Instant.now();
        userCopy.setCreatedAt(now);
        userCopy.setLastAccessedAt(now);

        return userCopy;
    }

    /**
     * Create user-specific copy from original entity with modified values.
     * Used for manual user edits (not from provider).
     */
    public ReverseGeocodingLocationEntity createUserCopyWithValues(
            UUID userId,
            ReverseGeocodingLocationEntity original,
            String displayName,
            String city,
            String country) {

        ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();

        UserEntity user = entityManager.getReference(UserEntity.class, userId);
        userCopy.setUser(user);

        // Copy spatial data from original
        userCopy.setRequestCoordinates(original.getRequestCoordinates());
        userCopy.setResultCoordinates(original.getResultCoordinates());
        userCopy.setBoundingBox(original.getBoundingBox());
        userCopy.setProviderName(original.getProviderName());

        // Set modified values
        userCopy.setDisplayName(displayName);
        userCopy.setCity(city);
        userCopy.setCountry(country);

        // Set timestamps
        Instant now = Instant.now();
        userCopy.setCreatedAt(now);
        userCopy.setLastAccessedAt(now);

        return userCopy;
    }

    /**
     * Update entity with values from FormattableGeocodingResult.
     */
    public void updateEntityFromResult(ReverseGeocodingLocationEntity entity, FormattableGeocodingResult result) {
        if (entity == null || result == null) {
            return;
        }

        entity.setResultCoordinates(result.getResultCoordinates());
        entity.setBoundingBox(result.getBoundingBox());
        entity.setDisplayName(result.getFormattedDisplayName());
        entity.setProviderName(result.getProviderName());
        entity.setCity(result.getCity());
        entity.setCountry(result.getCountry());
        entity.setLastAccessedAt(Instant.now());
    }

    /**
     * Update entity with manual values (user edits).
     */
    public void updateEntityWithValues(
            ReverseGeocodingLocationEntity entity,
            String displayName,
            String city,
            String country) {

        if (entity == null) {
            return;
        }

        entity.setDisplayName(displayName);
        entity.setCity(city);
        entity.setCountry(country);
        entity.setLastAccessedAt(Instant.now());
    }
}
