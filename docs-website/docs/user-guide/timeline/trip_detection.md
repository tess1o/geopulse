---
title: Trip Detection
description: How GeoPulse detects the start and end of trips between locations.
---

# Trip Detection

GeoPulse uses intelligent algorithms to detect when you're traveling between locations. This document explains how trips are identified, from the moment you start moving to when you arrive at your destination.

---

## Overview

A trip represents movement between two stays. The system must solve two key challenges:

1. **Detecting trip start** - When have you actually left a location (not just walking around)?
2. **Detecting trip end** - When have you actually arrived somewhere (not just stopped at a traffic light)?

The algorithm uses **conservative detection** to avoid false positives while still capturing genuine arrivals promptly.

---

## Trip Lifecycle

### 1. Trip Initiation

A trip begins when you move away from a stay location:

```
Current State: CONFIRMED_STAY or POTENTIAL_STAY
Trigger: GPS point received outside stay radius
  AND not within same Favorite Area
Result: Transition to IN_TRIP mode
```

The first GPS point outside the stay becomes the **trip starting point**.

### 2. Trip in Progress

While in trip mode:
- Each GPS point is added to the active trip
- Speed, distance, and duration are tracked
- System continuously checks for arrival signals

### 3. Trip Termination

A trip ends when a **sustained stop** is detected:

```
Conditions for sustained stop:
  - Multiple consecutive GPS points
  - Points are spatially clustered (within stay radius)
  - Points show low/zero speed
  - Duration exceeds threshold
```

The system then:
1. Finalizes the trip
2. Calculates statistics (distance, duration, speeds)
3. Classifies the trip type (WALK, CAR, etc.)
4. Transitions to POTENTIAL_STAY for next location

---

## Arrival Detection Algorithm

The most critical part of trip detection is knowing when you've truly arrived. The algorithm uses a **two-tier approach** to balance responsiveness with accuracy.

### Tier 1: Clear Arrival Detection

This catches obvious arrivals where all signals agree:

**Conditions (ALL must be true):**
- At least **N** recent GPS points available (N = **Minimum Stop Points**, default 3, configurable 2-5)
- All points within stay radius of each other (spatially clustered)
- All points have speed ≤ stop speed threshold
- Duration of cluster ≥ **Arrival Detection Duration**

**When this triggers:**
- You've clearly stopped and parked
- GPS shows zero or near-zero movement
- You've been stationary for sufficient time
- **Stay begins at the timestamp of the FIRST stopped point** (retroactive)

### Tier 2: Sustained Stop Detection

This catches arrivals with less clear signals:

**Conditions (ALL must be true):**
- At least **N** recent GPS points available (same configurable threshold as Tier 1)
- All points have speed below stop threshold
- Duration ≥ **Sustained Stop Duration**

**When this triggers:**
- GPS clustering might not be perfect (points scattered beyond stay radius)
- But speed consistently shows you're stopped
- Acts as a fallback when Tier 1 doesn't match
- **Stay begins at the timestamp of the FIRST stopped point** (retroactive)

### Why Two Tiers?

**Problem:** Traffic lights and brief stops

If the system only used speed to detect stops, every red light would end your trip. By requiring **both** spatial clustering **and** low speed **and** duration (Tier 1), or consistent low speed with longer duration (Tier 2), the algorithm filters out:
- Traffic light stops (short duration, typically < 90 seconds)
- Traffic jams where you're technically moving slowly
- Brief stops that resume shortly

**Key Design Principles:**
1. **Configurable sensitivity:** Minimum points threshold (default 3) adapts to GPS frequency
2. **Retroactive timestamps:** Stay begins at FIRST stopped point, not detection point
3. **Two-tier fallback:** Tier 1 for perfect conditions, Tier 2 when GPS clustering isn't ideal

---

## Understanding the Three Duration Settings

GeoPulse uses three different duration settings that work together but serve distinct purposes. Understanding how they interact is key to configuring accurate trip detection.

### The Three Parameters

| Parameter | Default | Purpose | When It's Used |
|-----------|---------|---------|----------------|
| **Arrival Detection Duration** | 90 sec | End the trip | During trip, to detect you've stopped |
| **Sustained Stop Duration** | 60 sec | End the trip (fallback) | During trip, when clustering isn't perfect |
| **Minimum Stay Duration** | 7 min | Confirm the stay | After trip ends, to validate the location |

