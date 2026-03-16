package org.github.tess1o.geopulse.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * AI-friendly DTO for friend's latest live location.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIFriendLiveLocationDTO {
    private UUID friendId;
    private String email;
    private String fullName;
    private String locationName;
    private Instant timestamp;
    private Long secondsAgo;
    private Boolean liveNow;
    private Boolean stale;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double altitude;
    private Double speedKmh;
}
