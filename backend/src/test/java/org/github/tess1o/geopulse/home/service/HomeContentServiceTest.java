package org.github.tess1o.geopulse.home.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.home.model.HomeContentResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class HomeContentServiceTest {

    @Test
    void getContent_UsesBundledFiles() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-30T10:00:00Z"));

        HomeContentService service = new HomeContentService(
                new ObjectMapper(),
                clock,
                "/home-content.json",
                "/whats_new.json"
        );

        service.init();
        HomeContentResponse response = service.getContent();

        assertEquals("bundled", response.meta().source());
        assertEquals("2026-03-30T10:00:00Z", response.meta().updatedAt());
        assertFalse(response.tips().isEmpty());
        assertFalse(response.whatsNew().isEmpty());
    }

    @Test
    void getContent_WhenWhatsNewFileIsMissing_ReturnsEmptyReleaseNotes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-30T10:00:00Z"));

        HomeContentService service = new HomeContentService(
                new ObjectMapper(),
                clock,
                "/home-content.json",
                "/missing-whats-new.json"
        );

        service.init();
        HomeContentResponse response = service.getContent();

        assertFalse(response.tips().isEmpty());
        assertTrue(response.whatsNew().isEmpty());
    }

    @Test
    void getContent_IgnoresInvalidEntriesAndKeepsValidOnes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-30T10:00:00Z"));

        HomeContentService service = new HomeContentService(
                new ObjectMapper(),
                clock,
                "/home-content-invalid.json",
                "/whats-new-invalid.json"
        );

        service.init();
        HomeContentResponse response = service.getContent();

        assertEquals(1, response.tips().size());
        assertEquals("valid-tip", response.tips().getFirst().id());
        assertEquals(1, response.whatsNew().size());
        assertEquals("8.8.8", response.whatsNew().getFirst().version());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
