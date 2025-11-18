package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingUpdateDTO;
import org.github.tess1o.geopulse.geocoding.mapper.GeocodingEntityMapper;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;

import java.util.UUID;

/**
 * Handler for copy-on-write operations on geocoding entities.
 * Implements the logic for creating user-specific copies when modifying shared originals.
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
public class GeocodingCopyOnWriteHandler {

    private final GeocodingEntityMapper entityMapper;
    private final ReverseGeocodingLocationRepository repository;
    private final TimelineGeocodingSyncService timelineSyncService;

    @Inject
    public GeocodingCopyOnWriteHandler(
            GeocodingEntityMapper entityMapper,
            ReverseGeocodingLocationRepository repository,
            TimelineGeocodingSyncService timelineSyncService) {
        this.entityMapper = entityMapper;
        this.repository = repository;
        this.timelineSyncService = timelineSyncService;
    }

    /**
     * Handle user update with copy-on-write semantics.
     * Returns the entity to use (either updated existing or new copy).
     */
    public UpdateResult handleUserUpdate(
            UUID currentUserId,
            ReverseGeocodingLocationEntity entity,
            ReverseGeocodingUpdateDTO updateDTO) {

        // Case 1: User updating their own custom entity
        if (entity.getUser() != null && entity.isOwnedBy(currentUserId)) {
            return handleUpdateOwnEntity(currentUserId, entity, updateDTO);
        }

        // Case 2: User modifying an original (shared) entity - COPY-ON-WRITE
        if (entity.isOriginal()) {
            return handleCopyOnWriteForUpdate(currentUserId, entity, updateDTO);
        }

        // Case 3: User trying to modify another user's custom entity
        log.warn("User {} attempted to modify geocoding entity {} owned by user {}",
                currentUserId, entity.getId(), entity.getUser().getId());
        throw new ForbiddenException("Cannot modify another user's geocoding data");
    }

    /**
     * Handle reconciliation with copy-on-write semantics.
     * Returns the entity to use (either updated existing or new copy).
     */
    public ReconciliationResult handleReconciliation(
            UUID currentUserId,
            ReverseGeocodingLocationEntity entity,
            FormattableGeocodingResult freshResult) {

        // Check if data changed
        if (!hasDataChanged(entity, freshResult)) {
            log.info("Reconciliation for geocoding {}: No changes detected", entity.getId());
            return ReconciliationResult.noChange(entity);
        }

        // Data changed - apply copy-on-write logic
        if (entity.isOriginal()) {
            return handleCopyOnWriteForReconciliation(currentUserId, entity, freshResult);
        } else {
            return handleUpdateOwnEntityFromResult(currentUserId, entity, freshResult);
        }
    }

    /**
     * Update user's own entity in-place (Case 1).
     */
    private UpdateResult handleUpdateOwnEntity(
            UUID userId,
            ReverseGeocodingLocationEntity entity,
            ReverseGeocodingUpdateDTO updateDTO) {

        log.info("User {} updating their own geocoding entity {}", userId, entity.getId());

        entityMapper.updateEntityWithValues(entity, updateDTO.getDisplayName(), updateDTO.getCity(), updateDTO.getCountry());
        repository.persist(entity);

        timelineSyncService.updateLocationNameForUser(userId, entity.getId(), updateDTO.getDisplayName());

        log.info("Updated user-specific geocoding entity {} for user {}", entity.getId(), userId);
        return UpdateResult.updated(entity);
    }

    /**
     * Create copy-on-write for original entity (Case 2).
     */
    private UpdateResult handleCopyOnWriteForUpdate(
            UUID userId,
            ReverseGeocodingLocationEntity original,
            ReverseGeocodingUpdateDTO updateDTO) {

        log.info("User {} creating copy-on-write for original geocoding entity {}", userId, original.getId());

        ReverseGeocodingLocationEntity userCopy = entityMapper.createUserCopyWithValues(
                userId, original,
                updateDTO.getDisplayName(), updateDTO.getCity(), updateDTO.getCountry());

        repository.persist(userCopy);

        timelineSyncService.switchToNewGeocodingReference(
                userId, original.getId(), userCopy.getId(), updateDTO.getDisplayName());

        log.info("Created user-specific copy {} for user {} (original {} unchanged)",
                userCopy.getId(), userId, original.getId());

        return UpdateResult.copied(userCopy, original.getId());
    }

    /**
     * Update user's own entity from reconciliation result.
     */
    private ReconciliationResult handleUpdateOwnEntityFromResult(
            UUID userId,
            ReverseGeocodingLocationEntity entity,
            FormattableGeocodingResult freshResult) {

        log.info("Reconciliation for geocoding {}: Updating user's copy with new data", entity.getId());

        entityMapper.updateEntityFromResult(entity, freshResult);
        repository.persist(entity);

        timelineSyncService.updateLocationNameForUser(userId, entity.getId(), freshResult.getFormattedDisplayName());

        return ReconciliationResult.updated(entity);
    }

    /**
     * Create copy-on-write for reconciliation.
     */
    private ReconciliationResult handleCopyOnWriteForReconciliation(
            UUID userId,
            ReverseGeocodingLocationEntity original,
            FormattableGeocodingResult freshResult) {

        log.info("Reconciliation for geocoding {}: Creating user copy with updated data", original.getId());

        ReverseGeocodingLocationEntity userCopy = entityMapper.createUserCopy(userId, original, freshResult);
        repository.persist(userCopy);

        timelineSyncService.switchToNewGeocodingReference(
                userId, original.getId(), userCopy.getId(), freshResult.getFormattedDisplayName());

        return ReconciliationResult.copied(userCopy, original.getId());
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
     * Result of an update operation.
     */
    public record UpdateResult(
            ReverseGeocodingLocationEntity entity,
            boolean wasCopied,
            Long originalId) {

        public static UpdateResult updated(ReverseGeocodingLocationEntity entity) {
            return new UpdateResult(entity, false, null);
        }

        public static UpdateResult copied(ReverseGeocodingLocationEntity newCopy, Long originalId) {
            return new UpdateResult(newCopy, true, originalId);
        }
    }

    /**
     * Result of a reconciliation operation.
     */
    public record ReconciliationResult(
            ReverseGeocodingLocationEntity entity,
            boolean changed,
            boolean wasCopied,
            Long originalId) {

        public static ReconciliationResult noChange(ReverseGeocodingLocationEntity entity) {
            return new ReconciliationResult(entity, false, false, null);
        }

        public static ReconciliationResult updated(ReverseGeocodingLocationEntity entity) {
            return new ReconciliationResult(entity, true, false, null);
        }

        public static ReconciliationResult copied(ReverseGeocodingLocationEntity newCopy, Long originalId) {
            return new ReconciliationResult(newCopy, true, true, originalId);
        }
    }
}
