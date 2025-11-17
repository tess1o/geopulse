package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;
import org.github.tess1o.geopulse.geocoding.dto.*;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing reverse geocoding results with copy-on-write semantics.
 * Handles CRUD operations, reconciliation, and synchronization with timeline_stays.
 * <p>
 * Copy-on-Write Strategy:
 * - user_id = NULL: Original/shared data from provider (unmodified by any user)
 * - user_id = UUID: User-specific copy (modified by that user)
 * <p>
 * Modification Behavior:
 * - Modifying original (user_id=NULL): Creates user-specific copy, keeps original
 * - Modifying own copy (user_id=current_user): Updates in-place
 * - Modifying other's copy (user_id!=current_user): Rejects with 403 Forbidden
 */
@ApplicationScoped
@Slf4j
public class ReverseGeocodingManagementService {

    private final ReverseGeocodingLocationRepository geocodingRepository;
    private final GeocodingProviderFactory providerFactory;
    private final GeocodingConfig geocodingConfig;
    private final EntityManager entityManager;

    @Inject
    public ReverseGeocodingManagementService(
            ReverseGeocodingLocationRepository geocodingRepository,
            GeocodingProviderFactory providerFactory,
            GeocodingConfig geocodingConfig,
            EntityManager entityManager) {
        this.geocodingRepository = geocodingRepository;
        this.providerFactory = providerFactory;
        this.geocodingConfig = geocodingConfig;
        this.entityManager = entityManager;
    }

