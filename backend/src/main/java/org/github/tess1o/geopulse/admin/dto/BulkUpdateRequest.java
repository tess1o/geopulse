package org.github.tess1o.geopulse.admin.dto;

import lombok.Data;
import java.util.List;

@Data
public class BulkUpdateRequest {
    private List<UpdateSettingRequest> settings;
}
