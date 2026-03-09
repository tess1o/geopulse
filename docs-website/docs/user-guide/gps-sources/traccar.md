---
title: Using Traccar Position Forwarding with GeoPulse
description: A step-by-step guide on how to configure Traccar Position Forwarding (JSON) to send location data to GeoPulse.
---

# Using Traccar Position Forwarding with GeoPulse

[Traccar](https://www.traccar.org/) is a GPS tracking platform that can forward received positions to external services.

This guide shows how to configure GeoPulse as a destination using Traccar **Position Forwarding** with `forward.type=json`.

---

## Step 1: Configure GeoPulse

First, create a location source in GeoPulse that will receive forwarded positions.

1.  In your GeoPulse account, navigate to **Settings -> Location Sources**.
2.  Click **Add New Source** and select **Traccar**.
3.  Enter a secure **Access Token**. Traccar will use this token in the `Authorization` header.
4.  Click **Save**.

---

## Step 2: Configure Traccar Position Forwarding

Edit your Traccar `traccar.xml` file and add (or update) the following entries:

```xml
<entry key='forward.enable'>true</entry>
<entry key='forward.type'>json</entry>
<entry key='forward.url'>https://geopulse.yourdomain.com/api/traccar</entry>
<entry key='forward.header'>Authorization: Bearer YOUR_CONFIGURED_TOKEN</entry>
```

Replace:

*   `https://geopulse.yourdomain.com/api/traccar` with your actual GeoPulse URL.
*   `YOUR_CONFIGURED_TOKEN` with the token created in GeoPulse (Step 1).

After saving `traccar.xml`, restart Traccar so the new forwarding settings are applied.

For all Position Forwarding options, see the official Traccar documentation: [https://www.traccar.org/forward/](https://www.traccar.org/forward/).
