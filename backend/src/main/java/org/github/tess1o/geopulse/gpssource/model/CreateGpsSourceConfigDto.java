package org.github.tess1o.geopulse.gpssource.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateGpsSourceConfigDto {
    private GpsSourceType type;
    private String username;
    private String password;
    private String token;
    private UUID userId;
    private GpsSourceConfigEntity.ConnectionType connectionType;
}