### How They Work Together

```
DRIVING (IN_TRIP mode)
    ↓
Stop at destination (parking lot)
    ↓
┌─────────────────────────────────────────┐
│ TRIP ENDING DETECTION                   │
│                                         │
│ Check 1: Arrival Detection              │
│   - 3+ GPS points clustered together    │
│   - All showing low/zero speed          │
│   - Duration ≥ 90 seconds               │
│                                         │
│ Check 2: Sustained Stop (fallback)      │
│   - 2+ GPS points with low speed        │
│   - Duration ≥ 60 seconds               │
│                                         │
│ Either check passing = TRIP ENDS        │
└─────────────────────────────────────────┘
    ↓
Enter POTENTIAL_STAY mode
    ↓
┌─────────────────────────────────────────┐
│ STAY CONFIRMATION                       │
│                                         │
│ - Continue collecting GPS points        │
│ - Wait for Minimum Stay Duration (7 min)│
│ - If you leave before 7 min: new trip   │
│ - If you stay 7+ min: CONFIRMED_STAY    │
└─────────────────────────────────────────┘
    ↓
CONFIRMED_STAY created
```

### Practical Example

You drive to a coffee shop:

```
10:00 - Leave home (trip starts)
10:15 - Arrive at coffee shop parking lot
10:15 - Park car, GPS shows you stopped

ARRIVAL DETECTION PHASE (during trip):
  10:15:00 - First stationary point (ACTUAL ARRIVAL)
  10:15:30 - Second stationary point
  10:16:00 - Third stationary point
  10:16:30 - 90 seconds elapsed with clustered, slow points
  → Arrival DETECTED (requires 3 points by default, configurable)
  → Trip ENDS at 10:15:00, STAY BEGINS at 10:15:00

STAY CONFIRMATION PHASE (after trip):
  10:15:00 - Start of potential stay (RETROACTIVE to first stopped point)
  10:16:30 - Still at location (90 sec)
  10:20:00 - Still at location (5 min)
  10:22:00 - 7 minutes elapsed since arrival
  → Stay CONFIRMED

Timeline result:
  - Trip: Home → Coffee Shop (10:00-10:15:00)
  - Stay: Coffee Shop (10:15:00-...)
```

:::tip Retroactive Stay Timestamps
**Important:** When arrival is detected, the stay start time is **retroactively set** to the timestamp of the **first stopped point**, not the detection time. This ensures accurate trip durations and stay times.

- **Before fix (old behavior):** Stay started at 10:16:30 (detection time) → 90-second delay
- **After fix (current):** Stay starts at 10:15:00 (actual arrival) → accurate timestamps
:::

:::info Configurable Detection Threshold
The number of stopped points required for detection (default: 3) is now configurable via the **Minimum Stop Points** setting. Users with infrequent GPS (10-15 min intervals) should set this to **2 points** to avoid excessive delays.
:::

### Why Separate Settings?

**Different questions being answered:**

- **Arrival Detection (90s):** "Has the vehicle stopped moving?"
  - Needs to be long enough to filter traffic lights (~60-90 sec cycles)
  - But short enough to detect real arrivals promptly

- **Minimum Stay Duration (7 min):** "Is this a meaningful location?"
  - Needs to be long enough to filter brief stops (gas station drive-through)
  - But short enough to capture intentional short visits

**Example of why both matter:**

```
Scenario: Stop at ATM for 2 minutes (with 3 points, 30-sec GPS)

10:00:00 - Arrive at ATM (1st stopped point)
10:00:30 - Still stopped (2nd point)
10:01:00 - Still stopped (3rd point)
10:01:30 - 90 seconds elapsed → Arrival detected
          Trip ends at 10:00:00 (first stopped point - RETROACTIVE)
          Stay begins at 10:00:00 (not 10:01:30)
10:02:00 - Leave ATM → New trip starts

Result: No stay created (2 min < 7 min minimum)
The brief ATM stop correctly doesn't clutter your timeline.
Note: Trip duration accurate (ended when you stopped, not when detected).
```

