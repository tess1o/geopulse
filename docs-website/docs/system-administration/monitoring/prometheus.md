---
sidebar_position: 2
---

# Prometheus

GeoPulse exposes a range of metrics in Prometheus format, providing valuable insights into the application's performance and usage. This document outlines how to access these metrics and provides a comprehensive list of all available metrics.

## Accessing Prometheus Metrics

The Prometheus metrics are available via the following endpoint:

```
/api/prometheus/metrics
```

You can use this endpoint to scrape metrics with a Prometheus server or any other compatible monitoring tool.

## Configuration

GeoPulse allows you to configure custom Prometheus metrics collection through environment variables at runtime. This is useful for controlling resource usage, especially in environments with many users or limited resources.

:::note
The Prometheus endpoint `/api/prometheus/metrics` is always available (configured at build time). You can control **custom GeoPulse metrics** at runtime, but default Micrometer metrics (HTTP requests, JVM, datasource, etc.) will always be collected and exposed.
:::

### Global Configuration

| Environment Variable                      | Default | Description                                                   |
| ----------------------------------------- | ------- | ------------------------------------------------------------- |
| `GEOPULSE_PROMETHEUS_ENABLED`             | `false` | Enable/disable all **custom GeoPulse metrics** (users, GPS points, timeline, favorites, etc.). Does not affect the Prometheus endpoint or default Micrometer metrics. |
| `GEOPULSE_PROMETHEUS_REFRESH_INTERVAL`    | `10m`   | Interval for refreshing custom metric values (e.g., `5m`, `30m`, `1h`). Only applies when custom metrics are enabled. |

### Per-Metric Configuration

You can selectively enable or disable specific metric categories:

| Environment Variable                         | Default | Description                                   |
| -------------------------------------------- | ------- | --------------------------------------------- |
| `GEOPULSE_PROMETHEUS_GPS_POINTS_ENABLED`     | `true`  | Enable/disable GPS points metrics.            |
| `GEOPULSE_PROMETHEUS_USER_METRICS_ENABLED`   | `true`  | Enable/disable user activity metrics.         |
| `GEOPULSE_PROMETHEUS_TIMELINE_ENABLED`       | `true`  | Enable/disable timeline metrics.              |
| `GEOPULSE_PROMETHEUS_FAVORITES_ENABLED`      | `true`  | Enable/disable favorite locations metrics.    |
| `GEOPULSE_PROMETHEUS_GEOCODING_ENABLED`      | `true`  | Enable/disable reverse geocoding metrics.     |
| `GEOPULSE_PROMETHEUS_MEMORY_ENABLED`         | `true`  | Enable/disable process memory metrics.        |

### Docker / Docker Compose Configuration

**Enable custom GeoPulse metrics (in addition to default Micrometer metrics):**
```bash
GEOPULSE_PROMETHEUS_ENABLED=true
```

**Disable custom GeoPulse metrics (keep only default Micrometer metrics):**
```bash
GEOPULSE_PROMETHEUS_ENABLED=false
```
This is the **default behavior** - only HTTP requests, JVM, and datasource metrics are exposed.

**Change refresh interval to 30 minutes:**
```bash
GEOPULSE_PROMETHEUS_ENABLED=true
GEOPULSE_PROMETHEUS_REFRESH_INTERVAL=30m
```

**Disable expensive per-user metrics while keeping aggregate metrics:**
```bash
GEOPULSE_PROMETHEUS_ENABLED=true
GEOPULSE_PROMETHEUS_GPS_POINTS_ENABLED=false
GEOPULSE_PROMETHEUS_TIMELINE_ENABLED=false
GEOPULSE_PROMETHEUS_FAVORITES_ENABLED=false
```

