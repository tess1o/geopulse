# GeoPulse

<p align="center">
  <img src="frontend/public/geopulse-logo.svg" alt="GeoPulse Logo" width="180"/>
</p>

<h3 align="center">The self-hosted, source-available Google Timeline alternative.</h3>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-BSL_1.1-red" alt="License"></a>
  <a href="#deployment-options"><img src="https://img.shields.io/badge/Install-Options-blue.svg" alt="Installation options"></a>
  <img src="https://img.shields.io/badge/Self--Hosted-Yes-green.svg" alt="Self-Hosted">
  <img src="https://img.shields.io/badge/Privacy-First-green.svg" alt="Privacy First">
  <img src="https://img.shields.io/badge/Locales-EN%20%7C%20UK-blue.svg" alt="Supported Locales">
</p>

GeoPulse transforms raw GPS data from OwnTracks, Overland, Dawarich, GPSLogger, Home Assistant, Traccar, Colota and other sources into a
searchable timeline of stays, trips, and movement patterns. It runs fully on your own infrastructure and integrates with
**Immich**, **Memos**, and **Weather** so photos, notes, and conditions appear directly on your map history.

<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/timeline.webp">
    <img src="docs-website/static/img/screenshots/light/timeline.webp" alt="GeoPulse Timeline with route map and stay and trip cards" width="800">
  </picture>
  <p><em>Comprehensive timeline visualization with automatic trip classification.</em></p>
</div>

---

## Quick installation

```bash
# Create directory and download config
mkdir geopulse && cd geopulse
curl -L -o .env https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example
curl -L -o docker-compose.yml https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.yml

# Start
docker compose up -d
```

