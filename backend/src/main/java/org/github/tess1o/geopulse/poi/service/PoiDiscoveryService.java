package org.github.tess1o.geopulse.poi.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.poi.client.wikidata.WikidataPoiClient;
import org.github.tess1o.geopulse.poi.dto.PoiDto;
import org.github.tess1o.geopulse.poi.dto.PoiSearchResponseDto;
import org.github.tess1o.geopulse.poi.model.PoiCandidate;
import org.github.tess1o.geopulse.poi.model.entity.PoiAreaQueryEntity;
import org.github.tess1o.geopulse.poi.model.entity.PoiCacheEntity;
import org.github.tess1o.geopulse.poi.repository.PoiAreaQueryRepository;
import org.github.tess1o.geopulse.poi.repository.PoiCacheRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Finds places worth visiting around a point, backed by a global cache.
 *
 * <p>A provider query takes several seconds and Wikidata is shared public infrastructure,
 * so the area is fetched at most once per TTL regardless of how many users or repeat
 * visits hit the same place. The cache is intentionally not user-scoped: a place in Paris
 * is the same fact for everybody.
 */
@ApplicationScoped
@Slf4j
public class PoiDiscoveryService {

    public static final int MIN_RADIUS_METERS = 500;
    public static final int MAX_RADIUS_METERS = 50_000;
    private static final int MAX_LIMIT = 60;
    private static final double METERS_PER_DEGREE_LAT = 111_320d;

    private final PoiConfigurationService config;
    private final PoiCacheRepository poiRepository;
    private final PoiAreaQueryRepository areaRepository;
    private final WikidataPoiClient wikidataPoiClient;
    private final PoiImageService imageService;

    @Inject
    public PoiDiscoveryService(PoiConfigurationService config,
                               PoiCacheRepository poiRepository,
                               PoiAreaQueryRepository areaRepository,
                               WikidataPoiClient wikidataPoiClient,
                               PoiImageService imageService) {
        this.config = config;
        this.poiRepository = poiRepository;
        this.areaRepository = areaRepository;
        this.wikidataPoiClient = wikidataPoiClient;
        this.imageService = imageService;
    }

    /** Bounding box, in the order the SPARQL service expects. */
    public record BoundingBox(double south, double north, double west, double east) {
    }

    @Transactional
    public PoiSearchResponseDto search(double latitude, double longitude, int radiusMeters, Integer requestedLimit) {
        if (!config.isEnabled()) {
            return new PoiSearchResponseDto(List.of(), PoiDto.DATA_ATTRIBUTION, true);
        }

        int radius = clampRadius(radiusMeters);
        int limit = clampLimit(requestedLimit);
        BoundingBox box = boxAround(latitude, longitude, radius);
        String areaHash = hashArea(box);
        Instant now = Instant.now();

        boolean fresh = areaRepository.findFresh(areaHash, now).isPresent();
        if (!fresh) {
            refreshArea(box, areaHash, now);
        }

        // The stored box is square, so its corners reach ~1.41x further than the radius the
        // caller asked for, and the repository applies no ordering. Over-fetch, then filter
        // by true distance and sort nearest-first - otherwise "what's here" returned an
        // arbitrary three places, only some of which were within the requested radius.
        int candidateLimit = Math.max(limit * 5, 25);
        List<PoiCacheEntity> candidates = poiRepository.findUnexpiredInBox(
                box.south(), box.north(), box.west(), box.east(), now, candidateLimit);

        List<PoiCacheEntity> nearest = candidates.stream()
                .map(poi -> Map.entry(poi, distanceMeters(latitude, longitude, poi)))
                .filter(entry -> entry.getValue() <= radius)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();

        return new PoiSearchResponseDto(toDtos(nearest), PoiDto.DATA_ATTRIBUTION, fresh);
    }

    /** Great-circle distance from the search point to a cached POI, in metres. */
    private static double distanceMeters(double latitude, double longitude, PoiCacheEntity poi) {
        return GeoUtils.haversine(latitude, longitude, poi.getLatitude(), poi.getLongitude());
    }

