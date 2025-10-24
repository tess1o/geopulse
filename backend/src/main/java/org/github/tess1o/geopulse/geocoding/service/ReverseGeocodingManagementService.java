package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;
import org.github.tess1o.geopulse.geocoding.dto.*;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing reverse geocoding results.
 * Handles CRUD operations, reconciliation, and synchronization with timeline_stays.
 */
@ApplicationScoped
@Slf4j
public class ReverseGeocodingManagementService {

    private final ReverseGeocodingLocationRepository geocodingRepository;
    private final GeocodingProviderFactory providerFactory;
    private final GeocodingConfig geocodingConfig;

    @Inject
    public ReverseGeocodingManagementService(
            ReverseGeocodingLocationRepository geocodingRepository,
            GeocodingProviderFactory providerFactory,
            GeocodingConfig geocodingConfig) {
        this.geocodingRepository = geocodingRepository;
        this.providerFactory = providerFactory;
        this.geocodingConfig = geocodingConfig;
    }

    /**
     * Get paginated list of geocoding results with filters.
     */
    public List<ReverseGeocodingDTO> getGeocodingResults(
            String providerName, String searchText, int page, int limit,
            String sortField, String sortOrder) {

        List<ReverseGeocodingLocationEntity> entities = geocodingRepository.findWithFilters(
                providerName, searchText, page, limit, sortField, sortOrder);

        return entities.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get total count with filters.
     */
    public long countGeocodingResults(String providerName, String searchText) {
        return geocodingRepository.countWithFilters(providerName, searchText);
    }

    /**
     * Get a single geocoding result by ID.
     */
    public ReverseGeocodingDTO getGeocodingResult(Long id) {
        ReverseGeocodingLocationEntity entity = geocodingRepository.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Geocoding result not found: " + id);
        }
        return convertToDTO(entity);
    }

    /**
     * Update geocoding result fields and sync with timeline_stays.
     */
    @Transactional
    public ReverseGeocodingDTO updateGeocodingResult(Long id, ReverseGeocodingUpdateDTO updateDTO) {
        ReverseGeocodingLocationEntity entity = geocodingRepository.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Geocoding result not found: " + id);
        }

        // Update fields
        entity.setDisplayName(updateDTO.getDisplayName());
        entity.setCity(updateDTO.getCity());
        entity.setCountry(updateDTO.getCountry());
        entity.setLastAccessedAt(Instant.now());

        geocodingRepository.persist(entity);

        // Sync with timeline_stays
        updateTimelineStaysForGeocodingId(id, updateDTO.getDisplayName());

        log.info("Updated geocoding result {} and synced timeline_stays", id);

