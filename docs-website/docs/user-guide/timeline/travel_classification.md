---
title: Travel Classification
description: How GeoPulse automatically identifies your travel mode (walking, car, bicycle, train, flight) based on GPS data.
---

# Travel Classification

GeoPulse automatically analyzes your trips to determine how you traveled  whether you walked, drove, cycled, took a train, or flew. This classification uses GPS speed data and advanced algorithms to accurately identify your mode of transport.

---

## Overview

When GeoPulse detects that you're moving between two locations (a trip), it analyzes the GPS data to classify the trip type. This classification is based primarily on:

- **Average Speed**  -  Your typical speed throughout the trip
- **Maximum Speed**  -  The highest speed reached during the trip
- **Speed Variance**  -  How consistent your speed was (important for distinguishing trains from cars)

The system then compares these metrics against configurable thresholds to determine which mode of transport best matches your trip characteristics.

---

## Supported Trip Types

GeoPulse supports both **mandatory** and **optional** trip types:

### Mandatory Types

These types are always enabled and cannot be disabled:

- **WALK**  -  Low-speed movement (walking, slow jogging)
- **CAR**  -  Motorized transport including cars, buses, motorcycles
- **UNKNOWN**  -  Trips that don't clearly match any category

### Optional Types

These types can be enabled or disabled in your Timeline Preferences:

- **BICYCLE**  -  Medium-speed cycling
- **RUNNING**  -  Medium-low speed running and jogging
- **TRAIN**  -  High-speed rail travel with consistent speeds
- **FLIGHT**  -  Air travel with very high speeds

:::tip
If you don't use certain travel modes, you can disable them in your Timeline Preferences to improve classification accuracy. For example, if you never cycle, disable **BICYCLE** to prevent car trips from being misclassified.
:::

---

## How Classification Works

### Classification Priority Order

The system evaluates trip types in a specific order to ensure accurate classification:

1. **FLIGHT**  -  Checked first (400+ km/h average OR 500+ km/h peak)
2. **TRAIN**  -  High speed with low variance (30-150 km/h, consistent speed)
3. **BICYCLE**  -  Medium speeds (8-25 km/h)  -  **Checked before RUNNING!**
4. **RUNNING**  -  Medium-low speeds (7-14 km/h)  -  **Must be before CAR!**
5. **CAR**  -  Motorized transport (10+ km/h average OR 15+ km/h peak)
6. **WALK**  -  Low speeds (≤6 km/h average, ≤8 km/h peak)
7. **UNKNOWN**  -  Fallback for edge cases

:::warning Important
**BICYCLE and RUNNING must be checked before CAR** because their speed ranges overlap. The order matters: BICYCLE (8-25 km/h) is checked first to catch faster speeds, then RUNNING (7-14 km/h), then CAR. If BICYCLE or RUNNING are disabled, those trips will be classified as the next matching type or CAR.
:::

### GPS Data Quality

GeoPulse includes sophisticated GPS noise detection to ensure accurate classification:

- **Supersonic Speed Detection**  -  Rejects impossible speeds above 1,200 km/h (GPS noise)
- **Reliability Validation**  -  Compares GPS speeds against calculated speeds from distance/duration
- **Adaptive Thresholds**  -  Uses different validation rules for low-speed vs. high-speed trips
- **Smart Fallbacks**  -  Automatically switches to calculated speeds when GPS data is unreliable

### Distance-Based Classification (Sparse GPS Data)

When GPS data is not available (e.g., inferred trips from data gaps, phone off during travel), GeoPulse uses intelligent distance-based heuristics instead of speed-based classification. This handles cases where only the start and end points are known, without intermediate GPS tracking.

**How It Works:**

For inferred trips, the system calculates average speed from distance and duration, then applies specialized rules that combine distance + speed patterns characteristic of each transport mode:

**FLIGHT Detection (Inferred Trips):**
- **Extreme distance** (>1000 km) + speed >350 km/h → FLIGHT (high confidence - distinguishes from high-speed rail)
- **Long distance** (>300 km) + speed >280 km/h → FLIGHT (medium-high confidence)
- **Short-haul** (350-600 km) + speed >110 km/h → FLIGHT (medium confidence - short domestic flights)
- **Very long distance** (>600 km) + speed >150 km/h → FLIGHT (medium confidence - flights with delays)

**TRAIN Detection (Inferred Trips):**
- **Distance** 100-1,500 km + **Speed** 50-200 km/h + **Duration** ≥1 hour → TRAIN
- Supports both regional trains (50-150 km/h) and high-speed rail (150-200 km/h)
- Extended range compared to GPS-based detection to handle modern high-speed rail (China HSR, Shinkansen, TGV)

