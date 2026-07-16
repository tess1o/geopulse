package org.github.tess1o.geopulse.immich.service;

import org.github.tess1o.geopulse.geocoding.service.GeonamesLocationNormalizationService;
import org.github.tess1o.geopulse.immich.client.ImmichClient;
import org.github.tess1o.geopulse.immich.model.ImmichAsset;
import org.github.tess1o.geopulse.immich.model.ImmichExifInfo;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoMapMarkerDto;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoMapMarkersResponse;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchRequest;
import org.github.tess1o.geopulse.immich.model.ImmichPreferences;
import org.github.tess1o.geopulse.immich.model.ImmichSearchRequest;
import org.github.tess1o.geopulse.immich.model.ImmichSearchResponse;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ImmichServiceTest {

    @Mock
    ImmichClient immichClient;

    @Mock
    UserRepository userRepository;

    @Mock
    GeonamesLocationNormalizationService geonamesLocationNormalizationService;

    private ImmichService service;

    @BeforeEach
    void setUp() {
        service = new ImmichService();
        service.immichClient = immichClient;
        service.userRepository = userRepository;
        service.geonamesLocationNormalizationService = geonamesLocationNormalizationService;
    }

    @Test
    void getPhotoMapMarkersIncludesSinglePhotoOnlyForSinglePhotoMarkers() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setImmichPreferences(ImmichPreferences.builder()
                .serverUrl("https://immich.example.test")
                .apiKey("test-api-key")
                .enabled(true)
                .build());

        ImmichPhotoSearchRequest request = new ImmichPhotoSearchRequest();
        request.setStartDate(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        request.setEndDate(OffsetDateTime.parse("2026-01-31T23:59:59Z"));

        ImmichSearchResponse searchResponse = searchResponse(List.of(
                asset("single-photo", "single.jpg", "2026-01-10T12:00:00Z", 10.12345, 20.12345),
                asset("group-a", "group-a.jpg", "2026-01-11T12:00:00Z", 11.12341, 21.12341),
                asset("group-b", "group-b.jpg", "2026-01-12T12:00:00Z", 11.12342, 21.12342)
        ));

        CompletableFuture<ImmichSearchResponse> immichSearchFuture = new CompletableFuture<>();

        when(userRepository.findById(userId)).thenReturn(user);
        when(immichClient.searchAssetsAllPages(
                eq("https://immich.example.test"),
                eq("test-api-key"),
                any(ImmichSearchRequest.class)
        )).thenReturn(immichSearchFuture);

        CompletableFuture<ImmichPhotoMapMarkersResponse> responseFuture = service.getPhotoMapMarkers(userId, request, 4);
        immichSearchFuture.complete(searchResponse);
        ImmichPhotoMapMarkersResponse response = responseFuture.join();

        assertThat(response.getMarkers()).hasSize(2);

        ImmichPhotoMapMarkerDto singleMarker = response.getMarkers().stream()
                .filter(marker -> marker.getCount() == 1)
                .findFirst()
                .orElseThrow();
        assertThat(singleMarker.getSinglePhoto()).isNotNull();
        assertThat(singleMarker.getSinglePhoto().getId()).isEqualTo("single-photo");
        assertThat(singleMarker.getSinglePhoto().getThumbnailUrl())
                .isEqualTo("/api/users/" + userId + "/immich/photos/single-photo/thumbnail");

        ImmichPhotoMapMarkerDto groupedMarker = response.getMarkers().stream()
                .filter(marker -> marker.getCount() == 2)
                .findFirst()
                .orElseThrow();
        assertThat(groupedMarker.getSinglePhoto()).isNull();
    }

    private ImmichSearchResponse searchResponse(List<ImmichAsset> assets) {
        ImmichSearchResponse response = new ImmichSearchResponse();
        ImmichSearchResponse.ImmichSearchAssets searchAssets = new ImmichSearchResponse.ImmichSearchAssets();
        searchAssets.setTotal(assets.size());
        searchAssets.setCount(assets.size());
        searchAssets.setItems(assets);
        response.setAssets(searchAssets);
        return response;
    }

    private ImmichAsset asset(String id, String fileName, String takenAt, double latitude, double longitude) {
        ImmichAsset asset = new ImmichAsset();
        asset.setId(id);
        asset.setOriginalFileName(fileName);
        asset.setTakenAt(OffsetDateTime.parse(takenAt));

        ImmichExifInfo exifInfo = new ImmichExifInfo();
        exifInfo.setLatitude(latitude);
        exifInfo.setLongitude(longitude);
        asset.setExifInfo(exifInfo);

        return asset;
    }
}
