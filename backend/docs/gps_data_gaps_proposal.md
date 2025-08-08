Here is a proposed solution that is reliable, requires minimal effort, and fits perfectly within your existing architecture.

Proposed Solution: Introduce 'Data Gaps' as a First-Class Timeline Entity
The most reliable and minimally invasive way to solve this is to explicitly model the periods of missing data. Instead of letting the generation logic make an incorrect assumption (long stay), we will teach it to recognize and label these periods as DataGaps or UnknownActivity.

This makes the timeline more honest and transparent to the user.

1. Core Logic Modification (The Heart of the Change)
   The change is confined entirely within the TimelineGenerationService. This service, when processing raw GPS points, needs to be made aware of time gaps.

Current (Inferred) Logic:

Get sorted GPS points for a period.

Cluster points that are close in space and time to form Stays.

The movement between Stays becomes a Trip.

Proposed New Logic:

Define a configuration constant: MAX_ALLOWED_TIME_BETWEEN_POINTS_SECONDS. A reasonable default could be 2-3 hours (e.g., 10800 seconds). This threshold defines the maximum time between two GPS points before we consider the user's activity "unknown".

Inside TimelineGenerationService, when iterating through the time-sorted GPS points:

Let the current point be P_n at time T_n and the next point be P_{n+1} at time T_{n+1}.

Calculate the time difference: Δt = T_{n+1} - T_n.

If Δt > MAX_ALLOWED_TIME_BETWEEN_POINTS_SECONDS:

Finalize the current activity (e.g., the Stay ending at P_n).

Create a new DataGap object from T_n to T_{n+1}.

Start a new activity analysis from point P_{n+1}.

Else (the gap is acceptable):

Continue with the existing logic to cluster P_{n+1} into the current stay or trip.

This single change in the generation algorithm prevents the creation of artificially long stays.

2. Data Model & Persistence (Minimal Effort)
   To support this, we need a way to represent and store these gaps.

A. New DTO/Entity:
Create a simple DataGap entity.

Java

// In your DTO package
public class DataGapDTO {
private Instant startTime;
private Instant endTime;
private int durationSeconds;
}
B. New Database Table:
This is the only necessary schema change. A new table to store the generated gaps, which aligns perfectly with your existing timeline_stays and timeline_trips tables.

SQL

CREATE TABLE timeline_data_gaps (
id UUID PRIMARY KEY,
user_id UUID NOT NULL REFERENCES users(id),
start_time TIMESTAMP WITH TIME ZONE NOT NULL,
end_time TIMESTAMP WITH TIME ZONE NOT NULL,
duration_seconds INTEGER NOT NULL,
created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
Indexing user_id and start_time is recommended.

C. Update the Main Timeline DTO:
Your MovementTimelineDTO (the object returned by the API) should be updated to include these new entities.

Java

public class MovementTimelineDTO {
// ... existing fields like source (LIVE, CACHED)
private List<StayDTO> stays;
private List<TripDTO> trips;
private List<DataGapDTO> dataGaps; // New field
// ... constructors, getters, setters
}
3. Impact on the Architecture
   This is where the "minimal effort" aspect shines. Your well-designed architecture contains the change beautifully.

TimelineQueryService: No changes needed. Its logic of Today, Past, Mixed remains the same. It orchestrates the generation or retrieval of a MovementTimelineDTO. It doesn't care what's inside that DTO.

TimelineCacheService: No changes needed. It simply saves and retrieves the MovementTimelineDTO. Since the new dataGaps list is part of this DTO, it will be cached and restored automatically. The cache invalidation logic for favorite changes also remains perfectly valid.

TimelineBackgroundService: No changes needed. Its job is to trigger regeneration. The regeneration process will now correctly produce Stays, Trips, AND DataGaps, which will be saved to the cache.

API/Frontend: This is the only other area of impact. The API response will now contain the dataGaps array. The frontend client will need to be updated to visualize this—perhaps as a grayed-out section, a dotted line on the map, or an explicit "Unknown Activity" card in the timeline view. This is an essential part of making the solution reliable and useful to the end-user.

Example Scenario: Before vs. After
Situation:

Last GPS point at Home: Monday 8:00 AM.

Phone off all day.

Next GPS point at Home: Monday 10:00 PM.

Result with Current Architecture:

One Stay from Monday 8:00 AM to Monday 10:00 PM (14 hours long). (Incorrect)

Result with Proposed Architecture:

TimelineGenerationService detects the 14-hour gap between points.

It creates:

A Stay at Home ending Monday 8:00 AM.

A DataGap from Monday 8:00 AM to Monday 10:00 PM.

A new Stay at Home starting Monday 10:00 PM.

The final MovementTimelineDTO contains all three distinct items. (Correct and transparent)

Summary of Benefits
Reliable: It directly solves the root cause by correctly interpreting the data (or lack thereof) instead of making assumptions.

Minimal Effort: The change is highly localized to the TimelineGenerationService and the data model. The rest of your robust architecture (caching, background processing, event handling) requires no modification.

Transparent UX: It provides a more truthful representation of the user's day, building trust. Users will see "Unknown Activity for 14 hours" instead of a questionable 14-hour stay at one location.

Maintainable: It avoids complex hacks. A DataGap is a clean, understandable concept that fits naturally alongside Stays and Trips.