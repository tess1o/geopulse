---
title: Data Gaps & Inference Features
description: How GeoPulse handles missing GPS data and intelligently infers stays or trips during gaps.
---

# Data Gaps & Inference Features

GeoPulse tracks your location continuously, but sometimes GPS data stops being collected. This document explains how data gaps are detected, recorded, and how **Gap Stay Inference** and **Gap Trip Inference** features can intelligently determine what happened during periods of missing data.

---

## Overview

A **Data Gap** occurs when there's a significant time jump between consecutive GPS points, indicating missing location data. This can happen when:

- Your phone is turned off or in airplane mode
- The GPS tracking app is closed or stopped
- You're in an area with no cell/GPS signal
- Battery saver mode restricts background location
- You're indoors where GPS doesn't work well

By default, GeoPulse creates a "Data Gap" event to represent that your location was not tracked during that period. However, two intelligent inference features can help:

- **Gap Stay Inference**: Determines if you stayed at the same location during the gap (e.g., overnight at home)
- **Gap Trip Inference**: Detects long-distance movements during gaps (e.g., international flights) and creates inferred trips

---

## How Data Gaps Are Detected

### Detection Algorithm

For each GPS point received, the system checks the time since the previous point:

```
Time Delta = Current point timestamp - Previous point timestamp

If Time Delta > Data Gap Threshold:
  AND Duration >= Minimum Gap Duration:
    → Create Data Gap event
    → Reset timeline state
```

### Example Timeline

```
14:00 - GPS point at Home
14:05 - GPS point at Home (5 min gap - normal)
14:10 - GPS point at Home (5 min gap - normal)
------ Phone turned off ------
18:00 - GPS point at Coffee Shop (3h50m gap)

Result:
  - Stay: Home (14:00-14:10)
  - Data Gap: (14:10-18:00)
  - Stay: Coffee Shop (18:00-...)
```

### Why Gaps Matter

Without gap detection, the system would:
- Draw a straight line from Home to Coffee Shop
- Create a 3h50m "trip" that didn't happen
- Misrepresent your actual activity

Data gaps preserve timeline integrity by honestly representing unknown periods.

---

## Key Settings Explained

### Data Gap Threshold

**Setting:** `Data Gap Threshold`
**Default:** 3 hours (10,800 seconds)
**Range:** 5 minutes - 24 hours

Time between GPS points that triggers gap detection.

| Value | Effect | Best For |
|-------|--------|----------|
| **5-30 min** | Very sensitive | High-frequency tracking, research |
| **1-3 hours** | Balanced | Most users |
| **6-12 hours** | Conservative | Irregular GPS sources |
| **24 hours** | Very conservative | Daily check-in style tracking |

**Trade-offs:**
- **Shorter threshold:** Catches more gaps but may fragment timeline
- **Longer threshold:** More continuous timeline but may miss real gaps

:::warning Setting Too Low
A threshold under 30 minutes may create many small gaps during normal use, especially if your GPS app doesn't track continuously. The 3-hour default works well for most scenarios.
:::

### Minimum Gap Duration

**Setting:** `Minimum Gap Duration`
**Default:** 30 minutes (1,800 seconds)
**Range:** 5 minutes - 4 hours

Minimum duration for a gap to be recorded.

| Value | Effect | Best For |
|-------|--------|----------|
| **5 min** | Record all gaps | Detailed tracking analysis |
| **30 min** | Balanced | Most users |
| **1-2 hours** | Only significant gaps | Clean timeline view |
| **4 hours** | Only major gaps | Minimal gap entries |

**Trade-offs:**
- **Shorter minimum:** More complete record but cluttered timeline
- **Longer minimum:** Cleaner timeline but small gaps are ignored

This setting filters out insignificant gaps (like brief connectivity issues) while still recording meaningful periods of missing data.

---

## Gap Stay Inference

**Gap Stay Inference** is a feature that intelligently determines whether you stayed at a location during a data gap. Instead of creating a gap, the system infers that you remained at the previous location.

