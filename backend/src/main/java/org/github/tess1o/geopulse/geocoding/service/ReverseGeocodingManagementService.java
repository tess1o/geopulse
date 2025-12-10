package org.github.tess1o.geopulse.geocoding.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.dto.*;
import org.github.tess1o.geopulse.geocoding.mapper.ReverseGeocodingDTOMapper;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.GeocodingCopyOnWriteHandler.ReconciliationResult;
import org.github.tess1o.geopulse.geocoding.service.GeocodingCopyOnWriteHandler.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private final GeocodingConfigurationService configService;
    private final ReverseGeocodingDTOMapper dtoMapper;
    private final GeocodingCopyOnWriteHandler copyOnWriteHandler;
    private final ReconciliationJobProgressService reconciliationProgressService;
    private final ManagedExecutor managedExecutor;

    @Inject
    public ReverseGeocodingManagementService(
            ReverseGeocodingLocationRepository geocodingRepository,
            GeocodingProviderFactory providerFactory,
            GeocodingConfigurationService configService,
            ReverseGeocodingDTOMapper dtoMapper,
            GeocodingCopyOnWriteHandler copyOnWriteHandler,
            ReconciliationJobProgressService reconciliationProgressService,
            ManagedExecutor managedExecutor) {
        this.geocodingRepository = geocodingRepository;
        this.providerFactory = providerFactory;
        this.configService = configService;
        this.dtoMapper = dtoMapper;
        this.copyOnWriteHandler = copyOnWriteHandler;
        this.reconciliationProgressService = reconciliationProgressService;
        this.managedExecutor = managedExecutor;
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

        return dtoMapper.toDTOList(entities);
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

        return dtoMapper.toDTO(entity);
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

        UpdateResult result = copyOnWriteHandler.handleUserUpdate(currentUserId, entity, updateDTO);
        return dtoMapper.toDTO(result.entity());
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

            ReconciliationResult result = copyOnWriteHandler.handleReconciliation(currentUserId, entity, freshResult);
            return dtoMapper.toDTO(result.entity());

        } catch (Exception e) {
            log.error("Failed to reconcile geocoding {} with provider {}: {}",
                    geocodingId, providerName, e.getMessage(), e);
            throw new RuntimeException("Reconciliation failed: " + e.getMessage(), e);
        }
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
     * Start async bulk reconciliation job and return job ID immediately.
     * This is the new async method that creates a job and processes it in the background.
     */
    public UUID reconcileWithProviderAsync(UUID currentUserId, ReverseGeocodingReconcileRequest request) {
        List<Long> idsToReconcile = determineIdsToReconcile(currentUserId, request);

        // Create job
        UUID jobId = reconciliationProgressService.createJob(
                currentUserId, request.getProviderName(), idsToReconcile.size());

        log.info("Starting async reconciliation job {} for user {} ({} items)",
                jobId, currentUserId, idsToReconcile.size());

        // Run reconciliation asynchronously using ManagedExecutor
        CompletableFuture.runAsync(() -> {
            try {
                processReconciliationJob(jobId, currentUserId, idsToReconcile, request.getProviderName());
            } catch (Exception e) {
                log.error("Failed to process reconciliation job {}", jobId, e);
                reconciliationProgressService.failJob(jobId, e.getMessage());
            }
        }, managedExecutor);

        return jobId;
    }

    /**
     * Process reconciliation job with progress tracking.
     * This runs asynchronously and updates progress after each item.
     */
    @Transactional
    @ActivateRequestContext
    void processReconciliationJob(UUID jobId, UUID userId, List<Long> ids, String providerName) {
        int successCount = 0;
        int failedCount = 0;

        log.info("Processing reconciliation job {} with {} items", jobId, ids.size());

        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);

            try {
                reconcileWithProvider(userId, id, providerName);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                log.warn("Failed to reconcile geocoding result {} in job {}: {}", id, jobId, e.getMessage());
            }

            // Update progress after each item
            reconciliationProgressService.updateProgress(jobId, i + 1, successCount, failedCount);
        }

        // Mark complete
        reconciliationProgressService.completeJob(jobId);
        log.info("Reconciliation job {} completed: {} success, {} failed", jobId, successCount, failedCount);
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

        String primaryProvider = configService.getPrimaryProvider();
        String fallbackProvider = configService.getFallbackProvider();

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
}
