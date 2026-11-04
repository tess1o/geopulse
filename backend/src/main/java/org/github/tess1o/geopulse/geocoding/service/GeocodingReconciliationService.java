package org.github.tess1o.geopulse.geocoding.service;

import org.github.tess1o.geopulse.geocoding.dto.GeocodingCandidateSelection;
import org.github.tess1o.geopulse.geocoding.dto.GeocodingCandidatePreview;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingCandidate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingDTO;
import org.github.tess1o.geopulse.geocoding.mapper.ReverseGeocodingDTOMapper;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.GeocodingCopyOnWriteHandler.ReconciliationResult;

import java.util.UUID;
import java.util.List;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import io.quarkus.scheduler.Scheduled;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_GEOCODING_REQUEST;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GEOCODING_ACCESS_DENIED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GEOCODING_RESULT_NOT_FOUND;

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
    private final UserLocationNormalizationService normalizationService;

    @Inject
    public GeocodingReconciliationService(
            ReverseGeocodingLocationRepository geocodingRepository,
            GeocodingProviderFactory providerFactory,
            ReverseGeocodingDTOMapper dtoMapper,
            GeocodingCopyOnWriteHandler copyOnWriteHandler,
            UserLocationNormalizationService normalizationService) {
        this.geocodingRepository = geocodingRepository;
        this.providerFactory = providerFactory;
        this.dtoMapper = dtoMapper;
        this.copyOnWriteHandler = copyOnWriteHandler;
        this.normalizationService = normalizationService;
    }

    /**
     * Reconcile geocoding entity with provider (re-fetch from API).
     * Runs in a dedicated transaction per invocation.
     */
    @Transactional
    public ReverseGeocodingDTO reconcileWithProvider(UUID currentUserId, Long geocodingId, String providerName) {
        ReverseGeocodingLocationEntity entity = geocodingRepository.findById(geocodingId);
        if (entity == null) {
            throw new GeoPulseException(GEOCODING_RESULT_NOT_FOUND, "Geocoding result not found");
        }

        if (entity.getUser() != null && !entity.isOwnedBy(currentUserId)) {
            throw new GeoPulseException(GEOCODING_ACCESS_DENIED, "Access denied");
        }

        try {
            FormattableGeocodingResult freshResult = providerFactory
                    .reconcileWithProvider(providerName, entity.getRequestCoordinates())
                    .await().indefinitely();
            freshResult = applyNormalizationRules(currentUserId, freshResult);

            ReconciliationResult result = copyOnWriteHandler.handleReconciliation(currentUserId, entity, freshResult);
            return dtoMapper.toDTO(result.entity());
        } catch (Exception e) {
            log.error("Failed to reconcile geocoding {} with provider {}: {}",
                    geocodingId, providerName, e.getMessage(), e);
            throw new IllegalStateException("Reconciliation failed: " + e.getMessage(), e);
        }
    }

    java.time.Clock clock = java.time.Clock.systemUTC();
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(10);
    // shortcut: previews are local to this backend, use shared storage if replica routing is introduced.
    private final ConcurrentHashMap<UUID, Preview> previews = new ConcurrentHashMap<>();

    private record Snapshot(String name, String city, String country, String provider,
                            String request, String result, String boundingBox, UUID owner) {
        static Snapshot of(ReverseGeocodingLocationEntity entity) {
            return new Snapshot(entity.getDisplayName(), entity.getCity(), entity.getCountry(), entity.getProviderName(),
                    geometry(entity.getRequestCoordinates()), geometry(entity.getResultCoordinates()),
                    geometry(entity.getBoundingBox()), entity.getUser() == null ? null : entity.getUser().getId());
        }
        private static String geometry(org.locationtech.jts.geom.Geometry geometry) {
            return geometry == null ? null : geometry.toText();
        }
    }

    private record Preview(UUID id, Long geocodingId, String provider, Instant expiresAt,
                           Snapshot snapshot, List<GeocodingCandidate> candidates) {}

    @Scheduled(every = "1m")
    void expirePreviews() {
        var now = Instant.now(clock);
        previews.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public GeocodingCandidatePreview previewCandidates(UUID userId, Long geocodingId, String providerName) {
        var entity = accessibleEntity(userId, geocodingId);
        requireEnabledProvider(providerName);
        var pending = new Preview(UUID.randomUUID(), geocodingId, providerName,
                Instant.now(clock).plus(PREVIEW_TTL), Snapshot.of(entity), List.of());
        previews.put(userId, pending);
        try {
            var candidates = List.copyOf(providerFactory.reconciliationCandidates(providerName, entity.getRequestCoordinates())
                    .await().indefinitely());
            var preview = new Preview(pending.id(), geocodingId, providerName, pending.expiresAt(), pending.snapshot(), candidates);
            var rows = new java.util.ArrayList<GeocodingCandidatePreview.Candidate>();
            for (int i = 0; i < candidates.size(); i++) {
                var candidate = candidates.get(i);
                var result = candidate.result();
                var point = result.getResultCoordinates();
                var request = entity.getRequestCoordinates();
                rows.add(new GeocodingCandidatePreview.Candidate(i, result.getFormattedDisplayName(), candidate.category(),
                        candidate.address(), result.getCity(), result.getCountry(), point == null ? null : point.getY(),
                        point == null ? null : point.getX(), point == null ? null :
                        GeoUtils.haversine(request.getY(), request.getX(), point.getY(), point.getX())));
            }
            if (!previews.replace(userId, pending, preview)) throw invalidPreview();
            return new GeocodingCandidatePreview(preview.id(), rows);
        } catch (RuntimeException e) {
            previews.remove(userId, pending);
            throw e;
        }
    }

    @Transactional
    public ReverseGeocodingDTO selectCandidate(UUID userId, Long geocodingId, GeocodingCandidateSelection selection) {
        var entity = accessibleEntity(userId, geocodingId);
        geocodingRepository.getEntityManager().lock(entity, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        geocodingRepository.getEntityManager().refresh(entity);
        // Recheck ownership after acquiring the lock as another request may have changed the entry.
        if (entity.getUser() != null && !entity.isOwnedBy(userId)) {
            throw new GeoPulseException(GEOCODING_ACCESS_DENIED, "Access denied");
        }
        var preview = previews.get(userId);
        if (preview == null || !preview.id().equals(selection.previewId())
                || !preview.geocodingId().equals(geocodingId) || !preview.expiresAt().isAfter(Instant.now(clock))
                || !preview.snapshot().equals(Snapshot.of(entity)) || selection.candidateId() < 0
                || selection.candidateId() >= preview.candidates().size()) throw invalidPreview();
        requireEnabledProvider(preview.provider());
        var chosen = applyNormalizationRules(userId, preview.candidates().get(selection.candidateId()).result());
        if (!previews.remove(userId, preview)) throw invalidPreview();
        return dtoMapper.toDTO(copyOnWriteHandler.handleReconciliation(userId, entity, chosen).entity());
    }

    private ReverseGeocodingLocationEntity accessibleEntity(UUID userId, Long geocodingId) {
        var entity = geocodingRepository.findById(geocodingId);
        if (entity == null) throw new GeoPulseException(GEOCODING_RESULT_NOT_FOUND, "Geocoding result not found");
        if (entity.getUser() != null && !entity.isOwnedBy(userId)) {
            throw new GeoPulseException(GEOCODING_ACCESS_DENIED, "Access denied");
        }
        return entity;
    }

    private void requireEnabledProvider(String provider) {
        if (providerFactory.getEnabledProviders().stream().noneMatch(name -> name.equalsIgnoreCase(provider))) {
            throw new GeoPulseException(INVALID_GEOCODING_REQUEST, "Unknown or disabled geocoding provider");
        }
    }

    private GeoPulseException invalidPreview() {
        return new GeoPulseException(INVALID_GEOCODING_REQUEST,
                "Candidate preview expired or changed. Reload candidates and select again.");
    }

    private FormattableGeocodingResult applyNormalizationRules(UUID userId, FormattableGeocodingResult result) {
        UserLocationNormalizationService.NormalizedLocation normalized =
                normalizationService.normalizeForUser(userId, result.getCity(), result.getCountry());
        if (!normalized.changed()) {
            return result;
        }

        return SimpleFormattableResult.builder()
                .requestCoordinates(result.getRequestCoordinates())
                .resultCoordinates(result.getResultCoordinates())
                .boundingBox(result.getBoundingBox())
                .formattedDisplayName(result.getFormattedDisplayName())
                .providerName(result.getProviderName())
                .city(normalized.city())
                .country(normalized.country())
                .build();
    }
}
