package org.github.tess1o.geopulse.mapmatching.service;

import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class MapMatchingConfigurationTest {

    @Test
    void availableWhenGloballyEnabledValhallaProviderIsConfigured() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.getBoolean("map-matching.enabled")).thenReturn(true);
        when(settingsService.getString("map-matching.provider")).thenReturn("valhalla");
        when(settingsService.getString("map-matching.valhalla.base-url")).thenReturn("http://valhalla:8002");

        MapMatchingConfiguration configuration = new MapMatchingConfiguration(settingsService);

        assertTrue(configuration.isAvailable());
    }

    @Test
    void unavailableWhenGloballyDisabledOrValhallaIsMissing() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.getBoolean("map-matching.enabled")).thenReturn(false);
        when(settingsService.getString("map-matching.provider")).thenReturn("valhalla");
        when(settingsService.getString("map-matching.valhalla.base-url")).thenReturn("http://valhalla:8002");

        MapMatchingConfiguration configuration = new MapMatchingConfiguration(settingsService);

        assertFalse(configuration.isAvailable());

        when(settingsService.getBoolean("map-matching.enabled")).thenReturn(true);
        when(settingsService.getString("map-matching.valhalla.base-url")).thenReturn("");

        assertFalse(configuration.isAvailable());
    }
}
