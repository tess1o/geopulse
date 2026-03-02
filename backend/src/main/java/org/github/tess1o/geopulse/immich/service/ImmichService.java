package org.github.tess1o.geopulse.immich.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.immich.client.ImmichClient;
import org.github.tess1o.geopulse.immich.model.*;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class ImmichService {
    private static final int PHOTO_SEARCH_RESPONSE_MAX_LIMIT = 5000;

    private final ConcurrentHashMap<PhotoSearchCacheKey, CachedPhotoSearchResult> photoSearchCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PhotoSearchCacheKey, CompletableFuture<List<ImmichPhotoDto>>> inFlightPhotoSearches = new ConcurrentHashMap<>();

    @Inject
    ImmichClient immichClient;

    @Inject
    UserRepository userRepository;

    @ConfigProperty(name = "immich.photos.search-cache-ttl-seconds", defaultValue = "300")
    long photoSearchCacheTtlSeconds;

    @ConfigProperty(name = "immich.photos.search-cache-max-entries", defaultValue = "200")
    int photoSearchCacheMaxEntries;

    public CompletableFuture<ImmichPhotoSearchResponse> searchPhotos(UUID userId, ImmichPhotoSearchRequest searchRequest) {
        log.debug("Searching photos for user {} with params: {}", userId, searchRequest);
        return loadAllFilteredPhotos(userId, searchRequest)
                .thenApply(allFilteredPhotos -> buildSearchResponse(allFilteredPhotos, searchRequest.getLimit()));
    }

    public CompletableFuture<ImmichPhotoMapMarkersResponse> getPhotoMapMarkers(
            UUID userId,
            ImmichPhotoSearchRequest searchRequest,
            Integer coordinatePrecision
    ) {
        int safePrecision = sanitizeCoordinatePrecision(coordinatePrecision);

        return loadAllFilteredPhotos(userId, searchRequest).thenApply((allFilteredPhotos) -> {
            Map<String, MapMarkerAccumulator> groups = new LinkedHashMap<>();
            int geotaggedCount = 0;

            for (ImmichPhotoDto photo : allFilteredPhotos) {
                if (photo.getLatitude() == null || photo.getLongitude() == null) {
                    continue;
                }

                geotaggedCount++;
                double roundedLat = roundCoordinate(photo.getLatitude(), safePrecision);
                double roundedLon = roundCoordinate(photo.getLongitude(), safePrecision);
                String key = buildMarkerKey(roundedLat, roundedLon);

                groups.compute(key, (ignored, existing) -> {
                    if (existing == null) {
                        return new MapMarkerAccumulator(roundedLat, roundedLon, photo.getTakenAt(), 1);
                    }
                    return existing.add(photo.getTakenAt());
                });
            }

            List<ImmichPhotoMapMarkerDto> markers = groups.values().stream()
                    .map(group -> ImmichPhotoMapMarkerDto.builder()
                            .latitude(group.latitude())
                            .longitude(group.longitude())
                            .count(group.count())
                            .latestTakenAt(group.latestTakenAt())
                            .build())
                    .sorted(Comparator.comparing(
                            ImmichPhotoMapMarkerDto::getLatestTakenAt,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .toList();

            return ImmichPhotoMapMarkersResponse.builder()
                    .markers(markers)
                    .totalPhotos(allFilteredPhotos.size())
                    .geotaggedPhotos(geotaggedCount)
                    .build();
        });
    }

    public CompletableFuture<ImmichPhotoSearchResponse> getPhotosForMapMarker(
            UUID userId,
            ImmichPhotoSearchRequest searchRequest,
            double markerLatitude,
            double markerLongitude,
            Integer coordinatePrecision,
            Integer limit
    ) {
        int safePrecision = sanitizeCoordinatePrecision(coordinatePrecision);
        double roundedMarkerLat = roundCoordinate(markerLatitude, safePrecision);
        double roundedMarkerLon = roundCoordinate(markerLongitude, safePrecision);
        String markerKey = buildMarkerKey(roundedMarkerLat, roundedMarkerLon);

        return loadAllFilteredPhotos(userId, searchRequest).thenApply((allFilteredPhotos) -> {
            List<ImmichPhotoDto> markerPhotos = allFilteredPhotos.stream()
                    .filter(photo -> photo.getLatitude() != null && photo.getLongitude() != null)
                    .filter(photo -> {
                        double roundedLat = roundCoordinate(photo.getLatitude(), safePrecision);
                        double roundedLon = roundCoordinate(photo.getLongitude(), safePrecision);
                        return buildMarkerKey(roundedLat, roundedLon).equals(markerKey);
                    })
                    .toList();

            return buildSearchResponse(markerPhotos, limit);
        });
    }

    public CompletableFuture<byte[]> getPhotoThumbnail(UUID userId, String photoId) {
        return getPhotoBytes(userId, photoId, true);
    }

    public CompletableFuture<byte[]> getPhotoOriginal(UUID userId, String photoId) {
        return getPhotoBytes(userId, photoId, false);
    }

    public Optional<ImmichConfigResponse> getUserImmichConfig(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            return Optional.empty();
        }

        ImmichPreferences immichPrefs = user.getImmichPreferences();
        if (immichPrefs == null) {
            return Optional.empty();
        }

        return Optional.of(ImmichConfigResponse.builder()
                .serverUrl(immichPrefs.getServerUrl())
                .enabled(immichPrefs.getEnabled())
                .apiKey(immichPrefs.getApiKey())
                .build());
    }

    @Transactional
    public void updateUserImmichConfig(UUID userId, UpdateImmichConfigRequest request) {
        log.debug("Updating Immich config for user {} and request {}", userId, request);

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        ImmichPreferences immichPrefs = ImmichPreferences.builder()
                .serverUrl(normalizeServerUrl(request.getServerUrl()))
                .apiKey(request.getApiKey())
                .enabled(request.getEnabled())
                .build();

        log.debug("Immich config for user {}: {}", userId, immichPrefs);

        user.setImmichPreferences(immichPrefs);
        userRepository.persist(user);
        invalidateSearchCacheForUser(userId);

        log.info("Updated Immich config for user {}", userId);
    }

    public CompletableFuture<TestImmichConnectionResponse> testImmichConnection(UUID userId, TestImmichConnectionRequest request) {
        log.debug("Testing Immich connection for user {} with server URL: {}", userId, request.getServerUrl());

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            return CompletableFuture.completedFuture(TestImmichConnectionResponse.builder()
                    .success(false)
                    .message("User not found")
                    .build());
        }

        String serverUrl = normalizeServerUrl(request.getServerUrl());
        String apiKey = request.getApiKey();

        // If no API key provided in request, try to use the saved one from DB
        if (apiKey == null || apiKey.isBlank()) {
            ImmichPreferences immichPrefs = user.getImmichPreferences();
            if (immichPrefs != null && immichPrefs.getApiKey() != null && !immichPrefs.getApiKey().isBlank()) {
                apiKey = immichPrefs.getApiKey();
                log.debug("Using saved API key from database for connection test");
            } else {
                return CompletableFuture.completedFuture(TestImmichConnectionResponse.builder()
                        .success(false)
                        .message("API key is required")
                        .details("No API key provided and no saved API key found in database")
                        .build());
            }
        }

        // Create a minimal search request to test the connection
        ImmichSearchRequest testSearchRequest = ImmichSearchRequest.builder()
                .type("IMAGE")
                .withExif(false)
                .build();

        return immichClient.searchAssets(serverUrl, apiKey, testSearchRequest)
                .thenApply(response -> {
                    log.info("Successfully connected to Immich server at {} for user {}", serverUrl, userId);
                    int totalAssets = response != null
                            && response.getAssets() != null
                            && response.getAssets().getTotal() != null
                            ? response.getAssets().getTotal()
                            : 0;
                    return TestImmichConnectionResponse.builder()
                            .success(true)
                            .message("Successfully connected to Immich server")
                            .details(String.format("Server responded with %d total assets", totalAssets))
                            .build();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to connect to Immich server at {} for user {}: {}",
                            serverUrl, userId, throwable.getMessage());

                    String errorMessage = "Failed to connect to Immich server";
                    String details = throwable.getMessage();

                    if (throwable.getMessage() != null) {
                        if (throwable.getMessage().contains("401") || throwable.getMessage().contains("Unauthorized")) {
                            errorMessage = "Authentication failed";
                            details = "Invalid API key or unauthorized access";
                        } else if (throwable.getMessage().contains("404") || throwable.getMessage().contains("Not Found")) {
                            errorMessage = "Server not found";
                            details = "Could not reach the Immich server at the provided URL";
                        } else if (throwable.getMessage().contains("timeout") || throwable.getMessage().contains("Connection refused")) {
                            errorMessage = "Connection timeout";
                            details = "Unable to connect to the server. Please check the URL and network connection";
                        }
                    }

                    return TestImmichConnectionResponse.builder()
                            .success(false)
                            .message(errorMessage)
                            .details(details)
                            .build();
                });
    }

    private CompletableFuture<byte[]> getPhotoBytes(UUID userId, String photoId, boolean thumbnail) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        ImmichPreferences immichPrefs = user.getImmichPreferences();
        if (immichPrefs == null || !Boolean.TRUE.equals(immichPrefs.getEnabled())) {
            throw new IllegalStateException("Immich not configured or disabled for user: " + userId);
        }

        if (thumbnail) {
            return immichClient.getThumbnail(immichPrefs.getServerUrl(), immichPrefs.getApiKey(), photoId);
        } else {
            return immichClient.getOriginal(immichPrefs.getServerUrl(), immichPrefs.getApiKey(), photoId);
        }
    }

    private CompletableFuture<List<ImmichPhotoDto>> loadAllFilteredPhotos(UUID userId, ImmichPhotoSearchRequest searchRequest) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        ImmichPreferences immichPrefs = user.getImmichPreferences();
        if (immichPrefs == null || !Boolean.TRUE.equals(immichPrefs.getEnabled())) {
            log.debug("Immich not configured or disabled for user {}", userId);
            return CompletableFuture.completedFuture(List.of());
        }

        ImmichSearchRequest immichSearchRequest = createImmichSearchRequest(searchRequest);
        PhotoSearchCacheKey cacheKey = PhotoSearchCacheKey.from(userId, searchRequest, immichSearchRequest);

        List<ImmichPhotoDto> cachedPhotos = getCachedSearchResult(cacheKey);
        if (cachedPhotos != null) {
            log.debug("Returning cached Immich search result for user {} with {} photos", userId, cachedPhotos.size());
            return CompletableFuture.completedFuture(cachedPhotos);
        }

        return inFlightPhotoSearches.computeIfAbsent(cacheKey, ignored ->
                immichClient.searchAssetsAllPages(immichPrefs.getServerUrl(), immichPrefs.getApiKey(), immichSearchRequest)
                        .thenApply(response -> {
                            List<ImmichPhotoDto> allFilteredPhotos = extractAndFilterPhotos(response, searchRequest, userId);
                            cacheSearchResult(cacheKey, allFilteredPhotos);
                            return allFilteredPhotos;
                        })
                        .whenComplete((ignoredResult, throwable) -> {
                            inFlightPhotoSearches.remove(cacheKey);
                            if (throwable != null) {
                                log.error("Failed to search photos for user {}: {}", userId, throwable.getMessage(), throwable);
                            }
                        })
        );
    }

    private ImmichSearchRequest createImmichSearchRequest(ImmichPhotoSearchRequest searchRequest) {
        return ImmichSearchRequest.builder()
                .takenAfter(searchRequest.getStartDate().format(DateTimeFormatter.ISO_INSTANT))
                .takenBefore(searchRequest.getEndDate().format(DateTimeFormatter.ISO_INSTANT))
                .type("IMAGE")
                .city(trimToNull(searchRequest.getCity()))
                .country(trimToNull(searchRequest.getCountry()))
                .withExif(true)
                .build();
    }

    private boolean filterByLocation(ImmichAsset asset, ImmichPhotoSearchRequest searchRequest) {
        if (searchRequest.getLatitude() == null || searchRequest.getLongitude() == null || searchRequest.getRadiusMeters() == null) {
            return true;
        }

        ImmichExifInfo exifInfo = asset.getExifInfo();
        if (exifInfo == null || exifInfo.getLatitude() == null || exifInfo.getLongitude() == null) {
            return false;
        }

        double distance = GeoUtils.haversine(
                searchRequest.getLatitude(), searchRequest.getLongitude(),
                exifInfo.getLatitude(), exifInfo.getLongitude()
        );

        return distance <= searchRequest.getRadiusMeters();
    }

    private ImmichPhotoDto mapToPhotoDto(ImmichAsset asset, UUID userId) {
        ImmichPhotoDto.ImmichPhotoDtoBuilder builder = ImmichPhotoDto.builder()
                .id(asset.getId())
                .originalFileName(asset.getOriginalFileName())
                .takenAt(asset.getTakenAt())
                .thumbnailUrl("/api/users/" + userId + "/immich/photos/" + asset.getId() + "/thumbnail")
                .downloadUrl("/api/users/" + userId + "/immich/photos/" + asset.getId() + "/download");

        if (asset.getExifInfo() != null) {
            builder.latitude(asset.getExifInfo().getLatitude())
                    .longitude(asset.getExifInfo().getLongitude());
        }

        return builder.build();
    }

    private String normalizeServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return serverUrl;
        }

        String normalized = serverUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private int resolveLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.min(requestedLimit, PHOTO_SEARCH_RESPONSE_MAX_LIMIT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ImmichPhotoDto pickMostRecentPhoto(ImmichPhotoDto first, ImmichPhotoDto second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        if (first.getTakenAt() == null) {
            return second;
        }
        if (second.getTakenAt() == null) {
            return first;
        }
        return first.getTakenAt().isAfter(second.getTakenAt()) ? first : second;
    }

    private List<ImmichPhotoDto> extractAndFilterPhotos(ImmichSearchResponse response, ImmichPhotoSearchRequest searchRequest, UUID userId) {
        List<ImmichAsset> assets = response.getAssets() != null && response.getAssets().getItems() != null
                ? response.getAssets().getItems()
                : List.of();

        return assets.stream()
                .filter(asset -> filterByLocation(asset, searchRequest))
                .map(asset -> mapToPhotoDto(asset, userId))
                .collect(Collectors.toMap(
                        ImmichPhotoDto::getId,
                        photo -> photo,
                        this::pickMostRecentPhoto,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(
                        ImmichPhotoDto::getTakenAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .collect(Collectors.toList());
    }

    private ImmichPhotoSearchResponse buildSearchResponse(List<ImmichPhotoDto> allFilteredPhotos, Integer limit) {
        List<ImmichPhotoDto> filteredPhotos = allFilteredPhotos.stream()
                .limit(resolveLimit(limit))
                .collect(Collectors.toList());

        return ImmichPhotoSearchResponse.builder()
                .photos(filteredPhotos)
                .totalCount(allFilteredPhotos.size())
                .build();
    }

    private int sanitizeCoordinatePrecision(Integer precision) {
        int defaultPrecision = 4;
        if (precision == null) {
            return defaultPrecision;
        }
        return Math.max(3, Math.min(6, precision));
    }

    private double roundCoordinate(double value, int precision) {
        double factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }

    private String buildMarkerKey(double roundedLatitude, double roundedLongitude) {
        return roundedLatitude + "," + roundedLongitude;
    }

    private List<ImmichPhotoDto> getCachedSearchResult(PhotoSearchCacheKey cacheKey) {
        evictExpiredSearchCacheEntries();
        long nowEpochMillis = Instant.now().toEpochMilli();
        CachedPhotoSearchResult cachedResult = photoSearchCache.computeIfPresent(cacheKey, (ignored, value) -> {
            if (value.expiresAtEpochMillis() <= nowEpochMillis) {
                return null;
            }
            return value.touch(nowEpochMillis);
        });
        if (cachedResult == null) {
            return null;
        }
        return cachedResult.photos();
    }

    private void cacheSearchResult(PhotoSearchCacheKey cacheKey, List<ImmichPhotoDto> allFilteredPhotos) {
        long nowEpochMillis = Instant.now().toEpochMilli();
        long ttlSeconds = Math.max(photoSearchCacheTtlSeconds, 1L);
        long expiresAtEpochMillis = nowEpochMillis + ttlSeconds * 1000L;
        photoSearchCache.put(cacheKey, new CachedPhotoSearchResult(
                List.copyOf(allFilteredPhotos),
                expiresAtEpochMillis,
                nowEpochMillis
        ));

        evictExpiredSearchCacheEntries();
        evictSearchCacheEntriesForSizeLimit();
    }

    private void invalidateSearchCacheForUser(UUID userId) {
        int before = photoSearchCache.size();
        photoSearchCache.entrySet().removeIf(entry -> entry.getKey().userId().equals(userId));
        int removed = before - photoSearchCache.size();
        if (removed > 0) {
            log.debug("Invalidated {} Immich photo cache entries for user {}", removed, userId);
        }
        inFlightPhotoSearches.entrySet().removeIf(entry -> entry.getKey().userId().equals(userId));
    }

    private void evictExpiredSearchCacheEntries() {
        long nowEpochMillis = Instant.now().toEpochMilli();
        photoSearchCache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMillis() <= nowEpochMillis);
    }

    private void evictSearchCacheEntriesForSizeLimit() {
        int maxEntries = Math.max(photoSearchCacheMaxEntries, 1);
        int overflow = photoSearchCache.size() - maxEntries;
        if (overflow <= 0) {
            return;
        }

        List<PhotoSearchCacheKey> keysToRemove = photoSearchCache.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<PhotoSearchCacheKey, CachedPhotoSearchResult>>comparingLong(
                                        entry -> entry.getValue().lastAccessEpochMillis())
                                .thenComparingLong(entry -> entry.getValue().expiresAtEpochMillis())
                )
                .limit(overflow)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        keysToRemove.forEach(photoSearchCache::remove);
        if (!keysToRemove.isEmpty()) {
            log.debug("Evicted {} Immich photo cache entries due to size limit {}", keysToRemove.size(), maxEntries);
        }
    }

    private record CachedPhotoSearchResult(List<ImmichPhotoDto> photos, long expiresAtEpochMillis,
                                           long lastAccessEpochMillis) {
        private CachedPhotoSearchResult touch(long touchedAtEpochMillis) {
            return new CachedPhotoSearchResult(photos, expiresAtEpochMillis, touchedAtEpochMillis);
        }
    }

    private record MapMarkerAccumulator(
            double latitude,
            double longitude,
            java.time.OffsetDateTime latestTakenAt,
            int count
    ) {
        private MapMarkerAccumulator add(java.time.OffsetDateTime takenAt) {
            java.time.OffsetDateTime latest = latestTakenAt;
            if (latest == null || (takenAt != null && takenAt.isAfter(latest))) {
                latest = takenAt;
            }
            return new MapMarkerAccumulator(latitude, longitude, latest, count + 1);
        }
    }

    private record PhotoSearchCacheKey(
            UUID userId,
            String takenAfter,
            String takenBefore,
            Double latitude,
            Double longitude,
            Double radiusMeters,
            String city,
            String country
    ) {
        static PhotoSearchCacheKey from(UUID userId, ImmichPhotoSearchRequest request, ImmichSearchRequest immichRequest) {
            return new PhotoSearchCacheKey(
                    userId,
                    immichRequest.getTakenAfter(),
                    immichRequest.getTakenBefore(),
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getRadiusMeters(),
                    trim(request.getCity()),
                    trim(request.getCountry())
            );
        }

        private static String trim(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
