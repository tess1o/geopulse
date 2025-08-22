# Timeline System Redesign

## Executive Summary

The current timeline system has grown into unmaintainable complexity:
- **TimelineQueryService**: 586 lines
- **WholeTimelineProcessor**: 492 lines  
- **Timeline Tests**: 6,855 lines across 28 test files
- **Data Gap Logic**: Added as patches, causing duplicates and inconsistencies

This document proposes a clean redesign with clear business logic, proper separation of concerns, and maintainable architecture.

## Current Problems

### 1. Architectural Issues
- **Monolithic Services**: Single services handling multiple responsibilities
- **Mixed Concerns**: Business logic, persistence, caching mixed together
- **No Clear Abstractions**: Procedural code instead of object-oriented design
- **Complex Control Flow**: Hard to debug and understand

### 2. Business Logic Issues
- **Unclear Request Handling**: Past/Mixed/Future logic buried in large methods
- **Event Continuity**: Complex logic for handling ongoing events across boundaries
- **Data Gap Handling**: Added as afterthought, causing duplicates
- **Persistence Strategy**: Unclear what gets saved when

### 3. Testing Issues
- **Integration-Heavy Tests**: 6,855 lines of mostly integration tests
- **Hard to Maintain**: Tests break when unrelated code changes
- **Poor Coverage**: Complex scenarios not well tested
- **Slow Execution**: Full database setup for simple logic tests

## Business Logic Design

### Core Concepts

#### Timeline Events
```
Timeline = Stays + Trips + Data Gaps
- Stay: Stationary period at a location (timestamp, duration, location)
- Trip: Movement between stays (timestamp, duration, path, distance)
- Data Gap: Period without GPS data >X hours (timestamp, duration)
```

#### Time Classifications
```
- Past: Dates before today (persistent in DB)
- Today: Current date (always live-generated)
- Future: Dates after today (empty response)
```

#### Event Continuity
```
Events can span time boundaries:
- Stay starting Aug 14 23:00 with 3h duration continues into Aug 15
- Data gap starting Aug 14 18:00 with 48h duration covers Aug 15-16
- Trip starting Aug 14 23:50 with 20min duration continues into Aug 15
```

### Request Processing Flow

#### 1. Request Analysis
```java
public enum RequestType {
    PAST_ONLY,    // Both start and end before today
    MIXED,        // Start before today, end is today or after
    FUTURE_ONLY   // Both start and end after today
}
```

#### 2. Boundary Expansion
```
For any request, find events that:
- Start before the requested period BUT
- Continue into the requested period

Example: Request Aug 15-16
- Find latest event before Aug 15 00:00
- If it extends past Aug 15 00:00, include it
- Adjust its duration to cover only the requested period
```

#### 3. Data Retrieval Strategy
```
PAST_ONLY:
  1. Query existing events from DB (with boundary expansion)
  2. If no gaps in coverage → return DB data
  3. If gaps in coverage → delete whole period → regenerate from scratch → save to DB → return

MIXED:
  1. Get past period from DB (with boundary expansion)
  2. Generate today's timeline live
  3. Combine both timelines

FUTURE_ONLY:
  1. Return empty timeline immediately
```

#### 4. Gap Detection Logic
```
For any generated timeline:
1. Sort all GPS points by timestamp
2. Calculate time differences between consecutive points
3. If gap > threshold (e.g., 3 hours) → create DataGap event
4. DataGap has start_time, end_time, duration
```

#### 5. Timeline Assembly
```
Combine events from all sources:
1. Merge stays, trips, data gaps into single chronological list
2. Resolve overlapping events (prefer more recent data)
3. Ensure continuity (no unexplained time gaps)
4. Set appropriate data source markers (LIVE/CACHED/MIXED) - optional, can be skipped if not needed
```

#### 6. Persistence Rules
```
Save to DB:
- Past timeline events (stays, trips, data gaps)
- Only after successful generation
- Replace any existing data for same date range

Never save:
- Today's timeline events (always live)
- Future timeline events (always empty)
- Failed/incomplete generation attempts
```

### Critical Missing Logic Patterns

The current implementation contains several complex logic patterns that must be preserved in the redesign:

#### 1. Previous Context Prepending  
```
When returning a timeline, prepend previous context to show continuity:
1. Find latest stay, trip, or data gap before the requested start time
2. Determine which is most recent (closest to request start)
3. Find earliest activity timestamp in current timeline
4. Adjust previous event's duration to extend until first current activity
5. Add adjusted previous event to beginning of timeline

Example: Request Aug 15 14:00-18:00, but user was at home from Aug 14 20:00
Result: Timeline shows home stay from Aug 14 20:00 until Aug 15 14:00 (adjusted duration)

Note: This is different from boundary expansion - prepending adjusts duration to show continuity,
while boundary expansion includes complete original events that span into the requested period.
```