**Access:** [http://localhost:5555](http://localhost:5555)  
*Note: For production, review your `.env` for security-related settings first.*

Need MQTT support for OwnTracks, Kubernetes, Unraid, Proxmox or bare-metal installation? See the [deployment options](#deployment-options).

---

## Why GeoPulse

- **Self-hosted, no telemetry:** Your location data remains on your own infrastructure.
- **Open ecosystem:** Works with popular GPS apps (OwnTracks, Overland, GPSLogger, Home Assistant, Colota, Traccar) and tools like Immich, Memos, and Weather.
- **Full data ownership:** Import historical data and export your data in standard formats anytime.
- **Lightweight runtime:** Typically under 100MB RAM and under 1% CPU in regular usage.
- **Accessible & Localized:** Currently available in English and Ukrainian, ensuring an inclusive experience across different regions.

---

## Features

**Timeline & Analysis**

- **Smart Detection:** Automatically converts GPS points into stays, trips, and data gaps.
- **Custom Logic:** Fully configurable detection sensitivity and travel mode classification.
- **Deep Insights:** Analytics for distance, visit frequency, and movement patterns over time.
- **[Map Matching](https://geopulse.cc/docs/user-guide/timeline/map-matching):** Valhalla-backed route refinement makes noisy trip paths follow roads and paths while raw GPS remains authoritative.
- **[Panoramax](https://geopulse.cc/docs/system-administration/configuration/panoramax):** Optional public street-level imagery coverage and photo viewing on Timeline maps.
- **Immich Integration:** Photos from your library appear directly on your map timeline.
- **Memos Integration:** Timestamped notes from Memos can appear alongside your timeline.
- **Weather Integration:** Current weather enrichment is enabled by default for trips, stays, map layers, and journey insights; historical backfill is admin opt-in. See [Weather processing architecture](docs/WEATHER_PROCESSING.md).

**Sources & Syncing**

- **Real-time Tracking:** Supports OwnTracks (HTTP/MQTT), Overland, GPSLogger, Home Assistant, Traccar, Dawarich or Colota.
- **Universal Import:** Bulk import from Google Timeline, GPX, GeoJSON, OwnTracks exports, and CSV.

**AI Chat & MCP**

- **AI Chat:** Bring your own OpenAI-compatible key for AI-assisted insights.
- **[MCP Server](https://geopulse.cc/docs/api/mcp):** Read-only, API-token-authenticated tools for AI clients to query their timeline and permitted friend data.

**Sharing & Privacy**

- **Friends System:** Per-user visibility controls for live location and history.
- **Guest Access:** Shareable links with optional password protection and instant revocation.
- **Multi-user Ready:** Built-in invitations, roles, and admin audit logs.
- **Enterprise Auth:** OIDC/SSO support alongside standard username/password login.

**Platform & Performance**

- **Lightweight:** Typically under 100MB RAM and 1% CPU usage.
- **Self-Sovereign:** No telemetry, no analytics beacons, and no third-party tracking.
- **Data Freedom:** Full data export and per-account deletion support.
- **[Backup & Restore](https://geopulse.cc/docs/system-administration/maintenance/backup-restore):** Encrypted, password-protected full backups with manual restore and scheduled automatic backups.

---

## 📸 Feature Tour

<table>
<tr>
<td colspan="2" width="100%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/timeline_trip_selected.webp">
  <img src="docs-website/static/img/screenshots/light/timeline_trip_selected.webp" alt="Timeline with a selected car trip being replayed on the map" width="100%">
</picture>
<p align="center"><b>Timeline & route replay</b><br><sub>Stays, trips, and data gaps with travel modes, weather, and step-by-step trip replay.</sub></p>
</td>
</tr>
<tr>
<td colspan="2" width="100%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/location_analytics.webp">
  <img src="docs-website/static/img/screenshots/light/location_analytics.webp" alt="Location Analytics map with visited places" width="100%">
</picture>
<p align="center"><b>Location Analytics</b><br><sub>Explore every visit by map, city, and country.</sub></p>
</td>
</tr>
<tr>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/dashboard-card.webp">
  <img src="docs-website/static/img/screenshots/light/dashboard-card.webp" alt="Dashboard with distance activity charts" width="100%">
</picture>
<p align="center"><b>Dashboard</b><br><sub>Selected period, 7-day, and 30-day overviews with charts, top places, and route stats.</sub></p>
</td>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/rewind-card.webp">
  <img src="docs-website/static/img/screenshots/light/rewind-card.webp" alt="Rewind monthly summary" width="100%">
</picture>
<p align="center"><b>Rewind</b><br><sub>Monthly and yearly summaries of distance, trips, highlights, and heatmaps.</sub></p>
</td>
</tr>
<tr>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/journey_insight-card.webp">
  <img src="docs-website/static/img/screenshots/light/journey_insight-card.webp" alt="Journey Insights with travel statistics" width="100%">
</picture>
<p align="center"><b>Journey Insights</b><br><sub>Countries, cities, time patterns, weather, milestones, and badges.</sub></p>
</td>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/city_page-card.webp">
  <img src="docs-website/static/img/screenshots/light/city_page-card.webp" alt="City details page for Boston" width="100%">
</picture>
<p align="center"><b>City details</b><br><sub>Visit overview, top places, and every visit in a city.</sub></p>
</td>
</tr>
<tr>
<td colspan="2" width="100%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/trip_workspace.webp">
  <img src="docs-website/static/img/screenshots/light/trip_workspace.webp" alt="Trip Workspace with planned stops on the map" width="100%">
</picture>
<p align="center"><b>Trip Plans</b><br><sub>Plan stops with travel modes and routed legs, then compare the plan with what you actually visited.</sub></p>
</td>
</tr>
<tr>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/location_sources.webp">
  <img src="docs-website/static/img/screenshots/light/location_sources.webp" alt="Location Sources page with setup instructions" width="100%">
</picture>
<p align="center"><b>Location Sources</b><br><sub>Connect OwnTracks, Overland, GPSLogger, Home Assistant, Traccar, Dawarich, or Colota.</sub></p>
</td>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/gps_data-card.webp">
  <img src="docs-website/static/img/screenshots/light/gps_data-card.webp" alt="GPS Data page with raw location points" width="100%">
</picture>
<p align="center"><b>GPS Data</b><br><sub>Inspect, filter, and export raw location points.</sub></p>
</td>
</tr>
<tr>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/export-card.webp">
  <img src="docs-website/static/img/screenshots/light/export-card.webp" alt="Data export with formats and data types" width="100%">
</picture>
<p align="center"><b>Export & Import</b><br><sub>Full backups and GPX, GeoJSON, OwnTracks, or CSV exports with a date range.</sub></p>
</td>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/profile.webp">
  <img src="docs-website/static/img/screenshots/light/profile.webp" alt="Personal Settings page" width="100%">
</picture>
<p align="center"><b>Personal Settings</b><br><sub>Language, time zone, units, map appearance, and connected apps.</sub></p>
</td>
</tr>
<tr>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/admin_overview-card.webp">
  <img src="docs-website/static/img/screenshots/light/admin_overview-card.webp" alt="Administration overview with instance health" width="100%">
</picture>
<p align="center"><b>Administration</b><br><sub>Instance health, usage, users, backups, and audit logs.</sub></p>
</td>
<td width="50%" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/admin_settings-card.webp">
  <img src="docs-website/static/img/screenshots/light/admin_settings-card.webp" alt="System Settings with authentication options" width="100%">
</picture>
<p align="center"><b>System Settings</b><br><sub>Authentication, geocoding, weather, map matching, and more without restarts.</sub></p>
</td>
</tr>
<tr>
<td colspan="2" width="100%" align="center" valign="top">
<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs-website/static/img/screenshots/dark/mobile_timeline.webp">
  <img src="docs-website/static/img/screenshots/light/mobile_timeline.webp" alt="GeoPulse Timeline on a phone" width="320">
</picture>
<p align="center"><b>Mobile</b><br><sub>The web app works on phones and installs as a PWA.</sub></p>
</td>
</tr>
</table>


## Deployment Options

Choose the installation path that matches your environment:

| Installation type | Best for | Guide |
|-------------------|----------|-------|
| Docker Compose | Fastest path for local and single-server use | [Docker Compose Guide](https://geopulse.cc/docs/getting-started/deployment/docker-compose) |
| Unraid | Unraid NAS and homelab servers using Docker Compose | [Unraid Guide](https://geopulse.cc/docs/getting-started/deployment/unraid) |
| Proxmox VE LXC | Proxmox homelabs and VM hosts that prefer LXC containers | [Proxmox VE LXC Guide](https://geopulse.cc/docs/getting-started/deployment/proxmox-lxc) |
| Kubernetes / Helm | Managed clusters and production Kubernetes environments | [Kubernetes Quick Install](https://geopulse.cc/docs/getting-started/deployment/kubernetes-helm) |
| Helm values reference | Advanced Helm customization | [Helm Values Reference](https://geopulse.cc/docs/getting-started/deployment/helm-deployment) |
| Manual installation | Bare metal servers or VMs without Docker/Kubernetes | [Manual Installation Guide](https://geopulse.cc/docs/getting-started/deployment/manual-installation) |
| Environment configuration | Reviewing all runtime settings | [Environment Variables Reference](https://geopulse.cc/docs/getting-started/deployment/environment-variables) |

### Kubernetes / Helm Quick Install

```shell
helm repo add geopulse https://tess1o.github.io/geopulse/charts
helm repo update
helm install my-geopulse geopulse/geopulse
```

**Post-deployment steps:**

1. Create the first account to become admin automatically, or set `GEOPULSE_ADMIN_EMAIL` for an explicit admin email.
2. Finish setup in the Admin Panel.
3. See [Initial Setup Guide](https://geopulse.cc/docs/system-administration/initial-setup) for more.

---

## 📖 Docs & Next Steps

* **New users:** [Quick Start Guide](https://geopulse.cc/docs/getting-started/quick-start)
* **GPS setup:** [GPS Sources Overview](https://geopulse.cc/docs/user-guide/gps-sources/overview)
* **Deployment:** [Docker](https://geopulse.cc/docs/getting-started/deployment/docker-compose) | [Unraid](https://geopulse.cc/docs/getting-started/deployment/unraid) | [Proxmox](https://geopulse.cc/docs/getting-started/deployment/proxmox-lxc) | [Kubernetes](https://geopulse.cc/docs/getting-started/deployment/kubernetes-helm) | [Manual](https://geopulse.cc/docs/getting-started/deployment/manual-installation) | [Env Variables](https://geopulse.cc/docs/getting-started/deployment/environment-variables)
* **Administration:** [Admin Panel](https://geopulse.cc/docs/system-administration/configuration/admin-panel) | [OIDC/SSO](https://geopulse.cc/docs/system-administration/configuration/oidc-sso)
* **Maintenance:** [Backup & Restore](https://geopulse.cc/docs/system-administration/maintenance/backup-restore) | [Updating](https://geopulse.cc/docs/system-administration/maintenance/updating)
* **Full documentation:** [Documentation Portal](https://tess1o.github.io/geopulse/)

---

## 📜 License & Commercial Use

GeoPulse is licensed under the **Business Source License 1.1 (BSL 1.1)**.

- Free for personal, educational, and non-commercial use.
- Commercial use requires a separate commercial license.

See [LICENSE](./LICENSE) for full terms.  
For commercial licensing: `kerriden1@gmail.com`
