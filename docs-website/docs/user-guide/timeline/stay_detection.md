---
title: Stay Detection
description: How GeoPulse identifies when you've stayed at a location using GPS clustering and duration analysis.
---

# Stay Detection

GeoPulse uses a sophisticated algorithm to identify when you've stopped at a location. This document explains the complete process of how stays are detected, from initial clustering to confirmation.

---

## Overview

Stay detection is the foundation of timeline generation. The system analyzes your GPS data to identify periods when you were stationary at a specific location. A stay is created when:

1. **GPS points cluster together** within a defined radius
2. **You remain stationary** for a minimum duration
3. **GPS data quality** meets accuracy requirements

The algorithm uses a **state machine** that transitions between states as it processes each GPS point, making decisions based on distance, time, and movement patterns.

---

## The Detection State Machine

GeoPulse processes GPS points sequentially through a four-state machine:

```
UNKNOWN → POTENTIAL_STAY → CONFIRMED_STAY
                ↓               ↓
              IN_TRIP ←─────────┘
```

### State Descriptions

| State | Description | What Triggers Exit |
|-------|-------------|-------------------|
| **UNKNOWN** | Initial state before any GPS data | First GPS point received |
| **POTENTIAL_STAY** | Clustering in progress, not yet confirmed | Duration threshold met OR movement detected |
| **CONFIRMED_STAY** | Valid stay identified | Movement beyond stay radius |
| **IN_TRIP** | Moving between locations | Sustained stop detected |

---

## How a Stay is Detected

### Step 1: Initial Clustering

When the first GPS point arrives, the system enters **POTENTIAL_STAY** mode and begins building a cluster:

1. The first point becomes the seed of the cluster
2. Each subsequent point is compared to the cluster's **centroid** (center point)
3. The centroid is calculated as the mean latitude and longitude of all clustered points

### Step 2: Distance Check

For each new GPS point, the system calculates the distance from the current centroid:

**If distance ≤ Stay Detection Radius:**
- Point is added to the cluster
- Centroid is recalculated
- Continue clustering

