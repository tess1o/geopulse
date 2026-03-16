package org.github.tess1o.geopulse.gpssource.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceTypeTelemetryConfigDTO;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceTypeTelemetryConfigEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsTelemetryMappingEntry;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceTypeTelemetryConfigRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class GpsSourceTypeTelemetryConfigService {

    private final GpsSourceTypeTelemetryConfigRepository telemetryConfigRepository;
    private final GpsTelemetryDefaultsService telemetryDefaultsService;
    private final EntityManager em;

    @Inject
    public GpsSourceTypeTelemetryConfigService(GpsSourceTypeTelemetryConfigRepository telemetryConfigRepository,
                                               GpsTelemetryDefaultsService telemetryDefaultsService,
                                               EntityManager em) {
        this.telemetryConfigRepository = telemetryConfigRepository;
        this.telemetryDefaultsService = telemetryDefaultsService;
        this.em = em;
    }

    public GpsSourceTypeTelemetryConfigDTO getResolvedConfig(UUID userId, GpsSourceType sourceType) {
        Optional<GpsSourceTypeTelemetryConfigEntity> existing = telemetryConfigRepository.findByUserIdAndSourceType(userId, sourceType);

        List<GpsTelemetryMappingEntry> resolvedMapping = telemetryDefaultsService.resolveMapping(
                sourceType,
                existing.map(GpsSourceTypeTelemetryConfigEntity::getMapping).orElse(null)
        );

        return GpsSourceTypeTelemetryConfigDTO.builder()
                .sourceType(sourceType.name())
                .customized(existing.isPresent())
                .mapping(resolvedMapping)
                .build();
    }

    public Map<GpsSourceType, List<GpsTelemetryMappingEntry>> getResolvedMappings(UUID userId, Set<GpsSourceType> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return Map.of();
        }

        List<GpsSourceTypeTelemetryConfigEntity> entities =
                telemetryConfigRepository.findByUserIdAndSourceTypes(userId, sourceTypes);
        Map<GpsSourceType, List<GpsTelemetryMappingEntry>> customizedByType = entities.stream()
                .collect(Collectors.toMap(GpsSourceTypeTelemetryConfigEntity::getSourceType, GpsSourceTypeTelemetryConfigEntity::getMapping));

        Map<GpsSourceType, List<GpsTelemetryMappingEntry>> output = new EnumMap<>(GpsSourceType.class);
        for (GpsSourceType sourceType : sourceTypes) {
            List<GpsTelemetryMappingEntry> resolved = telemetryDefaultsService.resolveMapping(
                    sourceType,
                    customizedByType.get(sourceType)
            );
            if (!resolved.isEmpty()) {
                output.put(sourceType, resolved);
            }
        }

        return output;
    }

    @Transactional
    public GpsSourceTypeTelemetryConfigDTO upsertConfig(UUID userId,
                                                        GpsSourceType sourceType,
                                                        List<GpsTelemetryMappingEntry> mapping) {
        List<GpsTelemetryMappingEntry> sanitized = telemetryDefaultsService.sanitizeMapping(mapping);

        Optional<GpsSourceTypeTelemetryConfigEntity> existing = telemetryConfigRepository.findByUserIdAndSourceType(userId, sourceType);
        GpsSourceTypeTelemetryConfigEntity entity = existing.orElseGet(() -> GpsSourceTypeTelemetryConfigEntity.builder()
                .user(em.getReference(UserEntity.class, userId))
                .sourceType(sourceType)
                .build());

        entity.setMapping(sanitized);

        if (entity.getId() == null) {
            telemetryConfigRepository.persist(entity);
        }

        return GpsSourceTypeTelemetryConfigDTO.builder()
                .sourceType(sourceType.name())
                .customized(true)
                .mapping(telemetryDefaultsService.resolveMapping(sourceType, entity.getMapping()))
                .build();
    }

    @Transactional
    public void resetConfig(UUID userId, GpsSourceType sourceType) {
        telemetryConfigRepository.deleteByUserIdAndSourceType(userId, sourceType);
    }
}
