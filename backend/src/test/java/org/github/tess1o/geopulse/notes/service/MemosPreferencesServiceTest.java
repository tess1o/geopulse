package org.github.tess1o.geopulse.notes.service;

import org.github.tess1o.geopulse.notes.model.MemosConfigResponse;
import org.github.tess1o.geopulse.notes.model.MemosPreferences;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class MemosPreferencesServiceTest {

    private final MemosPreferencesService service = new MemosPreferencesService();

    @Test
    void applyDefaultsEnablesSearchCacheWhenMissing() {
        MemosPreferences preferences = new MemosPreferences();
        preferences.setSearchCacheEnabled(null);

        MemosPreferences defaults = service.applyDefaults(preferences);

        assertTrue(defaults.getSearchCacheEnabled());
    }

    @Test
    void toConfigResponseIncludesSearchCacheFlag() {
        MemosPreferences preferences = new MemosPreferences();
        preferences.setSearchCacheEnabled(false);
        service.applyDefaults(preferences);

        MemosConfigResponse response = service.toConfigResponse(preferences);

        assertFalse(response.getSearchCacheEnabled());
    }
}
