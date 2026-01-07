# Timeline Global Configuration

## Real-time Timeline Processing

:::danger Global Settings
The following settings are for system administrators and control the global behavior of the real-time timeline
processing job. They affect all users and **cannot** be overridden on a per-user basis.
:::

Controls the background job that processes real-time timeline updates for all users.

| Property                               | Default | Description                                                                                                  |
|----------------------------------------|---------|--------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_PROCESSING_THREADS` | `2`     | The number of concurrent threads used to process timeline updates for different users.                       |
| `GEOPULSE_TIMELINE_JOB_INTERVAL`       | `5m`    | The interval at which the system checks for and processes pending real-time timeline updates for users.      |
| `GEOPULSE_TIMELINE_JOB_DELAY`          | `1m`    | The delay after application startup before the first timeline processing job is executed.                    |
| `GEOPULSE_TIMELINE_VIEW_ITEM_LIMIT`    | `150`   | Show a warning in UI if total number of timeline items (stays + trips + data gaps) is larger than this value |

To understand better how Timeline Processing works, please refer to
the [Timeline Processing](/docs/user-guide/core-features/timeline) section.

:::info
These settings can be overridden per-user through the web interface. Environment variables set the default values for
new users.
:::

## Staypoint Detection

Controls how GeoPulse identifies when you're stationary at a location.

| Property                                            | Default | Description                                                             |
|-----------------------------------------------------|---------|-------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_STAYPOINT_RADIUS_METERS`         | `50`    | Radius (in meters) used for stay point detection and clustering         |
| `GEOPULSE_TIMELINE_STAYPOINT_MIN_DURATION_MINUTES`  | `7`     | Minimum duration (in minutes) for a stay point to be confirmed          |
| `GEOPULSE_TIMELINE_STAYPOINT_USE_VELOCITY_ACCURACY` | `true`  | Whether to consider velocity accuracy in filtering                      |
| `GEOPULSE_TIMELINE_STAYPOINT_VELOCITY_THRESHOLD`    | `2.5`   | Velocity threshold (m/s) below which movement is considered a staypoint |
| `GEOPULSE_TIMELINE_STAYPOINT_ACCURACY_THRESHOLD`    | `60.0`  | Maximum GPS accuracy (meters) for staypoint detection                   |
| `GEOPULSE_TIMELINE_STAYPOINT_MIN_ACCURACY_RATIO`    | `0.5`   | Minimum accuracy ratio required for staypoint consideration             |

## Trip Classification

Controls how movement is classified into different transportation modes based on speed analysis.

### Trip Detection Settings

| Property                                                     | Default  | Description                                                                                                                 |
|--------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_TRIP_DETECTION_ALGORITHM`                 | `single` | Algorithm used for trip detection. Possible values: `single` and `multiple`                                                 |
| `GEOPULSE_TIMELINE_TRIP_ARRIVAL_MIN_DURATION_SECONDS`        | `90`     | Minimum duration (seconds) for arrival detection. GPS points must be clustered and slow for this duration to detect arrival |
| `GEOPULSE_TIMELINE_TRIP_SUSTAINED_STOP_MIN_DURATION_SECONDS` | `60`     | Minimum duration (seconds) for sustained stop detection. Filters out brief stops like traffic lights                        |
| `GEOPULSE_TIMELINE_SHORT_DISTANCE_KM`                        | `1.0`    | Distance threshold (km) for applying relaxed walking speed detection to account for GPS inaccuracies in short trips         |

### Walking Classification (Mandatory)

Walking is a mandatory trip type that cannot be disabled.

| Property                              | Default | Description                                                                                               |
|---------------------------------------|---------|-----------------------------------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_WALKING_MAX_AVG_SPEED` | `6.0`   | Maximum sustained speed (km/h) for walking classification. Trips above this are classified as non-walking |
| `GEOPULSE_TIMELINE_WALKING_MAX_MAX_SPEED` | `8.0`   | Maximum instantaneous speed (km/h) for walking trips. Brief bursts above this reclassify the entire trip  |

### Car Classification (Mandatory)

Car is a mandatory trip type that cannot be disabled. Covers all motorized transport including cars, buses, and motorcycles.

