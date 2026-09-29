package org.github.tess1o.geopulse.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Effective timeline display settings: the user's preferences with defaults applied, plus what the
 * server makes available.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineDisplaySettings {
    private TimelineDisplayPreferences preferences;
    private TimelineDisplayCapabilities capabilities;
}