**If distance > Stay Detection Radius:**
- Check for [Favorite Area](#favorite-areas-special-handling) containment
- If not in same area: transition to **IN_TRIP**
- Current cluster becomes a trip starting point

### Step 3: Duration Confirmation

While clustering continues (distance checks pass), the system tracks the total duration:

```
Duration = Last point timestamp - First point timestamp
```

**If duration ≥ Minimum Stay Duration:**
- Transition from POTENTIAL_STAY to **CONFIRMED_STAY**
- Stay is now considered valid

**If duration < Minimum Stay Duration:**
- Remain in POTENTIAL_STAY
- Continue collecting points

### Step 4: Stay Finalization

A confirmed stay ends when movement is detected:

1. New GPS point falls outside the stay radius
2. Point is not within a shared Favorite Area
3. System finalizes the stay with:
   - Start time (first clustered point)
   - Duration (last - first timestamp)
   - Location (centroid coordinates)
4. Transition to **IN_TRIP** mode

---

## Key Settings Explained

### Stay Detection Radius

**Setting:** `Stay Detection Radius`
**Default:** 50 meters
**Range:** 10-500 meters

This defines the size of the "bubble" used for clustering GPS points.

| Value | Effect | Best For |
|-------|--------|----------|
| **10-30m** | Very sensitive, separate nearby locations | Dense urban areas, multiple stores in a mall |
| **50m** | Balanced detection | Most users, typical accuracy |
| **100-200m** | Merge nearby locations | Poor GPS areas, large venues |
| **300-500m** | Very conservative | Extremely poor GPS, general location tracking |

**Trade-offs:**
- **Smaller radius:** More precise but may split single visits due to GPS drift
- **Larger radius:** More stable but may merge distinct nearby locations

:::tip GPS Drift Consideration
If your GPS device has accuracy issues (±30-50m), use a larger radius (75-100m) to prevent artificial stay splits from GPS wandering.
:::

### Minimum Stay Duration

**Setting:** `Minimum Stay Duration`
**Default:** 7 minutes
**Range:** 1-60 minutes

The minimum time you must remain stationary for a stay to be confirmed.

| Value | Effect | Best For |
|-------|--------|----------|
| **1-3 min** | Detect very short stops | Delivery drivers, frequent short stops |
| **5-7 min** | Balanced detection | Most users |
| **10-15 min** | Filter out traffic delays | Commuters, road trips |
| **20-60 min** | Only significant stays | Business meetings, appointments only |

**Trade-offs:**
- **Shorter duration:** More sensitive but may capture traffic stops as stays
- **Longer duration:** Cleaner timeline but may miss brief meaningful stops

:::warning Traffic Light Problem
Setting duration too low (1-2 minutes) will cause extended traffic stops to appear as stays. The default 7 minutes filters most traffic delays while capturing genuine stops.
:::

---

## GPS Quality Filtering

GeoPulse uses multiple layers of quality filtering to ensure accurate stay detection.

### Accuracy-Based Point Filtering

**Setting:** `GPS Accuracy Threshold`
**Default:** 60 meters
**Range:** 5-200 meters

Points with accuracy worse than this threshold are **excluded** from processing entirely.

```
If GPS point accuracy > threshold:
  → Point is ignored (not processed)
```

**Why this matters:**
- Poor GPS accuracy (>60m) indicates the device is uncertain about location
- Including these points would degrade cluster quality
- Filtered points don't affect stay detection at all

### Cluster Quality Validation

**Setting:** `Minimum Accuracy Ratio`
**Default:** 50% (0.5)
**Range:** 10-100%

After a stay cluster is formed, the system validates overall quality:

```
Accurate points = count(points with accuracy ≤ threshold)
Ratio = Accurate points / Total points

If ratio < Minimum Accuracy Ratio:
  → Stay is rejected
```

**Example:**
- 10 points in a cluster
- 4 points have accuracy ≤ 60m (good)
- 6 points have accuracy > 60m (poor)
- Ratio = 4/10 = 40%
- With default 50% threshold: **Stay rejected**

This ensures that stays are only created when sufficient high-quality GPS data exists.

### Enhanced Filtering Toggle

**Setting:** `Enhanced Filtering`
**Default:** Enabled

When enabled, the system uses both accuracy and velocity data for filtering. When disabled:
- All accuracy-based filtering is bypassed
- Useful for GPS sources that don't report accuracy
- May result in lower quality stay detection

---

## Favorite Areas: Special Handling

Favorite Areas receive special treatment during stay detection to handle large venues where you might walk around but still be "at" the location.

### The Problem

Consider a shopping mall:
- You enter and walk to different stores
- Movement between stores might exceed 50m
- Without special handling, this creates multiple separate stays

### The Solution

When movement exceeds the stay radius, the system checks if both points are within the same Favorite Area:

```
1. Calculate distance from centroid to new point
2. If distance > stay radius:
   a. Find Favorite Area containing the centroid
   b. Find Favorite Area containing the new point
   c. If same area: Continue stay (point added to cluster)
   d. If different areas: Transition to IN_TRIP
```

### Practical Examples

**University Campus (defined as Favorite Area):**
- You arrive at the library
- Walk to the cafeteria (500m away)
- Walk to your classroom (300m from cafeteria)
- **Result:** Single continuous stay at "University Campus"

**Two Shops in Same Mall (no Favorite Area defined):**
- You visit Store A
- Walk to Store B (100m away)
- **Result:** Two separate stays (Store A, Store B)

**Same Scenario with Mall as Favorite Area:**
- You visit Store A
- Walk to Store B
- **Result:** Single stay at "Shopping Mall"

:::tip Creating Favorite Areas
Define Favorite Areas for large venues you visit regularly (campuses, malls, office complexes) to get cleaner timeline entries instead of multiple fragmented stays.
:::

---

## Trip Detection and Stay Endings

### How Movement is Detected

A confirmed stay ends when the system detects you've moved away:

1. New GPS point is received
2. Distance to centroid is calculated
3. If distance > stay radius AND not in same Favorite Area:
   - Stay is finalized
   - Trip begins with the new point

### Returning to IN_TRIP Mode

When movement is detected:

```
Current State: CONFIRMED_STAY
Action: Receive point 120m from centroid (radius: 50m)
Result:
  1. Finalize stay (startTime, duration, location)
  2. Clear active cluster
  3. Start new trip with this point
  4. Transition to IN_TRIP
```

### Trip Ending Detection

For details on how trips end and new stays begin, see [Trip Detection](/docs/user-guide/timeline/trip_detection).

---

## Location Resolution

After a stay is detected, the system determines **where** you were:

### Resolution Priority

1. **Favorite Locations** - Check if centroid matches a user-defined favorite
2. **Geocoding Results** - Look up address/place name from coordinates

### Batch Processing

Location resolution is performed in batches after all stays are detected:
- Single database query for all stays
- Matches against favorites and geocoding cache
- Reduces processing time significantly

### What Gets Assigned

Each stay receives:
- **locationName** - Display name (e.g., "Home", "Work", or street address)
- **favoriteId** - Link to matching favorite location (if any)
- **geocodingId** - Link to geocoding result (if any)

---

## Configuration Recommendations

### For Different Use Cases

**Personal Location Tracking:**
```
Stay Detection Radius: 50m (default)
Minimum Stay Duration: 7 minutes (default)
GPS Accuracy Threshold: 60m (default)
```

**Delivery/Field Service:**
```
Stay Detection Radius: 30m (more precise)
Minimum Stay Duration: 2-3 minutes (catch quick stops)
GPS Accuracy Threshold: 30m (stricter quality)
```

**Road Trip Tracking:**
```
Stay Detection Radius: 75-100m (account for parking lot size)
Minimum Stay Duration: 10-15 minutes (filter rest stops)
GPS Accuracy Threshold: 60m (default)
```

**Urban High-Density:**
```
Stay Detection Radius: 30-40m (separate nearby venues)
Minimum Stay Duration: 5 minutes (default)
GPS Accuracy Threshold: 50m (stricter for cities)
```

### For Different GPS Quality

**High-Quality GPS (modern phone):**
```
Stay Detection Radius: 30-50m
GPS Accuracy Threshold: 30m
Minimum Accuracy Ratio: 70%
```

**Average GPS Quality:**
```
Stay Detection Radius: 50-75m
GPS Accuracy Threshold: 60m
Minimum Accuracy Ratio: 50%
```

**Poor GPS Quality (indoor, urban canyons):**
```
Stay Detection Radius: 100m
GPS Accuracy Threshold: 100m
Minimum Accuracy Ratio: 30%
```

---

## Troubleshooting

### Problem: GPS points at one location create multiple stays

**Possible Causes:**
- Stay radius too small for GPS accuracy
- GPS drift exceeding radius threshold

**Solutions:**
1. Increase **Stay Detection Radius** to 75-100m
2. Check GPS source accuracy settings
3. Consider defining a Favorite Area for the location

### Problem: Nearby distinct locations merge into one stay

**Possible Causes:**
- Stay radius too large
- Both locations fall within same radius

**Solutions:**
1. Decrease **Stay Detection Radius** to 30-40m
2. Ensure GPS accuracy is sufficient
3. Define separate Favorite Locations for each place

### Problem: Short stops aren't being detected

**Possible Causes:**
- Minimum stay duration too long
- Not enough GPS points during the stop

**Solutions:**
1. Decrease **Minimum Stay Duration** to 3-5 minutes
2. Check GPS tracking frequency (should be every 30-60 seconds)
3. Verify GPS accuracy isn't filtering out points

### Problem: Traffic delays appear as stays

**Possible Causes:**
- Minimum stay duration too short
- Extended traffic stopped in same location

**Solutions:**
1. Increase **Minimum Stay Duration** to 10-15 minutes
2. Use default 7-minute threshold for most cases

### Problem: Stays have wrong or missing location names

**Possible Causes:**
- No Favorite Location defined for the place
- Geocoding hasn't processed the coordinates
- Stay centroid is slightly off from actual location

**Solutions:**
1. Create a Favorite Location for frequently visited places
2. Wait for reverse geocoding to complete
3. Check if stay radius needs adjustment

---

## Impact on Timeline Rebuild

Changing stay detection settings triggers a **full timeline rebuild**:

**What Changes:**
- All stays are re-detected with new parameters
- Stay boundaries and durations may change
- Some stays may split or merge

**What Doesn't Change:**
- Original GPS data remains intact
- Favorite locations are preserved

:::info Rebuild Time
After changing stay detection settings, the timeline will rebuild from your earliest GPS data. For large datasets, this may take several minutes. You'll see a progress indicator showing the rebuild status.
:::

---

## Related Settings

Stay detection works together with other timeline features:

- **[Trip Detection](/docs/user-guide/timeline/trip_detection)** - How movement between stays is detected
- **[Data Gaps](/docs/user-guide/timeline/data_gaps)** - How missing GPS data is handled
- **[Travel Classification](/docs/user-guide/timeline/travel_classification)** - How trip types are determined

---

## Summary

Stay detection identifies when you've stopped at a location using GPS clustering:

- **Clustering algorithm** groups nearby GPS points
- **Distance threshold** (Stay Detection Radius) defines cluster size
- **Duration threshold** (Minimum Stay Duration) confirms valid stays
- **Quality filtering** ensures reliable GPS data
- **Favorite Areas** handle large venues specially

By understanding these settings and adjusting them for your GPS quality and use case, you can ensure GeoPulse accurately captures all your meaningful stays while filtering out noise and false positives.
