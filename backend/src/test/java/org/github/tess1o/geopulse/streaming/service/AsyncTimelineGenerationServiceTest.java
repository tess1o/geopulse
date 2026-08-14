package org.github.tess1o.geopulse.streaming.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AsyncTimelineGenerationServiceTest {

    @Mock
    StreamingTimelineGenerationService timelineGenerationService;

    private ExecutorService executorService;

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void scheduleTimelineRegenerationFromTimestamp_queuesAndCoalescesWhenJobIsActive() {
        UUID userId = UUID.randomUUID();
        Instant laterTimestamp = Instant.parse("2026-08-13T10:20:00Z");
        Instant earlierTimestamp = Instant.parse("2026-08-13T10:18:57Z");
        TimelineJobProgressService progressService = new TimelineJobProgressService();
        UUID activeJobId = progressService.createJob(userId);

        AsyncTimelineGenerationService service = new AsyncTimelineGenerationService();
        executorService = Executors.newSingleThreadExecutor();
        service.executorService = executorService;
        service.timelineGenerationService = timelineGenerationService;
        service.jobProgressService = progressService;

        AsyncTimelineGenerationService.TimelineSchedulingResult firstResult =
                service.scheduleTimelineRegenerationFromTimestamp(userId, laterTimestamp);
        AsyncTimelineGenerationService.TimelineSchedulingResult secondResult =
                service.scheduleTimelineRegenerationFromTimestamp(userId, earlierTimestamp);

        assertNull(firstResult.jobId());
        assertTrue(firstResult.scheduled());
        assertTrue(firstResult.queued());
        assertNull(secondResult.jobId());
        assertTrue(secondResult.scheduled());
        assertTrue(secondResult.queued());
        verifyNoInteractions(timelineGenerationService);

        progressService.completeJob(activeJobId);
        service.drainPendingRegenerations();

        verify(timelineGenerationService, timeout(1000))
                .generateTimelineFromTimestamp(eq(userId), eq(earlierTimestamp), any(UUID.class));
    }
}