```
Scenario: Traffic light for 3 minutes

10:00:00 - Stop at red light (1st stopped point)
10:00:30 - Still stopped (2nd point)
10:01:00 - Still stopped (3rd point) - could trigger detection
  BUT: Light turns green at 10:01:45, you start moving
  → Duration check fails (< 90s minimum), trip continues

Result: No trip split, continuous journey preserved.
```

### Common Configuration Scenarios

**Want to capture brief stops (like deliveries):**
```
Arrival Detection Duration: 60s (faster trip ending)
Minimum Stay Duration: 2-3 min (capture short stops)
```

**Want to avoid trip splits at long traffic lights:**
```
Arrival Detection Duration: 120s (ignore longer stops)
Minimum Stay Duration: 7 min (default)
```

**Want only significant locations:**
```
Arrival Detection Duration: 90s (default)
Minimum Stay Duration: 15 min (only meaningful stops)
```

---

## Key Settings Explained

### Arrival Detection Duration

**Setting:** `Arrival Detection Duration`
**Default:** 90 seconds
**Range:** 10-300 seconds

The minimum time for GPS points to show clustered, slow movement before confirming arrival.

| Value | Effect | Best For |
|-------|--------|----------|
| **10-30s** | Very quick arrival detection | Not recommended (false arrivals) |
| **60-90s** | Balanced detection | Most users |
| **120-180s** | Conservative detection | Highway driving, long trips |
| **240-300s** | Very conservative | Only if getting false arrivals |

**Trade-offs:**
- **Shorter duration:** Faster detection but may split trips at long traffic stops
- **Longer duration:** More robust but slower to detect genuine arrivals

:::tip Traffic Light Consideration
Default 90 seconds works well because most traffic lights cycle in 60-90 seconds. If you frequently stop at lights longer than this, increase to 120 seconds.
:::

### Sustained Stop Duration

**Setting:** `Sustained Stop Duration`
**Default:** 60 seconds
**Range:** 10-600 seconds

Minimum duration of consistent slow movement for the fallback detection mechanism.

| Value | Effect | Best For |
|-------|--------|----------|
| **10-30s** | Very sensitive | Not recommended |
| **60s** | Balanced | Most users |
| **120-180s** | Less sensitive | Reduce false arrivals |
| **300-600s** | Very conservative | Highway-focused tracking |

**Trade-offs:**
- **Shorter duration:** More responsive but more false positives
- **Longer duration:** More robust but may miss quick turnaround stops

### Minimum Stop Points for Arrival Detection

**Setting:** `Minimum Stop Points for Arrival Detection`
**Default:** 3 points
**Range:** 2-5 points

Minimum number of GPS points required to detect arrival at a destination. Both arrival detection tiers (spatial clustering + speed, and speed-only fallback) use this value.

| Value | Effect | Best For |
|-------|--------|----------|
| **2 points** | Faster detection | Infrequent GPS (10-15 min intervals) |
| **3 points** | Balanced detection | Most users (30-60 sec intervals) |
| **4-5 points** | Conservative detection | Very sensitive setups, high-frequency GPS |

**Critical for Infrequent GPS Tracking:**

If you use battery-saving automations with GPS updates every 10-15 minutes, set this to **2 points**:
- With 3 points (default): 30-45 minute delay before arrival detection
- With 2 points: 10-15 minute delay before arrival detection

**Example Scenario:**
```
GPS Frequency: Every 15 minutes
Arrival Time: 10:00 AM

With 3 points (default):
  10:00 - 1st stopped point
  10:15 - 2nd stopped point
  10:30 - 3rd stopped point → Arrival detected
  Result: 30-minute delay, trip duration inflated

With 2 points (configured):
  10:00 - 1st stopped point
  10:15 - 2nd stopped point → Arrival detected
  Result: 15-minute delay, more accurate timeline
```

**Trade-offs:**
- **Lower values (2):** Faster arrival detection but may increase false positives at long traffic lights
- **Higher values (3-4):** More reliable detection but requires more GPS points, causing delays with infrequent tracking

:::tip GPS Frequency Recommendation
Match this setting to your GPS tracking frequency:
- **High frequency (30-60s):** Use 3-4 points (default)
- **Medium frequency (2-5 min):** Use 2-3 points
- **Low frequency (10-15 min):** Use 2 points
:::

