# GeoPulse Configuration

## 🔧 Advanced Configuration

GeoPulse provides extensive configuration options to fine-tune timeline generation and GPS data processing. There are
two types of configurations:

- **🔧 User-Customizable Settings**: Timeline processing settings that can be customized per-user through the web
  interface. Environment variables set system-wide defaults, but users can override these in their personal settings.
- **⚙️ System-Wide Settings**: Application settings that apply to all users and cannot be customized individually (
  geocoding, favorites, processing, etc.).

## 🔧 User-Customizable Timeline Settings

*These settings can be overridden per-user through the web interface. Environment variables set the default values for
new users.*

### Staypoint Detection

Controls how GeoPulse identifies when you're stationary at a location. *Users can customize these settings
individually.*

| Property                                            | Default | Description                                                             |
|-----------------------------------------------------|---------|-------------------------------------------------------------------------|
| `geopulse.timeline.staypoint.radius_meters`         | `50`    | Radius (in meters) used for stay point detection and clustering         |
| `geopulse.timeline.staypoint.min_duration_minutes`  | `7`     | Minimum duration (in minutes) for a stay point to be confirmed          |
| `geopulse.timeline.staypoint.use_velocity_accuracy` | `true`  | Whether to consider velocity accuracy in filtering                      |
| `geopulse.timeline.staypoint.velocity.threshold`    | `2.5`   | Velocity threshold (m/s) below which movement is considered a staypoint |
| `geopulse.timeline.staypoint.accuracy.threshold`    | `60.0`  | Maximum GPS accuracy (meters) for staypoint detection                   |
| `geopulse.timeline.staypoint.min_accuracy_ratio`    | `0.5`   | Minimum accuracy ratio required for staypoint consideration             |

### Trip Classification

Controls how movement is classified into different transportation modes based on speed analysis. *Users can customize
these settings individually.*

| Property                                                        | Default  | Description                                                                                                                 |
|-----------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------|
| `geopulse.timeline.trip.detection.algorithm`                    | `single` | Algorithm used for trip detection. Possible values: `single` and `multiple`                                                 |
| `geopulse.timeline.trip.arrival.min_duration_seconds`           | `90`     | Minimum duration (seconds) for arrival detection. GPS points must be clustered and slow for this duration to detect arrival |
| `geopulse.timeline.trip.sustained_stop.min_duration_seconds`    | `60`     | Minimum duration (seconds) for sustained stop detection. Filters out brief stops like traffic lights                        |
| `geopulse.timeline.travel_classification.walking.max_avg_speed` | `6.0`    | Maximum sustained speed (km/h) for walking classification. Trips above this are classified as non-walking                   |
| `geopulse.timeline.travel_classification.walking.max_max_speed` | `8.0`    | Maximum instantaneous speed (km/h) for walking trips. Brief bursts above this reclassify the entire trip                    |
| `geopulse.timeline.travel_classification.car.min_avg_speed`     | `8.0`    | Minimum sustained speed (km/h) required for car classification. Trips below this won't be classified as driving             |
| `geopulse.timeline.travel_classification.car.min_max_speed`     | `15.0`   | Minimum peak speed (km/h) required for car classification. Trips never reaching this speed won't be classified as driving   |
| `geopulse.timeline.travel_classification.short_distance_km`     | `1.0`    | Distance threshold (km) for applying relaxed walking speed detection to account for GPS inaccuracies in short trips         |

### Staypoint Merging

Controls how nearby staypoints are combined to reduce timeline fragmentation. *Users can customize these settings
individually.*

| Property                                                 | Default | Description                                       |
|----------------------------------------------------------|---------|---------------------------------------------------|
| `geopulse.timeline.staypoint.merge.enabled`              | `true`  | Whether to merge nearby staypoints                |
| `geopulse.timeline.staypoint.merge.max_distance_meters`  | `400`   | Maximum distance (meters) for merging staypoints  |
| `geopulse.timeline.staypoint.merge.max_time_gap_minutes` | `15`    | Maximum time gap (minutes) for merging staypoints |

### GPS Path Simplification

Optimizes trip paths by reducing GPS points while preserving route accuracy. *Users can customize these settings
individually.*

| Property                                           | Default | Description                                                     |
|----------------------------------------------------|---------|-----------------------------------------------------------------|
| `geopulse.timeline.path.simplification.enabled`    | `true`  | Whether GPS path simplification is enabled                      |
| `geopulse.timeline.path.simplification.tolerance`  | `15.0`  | Base tolerance (meters) for path simplification                 |
| `geopulse.timeline.path.simplification.max_points` | `100`   | Maximum GPS points to retain in simplified paths (0 = no limit) |
| `geopulse.timeline.path.simplification.adaptive`   | `true`  | Enable adaptive simplification based on trip characteristics    |

### Data Gap Detection

Detects and handles gaps in GPS data that might indicate missing location information. *Users can customize these
settings individually.*

