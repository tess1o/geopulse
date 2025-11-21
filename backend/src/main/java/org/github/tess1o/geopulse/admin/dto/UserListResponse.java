package org.github.tess1o.geopulse.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserListResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String role;
    private boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    private long gpsPointsCount;
    private List<String> linkedOidcProviders;
}