### Stop Speed Threshold

**Internal Setting:** 2 m/s (7.2 km/h)

Speed at or below which movement is considered "stopped." This is an internal threshold that balances:
- GPS noise (stationary phones often show 1-3 km/h)
- Slow walking within a location
- Idle vehicle movement

---

## Multiple Trip Detection Algorithm

GeoPulse supports two algorithms for detecting trips between stays:

### Single Trip Algorithm

**Setting:** `Trip Detection Algorithm = single`

Always creates exactly one trip between two stays.

**Behavior:**
- Movement from Stay A to Stay B = 1 trip
- Entire journey gets one classification
- Simpler timeline view

**Best for:**
- Most users
- Clean, simple timelines
- When you don't change transport modes mid-journey

### Multiple Trip Algorithm

**Setting:** `Trip Detection Algorithm = multiple`

Intelligently detects legitimate mode changes during a journey and creates separate trips for each segment.

**Behavior:**
- Analyzes velocity patterns to detect mode transitions
- Walking to car → driving → walking from car = 3 trips
- Merges fragmented same-mode segments automatically
- Only splits when mode changes are significant

**Best for:**
- Multi-modal commutes (walk → train → walk)
- Detailed movement analysis
- Users who frequently switch transport modes

### How Multiple Trip Detection Works

The algorithm uses sophisticated analysis to distinguish legitimate mode changes from traffic-induced fragmentation:

#### Step 1: Merge Same-Type Fragments

First, consecutive trips of the same type are merged to eliminate noise:

```
Input:  [WALK, WALK, WALK, CAR, CAR, WALK]
Output: [WALK, CAR, WALK]
```

This prevents traffic slowdowns from creating artificial trip fragments.

#### Step 2: Validate Mode Significance

The algorithm then checks if each mode contributes meaningfully to the journey using a **15% contribution threshold**:

```
For mode changes to be kept separate, BOTH modes must contribute:
  - At least 15% of total distance, OR
  - At least 15% of total time
```

**Example 1: Legitimate mode change (kept separate)**
```
Journey: Walk 500m (5 min) → Drive 3km (10 min)

Walking: 500m / 3500m = 14.3% distance, 5min / 15min = 33% time
Driving: 3000m / 3500m = 85.7% distance, 10min / 15min = 67% time

Time-based check passes (both > 15%)
→ Result: 2 separate trips (WALK + CAR)
```

**Example 2: Traffic fragmentation (merged)**
```
Journey: Drive 2km → Brief slow segment (50m) → Drive 3km

Walking segment: 50m / 5050m = 1% distance, minimal time
Driving: 5000m / 5050m = 99% distance

Neither check passes for walking
→ Result: 1 merged trip (CAR)
```

#### Step 3: Ensure Timeline Continuity

The algorithm always ensures at least one trip exists between stays, even if all segments are too short to meet normal thresholds. This preserves timeline integrity.

### Practical Examples

**Morning Commute with Multiple Modes:**
```
07:00 - Leave home
07:10 - Walk to train station (800m)
07:15 - Board train
07:45 - Arrive at destination station
07:55 - Walk to office (600m)
08:00 - Arrive at office

With Multiple Trip Algorithm:
  - Trip 1: WALK 10 min, 800m (07:00-07:10)
  - Stay: Train Station (07:10-07:15)
  - Trip 2: TRAIN 30 min (07:15-07:45)
  - Trip 3: WALK 10 min, 600m (07:50-08:00)

With Single Trip Algorithm:
  - Trip: Mixed mode, 30+ min (07:00-07:55)
  - (Train station stay still detected due to 5+ min wait)
```

**Parking Lot to Building:**
```
08:30 - Park car
08:32 - Walk 200m to office entrance

Walking: 200m (2 min)
Driving: previous segment

Walking contribution < 15% of journey
→ Result: Merged into single CAR trip
  (The brief walk is absorbed, not shown separately)
```

**Drive with Traffic Jam:**
```
09:00 - Start driving
09:15 - Heavy traffic (GPS shows walking speeds for 3 min)
09:18 - Traffic clears
09:30 - Arrive

The 3-minute slow segment doesn't meet 15% threshold
→ Result: Single CAR trip (09:00-09:30)
```

