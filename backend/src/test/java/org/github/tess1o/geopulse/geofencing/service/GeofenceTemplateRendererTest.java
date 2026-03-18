package org.github.tess1o.geopulse.geofencing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GeofenceTemplateRendererTest {

    private final GeofenceTemplateRenderer renderer = new GeofenceTemplateRenderer();

    @Test
    void shouldReplaceKnownPlaceholders() {
        String template = "{{subjectName}} {{eventType}} {{geofenceName}} at {{timestamp}}";
        String result = renderer.render(template, Map.of(
                "subjectName", "Alice",
                "eventType", "ENTER",
                "geofenceName", "Home",
                "timestamp", "2026-03-18T12:00:00Z"
        ));

        assertThat(result).isEqualTo("Alice ENTER Home at 2026-03-18T12:00:00Z");
    }

    @Test
    void shouldReturnEmptyStringForBlankTemplate() {
        assertThat(renderer.render(" ", Map.of("a", "b"))).isEmpty();
    }
}
