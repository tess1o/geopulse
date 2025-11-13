---
title: Using OwnTracks with GeoPulse
description: A step-by-step guide on how to configure the OwnTracks app to send location data to GeoPulse via HTTP or MQTT.
---

# Using OwnTracks with GeoPulse

[OwnTracks](https://owntracks.org/) is a popular open-source location tracking application for iOS and Android. GeoPulse supports integration with OwnTracks through two different connection methods: **HTTP** and **MQTT**.

This guide provides the necessary steps to configure both GeoPulse and the OwnTracks app.

---

## Step 1: Configure GeoPulse

First, create a new location source in GeoPulse to receive data from your device.

1.  In your GeoPulse account, navigate to **Settings -> Location Sources**.
2.  Click **Add New Source** and select **OwnTracks**.
3.  Choose your desired **Connection Type**:
    *   **HTTP:** A simple and direct web request from your phone to GeoPulse. Recommended for most users.
    *   **MQTT:** A more complex but potentially more real-time method that requires a separate MQTT broker. See [Deployment Guide](/docs/getting-started/deployment/docker-compose) how to install GeoPulse with MQTT broker
4.  Enter a unique **Username** and a strong **Password**. You will need these credentials for the OwnTracks app.
5.  Click **Save** to create the new location source.

---

## Step 2: Configure the OwnTracks App

After setting up the source in GeoPulse, open the OwnTracks app on your mobile device and configure it based on the connection type you chose.

### HTTP Configuration

This is the simplest method to get started.

1.  In the OwnTracks app, go to **Preferences -> Connection**.
2.  Set the **Mode** to `HTTP`.
3.  In the **Host** field, enter the full URL for your GeoPulse OwnTracks endpoint. You can find this URL on the **Location Sources** page in GeoPulse. It will look like this:
    ```
    https://geopulse.yourdomain.com/api/owntracks
    ```
4.  Enable **Authentication**.
5.  Enter the **Username** and **Password** you created in GeoPulse.

### MQTT Configuration

This method requires that you have the GeoPulse MQTT broker enabled and accessible from the internet.

1.  In the OwnTracks app, go to **Preferences -> Connection**.
2.  Set the **Mode** to `MQTT`.
3.  Configure the following connection details:
    *   **Host:** Enter the public domain or IP address of your GeoPulse server (do not include `http://` or `https://`).
    *   **Port:** `1883` (or your custom MQTT port if you have changed it).
    *   **Authentication:** Enable this and enter the **Username** and **Password** you created in GeoPulse.
    *   **TLS:** Ensure that TLS/SSL is **disabled**, as the default GeoPulse broker does not use it.

Once saved, the OwnTracks app will begin sending location updates to your GeoPulse account, which will be automatically processed to build your timeline.
