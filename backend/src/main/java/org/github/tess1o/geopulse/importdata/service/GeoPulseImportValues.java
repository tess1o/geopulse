package org.github.tess1o.geopulse.importdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shared parsing and value defaults for the GeoPulse ZIP importers.
 */
@Slf4j
final class GeoPulseImportValues {

    static final ObjectMapper IMPORT_JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private GeoPulseImportValues() {
    }

    static UserEntity requireImportingUser(UserRepository userRepository, ImportJob job) {
        UserEntity user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }
        return user;
    }

    static boolean shouldSkipDueToDateFilter(Instant timestamp, ImportJob job) {
        return job.getOptions().isOutsideTimeRange(timestamp);
    }

    static Long resolveMappedId(Long oldId, Map<Long, Long> mappedIds) {
        if (oldId == null) {
            return null;
        }
        return mappedIds.get(oldId);
    }

    static long durationSeconds(Instant start, Instant end, Long fallback) {
        if (fallback != null) {
            return Math.max(0L, fallback);
        }
        if (start != null && end != null && end.isAfter(start)) {
            return end.getEpochSecond() - start.getEpochSecond();
        }
        return 0L;
    }

    static Instant defaultInstant(Instant value) {
        return value == null ? Instant.now() : value;
    }

    static double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown {} value '{}', using fallback {}", enumClass.getSimpleName(), value, fallback);
            return fallback;
        }
    }

    static <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
