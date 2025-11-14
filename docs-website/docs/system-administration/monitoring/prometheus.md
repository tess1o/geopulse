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

| Metric Name                      | Description                                             | Tags   |
| -------------------------------- | ------------------------------------------------------- | ------ |
| `favorite_locations_total`       | Total number of Favorite Locations.                     | `user` |
| `gps_points_total`               | Total number of GPS points for all users.               | `user` |
| `gps_last_timestamp`             | Unix timestamp of the last GPS point received.          | `user` |
| `process_resident_memory_bytes`  | Resident memory of the application process.             | -      |
| `process_virtual_memory_bytes`   | Virtual memory of the application process.              | -      |
| `reverse_geocoding_total`        | Total number of reverse geocoding lookups.              | -      |
| `timeline_stays_total`           | Total number of timeline stays.                         | `user` |
| `timeline_trips_total`           | Total number of timeline trips.                         | `user` |
| `timeline_data_gaps_total`       | Total number of timeline data gaps.                     | `user` |
| `users_total`                    | Total number of users.                                  | -      |

**Note on Tags:**

- The `user` tag allows for filtering metrics by a specific user, providing a more granular view of the data. When the `user` tag is not present, the metric represents the total value across all users.
