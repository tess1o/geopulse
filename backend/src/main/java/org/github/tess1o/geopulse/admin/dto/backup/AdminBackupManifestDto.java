package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBackupManifestDto {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private int schemaVersion;
    private String backupType;
    private String appVersion;
    private Instant backupStartedAt;
    private Instant exportedAt;
    private int userCount;
    private List<UUID> userIds;
    private List<String> warnings;
}