    /** Fetches the area from the provider and stores the results. */
    private void refreshArea(BoundingBox box, String areaHash, Instant now) {
        List<PoiCandidate> candidates;
        try {
            candidates = wikidataPoiClient
                    .searchBox(box.south(), box.north(), box.west(), box.east(), config.getMaxPois())
                    .await().atMost(java.time.Duration.ofSeconds(60));
        } catch (Exception e) {
            // Do not record the area as fetched: a provider failure must not be cached as
            // "this area has no places", which would look like a valid empty answer.
            log.warn("POI provider lookup failed for area {}; not caching the result", areaHash, e);
            throw new PoiUnavailableException("Could not reach the place database", e);
        }

        for (PoiCandidate candidate : candidates) {
            upsert(candidate, now);
        }

        areaRepository.persist(PoiAreaQueryEntity.builder()
                .areaHash(areaHash)
                .poiCount(candidates.size())
                .fetchedAt(now)
                .expiresAt(now.plus(config.getCacheTtlDays(), ChronoUnit.DAYS))
                .build());
    }

    private void upsert(PoiCandidate candidate, Instant now) {
        PoiCacheEntity entity = poiRepository
                .findByProviderAndExternalId(wikidataPoiClient.providerName(), candidate.externalId())
                .orElseGet(() -> PoiCacheEntity.builder()
                        .provider(wikidataPoiClient.providerName())
                        .externalId(candidate.externalId())
                        .build());

        entity.setName(candidate.name());
        entity.setDescription(candidate.description());
        entity.setLatitude(candidate.latitude());
        entity.setLongitude(candidate.longitude());
        entity.setImageFile(candidate.imageFile());
        entity.setFetchedAt(now);
        entity.setExpiresAt(now.plus(config.getCacheTtlDays(), ChronoUnit.DAYS));
        poiRepository.persist(entity);
    }

    /**
     * Resolves licence metadata for the whole page in one Commons call, and only exposes an
     * image URL where a licence was actually established.
     */
    private List<PoiDto> toDtos(List<PoiCacheEntity> entities) {
        List<String> files = entities.stream()
                .map(PoiCacheEntity::getImageFile)
                .filter(Objects::nonNull)
                .toList();

        Map<String, PoiImageService.CommonsImage> licenses = files.isEmpty()
                ? Map.of()
                : imageService.resolveFiles(files);

        List<PoiDto> results = new ArrayList<>(entities.size());
        for (PoiCacheEntity entity : entities) {
            PoiImageService.CommonsImage image = entity.getImageFile() == null
                    ? null
                    : licenses.get(entity.getImageFile());

            results.add(new PoiDto(
                    entity.getId(),
                    entity.getExternalId(),
                    entity.getName(),
                    entity.getDescription(),
                    entity.getLatitude(),
                    entity.getLongitude(),
                    image != null ? imageUrl(entity.getId()) : null,
                    image != null ? image.author() : null,
                    image != null ? image.licenseName() : null,
                    image != null ? image.licenseUrl() : null,
                    image != null ? image.filePageUrl() : null));
        }
        return results;
    }

    /**
     * Relative to the API base, matching how the frontend addresses other authenticated
     * binary endpoints. The image cannot be a bare {@code <img src>}: it requires a bearer
     * token, so the client fetches it with credentials and renders a blob URL.
     */
    static String imageUrl(Long poiId) {
        return "/poi/images/" + poiId + "/thumbnail";
    }

    static BoundingBox boxAround(double latitude, double longitude, int radiusMeters) {
        double latDelta = radiusMeters / METERS_PER_DEGREE_LAT;
        double cos = Math.cos(Math.toRadians(latitude));
        // Guard against the poles, where the longitude span would explode.
        double lonDelta = Math.abs(cos) < 1e-6 ? 180d : radiusMeters / (METERS_PER_DEGREE_LAT * cos);

        return new BoundingBox(
                Math.max(-90, latitude - latDelta),
                Math.min(90, latitude + latDelta),
                Math.max(-180, longitude - lonDelta),
                Math.min(180, longitude + lonDelta));
    }

    private static int clampRadius(int radiusMeters) {
        return Math.max(MIN_RADIUS_METERS, Math.min(MAX_RADIUS_METERS, radiusMeters));
    }

    private int clampLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return config.getMaxPois();
        }
        return Math.max(1, Math.min(MAX_LIMIT, requestedLimit));
    }

    /** Rounded to ~100 m so trivially different clicks share one cache entry. */
    static String hashArea(BoundingBox box) {
        String normalized = String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.3f",
                box.south(), box.north(), box.west(), box.east());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Signals a provider problem, as opposed to a genuine empty result. */
    public static class PoiUnavailableException extends RuntimeException {
        public PoiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