:::tip Why Distance Matters
Speed alone can be misleading for sparse data. Example: An international flight with 2 hours of taxi/ground time shows ~150-200 km/h average speed, below the normal flight threshold (400 km/h). But 1,800 km distance over 11 hours is clearly a flight, not a car trip. The distance-based heuristics catch these cases.
:::

**Example Scenarios:**

| Distance | Duration | Avg Speed | Classification | Reason |
|----------|----------|-----------|----------------|--------|
| 1,800 km | 11 hours | 164 km/h | FLIGHT | Extreme distance with adequate speed |
| 450 km | 3 hours | 150 km/h | FLIGHT | Short-haul flight (domestic) |
| 700 km | 6.7 hours | 105 km/h | FLIGHT | Very long distance with modest speed |
| 1,318 km | 5 hours | 263 km/h | TRAIN | High-speed rail (Beijing-Shanghai HSR) |
| 250 km | 2.5 hours | 100 km/h | TRAIN | Regional/intercity train |

### Special Cases

The classification algorithm handles several special scenarios:

**Long Flights with Ground Time**
Uses OR logic for flight detection: if **either** average speed e400 km/h **or** peak speed e500 km/h, the trip is classified as FLIGHT. This handles flights with long taxi/boarding times that lower the average.

**Train vs. Car Distinction**
Trains typically maintain consistent speeds (low variance) while cars have variable speeds due to traffic, stops, and acceleration. A trip with 60 km/h average might be classified as TRAIN if speed variance is low, or CAR if variance is high.

**Bicycle vs. Running vs. Car Overlap**
Speeds between 7-25 km/h could be cycling, running, or slow driving. The system checks in priority order: BICYCLE first (8-25 km/h), then RUNNING (7-14 km/h), then CAR. If BICYCLE or RUNNING are disabled, trips in their ranges will be classified as the next matching type or CAR.

**Walking Verification**
After initial classification, the system double-checks WALK classifications. If the calculated speed exceeds the walking threshold by more than 20%, the trip is reclassified as CAR. This catches cases where GPS noise made a car trip appear slower than it was.

---

## Default Speed Thresholds

See [Timeline Preferences](/docs/system-administration/configuration/timeline-global-config) for default thresholds for each trip type.

---

## Customizing Classification Settings

You can fine-tune travel classification in **Timeline Preferences** to match your specific travel patterns:

### Accessing Settings

1. Navigate to **Menu** ->  **Timeline Preferences** or go to `/app/timeline/preferences`
2. Scroll to the **Trip Classification** section
3. Adjust the settings for each trip type

### Available Settings

#### Walk Settings

- **Walking Max Average Speed** (default: 6 km/h)
  Maximum average speed for walking trips. Increase if you walk very briskly.

- **Walking Max Maximum Speed** (default: 8 km/h)
  Maximum peak speed for walking trips. Accounts for brief faster walking.

#### Car Settings

- **Car Min Average Speed** (default: 10 km/h)
  Minimum average speed for car trips. Lower if you drive in very heavy traffic.

- **Car Min Maximum Speed** (default: 15 km/h)
  Minimum peak speed for car trips. Uses OR logic with average speed.

#### Bicycle Settings (Optional)

- **Enable Bicycle** (default: disabled)
  Toggle to enable/disable bicycle classification.

- **Bicycle Min Average Speed** (default: 8 km/h)
  Minimum average speed for cycling trips.

- **Bicycle Max Average Speed** (default: 25 km/h)
  Maximum average speed for cycling trips. Faster trips are classified as CAR.

- **Bicycle Max Maximum Speed** (default: 35 km/h)
  Maximum peak speed allowed for bicycle trips. Allows for downhill segments and e-bikes.

#### Running Settings (Optional)

- **Enable Running** (default: disabled)
  Toggle to enable/disable running classification. When disabled, running speeds are classified as BICYCLE (if enabled) or CAR.

- **Running Min Average Speed** (default: 7 km/h)
  Minimum average speed for running trips. Slower trips are classified as WALK.

- **Running Max Average Speed** (default: 14 km/h)
  Maximum average speed for running trips. Faster trips are classified as BICYCLE (if enabled) or CAR.

- **Running Max Maximum Speed** (default: 18 km/h)
  Maximum peak speed allowed for running trips. Allows for sprint segments.

#### Train Settings (Optional)

- **Enable Train** (default: disabled)
  Toggle to enable/disable train classification.

- **Train Min Average Speed** (default: 30 km/h)
  Minimum average speed for train trips.

- **Train Max Average Speed** (default: 150 km/h)
  Maximum average speed for train trips (excludes high-speed rail at 200+ km/h).

- **Train Min Maximum Speed** (default: 40 km/h)
  Minimum peak speed required (filters out station-only trips).

- **Train Max Maximum Speed** (default: 200 km/h)
  Maximum peak speed for train trips.

- **Train Max Speed Variance** (default: 15)
  Maximum allowed speed variance. Lower values mean more consistent speeds required.