### Choosing Between Algorithms

| Scenario | Recommended Algorithm |
|----------|----------------------|
| Simple car commute | Single |
| Walk → Transit → Walk commute | Multiple |
| Delivery routes with driving only | Single |
| Fitness tracking (run + walk intervals) | Multiple |
| Mixed urban transport | Multiple |
| Long road trips | Single |

:::tip
If you use public transit or frequently combine walking with other transport modes, the **Multiple** algorithm will give you more accurate and detailed trip breakdowns. For car-only travel, **Single** provides a cleaner timeline.
:::

---

## Short Trip Handling

Very short trips receive special treatment to account for GPS limitations.

### Short Distance Threshold

**Setting:** `Short Trip Distance Threshold`
**Default:** 1.0 km
**Range:** 0.1-3.0 km

Trips shorter than this distance use more lenient speed classification.

**Why this matters:**
- Short trips have less GPS data
- Fewer points = more statistical noise
- Speed calculations are less reliable

**Effect:**
- Walking speed thresholds get a small tolerance boost
- Reduces false CAR classifications for brief walks
- Accounts for GPS inaccuracies over short distances

---

## Trip Statistics Calculation

When a trip is finalized, the system calculates detailed statistics:

### Distance Calculation

```
Total Distance = Sum of distances between consecutive GPS points
```

This gives actual traveled distance, not straight-line distance.

### Speed Statistics

**Average Speed:**
```
Average = Mean of all GPS-reported speeds during trip
```

**Maximum Speed:**
```
Maximum = Highest GPS-reported speed
```

**Speed Variance:**
```
Variance = Statistical variance of speeds
(Used to distinguish trains from cars)
```

### Duration

```
Duration = Last point timestamp - First point timestamp
```

---

## Common Scenarios

### Scenario 1: Drive to Work

```
08:00:00 - Leave home (CONFIRMED_STAY → IN_TRIP)
08:00-08:29 - Driving with traffic stops
08:29:30 - Park at work (1st stopped point - ACTUAL ARRIVAL)
08:30:00 - Walk to building (2nd stopped point)
08:30:30 - Arrive at desk (3rd stopped point)
08:31:00 - Detection: 3 points, 90 seconds, clustered → Arrival confirmed
          Trip ends at 08:29:30 (RETROACTIVE to first stopped point)
          Stay begins at 08:29:30 (not 08:31:00)

Timeline:
  - Stay: Home (until 08:00:00)
  - Trip: 29.5 min drive (08:00:00-08:29:30)
  - Stay: Work (from 08:29:30)
```

**Key Points:**
- The 1.5-minute detection delay (08:29:30 → 08:31:00) does NOT inflate trip duration
- Stay timestamp is **retroactive** to when you actually stopped (08:29:30)
- Walk from car to desk absorbed into stay cluster (all points within stay radius)

### Scenario 2: Extended Traffic Stop

```
09:00 - Driving on highway
09:15 - Major traffic jam, nearly stopped
09:18 - Traffic clears, resume driving
09:30 - Arrive at destination

Timeline:
  - Trip: 30 min drive (09:00-09:30)
```

The 3-minute traffic stop doesn't create a stay because:
- Duration (3 min) < Arrival Detection Duration (90s minimum, but spatial clustering also required)
- Movement resumes before stay conditions are met

### Scenario 3: Brief Errand Stop

```
10:00 - Leave office
10:10 - Stop at gas station (3 minutes)
10:13 - Continue to lunch
10:25 - Arrive at restaurant

Timeline with 7-min minimum stay:
  - Trip: 25 min drive (10:00-10:25)
  (Gas station not captured as stay - too short)

Timeline with 3-min minimum stay:
  - Trip: 10 min (10:00-10:10)
  - Stay: Gas Station (10:10-10:13)
  - Trip: 12 min (10:13-10:25)
```

Configure **Minimum Stay Duration** based on whether you want brief stops captured.

### Scenario 4: Walking to Transit

```
07:00 - Leave home
07:10 - Arrive at train station
07:15 - Train departs
07:45 - Arrive at destination station
07:50 - Walk to office
08:00 - Arrive at office

Timeline (with Train enabled):
  - Trip: WALK 10 min (07:00-07:10)
  - Stay: Train Station (07:10-07:15)
  - Trip: TRAIN 30 min (07:15-07:45)
  - Trip: WALK 10 min (07:50-08:00)
```