| Property                          | Default | Description                                                                                             |
|-----------------------------------|---------|---------------------------------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_CAR_MIN_AVG_SPEED` | `8.0`   | Minimum sustained speed (km/h) required for car classification. Trips below this won't be classified as driving |
| `GEOPULSE_TIMELINE_CAR_MIN_MAX_SPEED` | `15.0`  | Minimum peak speed (km/h) required for car classification. Trips never reaching this speed won't be classified as driving |

:::info
Car classification uses **OR logic**: a trip is classified as CAR if **either** average speed ≥ `CAR_MIN_AVG_SPEED` **or** maximum speed ≥ `CAR_MIN_MAX_SPEED`.
:::

### Bicycle Classification (Optional)

Bicycle is an optional trip type for detecting cycling. **Disabled by default.**

:::warning Classification Priority
Bicycle must be checked **before** RUNNING and CAR in the classification algorithm because their speed ranges overlap. If disabled, trips in the cycling range will be classified as RUNNING (if enabled) or CAR.
:::

| Property                              | Default | Description                                                                       |
|---------------------------------------|---------|-----------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_BICYCLE_ENABLED`       | `false` | Whether bicycle classification is enabled                                         |
| `GEOPULSE_TIMELINE_BICYCLE_MIN_AVG_SPEED` | `8.0`   | Minimum average speed (km/h) for bicycle trips. Slower trips classify as running or walking  |
| `GEOPULSE_TIMELINE_BICYCLE_MAX_AVG_SPEED` | `25.0`  | Maximum average speed (km/h) for bicycle trips. Faster trips classify as car      |
| `GEOPULSE_TIMELINE_BICYCLE_MAX_MAX_SPEED` | `35.0`  | Maximum peak speed (km/h) for bicycle trips. Allows for downhill segments/e-bikes |

### Running Classification (Optional)

Running is an optional trip type for detecting running and jogging. **Disabled by default.**

:::warning Classification Priority
Running must be checked **before** CAR in the classification algorithm because their speed ranges overlap (7-14 km/h). When disabled, running speeds are classified as BICYCLE (if enabled) or CAR.
:::

| Property                              | Default | Description                                                                       |
|---------------------------------------|---------|-----------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_RUNNING_ENABLED`       | `false` | Whether running classification is enabled                                         |
| `GEOPULSE_TIMELINE_RUNNING_MIN_AVG_SPEED` | `7.0`   | Minimum average speed (km/h) for running trips. Slower trips classify as walking  |
| `GEOPULSE_TIMELINE_RUNNING_MAX_AVG_SPEED` | `14.0`  | Maximum average speed (km/h) for running trips. Faster trips classify as bicycle or car |
| `GEOPULSE_TIMELINE_RUNNING_MAX_MAX_SPEED` | `18.0`  | Maximum peak speed (km/h) for running trips. Allows for sprint segments |

### Train Classification (Optional)

Train is an optional trip type for detecting rail travel. Uses speed variance to distinguish from cars. **Disabled by default.**

:::tip Speed Variance
Trains maintain consistent speeds (low variance < 15), while cars have variable speeds due to traffic and stops (high variance > 25). This is the key discriminator between train and car trips at similar speeds.
:::

| Property                              | Default  | Description                                                                 |
|---------------------------------------|----------|-----------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_TRAIN_ENABLED`         | `false`  | Whether train classification is enabled                                     |
| `GEOPULSE_TIMELINE_TRAIN_MIN_AVG_SPEED`   | `30.0`   | Minimum average speed (km/h) for train trips. Separates from cars in traffic |
| `GEOPULSE_TIMELINE_TRAIN_MAX_AVG_SPEED`   | `150.0`  | Maximum average speed (km/h) for train trips. Covers regional and intercity trains |
| `GEOPULSE_TIMELINE_TRAIN_MIN_MAX_SPEED`   | `80.0`   | Minimum peak speed (km/h) required. Filters out trips with only station waiting time |
| `GEOPULSE_TIMELINE_TRAIN_MAX_MAX_SPEED`   | `180.0`  | Maximum peak speed (km/h) for train trips. Upper limit for rail speeds     |
| `GEOPULSE_TIMELINE_TRAIN_MAX_SPEED_VARIANCE` | `15.0`   | Maximum speed variance allowed. Lower values require more consistent speeds |

