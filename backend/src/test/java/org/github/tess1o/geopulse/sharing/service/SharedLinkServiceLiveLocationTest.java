package org.github.tess1o.geopulse.sharing.service;

import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.sharing.mapper.SharedLinkMapper;
import org.github.tess1o.geopulse.sharing.model.LocationHistoryResponse;
import org.github.tess1o.geopulse.sharing.model.ShareType;
import org.github.tess1o.geopulse.sharing.model.SharedLinkEntity;
import org.github.tess1o.geopulse.sharing.repository.SharedLinkRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.SHARED_LINK_ACCESS_DENIED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Based on the fix proposed by @aroh3006 in https://github.com/tess1o/geopulse/pull/609.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class SharedLinkServiceLiveLocationTest {

    private static final String TOKEN = "temp-token";

    @Mock
    SharedLinkRepository sharedLinkRepository;

    @Mock
    GpsPointRepository gpsPointRepository;

    @Mock
    SharedLinkMapper mapper;

    @Mock
    SharedLinkAccessTokenService accessTokenService;

    private SharedLinkService service;
    private UUID linkId;
    private UUID ownerId;

    @BeforeEach
    void setUp() throws Exception {
        service = new SharedLinkService();
        service.sharedLinkRepository = sharedLinkRepository;
        service.gpsPointRepository = gpsPointRepository;
        service.mapper = mapper;
        service.accessTokenService = accessTokenService;

        linkId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        when(accessTokenService.isValid(TOKEN, linkId)).thenReturn(true);
    }

    @Test
    void timelineShareCannotReadLiveLocation() {
        SharedLinkEntity timelineLink = link(ShareType.TIMELINE);
        timelineLink.setShowCurrentLocation(false);
        when(sharedLinkRepository.findActiveById(linkId)).thenReturn(Optional.of(timelineLink));

        assertThatThrownBy(() -> service.getSharedLocation(linkId, TOKEN))
                .isInstanceOfSatisfying(GeoPulseException.class,
                        exception -> assertThat(exception.code()).isEqualTo(SHARED_LINK_ACCESS_DENIED));

        verifyNoInteractions(gpsPointRepository, mapper);
    }

    @Test
    void timelineShareWithHistoryCannotReadLiveLocation() {
        SharedLinkEntity timelineLink = link(ShareType.TIMELINE);
        timelineLink.setShowHistory(true);
        timelineLink.setHistoryHours(48);
        when(sharedLinkRepository.findActiveById(linkId)).thenReturn(Optional.of(timelineLink));

        assertThatThrownBy(() -> service.getSharedLocation(linkId, TOKEN))
                .isInstanceOfSatisfying(GeoPulseException.class,
                        exception -> assertThat(exception.code()).isEqualTo(SHARED_LINK_ACCESS_DENIED));

        verifyNoInteractions(gpsPointRepository, mapper);
    }

    @Test
    void liveShareStillReturnsLiveLocation() {
        SharedLinkEntity liveLink = link(ShareType.LIVE_LOCATION);
        liveLink.setShowHistory(true);
        liveLink.setHistoryHours(1);
        when(sharedLinkRepository.findActiveById(linkId)).thenReturn(Optional.of(liveLink));

        var current = new LocationHistoryResponse.CurrentLocationData(50.0, 30.0, Instant.now(), null);
        when(gpsPointRepository.findLatestLocation(ownerId)).thenReturn(current);
        when(gpsPointRepository.findLocationHistory(any(), any(Instant.class), any(Instant.class))).thenReturn(List.of());
        LocationHistoryResponse actual = service.getSharedLocation(linkId, TOKEN);
        assertThat(actual.getCurrent()).isSameAs(current);
        assertThat(actual.getHistory()).isEmpty();
        assertThat(actual.getCurrent().getAccuracy()).isNull();
    }

    @Test
    void liveShareWithoutHistoryKeepsLegacyZeroAccuracyDefault() {
        var live = link(ShareType.LIVE_LOCATION);
        live.setShowHistory(false);
        when(sharedLinkRepository.findActiveById(linkId)).thenReturn(Optional.of(live));
        when(gpsPointRepository.findLatestLocation(ownerId))
                .thenReturn(new LocationHistoryResponse.CurrentLocationData(50.0, 30.0, Instant.now(), null));
        var response = service.getSharedLocation(linkId, TOKEN);
        assertThat(response.getCurrent().getAccuracy()).isEqualTo(0.0);
        assertThat(response.getHistory()).isEmpty();
        verify(gpsPointRepository, never()).findLocationHistory(any(), any(), any());
    }

    private SharedLinkEntity link(ShareType shareType) {
        UserEntity owner = new UserEntity();
        owner.setId(ownerId);

        SharedLinkEntity entity = new SharedLinkEntity();
        entity.setId(linkId);
        entity.setUser(owner);
        entity.setShareType(shareType);
        return entity;
    }
}