**Enable only lightweight custom metrics:**
```bash
GEOPULSE_PROMETHEUS_ENABLED=true
GEOPULSE_PROMETHEUS_GPS_POINTS_ENABLED=false
GEOPULSE_PROMETHEUS_TIMELINE_ENABLED=false
GEOPULSE_PROMETHEUS_FAVORITES_ENABLED=false
GEOPULSE_PROMETHEUS_USER_METRICS_ENABLED=true
GEOPULSE_PROMETHEUS_GEOCODING_ENABLED=true
GEOPULSE_PROMETHEUS_MEMORY_ENABLED=true
```

### Kubernetes / Helm Configuration

The GeoPulse Helm chart provides native support for Prometheus metrics configuration via `values.yaml`:

**Enable all custom metrics:**
```yaml
# values.yaml or custom-values.yaml
config:
  prometheus:
    enabled: true
    refreshInterval: "10m"
```

**Enable with custom refresh interval:**
```yaml
config:
  prometheus:
    enabled: true
    refreshInterval: "5m"  # Refresh every 5 minutes
```

**Enable only lightweight metrics (disable per-user metrics):**
```yaml
config:
  prometheus:
    enabled: true
    refreshInterval: "10m"
    # Disable resource-intensive per-user metrics
    gpsPoints:
      enabled: false
    timeline:
      enabled: false
    favorites:
      enabled: false
    # Keep lightweight aggregate metrics
    userMetrics:
      enabled: true
    geocoding:
      enabled: true
    memory:
      enabled: true
```

**Apply the configuration:**
```bash
helm upgrade geopulse ./helm/geopulse -f custom-values.yaml
```

**Using with Prometheus Operator / ServiceMonitor:**

If you're using the Prometheus Operator, create a ServiceMonitor to scrape GeoPulse metrics:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: geopulse
  labels:
    app: geopulse
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: geopulse
      app.kubernetes.io/component: backend
  endpoints:
  - port: http
    path: /api/prometheus/metrics
    interval: 30s
