---
title: Measurement Unit
description: Configure Metric or Imperial measurement units in GeoPulse.
---

# Measurement Unit

GeoPulse supports both **Metric** and **Imperial** measurement systems. This setting controls how distances, speeds, and
other units are displayed throughout the app, without changing the way data is stored.

---

## Default Setting

By default, GeoPulse uses the **Metric system**, displaying values in:

- **Meters** and **kilometers** for distance
- **km/h** for speed

If you prefer the **Imperial system**, you can switch to:

- **Feet** and **miles** for distance
- **mph** for speed

---

## Changing the Measurement Unit

1. Open the **Profile** page:  
   Navigate to **Menu → Profile** or go directly to `https://geopulse.mydomain.com/app/profile`
2. Find the **Measurement Unit** setting.
3. Select your preferred option:
    - **Metric (default)**
    - **Imperial**
4. Save your changes. The new measurement system will be applied immediately across all pages.

---

## Where It Applies

Your chosen measurement unit affects the following sections of GeoPulse:

- **Timeline** – Distances shown in trip summaries
- **Dashboard** – Total distance, average speed, and movement statistics
- **Timeline Reports** – Distance-based insights and summaries
- **Journey Insights** – Trip analytics and speed breakdowns
- **Rewind** – Daily and historical playback statistics
- **GPS Data** – Point-by-point distances and speeds

---

## Data Storage

All GPS data is **always stored in meters** in the database, regardless of your selected measurement system.  
Changing units only affects how data is displayed — not how it’s saved or processed.

---

## Example

| Setting      | Distance Example | Speed Example |
|--------------|------------------|---------------|
| **Metric**   | 12.4 km          | 65 km/h       |
| **Imperial** | 7.7 mi           | 40 mph        |

---

:::info
Switching between Metric and Imperial units is safe — no data will be lost or recalculated. It’s purely a display
preference applied per user.
:::