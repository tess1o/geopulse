---
title: Mirroring to GeoPulse (Existing Dawarich Setup)
sidebar_label: Mirror Existing Dawarich
description: Add GeoPulse tracking to an existing Dawarich installation using Nginx Traffic Mirroring.
---

If you are already successfully sending OwnTracks data to a self-hosted Dawarich instance (e.g., `dawarich.mydomain.com`), you can configure Nginx to "mirror" that traffic to GeoPulse without changing the domain name on your phone.

## Prerequisites

* **Nginx Proxy Manager** handling SSL for your existing Dawarich domain.
* **Dawarich** is running (assumed container name: `dawarich_app`, port: `3000`).
* **GeoPulse** is running (assumed container name: `geopulse-ui`, port: `80`).
* All containers share the same Docker network.

## 1. Update OwnTracks App Configuration

Even though you are keeping the URL pointing to your Dawarich domain, you must update the authentication settings on your phone.

**Why?** * **Dawarich** ignores Basic Auth headers (it uses the `api_key` in the URL).
* **GeoPulse** requires Basic Auth headers.
* By sending **both**, both services will be happy.

1.  Open **OwnTracks** on your mobile device.
2.  **URL:** Keep your existing URL (e.g., `https://dawarich.mydomain.com/api/v1/owntracks/points?api_key=YOUR_DAWARICH_KEY`).
3.  **Authentication:** Enable this setting.
    * **Username:** Enter your **GeoPulse** username.
    * **Password:** Enter your **GeoPulse** password.

## 2. Configure Nginx Proxy Manager

1.  Log in to Nginx Proxy Manager.
2.  Find the **existing** Proxy Host for `dawarich.mydomain.com` and click **Edit**.
3.  Go to the **Advanced** tab.
4.  Paste the following configuration into the **Custom Nginx Configuration** box.

:::warning Important: Check your Container Names
The code below assumes your Dawarich container is named `dawarich_app` and runs on port `3000`. If yours is different, update the `proxy_pass` line in the **primary** location block below.
:::

```nginx
# 1. Define the internal location for the MIRRORED request (GeoPulse)
location /mirror_to_geopulse {
    # Internal only - not accessible from outside
    internal;

    # Send the copy to GeoPulse
    # Note: We force the path to /api/owntracks which GeoPulse expects
    proxy_pass http://geopulse-ui:80/api/owntracks;

    # Pass the body (location data) and headers (Auth)
    proxy_pass_request_body on;
    proxy_pass_request_headers on;

    # Standard Proxy Headers
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

# 2. Override the main location to enable mirroring
# This block replaces the standard GUI routing for this specific path
location / {
    # Trigger the mirror
    mirror /mirror_to_geopulse;
    mirror_request_body on;

    # Send original request to Dawarich (Primary)
    # REPLACE 'dawarich_app:3000' if your container name differs
    proxy_pass http://dawarich_app:3000;

    # Pass headers so Dawarich receives the original Host/IP data
    proxy_pass_request_headers on;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}