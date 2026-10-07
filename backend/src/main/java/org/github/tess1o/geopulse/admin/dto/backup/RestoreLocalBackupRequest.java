package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class RestoreLocalBackupRequest {
    @lombok.ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @Schema(examples = "a-long-backup-passphrase")
    private String password;
    @Schema(examples = "geopulse-backup-20250601-030000.gpb")
    private String fileName;
}