#### Flight Settings (Optional)

- **Enable Flight** (default: disabled)
  Toggle to enable/disable flight classification.

- **Flight Min Average Speed** (default: 400 km/h)
  Minimum average speed for flight trips (includes ground time).

- **Flight Min Maximum Speed** (default: 500 km/h)
  Minimum peak speed for flight trips (cruising altitude).

---

## When to Customize Settings

### You Walk Very Fast or Very Slow

**Problem:** Your walking trips are classified as CAR, or your slow walks are marked as UNKNOWN.

**Solution:** Adjust the **Walking Max Average Speed** threshold up or down to match your typical walking pace.

### You Frequently Cycle

**Problem:** Your bicycle trips are classified as CAR.

**Solution:**
1. **Enable Bicycle** classification
2. Adjust **Bicycle Min/Max Average Speed** to match your cycling speeds
3. Consider your typical cycling speed range to set appropriate thresholds

### You Drive in Heavy Traffic

**Problem:** Your car trips in traffic are classified as WALK or BICYCLE.

**Solution:** Lower the **Car Min Average Speed** threshold (e.g., to 7-8 km/h) to account for stop-and-go traffic.

### You Take Commuter Trains

**Problem:** Your train trips are classified as CAR.

**Solution:**
1. **Enable Train** classification
2. Adjust **Train Min/Max Average Speed** to match your local train speeds
3. Consider the **Train Max Speed Variance** setting  commuter trains with frequent stops may need a higher variance threshold

### Classification is Inconsistent

**Problem:** Similar trips are classified differently.

**Solution:**
1. Check if the trips actually had different speed profiles (traffic, route differences)
2. Review GPS data quality  trips with poor GPS may be classified as UNKNOWN
3. Ensure your thresholds don't overlap confusingly (e.g., bicycle max too close to car min)

---

## Understanding Classification Results

### Viewing Trip Classifications

Trip classifications appear in several places throughout GeoPulse:

- **Timeline View**  -  Each trip shows an icon indicating its type (walking person, car, bicycle, train, airplane)
- **Journey Insights**  -  Breakdown of trips by type with statistics
- **Dashboard**  -  Summary of travel modes used over time periods

### Trip Type Icons

| Icon | Type | Description |
|------|------|-------------|
| =� | WALK | Walking or jogging |
| =� | CAR | Car, bus, motorcycle, or other motorized vehicle |
| =� | BICYCLE | Cycling or fast running |
| =� | TRAIN | Train or metro travel |
|  | FLIGHT | Air travel |
| S | UNKNOWN | Unable to classify with confidence |

### Why a Trip Might Be UNKNOWN

A trip may be classified as UNKNOWN for several reasons:

1. **GPS Noise**  -  Impossible speeds detected (>1,200 km/h), likely due to GPS errors
2. **Speed in Gray Area**  -  Speed falls between classification thresholds
   - Example: 7 km/h (above WALK max of 6, below CAR min of 10, BICYCLE disabled)
3. **Missing GPS Data**  -  Insufficient speed information to make a determination
4. **Disabled Optional Types**  -  Trip matches an optional type that's currently disabled

:::tip
If you see many UNKNOWN classifications, try enabling more optional trip types (BICYCLE, TRAIN, FLIGHT) or adjusting your speed thresholds to reduce gaps between categories.
:::

---

## Technical Details

### GPS Data Processing

GeoPulse calculates speed statistics during trip processing:

1. **Average GPS Speed** Mean of all GPS point speeds during the trip
2. **Maximum GPS Speed** Highest speed recorded at any GPS point
3. **Speed Variance**  Statistical variance of speeds (consistency measure)
4. **Calculated Speed**  Distance divided by duration (backup/validation)

The system uses GPS-reported speeds when available and reliable, falling back to calculated speeds when GPS data is questionable.

### Noise Detection Algorithm

The classification system includes multiple layers of GPS noise detection:

**Layer 1: Supersonic Detection**
Rejects speeds above 1,200 km/h as physically impossible (commercial jets max ~900 km/h).

