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
