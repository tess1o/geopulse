# Abstractions and Data Models

Java example

```java
public enum EventType {
    STAY, TRIP, DATA_GAP
}


public enum TripType {
    WALK, CAR, UNKNOWN // Added for trip classification
}

// Internal state for the processor's state machine
enum ProcessorMode {
    UNKNOWN, POTENTIAL_STAY, CONFIRMED_STAY, IN_TRIP
}
```

Data Models (POJOs)
The input GPSPoint and the output TimelineEvent hierarchy.

```java
import java.time.Instant;

public class GPSPoint {
    private final Instant timestamp;
    private final double latitude;
    private final double longitude;
    private final double speed; // in meters/second

    // Constructor, Getters...

}

public abstract class TimelineEvent {
    protected EventType type;
    protected Instant startTime;
    protected Instant endTime;

    // Getters...

}

public class Stay extends TimelineEvent {
    private double latitude;
    private double longitude;
// Constructor, Getters...
}

public class Trip extends TimelineEvent {
    private TripType tripType;
    private List<GPSPoint> path;
    private double distanceMeters;
// Constructor, Getters...
}

public class DataGap extends TimelineEvent {
// Constructor, Getters...
}

```

2. User Preferences (Customization)
   This class is key to making your algorithm flexible. You can pass an instance of this to the processor.

```java
import java.time.Duration;

public class TimelinePreferences {
    // Duration-based preferences
    private final Duration minStayDuration;
    private final Duration minTripDuration;
    private final Duration dataGapThreshold;

    // Distance and speed-based preferences
    private final double stayRadiusMeters;
    private final double minTripDistanceMeters;
    private final double walkCarSpeedThresholdMps; // Meters per second

    public TimelinePreferences() {
        // Default values
        this.minStayDuration = Duration.ofMinutes(10);
        this.minTripDuration = Duration.ofMinutes(1);
        this.dataGapThreshold = Duration.ofHours(1);
        this.stayRadiusMeters = 100.0;
        this.minTripDistanceMeters = 200.0;
        this.walkCarSpeedThresholdMps = 4.5; // e.g., ~16 km/h
    }

    // A constructor to set custom values would go here
    // Getters for all fields...

}
```

3. State Management
   This object holds the "memory" of the algorithm for a single user between GPS points.

```java
import java.util.ArrayList;
import java.util.List;

public class UserState {
    private ProcessorMode currentMode = ProcessorMode.UNKNOWN;
    private GPSPoint lastProcessedPoint;
    private List<GPSPoint> activePoints = new ArrayList<>();

    // Constructor, Getters, and Setters...

}


public class ProcessingResult {
    private final UserState updatedState;
    private final List<TimelineEvent> finalizedEvents;

    // Constructor, Getters...

}
```

4. The Core Algorithm (TimelineProcessor)
   This is the most important part, containing the state machine logic. It takes the preferences in its constructor and
   processes points via the processPoint method.

