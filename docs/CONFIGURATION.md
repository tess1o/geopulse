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

| Property                                            | Default    | Description                                                                       |
|-----------------------------------------------------|------------|-----------------------------------------------------------------------------------|
| `geopulse.timeline.staypoint.detection.algorithm`   | `enhanced` | Algorithm used for staypoint detection. Possible values: `simple` and `enchanced` |
| `geopulse.timeline.staypoint.use_velocity_accuracy` | `true`     | Whether to consider velocity accuracy in filtering                                |
| `geopulse.timeline.staypoint.velocity.threshold`    | `2.5`      | Velocity threshold (m/s) below which movement is considered a staypoint           |
| `geopulse.timeline.staypoint.accuracy.threshold`    | `60.0`     | Maximum GPS accuracy (meters) for staypoint detection                             |
| `geopulse.timeline.staypoint.min_accuracy_ratio`    | `0.5`      | Minimum accuracy ratio required for staypoint consideration                       |

### Trip Detection

Defines criteria for identifying meaningful trips vs. noise. *Users can customize these settings individually.*

| Property                                      | Default  | Description                                                                 |
|-----------------------------------------------|----------|-----------------------------------------------------------------------------|
| `geopulse.timeline.trip.detection.algorithm`  | `single` | Algorithm used for trip detection. Possible values: `single` and `multiple` |
| `geopulse.timeline.trip.min_distance_meters`  | `50`     | Minimum distance (meters) for a trip to be considered valid                 |
| `geopulse.timeline.trip.min_duration_minutes` | `7`      | Minimum duration (minutes) for a trip to be considered valid                |

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

### Geocoding Services

GeoPulse uses geocoding services to convert GPS coordinates into human-readable addresses and location names. You can
configure multiple providers with automatic fallback support. **This configuration applies to all users.**

### Available Providers

| Provider        | Free Tier | API Key Required | Rate Limits                 |
|-----------------|-----------|------------------|-----------------------------|
| **Nominatim**   | Yes       | No               | 1 request/second            |
| **Google Maps** | Limited   | Yes              | 40,000 requests/month free  |
| **Mapbox**      | Limited   | Yes              | 100,000 requests/month free |

### Geocoding Configuration

| Property                                | Default     | Description                                                     |
|-----------------------------------------|-------------|-----------------------------------------------------------------|
| `geocoding.provider.primary`            | `nominatim` | Primary geocoding service (`nominatim`, `googlemaps`, `mapbox`) |
| `geocoding.provider.fallback`           | _(empty)_   | Fallback service if primary fails                               |
| `geocoding.provider.nominatim.enabled`  | `true`      | Enable Nominatim geocoding service                              |
| `geocoding.provider.googlemaps.enabled` | `false`     | Enable Google Maps geocoding service                            |
| `geocoding.provider.mapbox.enabled`     | `false`     | Enable Mapbox geocoding service                                 |
| `geocoding.googlemaps.api-key`          | _(empty)_   | Google Maps API key (required for Google Maps)                  |
| `geocoding.mapbox.access-token`         | _(empty)_   | Mapbox access token (required for Mapbox)                       |

### Service Setup

**Nominatim (Default)**

- No API key required
- Uses OpenStreetMap data
- Rate limited to 1 request per second
- Best for privacy-conscious users

**Google Maps**

