# GPS Data Filtering

:::info
These settings define the default filtering parameters for new GPS sources. They can be overridden on a per-source basis
in the web interface.
:::

| Environment Variable                          | Default | Description                                                                                       |
|-----------------------------------------------|---------|---------------------------------------------------------------------------------------------------|
| `GEOPULSE_GPS_FILTER_INACCURATE_DATA_ENABLED` | `false` | Enables GPS data filtering by default for new sources.                                            |
| `GEOPULSE_GPS_MAX_ALLOWED_ACCURACY`           | `100`   | Default maximum allowed accuracy in meters. Points with higher accuracy values will be discarded. |
| `GEOPULSE_GPS_MAX_ALLOWED_SPEED`              | `250`   | Default maximum allowed speed in km/h. Points with higher speed values will be discarded.         |

## Duplicate Detection (OwnTracks)

:::info
This setting applies only to OwnTracks GPS source. Other GPS sources (Overland, Dawarich, Home Assistant) use exact timestamp matching for duplicate detection.
:::

| Environment Variable                                               | Default | Description                                                                                                                                              |
|--------------------------------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_GPS_DUPLICATE_DETECTION_LOCATION_TIME_THRESHOLD_MINUTES` | `2`     | Time window in minutes for location-based duplicate detection. Prevents storing the same coordinates repeatedly. Set to 0 or negative to disable check. |

**How it works:**
- When a new GPS point arrives, GeoPulse checks if any point with the same location (within ~11 meters) exists within ± threshold minutes
- If a duplicate is found, the new point is skipped
- This prevents OwnTracks devices from spamming the database with identical coordinates when stationary

**When to disable (set to 0 or -1):**
- Importing historical GPS data from backup/export
- Testing and development
- Need to preserve all data points regardless of duplication

## Kubernetes / Helm Configuration

Configure in `values.yaml`:

```yaml
config:
  gps:
    filterInaccurateData: true
    maxAllowedAccuracy: 50  # More strict filtering
    maxAllowedSpeed: 200
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-deployment#gps-filtering).
