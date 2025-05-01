package org.github.tess1o.geopulse.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String userId;
    private String deviceId;
    
    // Don't include passwordHash in responses
}