#### 2. Cross-day Gap Detection  
```
When combining past and today timelines, detect gaps at the boundary:
1. Find last activity timestamp in past timeline (stay/trip/gap end)
2. Find first activity timestamp in today timeline (stay/trip/gap start)
3. Calculate gap duration between these timestamps
4. If gap > threshold → create cross-day data gap
5. Add cross-day gap to combined timeline

Example: Past timeline ends at Aug 14 22:00, today starts at Aug 15 08:00
Result: 10-hour cross-day gap created from 22:00 to 08:00
```

#### 3. Event Boundary Expansion Logic
```
For any timeline request, include ongoing events that span into the requested period:
1. Query events (stays/trips/data gaps) that start before request period
2. Check if any ongoing event extends into request period  
3. If yes, include the complete event in the timeline (full duration, original timestamps)
4. Show the complete event even if it started before the requested period

Example: Request Aug 12-15, but user was at home from Aug 11 23:00 until Aug 12 06:00
Result: Timeline includes the complete home stay (Aug 11 23:00 → Aug 12 06:00, 7 hours)
The UI handles displaying/adjusting the view as needed.

No server-side duration adjustment - return complete events with original timestamps.
The boundary expansion ensures users see complete context for proper timeline continuity.

Special case - Overnight Stay Extension (4-step algorithm for daily processing):
1. Find last saved event (stay/trip) before processing day
2. Generate timeline from that event's start time to end of day
3. Update existing event with new duration from generated timeline
4. Save remaining generated events (excluding the first one used for update)
```

#### 4. Multi-day vs Single-day Processing
```
Single-day Processing (WholeTimelineProcessor):
- Uses 4-step overnight algorithm
- Updates existing events that span midnight
- Handles boundary expansion automatically

Multi-day Processing:
- Process entire range as single unit for proper data gap detection
- Handle boundary expansion for first day only
- Create data gaps for periods where GPS data ends before request end
- Save timeline data covering the entire requested range

Key Difference: Single-day updates existing events, multi-day creates new coverage
```

#### 5. Data Gap Creation Strategies
```
Strategy 1: No GPS Data for Entire Period
- Create single data gap covering full requested range
- Persist gap entity to database
- Cache timeline with gap for future requests

Strategy 2: GPS Data Ends Before Request End
- Generate timeline for available GPS data period
- Create data gap from last GPS point to request end time
- Combine generated timeline + data gap

Strategy 3: Cross-day Gaps (Mixed requests)
- Detect gaps between past timeline end and today timeline start
- Only create if gap duration exceeds configured threshold
- Add to combined timeline without persisting (temporary)
```

## Architectural Design

### Service Decomposition

Replace monolithic services with focused, single-responsibility services:

#### 1. TimelineRequestRouter (~50 lines)
```java
@ApplicationScoped
public class TimelineRequestRouter {
    
    public RequestType analyzeRequest(Instant start, Instant end) {
        // Determine PAST_ONLY/MIXED/FUTURE_ONLY
    }
    
    public MovementTimelineDTO routeRequest(UUID userId, Instant start, Instant end) {
        RequestType type = analyzeRequest(start, end);
        return switch (type) {
            case PAST_ONLY -> pastRequestHandler.handle(userId, start, end);
            case MIXED -> mixedRequestHandler.handle(userId, start, end);
            case FUTURE_ONLY -> createEmptyTimeline(userId);
        };
    }
}
```

#### 2. TimelineEventRetriever (~100 lines)
```java
@ApplicationScoped  
public class TimelineEventRetriever {
    
    public TimelineEvents getExistingEvents(UUID userId, Instant start, Instant end) {
        // 1. Query stays, trips, data gaps from DB
        // 2. Apply boundary expansion logic
        // 3. Return combined events
    }
    
    public Optional<TimelineEvent> getLatestEventBefore(UUID userId, Instant timestamp) {
        // Find most recent stay/trip/data gap before timestamp
    }
    
    public boolean hasCompleteData(UUID userId, Instant start, Instant end) {
        // Check if DB has complete timeline coverage for period
    }
}
```

#### 3. TimelineGenerator (~150 lines)
```java
@ApplicationScoped
public class TimelineGenerator {
    
    public MovementTimelineDTO generateFromGps(UUID userId, Instant start, Instant end) {
        // 1. Get GPS data for period
        // 2. Detect stays and trips
        // 3. Detect data gaps
        // 4. Assemble timeline
        // 5. Return timeline (NOT persisted)
    }
    
    private List<DataGapEvent> detectDataGaps(List<GpsPoint> gpsPoints) {
        // Identify periods >threshold without GPS data
    }
}
```