### The Problem

Consider this common scenario:

```
20:00 - GPS point at Home
20:05 - GPS point at Home
------ Phone overnight (not tracking) ------
08:00 - GPS point at Home

Without inference:
  - Stay: Home (20:00-20:05)
  - Data Gap: (20:05-08:00) - 12 hours unknown
  - Stay: Home (08:00-...)

With inference:
  - Stay: Home (20:00-08:00) - 12 hour stay
```

The user was clearly at home overnight, but without inference, this appears as a 12-hour data gap followed by a new stay at the same location.

### How It Works

When a data gap is detected, the system checks if stay inference should apply:

```
1. Is Gap Stay Inference enabled? (default: disabled)
2. Was user stationary before the gap? (POTENTIAL_STAY or CONFIRMED_STAY)
3. Is gap duration ≤ Maximum Gap Duration for Inference?
4. Is the new point within stay radius of the previous location?

If ALL conditions are true:
  → Skip gap creation
  → Add new point to existing stay cluster
  → Stay continues across the gap

If ANY condition is false:
  → Create normal Data Gap
  → Reset timeline state
```

### Key Conditions

**Must be stationary before gap:**
Inference only applies when you were at a location (stay). If you were traveling (IN_TRIP mode), the gap is created normally because we don't know where you ended up.

**Must return to same location:**
The new GPS point must be within the stay radius of where you were. If you're at a different location after the gap, a normal gap is created.

**Gap must be reasonable duration:**
Gaps longer than the maximum (default 24 hours) create normal gaps. A week-long gap shouldn't be inferred as continuous stay.

---

## Gap Stay Inference Settings

### Enable Gap Stay Inference

**Setting:** `Gap Stay Inference`
**Default:** Disabled

Toggle to enable/disable the inference feature.

**When to enable:**
- You frequently stay home overnight without GPS tracking
- Your GPS app only runs during certain hours
- You want continuous stays across predictable gaps

