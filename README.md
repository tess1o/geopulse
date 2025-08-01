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

GeoPulse transforms raw GPS data from tracking apps like OwnTracks and Overland into organized timelines and insights.
It automatically categorizes your location data into stays and trips, providing a clear view of your movement patterns
while keeping everything on your own server.

<div align="center">
  <img src="docs/images/timeline.png" alt="GeoPulse Interactive Timeline" width="800"/>
  <p><em>Interactive timeline with automatic stay/trip detection and map visualization</em></p>
</div>

## Getting Started

### Quick Start
- **[Deployment Guide](docs/DEPLOYMENT_GUIDE.md)** - Docker deployment instructions
- **[Configuration Guide](docs/CONFIGURATION.md)** - Geopulse configuration (optional)
- **[Setup Guide](docs/SETUP.md)** - Complete setup instructions for new users

## Features

**GPS Data Integration**

- Works with OwnTracks (HTTP only) and Overland tracking apps
- Real-time data sync and import/export capabilities
- Flexible authentication (username/password or token-based)

**Timeline and Maps**

- Automatic categorization of GPS data into stays and trips
- Interactive maps showing your complete movement history
- Flexible date range viewing (single day to months of data)
- Real-time location display for current day

**Analytics**

- Dashboard with distance traveled and visit statistics
- Journey insights showing countries and cities visited
- Movement pattern analysis and activity tracking
- Most visited locations with detailed counts

**Social Features**

- Connect with friends to share locations
- Real-time friend location sharing
- Privacy controls for what and when you share
- Email-based friend invitations

**Sharing**

- Public share links for non-registered users
- Time-limited and password-protected access
- Temporary, revocable sharing links

**Places Management**

- Save favorite locations and areas
- Add favorites directly from the map
- Search and organize saved places

**Customization**

- Adjustable timeline sensitivity settings
- Data export in multiple formats
- Dark/light themes with system preference detection
- Mobile-responsive design

## Architecture

- **Backend**: Java with Quarkus framework
- **Database**: PostGIS (PostgreSQL with geographic extensions)
- **Frontend**: Vue.js 3 with PrimeVue components
- **Maps**: Leaflet with OpenStreetMap
- **Deployment**: Docker Compose

## Compatible Apps

**OwnTracks** (iOS, Android, Desktop)

- Username/password authentication
- High-precision tracking with offline support
- Configure HTTP endpoint in app settings

**Overland** (iOS)

- Token-based authentication
- Battery-efficient background tracking
- Add GeoPulse endpoint URL in app

## Privacy and Security

GeoPulse keeps your data under your control:

- Self-hosted - your data stays on your server
- No third-party tracking or external data sharing
- Secure JWT authentication with HTTPS required
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