package org.github.tess1o.geopulse.home.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.home.model.HomeContentResponse;
import org.github.tess1o.geopulse.user.model.SupportedLanguages;
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
        HomeContentResponse response = service.getContent(SupportedLanguages.DEFAULT.getCode());

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
        HomeContentResponse response = service.getContent(SupportedLanguages.DEFAULT.getCode());

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
        HomeContentResponse response = service.getContent(SupportedLanguages.DEFAULT.getCode());

        assertEquals(1, response.tips().size());
        assertEquals("valid-tip", response.tips().getFirst().id());
        assertEquals(1, response.whatsNew().size());
        assertEquals("8.8.8", response.whatsNew().getFirst().version());
    }

    @Test
    void getContent_WithTranslatedLocale_ReturnsLocalizedTips() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-30T10:00:00Z"));

        HomeContentService service = new HomeContentService(
                new ObjectMapper(),
                clock,
                "/home-content-locale-test.json",
                "/whats_new.json"
        );

        service.init();

        HomeContentResponse english = service.getContent("en");
        assertEquals("English title", english.tips().getFirst().title());

        HomeContentResponse ukrainian = service.getContent("uk");
        assertEquals("Український заголовок", ukrainian.tips().getFirst().title());
    }

    @Test
    void getContent_WithUntranslatedOrUnsupportedLocale_FallsBackToEnglish() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-30T10:00:00Z"));

        HomeContentService service = new HomeContentService(
                new ObjectMapper(),
                clock,
                // No "/home-content-invalid_uk.json" sibling exists -- the uk translation is missing.
                "/home-content-invalid.json",
                "/whats-new-invalid.json"
        );

        service.init();

        HomeContentResponse missingTranslation = service.getContent("uk");
        assertEquals(1, missingTranslation.tips().size());
        assertEquals("valid-tip", missingTranslation.tips().getFirst().id());

        HomeContentResponse unsupportedLocale = service.getContent("fr");
        assertEquals(1, unsupportedLocale.tips().size());
        assertEquals("valid-tip", unsupportedLocale.tips().getFirst().id());

        HomeContentResponse nullLocale = service.getContent(null);
        assertEquals(1, nullLocale.tips().size());
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