Multiple trips are created because the train station creates an intermediate stay (>5 min wait).

---

## Configuration Recommendations

### For Different Use Cases

**Daily Commuting:**
```
Arrival Detection Duration: 90s (default)
Sustained Stop Duration: 60s (default)
Trip Detection Algorithm: single
```

**Delivery/Field Service:**
```
Arrival Detection Duration: 60s (faster detection)
Sustained Stop Duration: 45s (quicker arrivals)
Minimum Stop Points: 2-3 (responsive detection)
Minimum Stay Duration: 2-3 min (catch brief stops)
```

**Road Trip Tracking:**
```
Arrival Detection Duration: 120s (avoid rest stop false ends)
Sustained Stop Duration: 90s
Minimum Stay Duration: 10-15 min (only significant stops)
```

**Multi-Modal Commute:**
```
Trip Detection Algorithm: single (or multiple if you want detail)
Enable Train, Bicycle as needed
Arrival Detection Duration: 90s
Minimum Stop Points: 3 (default)
```

**Infrequent GPS / Battery Saving:**
```
Minimum Stop Points: 2 (CRITICAL for 10-15 min intervals)
Arrival Detection Duration: 90-120s
Sustained Stop Duration: 60s
Minimum Stay Duration: 7-10 min
```
:::warning
If using GPS automations with 10-15 minute intervals (e.g., iOS Shortcuts), **you must set Minimum Stop Points to 2**. The default value of 3 will cause 30-45 minute delays in arrival detection, making trip durations appear much longer than reality.
:::

### For Different GPS Quality

**High-Quality GPS:**
```
Arrival Detection Duration: 60-90s
Stay Detection Radius: 30-50m
```

**Average GPS Quality:**
```
Arrival Detection Duration: 90s (default)
Stay Detection Radius: 50m
```

**Poor GPS Quality:**
```
Arrival Detection Duration: 120s (more conservative)
Stay Detection Radius: 75-100m
```

---

## Retroactive Stay Timestamps

**Important Behavioral Change:** When arrival is detected, the stay start time is set **retroactively** to the timestamp of the **first stopped point**, not the detection time.

### Why This Matters

Previously, the system would:
1. Detect arrival when N points met the criteria (e.g., 3 points at 10:00, 10:15, 10:30)
2. Start the stay at the **detection time** (10:30 - the last point)
3. This inflated trip durations by the detection delay

**Example of the problem:**
```
User arrives at 10:00 AM (GPS every 15 min)
Points: 10:00, 10:15, 10:30 (3rd point triggers detection)

OLD BEHAVIOR (incorrect):
  - Trip ends: 10:30 (detection time)
  - Stay starts: 10:30 (detection time)
  - Result: Trip appears 30 minutes longer than reality

NEW BEHAVIOR (correct):
  - Trip ends: 10:00 (first stopped point)
  - Stay starts: 10:00 (first stopped point)
  - Result: Accurate trip duration
```

### How It Works

When the algorithm detects a sustained stop:

1. **Identifies the stopped cluster:** The last N points that meet stop criteria (default N=3, configurable)
2. **Splits the timeline:**
   - **Trip points:** All points BEFORE the first stopped point
   - **Stay points:** The stopped cluster (N points)
3. **Finalizes retroactively:**
   - Trip ends at timestamp of last moving point
   - Stay begins at timestamp of first stopped point

**Visual representation:**
```
Trip Points:     [P1] [P2] [P3] [P4] [P5]  |  [S1] [S2] [S3]
                  ↑                    ↑       ↑           ↑
               Moving              Moving  Stopped    Detected
                                   (last)   (first)    (3rd)

Trip ends at: P5 timestamp (last moving point)
Stay starts at: S1 timestamp (first stopped point)
NOT at: S3 timestamp (detection point) ❌
```

### Benefits

✅ **Accurate trip durations:** No inflation from detection delays
✅ **Accurate stay timestamps:** Reflects when you actually arrived
✅ **Works with infrequent GPS:** Even with 15-min intervals, timestamps are correct
✅ **Consistent timeline:** No gaps between trip end and stay start

