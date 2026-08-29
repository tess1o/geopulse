package org.github.tess1o.geopulse.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminSettingsImportResult {
    private int settingsImported;
    private int oidcProvidersImported;
    private int oidcProvidersRemoved;
    private int oidcEnvironmentOverridesCreated;
    private int customGeocodingProvidersImported;
    private int customGeocodingProvidersRemoved;
    private List<String> unsupportedSettings;
}