#### 4. TimelineAssembler (~150 lines)
```java
@ApplicationScoped
public class TimelineAssembler {
    
    public MovementTimelineDTO combineTimelines(MovementTimelineDTO... timelines) {
        // 1. Merge events from multiple timelines
        // 2. Sort chronologically  
        // 3. Resolve overlaps
        // 4. Set data source markers
    }
    
    public MovementTimelineDTO applyBoundaryExpansion(MovementTimelineDTO timeline, 
                                                      Optional<TimelineEvent> expandEvent,
                                                      Instant requestStart) {
        // Extend ongoing event into requested period
    }
    
    public MovementTimelineDTO detectCrossDayGaps(MovementTimelineDTO pastTimeline,
                                                  MovementTimelineDTO todayTimeline,
                                                  TimelineConfig config) {
        // 1. Find last activity timestamp in past timeline
        // 2. Find first activity timestamp in today timeline  
        // 3. Calculate gap duration between timestamps
        // 4. If gap > threshold → create cross-day data gap
        // 5. Return combined timeline with cross-day gaps
    }
    
    public MovementTimelineDTO prependPreviousContext(MovementTimelineDTO timeline,
                                                      UUID userId,
                                                      Instant requestStartTime) {
        // 1. Find latest stay or trip before request start time
        // 2. Find earliest activity in current timeline
        // 3. Adjust previous event duration to show continuity
        // 4. Prepend adjusted event to timeline
    }
}
```

#### 5. TimelinePersister (~50 lines)
```java
@ApplicationScoped
public class TimelinePersister {
    
    @Transactional
    public void saveTimeline(UUID userId, MovementTimelineDTO timeline, Instant start, Instant end) {
        // 1. Delete existing events in date range
        // 2. Save stays, trips, data gaps to DB
        // 3. Update cache markers
    }
    
    public boolean shouldPersist(Instant start, Instant end) {
        // Only persist past dates, never today or future
    }
}
```

#### 6. TimelineOvernightProcessor (~100 lines)
```java
@ApplicationScoped
public class TimelineOvernightProcessor {
    
    public MovementTimelineDTO processWholeDay(UUID userId, LocalDate processingDate) {
        // Implements the 4-step overnight algorithm:
        // 1. Find last saved event (stay/trip) before processing day
        // 2. Generate timeline from that event's start time to end of day
        // 3. Update existing event with new duration from generated timeline
        // 4. Save remaining generated events (excluding first used for update)
    }
    
    public MovementTimelineDTO processMultiDayRange(UUID userId, Instant start, Instant end) {
        // Handle multi-day processing with proper data gap detection
        // 1. Find boundary expansion event
        // 2. Generate for entire range as single unit
        // 3. Create data gaps for missing periods
        // 4. Save timeline covering full range
    }
    
    private LastSavedEvent findLastSavedEvent(UUID userId, Instant beforeTimestamp) {
        // Find most recent stay or trip before timestamp
    }
}
```

#### 7. Request Handlers (~75 lines each)
```java
@ApplicationScoped
public class PastRequestHandler {
    
    public MovementTimelineDTO handle(UUID userId, Instant start, Instant end) {
        // 1. Check for existing complete data in DB
        // 2. If complete → return DB data with boundary expansion
        // 3. If incomplete → use OvernightProcessor → save → return
    }
}

@ApplicationScoped  
public class MixedRequestHandler {
    
    public MovementTimelineDTO handle(UUID userId, Instant start, Instant end) {
        // 1. Get past period from DB with boundary expansion
        // 2. Generate today live
        // 3. Use TimelineAssembler to combine with cross-day gap detection
    }
}
```

### Service Dependencies
```
TimelineRequestRouter
├── PastRequestHandler
│   ├── TimelineEventRetriever
│   ├── TimelineOvernightProcessor
│   ├── TimelineAssembler (with boundary expansion & prepending)
│   └── TimelinePersister
├── MixedRequestHandler
│   ├── TimelineEventRetriever
│   ├── TimelineGenerator (for today's live data)
│   └── TimelineAssembler (with cross-day gap detection)
└── (Future requests handled inline - empty response)

TimelineOvernightProcessor
├── TimelineEventRetriever (for finding last saved events)
├── TimelineGenerator (for GPS-based timeline generation)
└── TimelinePersister (for saving updated events)

TimelineAssembler (Complex logic coordinator)
├── TimelineEventRetriever (for previous context lookup)
├── Cross-day gap detection logic
├── Previous context prepending logic
└── Boundary expansion logic
```

### Data Model

#### Timeline Events (Base Interface)
```java
public interface TimelineEvent {
    Instant getStartTime();
    Duration getDuration();
    Instant getEndTime(); // calculated: startTime + duration
    TimelineEventType getType();
}

public enum TimelineEventType {
    STAY, TRIP, DATA_GAP
}
```

#### Event Implementations
```java
public class StayEvent implements TimelineEvent {
    private Instant startTime;
    private Duration duration;
    private GeoPoint location;
    private String locationName;
    private LocationSource locationSource;
}

public class TripEvent implements TimelineEvent {
    private Instant startTime;
    private Duration duration;
    private GeoPoint startLocation;
    private GeoPoint endLocation;
    private Double distanceKm;
    private MovementType movementType;
    private LineString path;
}

public class DataGapEvent implements TimelineEvent {
    private Instant startTime;
    private Duration duration;
    // No additional fields - represents absence of data
}
```

#### Timeline Container
```java
public class MovementTimelineDTO {
    private UUID userId;
    private List<StayEvent> stays;
    private List<TripEvent> trips;
    private List<DataGapEvent> dataGaps;
    private TimelineDataSource dataSource; // LIVE, CACHED, MIXED
    private Instant lastUpdated;
    
    public List<TimelineEvent> getAllEvents() {
        // Return all events sorted chronologically
    }
}
```

