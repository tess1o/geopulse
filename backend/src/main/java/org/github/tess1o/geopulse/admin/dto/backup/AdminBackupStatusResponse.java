package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminBackupStatusResponse {
    private String status;
    private String message;
    private AdminBackupStatusDto data;

    public static AdminBackupStatusResponse success(AdminBackupStatusDto data) {
        return new AdminBackupStatusResponse("success", null, data);
    }
}