```java
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TimelineProcessor {

    private final TimelinePreferences prefs;

    public TimelineProcessor(TimelinePreferences prefs) {
        this.prefs = prefs;
    }

    public ProcessingResult processPoint(GPSPoint point, UserState state) {
        List<TimelineEvent> finalizedEvents = new ArrayList<>();

        // 1. Handle Data Gaps first - this can interrupt any state
        if (state.getLastProcessedPoint() != null) {
            Duration timeDelta = Duration.between(state.getLastProcessedPoint().getTimestamp(), point.getTimestamp());
            if (timeDelta.compareTo(prefs.getDataGapThreshold()) > 0) {
                // A gap occurred. Finalize what was active before the gap.
                TimelineEvent lastEvent = finalizeActiveEvent(state, state.getLastProcessedPoint());
                if (lastEvent != null) {
                    finalizedEvents.add(lastEvent);
                }

                // Create the data gap event
                finalizedEvents.add(new DataGap(state.getLastProcessedPoint().getTimestamp(), point.getTimestamp()));

                // Reset the state completely before proceeding with the current point
                state = new UserState();
            }
        }

        // 2. The Main State Machine
        switch (state.getCurrentMode()) {
            case UNKNOWN:
                transitionToPotentialStay(state, point);
                break;

            case POTENTIAL_STAY:
                // Logic as described before: check distance, then check duration
                // If point is far -> transition to trip
                // If point is close and duration > minStayDuration -> transition to confirmed stay
                break;

            case CONFIRMED_STAY:
                // Logic: check distance
                // If point is far -> finalize stay, transition to trip
                break;

            case IN_TRIP:
                state.getActivePoints().add(point);
                // Check for a potential stop (e.g., low speed)
                if (point.getSpeed() < 2.0) { // Simple heuristic for stopping
                    TimelineEvent trip = finalizeActiveEvent(state, point);
                    if (trip != null) {
                        finalizedEvents.add(trip);
                    }
                    transitionToPotentialStay(state, point);
                }
                break;
        }

        state.setLastProcessedPoint(point);
        return new ProcessingResult(state, finalizedEvents);
    }

    private void transitionToPotentialStay(UserState state, GPSPoint point) {
        state.setCurrentMode(ProcessorMode.POTENTIAL_STAY);
        state.getActivePoints().clear();
        state.getActivePoints().add(point);
    }

    private TimelineEvent finalizeActiveEvent(UserState state, GPSPoint lastPointOfEvent) {
        if (state.getActivePoints().isEmpty()) {
            return null;
        }

        switch (state.getCurrentMode()) {
            case POTENTIAL_STAY: // A potential stay that was interrupted is just part of a trip
            case IN_TRIP:
                return finalizeTrip(state, lastPointOfEvent);

            case CONFIRMED_STAY:
                return finalizeStay(state);
        }
        return null;
    }

    /**
     * This is where the Trip Type detection happens.
     */
    private Trip finalizeTrip(UserState state, GPSPoint endPoint) {
        List<GPSPoint> path = new ArrayList<>(state.getActivePoints());
        path.add(endPoint);
        // ... calculate total distance ...

        // **Trip Type Detection Logic**
        // Calculate the 85th percentile speed to be robust against traffic stops
        double highPercentileSpeed = path.stream()
                .mapToDouble(GPSPoint::getSpeed)
                .sorted()
                .skip((long) (path.size() * 0.85))
                .findFirst()
                .orElse(0.0);

        TripType type = (highPercentileSpeed > prefs.getWalkCarSpeedThresholdMps())
                ? TripType.CAR
                : TripType.WALK;

        // ... Check against minTripDistanceMeters and minTripDuration from prefs ...
        // If it meets criteria, create and return new Trip object, otherwise return null.
        return new Trip(type, path, ...);
    }

    private Stay finalizeStay(UserState state) {
        // ... calculate centroid of stay points ...
        // create and return new Stay object
        return new Stay(...);
    }
}
```

# Example

```
Time	Location	Speed (m/s)	Action & State Transition	Output Events
10:00 AM	(Home Lat/Lon)	0.5	Point 1: current_mode is UNKNOWN. Transition -> POTENTIAL_STAY. Start time is 10:00.	None
10:03 AM	(Home Lat/Lon)	0.2	Point 2: mode is POTENTIAL_STAY. Point is close to centroid. Duration (3 min) < T_MIN_STAY. State remains POTENTIAL_STAY.	None
10:11 AM	(Home Lat/Lon)	0.4	Point 3: mode is POTENTIAL_STAY. Point is close. Duration (11 min) > T_MIN_STAY. Transition -> CONFIRMED_STAY.	None
10:45 AM	(Home Lat/Lon)	0.3	Point 4: mode is CONFIRMED_STAY. Point is close. State remains CONFIRMED_STAY.	None
10:46 AM	(Street nearby)	8	Point 5: mode is CONFIRMED_STAY. Point is > R_STAY away. Finalize Stay from 10:00-10:45. Transition -> IN_TRIP starting at 10:46.	STAY (10:00-10:45 @ Home)
10:55 AM	(On highway)	25	Point 6: mode is IN_TRIP. Speed is high. Continue trip.	None
11:05 AM	(Work Lat/Lon)	1.5	Point 7: mode is IN_TRIP. Speed is low. Finalize Trip from 10:46-11:05. Transition -> POTENTIAL_STAY starting at 11:05.	TRIP (10:46-11:05)
11:40 AM	(Work Lat/Lon)	0.6	No data for 35 mins! Point 8 arrives. time_delta > T_GAP_THRESHOLD. Finalize previous event (POTENTIAL_STAY). Create Gap. Reset state. Process point.	DATA_GAP (11:05-11:40)
...	...	...	After the gap, the logic restarts with Point 8, which will immediately trigger a new POTENTIAL_STAY.	...
```