| Property                                          | Default | Description                                                     |
|---------------------------------------------------|---------|-----------------------------------------------------------------|
| `geopulse.timeline.data_gap.threshold_seconds`    | `10800` | Time threshold (seconds) to consider a gap in data (3 hours)    |
| `geopulse.timeline.data_gap.min_duration_seconds` | `1800`  | Minimum duration (seconds) for a gap to be significant (30 min) |

## ⚙️ System-Wide Application Settings

*These settings apply to all users and cannot be customized individually.*




### AI Chat Configuration

GeoPulse includes an AI chat assistant that can analyze your location data and provide intelligent insights.

#### AI Encryption Key

The AI encryption key is automatically generated during the initial setup process and stored securely in the keys
directory. For advanced users who need to customize the key location:

```bash
# Optional: Custom AI encryption key location (advanced users only)
GEOPULSE_AI_ENCRYPTION_KEY_LOCATION=file:/app/keys/ai-encryption-key.txt
```

#### AI Configuration

AI features are configured through the web interface and support any OpenAI-compatible API:

- API key and model selection
- Custom API URL (for OpenAI-compatible services) or leave empty for OpenAI
- AI assistant preferences and settings
- All AI settings are encrypted using the automatically generated encryption key

**Supported AI Providers:**

- **OpenAI** (default) - Leave API URL empty to use OpenAI's official API
- **OpenAI-compatible services** - Specify custom API URL for services like:
    - Local AI deployments (Ollama, LocalAI, etc.)
    - Alternative providers with OpenAI-compatible APIs
    - Self-hosted LLM services

**Setup Process:**

1. Configure API key in Profile → AI Assistant
2. (Optional) Set custom API URL for OpenAI-compatible services
3. Select preferred AI model (GPT-3.5-turbo, GPT-4, or custom models)
4. Start asking questions about your location data

## Environment Variables Reference

### User-Customizable Timeline Settings

*Environment variables set defaults for new users. Each user can override these settings in their personal preferences.*

```bash
# Staypoint Detection
GEOPULSE_TIMELINE_STAYPOINT_RADIUS_METERS=50
GEOPULSE_TIMELINE_STAYPOINT_MIN_DURATION_MINUTES=7
GEOPULSE_TIMELINE_STAYPOINT_USE_VELOCITY_ACCURACY=true
GEOPULSE_TIMELINE_STAYPOINT_VELOCITY_THRESHOLD=2.5
GEOPULSE_TIMELINE_STAYPOINT_ACCURACY_THRESHOLD=60.0
GEOPULSE_TIMELINE_STAYPOINT_MIN_ACCURACY_RATIO=0.5

# Trip Detection
GEOPULSE_TIMELINE_TRIP_DETECTION_ALGORITHM=single
GEOPULSE_TIMELINE_TRIP_ARRIVAL_MIN_DURATION_SECONDS=90
GEOPULSE_TIMELINE_TRIP_SUSTAINED_STOP_MIN_DURATION_SECONDS=60

# Travel Classification
GEOPULSE_TIMELINE_WALKING_MAX_AVG_SPEED=6.0
GEOPULSE_TIMELINE_WALKING_MAX_MAX_SPEED=8.0
GEOPULSE_TIMELINE_CAR_MIN_AVG_SPEED=8.0
GEOPULSE_TIMELINE_CAR_MIN_MAX_SPEED=15.0
GEOPULSE_TIMELINE_SHORT_DISTANCE_KM=1.0

# Staypoint Merging
GEOPULSE_TIMELINE_STAYPOINT_MERGE_ENABLED=true
GEOPULSE_TIMELINE_STAYPOINT_MERGE_MAX_DISTANCE_METERS=400
GEOPULSE_TIMELINE_STAYPOINT_MERGE_MAX_TIME_GAP_MINUTES=15

# GPS Path Simplification
GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_ENABLED=true
GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_TOLERANCE=15.0
GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_MAX_POINTS=100
GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_ADAPTIVE=true
```

### Data Gap Detection

```bash
GEOPULSE_TIMELINE_DATA_GAP_THRESHOLD_SECONDS=10800
GEOPULSE_TIMELINE_DATA_GAP_MIN_DURATION_SECONDS=1800
```

### Favorites Configuration

Controls location matching for favorite places. **Applies to all users.**

| Property                                     | Default | Description                                                        |
|----------------------------------------------|---------|--------------------------------------------------------------------|
| `geopulse.favorites.max-distance-from-point` | `50`    | Maximum distance (meters) to match a GPS point to a favorite place |
| `geopulse.favorites.max-distance-from-area`  | `15`    | Maximum distance (meters) to match a GPS point to a favorite area  |

### Realtime Timeline Job

Controls processing of timeline generation. **Applies to all users.**