### Configuration Impact

The **Minimum Stop Points** setting affects how many points form the stopped cluster:
- **2 points:** Faster detection, shorter potential delay (suitable for infrequent GPS)
- **3 points (default):** Balanced detection, filters most traffic lights
- **4-5 points:** Very conservative, but longer detection delay

Regardless of the setting, the stay **always** starts at the **first stopped point** in the cluster.

---

## Troubleshooting

### Problem: Trips are split at traffic lights

**Possible Causes:**
- Arrival Detection Duration too short
- GPS showing near-zero speeds for extended periods

**Solutions:**
1. Increase **Arrival Detection Duration** to 120-180 seconds
2. Increase **Sustained Stop Duration** to 90-120 seconds
3. Check if GPS is reporting unrealistically low speeds during traffic

### Problem: Genuine arrivals aren't detected quickly

**Possible Causes:**
- Detection thresholds too conservative
- GPS not reporting zero speed when stopped
- **Infrequent GPS tracking** (most common with battery-saving automations)

**Solutions:**
1. **For Infrequent GPS (10-15 min intervals):** Decrease **Minimum Stop Points** to 2
2. Decrease **Arrival Detection Duration** to 60 seconds
3. Decrease **Sustained Stop Duration** to 45 seconds
4. Check GPS source configuration for speed reporting

**Example: Infrequent GPS Issue**
```
Symptom: Drive from work to home takes 10 minutes, but timeline shows 1.5 hours
Cause: GPS updates every 15 minutes, need 3 points for detection (45 min delay)
Solution: Set Minimum Stop Points to 2
Result: Detection after 2 points (15 min delay instead of 45 min)
```

### Problem: Walking between car and building creates extra trip

**Possible Causes:**
- Normal behavior for longer walks
- Distance exceeds stay radius

**Solutions:**
1. This is often correct behavior - the walk is a valid mini-trip
2. Increase **Stay Detection Radius** if you want to merge
3. Define a Favorite Area for the parking lot + building

### Problem: Trip shows impossible route or distance

**Possible Causes:**
- GPS noise or gaps during trip
- Poor accuracy points included

**Solutions:**
1. Check GPS source accuracy settings
2. Enable path simplification to smooth noise
3. Review GPS accuracy threshold settings

### Problem: Trip statistics seem wrong

**Possible Causes:**
- GPS-reported speeds are inaccurate
- Missing GPS points during key moments

**Solutions:**
1. System uses GPS speeds when reliable, calculated speeds as fallback
2. Check GPS tracking frequency (30-60 second intervals recommended)
3. Some variation is normal due to GPS limitations

---

## Impact on Timeline Rebuild

Changing trip detection settings triggers a **full timeline rebuild**:

**What Changes:**
- All trips are re-detected with new parameters
- Trip start/end times may shift
- Some trips may merge or split

**What Doesn't Change:**
- Original GPS data remains intact
- Favorite locations are preserved

:::info Rebuild Time
After changing trip detection settings, the timeline will rebuild completely. For large datasets, this may take several minutes.
:::

---

## Related Settings

Trip detection works together with other timeline features:

- **[Stay Detection](/docs/user-guide/timeline/stay_detection)** - How stationary periods are identified
- **[Travel Classification](/docs/user-guide/timeline/travel_classification)** - How trip types are determined
- **[Data Gaps](/docs/user-guide/timeline/data_gaps)** - How missing GPS data affects trips
- **[Timeline Merging](/docs/user-guide/core-features/timeline#simplifying-your-timeline-merging-and-cleaning-up)** - How nearby stays are consolidated

---

## Summary

Trip detection identifies movement between stays using intelligent algorithms:

- **Trip start:** Detected when you move beyond stay radius
- **Trip end:** Detected via sustained stop with spatial clustering and low speed
- **Two-tier detection:** Balances responsiveness with traffic stop filtering
- **Configurable thresholds:** Adjust for your traffic conditions and use case
- **Path simplification:** Reduces storage while preserving route accuracy

By understanding these settings and tuning them for your typical travel patterns, you can ensure GeoPulse accurately captures your trips without false starts or ends due to traffic conditions.
