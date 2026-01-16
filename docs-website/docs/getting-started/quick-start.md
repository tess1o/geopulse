---
title: Quick Start Guide
description: Get started with GeoPulse in minutes - import your data, configure sources, and explore your location history.
---

# Quick Start Guide

Welcome to GeoPulse! This guide will walk you through everything you need to get started tracking and exploring your location history. By the end of this guide, you'll have data in GeoPulse and know how to use the main features.

:::tip Already Deployed?
This guide assumes you've already deployed GeoPulse and completed the [Initial Setup](/docs/system-administration/initial-setup). If you haven't deployed yet, see the [Deployment guides](/docs/getting-started/deployment/docker-compose) first.
:::

## Overview: Your First Steps

Getting started with GeoPulse involves three main steps:

1. **Get your data in** - Import existing location data (if you have it)
2. **Configure GPS sources** - Set up apps to send new location data
3. **Explore and use your data** - View timeline, analyze trips, and more

Let's walk through each step!

---

## Step 1: Get Your Data Into GeoPulse

If you have existing location data from other tracking apps or services, you'll want to import it first. If you don't have existing data, you can skip to Step 2.

### Do You Have Existing Location Data?

Common sources of existing location data include:
- **Google Timeline** / Google Location History exports
- **OwnTracks** backup files
- **Fitness tracker** exports (GPX files from Garmin, Strava, etc.)
- **Previous tracking apps** (GPX, GeoJSON, or CSV exports)
- **Other GeoPulse instances** (GeoPulse backup files)

### How to Import Your Data

1. **Navigate to Import/Export**
   - Click **Menu** (☰) in the top-left
   - Select **Import/Export Data**
   - Click the **Import** tab

2. **Select Your Format**
   - Choose the format that matches your data:
     - **GeoPulse** - Restoring from GeoPulse backup
     - **Google Timeline** - Importing from Google Takeout
     - **OwnTracks** - Importing OwnTracks exports
     - **GPX** - Fitness trackers, Garmin devices
     - **GeoJSON** - GIS data exports
     - **CSV** - Custom data sources

3. **Upload Your File**
   - Click **Choose Export File**
   - Select your file (any size supported up to 2GB)

4. **Start Import**
   - Click **Start Import**
   - Watch the progress bar as your data uploads and processes
   - Timeline generation will start automatically after GPS data import

5. **Wait for Completion**
   - Small files (< 100MB): 1-5 minutes
   - Large files (> 500MB): 10-30 minutes
   - The system uses efficient streaming parsers, so any size works

### Detailed Import Documentation

For detailed information about all supported formats, options, and troubleshooting, see the complete [Import/Export Data](/docs/user-guide/interacting-with-data/import-export) guide.

### What If I Don't Have Existing Data?

No problem! Skip to **Step 2** to configure GPS sources and start collecting new location data.

---

## Step 2: Configure GPS Sources

