package org.github.tess1o.geopulse.geocoding.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingDTO;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between ReverseGeocodingLocationEntity and ReverseGeocodingDTO.
 * Handles DTO conversions for API responses.
 */
@ApplicationScoped
public class ReverseGeocodingDTOMapper {

    /**
     * Convert entity to DTO.
     */
    public ReverseGeocodingDTO toDTO(ReverseGeocodingLocationEntity entity) {
        if (entity == null) {
            return null;
        }

        return ReverseGeocodingDTO.builder()
                .id(entity.getId())
                .longitude(entity.getRequestCoordinates().getX())
                .latitude(entity.getRequestCoordinates().getY())
                .displayName(entity.getDisplayName())
                .city(entity.getCity())
                .country(entity.getCountry())
                .providerName(entity.getProviderName())
                .createdAt(entity.getCreatedAt())
                .lastAccessedAt(entity.getLastAccessedAt())
                .isUserSpecific(entity.getUser() != null)
                .build();
    }

    /**
     * Convert list of entities to DTOs.
     */
    public List<ReverseGeocodingDTO> toDTOList(List<ReverseGeocodingLocationEntity> entities) {
        if (entities == null) {
            return List.of();
        }

        return entities.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
