package org.github.tess1o.geopulse.gpssource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GpsSourceConfigDTO {
    private UUID id;
    private String type;
    private String username;
    private String token;
    private UUID userId;
    private boolean active;
    private GpsSourceConfigEntity.ConnectionType connectionType;
}
