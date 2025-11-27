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
}