    /**
     * Get paginated list of geocoding results for user management page.
     * Shows only entities relevant to current user.
     */
    public List<ReverseGeocodingDTO> getGeocodingResults(
            UUID userId, String providerName, String searchText, int page, int limit,
            String sortField, String sortOrder) {

        // Use new user-filtered repository method
        List<ReverseGeocodingLocationEntity> entities = geocodingRepository.findForUserManagementPage(
                userId, providerName, null, null, searchText, page, limit);

        return entities.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get total count with filters for user management page.
     */
    public long countGeocodingResults(UUID userId, String providerName, String searchText) {
        return geocodingRepository.countForUserManagementPage(userId, providerName, null, null, searchText);
    }

    /**
     * Get a single geocoding result by ID.
     * Ensures user can only access their own copies or originals they reference.
     */
    public ReverseGeocodingDTO getGeocodingResult(UUID userId, Long id) {
        ReverseGeocodingLocationEntity entity = geocodingRepository.findById(id);
        if (entity == null) {
            throw new NotFoundException("Geocoding result not found: " + id);
        }

        // Security check: User can access if it's original OR belongs to them
        if (entity.getUser() != null && !entity.isOwnedBy(userId)) {
            throw new ForbiddenException("Cannot access another user's geocoding data");
        }

        return convertToDTO(entity);
    }

    /**
     * Update geocoding result with copy-on-write semantics.
     * <p>
     * Behavior:
     * - If entity belongs to current user: Update in-place
     * - If entity is original (user_id=NULL): Create user-specific copy, keep original
     * - If entity belongs to another user: Reject (403 Forbidden)
     */
    @Transactional
    public ReverseGeocodingDTO updateGeocodingResult(UUID currentUserId, Long geocodingId, ReverseGeocodingUpdateDTO updateDTO) {
        ReverseGeocodingLocationEntity entity = geocodingRepository.findById(geocodingId);
        if (entity == null) {
            throw new NotFoundException("Geocoding result not found: " + geocodingId);
        }

        // Case 1: User updating their own custom entity
        if (entity.getUser() != null && entity.isOwnedBy(currentUserId)) {
            log.info("User {} updating their own geocoding entity {}", currentUserId, geocodingId);

            // Update in-place (already their copy)
            entity.setDisplayName(updateDTO.getDisplayName());
            entity.setCity(updateDTO.getCity());
            entity.setCountry(updateDTO.getCountry());
            entity.setLastAccessedAt(Instant.now());
            geocodingRepository.persist(entity);

            // Update only this user's timeline stays
            updateTimelineStaysForUser(currentUserId, geocodingId, updateDTO.getDisplayName());

            log.info("Updated user-specific geocoding entity {} for user {}", geocodingId, currentUserId);
            return convertToDTO(entity);
        }

        // Case 2: User modifying an original (shared) entity - COPY-ON-WRITE
        else if (entity.isOriginal()) {
            log.info("User {} creating copy-on-write for original geocoding entity {}", currentUserId, geocodingId);

            // Create user-specific copy
            ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();

            // Set user (marks as user-specific)
            UserEntity user = entityManager.getReference(UserEntity.class, currentUserId);
            userCopy.setUser(user);

            // Copy spatial data from original
            userCopy.setRequestCoordinates(entity.getRequestCoordinates());
            userCopy.setResultCoordinates(entity.getResultCoordinates());
            userCopy.setBoundingBox(entity.getBoundingBox());
            userCopy.setProviderName(entity.getProviderName());

            // Set modified values
            userCopy.setDisplayName(updateDTO.getDisplayName());
            userCopy.setCity(updateDTO.getCity());
            userCopy.setCountry(updateDTO.getCountry());

            // Set timestamps
            userCopy.setCreatedAt(Instant.now());
            userCopy.setLastAccessedAt(Instant.now());

            // Persist new entity
            geocodingRepository.persist(userCopy);

            // Update only this user's timeline stays to reference new copy
            updateTimelineStaysToNewCopy(currentUserId, geocodingId, userCopy.getId(), updateDTO.getDisplayName());

            log.info("Created user-specific copy {} for user {} (original {} unchanged)",
                    userCopy.getId(), currentUserId, geocodingId);

            return convertToDTO(userCopy);
        }

        // Case 3: User trying to modify another user's custom entity
        else {
            log.warn("User {} attempted to modify geocoding entity {} owned by user {}",
                    currentUserId, geocodingId, entity.getUser().getId());
            throw new ForbiddenException("Cannot modify another user's geocoding data");
        }
    }

    /**
     * Update timeline stays for user when modifying their own entity (in-place update).
     */
    private void updateTimelineStaysForUser(UUID userId, Long geocodingId, String locationName) {
        int updatedCount = entityManager.createQuery(
                        "UPDATE TimelineStayEntity t " +
                                "SET t.locationName = :locationName " +
                                "WHERE t.geocodingLocation.id = :geocodingId " +
                                "  AND t.user.id = :userId"
                )
                .setParameter("geocodingId", geocodingId)
                .setParameter("userId", userId)
                .setParameter("locationName", locationName)
                .executeUpdate();

        log.debug("Updated {} timeline stays for user {} with new location name",
                updatedCount, userId);
    }

    /**
     * Update timeline stays when copy-on-write creates a new entity.
     * Changes stay references from old (original) to new (user-specific copy).
     */
    private void updateTimelineStaysToNewCopy(
            UUID userId, Long oldGeocodingId, Long newGeocodingId, String locationName) {

        // Need to use native query because JPA doesn't support updating FK directly
        String updateSql = """
                UPDATE timeline_stays
                SET geocoding_id = :newGeocodingId,
                    location_name = :locationName
                WHERE geocoding_id = :oldGeocodingId
                  AND user_id = :userId
                """;

        int updatedCount = entityManager.createNativeQuery(updateSql)
                .setParameter("oldGeocodingId", oldGeocodingId)
                .setParameter("newGeocodingId", newGeocodingId)
                .setParameter("userId", userId)
                .setParameter("locationName", locationName)
                .executeUpdate();

        log.info("Updated {} timeline stays for user {} from geocoding {} to {}",
                updatedCount, userId, oldGeocodingId, newGeocodingId);
    }

    /**
     * Reconcile geocoding entity with provider (re-fetch from API).
     * <p>
     * Behavior:
     * - If data unchanged: No action
     * - If data changed:
     * - Original (user_id=NULL): Create user-specific copy with new data
     * - User's copy: Update in-place
     * - Another user's copy: Reject (403)
     */
    @Transactional
    public ReverseGeocodingDTO reconcileWithProvider(UUID currentUserId, Long geocodingId, String providerName) {
        ReverseGeocodingLocationEntity entity = geocodingRepository.findById(geocodingId);
        if (entity == null) {
            throw new NotFoundException("Geocoding result not found: " + geocodingId);
        }

        // Security check
        if (entity.getUser() != null && !entity.isOwnedBy(currentUserId)) {
            throw new ForbiddenException("Cannot reconcile another user's geocoding data");
        }

        try {
            // Fetch fresh data from provider
            FormattableGeocodingResult freshResult = providerFactory
                    .reconcileWithProvider(providerName, entity.getRequestCoordinates())
                    .await().indefinitely();

            // Check if data changed
            boolean dataChanged = hasDataChanged(entity, freshResult);

            if (!dataChanged) {
                log.info("Reconciliation for geocoding {}: No changes detected", geocodingId);
                return convertToDTO(entity);
            }

            // Data changed - apply copy-on-write logic
            if (entity.isOriginal()) {
                // Original: Create user-specific copy with new data
                log.info("Reconciliation for geocoding {}: Creating user copy with updated data", geocodingId);

                ReverseGeocodingLocationEntity userCopy = createUserCopyFromResult(currentUserId, entity, freshResult);
                geocodingRepository.persist(userCopy);

                updateTimelineStaysToNewCopy(currentUserId, geocodingId, userCopy.getId(), freshResult.getFormattedDisplayName());

                return convertToDTO(userCopy);
            } else {
                // User's own copy: Update in-place
                log.info("Reconciliation for geocoding {}: Updating user's copy with new data", geocodingId);

                applyResultToEntity(entity, freshResult);
                geocodingRepository.persist(entity);

                updateTimelineStaysForUser(currentUserId, geocodingId, freshResult.getFormattedDisplayName());

                return convertToDTO(entity);
            }

        } catch (Exception e) {
            log.error("Failed to reconcile geocoding {} with provider {}: {}",
                    geocodingId, providerName, e.getMessage(), e);
            throw new RuntimeException("Reconciliation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if geocoding data has changed.
     */
    private boolean hasDataChanged(ReverseGeocodingLocationEntity entity, FormattableGeocodingResult freshResult) {
        if (!entity.getDisplayName().equals(freshResult.getFormattedDisplayName())) {
            return true;
        }
        if (entity.getCity() != null && !entity.getCity().equals(freshResult.getCity())) {
            return true;
        }
        if (entity.getCountry() != null && !entity.getCountry().equals(freshResult.getCountry())) {
            return true;
        }
        return false;
    }

    /**
     * Create user-specific copy from fresh geocoding result.
     */
    private ReverseGeocodingLocationEntity createUserCopyFromResult(
            UUID userId, ReverseGeocodingLocationEntity original, FormattableGeocodingResult freshResult) {

        ReverseGeocodingLocationEntity userCopy = new ReverseGeocodingLocationEntity();

        UserEntity user = entityManager.getReference(UserEntity.class, userId);
        userCopy.setUser(user);

        // Copy spatial data from original
        userCopy.setRequestCoordinates(original.getRequestCoordinates());
        userCopy.setResultCoordinates(freshResult.getResultCoordinates());
        userCopy.setBoundingBox(freshResult.getBoundingBox());
        userCopy.setProviderName(freshResult.getProviderName());

        // Set new data
        userCopy.setDisplayName(freshResult.getFormattedDisplayName());
        userCopy.setCity(freshResult.getCity());
        userCopy.setCountry(freshResult.getCountry());

        userCopy.setCreatedAt(Instant.now());
        userCopy.setLastAccessedAt(Instant.now());

        return userCopy;
    }

    /**
     * Apply fresh result to existing entity.
     */
    private void applyResultToEntity(ReverseGeocodingLocationEntity entity, FormattableGeocodingResult freshResult) {
        entity.setResultCoordinates(freshResult.getResultCoordinates());
        entity.setBoundingBox(freshResult.getBoundingBox());
        entity.setDisplayName(freshResult.getFormattedDisplayName());
        entity.setCity(freshResult.getCity());
        entity.setCountry(freshResult.getCountry());
        entity.setProviderName(freshResult.getProviderName());
        entity.setLastAccessedAt(Instant.now());
    }

    /**
     * Reconcile geocoding results with a specific provider (batch operation).
     */
    @Transactional
    public ReverseGeocodingReconcileResult reconcileWithProvider(UUID currentUserId, ReverseGeocodingReconcileRequest request) {
        List<Long> idsToReconcile = determineIdsToReconcile(currentUserId, request);

        log.info("Reconciling {} geocoding results for user {} with provider: {}",
                idsToReconcile.size(), currentUserId, request.getProviderName());

        int totalProcessed = 0;
        int successfulUpdates = 0;
        int failedUpdates = 0;
        List<ReverseGeocodingReconcileResult.ReconcileError> errors = new ArrayList<>();

        for (Long id : idsToReconcile) {
                totalProcessed++;
            try {
                reconcileWithProvider(currentUserId, id, request.getProviderName());
                successfulUpdates++;
            } catch (Exception e) {
                failedUpdates++;
                log.warn("Failed to reconcile geocoding result {} for user {}: {}",
                        id, currentUserId, e.getMessage());
                errors.add(ReverseGeocodingReconcileResult.ReconcileError.builder()
                        .geocodingId(id)
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        log.info("Reconciliation complete for user {}: {} processed, {} successful, {} failed",
                currentUserId, totalProcessed, successfulUpdates, failedUpdates);

        return ReverseGeocodingReconcileResult.builder()
                .totalProcessed(totalProcessed)
                .successfulUpdates(successfulUpdates)
                .failedUpdates(failedUpdates)
                .errors(errors)
                .build();
    }

    /**
     * Determine which IDs to reconcile based on request.
     */
    private List<Long> determineIdsToReconcile(UUID userId, ReverseGeocodingReconcileRequest request) {
        if (Boolean.TRUE.equals(request.getReconcileAll())) {
            // Get all geocoding IDs for user (from management page query)
            List<ReverseGeocodingLocationEntity> entities = geocodingRepository.findForUserManagementPage(
                    userId, request.getFilterByProvider(), null, null, null, 1, Integer.MAX_VALUE);
            return entities.stream()
                    .map(ReverseGeocodingLocationEntity::getId)
                    .collect(Collectors.toList());
        } else {
            // Reconcile only specified IDs
            return request.getGeocodingIds();
        }
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
                .isUserSpecific(entity.getUser() != null)
                .build();
    }
}
