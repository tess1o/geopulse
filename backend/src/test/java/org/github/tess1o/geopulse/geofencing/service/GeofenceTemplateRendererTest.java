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
        String template = "{{subjectName}} {{eventCode}} {{geofenceName}} at {{timestamp}}";
        String result = renderer.render(template, Map.of(
                "subjectName", "Alice",
                "eventCode", "ENTER",
                "geofenceName", "Home",
                "timestamp", "03/18/2026 12:00:00"
        ));

        assertThat(result).isEqualTo("Alice ENTER Home at 03/18/2026 12:00:00");
    }

    @Test
    void shouldSupportWhitespaceInsidePlaceholders() {
        String template = "{{ subjectName }} {{ eventVerb }} {{ geofenceName }}";
        String result = renderer.render(template, Map.of(
                "subjectName", "Peter",
                "eventVerb", "entered",
                "geofenceName", "Home"
        ));

        assertThat(result).isEqualTo("Peter entered Home");
    }

    @Test
    void shouldReturnEmptyStringForBlankTemplate() {
        assertThat(renderer.render(" ", Map.of("a", "b"))).isEmpty();
    }
}
