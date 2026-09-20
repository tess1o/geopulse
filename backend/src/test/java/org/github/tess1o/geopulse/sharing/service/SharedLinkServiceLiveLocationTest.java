package org.github.tess1o.geopulse.sharing.service;

import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    JWTParser jwtParser;

    @Mock
    JsonWebToken jwt;

    private SharedLinkService service;
    private UUID linkId;
    private UUID ownerId;

    @BeforeEach
    void setUp() throws Exception {
        service = new SharedLinkService();
        service.sharedLinkRepository = sharedLinkRepository;
        service.gpsPointRepository = gpsPointRepository;
        service.mapper = mapper;
        service.jwtParser = jwtParser;

        linkId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        when(jwtParser.parse(TOKEN)).thenReturn(jwt);
        when(jwt.<String>getClaim("type")).thenReturn("temp");
        when(jwt.<String>getClaim("linkId")).thenReturn(linkId.toString());
    }

    @Test
    void timelineShareCannotReadLiveLocation() {
        SharedLinkEntity timelineLink = link(ShareType.TIMELINE);
        timelineLink.setShowCurrentLocation(false);
        when(sharedLinkRepository.findActiveById(linkId)).thenReturn(Optional.of(timelineLink));

        assertThatThrownBy(() -> service.getSharedLocation(linkId, TOKEN))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(gpsPointRepository);
        verify(sharedLinkRepository, never()).incrementViewCount(any());
    }

    @Test
    void timelineShareWithHistoryCannotReadLiveLocation() {
        SharedLinkEntity timelineLink = link(ShareType.TIMELINE);
        timelineLink.setShowHistory(true);
        timelineLink.setHistoryHours(48);
        when(sharedLinkRepository.findActiveById(linkId)).thenReturn(Optional.of(timelineLink));

        assertThatThrownBy(() -> service.getSharedLocation(linkId, TOKEN))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(gpsPointRepository);
    }

    @Test
    void liveShareStillReturnsLiveLocation() {
        SharedLinkEntity liveLink = link(ShareType.LIVE_LOCATION);
        liveLink.setShowHistory(true);
        liveLink.setHistoryHours(1);
        when(sharedLinkRepository.findActiveById(linkId)).thenReturn(Optional.of(liveLink));

        GpsPointEntity latest = new GpsPointEntity();
        when(gpsPointRepository.findByUserIdLatestGpsPoint(ownerId)).thenReturn(latest);
        when(gpsPointRepository.findByUserIdAndTimePeriod(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        LocationHistoryResponse expected = new LocationHistoryResponse(null, List.of());
        when(mapper.toLocationHistoryResponse(latest, List.of())).thenReturn(expected);

        LocationHistoryResponse actual = service.getSharedLocation(linkId, TOKEN);

        assertThat(actual).isSameAs(expected);
        verify(sharedLinkRepository).incrementViewCount(linkId);
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
