package org.github.tess1o.geopulse.testsupport;

import io.quarkus.test.junit.QuarkusMock;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AsyncTimelineTestMocks {

    private AsyncTimelineTestMocks() {
    }

    public static void install() {
        AsyncTimelineGenerationService service = mock(AsyncTimelineGenerationService.class);
        when(service.regenerateTimelineAsync(any(UUID.class))).thenAnswer(ignored -> UUID.randomUUID());
        when(service.scheduleTimelineRegenerationFromTimestamp(any(UUID.class), any(Instant.class)))
                .thenAnswer(ignored -> new AsyncTimelineGenerationService.TimelineSchedulingResult(
                        UUID.randomUUID(), true, false));
        QuarkusMock.installMockForType(service, AsyncTimelineGenerationService.class);
    }
}
