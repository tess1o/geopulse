# Timeline Global Configuration

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

| Property                                                     | Default  | Description                                                                                                                 |
|--------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_TRIP_DETECTION_ALGORITHM`                 | `single` | Algorithm used for trip detection. Possible values: `single` and `multiple`                                                 |
| `GEOPULSE_TIMELINE_TRIP_ARRIVAL_MIN_DURATION_SECONDS`        | `90`     | Minimum duration (seconds) for arrival detection. GPS points must be clustered and slow for this duration to detect arrival |
| `GEOPULSE_TIMELINE_TRIP_SUSTAINED_STOP_MIN_DURATION_SECONDS` | `60`     | Minimum duration (seconds) for sustained stop detection. Filters out brief stops like traffic lights                        |
| `GEOPULSE_TIMELINE_WALKING_MAX_AVG_SPEED`                    | `6.0`    | Maximum sustained speed (km/h) for walking classification. Trips above this are classified as non-walking                   |
| `GEOPULSE_TIMELINE_WALKING_MAX_MAX_SPEED`                    | `8.0`    | Maximum instantaneous speed (km/h) for walking trips. Brief bursts above this reclassify the entire trip                    |
| `GEOPULSE_TIMELINE_CAR_MIN_AVG_SPEED`                        | `8.0`    | Minimum sustained speed (km/h) required for car classification. Trips below this won't be classified as driving             |
| `GEOPULSE_TIMELINE_CAR_MIN_MAX_SPEED`                        | `15.0`   | Minimum peak speed (km/h) required for car classification. Trips never reaching this speed won't be classified as driving   |
| `GEOPULSE_TIMELINE_SHORT_DISTANCE_KM`                        | `1.0`    | Distance threshold (km) for applying relaxed walking speed detection to account for GPS inaccuracies in short trips         |

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