| Property                               | Default | Description                                                                           |
|----------------------------------------|---------|---------------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_PROCESSING_THREADS` | `2`     | Number of threads used in timeline job processing. Increase it if you have many users |
| `GEOPULSE_TIMELINE_JOB_INTERVAL`       | `5m`    | Execute timeline job each X minutes                                                   |
| `GEOPULSE_TIMELINE_JOB_DELAY`          | `1m`    | Initial delay (after server start) for timeline job processing                        |

### GPS Data Processing

Controls GPS point duplicate detection and processing. **Applies to all users.**

| Property                                                           | Default | Description                                                                                   |
|--------------------------------------------------------------------|---------|-----------------------------------------------------------------------------------------------|
| `geopulse.gps.duplicate-detection.location-time-threshold-minutes` | `2`     | Time threshold (minutes) for considering GPS points as duplicates (with the same coordinates) |

### GPS Data Filtering

These settings define the default filtering parameters for new GPS sources. They can be overridden on a per-source basis
in the web interface.

| Environment Variable                          | Default | Description                                                                                       |
|-----------------------------------------------|---------|---------------------------------------------------------------------------------------------------|
| `GEOPULSE_GPS_FILTER_INACCURATE_DATA_ENABLED` | `false` | Enables GPS data filtering by default for new sources.                                            |
| `GEOPULSE_GPS_MAX_ALLOWED_ACCURACY`           | `100`   | Default maximum allowed accuracy in meters. Points with higher accuracy values will be discarded. |
| `GEOPULSE_GPS_MAX_ALLOWED_SPEED`              | `250`   | Default maximum allowed speed in km/h. Points with higher speed values will be discarded.         |

### OwnTracks

| Property                                     | Default | Description                                                                                                                                                                                                                                   |
|----------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_OWNTRACKS_PING_TIMESTAMP_OVERRIDE` | `false` | When OwnTracks sends a message with `_type=location` and `p=t` (ping) and this property is `true` -  the system will override OwnTracks's message timestamp to the current time.Otherwise, it will use the timestamp received from OwnTracks. |

### Environment Variables Reference

```bash
# System-Wide Settings (apply to all users)

# Sharing
GEOPULSE_SHARE_BASE_URL=

# Favorites Configuration
GEOPULSE_FAVORITES_MAX_DISTANCE_FROM_POINT=75
GEOPULSE_FAVORITES_MAX_DISTANCE_FROM_AREA=15

# GPS Data Processing
GEOPULSE_GPS_DUPLICATE_DETECTION_LOCATION_TIME_THRESHOLD_MINUTES=2

# OwnTracks
GEOPULSE_OWNTRACKS_PING_TIMESTAMP_OVERRIDE=false

# Realtime Timeline Job
GEOPULSE_TIMELINE_PROCESSING_THREADS=2
GEOPULSE_TIMELINE_JOB_INTERVAL=5m
GEOPULSE_TIMELINE_JOB_DELAY=1m
```


## How Configuration Works

### 🔧 User-Customizable Timeline Settings

1. **Environment Variables**: Set system-wide default values for all new users (and existing users if they haven't
   overwritten these settings yet)
2. **Web Interface**: Users can customize these settings for their personal timeline generation
3. **User Overrides**: Once a user changes settings in the UI, their personal preferences are saved and will not be
   affected by future changes to environment variable defaults
4. **New Users**: Will inherit the current environment variable defaults when they first use the system

### ⚙️ System-Wide Application Settings

1. **Environment Variables Only**: These settings can only be configured via environment variables
2. **Global Application**: Apply to all users and cannot be customized individually
3. **Restart Required**: Changes require restarting the GeoPulse application (and docker container re-creation)

## Configuration Notes

- **Environment Variable Format**: Convert dots (`.`) to underscores (`_`) and use uppercase letters
- **Restart Required**: Changes to environment variables require re-creating the GeoPulse containers
- **User Preferences**: Changes made in the web interface are stored per-user and persist even if environment defaults
  change
- **Validation**: Invalid values will fall back to defaults with warnings in the logs

**For Higher Accuracy** (more sensitive detection):

- Decrease `geopulse.timeline.staypoint.velocity.threshold` (e.g., 3-5 m/s)
- Decrease `geopulse.timeline.staypoint.accuracy.threshold` (e.g., 30-40 meters)
- Decrease `geopulse.timeline.staypoint.radius_meters` (e.g., 25-30 meters)

**For Less Noise** (less sensitive detection):

- Increase `geopulse.timeline.staypoint.velocity.threshold` (e.g., 10-15 m/s)
- Increase `geopulse.timeline.staypoint.radius_meters` (e.g., 100-200 meters)
- Increase `geopulse.timeline.staypoint.min_duration_minutes` (e.g., 10-15 minutes)

**For Battery/Storage Optimization**:

- Enable path simplification with higher tolerance (20-30 meters)
- Set lower `geopulse.timeline.path.simplification.max_points` (50-75 points)
- Enable adaptive simplification