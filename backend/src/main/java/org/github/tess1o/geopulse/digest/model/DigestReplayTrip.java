package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigestReplayTrip {
    private Long id;
    private Instant startTime;
    private Instant endTime;
    private long tripDuration;
    private long distanceMeters;
    private String movementType;
    private double startLatitude;
    private double startLongitude;
    private double endLatitude;
    private double endLongitude;
    private List<DigestReplayPathPoint> path;
    /** Name of the user's trip this chapter replays; null for a single timeline trip. */
    private String name;
    /** Timeline trips joined into this chapter: 1 for a single trip, more for a named trip. */
    private int legCount;
    /** Where the user stayed just before a single trip; null when unknown or for a named trip. */
    private String originName;
    /** Where the user stayed just after a single trip; null when unknown or for a named trip. */
    private String destinationName;
}
