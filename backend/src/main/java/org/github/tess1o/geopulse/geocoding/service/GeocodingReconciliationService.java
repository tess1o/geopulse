package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingDTO;
import org.github.tess1o.geopulse.geocoding.mapper.ReverseGeocodingDTOMapper;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.GeocodingCopyOnWriteHandler.ReconciliationResult;

import java.util.UUID;

/**
 * Executes single geocoding reconciliation operations in their own transaction.
 */
@ApplicationScoped
@Slf4j
public class GeocodingReconciliationService {

    private final ReverseGeocodingLocationRepository geocodingRepository;
    private final GeocodingProviderFactory providerFactory;
    private final ReverseGeocodingDTOMapper dtoMapper;
    private final GeocodingCopyOnWriteHandler copyOnWriteHandler;

    @Inject
    public GeocodingReconciliationService(
            ReverseGeocodingLocationRepository geocodingRepository,
            GeocodingProviderFactory providerFactory,
            ReverseGeocodingDTOMapper dtoMapper,
            GeocodingCopyOnWriteHandler copyOnWriteHandler) {
        this.geocodingRepository = geocodingRepository;
        this.providerFactory = providerFactory;
        this.dtoMapper = dtoMapper;
        this.copyOnWriteHandler = copyOnWriteHandler;
    }

    /**
     * Reconcile geocoding entity with provider (re-fetch from API).
     * Runs in a dedicated transaction per invocation.
     */
    @Transactional
    public ReverseGeocodingDTO reconcileWithProvider(UUID currentUserId, Long geocodingId, String providerName) {
        ReverseGeocodingLocationEntity entity = geocodingRepository.findById(geocodingId);
        if (entity == null) {
            throw new NotFoundException("Geocoding result not found: " + geocodingId);
        }

        if (entity.getUser() != null && !entity.isOwnedBy(currentUserId)) {
            throw new ForbiddenException("Cannot reconcile another user's geocoding data");
        }

        try {
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
}