## Testing Strategy

### Current Issues (Critical Problems Found)
- **6,855 lines across 26 test files** - massive over-testing
- **100% Integration Tests**: Every test uses `@QuarkusTest` + PostgreSQL database
- **No Unit Tests**: Simple business logic requires full application context
- **Slow Execution**: Database setup/teardown for every test method
- **Brittle Tests**: Break when unrelated code changes due to tight coupling
- **Poor Coverage**: Complex edge cases buried in integration test complexity
- **Hard to Debug**: Test failures require full application context to investigate

### Specific Test Problems Identified
- **TimelineQueryServiceTest**: 587-line service tested only with full database
- **OvernightTimelineProcessorTest**: 4-step algorithm tested only as integration  
- **TimelineDataGapTest**: Simple gap creation logic requires PostgreSQL
- **TimelinePrependingTest**: Business logic for duration adjustment needs full app
- **Cross-day Gap Detection**: Complex algorithm has no isolated unit tests

### Proposed Testing Approach

**Note**: Focus on real database testing with complete GPS data scenarios. Each test must generate actual GPS points, process them through the services, and verify the resulting timeline data (stays, trips, data gaps).

#### 1. Core Business Logic Tests (50% of tests)
```java
// Test with real GPS data and database - validate complete timeline generation

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class TimelineGenerationTest {

    @Test
    @Transactional
    void shouldGenerateStayAndTrip_WithRealGpsData() {
        // Create real GPS points: home stay, then trip to office, then office stay
        UserEntity user = createTestUser("timeline-test@geopulse.app");
        
        // Generate home stay: Aug 15 08:00-10:00 (stationary points)
        List<GpsPointEntity> homePoints = createStationaryPoints(
            user, HOME_LAT, HOME_LON, 
            "2024-08-15T08:00:00Z", "2024-08-15T10:00:00Z", 5 /* minutes interval */);
        
        // Generate trip: Aug 15 10:00-10:30 (moving points)
        List<GpsPointEntity> tripPoints = createMovingPoints(
            user, HOME_LAT, HOME_LON, OFFICE_LAT, OFFICE_LON,
            "2024-08-15T10:00:00Z", "2024-08-15T10:30:00Z");
            
        // Generate office stay: Aug 15 10:30-15:00 (stationary points)
        List<GpsPointEntity> officePoints = createStationaryPoints(
            user, OFFICE_LAT, OFFICE_LON,
            "2024-08-15T10:30:00Z", "2024-08-15T15:00:00Z", 10 /* minutes interval */);
        
        gpsPointRepository.persist(homePoints);
        gpsPointRepository.persist(tripPoints);
        gpsPointRepository.persist(officePoints);
        
        // Act - Process timeline using the new service
        Instant start = Instant.parse("2024-08-15T08:00:00Z");
        Instant end = Instant.parse("2024-08-15T15:00:00Z");
        MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(user.getId(), start, end);
        
        // Assert - Verify timeline structure
        assertThat(timeline.getStays()).hasSize(2);
        assertThat(timeline.getTrips()).hasSize(1);
        assertThat(timeline.getDataGaps()).hasSize(0);
        
        // Verify home stay
        TimelineStayLocationDTO homeStay = timeline.getStays().get(0);
        assertThat(homeStay.getTimestamp()).isEqualTo(Instant.parse("2024-08-15T08:00:00Z"));
        assertThat(homeStay.getStayDuration()).isEqualTo(120); // 2 hours in minutes
        assertStayLocation(homeStay, HOME_LAT, HOME_LON);
        
        // Verify trip
        TimelineTripDTO trip = timeline.getTrips().get(0);
        assertThat(trip.getTimestamp()).isEqualTo(Instant.parse("2024-08-15T10:00:00Z"));
        assertThat(trip.getTripDuration()).isEqualTo(30); // 30 minutes
        assertTripPath(trip, HOME_LAT, HOME_LON, OFFICE_LAT, OFFICE_LON);
        
        // Verify office stay
        TimelineStayLocationDTO officeStay = timeline.getStays().get(1);
        assertThat(officeStay.getTimestamp()).isEqualTo(Instant.parse("2024-08-15T10:30:00Z"));
        assertThat(officeStay.getStayDuration()).isEqualTo(270); // 4.5 hours in minutes
        assertStayLocation(officeStay, OFFICE_LAT, OFFICE_LON);
    }
    
    @Test
    @Transactional
    void shouldCreateDataGap_WhenNoGpsDataExists() {
        // Create user but no GPS points for requested period
        UserEntity user = createTestUser("gap-test@geopulse.app");
        
        // Request timeline for period with no GPS data
        Instant start = Instant.parse("2024-08-15T08:00:00Z");
        Instant end = Instant.parse("2024-08-15T18:00:00Z");
        MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(user.getId(), start, end);
        
        // Verify data gap was created and persisted
        assertThat(timeline.getStays()).hasSize(0);
        assertThat(timeline.getTrips()).hasSize(0);
        assertThat(timeline.getDataGaps()).hasSize(1);
        
        TimelineDataGapDTO dataGap = timeline.getDataGaps().get(0);
        assertThat(dataGap.getStartTime()).isEqualTo(start);
        assertThat(dataGap.getEndTime()).isEqualTo(end);
        
        // Verify gap was persisted to database
        List<TimelineDataGapEntity> persistedGaps = timelineDataGapRepository
            .findByUserIdAndTimeRange(user.getId(), start, end);
        assertThat(persistedGaps).hasSize(1);
        assertThat(persistedGaps.get(0).getStartTime()).isEqualTo(start);
        assertThat(persistedGaps.get(0).getEndTime()).isEqualTo(end);
    }
}
```

