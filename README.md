# GeoPulse

<p align="center">
  <img src="frontend/public/geopulse-logo.svg" alt="GeoPulse Logo" width="200"/>
</p>

**A self-hosted location tracking and analysis platform**

[![License: BSL 1.1](https://img.shields.io/badge/License-Business_Source_1.1-red)](LICENSE)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](docs/DEPLOYMENT_GUIDE.md)
[![Self-Hosted](https://img.shields.io/badge/Self--Hosted-✓-green.svg)](#)
[![Privacy First](https://img.shields.io/badge/Privacy-First-green.svg)](#)

---

GeoPulse transforms raw GPS data from tracking apps like OwnTracks, Overland, Dawarich or HomeAsssitant into organized
timelines and
insights. It automatically categorizes your location data into stays and trips, providing a clear view of your movement
patterns while keeping everything on your own server. GeoPulse has integration with Immich server so you can view your
photos directly on the timeline map.

<div align="center">
  <img src="docs/images/timeline.png" alt="GeoPulse Interactive Timeline" width="800"/>
  <p><em>Interactive timeline with automatic stay/trip detection and map visualization</em></p>
</div>

## 🚀 Getting Started

GeoPulse can be deployed using **Docker Compose** or **Kubernetes (Helm)**.

### Quick Start with Docker

Deploy GeoPulse in under 5 minutes:

**Choose your deployment scenario:**

- 🏠 **Local Deployment** - Deploy on your local machine (zero configuration required)
- 🌐 **Production** - Deploy on server with domain and reverse proxy

Both scenarios optionally support MQTT for real-time OwnTracks integration.

**👉 [Docker Deployment Instructions](https://tess1o.github.io/geopulse/docs/getting-started/deployment/docker-compose)**

Once deployed:

- **Local Deployment**: http://localhost:5555
- **Production**: https://your-domain.com

### Kubernetes Deployment

For Kubernetes clusters you can install GeoPulse using the provided Helm chart.

```shell
helm repo add geopulse https://tess1o.github.io/geopulse/charts
helm repo update
helm install my-geopulse geopulse/geopulse
```

The interactive script will guide you through the process.

**👉 [View the Full Kubernete Guide](https://tess1o.github.io/geopulse/docs/getting-started/deployment/kubernetes-helm)**
**👉 [View the Full Helm Guide](https://tess1o.github.io/geopulse/docs/getting-started/deployment/helm)**
---

## Features

**GPS Data Integration**

- Works with OwnTracks (HTTP or MQTT), Overland, Dawarich, HomeAssistant and GPSLogger tracking apps
- Real-time data sync with GPS tracking apps.
- Manual import from Google Timeline, GPX, GeoJSON or OwnTracks JSON files

**Timeline and Maps**

- Automatic categorization of GPS data into stays, trips and data gap when no GPS is available
- Interactive maps showing your complete movement history
- Immich integration to show your photos on the timeline map
- Flexible date range viewing (single day to months of data)

**Analytics**

- Dashboard with distance traveled and visit statistics
- Journey insights showing countries and cities visited
- Movement pattern analysis and activity tracking
- AI-powered location insights and intelligent data analysis

**AI Chat Assistant**

- Ask questions about your location data and get intelligent insights
- Natural language queries about travel patterns, visits, and habits
- Use any OpenAI compatible service (each user needs to provide their own API Key)

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
- Reverse geocoding using `Nomatim`, `Google Maps` or `Mapbox API`.

**Customization**

- Adjustable timeline sensitivity settings individually for each user
- **Custom map tiles support** - Use satellite imagery or different map styles from providers like MapTiler, Mapbox, and more
- Data export in multiple formats
- Dark/light themes in UI
- Mobile-responsive design

## 📚 Complete Documentation

### Detailed Guides

- **[Deployment Guide](https://tess1o.github.io/geopulse/docs/getting-started/deployment/docker-compose)** - Comprehensive deployment options and troubleshooting
- **[Configuration Guide](https://tess1o.github.io/geopulse/docs/system-administration/initial-setup)** - Advanced configuration and environment variables

## Architecture

- **Backend**: Java with Quarkus framework in Native mode
- **Database**: PostGIS (PostgreSQL with geographic extensions)
- **Frontend**: Vue.js 3 with PrimeVue components and chart.js
- **Maps**: Leaflet with OpenStreetMap
- **Deployment**: Docker Compose, Kubernetes (Helm Charts)
- **MQTT broker**: Mosquitto (optional, used for OwnTracks MQTT integration)

## Compatible Apps

**OwnTracks** (iOS, Android) - HTTP and MQTT
**Overland** (iOS)
**Dawarich** (iOS)
**Home Assistant**
**GPSLogger**

## Authentication

GeoPulse supports multiple authentication methods:

**Traditional Authentication**
- Username/password registration and login
- Secure password hashing and JWT tokens

**OIDC/SSO Authentication** 
- OpenID Connect support for enterprise SSO
- Compatible with Google, Microsoft, Keycloak, Auth0, and more
- Multiple providers can be configured simultaneously
- Account linking for users with existing accounts

See the [Configuration Guide](docs/CONFIGURATION.md#oidc-authentication) for OIDC setup instructions.

## Privacy and Security

GeoPulse keeps your data under your control:

- Self-hosted - your data stays on your server
- No third-party tracking or external data sharing
- Secure HTTP-only cookies with JWT tokens for API access
- OIDC/SSO integration for enterprise environments
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
<summary>Timeline (light theme)</summary>

![Timeline (light theme)](docs/images/timeline_light_theme.png)

</details>

<details>
<summary>Journey Insights</summary>

![Journey Insights](docs/images/journey_insights.png)

</details>


<details>
<summary>Rewind</summary>

![Rewind](docs/images/rewind.png)

</details>

<details>
<summary>Configuration & Setup</summary>

![Location Sources](docs/images/location_sources.png)
![Timeline Preferences](docs/images/timeline_preferences.png)

</details>

<details>
<summary>Share Location</summary>

![Share Links](docs/images/share_links.png)
</details>

<details>
<summary>Data Management</summary>

![Export Page](docs/images/export_page.png)
![Import Page](docs/images/import_page.png)
![GPS Data](docs/images/gps_data.png)
</details>

<details>
<summary>AI Assistant</summary>

![AI Chat](docs/images/ai_chat.png)

</details>

<details>
<summary>User Profile</summary>

![User Profile](docs/images/user_profile.png)

</details>

## License

GeoPulse is licensed under the **Business Source License 1.1 (BSL 1.1)**.

- Source code is available publicly.  
- Free for **personal, educational, and non-commercial use**.  
- **Commercial use is strictly prohibited** without a separate commercial license.

See the [LICENSE](./LICENSE) file for full details.  

For commercial licensing, please contact: kerriden1@gmail.com
