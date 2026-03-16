package org.github.tess1o.geopulse.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Friend candidate metadata used by AI tools to resolve timeline targets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AITimelineFriendCandidateDTO {
    private UUID userId;
    private String email;
    private String fullName;
    private boolean timelineAccessGranted;
}
