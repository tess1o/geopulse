package org.github.tess1o.geopulse.admin.dto.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceStatusDto {
    private String state;
    private boolean blocked;
    private boolean warning;
    private boolean restarting;
    private MessageDescriptor message;
    private String backupCreatedAt;
    private String completedAt;
}
