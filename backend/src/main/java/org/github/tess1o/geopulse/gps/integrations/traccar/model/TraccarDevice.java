package org.github.tess1o.geopulse.gps.integrations.traccar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

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
    @Schema(implementation = String.class, description = "Date-time as ISO-8601 text or epoch seconds or milliseconds.",
            examples = "2025-06-07T09:15:30.000+00:00")
    private JsonNode lastUpdate;
    private Long positionId;
    private String phone;
    private String model;
    private String contact;
    private String category;
    private Boolean disabled;
    @Schema(implementation = String.class, description = "Date-time as ISO-8601 text or epoch seconds or milliseconds.",
            examples = "2025-06-07T09:15:30.000+00:00")
    private JsonNode expirationTime;
}
