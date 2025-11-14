---
title: Architecture Overview
---

# Architecture Overview

GeoPulse is designed with a modern, cloud-native architecture, leveraging robust and scalable technologies to provide efficient location tracking and insights. Below is a high-level overview of the main components and their roles.

## Core Components

### Backend

The backend is built using **Java 25** and the **Quarkus framework**. It operates in **Native mode**, which provides extremely fast startup times and low memory consumption, making it ideal for cloud deployments and microservices architectures.

-   **Background Jobs**: Several background tasks, such as timeline processing and achievement calculations, are handled by the **Quarkus Scheduler**. These jobs are in-memory and are not persisted.

### Database

**PostGIS** serves as the primary database, extending **PostgreSQL** with powerful geographic and spatial capabilities. This allows GeoPulse to efficiently store, query, and analyze location-based data.

### Frontend

The user interface is developed with **Vue.js 3**. It utilizes **PrimeVue** components for a rich and interactive user experience, and **Chart.js** for rendering various data visualizations and trends.

### Maps

Mapping functionalities are powered by **Leaflet**, a lightweight and open-source JavaScript library for mobile-friendly interactive maps. It integrates with **OpenStreetMap** for base map data, with the option to configure and use custom map tiles.

## Deployment

GeoPulse is designed for flexible deployment across various environments:

-   **Docker Compose**: For local development and smaller-scale deployments, Docker Compose provides an easy way to set up and run all GeoPulse services.
-   **Kubernetes (Helm Charts)**: For production-grade, scalable, and resilient deployments, official Helm Charts are provided to streamline deployment and management on Kubernetes clusters.

## Optional Integrations

### MQTT Broker

**Mosquitto** is an optional, lightweight open-source message broker that implements the MQTT protocol. It can be integrated with GeoPulse to support real-time location updates from devices using the **OwnTracks MQTT integration**.