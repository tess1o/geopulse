---
title: Using Colota with GeoPulse
description: A step-by-step guide on how to configure Colota to send location data to GeoPulse.
---

# Using Colota with GeoPulse

**Colota** can send location updates to GeoPulse over HTTP using a simple JSON payload. This guide explains how to configure both GeoPulse and your Colota instance.

---

## Step 1: Configure GeoPulse

First, create a new location source in GeoPulse that will receive the data from Colota.

1.  In your GeoPulse account, navigate to **Settings -> Location Sources**.
2.  Click **Add New Source** and select **Colota**.
3.  Enter a unique **Username** and a strong **Password**. You will need these credentials for Colota, so save them.
4.  Click **Save** to create the source. GeoPulse is now ready to receive data at `/api/colota`.

---

## Step 2: Configure Colota

After creating the source in GeoPulse, configure Colota to post to the endpoint below:

1.  Set the endpoint URL to:
    ```
    https://geopulse.yourdomain.com/api/colota
    ```
    Replace `geopulse.yourdomain.com` with the public URL of your GeoPulse instance.

2.  Use **Basic Authentication** with the credentials from Step 1.

3.  Use the JSON payload format:
    ```json
    {
      "lat": 48.135,
      "lon": 11.582,
      "acc": 12,
      "alt": 519,
      "vel": 0,
      "batt": 85,
      "bs": 2,
      "tst": 1704067200,
      "bear": 180.5
    }
    ```

    Fields:
    - `lat`, `lon` - coordinates
    - `acc` - accuracy in meters
    - `alt` - altitude in meters
    - `vel` - speed in m/s (GeoPulse converts to km/h internally)
    - `batt` - battery level
    - `bs` - battery status
    - `tst` - Unix timestamp (seconds)
    - `bear` - bearing in degrees

4.  Save/apply the settings in Colota and send a test update.

Once sent, GeoPulse should start receiving points on the same basis as other HTTP sources.