#### 2. Overnight Algorithm Tests (25% of tests)
```java
// Test 4-step overnight algorithm with real GPS data and database persistence

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class TimelineOvernightAlgorithmTest {
    
    @Test
    @Transactional
    void shouldExtendOvernightStay_With4StepAlgorithm() {
        // Scenario: User at home from Aug 14 23:00 (saved to DB), 
        // then continuous GPS points until Aug 15 06:00
        UserEntity user = createTestUser("overnight-test@geopulse.app");
        
        // Step 1: Create and save initial stay (Aug 14 23:00-23:59)
        Instant aug14_23_00 = Instant.parse("2024-08-14T23:00:00Z");
        Instant aug14_23_59 = Instant.parse("2024-08-14T23:59:59Z");
        TimelineStayEntity initialStay = createAndSaveStay(user, HOME_LAT, HOME_LON, 
            aug14_23_00, Duration.ofMinutes(59));
            
        // Step 2: Create GPS points for Aug 15 (continuing at same location)
        List<GpsPointEntity> aug15Points = createStationaryPoints(
            user, HOME_LAT, HOME_LON,
            "2024-08-15T00:00:00Z", "2024-08-15T06:00:00Z", 5 /* minutes */);
        gpsPointRepository.persist(aug15Points);
        
        // Step 3: Process Aug 15 using overnight algorithm 
        LocalDate aug15 = LocalDate.of(2024, 8, 15);
        MovementTimelineDTO timeline = timelineOvernightProcessor.processWholeDay(user.getId(), aug15);
        
        // Step 4: Verify algorithm results
        
        // Check database: original stay should have extended duration (23:00 → 06:00 = 7 hours)
        TimelineStayEntity updatedStay = timelineStayRepository.findById(initialStay.getId());
        assertThat(updatedStay.getStayDuration()).isEqualTo(7 * 3600); // 7 hours in seconds
        
        // Check timeline: should start with the extended stay
        assertThat(timeline.getStays()).hasSize(1);
        TimelineStayLocationDTO timelineStay = timeline.getStays().get(0);
        assertThat(timelineStay.getTimestamp()).isEqualTo(aug14_23_00);
        assertThat(timelineStay.getStayDuration()).isEqualTo(7 * 60); // 7 hours in minutes
        
        // Verify no additional stays were created for Aug 15 (algorithm skipped first generated event)
        List<TimelineStayEntity> aug15Stays = timelineStayRepository
            .findByUserAndDateRange(user.getId(), 
                LocalDate.of(2024, 8, 15).atStartOfDay(ZoneOffset.UTC).toInstant(),
                LocalDate.of(2024, 8, 15).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1));
        assertThat(aug15Stays).hasSize(0); // First generated stay was skipped per algorithm
    }
    
    @Test
    @Transactional
    void shouldHandlePreviousContextPrepending_WithRealData() {
        // Scenario: Request Aug 15 14:00-18:00, but user was at home until 14:00
        UserEntity user = createTestUser("prepend-test@geopulse.app");
        
        // Create previous stay: Aug 14 20:00-23:59 (saved to DB)
        TimelineStayEntity previousStay = createAndSaveStay(user, HOME_LAT, HOME_LON,
            Instant.parse("2024-08-14T20:00:00Z"), Duration.ofHours(4));
            
        // Create GPS points for Aug 15 14:00-18:00 (at office)
        List<GpsPointEntity> officePoints = createStationaryPoints(
            user, OFFICE_LAT, OFFICE_LON,
            "2024-08-15T14:00:00Z", "2024-08-15T18:00:00Z", 10);
        gpsPointRepository.persist(officePoints);
        
        // Request timeline for Aug 15 14:00-18:00
        Instant start = Instant.parse("2024-08-15T14:00:00Z");
        Instant end = Instant.parse("2024-08-15T18:00:00Z");
        MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(user.getId(), start, end);
        
        // Verify previous context was prepended with adjusted duration
        assertThat(timeline.getStays()).hasSize(2);
        
        // First stay should be the prepended previous stay with adjusted duration (20:00 → 14:00 = 18 hours)
        TimelineStayLocationDTO prependedStay = timeline.getStays().get(0);
        assertThat(prependedStay.getTimestamp()).isEqualTo(Instant.parse("2024-08-14T20:00:00Z"));
        assertThat(prependedStay.getStayDuration()).isEqualTo(18 * 60); // 18 hours in minutes
        assertStayLocation(prependedStay, HOME_LAT, HOME_LON);
        
        // Second stay should be the office stay from GPS points
        TimelineStayLocationDTO officeStay = timeline.getStays().get(1);
        assertThat(officeStay.getTimestamp()).isEqualTo(Instant.parse("2024-08-15T14:00:00Z"));
        assertThat(officeStay.getStayDuration()).isEqualTo(4 * 60); // 4 hours in minutes
        assertStayLocation(officeStay, OFFICE_LAT, OFFICE_LON);
    }
}
```

