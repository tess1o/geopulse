---
sidebar_position: 3
---

# Grafana Dashboard

GeoPulse provides a pre-built Grafana dashboard for monitoring application health and business metrics. This dashboard gives administrators a comprehensive view of system performance, user activity, and data collection status.

## Prerequisites

Before importing the GeoPulse Grafana dashboard, ensure you have:

1. **Grafana** installed and running (version 9.x or later)
2. **Prometheus** configured as a data source in Grafana
3. **GeoPulse Prometheus metrics** enabled (see [Prometheus Configuration](./prometheus.md))
4. Prometheus configured to scrape the GeoPulse `/api/prometheus/metrics` endpoint

## Installation

### Step 1: Download the Dashboard

The Grafana dashboard JSON file is available in the GeoPulse repository:

[**Download grafana-dashboard.json**](https://github.com/tess1o/geopulse/blob/main/grafana-dashboard.json)

You can download it directly or use curl:

```bash
curl -O https://raw.githubusercontent.com/tess1o/geopulse/main/grafana-dashboard.json
```

### Step 2: Import into Grafana

1. Open your Grafana instance in a web browser
2. Navigate to **Dashboards** → **Import** (or click the **+** icon → **Import**)
3. Click **Upload JSON file** and select `grafana-dashboard.json`, or paste the JSON content directly
4. Select your **Prometheus data source** from the dropdown
5. Click **Import**

The dashboard will be created with the name "GeoPulse Admin Dashboard" and will be immediately available for use.

### Step 3: Verify Data

After importing, verify that panels are displaying data:

- If panels show "No data", check that Prometheus is successfully scraping GeoPulse metrics
- If you see "N/A" or empty panels, ensure `GEOPULSE_PROMETHEUS_ENABLED=true` in your GeoPulse configuration
- Custom metrics are refreshed every 10 minutes by default (configurable via `GEOPULSE_PROMETHEUS_REFRESH_INTERVAL`)

## Dashboard Overview

The GeoPulse Admin Dashboard is organized into 6 main sections (rows) for easy monitoring:

### 1. Health Status

**Purpose**: Quick health check with visual alerts (red/yellow/green)

Critical indicators that show system health at a glance:

- **Last GPS Point Received**: Time since last GPS data ingestion
  - 🟢 Green: < 15 minutes (healthy)
  - 🟡 Yellow: 15-30 minutes (warning)
  - 🔴 Red: > 30 minutes (critical - no recent data)
- **Active Users (24h)**: Number of users with GPS activity in last 24 hours
- **CPU Usage**: Current CPU utilization as a percentage gauge

### 2. User Metrics

Tracks user engagement and growth:

- **Total Users**: Total registered users
- **Active Users (7d)**: Users with GPS activity in last 7 days
- **Users with GPS Data**: Users who have at least one GPS point
- **User Activity Trend**: Graph showing 24h and 7d active users over time

### 3. GPS Data Metrics

Core data collection monitoring:

- **Total GPS Points**: Cumulative count of all GPS points
- **Avg Points per User**: Average GPS points per user (among users with data)
- **Top 5 Users by GPS Points**: Table showing power users
- **GPS Points Growth**: Graph showing total points accumulation over time
- **Recent GPS Activity (24h)**: Graph showing GPS points in rolling 24-hour window

### 4. Timeline & Features

Business feature usage:

- **Timeline Stays**: Total number of detected stay events
- **Timeline Trips**: Total number of detected trip events
- **Data Gaps**: Total number of timeline data gaps (🟡 yellow if > 10, 🔴 red if > 50)
- **Favorite Locations**: Total number of favorite locations
- **Geocoding Cache**: Number of cached reverse geocoding entries

### 5. HTTP Performance

API monitoring:

- **Request Rate**: HTTP requests per second
- **HTTP Error Rate**: Graph showing 4xx (client errors) and 5xx (server errors) rates over time
- **Request Duration by Status**: Average request duration grouped by status code

### 6. System Resources

System health and resource utilization:

- **Memory Usage**: Graph showing Resident (RSS) and Virtual memory over time
- **CPU Usage Trend**: CPU percentage over time
- **Database Connections**: Agroal connection pool status (Active, Idle, Max Used)

## Configuration

### Time Range

The dashboard defaults to showing the **last 30 minutes** of data. You can adjust this using Grafana's time picker in the top-right corner. Common presets:

- Last 30 minutes (default)
- Last 1 hour
- Last 6 hours
- Last 24 hours
- Last 7 days

### Auto-Refresh

The dashboard auto-refreshes every **30 seconds** by default. You can change this in the time picker dropdown or disable auto-refresh if needed.

### Data Source

If you have multiple Prometheus instances, you can switch the data source using the dropdown at the top of the dashboard.

## Understanding Key Metrics

### GPS Points (24h) Fluctuation

The "Recent GPS Activity (24h)" graph shows a **rolling 24-hour window** count. It's normal for this number to fluctuate as the window slides forward - old data points drop off as new ones are added. This doesn't indicate a problem; it's just how rolling windows work.

### Active Users

"Active users" are defined as users who have submitted at least one GPS point in the specified time window (24h or 7d). This indicates actual usage, not just registered accounts.

### Timeline Data Gaps

Data gaps occur when there are periods with no GPS data between detected locations. Some gaps are normal (e.g., when users turn off their devices), but excessive gaps may indicate issues with data collection.

### HTTP Error Rate

- **4xx errors**: Client-side errors (bad requests, authentication issues, not found, etc.) - usually indicate issues with client applications or API usage
- **5xx errors**: Server-side errors - indicate problems with the GeoPulse backend that need immediate attention