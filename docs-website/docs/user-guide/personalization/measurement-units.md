---
title: Measurement Units
description: Configure distance and temperature units in GeoPulse.
---

# Measurement Units

GeoPulse lets you choose distance and temperature units independently. This supports users who prefer miles for distance
and Celsius for weather, without changing the way data is stored.

---

## Default Setting

By default, GeoPulse displays values with:

- **Meters** and **kilometers** for distance
- **km/h** for speed
- **Celsius** for temperature

You can switch distance to:

- **Feet** and **miles** for distance
- **mph** for speed

You can switch temperature to **Fahrenheit** separately.

Administrators can change the default assigned to newly created users in **Admin → Settings → System** or with
`GEOPULSE_USER_DEFAULT_DISTANCE_UNIT` and `GEOPULSE_USER_DEFAULT_TEMPERATURE_UNIT`. Existing users keep their own profile preference.

---

## Changing Measurement Units

1. Open the **Profile** page:  
   Navigate to **Menu → Profile** or go directly to `https://geopulse.mydomain.com/app/profile`
2. Find the **Distance Unit** and **Temperature Unit** settings.
3. Select your preferred options.
4. Save your changes. The new units will be applied immediately across all pages.

---

## Where It Applies

Your chosen units affect the following sections of GeoPulse:

- **Timeline** – Distances shown in trip summaries
- **Dashboard** – Total distance, average speed, and movement statistics
- **Timeline Reports** – Distance-based insights and summaries
- **Journey Insights** – Trip analytics and speed breakdowns
- **Rewind** – Daily and historical playback statistics
- **GPS Data** – Point-by-point distances and speeds
- **Weather** – Temperatures use your temperature unit; wind and precipitation follow your distance unit

---

## Data Storage

All GPS data is **always stored in meters** in the database, regardless of your selected display units.  
Changing units only affects how data is displayed — not how it’s saved or processed.

---

## Example

| Distance Unit | Temperature Unit | Distance Example | Speed Example | Temperature Example |
|---------------|------------------|------------------|---------------|---------------------|
| Kilometers    | Celsius          | 12.4 km          | 65 km/h       | 22°C                |
| Miles         | Celsius          | 7.7 mi           | 40 mph        | 22°C                |
| Miles         | Fahrenheit       | 7.7 mi           | 40 mph        | 72°F                |

---

:::info
Switching display units is safe — no data will be lost or recalculated. It’s purely a display
preference applied per user.
:::