**When to keep disabled:**
- You want explicit record of tracking gaps
- You travel frequently and gaps might span different locations
- You prefer conservative timeline (only what's directly observed)

### Maximum Gap Duration for Inference

**Setting:** `Maximum Gap Duration for Inference`
**Default:** 24 hours
**Range:** 1-168 hours (1 week)

Maximum gap duration that can be inferred as a stay.

| Value | Effect | Best For |
|-------|--------|----------|
| **1-6 hours** | Only short gaps | Conservative inference |
| **12-24 hours** | Overnight gaps | Most users |
| **48-72 hours** | Weekend gaps | Infrequent tracking |
| **168 hours** | Week-long gaps | Very infrequent tracking |

**Trade-offs:**
- **Shorter duration:** More conservative, less risk of wrong inference
- **Longer duration:** Handles longer gaps but higher risk of incorrect inference

:::tip Recommended Setting
For most users, **24 hours** works well. This covers overnight gaps at home without inferring multi-day gaps that are more likely to involve actual movement.
:::

---

## Gap Trip Inference

**Gap Trip Inference** is a feature that detects long-distance movements during data gaps and creates inferred trips instead of data gaps. This is particularly useful for capturing flights, long-distance trains, or drives where GPS tracking was off.

### The Problem

Consider this common scenario:

```
20:00 Dec 20 - Last GPS point at Home (Country A)
------ International flight overnight ------
05:25 Dec 21 - First GPS point near Airport (Country B)

Distance: 1,779 km
Duration: 9+ hours

Without inference:
  - Stay: Home (20:00)
  - Data Gap: (20:00 Dec 20 - 05:25 Dec 21) - 9+ hours unknown
  - Trip/Stay: Country B (05:25-...)

With inference:
  - Stay: Home (ends at 20:00)
  - Inferred Trip: FLIGHT (20:00 Dec 20 - 05:25 Dec 21, 1,779 km)
  - Trip/Stay: Country B (05:25-...)
```

The system detects that the large distance (1,779 km) between the last and first GPS points indicates travel occurred, and creates a trip instead of a meaningless data gap.

### How It Works

When a data gap is detected, the system checks if trip inference should apply:

```
1. Is Gap Trip Inference enabled? (default: disabled)
2. Is gap duration within configured range? (min and max hours)
3. Is the distance between points ≥ Minimum Distance threshold?

If ALL conditions are true:
  → Create inferred trip (with distance-based classification)
  → Trip mode determined by existing algorithm (FLIGHT, TRAIN, CAR, etc.)

If ANY condition is false:
  → Fall through to normal gap or stay inference
```

### Key Features

**Distance-Based Classification:**
When GPS statistics aren't available (only 2 points), the system uses intelligent distance + duration heuristics:

- **>1000 km**: Almost certainly a flight
- **>300 km + >100 km/h avg**: Clear flight signature
- **>500 km + >80 km/h avg**: Likely flight with ground time
- **100-800 km + 50-150 km/h**: Possibly train travel

**Automatic Trip Classification:**
The inferred trip is classified using the same algorithm as normal trips:
- International flight (1,779 km in 9h) → **FLIGHT**
- Intercity train (250 km in 3h) → **TRAIN**
- Long drive (400 km in 5h) → **CAR**

**Priority System:**
Trip inference runs after stay inference, so:
1. Same location → Stay inference (if enabled)
2. Long distance → Trip inference (if enabled)
3. Neither applies → Normal data gap

---

## Gap Trip Inference Settings

### Enable Gap Trip Inference

**Setting:** `Gap Trip Inference`
**Default:** Disabled

Toggle to enable/disable the feature.

**When to enable:**
- You frequently travel long distances (flights, trains)
- Your phone is often off during travel
- You want trips instead of gaps for long-distance movement

**When to keep disabled:**
- You want explicit record of all tracking gaps
- You rarely travel long distances
- You prefer conservative timeline (only directly observed trips)

### Minimum Distance for Trip Inference

**Setting:** `Minimum Distance for Trip Inference`
**Default:** 100 km (100,000 meters)
**Range:** 1 km - 1,000 km

Minimum distance between GPS points to infer a trip.

| Value | Effect | Best For |
|-------|--------|----------|
| **10-50 km** | Very sensitive | Detect short intercity trips |
| **100 km** | Balanced | Most users (default) |
| **200-300 km** | Conservative | Only long-distance travel |
| **500+ km** | Very conservative | Primarily flights |

**Trade-offs:**
- **Lower threshold:** Catches more trips but may create false positives
- **Higher threshold:** More conservative but misses shorter long-distance trips

### Minimum Gap Duration for Trip Inference

**Setting:** `Minimum Gap Duration for Trip Inference`
**Default:** 1 hour
**Range:** 0 - 24 hours

Minimum gap duration to consider for trip inference.

| Value | Effect | Best For |
|-------|--------|----------|
| **0 hours** | Any gap | Capture all long-distance gaps |
| **1 hour** | Short gaps excluded | Most users (default) |
| **3-6 hours** | Only longer gaps | Conservative inference |

**Trade-offs:**
- **Shorter minimum:** More trips detected, but may catch brief connectivity issues
- **Longer minimum:** More conservative, only significant gaps

### Maximum Gap Duration for Trip Inference

**Setting:** `Maximum Gap Duration for Trip Inference`
**Default:** 24 hours
**Range:** 1 - 336 hours (2 weeks)

Maximum gap duration for trip inference.

| Value | Effect | Best For |
|-------|--------|----------|
| **6-12 hours** | Short trips only | Conservative inference |
| **24 hours** | Day trips | Most users (default) |
| **48-72 hours** | Multi-day trips | Extended travel |
| **168+ hours** | Week-long trips | Very long travel periods |

**Trade-offs:**
- **Shorter maximum:** More conservative, less risk of wrong inference
- **Longer maximum:** Handles extended travel but higher risk of incorrect inference

:::warning Multi-Day Gaps
Gaps longer than a few days are risky to infer as trips. The user might have been stationary at an unmapped location, or the gap might span multiple trips. Use conservative maximum values.
:::

---

## Practical Examples

### Example A: International Flight

```
Configuration:
  Gap Trip Inference: Enabled
  Min Distance: 100 km
  Min Gap Hours: 1
  Max Gap Hours: 24

Timeline:
  18:18 Dec 20 - At Home (Country A)
  ------ Phone off for flight ------
  05:25 Dec 21 - At destination (Country B)

Distance: 1,779 km > 100 km ✓
Duration: 11 hours (1 ≤ 11 ≤ 24) ✓

Result: Inferred trip created
  - Stay: Home (ends at 18:18)
  - Trip: FLIGHT (18:18 - 05:25, 1,779 km)
  - Automatic classification detects: 1,779km > 1000km → FLIGHT
```

### Example B: Short Local Gap

```
Configuration:
  Gap Trip Inference: Enabled
  Min Distance: 100 km
  Min Gap Hours: 1
  Max Gap Hours: 24

Timeline:
  14:00 - At Home
  ------ 2 hours without tracking ------
  16:00 - At Office (15 km away)

Distance: 15 km < 100 km ✗

Result: Normal data gap
  - Stay: Home (14:00)
  - Data Gap: (14:00-16:00)
  - Stay: Office (16:00-...)

Trip inference doesn't apply because distance is below threshold.
```

### Example C: Intercity Train

```
Configuration:
  Gap Trip Inference: Enabled
  Min Distance: 100 km
  Min Gap Hours: 1
  Max Gap Hours: 24

Timeline:
  10:00 - At City A
  ------ Phone off during train ride ------
  13:30 - At City B

Distance: 280 km > 100 km ✓
Duration: 3.5 hours ✓

Result: Inferred trip created
  - Classification: 280km, 3.5h → 80 km/h avg → TRAIN
```

### Example D: Weekend Away (Too Long)

```
Configuration:
  Gap Trip Inference: Enabled
  Min Distance: 100 km
  Min Gap Hours: 1
  Max Gap Hours: 24

Timeline:
  Friday 18:00 - At Home
  ------ Weekend trip, 72 hours ------
  Monday 18:00 - At Beach House (200 km away)

Distance: 200 km > 100 km ✓
Duration: 72 hours > 24 hours ✗

Result: Normal data gap
  - Stay: Home (Friday 18:00)
  - Data Gap: (Friday 18:00 - Monday 18:00)
  - Stay: Beach House (Monday 18:00-...)

Gap exceeds maximum duration - too risky to infer.
```

---

## Practical Examples (Stay Inference)

### Example 1: Overnight at Home

```
Configuration:
  Gap Stay Inference: Enabled
  Max Gap Duration: 24 hours

Timeline:
  21:00 - At Home, watching TV
  21:30 - Last GPS point (app stopped for night)
  ------ 9 hours without tracking ------
  06:30 - First GPS point (morning)
  06:35 - At Home, getting ready

Distance check: 06:30 point is 10m from 21:30 centroid (within 50m radius)

Result: Single stay at Home (21:00-06:35)
  - 9.5 hour continuous stay
  - No data gap created
  - Accurately represents that you were home overnight
```

### Example 2: Different Location After Gap

```
Configuration:
  Gap Stay Inference: Enabled
  Max Gap Duration: 24 hours

Timeline:
  20:00 - At Home
  20:05 - Last GPS point at Home
  ------ Phone off overnight ------
  08:00 - First GPS point at Office

Distance check: 08:00 point is 5km from Home centroid (outside 50m radius)

Result:
  - Stay: Home (20:00-20:05)
  - Data Gap: (20:05-08:00)
  - Stay: Office (08:00-...)

Inference doesn't apply because you're at a different location.
```

### Example 3: Gap Too Long

```
Configuration:
  Gap Stay Inference: Enabled
  Max Gap Duration: 24 hours

Timeline:
  Friday 18:00 - At Home
  Friday 18:05 - Last GPS point
  ------ Weekend without tracking (48+ hours) ------
  Monday 08:00 - First GPS point at Home

Gap Duration: ~62 hours > 24 hour maximum

Result:
  - Stay: Home (Friday 18:00-18:05)
  - Data Gap: (Friday 18:05 - Monday 08:00)
  - Stay: Home (Monday 08:00-...)

Inference doesn't apply because gap exceeds maximum duration.
```

### Example 4: Gap During Trip

```
Configuration:
  Gap Stay Inference: Enabled
  Max Gap Duration: 24 hours

Timeline:
  10:00 - Left Home (trip started)
  10:15 - Driving on highway
  10:20 - Last GPS point (battery died)
  ------ 2 hours without tracking ------
  12:20 - First GPS point at Restaurant

State before gap: IN_TRIP (not stationary)

Result:
  - Trip: (10:00-10:20)
  - Data Gap: (10:20-12:20)
  - Stay: Restaurant (12:20-...)

Inference doesn't apply during trips because we don't know where you ended up.
```

---

## State Reset on Gaps

When a data gap is created (inference doesn't apply), the system performs a **complete state reset**:

1. **Finalize active event** - Complete any in-progress stay or trip
2. **Create gap event** - Record the data gap period
3. **Clear state** - Reset mode to UNKNOWN
4. **Start fresh** - Next point begins new detection

This ensures that gaps provide clean breaks in the timeline without carrying over potentially stale state.

---

## Ongoing Data Gaps

At the end of timeline generation, the system checks for an **ongoing gap** from the last GPS point to the current time:

```
If (Current time - Last GPS time) > Gap Threshold:
  AND Duration >= Minimum Gap Duration:
    → Create or extend ongoing Data Gap
```

This shows when tracking has stopped and the current location is unknown.

### Example

```
Timeline generated at 15:00:
  Last GPS point: 11:00 at Office

Time since last point: 4 hours > 3 hour threshold

Result:
  - Stay: Office (09:00-11:00)
  - Data Gap: (11:00-15:00) - Ongoing
```

The ongoing gap updates each time the timeline is regenerated until new GPS data arrives.

---

## Configuration Recommendations

### For Different Use Cases

**Continuous Personal Tracking:**
```
Data Gap Threshold: 3 hours (default)
Minimum Gap Duration: 30 minutes
Gap Stay Inference: Enabled
Max Gap Duration for Inference: 24 hours
Gap Trip Inference: Enabled
Min Distance for Trip Inference: 100 km
Min/Max Gap Hours for Trip: 1-24 hours
```

**Business/Fleet Tracking:**
```
Data Gap Threshold: 1 hour
Minimum Gap Duration: 15 minutes
Gap Stay Inference: Disabled (explicit tracking required)
Gap Trip Inference: Disabled (explicit tracking required)
```

**Casual Location Diary:**
```
Data Gap Threshold: 6 hours
Minimum Gap Duration: 1 hour
Gap Stay Inference: Enabled
Max Gap Duration for Inference: 48 hours
Gap Trip Inference: Enabled
Min Distance for Trip Inference: 100 km
Min/Max Gap Hours for Trip: 1-48 hours
```

**Research/Analysis:**
```
Data Gap Threshold: 30 minutes
Minimum Gap Duration: 5 minutes
Gap Stay Inference: Disabled (preserve all gaps for analysis)
Gap Trip Inference: Disabled (preserve all gaps for analysis)
```

### For Different GPS Sources

**Always-on tracking app:**
```
Data Gap Threshold: 1-2 hours
Minimum Gap Duration: 30 minutes
Gap Stay Inference: Enabled
```

**Battery-conscious tracking:**
```
Data Gap Threshold: 6 hours
Minimum Gap Duration: 1 hour
Gap Stay Inference: Enabled
Max Gap Duration for Inference: 24 hours
```

**Manual check-in style:**
```
Data Gap Threshold: 24 hours
Minimum Gap Duration: 4 hours
Gap Stay Inference: Enabled
Max Gap Duration for Inference: 72 hours
```

---

## Troubleshooting

### Problem: Too many data gaps appearing

**Possible Causes:**
- Gap threshold too low
- GPS app not tracking continuously
- Frequent connectivity issues

**Solutions:**
1. Increase **Data Gap Threshold** to 6 hours
2. Increase **Minimum Gap Duration** to 1 hour
3. Check GPS app settings for continuous tracking
4. Enable **Gap Stay Inference** if gaps occur at known locations

### Problem: Overnight at home shows as data gap

**Possible Causes:**
- Gap Stay Inference is disabled
- Max gap duration is too short
- Morning GPS point is outside stay radius

**Solutions:**
1. Enable **Gap Stay Inference**
2. Increase **Max Gap Duration for Inference** to 24 hours
3. Check that your GPS accuracy is good enough (point should be within 50m)

### Problem: Wrong location inferred during gap

**Possible Causes:**
- GPS drift caused points to appear at same location
- Max gap duration too long
- Actually did return to same location by coincidence

**Solutions:**
1. Decrease **Max Gap Duration for Inference** to 12 hours
2. Disable inference if it's causing incorrect timelines
3. Inference is conservative - it only applies when locations clearly match

### Problem: Gaps during trips create confusing timeline

**Possible Causes:**
- Normal behavior - gaps during trips should create gaps
- Inference correctly doesn't apply during movement

**Solutions:**
1. This is expected behavior
2. Gaps during trips protect against false route assumptions
3. Consider if your GPS tracking should be more continuous during trips

### Problem: Gap between two different locations not detected

**Possible Causes:**
- Gap duration below threshold
- Minimum gap duration not met

**Solutions:**
1. Decrease **Data Gap Threshold** if gaps should be shorter
2. Decrease **Minimum Gap Duration**
3. Check actual time between points vs. threshold

---

## Impact on Timeline Rebuild

Changing data gap settings triggers a **full timeline rebuild**:

**What Changes:**
- All gaps are re-evaluated with new thresholds
- Inference is applied or removed based on new settings
- Stay durations may change when inference is toggled

**What Doesn't Change:**
- Original GPS data remains intact
- Non-gap timeline events maintain their detection parameters

:::info Enabling Inference
When you enable Gap Stay Inference, stays that previously ended at gaps will now extend across those gaps (where conditions are met). This can significantly change stay durations in your historical timeline.
:::

---

## Related Settings

Data gap detection works together with other timeline features:

- **[Stay Detection](/docs/user-guide/timeline/stay_detection)** - Gaps reset stay detection state
- **[Trip Detection](/docs/user-guide/timeline/trip_detection)** - Gaps reset trip tracking
- **[Timeline Preferences](/docs/user-guide/core-features/timeline#fine-tuning-your-timeline-key-settings-explained)** - Overall timeline configuration

---

## Summary

Data gaps and gap stay inference handle missing GPS data intelligently:

- **Data gaps** honestly represent periods of unknown location
- **Gap threshold** controls when gaps are detected (default 3 hours)
- **Minimum duration** filters out insignificant gaps (default 30 minutes)
- **Gap stay inference** infers stays when returning to same location
- **Inference conditions** ensure conservative, accurate inference

By enabling gap stay inference and configuring appropriate thresholds, you can get more accurate continuous stays (like overnight at home) while still preserving the integrity of your timeline when GPS tracking truly stops.

:::tip Best Practice
Enable Gap Stay Inference with a 24-hour maximum for most personal tracking use cases. This captures common overnight gaps at home while remaining conservative enough to avoid incorrect inferences for longer gaps.
:::
