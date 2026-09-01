package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.Data;

@Data
public class RestoreLocalBackupRequest {
    @lombok.ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String fileName;
}
