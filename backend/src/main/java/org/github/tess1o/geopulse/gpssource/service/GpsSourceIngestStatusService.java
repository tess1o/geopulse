package org.github.tess1o.geopulse.gpssource.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.service.GpsIngestRejection;
import org.github.tess1o.geopulse.gps.service.GpsPointService.GpsIngestSummary;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceAuthFailureCode;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceIngestStatusDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceIngestStatusEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceIngestStatusRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Records the latest ingest activity of each GPS source so users can see whether their tracker reaches
 * GeoPulse and why points are skipped. Recording is best effort: failures are logged and never affect ingestion.
 */
@ApplicationScoped
@Slf4j
public class GpsSourceIngestStatusService {

    private static final TypeReference<Map<String, Object>> PARAMS_TYPE = new TypeReference<>() {
    };

    private final GpsSourceIngestStatusRepository repository;
    private final ObjectMapper objectMapper;

    @Inject
    public GpsSourceIngestStatusService(GpsSourceIngestStatusRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records one authenticated ingest request for a source.
     */
    public void recordIngest(GpsSourceConfigEntity config, GpsIngestSummary summary) {
        if (config == null || config.getId() == null || summary == null) {
            return;
        }
        try {
            Instant now = Instant.now();
            GpsIngestRejection rejection = summary.lastRejection();
            repository.upsertIngest(config.getId(), now,
                    summary.saved() > 0 ? now : null,
                    summary.newestSavedTimestamp(),
                    rejection != null ? now : null,
                    rejection != null ? rejection.code().name() : null,
                    rejection != null ? writeParams(rejection.params()) : null);
        } catch (Exception e) {
            log.warn("Failed to record ingest status for GPS source {}: {}", config.getId(), e.getMessage());
        }
    }

    /**
     * Records a refused request that could be attributed to a source (known username, wrong password).
     */
    public void recordAuthFailure(GpsSourceConfigEntity config, GpsSourceAuthFailureCode code) {
        if (config == null || config.getId() == null || code == null) {
            return;
        }
        try {
            repository.upsertAuthFailure(config.getId(), Instant.now(), code.name());
        } catch (Exception e) {
            log.warn("Failed to record auth failure for GPS source {}: {}", config.getId(), e.getMessage());
        }
    }

    public Map<UUID, GpsSourceIngestStatusDTO> findBySourceConfigIds(Collection<UUID> sourceConfigIds) {
        return repository.findBySourceConfigIds(sourceConfigIds).stream()
                .collect(Collectors.toMap(GpsSourceIngestStatusEntity::getSourceConfigId, this::toDTO));
    }

    private GpsSourceIngestStatusDTO toDTO(GpsSourceIngestStatusEntity entity) {
        return new GpsSourceIngestStatusDTO(
                entity.getLastReceivedAt(),
                entity.getLastSavedAt(),
                entity.getLastSavedPointTimestamp(),
                entity.getLastRejectedAt(),
                entity.getLastRejectionCode(),
                readParams(entity.getLastRejectionParams()),
                entity.getLastAuthFailureAt(),
                entity.getLastAuthFailureCode());
    }

    private String writeParams(Map<String, Object> params) throws JsonProcessingException {
        return params == null || params.isEmpty() ? null : objectMapper.writeValueAsString(params);
    }

    private Map<String, Object> readParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, PARAMS_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Ignoring unreadable ingest rejection params: {}", e.getMessage());
            return Map.of();
        }
    }
}
