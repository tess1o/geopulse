---
title: Favorites and Geocoding
description: Learn how to manage place names using Favorite Locations and understand the reverse geocoding process.
---

# Managing Places: Favorites and Geocoding

GeoPulse gives you powerful tools to manage how locations are named in your timeline. You can set custom names for places you visit often using **Favorite Locations**, and for all other places, GeoPulse uses a process called **Reverse Geocoding** to provide meaningful names.

---

## Favorite Locations

Favorite Locations are the best way to keep your timeline organized and easy to read. By assigning a custom name to a location (e.g., "Home", "Office", "Gym"), you ensure that any visit to that place is always labeled correctly, overriding any automatic naming.

There are two types of favorites you can create:

*   **Favorite Point:** A single, specific location on the map. All your GPS points within **75 meters** of this point will be associated with this favorite. This is perfect for smaller, well-defined places.
*   **Favorite Area:** A rectangular zone that you draw on the map. Any GPS points that fall **inside this rectangle** will be associated with this favorite. This is ideal for larger areas where you might move around, such as a university campus, a large park, or a shopping mall.

### The Favorites Management Page

The central hub for managing your favorites is the **Favorites Management** page, accessible from the main application menu. This page provides a comprehensive set of tools:

*   **Interactive Map:** A full-screen map that visually displays all your Favorite Points and Areas.
*   **Filter and Search:** Quickly find favorites by filtering by type (Point or Area) or searching by name, city, or country.
*   **Detailed Table:** A sortable list of all your favorites with key details, along with actions to manage them.

### How to Create a Favorite

There are three primary ways to create a new Favorite Location:

**1. From the Timeline Page Map**

You can quickly add a favorite directly from the interactive map on your Timeline page.

1.  Open your **Timeline** page.
2.  **Right-click** anywhere on the map where you want to add a favorite.
3.  A context menu will appear. Choose one of the following:
    *   **Add point to favorites:** To create a Favorite Point at the clicked location.
    *   **Add area to favorites:** To begin drawing a Favorite Area. Click and drag on the map to define the rectangular area.
4.  A dialog box will appear. Enter a descriptive **name** for your new favorite and click **Save**.

**2. From the Favorites Management Page**

This is the most direct way to add a new favorite from scratch.

1.  Navigate to the **Favorites Management** page.
2.  On the map, **right-click** where you want to add your favorite.
3.  A context menu will appear. Choose one of the following:
    *   **Add to Favorites:** To create a Favorite Point at the location you clicked.
    *   **Add an area to Favorites:** To begin drawing a Favorite Area. Click and drag on the map to draw the rectangle that defines your area.
4.  A dialog box will appear. Enter a descriptive **name** for your new favorite and click **Save**.

**3. From a Place's Detail Page**

You can easily "promote" any location you've visited to a Favorite.

1.  From your Timeline or another page, click on a location to navigate to its **Place Details** page.
2.  If the location is not already a favorite, you will see an **"Add to Favorites"** button in the header.
3.  Click the button, give the location a custom name, and save it. This creates a new Favorite Point at that location's coordinates.

### Editing and Deleting Favorites

All editing and deleting is done from the **Favorites Management** page.

1.  Find the favorite you wish to modify in the table.
2.  In the **Actions** column, click the appropriate button:
    *   **Edit (pencil icon):** Allows you to change the name of the favorite.
    *   **Delete (trash icon):** Permanently removes the favorite.

> **Important:** Creating or deleting a favorite will automatically trigger a **timeline regeneration**. This is necessary because changing a favorite can affect how past stays and trips are named and organized. The process is automatic and runs in the background.

---

## Understanding Reverse Geocoding

For any location that is not covered by one of your favorites, GeoPulse uses a process called **reverse geocoding** to convert raw GPS coordinates into a human-readable address (e.g., “Kyiv City Center” or “Berlin Hauptbahnhof”).

### How GeoPulse Resolves Location Names

When your timeline identifies a **stay** at a location, the system follows a clear priority order to name that place:

1.  **Favorite Locations (Highest Priority):** If the stay's coordinates fall within one of your Favorite Points or Areas, the custom favorite name is always used.

2.  **Cached Reverse Geocoding Results:** GeoPulse saves all successful geocoding results. If the location has been identified before, the system reuses the stored name for speed and efficiency.

3.  **Primary Reverse Geocoding Provider:** If the location is new, GeoPulse queries the primary provider you have configured in the system settings.

4.  **Failover Provider:** If the primary provider fails or returns no result, the system automatically tries the configured failover provider.

5.  **Fallback: Unknown Location:** If all other methods fail, the stay is labeled **“Unknown location”**.

### Managing Cached Geocoding Data

Advanced users can view and maintain the cached reverse geocoding results from the **Reverse Geocoding Management page** (`/app/geocoding-management`). This page allows you to search, refresh (reconcile), and manually edit stored geocoding results.

> **Important:** Currently, all changes on this page affect **all users** of the GeoPulse instance.

Key features of this page include:
-   **Search Records:** Find entries by provider, location name, city, or country.
-   **Reconcile Selected Records:** Refresh one or more entries using a specific provider. This is useful if you change providers and want to update existing data.
-   **Reconcile All:** Refresh **every** stored entry. Use with caution, as this can be a long-running process.
-   **Manual Editing:** Manually update the display name, city, or country for any record.