```

For more details on Helm configuration, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-deployment#prometheus-metrics).

:::note
All configuration changes require an application restart to take effect. For Kubernetes deployments, this happens automatically during `helm upgrade`.
:::

## Available Metrics

Below is a list of all the metrics exposed by GeoPulse.

### Default Micrometer Metrics

In addition to the custom metrics listed below, GeoPulse also exposes the default metrics provided by Micrometer. These include metrics for HTTP server requests, such as:

- `http_server_requests_seconds_count`: The total number of HTTP requests.
- `http_server_requests_seconds_sum`: The total time spent processing HTTP requests.

These metrics are available for each API endpoint separately, allowing for detailed monitoring of API performance.

### Custom Application Metrics

The following custom metrics provide insights into specific features and components of GeoPulse:

#### User Metrics

| Metric Name                | Description                                                      | Tags |
| -------------------------- | ---------------------------------------------------------------- | ---- |
| `users_total`              | Total number of users.                                           | -    |
| `users_active_last_24h`    | Number of users with GPS points in last 24 hours.                | -    |
| `users_active_last_7d`     | Number of users with GPS points in last 7 days.                  | -    |
| `users_with_gps_data`      | Number of users who have at least one GPS point.                 | -    |

#### GPS Points Metrics

| Metric Name                      | Description                                                   | Tags   |
| -------------------------------- | ------------------------------------------------------------- | ------ |
| `gps_points_total`               | Total number of GPS points for all users.                     | -      |
| `gps_points_per_user_total`      | Total number of GPS points for a specific user.               | `user` |
| `gps_last_timestamp`             | Unix timestamp of the last GPS point received (overall).      | -      |
| `gps_last_timestamp_per_user`    | Unix timestamp of last GPS point for a specific user.         | `user` |
| `gps_points_last_24h`            | Number of GPS points added in the last 24 hours.              | -      |
| `gps_avg_points_per_user`        | Average number of GPS points per user (among users with GPS). | -      |

#### Favorite Locations Metrics

| Metric Name                         | Description                                                           | Tags   |
| ----------------------------------- | --------------------------------------------------------------------- | ------ |
| `favorite_locations_total`          | Total number of Favorite Locations.                                   | -      |
| `favorite_locations_per_user_total` | Total number of Favorite Locations for a specific user.               | `user` |
| `favorite_locations_avg_per_user`   | Average number of Favorite Locations per user (among users with any). | -      |

#### Timeline Metrics

| Metric Name                         | Description                                               | Tags   |
| ----------------------------------- | --------------------------------------------------------- | ------ |
| `timeline_stays_total`              | Total number of Timeline stays for all users.             | -      |
| `timeline_stays_per_user_total`     | Total number of Timeline stays for a specific user.       | `user` |
| `timeline_trips_total`              | Total number of Timeline trips for all users.             | -      |
| `timeline_trips_per_user_total`     | Total number of Timeline trips for a specific user.       | `user` |
| `timeline_data_gaps_total`          | Total number of Timeline data gaps for all users.         | -      |
| `timeline_data_gaps_per_user_total` | Total number of Timeline data gaps for a specific user.   | `user` |

#### Other Metrics

| Metric Name                      | Description                                    | Tags |
| -------------------------------- | ---------------------------------------------- | ---- |
| `reverse_geocoding_total`        | Total number of reverse geocoding lookups.     | -    |
| `process_resident_memory_bytes`  | Resident memory of the application process.    | -    |
| `process_virtual_memory_bytes`   | Virtual memory of the application process.     | -    |

**Note on Metric Naming:**

- Metrics without tags represent overall/aggregate values across all users.
- Metrics with `_per_user_` in the name are tagged with `user` and provide per-user breakdowns.
- All metrics are refreshed at regular intervals (default: 10 minutes, configurable via `GEOPULSE_PROMETHEUS_REFRESH_INTERVAL`).

## Performance Considerations

### Resource Usage

Custom metrics collection involves periodic database queries. Consider the following when configuring metrics:

- **Per-user metrics** (`gps_points_per_user_total`, `timeline_stays_per_user_total`, etc.) iterate over all users and can be resource-intensive with many users.
- **Aggregate metrics** (`gps_points_total`, `users_total`, etc.) are lightweight single queries.
- **Memory metrics** (`process_resident_memory_bytes`, `process_virtual_memory_bytes`) are very lightweight and don't query the database.

### Optimization Strategies

For high-user-count deployments:

1. **Disable per-user metrics** if you only need aggregate statistics:
   ```bash
   GEOPULSE_PROMETHEUS_GPS_POINTS_ENABLED=false
   GEOPULSE_PROMETHEUS_TIMELINE_ENABLED=false
   GEOPULSE_PROMETHEUS_FAVORITES_ENABLED=false
   ```

2. **Increase refresh interval** to reduce database load:
   ```bash
   GEOPULSE_PROMETHEUS_REFRESH_INTERVAL=30m
   ```

3. **Disable all custom metrics** (keep only default Micrometer metrics):
   ```bash
   GEOPULSE_PROMETHEUS_ENABLED=false
   ```
   This is the default configuration. The endpoint will still be available with HTTP, JVM, and datasource metrics.

## Configuration Hierarchy

The configuration works in a hierarchical manner:

1. **Prometheus Endpoint** - Always enabled at build time. The `/api/prometheus/metrics` endpoint is always available and will always expose default Micrometer metrics (HTTP requests, JVM, datasource).

2. **`GEOPULSE_PROMETHEUS_ENABLED=false`** (default) - Disables all custom GeoPulse metrics. Only default Micrometer metrics are exposed.

3. **`GEOPULSE_PROMETHEUS_ENABLED=true`** - Enables custom GeoPulse metrics in addition to default Micrometer metrics.

4. **Per-metric flags** (e.g., `GEOPULSE_PROMETHEUS_GPS_POINTS_ENABLED=false`) - Fine-grained control over specific custom metric categories. Only effective when `GEOPULSE_PROMETHEUS_ENABLED=true`.
