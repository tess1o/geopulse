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

## Duplicate Detection

Duplicate detection can now be configured on a per-source basis. When enabled, it prevents storing GPS points with the same coordinates (within ~11 meters) within a configurable time window.

### Per-Source Settings (Recommended)

Configure duplicate detection settings directly in the web UI when adding or editing a GPS source:

- **Enable duplicate detection**: Toggle to enable/disable for this specific source
- **Time threshold (minutes)**: Time window for duplicate detection. Leave empty to use global default.

### Default Values for New Sources

| Environment Variable                            | Default | Description                                                                                  |
|-------------------------------------------------|---------|----------------------------------------------------------------------------------------------|
| `GEOPULSE_GPS_DUPLICATE_DETECTION_ENABLED`      | `false` | Enables duplicate detection by default for new sources.                                      |
| `GEOPULSE_GPS_DUPLICATE_DETECTION_THRESHOLD_MINUTES` | `2` | Default time threshold for duplicate detection when creating new sources.                    |

### Legacy Global Setting (Deprecated)

:::warning
This setting is deprecated since version `1.14.0` but still supported for backward compatibility. It is used as a fallback when a source has duplicate detection enabled but no threshold configured (NULL).
:::

| Environment Variable                                               | Default | Description                                                                                                                                                                     |
|--------------------------------------------------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_GPS_DUPLICATE_DETECTION_LOCATION_TIME_THRESHOLD_MINUTES` | `2`     | Fallback time threshold for existing OwnTracks sources with NULL threshold. Set to 0 or negative to disable. This value is used when per-source threshold is not set (NULL). |

### Migration Notes

When upgrading to this version:
- **Existing OwnTracks sources**: Automatically have duplicate detection enabled with threshold=NULL, so they continue using the global `GEOPULSE_GPS_DUPLICATE_DETECTION_LOCATION_TIME_THRESHOLD_MINUTES` value
- **Existing non-OwnTracks sources**: Duplicate detection remains disabled (no behavior change)
- **New sources (all types)**: Duplicate detection is disabled by default

**How it works:**
- When a new GPS point arrives, GeoPulse checks if any point with the same location (within ~11 meters) exists within ± threshold minutes
- If a duplicate is found, the new point is skipped
- This prevents devices from spamming the database with identical coordinates when stationary
- Exact timestamp matching is always performed as a safety net, regardless of duplicate detection settings


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