#### 3. Integration & System Tests (25% of tests)
```java
// Test cross-day gaps, mixed requests, and multi-day scenarios with real data

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class TimelineSystemTest {
    
    @Test
    @Transactional
    void shouldDetectCrossDayGap_InMixedRequest() {
        // Scenario: User at home Aug 14 until 22:00, then gap until Aug 15 08:00 (today)
        UserEntity user = createTestUser("crossday-test@geopulse.app");
        
        // Create past timeline: Aug 14 stay ending at 22:00
        List<GpsPointEntity> aug14Points = createStationaryPoints(
            user, HOME_LAT, HOME_LON,
            "2024-08-14T20:00:00Z", "2024-08-14T22:00:00Z", 5);
        gpsPointRepository.persist(aug14Points);
        
        // Process and cache Aug 14 timeline
        timelineOvernightProcessor.processWholeDay(user.getId(), LocalDate.of(2024, 8, 14));
        
        // Create today's timeline: Aug 15 starting at 08:00
        List<GpsPointEntity> aug15Points = createStationaryPoints(
            user, OFFICE_LAT, OFFICE_LON,
            "2024-08-15T08:00:00Z", "2024-08-15T18:00:00Z", 10);
        gpsPointRepository.persist(aug15Points);
        
        // Request mixed timeline: Aug 14 20:00 to Aug 15 18:00
        Instant start = Instant.parse("2024-08-14T20:00:00Z");
        Instant end = Instant.parse("2024-08-15T18:00:00Z");
        MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(user.getId(), start, end);
        
        // Verify cross-day gap was detected
        assertThat(timeline.getStays()).hasSize(2); // Aug 14 home + Aug 15 office
        assertThat(timeline.getTrips()).hasSize(0);
        assertThat(timeline.getDataGaps()).hasSize(1); // Cross-day gap
        
        // Verify cross-day gap: 22:00 → 08:00 (10 hours)
        TimelineDataGapDTO crossDayGap = timeline.getDataGaps().get(0);
        assertThat(crossDayGap.getStartTime()).isEqualTo(Instant.parse("2024-08-14T22:00:00Z"));
        assertThat(crossDayGap.getEndTime()).isEqualTo(Instant.parse("2024-08-15T08:00:00Z"));
        assertThat(crossDayGap.getDurationMinutes()).isEqualTo(10 * 60); // 10 hours
    }
    
    @Test
    @Transactional
    void shouldHandleMultiDayRequest_WithPartialGpsData() {
        // Scenario: Request Aug 10-16, but GPS data only exists for Aug 12-14
        UserEntity user = createTestUser("multiday-test@geopulse.app");
        
        // Create GPS data only for Aug 12-14
        createDayOfGpsData(user, LocalDate.of(2024, 8, 12), HOME_LAT, HOME_LON);
        createDayOfGpsData(user, LocalDate.of(2024, 8, 13), OFFICE_LAT, OFFICE_LON);
        createDayOfGpsData(user, LocalDate.of(2024, 8, 14), HOME_LAT, HOME_LON);
        
        // Request timeline for Aug 10-16 (7 days total)
        Instant start = LocalDate.of(2024, 8, 10).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = LocalDate.of(2024, 8, 16).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
        MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(user.getId(), start, end);
        
        // Verify timeline structure
        assertThat(timeline.getStays()).hasSize(3); // Aug 12, 13, 14 stays
        assertThat(timeline.getDataGaps()).hasSize(2); // Aug 10-11 gap + Aug 15-16 gap
        
        // Verify first data gap: Aug 10-12 (no GPS data)
        TimelineDataGapDTO firstGap = timeline.getDataGaps().get(0);
        assertThat(firstGap.getStartTime()).isEqualTo(start);
        assertThat(firstGap.getEndTime()).isEqualTo(LocalDate.of(2024, 8, 12).atStartOfDay(ZoneOffset.UTC).toInstant());
        
        // Verify last data gap: Aug 14 end to Aug 16 end
        TimelineDataGapDTO lastGap = timeline.getDataGaps().get(1);
        assertThat(lastGap.getEndTime()).isEqualTo(end);
        
        // Verify stays exist for days with GPS data
        assertStayExistsForDate(timeline, LocalDate.of(2024, 8, 12));
        assertStayExistsForDate(timeline, LocalDate.of(2024, 8, 13));
        assertStayExistsForDate(timeline, LocalDate.of(2024, 8, 14));
    }
    
    private void createDayOfGpsData(UserEntity user, LocalDate date, double lat, double lon) {
        List<GpsPointEntity> points = createStationaryPoints(
            user, lat, lon,
            date.atTime(8, 0).atZone(ZoneOffset.UTC).toInstant().toString(),
            date.atTime(18, 0).atZone(ZoneOffset.UTC).toInstant().toString(),
            60 /* 1 hour intervals */);
        gpsPointRepository.persist(points);
    }
}
```
```

### Test Organization
```
src/test/java/timeline/
├── unit/
│   ├── TimelineGeneratorTest.java
│   ├── TimelineAssemblerTest.java
│   ├── RequestRouterTest.java
│   └── DataGapDetectionTest.java
├── integration/
│   ├── TimelineEventRetrieverTest.java
│   └── TimelinePersisterTest.java
└── system/
    └── TimelineSystemTest.java
