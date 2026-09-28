package org.github.tess1o.geopulse.notes.service;

import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TimelineNoteLocationServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant RANGE_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant RANGE_END = Instant.parse("2026-01-02T00:00:00Z");

    @Mock
    TimelineStayRepository stayRepository;

    @Mock
    TimelineTripRepository tripRepository;

    TimelineNoteLocationService service;

    @BeforeEach
    void setUp() {
        service = new TimelineNoteLocationService();
        service.stayRepository = stayRepository;
        service.tripRepository = tripRepository;
    }

    @Test
    void noteInsideDataGapSnapsToClosestPrecedingStay() {
        TimelineStayEntity before = TimelineStayEntity.builder()
                .id(1L)
                .timestamp(Instant.parse("2026-01-01T08:00:00Z"))
                .stayDuration(600) // ends 08:10
                .location(GeoUtils.createPoint(10.0, 20.0))
                .build();
        TimelineStayEntity after = TimelineStayEntity.builder()
                .id(2L)
                .timestamp(Instant.parse("2026-01-01T12:00:00Z"))
                .stayDuration(600)
                .location(GeoUtils.createPoint(30.0, 40.0))
                .build();

        when(stayRepository.findByUserIdAndTimeRangeWithExpansion(USER_ID, RANGE_START, RANGE_END))
                .thenReturn(List.of(before, after));
        when(tripRepository.findByUserIdAndTimeRangeWithExpansion(USER_ID, RANGE_START, RANGE_END))
                .thenReturn(List.of());

        // Falls in the gap between the two stays, closer to the preceding one (08:10 -> 08:20 = 10 min vs 12:00 - 08:20 ~= 3h40m)
        NoteDto gapNote = NoteDto.builder()
                .eventTime(Instant.parse("2026-01-01T08:20:00Z"))
                .build();

        service.resolveTimelineLocations(USER_ID, RANGE_START, RANGE_END, List.of(gapNote));

        assertEquals(NoteAnchorType.STAY, gapNote.getAnchorType());
        assertEquals(1L, gapNote.getAnchorId());
        assertEquals(NoteLocationSource.DERIVED_GAP_NEIGHBOR, gapNote.getLocationSource());
        assertEquals(20.0, gapNote.getLatitude());
        assertEquals(10.0, gapNote.getLongitude());
    }

    @Test
    void noteInsideDataGapSnapsToClosestFollowingStayWhenCloser() {
        TimelineStayEntity before = TimelineStayEntity.builder()
                .id(1L)
                .timestamp(Instant.parse("2026-01-01T08:00:00Z"))
                .stayDuration(600)
                .location(GeoUtils.createPoint(10.0, 20.0))
                .build();
        TimelineStayEntity after = TimelineStayEntity.builder()
                .id(2L)
                .timestamp(Instant.parse("2026-01-01T12:00:00Z"))
                .stayDuration(600)
                .location(GeoUtils.createPoint(30.0, 40.0))
                .build();

        when(stayRepository.findByUserIdAndTimeRangeWithExpansion(USER_ID, RANGE_START, RANGE_END))
                .thenReturn(List.of(before, after));
        when(tripRepository.findByUserIdAndTimeRangeWithExpansion(USER_ID, RANGE_START, RANGE_END))
                .thenReturn(List.of());

        // Just before the following stay starts.
        NoteDto gapNote = NoteDto.builder()
                .eventTime(Instant.parse("2026-01-01T11:55:00Z"))
                .build();

        service.resolveTimelineLocations(USER_ID, RANGE_START, RANGE_END, List.of(gapNote));

        assertEquals(NoteAnchorType.STAY, gapNote.getAnchorType());
        assertEquals(2L, gapNote.getAnchorId());
        assertEquals(NoteLocationSource.DERIVED_GAP_NEIGHBOR, gapNote.getLocationSource());
        assertEquals(40.0, gapNote.getLatitude());
        assertEquals(30.0, gapNote.getLongitude());
    }

    @Test
    void noteInsideDataGapSnapsToNearestTripEndpoint() {
        TimelineTripEntity trip = TimelineTripEntity.builder()
                .id(5L)
                .timestamp(Instant.parse("2026-01-01T08:00:00Z"))
                .tripDuration(600) // ends 08:10
                .startPoint(GeoUtils.createPoint(1.0, 2.0))
                .endPoint(GeoUtils.createPoint(3.0, 4.0))
                .build();

        when(stayRepository.findByUserIdAndTimeRangeWithExpansion(USER_ID, RANGE_START, RANGE_END))
                .thenReturn(List.of());
        when(tripRepository.findByUserIdAndTimeRangeWithExpansion(USER_ID, RANGE_START, RANGE_END))
                .thenReturn(List.of(trip));

        NoteDto gapNote = NoteDto.builder()
                .eventTime(Instant.parse("2026-01-01T09:00:00Z"))
                .build();

        service.resolveTimelineLocations(USER_ID, RANGE_START, RANGE_END, List.of(gapNote));

        assertEquals(NoteAnchorType.TRIP, gapNote.getAnchorType());
        assertEquals(5L, gapNote.getAnchorId());
        assertEquals(NoteLocationSource.DERIVED_GAP_NEIGHBOR, gapNote.getLocationSource());
        assertEquals(4.0, gapNote.getLatitude());
        assertEquals(3.0, gapNote.getLongitude());
    }

    @Test
    void noteRemainsUnresolvedWhenNoStaysOrTripsExist() {
        when(stayRepository.findByUserIdAndTimeRangeWithExpansion(USER_ID, RANGE_START, RANGE_END))
                .thenReturn(List.of());
        when(tripRepository.findByUserIdAndTimeRangeWithExpansion(USER_ID, RANGE_START, RANGE_END))
                .thenReturn(List.of());

        NoteDto gapNote = NoteDto.builder()
                .eventTime(Instant.parse("2026-01-01T09:00:00Z"))
                .build();

        service.resolveTimelineLocations(USER_ID, RANGE_START, RANGE_END, List.of(gapNote));

        assertNull(gapNote.getAnchorType());
        assertNull(gapNote.getLatitude());
        assertNull(gapNote.getLongitude());
    }
}
