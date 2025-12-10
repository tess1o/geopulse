---
title: Mirroring OwnTracks to GeoPulse and Dawarich
sidebar_label: Mirror to GeoPulse
description: Configure Nginx Proxy Manager to send OwnTracks location data to both Dawarich and GeoPulse simultaneously.
---

This guide details how to configure Nginx Proxy Manager (NPM) to act as a reverse proxy that accepts location data from the OwnTracks app and mirrors that traffic to two different self-hosted services: **Dawarich** and **GeoPulse**.

## Prerequisites

Before proceeding, ensure your environment meets the following assumptions:

* **Nginx Proxy Manager** is installed and running.
* **Dawarich** is running with container name `dawarich_app` on port `3000`.
* **GeoPulse** is running with container name `geopulse-ui` on port `80`.
* All three containers (NPM, Dawarich, GeoPulse) share a Docker network and can communicate with each other.

## 1. DNS & Client Configuration

### DNS Setup
Create a DNS record (e.g., `gps.mydomain.com`) pointing to your Nginx Proxy Manager server IP.

### OwnTracks App Configuration
Configure your OwnTracks mobile application with the following settings:

1.  **Mode:** HTTP
2.  **URL:** `https://gps.mydomain.com/api/v1/owntracks/points?api_key=YOUR_DAWARICH_KEY`
    * *Replace `YOUR_DAWARICH_KEY` with your actual Dawarich access key.*
3.  **Authentication:** Enabled
    * **Username:** Your GeoPulse username
    * **Password:** Your GeoPulse password

:::info Authentication Logic
Dawarich authenticates via the `api_key` in the URL. GeoPulse authenticates via the Username/Password (Basic Auth) sent in the headers. This configuration allows both services to accept the request simultaneously.
:::

## 2. Nginx Proxy Manager Configuration

Log in to your Nginx Proxy Manager interface and add a new **Proxy Host**.

### Details Tab
* **Domain Names:** `gps.mydomain.com`
* **Scheme:** `http`
* **Forward Hostname / IP:** `dawarich_app`
* **Forward Port:** `3000`
* **Cache Assets:** Disabled
* **Block Common Exploits:** Optional (Recommended)

### SSL Tab
* **SSL Certificate:** Request a new Let's Encrypt certificate (or use an existing one).
* **Force SSL:** Enabled

### Advanced Tab
This is the critical step. We will manually define the Nginx location blocks to handle the traffic mirroring. Paste the following configuration into the **Custom Nginx Configuration** text area:

```nginx
# 1. Define the internal location for the MIRRORED request
location /mirror_to_geopulse {
  # 'internal' means this can only be called by Nginx, not by the public internet
  internal;

  # Set the destination for the mirrored request
  # Note: The URI path is preserved from the original request
  proxy_pass http://geopulse-ui:80/api/owntracks;

  # Make sure the body and headers (specifically Auth) are passed along
  proxy_pass_request_body on;
  proxy_pass_request_headers on;
  
  # Standard Proxy Headers
  proxy_set_header Host $host;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  proxy_set_header X-Forwarded-Proto $scheme;
}

# 2. Define the main location for the PRIMARY request
# This block overrides the default GUI settings for the root path
location / {
  # Tell Nginx to copy any request hitting this block to our mirror location
  mirror /mirror_to_geopulse;
  
  # Ensure the request body (location data) is also mirrored
  mirror_request_body on;

  # Set the PRIMARY destination (Dawarich)
  proxy_pass http://dawarich_app:3000;

  # Standard proxy headers to pass to the primary server
  proxy_pass_request_headers on;
  proxy_set_header Host $host;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  proxy_set_header X-Forwarded-Proto $scheme;
}