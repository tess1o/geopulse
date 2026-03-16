package org.github.tess1o.geopulse.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Candidate friend entry for AI live location tools.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIFriendLiveCandidateDTO {
    private UUID friendId;
    private String email;
    private String fullName;
    private boolean liveLocationAccessGranted;
    private String lastSeen;
    private String lastLocation;
}
