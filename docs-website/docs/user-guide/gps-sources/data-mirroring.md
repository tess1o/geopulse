---
title: Mirroring OwnTracks Data to GeoPulse
sidebar_label: Data Mirroring
description: Configure Nginx to mirror OwnTracks location data to GeoPulse alongside other tracking services.
---

# Mirroring OwnTracks Data to GeoPulse

This guide explains how to configure Nginx Proxy Manager (NPM) to mirror OwnTracks location data to GeoPulse while maintaining compatibility with existing tracking services like Dawarich. Traffic mirroring allows you to send location data to multiple services simultaneously without modifying your mobile app configuration multiple times.

## When to Use Traffic Mirroring

Traffic mirroring is useful when you want to:
- Test GeoPulse alongside your existing tracking solution without disrupting it
- Send location data to multiple tracking services simultaneously
- Gradually migrate from another service to GeoPulse
- Maintain redundancy across multiple tracking platforms

## Prerequisites

Before proceeding, ensure your environment meets the following requirements:

* **Nginx Proxy Manager** is installed and running
* **GeoPulse** is running (assumed container name: `geopulse-ui`, port: `80`)
* All containers share a Docker network and can communicate with each other

---

## Scenario 1: New Setup - Mirror OwnTracks to Both GeoPulse and Dawarich

Use this approach when setting up a new tracking system from scratch and want to send data to both services from the beginning.

### Prerequisites

In addition to the general prerequisites above:

* **Dawarich** is running (assumed container name: `dawarich_app`, port: `3000`)

### 1. DNS & Client Configuration

#### DNS Setup
Create a DNS record (e.g., `gps.mydomain.com`) pointing to your Nginx Proxy Manager server IP.

#### OwnTracks App Configuration
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

### 2. Nginx Proxy Manager Configuration

Log in to your Nginx Proxy Manager interface and add a new **Proxy Host**.

#### Details Tab
* **Domain Names:** `gps.mydomain.com`
* **Scheme:** `http`
* **Forward Hostname / IP:** `dawarich_app`
* **Forward Port:** `3000`
* **Cache Assets:** Disabled
* **Block Common Exploits:** Optional (Recommended)

#### SSL Tab
* **SSL Certificate:** Request a new Let's Encrypt certificate (or use an existing one).
* **Force SSL:** Enabled

#### Advanced Tab
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
```

**Save** the configuration and test by sending a location update from your OwnTracks app. Both services should receive the data.

---

## Scenario 2: Existing Dawarich Setup - Add GeoPulse Mirror

Use this approach when you already have a working Dawarich installation and want to add GeoPulse without changing your domain or OwnTracks configuration significantly.

### Prerequisites

In addition to the general prerequisites above:

* **Dawarich** is already configured and receiving data (e.g., via `dawarich.mydomain.com`)
* **Nginx Proxy Manager** is already handling SSL for your Dawarich domain

### 1. Update OwnTracks App Configuration

Even though you are keeping the URL pointing to your Dawarich domain, you must update the authentication settings on your phone.

**Why?**
* **Dawarich** ignores Basic Auth headers (it uses the `api_key` in the URL).
* **GeoPulse** requires Basic Auth headers.
* By sending **both**, both services will be happy.

1.  Open **OwnTracks** on your mobile device.
2.  **URL:** Keep your existing URL (e.g., `https://dawarich.mydomain.com/api/v1/owntracks/points?api_key=YOUR_DAWARICH_KEY`).
3.  **Authentication:** Enable this setting.
    * **Username:** Enter your **GeoPulse** username.
    * **Password:** Enter your **GeoPulse** password.

### 2. Configure Nginx Proxy Manager

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
```

**Save** the configuration and test by sending a location update from your OwnTracks app. Both services should receive the data.

---

## Troubleshooting

### Both Services Not Receiving Data

1. **Check container names and ports:** Ensure `geopulse-ui:80` and `dawarich_app:3000` (or your custom names) are correct.
2. **Verify network connectivity:** Test that NPM can reach both containers:
   ```bash
   docker exec -it nginx-proxy-manager curl http://geopulse-ui:80/health
   docker exec -it nginx-proxy-manager curl http://dawarich_app:3000
   ```
3. **Check Nginx logs:** View NPM logs for error messages about the mirror location.

### GeoPulse Not Receiving Data

1. **Verify Basic Auth is enabled:** In OwnTracks, ensure Authentication is turned ON with your GeoPulse credentials.
2. **Check credentials:** Ensure the username and password match your GeoPulse account.
3. **Test GeoPulse endpoint directly:** Use curl to test the endpoint:
   ```bash
   curl -u "username:password" -X POST https://geopulse.mydomain.com/api/owntracks \
     -H "Content-Type: application/json" \
     -d '{"_type":"location","lat":52.0,"lon":13.0,"tst":1234567890}'
   ```

### Dawarich Not Receiving Data

1. **Verify API key:** Ensure the `api_key` in your OwnTracks URL is correct.
2. **Check Dawarich logs:** Look for authentication errors or rejected requests.

---

## Security Considerations

* **HTTPS Only:** Always use HTTPS with valid SSL certificates for location data transmission.
* **Strong Passwords:** Use strong, unique passwords for both GeoPulse and Dawarich accounts.
* **Network Isolation:** Keep your tracking services on isolated Docker networks when possible.
* **Monitor Logs:** Regularly review Nginx and application logs for suspicious activity.

---

## Alternative: Mirroring Without Dawarich

If you only want to mirror to GeoPulse without Dawarich, simply configure the primary `proxy_pass` to point to GeoPulse and remove the mirror directive. However, for a single destination, mirroring adds unnecessary complexity—use a standard proxy configuration instead.
