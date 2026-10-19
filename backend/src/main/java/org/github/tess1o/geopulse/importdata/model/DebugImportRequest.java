package org.github.tess1o.geopulse.importdata.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DebugImportRequest {
    private boolean clearExistingData = true;
    private boolean updateTimelineConfig = true;
    /** Use the exporting user's timezone, so days in the timeline start and end where theirs do. */
    private boolean updateTimezone = true;

    public DebugImportRequest(boolean clearExistingData, boolean updateTimelineConfig) {
        this(clearExistingData, updateTimelineConfig, true);
    }
}
