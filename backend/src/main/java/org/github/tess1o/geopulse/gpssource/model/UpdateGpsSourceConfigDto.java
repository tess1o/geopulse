package org.github.tess1o.geopulse.gpssource.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateGpsSourceConfigDto {
    private String id;
    private String type;
    private String username;
    private String password;
    private String token;
    private String userId;
    private GpsSourceConfigEntity.ConnectionType connectionType;
    private boolean filterInaccurateData;
    private Integer maxAllowedAccuracy;
    private Integer maxAllowedSpeed;
}
