---
title: Using GPSLogger with GeoPulse
description: A step-by-step guide on how to configure the GPSLogger Android app to send location data to GeoPulse.
---

# Using GPSLogger with GeoPulse

**GPSLogger** is a versatile and battery-efficient location tracking app for Android. While GeoPulse does not have a dedicated "GPSLogger" source type, you can easily configure it to send data by using the **OwnTracks HTTP** integration.

This guide will walk you through the required configuration in both GeoPulse and the GPSLogger app.

---

## Step 1: Configure GeoPulse

First, you need to create a new location source in GeoPulse that will receive the data from GPSLogger.

1.  In your GeoPulse account, navigate to **Settings -> Location Sources**.
2.  Click **Add New Source** and select **OwnTracks**.
3.  Set the integration mode to **HTTP**.
4.  Enter a unique **Username** and a strong **Password**. You will need these credentials for the GPSLogger app, so be sure to save them.
5.  Click **Save** to create the new location source. GeoPulse is now ready to receive data at your unique OwnTracks endpoint.

---

## Step 2: Configure GPSLogger

Next, configure the GPSLogger app on your Android device to send data to the endpoint you just created.

1.  In the GPSLogger app, navigate to **Logging details** and enable **Log to custom URL**.
2.  Tap on **URL** and enter the endpoint address for your GeoPulse instance, followed by `/api/owntracks`. For example:
    ```
    https://geopulse.yourdomain.com/api/owntracks
    ```
    > Be sure to replace `geopulse.yourdomain.com` with the actual domain of your GeoPulse server.

3.  Scroll down and configure the following HTTP request settings:

    *   **HTTP Method:** Set to `POST`.

    *   **HTTP Body:** Paste the following JSON structure into the body. This format maps GPSLogger's variables to the fields that GeoPulse expects from an OwnTracks device.
        ```json
        {
            "_type" : "location",
            "t": "u",
            "acc": "%ACC",
            "alt": "%ALT",
            "batt": "%BATT",
            "bs": "%ISCHARGING",
            "lat": "%LAT",
            "lon": "%LON",
            "tst": "%TIMESTAMP",
            "vel": "%SPD"
        }
        ```

    *   **HTTP Headers:** Add the following header:
        ```
        Content-Type: application/json
        ```
        Optionally, you can add a device ID header (`X-Limit-D`) to give your device a specific name in GeoPulse. If you omit this, it will appear as "Unknown Device".
        ```
        X-Limit-D: my-android-phone
        ```

    *   **Authentication:** Enable **Basic Authentication** and enter the **Username** and **Password** that you created in GeoPulse in Step 1.

4.  Save your settings in GPSLogger.

Once saved, GPSLogger will begin sending location updates to your GeoPulse account according to the frequency you have set in the app. These points will be automatically processed to build your timeline.
