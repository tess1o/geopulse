---
title: Using Overland with GeoPulse
description: A step-by-step guide on how to configure the Overland app to send location data to GeoPulse.
---

# Using Overland with GeoPulse

[Overland](https://overland.p3k.app/) is a simple, open-source location tracking app for iOS that sends data to a specified HTTP endpoint.

This guide will walk you through the required configuration in both GeoPulse and the Overland app.

---

## Step 1: Configure GeoPulse

First, you need to create a new location source in GeoPulse that will receive the data from Overland.

1.  In your GeoPulse account, navigate to **Settings -> Location Sources**.
2.  Click **Add New Source** and select **Overland**.
3.  Enter a secure and unique **Access Token**. This token will be used to authorize requests from the Overland app.
4.  Click **Save** to create the new location source. Make sure to copy the generated token for the next step.

---

## Step 2: Configure the Overland App

After setting up the source in GeoPulse, configure the Overland app on your iOS device.

1.  In the Overland app, go to the settings screen.
2.  In the **Endpoint URL** field, enter the endpoint address for your GeoPulse instance. You can find this URL on the **Location Sources** page in GeoPulse. It will look like this:
    ```
    https://geopulse.yourdomain.com/api/overland
    ```
    > Be sure to replace `geopulse.yourdomain.com` with the actual domain of your GeoPulse server.

3.  In the **Access Token** field, paste the token that you created in GeoPulse in Step 1.

Once saved, the Overland app will begin sending location updates to your GeoPulse account, which will be automatically processed to build your timeline.
