package org.github.tess1o.geopulse.admin.deletion;

import lombok.Builder;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a user deletion stands.
 */
@Data
@Builder
@Schema(description = "Progress of a user deletion. The user stays locked out until the status is COMPLETED.")
public class UserDeletionResponse {
    private UUID userId;
    private String userEmail;
    @Schema(description = "QUEUED, RUNNING, FAILED (an admin can retry) or COMPLETED.")
    private UserDeletionStatus status;
    @Schema(description = "What the deletion is doing: WAITING (for timeline processing of the user to finish), "
            + "TIMELINE, LOCATION_DATA, GPS_POINTS or FINALIZING.")
    private UserDeletionPhase.Stage phase;
    @Schema(description = "GPS points deleted so far.")
    private long deletedGpsPoints;
    @Schema(description = "Failed attempts since the last progress.")
    private int attempts;
    private String lastError;
    private Instant requestedAt;
    private Instant completedAt;

    static UserDeletionResponse of(UserDeletionEntity entity) {
        return UserDeletionResponse.builder()
                .userId(entity.getUserId())
                .userEmail(entity.getUserEmail())
                .status(entity.getStatus())
                .phase(entity.getPhase() == null ? UserDeletionPhase.Stage.WAITING : entity.getPhase().stage())
                .deletedGpsPoints(entity.getDeletedGpsPoints())
                .attempts(entity.getAttempts())
                .lastError(entity.getLastError())
                .requestedAt(entity.getRequestedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
