package org.github.tess1o.geopulse.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for user responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private UUID userId;
    private String fullName;
    private String role;
    private String email;
    private String timezone;
    // Don't include passwordHash in responses
}