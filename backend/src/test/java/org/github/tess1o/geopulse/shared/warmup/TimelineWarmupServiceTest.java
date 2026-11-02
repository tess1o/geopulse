package org.github.tess1o.geopulse.shared.warmup;

import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.repository.TimelineReadRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class TimelineWarmupServiceTest {
    @Test
    void warmupBoundsReadsAndSharesStayTripBudget() {
        var service = new TimelineWarmupService();
        service.timelineReadRepository = mock(TimelineReadRepository.class);
        service.sampleDays = 30;
        service.maxWarmupItems = 2;
        var user = UUID.randomUUID();
        when(service.timelineReadRepository.findStays(eq(user), any(), any(), eq(2)))
                .thenReturn(List.of(new TimelineStayLocationDTO()));
        service.warmupRealQueries(user);
        verify(service.timelineReadRepository).findStays(eq(user), any(), any(), eq(2));
        verify(service.timelineReadRepository).findTrips(eq(user), any(), any(), eq(1));
        verify(service.timelineReadRepository).findGaps(eq(user), any(), any(), isNull(), eq(2));
    }
}
