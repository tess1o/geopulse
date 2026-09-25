package org.github.tess1o.geopulse.poi.service;

import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class PoiConfigurationServiceTest {

    @Mock
    SystemSettingsService settings;

    @Test
    void languageFallsBackToEnglishWhenUnset() {
        when(settings.getString(PoiConfigurationService.KEY_LANGUAGE)).thenReturn("");
        assertThat(new PoiConfigurationService(settings).getLanguage()).isEqualTo("en");
    }

    @Test
    void languageAcceptsOrdinaryTags() {
        when(settings.getString(PoiConfigurationService.KEY_LANGUAGE)).thenReturn("de");
        assertThat(new PoiConfigurationService(settings).getLanguage()).isEqualTo("de");
    }

    /**
     * The language is interpolated into the SPARQL query text, so anything that is not a
     * plain language tag must be rejected rather than passed through.
     */
    @Test
    void languageRejectsAnythingThatCouldAlterTheQuery() {
        PoiConfigurationService config = null;

        for (String hostile : new String[]{
                "en\" } UNION { ?x ?y ?z } #",
                "en\". } ",
                "en\"; DROP",
                "<script>",
                "english language"}) {
            when(settings.getString(PoiConfigurationService.KEY_LANGUAGE)).thenReturn(hostile);
            config = new PoiConfigurationService(settings);
            assertThat(config.getLanguage())
                    .as("hostile language %s must not reach the query", hostile)
                    .isEqualTo("en");
        }
    }
}
