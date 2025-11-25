package org.github.tess1o.geopulse.immich.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.immich.client.ImmichClient;
import org.github.tess1o.geopulse.immich.model.*;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class ImmichService {

    @Inject
    ImmichClient immichClient;

    @Inject
    UserRepository userRepository;

    public CompletableFuture<ImmichPhotoSearchResponse> searchPhotos(UUID userId, ImmichPhotoSearchRequest searchRequest) {
        log.debug("Searching photos for user {} with params: {}", userId, searchRequest);

        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        ImmichPreferences immichPrefs = user.getImmichPreferences();
        if (immichPrefs == null || !Boolean.TRUE.equals(immichPrefs.getEnabled())) {
            log.debug("Immich not configured or disabled for user {}", userId);
            return CompletableFuture.completedFuture(ImmichPhotoSearchResponse.builder()
                    .photos(List.of())
                    .totalCount(0)
                    .build());
        }

        ImmichSearchRequest immichSearchRequest = ImmichSearchRequest.builder()
                .takenAfter(searchRequest.getStartDate().format(DateTimeFormatter.ISO_INSTANT))
                .takenBefore(searchRequest.getEndDate().format(DateTimeFormatter.ISO_INSTANT))
                .type("IMAGE")
                .withExif(true)
                .build();

        return immichClient.searchAssets(immichPrefs.getServerUrl(), immichPrefs.getApiKey(), immichSearchRequest)
                .thenApply(response -> {
                    List<ImmichAsset> assets = response.getAssets().getItems();

                    List<ImmichPhotoDto> filteredPhotos = assets.stream()
                            .filter(asset -> filterByLocation(asset, searchRequest))
                            .map(asset -> mapToPhotoDto(asset, userId))
                            .collect(Collectors.toList());

                    return ImmichPhotoSearchResponse.builder()
                            .photos(filteredPhotos)
                            .totalCount(filteredPhotos.size())
                            .build();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to search photos for user {}: {}", userId, throwable.getMessage(), throwable);
                    return ImmichPhotoSearchResponse.builder()
                            .photos(List.of())
                            .totalCount(0)
                            .build();
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
                    return TestImmichConnectionResponse.builder()
                            .success(true)
                            .message("Successfully connected to Immich server")
                            .details(String.format("Server responded with %d total assets", response.getAssets().getTotal()))
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
}