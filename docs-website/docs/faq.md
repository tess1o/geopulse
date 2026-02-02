---
title: Frequently Asked Questions
description: Common questions and answers about GeoPulse
---

# Frequently Asked Questions

Find answers to common questions about GeoPulse, troubleshooting tips, and guidance on reporting issues.

---

## General Questions

### What is GeoPulse?

GeoPulse is a self-hosted location tracking and analysis platform that transforms raw GPS data into meaningful insights.
It integrates with popular tracking apps like OwnTracks, Traccar, GPSLogger, and HomeAssistant to provide timeline
visualization, trip classification, and analytics — all while keeping your data private on your own server.

### What location sources are supported?

GeoPulse supports multiple GPS data sources:

- **OwnTracks** (HTTP and MQTT)
- **Overland** (HTTP)
- **Dawarich** (HTTP)
- **GPSLogger** (HTTP)
- **HomeAssistant** (HTTP)
- **File imports** (GPX, GeoJSON, Google Timeline, CSV, OwnTracks)

Each source can be configured with custom endpoints and authentication in the Location Sources page.

### Is GeoPulse free and open source?

Yes! GeoPulse is released under the BSL 1.1 License and is completely free for personal use. The source code is
available on [GitHub](https://github.com/tess1o/geopulse).

### Can I self-host GeoPulse?

Absolutely! Self-hosting is the primary way to use GeoPulse. See
the [Quick Start Guide](/docs/getting-started/quick-start) for deployment instructions using Docker Compose or
Kubernetes.

---

## Timeline & GPS Data

### Why isn't my timeline generating?

If your timeline isn't generating, check the following:

1. **Verify GPS data exists**: Navigate to **GPS Data** page and ensure location points are being uploaded
2. Check `geopulse-backend` container logs for errors

### Why are there gaps in my timeline?

Data gaps occur when there's a significant time period without GPS data. Common causes:

- **Device turned off or airplane mode**
- **GPS tracking app not running**
- **No network connectivity** (for HTTP-based sources)
- **Battery optimization** killing the tracking app

GeoPulse detects gaps and displays them in the timeline. You can adjust gap detection thresholds in **Timeline
Preferences → GPS Gaps Detection**.

### How long does timeline generation take?

Timeline generation time depends on the volume of GPS data and reverse geocoding. Very first timeline generation can
take more time because for each identified Stay location we need to get reverse geocoding information and performnace
depends on selected provider. Once we cached in the database reverse geocoding information, subsequent timeline
generations are much faster - usually in seconds.

You can monitor progress in the **Timeline Jobs** page (/app/timeline/jobs) Timeline generation happens in the
background, so you can
continue using the app.

### How accurate is the timeline?

Timeline accuracy depends on:

- **GPS signal quality**: Better GPS accuracy results in more precise locations
- **GPS points number**: More points = more accurate timeline. Simple as that
- **Environment**: Urban areas with tall buildings can cause GPS drift
- **Timeline preferences**: Fine-tuning detection thresholds improves accuracy

GeoPulse uses GPS accuracy metrics to filter unreliable points and can interpolate missing data based on your
preferences.

---

## Trip Classification

### How does GeoPulse classify trips?

GeoPulse analyzes trip speed, distance, and duration to classify transportation modes (walk, car, bicycle, running,
train, flight). The classification uses:

- **GPS speed data**: Average and maximum speeds from GPS points
- **Calculated speed**: Distance divided by duration as a fallback
- **Configurable thresholds**: Speed ranges for each transport type
- **Priority order**: Higher-priority modes (like flight) are checked first

See the [Trip Classification Guide](/docs/user-guide/timeline/travel_classification) for detailed threshold information.

### Why is my car trip classified as walking (or vice versa)?

Misclassification can happen due to:

- **GPS inaccuracy**: Poor GPS signal can report incorrect speeds
- **Threshold settings**: Your speed thresholds might need adjustment
- **Short trips**: Very short trips have limited data for classification
- **Traffic conditions**: Heavy traffic can make car speeds similar to walking

You can view why a specific trip was classified by **right-clicking the trip card** and selecting **"View Classification
Details"**. Adjust thresholds in **Timeline Preferences → Trip Classification** if needed.

### Can I manually change trip classifications?

Not currently. Trip classifications are automatically determined based on GPS data and your preferences. However, you
can:

- Adjust classification thresholds in Timeline Preferences
- Enable/disable specific transport modes (bicycle, running, train, flight)
- View classification reasoning by right-clicking a trip

GeoPulse comparing to Google Timeline allows to change almost any timeline parameter which leads to complete timeline
regeneration. Thus, we do not allow to modify Stays or Trips – those changes will be lost once timeline is regenerated
again. It's a fundumental tradeoff between flexibility (we allow end users to change almost any timeline parameter) and
allowing to modify timeline data.
---

## Troubleshooting

### Timeline jobs keep failing – what should I do?

If timeline jobs consistently fail:

1. **Check job details**: Click on the failed job to see the error message
2. **Review preferences**: Extremely restrictive thresholds can cause processing failures
3. **Verify database health**: Ensure PostgreSQL/PostGIS is running and has enough disk space
4. **Look at logs**: Check application logs for detailed error information
5. **Export debug data**: Use the debug export tool and report the issue

Common causes include database connectivity issues, corrupted GPS data, or configuration errors.


---

## Debug Export & Issue Reporting

### When should I export debug data?

Export debug data when you encounter:

- **Incorrect trip classifications** (walks classified as cars, etc.)
- **Incorrect stay or trip durations** (stays or trips are longer or shorter than expected)

Debug exports are **privacy-preserving** — coordinates are anonymized by random shifting, and location names are
replaced with generic labels.

### What does debug export share? Is it safe?

Debug exports are designed to be safe to share publicly:

- **Coordinates shifted**: All latitude/longitude values are shifted by a random offset (you can set your own offset).
  Thus, no one can identify original coordinates, however timestamps, distances between GPS points, speed, accuracy is
  preserved.
- **Names anonymized**: Location names like "Home" become "Location 1", "Work" becomes "Location 2"
- **Relative positions preserved**: The spatial relationship between points is maintained for debugging
- **Timeline config included**: Your preferences and thresholds are included (no personal data)

The export **does not include**:

- Original coordinates or addresses
- User account information
- Authentication tokens or passwords

### How do I report a bug effectively?

Follow these steps to report a bug:

1. **Export debug data**: Go to **Help & Support → Export Debug Data** or directly to **Debug Export** page
    - Select the date range that covers the issue
    - Use auto-generated coordinate shifts or provide your own
    - Download the ZIP file

2. **Create a GitHub issue**:
    - Go to [github.com/tess1o/geopulse/issues](https://github.com/tess1o/geopulse/issues/new)
    - Use a clear, descriptive title (e.g., "Timeline fails to generate for trips over 500km")

3. **Attach debug export**: Upload the ZIP file to the issue

4. **Describe the problem**:
    - What you expected to happen
    - What actually happened
    - Steps to reproduce (if known)
    - Any error messages shown

5. **Include environment info**:
    - GeoPulse version (shown in Help & Support page)
    - Deployment method (Docker, Kubernetes, etc.)
    - Browser and OS (for frontend issues)

---

## Privacy & Security

### Who can see my location data?

Your location data is stored **only on your self-hosted server**. GeoPulse doesn't send data to external services
except:

- **Map tiles**: Loaded from OpenStreetMap or your configured tile provider
- **Geocoding**: Reverse geocoding uses Nominatim by default (can be self-hosted). You can change it to Photon (
  including self hosted version), Google Maps
- **AI features**: If enabled, queries are sent to your configured OpenAI-compatible API

You control all data access through user accounts and permissions.

### Is my location data encrypted?

- **In transit**: All API communication uses HTTPS (if configured)
- **At rest**: Database encryption depends on your PostgreSQL configuration - by default location data is stored in
  unencrypted tables
- **Sharing**: Shared timeline links use unique tokens, but data is served unencrypted
- **AI API Token**: encrypted in database
- **Account passwords**: encrypted in database

For maximum security, enable PostgreSQL transparent data encryption (TDE) and use HTTPS for all connections.

### How do I backup my data?

GeoPulse data is stored in PostgreSQL. To back up:

**Database backup**:

```bash
pg_dump -h localhost -U geopulse geopulse_db > backup.sql
```

**Data export** (user-friendly):

- Use **Data Export & Import** page to export your GPS data, favorites, and timeline

See the [Backup Guide](/docs/system-administration/maintenance/backup-restore) for detailed instructions.

---

## Features

### What are Favorites and how do I use them?

Favorites are important locations you want to track:

- **Favorite Locations**: Specific points (e.g., Home, Work, Gym)
- **Favorite Areas**: Geographic regions (e.g., Downtown, Neighborhood)

When timeline processing detects you stayed at a favorite location, it automatically labels the stay. Manage favorites
in **Favorites Management** page.

### What are Period Tags?

Period Tags let you annotate time ranges with labels (e.g., "Vacation in Italy", "Business Trip"). They help organize
and filter timeline data by meaningful events.

Create period tags in **Period Tags Management** page and view them overlaid on your timeline.

### Can I export my data?

Yes! Use the **Data Export & Import** page to export your GPS data in various formats:

- **GPX**:
- **GeoJSON**:
- **CSV**:
- **GeoPulse JSON**:
- **OwnTracks JSON**:

---

## Technical

### What are the system requirements?

**Minimum requirements**:

- **CPU**: 1 core (at idle CPU usage is 0.25% vCPU)
- **RAM**: 512 MB
- **Disk**: 2 GB (depends on GPS data volume, migth be more for large datasets)
- **Database**: PostgreSQL 17

**Recommended for large datasets**:

- **CPU**: 2+ cores
- **RAM**: 1-2 GB
- **Disk**: 50+ GB SSD

See the [Deployment Guide](/docs/getting-started/deployment/docker-compose) for detailed requirements.

### How do I configure authentication?

GeoPulse supports:

- **Local accounts**: Username/password stored in database
- **OIDC/OAuth2**: Integrate with Keycloak, Auth0, Google, etc.

OIDC configuration is done by administrators in **Admin → OIDC Providers** or via environment variables See
the [Authentication Guide](/docs/system-administration/configuration/authentication) for setup instructions.

---

## Still Need Help?

If your question wasn't answered:

1. **Check the documentation**: [Full Documentation](https://tess1o.github.io/geopulse/)
2. **Search GitHub issues**: [Existing Issues](https://github.com/tess1o/geopulse/issues)
3. **Report a bug**: [Create New Issue](https://github.com/tess1o/geopulse/issues/new)
4. **Export debug data**: Use the **Help & Support** page in the app

---

*Last updated: February 2026*