        return convertToDTO(entity);
    }

    /**
     * Reconcile geocoding results with a specific provider.
     */
    @Transactional
    public ReverseGeocodingReconcileResult reconcileWithProvider(ReverseGeocodingReconcileRequest request) {
        List<Long> idsToReconcile = determineIdsToReconcile(request);

        log.info("Reconciling {} geocoding results with provider: {}",
                idsToReconcile.size(), request.getProviderName());

        int totalProcessed = 0;
        int successfulUpdates = 0;
        int failedUpdates = 0;
        List<ReverseGeocodingReconcileResult.ReconcileError> errors = new ArrayList<>();

        for (Long id : idsToReconcile) {
            totalProcessed++;
            try {
                reconcileSingleResult(id, request.getProviderName());
                successfulUpdates++;
            } catch (Exception e) {
                failedUpdates++;
                log.warn("Failed to reconcile geocoding result {}: {}", id, e.getMessage());
                errors.add(ReverseGeocodingReconcileResult.ReconcileError.builder()
                        .geocodingId(id)
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        log.info("Reconciliation complete: {} processed, {} successful, {} failed",
                totalProcessed, successfulUpdates, failedUpdates);

        return ReverseGeocodingReconcileResult.builder()
                .totalProcessed(totalProcessed)
                .successfulUpdates(successfulUpdates)
                .failedUpdates(failedUpdates)
                .errors(errors)
                .build();
    }

    /**
     * Get list of enabled providers (for reconciliation).
     */
    public List<GeocodingProviderDTO> getEnabledProviders() {
        List<GeocodingProviderDTO> providers = new ArrayList<>();

        String primaryProvider = geocodingConfig.provider().primary();
        String fallbackProvider = geocodingConfig.provider().fallback().orElse(null);

        List<String> enabledProviders = providerFactory.getEnabledProviders();

        for (String providerName : enabledProviders) {
            providers.add(GeocodingProviderDTO.builder()
                    .name(providerName)
                    .displayName(providerName)
                    .enabled(true)
                    .isPrimary(providerName.equalsIgnoreCase(primaryProvider))
                    .isFallback(providerName.equalsIgnoreCase(fallbackProvider))
                    .build());
        }

        return providers;
    }

    /**
     * Get list of providers that have data in the database (for filtering).
     */
    public List<String> getProvidersWithData() {
        return geocodingRepository.findDistinctProviderNames();
    }

    /**
     * Determine which IDs to reconcile based on request.
     */
    private List<Long> determineIdsToReconcile(ReverseGeocodingReconcileRequest request) {
        if (Boolean.TRUE.equals(request.getReconcileAll())) {
            // Reconcile all or filtered by provider
            return geocodingRepository.findIdsWithFilters(request.getFilterByProvider());
        } else {
            // Reconcile only specified IDs
            return request.getGeocodingIds();
        }
    }

    /**
     * Reconcile a single geocoding result with a provider.
     */
    private void reconcileSingleResult(Long id, String providerName) {
        ReverseGeocodingLocationEntity entity = geocodingRepository.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Geocoding result not found: " + id);
        }

        try {
            // Fetch new result from provider
            FormattableGeocodingResult newResult = providerFactory
                    .reconcileWithProvider(providerName, entity.getRequestCoordinates())
                    .await().indefinitely();

            // Update entity with new data
            entity.setResultCoordinates(newResult.getResultCoordinates());
            entity.setBoundingBox(newResult.getBoundingBox());
            entity.setDisplayName(newResult.getFormattedDisplayName());
            entity.setCity(newResult.getCity());
            entity.setCountry(newResult.getCountry());
            entity.setProviderName(newResult.getProviderName());
            entity.setLastAccessedAt(Instant.now());

            geocodingRepository.persist(entity);

            // Sync with timeline_stays
            updateTimelineStaysForGeocodingId(id, newResult.getFormattedDisplayName());

            log.debug("Successfully reconciled geocoding result {} with provider {}", id, providerName);

        } catch (Exception e) {
            // Keep original data on failure
            log.warn("Failed to reconcile geocoding result {} with provider {}: {}",
                    id, providerName, e.getMessage());
            throw new RuntimeException("Reconciliation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Update timeline_stays location_name for all records referencing this geocoding_id.
     * This ensures denormalized data stays in sync across all users.
     */
    private void updateTimelineStaysForGeocodingId(Long geocodingId, String newLocationName) {
        try {
            int updatedCount = geocodingRepository.getEntityManager()
                    .createQuery("UPDATE TimelineStayEntity t SET t.locationName = :locationName WHERE t.geocodingLocation.id = :geocodingId")
                    .setParameter("locationName", newLocationName)
                    .setParameter("geocodingId", geocodingId)
                    .executeUpdate();

            log.info("Updated {} timeline_stays records for geocoding_id {}", updatedCount, geocodingId);
        } catch (Exception e) {
            log.error("Failed to update timeline_stays for geocoding_id {}: {}",
                    geocodingId, e.getMessage(), e);
            // Don't fail the whole operation if timeline sync fails
        }
    }

    /**
     * Convert entity to DTO.
     */
    private ReverseGeocodingDTO convertToDTO(ReverseGeocodingLocationEntity entity) {
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
                .build();
    }
}
