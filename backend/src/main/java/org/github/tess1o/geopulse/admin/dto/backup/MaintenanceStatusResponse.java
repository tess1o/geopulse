package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceStatusResponse {
    private String status;
    private String message;
    private MaintenanceStatusDto data;

    public static MaintenanceStatusResponse success(MaintenanceStatusDto data) {
        return new MaintenanceStatusResponse("success", null, data);
    }
}
