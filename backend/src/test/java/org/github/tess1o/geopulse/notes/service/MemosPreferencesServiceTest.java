package org.github.tess1o.geopulse.notes.service;

import org.github.tess1o.geopulse.notes.model.MemosConfigResponse;
import org.github.tess1o.geopulse.notes.model.MemosPreferences;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void applyDefaultsUsesEmptyTagListsWhenMissing() {
        MemosPreferences preferences = new MemosPreferences();
        preferences.setIncludeTags(null);
        preferences.setExcludeTags(null);

        MemosPreferences defaults = service.applyDefaults(preferences);

        assertEquals(List.of(), defaults.getIncludeTags());
        assertEquals(List.of(), defaults.getExcludeTags());
    }

    @Test
    void normalizeTagListTrimsHashesBlanksAndDuplicates() {
        List<String> tags = service.normalizeTagList(List.of(" #travel ", "travel", "##work", " ", "#", "Work"));

        assertEquals(List.of("travel", "work", "Work"), tags);
    }

    @Test
    void toConfigResponseIncludesMemosFilters() {
        MemosPreferences preferences = new MemosPreferences();
        preferences.setSearchCacheEnabled(false);
        preferences.setIncludeTags(List.of("#travel"));
        preferences.setExcludeTags(List.of("#private"));
        service.applyDefaults(preferences);

        MemosConfigResponse response = service.toConfigResponse(preferences);

        assertFalse(response.getSearchCacheEnabled());
        assertEquals(List.of("travel"), response.getIncludeTags());
        assertEquals(List.of("private"), response.getExcludeTags());
    }
}