### Flight Classification (Optional)

Flight is an optional trip type for detecting air travel. **Disabled by default.**

:::info OR Logic for Flights
Flight classification uses **OR logic**: a trip is classified as FLIGHT if **either** average speed ≥ `FLIGHT_MIN_AVG_SPEED` **or** maximum speed ≥ `FLIGHT_MIN_MAX_SPEED`. This handles flights with long taxi/boarding times that lower the overall average.
:::

| Property                              | Default  | Description                                                                   |
|---------------------------------------|----------|-------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_FLIGHT_ENABLED`        | `false`  | Whether flight classification is enabled                                      |
| `GEOPULSE_TIMELINE_FLIGHT_MIN_AVG_SPEED`  | `400.0`  | Minimum average speed (km/h) for flight trips. Conservative default includes ground time |
| `GEOPULSE_TIMELINE_FLIGHT_MIN_MAX_SPEED`  | `500.0`  | Minimum peak speed (km/h) for flight trips. Catches flights with long taxi time |

### Classification Priority Order

The system evaluates trip types in this specific order:

1. **FLIGHT** - Highest priority (400+ km/h avg OR 500+ km/h peak)
2. **TRAIN** - High speed with low variance (30-150 km/h, variance < 15)
3. **BICYCLE** - Medium speeds (8-25 km/h) - **Checked before RUNNING!**
4. **RUNNING** - Medium-low speeds (7-14 km/h) - **Must be before CAR!**
5. **CAR** - Motorized transport (10+ km/h avg OR 15+ km/h peak)
6. **WALK** - Low speeds (≤6 km/h avg, ≤8 km/h peak)
7. **UNKNOWN** - Fallback for edge cases

:::danger Important
The order is critical! BICYCLE and RUNNING must be checked before CAR because their speed ranges overlap. Changing the order will cause misclassification.
:::

### GPS Noise Detection

The classification system includes automatic GPS noise detection:

- **Supersonic Speed Rejection**: Speeds above 1,200 km/h are rejected as GPS noise
- **Reliability Validation**: GPS speeds are compared against calculated speeds from distance/duration
- **Adaptive Thresholds**: Different validation rules for low-speed vs. high-speed trips
- **Smart Fallbacks**: Automatically switches to calculated speeds when GPS data is unreliable

For more details on how travel classification works, see the [Travel Classification Guide](/docs/user-guide/timeline/travel_classification).

## Staypoint Merging

Controls how nearby staypoints are combined to reduce timeline fragmentation.

| Property                                                 | Default | Description                                       |
|----------------------------------------------------------|---------|---------------------------------------------------|
| `GEOPULSE_TIMELINE_STAYPOINT_MERGE_ENABLED`              | `true`  | Whether to merge nearby staypoints                |
| `GEOPULSE_TIMELINE_STAYPOINT_MERGE_MAX_DISTANCE_METERS`  | `400`   | Maximum distance (meters) for merging staypoints  |
| `GEOPULSE_TIMELINE_STAYPOINT_MERGE_MAX_TIME_GAP_MINUTES` | `15`    | Maximum time gap (minutes) for merging staypoints |

## GPS Path Simplification

Optimizes trip paths by reducing GPS points while preserving route accuracy. This configuration is used when we save a
trip to the database and then display it on the map and it doesn't impact how
accurate the trip is calculated

| Property                                           | Default | Description                                                     |
|----------------------------------------------------|---------|-----------------------------------------------------------------|
| `GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_ENABLED`    | `true`  | Whether GPS path simplification is enabled                      |
| `GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_TOLERANCE`  | `15.0`  | Base tolerance (meters) for path simplification                 |
| `GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_MAX_POINTS` | `100`   | Maximum GPS points to retain in simplified paths (0 = no limit) |
| `GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_ADAPTIVE`   | `true`  | Enable adaptive simplification based on trip characteristics    |

## Data Gap Detection

Detects and handles gaps in GPS data that might indicate missing location information.

| Property                                          | Default | Description                                                     |
|---------------------------------------------------|---------|-----------------------------------------------------------------|
| `GEOPULSE_TIMELINE_DATA_GAP_THRESHOLD_SECONDS`    | `10800` | Time threshold (seconds) to consider a gap in data (3 hours)    |
| `GEOPULSE_TIMELINE_DATA_GAP_MIN_DURATION_SECONDS` | `1800`  | Minimum duration (seconds) for a gap to be significant (30 min) |

### Gap Stay Inference

Controls whether to infer continuous stays during data gaps when GPS resumes at the same location.

| Property                                              | Default | Description                                                                         |
|-------------------------------------------------------|---------|-------------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_GAP_STAY_INFERENCE_ENABLED`        | `false` | Enable stay inference during gaps (e.g., overnight at home)                         |
| `GEOPULSE_TIMELINE_GAP_STAY_INFERENCE_MAX_GAP_HOURS`  | `24`    | Maximum gap duration (hours) for stay inference. Longer gaps create normal data gaps |

