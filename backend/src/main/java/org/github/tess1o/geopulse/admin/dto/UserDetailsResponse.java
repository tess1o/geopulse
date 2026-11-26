package org.github.tess1o.geopulse.admin.dto;

import org.github.tess1o.geopulse.admin.deletion.UserDeletionResponse;
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
    /** Not counted while the user is being deleted (see {@link #deletion}). */
    private Long gpsPointsCount;
    private Instant lastGpsPointAt;
    private List<String> linkedOidcProviders;
    /** The pending deletion of this user, or null. */
    private UserDeletionResponse deletion;
    private boolean hasPassword;
}
