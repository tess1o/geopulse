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
- All metrics are updated every 10 minutes via a scheduled job.
