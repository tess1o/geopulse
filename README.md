# GeoPulse

<div align="center">
  <img src="/frontend/public/geopulse-logo.svg" alt="GeoPulse Logo" width="200"/>

**A self-hosted location tracking and analysis platform**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](docs/DEPLOYMENT_GUIDE.md)
[![Self-Hosted](https://img.shields.io/badge/Self--Hosted-✓-green.svg)](#)
[![Privacy First](https://img.shields.io/badge/Privacy-First-green.svg)](#)
</div>

---

GeoPulse transforms raw GPS data from tracking apps like OwnTracks, Overland or Dawarich into organized timelines and
insights. It automatically categorizes your location data into stays and trips, providing a clear view of your movement
patterns while keeping everything on your own server. GeoPulse has integration with Immich server so you can view your
photos directly on the timeline map.

<div align="center">
  <img src="docs/images/timeline.png" alt="GeoPulse Interactive Timeline" width="800"/>
  <p><em>Interactive timeline with automatic stay/trip detection and map visualization</em></p>
</div>

## 🚀 Getting Started

Deploy GeoPulse in under 5 minutes with Docker:

**Choose your deployment:**

- 🏠 **Local Deployment** - Deploy on your local machine (zero configuration required)
- 🌐 **Production** - Deploy on server with domain and reverse proxy

Both scenarios optionally support MQTT for real-time OwnTracks integration.

**Simple deployment process:**

1. Download single `.env` configuration (works without changes for local Deployment)
2. Download `docker-compose` file
3. Run `docker compose up -d`

**👉 [Complete Step-by-Step Instructions](docs/DEPLOYMENT_GUIDE.md)**

Once deployed:

- **Local Deployment**: http://localhost:5555
- **Production**: https://your-domain.com

---

## Features

**GPS Data Integration**

- Works with OwnTracks (HTTP or MQTT), Overland and Dawarich tracking apps
- Real-time data sync with GPS tracking apps.
- Manual import from Google Takeout, GPX files

**Timeline and Maps**

- Automatic categorization of GPS data into stays and trips
- Interactive maps showing your complete movement history
- Immich integration to show your photos on the timeline map
- Flexible date range viewing (single day to months of data)
- Real-time location display for current day

**Analytics**

- Dashboard with distance traveled and visit statistics
- Journey insights showing countries and cities visited
- Movement pattern analysis and activity tracking

**Social Features**

- Connect with friends to share locations
- Real-time friend location sharing
- Privacy controls for what and when you share

**Sharing**

- Public share links for non-registered users
- Time-limited and password-protected access
- Temporary, revocable sharing links

**Places Management**

- Save favorite locations and areas
- Add/Edit/Delete favorite places directly on the map
- Reverse geocoding using Nomatim, Google Maps or Mapbox API.

**Customization**

- Adjustable timeline sensitivity settings individually for each user
- Data export in multiple formats
- Dark/light themes with system preference detection
- Mobile-responsive design

## 📚 Complete Documentation

### Detailed Guides

- **[Deployment Guide](docs/DEPLOYMENT_GUIDE.md)** - Comprehensive deployment options and troubleshooting
- **[Configuration Guide](docs/CONFIGURATION.md)** - Advanced configuration and environment variables
- **[Setup Guide](docs/SETUP.md)** - GPS apps setup and initial configuration

## Architecture

- **Backend**: Java with Quarkus framework
- **Database**: PostGIS (PostgreSQL with geographic extensions)
- **Frontend**: Vue.js 3 with PrimeVue components
- **Maps**: Leaflet with OpenStreetMap
- **Deployment**: Docker Compose
- **MQTT broker**: Mosquitto (optional, used for OwnTracks MQTT integration)

## Compatible Apps

**OwnTracks** (iOS, Android, Desktop)

- Configure username/password in app settings
- High-precision tracking with offline support
- Supports both HTTP and MQTT protocols

**Overland** (iOS)

- Configure with API token from GeoPulse
- Battery-efficient background tracking
- HTTP-based location transmission

**Dawarich** (iOS)

- Configure with API key from GeoPulse
- Simple setup and configuration
- HTTP-based location transmission

## Privacy and Security

GeoPulse keeps your data under your control:

- Self-hosted - your data stays on your server
- No third-party tracking or external data sharing
- Secure HTTP-only cookies with JWT tokens for API access
- Granular sharing controls
- Full data export capabilities

## Screenshots

### Timeline View

<div align="center">
  <img src="docs/images/timeline.png" alt="GeoPulse Interactive Timeline" width="800"/>
</div>

### Dashboard

<div align="center">
  <img src="docs/images/dashboard.png" alt="GeoPulse Dashboard" width="800"/>
</div>

### Additional Views

<details>
<summary>Journey Insights & Analytics</summary>

![Journey Insights](docs/images/journey_insights.png)

</details>

<details>
<summary>Configuration & Setup</summary>

![Location Sources](docs/images/location_sources.png)
![Timeline Preferences](docs/images/timeline_preferences.png)

</details>

<details>
<summary>Data Management</summary>

![Share Links](docs/images/share_links.png)
![Export Page](docs/images/export_page.png)
![Import Page](docs/images/import_page.png)

</details>

## Development

```bash
# Clone repository
git clone https://github.com/tess1o/geopulse
cd geopulse

# Backend development
cd backend
./mvnw quarkus:dev

# Frontend development  
cd frontend
npm install
npm run dev
```

## License

AGPL-3.0 with Non-Commercial Use Restriction  
See [LICENSE](./LICENSE) for details.