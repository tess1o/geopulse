---
title: Using Dawarich with GeoPulse
description: A step-by-step guide on how to configure the Dawarich app to send location data to GeoPulse.
---

# Using Dawarich with GeoPulse

[Dawarich](https://dawarich.com/) is a privacy-focused location tracking app that allows you to send your location data to a custom server using an API key.

This guide will walk you through the required configuration in both GeoPulse and the Dawarich app.

---

## Step 1: Configure GeoPulse

First, you need to create a new location source in GeoPulse that will receive the data from Dawarich.

1.  In your GeoPulse account, navigate to **Settings -> Location Sources**.
2.  Click **Add New Source** and select **Dawarich**.
3.  Enter a secure and unique **API Key**. This key will be used to authorize requests from the Dawarich app.
4.  Click **Save** to create the new location source. Make sure to copy the generated API Key for the next step.

---

## Step 2: Configure the Dawarich App

After setting up the source in GeoPulse, configure the Dawarich app on your mobile device.

1.  In the Dawarich app, go to the settings or server configuration section.
2.  In the **Server URL** field, enter the endpoint address for your GeoPulse instance. You can find this URL on the **Location Sources** page in GeoPulse. It will look like this:
    ```
    https://geopulse.yourdomain.com/api/dawarich
    ```
    > Be sure to replace `geopulse.yourdomain.com` with the actual domain of your GeoPulse server.

3.  In the **API Key** field, paste the API Key that you created in GeoPulse in Step 1.

Once saved, the Dawarich app will begin sending location updates to your GeoPulse account, which will be automatically processed to build your timeline.
