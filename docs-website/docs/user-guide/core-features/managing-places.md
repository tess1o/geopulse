---
title: Favorites and Geocoding
description: Learn how to manage place names using Favorite Locations and understand the reverse geocoding process.
---

# Managing Places: Favorites and Geocoding

GeoPulse gives you powerful tools to manage how locations are named in your timeline. You can set custom names for places you visit often using **Favorite Locations**, and for all other places, GeoPulse uses a process called **Reverse Geocoding** to provide meaningful names.

---

## Favorite Locations

Favorite Locations let you assign custom names to specific places on your **Timeline Map**. They are the easiest way to keep your Timeline organized and meaningful, as they always override any automatic results.

There are two types of favorites:

### Favorite Point

A single location on the map. All GPS points within **75 meters** of this point will use the custom name you assign. Use this to label frequently visited places like **Home**, **Office**, or **Gym**.

### Favorite Area

A rectangular zone you draw on the map. All GPS points **inside that rectangle**, and within **15 meters outside its border**, will use the same custom name. Use this for larger areas such as **parks**, **malls**, or **city squares**.

### Add a Favorite Point or Area

1.  Open your **Timeline Map**.
2.  Right-click anywhere on the map.
3.  Choose one of the following options:
    *   **Add to Favorites** – to create a Favorite Point.
    *   **Add an Area to Favorites** – to create a Favorite Area.
4.  (For areas only) Draw a rectangle covering the desired region.
5.  Enter a **custom name** and click **Save**.

### Edit or Delete a Favorite Location

1.  Open your **Timeline Map**.
2.  Click the **bookmark icon** in the top-right corner to show all your Favorite Locations.
3.  Find the Favorite you want to change.
4.  Right-click on it and choose:
    *   **Edit** – to update its name or area.
    *   **Delete** – to remove it.

---

## Understanding Reverse Geocoding

For any location that is not marked as a favorite, GeoPulse uses **reverse geocoding** to convert GPS coordinates into human-readable location names (e.g., “Kyiv City Center” or “Berlin Hauptbahnhof”). This information is displayed throughout the application.

### How GeoPulse Resolves Location Names

When the Timeline detects a **stay** (visit) at a specific location, GeoPulse tries to determine the best name for that place. The resolution order is:

1.  **Favorite Locations**
    If the coordinate falls within a **Favorite Point** or **Favorite Area**, the favorite name is used immediately. *(Highest priority)*

2.  **Cached Reverse Geocoding Results**
    GeoPulse stores all reverse geocoding results in the database. If a matching entry already exists, it is reused to improve performance and avoid unnecessary requests to external providers.

3.  **Primary Reverse Geocoding Provider**
    If the database contains no result, GeoPulse queries the **primary** configured provider. If successful, the result is saved for future use.

4.  **Failover Provider**
    If the primary provider fails, GeoPulse automatically tries the **failover** provider.

5.  **Fallback: Unknown Location**
    If all providers fail, GeoPulse assigns the default label **“Unknown location”**.

### Managing Cached Geocoding Data

Advanced users can view and maintain the cached reverse geocoding results from the **Reverse Geocoding Management page** (`/app/geocoding-management`). This page allows you to search, refresh (reconcile), and manually edit stored geocoding results.

> **Important:** Currently, all changes on this page affect **all users**.

Key features of this page include:
-   **Search Records:** Find entries by provider, location name, city, or country.
-   **Reconcile Selected Records:** Refresh one or more entries using a specific provider. This is useful if you change providers and want to update existing data.
-   **Reconcile All:** Refresh **every** stored entry. Use with caution, as this can be a long-running process.
-   **Manual Editing:** Manually update the display name, city, or country for any record.
