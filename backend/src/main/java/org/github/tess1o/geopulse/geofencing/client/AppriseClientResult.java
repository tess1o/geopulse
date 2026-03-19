package org.github.tess1o.geopulse.geofencing.client;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AppriseClientResult {
    private boolean success;
    private int statusCode;
    private String message;
}