1. Get API key from [Google Cloud Console](https://console.cloud.google.com/)
2. Enable Geocoding API
3. Set billing account (free tier available)

**Mapbox**

1. Create account at [Mapbox](https://account.mapbox.com/)
2. Generate access token
3. Monitor usage in dashboard

### Geocoding Environment Variables

```bash
# Geocoding Service Configuration
GEOPULSE_GEOCODING_PRIMARY_PROVIDER=nominatim
GEOPULSE_GEOCODING_FALLBACK_PROVIDER=googlemaps

# Service Enablement
GEOPULSE_GEOCODING_NOMINATIM_ENABLED=true
GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED=true
GEOPULSE_GEOCODING_MAPBOX_ENABLED=false

# API Credentials (if using paid services)
GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY=your_google_api_key_here
GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN=your_mapbox_token_here
```

**Recommended Setup:**

- **Privacy-focused**: Use Nominatim only
- **High-volume**: Use Google Maps or Mapbox with Nominatim as fallback
- **Reliability**: Configure both primary and fallback services

## Environment Variables Reference

### User-Customizable Timeline Settings

*Environment variables set defaults for new users. Each user can override these settings in their personal preferences.*

```bash
# Staypoint Detection
GEOPULSE_TIMELINE_STAYPOINT_DETECTION_ALGORITHM=enhanced
GEOPULSE_TIMELINE_STAYPOINT_USE_VELOCITY_ACCURACY=true
GEOPULSE_TIMELINE_STAYPOINT_VELOCITY_THRESHOLD=2.5
GEOPULSE_TIMELINE_STAYPOINT_ACCURACY_THRESHOLD=60.0
GEOPULSE_TIMELINE_STAYPOINT_MIN_ACCURACY_RATIO=0.5

# Trip Detection
GEOPULSE_TIMELINE_TRIP_DETECTION_ALGORITHM=single
GEOPULSE_TIMELINE_TRIP_MIN_DISTANCE_METERS=50
GEOPULSE_TIMELINE_TRIP_MIN_DURATION_MINUTES=7

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

### Daily Timeline Processing

Controls batch processing of timeline generation. **Applies to all users.**

| Property                                            | Default | Description                                        |
|-----------------------------------------------------|---------|----------------------------------------------------|
| `geopulse.timeline.daily-processing.enabled`        | `true`  | Whether daily batch timeline processing is enabled |
| `geopulse.timeline.daily-processing.batch-size`     | `20`    | Number of users processed in each batch            |
| `geopulse.timeline.daily-processing.batch-delay-ms` | `1000`  | Delay (milliseconds) between processing each batch |

### GPS Data Processing

Controls GPS point duplicate detection and processing. **Applies to all users.**

| Property                                                           | Default | Description                                                       |
|--------------------------------------------------------------------|---------|-------------------------------------------------------------------|
| `geopulse.gps.duplicate-detection.location-time-threshold-minutes` | `2`     | Time threshold (minutes) for considering GPS points as duplicates |

### Backend URL Configuration

Configures URLs for GPS data source endpoints. **Applies to all users.**

| Property                           | Default                                 | Description                     |
|------------------------------------|-----------------------------------------|---------------------------------|  
| `geopulse.backend.url`             | `http://localhost:8080`                 | Base backend URL                |
| `geopulse.gps.source.owntrack.url` | `${geopulse.backend.url}/api/owntracks` | OwnTracks GPS data endpoint URL |
| `geopulse.gps.source.overland.url` | `${geopulse.backend.url}/api/overland`  | Overland GPS data endpoint URL  |
| `geopulse.gps.source.dawarich.url` | `${geopulse.backend.url}/api/dawarich`  | Dawarich GPS data endpoint URL  |

### Environment Variables Reference

```bash
# System-Wide Settings (apply to all users)

# Favorites Configuration
GEOPULSE_FAVORITES_MAX_DISTANCE_FROM_POINT=50
GEOPULSE_FAVORITES_MAX_DISTANCE_FROM_AREA=15

# Daily Timeline Processing
GEOPULSE_TIMELINE_DAILY_PROCESSING_ENABLED=true
GEOPULSE_TIMELINE_DAILY_PROCESSING_BATCH_SIZE=20
GEOPULSE_TIMELINE_DAILY_PROCESSING_BATCH_DELAY_MS=1000

# GPS Data Processing
GEOPULSE_GPS_DUPLICATE_DETECTION_LOCATION_TIME_THRESHOLD_MINUTES=2

# Backend URL Configuration
GEOPULSE_BACKEND_URL=http://localhost:8080
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
- Decrease `geopulse.timeline.trip.min_distance_meters` (e.g., 25-30 meters)

**For Less Noise** (less sensitive detection):

- Increase `geopulse.timeline.staypoint.velocity.threshold` (e.g., 10-15 m/s)
- Increase `geopulse.timeline.trip.min_distance_meters` (e.g., 100-200 meters)
- Increase `geopulse.timeline.trip.min_duration_minutes` (e.g., 10-15 minutes)

**For Battery/Storage Optimization**:

- Enable path simplification with higher tolerance (20-30 meters)
- Set lower `geopulse.timeline.path.simplification.max_points` (50-75 points)
- Enable adaptive simplification