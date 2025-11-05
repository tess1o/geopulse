---
title: Introduction
description: Introduction to GeoPulse — a self-hosted location tracking and analysis platform.
---

# Introduction

**GeoPulse** is a self-hosted location tracking and analysis platform that turns raw GPS data into meaningful insights — all securely stored on your own server.

It integrates with popular tracking apps like **OwnTracks**, **Overland**, **Dawarich**, **GPSLogger**, and **HomeAssistant**, providing real-time synchronization, timeline visualization, and deep analytics.

---

## Key Features

### 🗺️ GPS Data Integration
Connect multiple GPS sources using HTTP, MQTT, or file imports (GPX, GeoJSON, or Google Timeline).  
GeoPulse automatically merges and cleans incoming location data.

### 📍 Timeline and Maps
Visualize your movement history with categorized **stays**, **trips**, and **data gaps**.  
See photos from Immich directly on the map, and explore your routes by date range.

### 📊 Analytics
Get detailed travel statistics, such as total distance, visited countries and cities, and journey insights powered by AI.

### 🤖 AI Assistant
Ask natural-language questions about your travel patterns — powered by any OpenAI-compatible API key you provide.

### 🔐 Sharing and Privacy
Share your real-time location or timeline safely using password-protected and time-limited links.

### ⭐ Personalization
Customize map tiles, define favorite places, configure time zones, measurement units, and AI assistant preferences — per user.

---

## Next Steps

- [Deployment](./deployment/docker-deployment.md) – Install GeoPulse using Docker or Kubernetes
- [System Configuration](system-configuration/authentication-config.md) – Set up authentication and integrations