**Layer 2: Cross-Validation**
Compares GPS speeds against calculated speeds:
- Low-speed trips (< 20 km/h): GPS must be within 2x of calculated
- Medium-speed trips: GPS must be within 50% of calculated
- High-speed trips (>200 km/h): GPS is trusted over calculated (flight/train routes aren't straight lines)

**Layer 3: Spike Detection**
If GPS max speed exceeds calculated average by more than 5x, it's considered a noise spike and replaced with an estimated maximum (calculated � 1.5).

### Classification Verification

After initial classification, the system performs final verification:

**Walk Verification**
If a trip is classified as WALK but the calculated speed exceeds the walking threshold by more than 20%, it's reclassified as CAR. This catches GPS noise that made a car trip appear slower.

Example: Trip initially classified as WALK with GPS showing 5 km/h, but calculated speed is 12 km/h � reclassified as CAR.

---

## Best Practices

### For Accurate Classification

1. **Enable relevant optional types**  -  If you cycle, take trains, or fly, enable those classifications
2. **Calibrate to your patterns**  -  Adjust speed thresholds based on your actual travel speeds
3. **Consider your location**  -  Urban heavy traffic may need lower CAR min speeds
4. **Monitor UNKNOWN trips**  -  If you see many UNKNOWN classifications, review and adjust thresholds

### For Specific Use Cases

**Urban Commuter**
- Enable BICYCLE and TRAIN if you use them
- Lower CAR min average speed to 7-8 km/h for heavy traffic
- Consider higher BICYCLE max speeds if you use e-bikes

**Frequent Traveler**
- Enable FLIGHT classification
- Keep default thresholds as they handle airport ground time well

**Active Lifestyle**
- Enable BICYCLE for cycling and running
- Adjust BICYCLE min speed if you do slow recreational cycling
- Consider walking thresholds if you hike at varied speeds

**Data Analyst**
- Use consistent thresholds across time periods for meaningful comparisons
- Document any threshold changes when analyzing historical trends
- Consider disabling unused types to reduce classification ambiguity

---

## Troubleshooting

### Problem: Most trips are classified as UNKNOWN

**Possible Causes:**
- Optional trip types are disabled, creating gaps in speed coverage
- GPS data quality is poor with many noise spikes
- Thresholds have gaps between categories

**Solutions:**
1. Enable optional trip types (BICYCLE, TRAIN, FLIGHT) to cover more speed ranges
2. Check GPS source quality and accuracy settings
3. Review and adjust thresholds to eliminate gaps

### Problem: Bicycle trips classified as CAR

**Possible Causes:**
- BICYCLE classification is disabled
- BICYCLE max speed threshold is too low for your cycling speeds

**Solutions:**
1. Enable BICYCLE classification in Timeline Preferences
2. Increase BICYCLE max average/maximum speeds if you cycle faster
3. Check that CAR min speeds aren't too low (should be at least 10 km/h)

### Problem: Car trips in traffic classified as WALK

**Possible Causes:**
- Car min average speed threshold is too high for stop-and-go traffic
- GPS noise is making car speeds appear lower than reality

**Solutions:**
1. Lower CAR min average speed to 7-8 km/h for urban traffic
2. Ensure car trips meet the CAR min maximum speed threshold (15 km/h)
3. Check GPS data quality for the affected trips

### Problem: Train trips classified as CAR

**Possible Causes:**
- TRAIN classification is disabled
- Train speed variance threshold is too strict
- Commuter trains have frequent stops increasing variance

**Solutions:**
1. Enable TRAIN classification in Timeline Preferences
2. Increase TRAIN max speed variance (try 20-25 for commuter trains)
3. Adjust TRAIN min/max average speeds to match your local trains

### Problem: Short car trips classified as WALK

**Possible Causes:**
- Trip was genuinely slow (parking lot, driveway)
- GPS didn't capture higher speeds
- Trip duration was very short

**Solutions:**
1. Ensure GPS tracking frequency is adequate (at least every 30 seconds)
2. Consider that very short trips (< 100m) may legitimately be WALK
3. Check CAR min maximum speed threshold (15 km/h should catch most car movement)

---

## Impact on Timeline Rebuild

Changing travel classification settings triggers a full timeline rebuild to reclassify all existing trips with the new thresholds.

**What Gets Reclassified:**
- All past trips are re-evaluated using the new speed thresholds
- Trip icons and type labels are updated throughout the app
- Journey Insights and statistics are recalculated

**What Doesn't Change:**
- Stay locations remain the same
- Trip start/end times and locations are unchanged
- GPS data and speed calculations are not modified

:::info
After changing classification settings, you'll see a progress modal showing the timeline rebuild status. This process may take a few minutes for users with extensive travel history.
:::

---

## Related Settings

Travel classification works together with other timeline features:

- **[Timeline Generation](/docs/user-guide/core-features/timeline)**  How trips and stays are detected
- **[Measurement Units](/docs/user-guide/personalization/measurement-units)**  Display speeds in km/h or mph

---

## Summary

Travel classification automatically identifies how you traveled based on GPS speed data:

 **Five trip types**: WALK, CAR (mandatory) + BICYCLE, TRAIN, FLIGHT (optional)
 **Smart algorithms**: GPS noise detection, reliability validation, special case handling
 **Fully customizable**: Adjust speed thresholds to match your travel patterns
 **Automatic updates**: Enable/disable trip types and see classifications update instantly

By understanding how classification works and customizing the settings to match your lifestyle, you can ensure GeoPulse accurately captures your travel modes and provides meaningful insights into your journeys.
