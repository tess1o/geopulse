package org.github.tess1o.geopulse.gps.integrations.traccar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraccarDevice {
    private Long id;
    private Map<String, Object> attributes;
    private Long groupId;
    private Long calendarId;
    private String name;
    private String uniqueId;
    private String status;
    private JsonNode lastUpdate;
    private Long positionId;
    private String phone;
    private String model;
    private String contact;
    private String category;
    private Boolean disabled;
    private JsonNode expirationTime;
}