Now that your existing data is imported (or if you're starting fresh), you need to configure GPS sources to continuously track your location.

### Choose Your GPS Tracking App

GeoPulse supports multiple GPS tracking apps. Choose the one that works best for you:

| App | Platform | Best For | Battery Impact |
|-----|----------|----------|----------------|
| **OwnTracks** | iOS, Android | Full-featured tracking, highly customizable | Medium |
| **Overland** | iOS | Simple setup, reliable iOS tracking | Low |
| **GPS Logger** | Android | Lightweight, battery-efficient | Low |
| **Dawarich** | iOS, Android | Privacy-focused, API key authentication | Low-Medium |
| **Home Assistant** | Any | Smart home integration, existing HA users | Varies |

### Quick Setup Process

1. **Get Your Endpoint URL**
   - Navigate to **Menu → Location Sources**
   - Click **Add New Source**
   - Choose your tracking app
   - Copy the provided endpoint URL

2. **Install the Tracking App**
   - Download your chosen app from the App Store or Google Play
   - Open the app and go to settings

3. **Configure the App**
   - Paste your GeoPulse endpoint URL
   - Configure tracking frequency (recommended: 1-5 minutes)
   - Enable battery optimization settings
   - Start tracking!

4. **Verify Data Flow**
   - Wait a few minutes
   - Check **Menu → GPS Data** to see incoming location points
   - Timeline items will be generated automatically

### Detailed Setup Guides

For step-by-step instructions for each app, see:
- [OwnTracks Setup Guide](/docs/user-guide/gps-sources/owntracks)
- [Overland Setup Guide](/docs/user-guide/gps-sources/overland)
- [GPS Logger Setup Guide](/docs/user-guide/gps-sources/gps_logger)
- [Dawarich Setup Guide](/docs/user-guide/gps-sources/dawarich)
- [Home Assistant Integration](/docs/user-guide/gps-sources/home_assistant)
- [All GPS Sources Overview](/docs/user-guide/gps-sources/overview)

:::info Multiple Devices?
You can track multiple devices! Create a separate location source for each device (phone, tablet, car tracker, etc.). GeoPulse will combine all data into a unified timeline.
:::

### Advanced: Data Mirroring

If you're already using another tracking service (like Dawarich) and want to send data to both services simultaneously without changing your app configuration multiple times, you can set up traffic mirroring.

**Benefits:**
- Test GeoPulse alongside your existing tracking solution
- Send location data to multiple services simultaneously
- Maintain redundancy across platforms

[Learn about Data Mirroring with OwnTracks](/docs/user-guide/gps-sources/data-mirroring)

---

## Step 3: Explore Your Data

Now that you have data in GeoPulse (either imported or from GPS sources), let's explore the main features!

### Timeline - Your Location History

The **Timeline** is your chronological location history, automatically organized into trips and stays.

**How to use:**
1. Navigate to **Menu → Timeline**
2. Browse your location history by date
3. Click any trip or stay to see details
4. Filter by date range, location, or activity type

**What you'll see:**
- 🚗 **Trips** - When you were moving (with routes on map)
- 🏠 **Stays** - When you were stationary (home, work, restaurants)
- 📍 **Addresses** - Automatic reverse geocoding for locations
- ⏱️ **Duration & Distance** - How long and how far you traveled

[Learn more about Timeline features](/docs/user-guide/core-features/timeline)

### Dashboard - Statistics Overview

The **Dashboard** provides insights about your location patterns and activity.

**Navigate to:** Menu → Dashboard

**What you'll see:**
- **Total distance** traveled (today, this week, this month)
- **Most visited places** and time spent
- **Activity breakdown** - walking, driving, cycling
- **Travel patterns** over time
- **Recent activity** summary

[Learn more about Dashboard](/docs/user-guide/core-features/dashboard)

### Journey Insights - Deep Analysis

**Journey Insights** provides detailed analysis of your trips and travel patterns.

**Navigate to:** Menu → Journey Insights

**Features:**
- Trip-by-trip analysis
- Speed and route efficiency metrics
- Comparison between time periods
- Activity type breakdowns
- Export trip data

[Learn more about Journey Insights](/docs/user-guide/core-features/journey-insights)

### Rewind - Visual Timeline Playback

**Rewind** Offers a captivating journey through your past location data, transforming it into insightful summaries and visualisations. Discover patterns, revisit significant places, and understand your activity trends over different periods.

**Navigate to:** Menu → Rewind

[Learn more about Rewind](/docs/user-guide/core-features/rewind)

### Managing Places

Save frequently visited locations as **Places** for quick reference and better organization.

**Navigate to:** Menu → Places

**Features:**
- Mark favorite locations
- Categorize places (home, work, gym, etc.)
- Automatically recognize future visits
- View visit history for each place

[Learn more about Managing Places](/docs/user-guide/core-features/managing-places)

---

## Next Steps: Advanced Features

Now that you're familiar with the basics, explore these advanced features:

### AI Assistant

Enable AI-powered features for intelligent insights and natural language queries about your location history.

[Configure AI Assistant](/docs/user-guide/personalization/ai-assistant-settings)

### Location Sharing with Friends

Share your location with friends and see where they are in real-time (privacy-controlled).

[Learn about Friends feature](/docs/user-guide/social-and-sharing/friends)

### Custom Map Tiles

Customize the map appearance with satellite imagery, terrain maps, or custom styles.

[Set up Custom Map Tiles](/docs/user-guide/personalization/custom-map-tiles)

### Photo Integration (Immich)

Connect your Immich instance to see photos alongside your timeline.

[Configure Immich Integration](/docs/user-guide/personalization/immich-integration)

### Backup Your Data

Regularly export your data to ensure you never lose your location history.

**Quick backup:**
1. Navigate to **Menu → Import/Export Data**
2. Select **Export** tab
3. Choose **GeoPulse** format
4. Select all data types
5. Choose **All Time** date range
6. Click **Start Export**
7. Download the backup file when ready

[Learn more about Backup & Restore](/docs/system-administration/maintenance/backup-restore)

---

## Common Questions

**Q: How often should my GPS app send location updates?**
A: For good timeline accuracy, configure your GPS app to send updates every 1-5 minutes. More frequent updates provide better trip detection but use more battery.

**Q: Why don't I see trips on my timeline yet?**
A: Timeline generation can take a few minutes after importing data or receiving new GPS points. Check the Timeline Generation progress in the import status.

**Q: Can I use multiple GPS tracking apps?**
A: Yes! Create separate location sources for each app. GeoPulse will merge all data intelligently.

**Q: How much data can GeoPulse handle?**
A: GeoPulse uses efficient streaming parsers and can handle years of location history. Import files up to 2GB are supported (200MB default, configurable).

**Q: Will importing data delete my existing location history?**
A: Only if you enable "Replace existing data in time range." Otherwise, new data is merged with existing data (slower but safer).

**Q: How do I increase the 200MB upload limit?**
A: Administrators can increase the limit by setting `CLIENT_MAX_BODY_SIZE` in the deployment configuration. See [Frontend Configuration](/docs/system-administration/configuration/frontend).

---

## Getting Help

If you run into issues:

1. **Check the documentation** - Most topics have detailed guides
2. **Review troubleshooting sections** - Common issues have solutions
3. **Report issues** - Visit [GitHub Issues](https://github.com/tess1o/geopulse/issues)

## What's Next?

- ✅ Data imported or GPS source configured
- ✅ Timeline showing your location history
- ✅ Dashboard providing insights

You're all set! GeoPulse will now continuously track your location (from configured sources) and provide rich insights into your movements and travel patterns.

Explore the User Guide sections to learn about advanced features, or dive into Configuration & Administration if you're managing GeoPulse for multiple users.

Happy tracking! 🗺️