```

## Migration Strategy

### Phase 1: Create New Services (No Breaking Changes)
1. Implement new service interfaces
2. Add unit tests for new services
3. Keep old services running in parallel

### Phase 2: Gradual Migration  
1. Update TimelineResource to use new TimelineRequestRouter
2. Run both old and new implementations in parallel (feature flag)
3. Compare results to ensure correctness

### Phase 3: Remove Old Code
1. Delete old TimelineQueryService and WholeTimelineProcessor
2. Remove old tests
3. Update documentation

### Phase 4: Optimization
1. Add performance monitoring
2. Optimize database queries
3. Add caching where beneficial

## Expected Benefits

### Code Quality
- **Reduced Complexity**: 1,078 lines → ~425 lines across focused services
- **Better Testability**: Unit tests for business logic, integration tests for data access
- **Clearer Responsibilities**: Each service has single, well-defined purpose
- **Easier Debugging**: Simpler control flow, clear service boundaries

### Maintainability  
- **Focused Changes**: Modifications only affect relevant services
- **Clear Business Logic**: Timeline generation rules clearly documented and implemented
- **Better Error Handling**: Failures isolated to specific services
- **Easier Testing**: Fast unit tests for most scenarios

### Performance
- **Faster Tests**: Unit tests execute in milliseconds vs seconds
- **Efficient Queries**: Focused database access patterns
- **Better Caching**: Clear separation of cached vs live data
- **Reduced Complexity**: Simpler code paths

## Implementation Checklist

### New Services
- [ ] TimelineRequestRouter
- [ ] TimelineEventRetriever  
- [ ] TimelineGenerator
- [ ] TimelineAssembler (enhanced with complex logic)
  - [ ] Cross-day gap detection logic
  - [ ] Previous context prepending logic 
  - [ ] Boundary expansion logic
- [ ] TimelinePersister
- [ ] TimelineOvernightProcessor (4-step algorithm)
- [ ] PastRequestHandler
- [ ] MixedRequestHandler

### Data Model  
- [ ] TimelineEvent interface
- [ ] StayEvent, TripEvent, DataGapEvent implementations
- [ ] Enhanced MovementTimelineDTO

### Tests
- [ ] Unit tests for all new services
- [ ] Integration tests for data access
- [ ] System tests for end-to-end scenarios
- [ ] Performance tests for critical paths

### Migration
- [ ] Feature flag for new vs old implementation
- [ ] Parallel execution and result comparison
- [ ] Gradual rollout plan
- [ ] Rollback procedures

## Implementation Roadmap

Based on the comprehensive review of 1,080 lines of service code and 6,855 lines of tests, this roadmap ensures safe migration of all complex logic patterns:

### Phase 1: Foundation & Core Services (Weeks 1-2)
**Goal**: Build basic services without breaking existing functionality

1. **Create New Service Interfaces**
   ```java
   // Start with simple interfaces, no implementation yet
   public interface TimelineRequestRouter
   public interface TimelineEventRetriever
   public interface TimelineGenerator  
   public interface TimelineAssembler
   public interface TimelinePersister
   ```

2. **Implement TimelineEventRetriever** 
   - Extract database access logic from current services
   - Add boundary expansion query methods
   - **Critical**: Include `findLatestEventBefore()` for prepending logic
   - Test with focused integration tests (database access only)

3. **Create Enhanced TimelineAssembler Foundation**
   - Implement basic timeline combination logic  
   - **IMPORTANT**: Start with simple merge, add complex logic in Phase 2
   - Unit test the basic functionality

**Risk Mitigation**: Keep all existing services running. New services are additive only.

### Phase 2: Complex Logic Migration (Weeks 3-5)
**Goal**: Migrate the most complex logic patterns safely

4. **Implement Previous Context Prepending**
   ```java
   // In TimelineAssembler
   public MovementTimelineDTO prependPreviousContext(...)
   ```
   - Extract from TimelineQueryService lines 461-586
   - Create comprehensive unit tests with mocks
   - **Critical**: Test duration adjustment logic thoroughly

5. **Implement Cross-day Gap Detection**
   ```java  
   // In TimelineAssembler
   public MovementTimelineDTO detectCrossDayGaps(...)
   ```
   - Extract from TimelineQueryService lines 287-332
   - Unit test gap threshold logic
   - **Critical**: Test boundary timestamp calculations

6. **Implement TimelineOvernightProcessor**
   - Extract 4-step algorithm from WholeTimelineProcessor
   - **Most Complex**: Handle stay/trip duration updates correctly
   - Create thorough unit tests for each step
   - **Critical**: Test multi-day vs single-day processing differences

### Phase 3: Request Handler Migration (Weeks 6-7)  
**Goal**: Implement the main request routing logic

7. **Create TimelineRequestRouter**
   - Simple request classification logic (Past/Mixed/Future)
   - Route to appropriate handlers
   - Unit test request type classification

8. **Implement PastRequestHandler**
   - Use TimelineEventRetriever for cache checks
   - Use TimelineOvernightProcessor for generation
   - Use TimelineAssembler for boundary expansion + prepending
   - **Critical**: Preserve existing cache-first logic

9. **Implement MixedRequestHandler**  
   - Combine past (cached) + today (live) timelines
   - Use TimelineAssembler for cross-day gap detection
   - **Critical**: Test timeline combination thoroughly

### Phase 4: Testing Strategy Overhaul (Weeks 8-9)
**Goal**: Replace 6,855 lines of integration tests with focused unit tests

10. **Create Unit Test Suite** 
    - **80% unit tests**: Test business logic with mocks
    - **15% integration tests**: Database access patterns only
    - **5% system tests**: End-to-end critical scenarios
    - **Target**: Reduce test execution time from minutes to seconds

11. **Test Complex Logic Patterns**
    ```java
    // Example: Unit test overnight algorithm without database
    @Test void shouldUpdateExistingStayDuration_In4StepAlgorithm()
    @Test void shouldDetectCrossDayGap_WhenTimelinesHaveBoundaryGap()
    @Test void shouldAdjustPreviousStayDuration_ForContinuity()
    ```

### Phase 5: Gradual Rollout (Weeks 10-11)
**Goal**: Safely replace existing services with feature flags

12. **Feature Flag Implementation**
    ```java
    @ConfigProperty(name = "timeline.use-new-architecture", defaultValue = "false") 
    boolean useNewArchitecture;
    ```

13. **Parallel Execution & Validation**
    - Run both old and new implementations 
    - Compare results for consistency
    - Log any differences for investigation
    - **Critical**: Ensure 100% functional compatibility

14. **Gradual User Migration**
    - Start with 5% of users
    - Monitor error rates and performance
    - Gradually increase to 100%
    - **Rollback plan**: Immediate feature flag disable

### Phase 6: Cleanup & Optimization (Week 12)
**Goal**: Remove legacy code and optimize new implementation

15. **Remove Legacy Services**
    - Delete TimelineQueryService (587 lines)
    - Delete WholeTimelineProcessor (493 lines)  
    - Delete old integration tests
    - **Result**: ~1,080 lines removed

16. **Performance Optimization**
    - Add performance monitoring
    - Optimize database queries  
    - Add strategic caching
    - Monitor memory usage

### Critical Success Factors

**Preserve All Complex Logic:**
- ✅ Previous context prepending with duration adjustment
- ✅ Cross-day gap detection between timelines  
- ✅ 4-step overnight stay extension algorithm
- ✅ Multi-day vs single-day processing differences
- ✅ Data gap creation strategies (3 different patterns)
- ✅ Event boundary expansion logic

**Risk Mitigation:**
- Feature flags for immediate rollback
- Parallel execution for validation
- Comprehensive unit tests before migration
- Gradual user rollout with monitoring

**Success Metrics:**
- **Code Reduction**: 1,080 lines → ~500 lines (53% reduction)
- **Test Improvement**: 6,855 lines → ~2,000 lines (71% reduction)  
- **Test Speed**: Integration test suite → Unit test suite (10x faster)
- **Maintainability**: Single-responsibility services
- **Reliability**: Better error isolation and debugging

### Post-Implementation Benefits

1. **Developer Experience**
   - New features can be added without touching monolithic services
   - Business logic can be unit tested without database setup
   - Clear service boundaries make debugging straightforward

2. **System Reliability**  
   - Errors are isolated to specific services
   - Complex logic has comprehensive unit test coverage
   - Performance issues can be diagnosed at service level

3. **Future Extensibility**
   - New timeline event types can be added cleanly
   - Additional data sources can be integrated easily
   - Complex features like real-time timeline updates become feasible

## Conclusion

This redesign addresses the core issues in the current timeline system:

1. **Clear Business Logic**: Well-defined rules for timeline generation and persistence
2. **Proper Architecture**: Single-responsibility services with clear boundaries  
3. **Better Testing**: Fast unit tests with focused integration tests
4. **Maintainable Code**: Simpler, more understandable implementation

The phased implementation roadmap ensures all complex logic patterns are preserved while safely migrating from the current 1,080-line monolithic system to a clean, maintainable architecture.