**How it works:**
When enabled and GPS resumes within the stay radius of the previous location, extends the stay across the gap instead of creating a data gap. Useful for overnight stays at home when GPS tracking stops.

**Example:**
- Without inference: Stay (20:00-20:05) → Gap (20:05-08:00) → Stay (08:00-...)
- With inference: Stay (20:00-08:00) continuous overnight

### Gap Trip Inference

Controls whether to infer trips during data gaps based on long-distance movement between GPS points.

| Property                                                     | Default  | Description                                                                                   |
|--------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_ENABLED`               | `false`  | Enable trip inference during gaps (e.g., flights, long-distance travel)                       |
| `GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_MIN_DISTANCE_METERS`   | `100000` | Minimum distance (meters) between points to infer a trip (default: 100 km)                    |
| `GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_MIN_GAP_HOURS`         | `1`      | Minimum gap duration (hours) for trip inference                                               |
| `GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_MAX_GAP_HOURS`         | `24`     | Maximum gap duration (hours) for trip inference. Longer gaps create normal data gaps          |

**How it works:**
When enabled and the distance between GPS points before/after a gap exceeds the minimum threshold, creates an inferred trip instead of a data gap. The trip is automatically classified (FLIGHT, TRAIN, CAR) based on distance and duration using intelligent heuristics.

**Example:**
- Home (Country A) at 18:18 → [GPS off during flight] → Airport (Country B) at 05:25
- Distance: 1,779 km > 100 km threshold
- Result: Creates inferred FLIGHT trip instead of data gap

**Distance-based classification heuristics:**
- **>1000 km**: Almost certainly a flight
- **>300 km + >100 km/h avg**: Clear flight signature
- **>500 km + >80 km/h avg**: Likely flight with ground time
- **100-800 km + 50-150 km/h**: Possibly train travel

**Priority system:**
1. Same location → Stay inference (if enabled)
2. Long distance → Trip inference (if enabled)
3. Neither applies → Normal data gap

:::info User Preference Override
These gap inference settings can be overridden per-user through the Timeline Preferences interface. Environment variables set the default values for new users.
:::

## Kubernetes / Helm Configuration

Timeline configuration variables are **not included** in the default Helm chart values to keep it simple. Most users can configure timeline settings via the **Admin Panel UI**.

If you need to override timeline defaults, use custom environment variables:

```yaml
# custom-values.yaml
backend:
  extraEnv:
    - name: GEOPULSE_TIMELINE_PROCESSING_THREADS
      value: "4"
    - name: GEOPULSE_TIMELINE_VIEW_ITEM_LIMIT
      value: "200"
    - name: GEOPULSE_TIMELINE_STAYPOINT_RADIUS_METERS
      value: "75"
    # Gap inference settings
    - name: GEOPULSE_TIMELINE_GAP_STAY_INFERENCE_ENABLED
      value: "true"
    - name: GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_ENABLED
      value: "true"
    - name: GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_MIN_DISTANCE_METERS
      value: "100000"
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details on adding custom environment variables, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-deployment#advanced-configuration-custom-environment-variables).