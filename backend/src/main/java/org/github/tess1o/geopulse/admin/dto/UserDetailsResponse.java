package org.github.tess1o.geopulse.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserDetailsResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String role;
    private boolean isActive;
    private boolean emailVerified;
    private String avatar;
    private String timezone;
    private Instant createdAt;
    private Instant updatedAt;
    private long gpsPointsCount;
    private Instant lastGpsPointAt;
    private List<String> linkedOidcProviders;
    private boolean hasPassword;
}
