---
title: Reverse Geocoding
description: How GeoPulse resolves location names and how users can manage and reconcile reverse geocoding data.
---

# Reverse Geocoding

GeoPulse uses **reverse geocoding** to convert GPS coordinates into meaningful location names (e.g., “Kyiv City Center” or “Berlin Hauptbahnhof”).  
This information is displayed in **Timelines**, **Reports**, **Dashboard**, and **Journey Insights**.

Reverse geocoding behavior is determined by:

- **System-wide configuration** (providers, primary/failover settings — see  
  [Reverse Geocoding Configuration](../system-configuration/reverse-geocoding-config.md))
- **Favorite Locations**, which always override geocoding results (see  
  [Favorite Locations](./favorite-locations.md))

This page explains how GeoPulse resolves names and how users can manage stored reverse geocoding data.

---

## How GeoPulse Resolves Location Names

When the Timeline detects a **stay** (visit) at a specific location, GeoPulse tries to determine the best name for that place.  
The resolution order is:

### **1. Favorite Locations**
If the coordinate falls within a **Favorite Point** or **Favorite Area**, the favorite name is used immediately.  
(Highest priority)

### **2. Cached Reverse Geocoding Results**
GeoPulse stores all reverse geocoding results in the database.  
If a matching entry already exists, it is reused — avoiding additional requests to external providers.

### **3. Primary Reverse Geocoding Provider**
If the database contains no result:
- GeoPulse queries the **primary** configured provider.
- If successful, the result is saved for future use (cached).

### **4. Failover Provider**
If the primary provider fails:
- GeoPulse automatically tries the **failover** provider.
- Successful results are stored in the database.

### **5. Fallback: Unknown Location**
If both providers fail:
- GeoPulse assigns the default label **“Unknown location”**.

---

## Managing Reverse Geocoding Data

Users can view and maintain reverse geocoding results using the:

**Reverse Geocoding Management page (`/app/geocoding-management`)**

This page allows users to:
- Search stored geocoding results
- Reconcile (refresh/update) results from providers
- Edit stored entries manually

> **Important:**  
> Currently, all changes on this page affect **all users**. (A future update may introduce per-user overrides.)

---

## Features of the Management Page

### ✅ 1. Search Reverse Geocoding Records
You can search by:
- Provider name
- Location name
- City
- Country

Useful when inspecting or cleaning up incorrect or outdated entries.

---

### ✅ 2. Reconcile Selected Records
Select one or multiple entries and press **Reconcile Selected**.

A dialog will appear asking you to choose a **Reverse Geocoding Provider**.

GeoPulse will:
- Fetch updated data from the selected provider
- Update the **Display Name**, **City**, and **Country** fields
- Automatically refresh all affected **timelines** across the system

This is useful when:
- You originally used provider **A**
- You later switched to provider **B**
- You want your existing data to match the new provider

---

### ✅ 3. Reconcile All
Reconciles **every stored geocoding entry** using the selected provider.

Use this when performing a full migration to a different reverse geocoding provider.

> ⚠️ **Warning:**  
> This operation may take time depending on the number of entries and your provider’s performance/rate limits.

---

### ✅ 4. Manual Editing
Click **Edit** on any record to manually update:
- Display Name
- City
- Country

This is useful when providers return incorrect or unclear names and you want to fix them manually.

---

## Performance and Processing Notes

- Reconciliation jobs may run for a long time depending on your provider, rate limits, and number of records.
- All updated results are immediately applied to **all user timelines**.
- Favorite Locations still remain the highest priority and will override any geocoding change.

---

## Related Documentation

- **Global Settings:**  
  [Reverse Geocoding Configuration](../system-configuration/reverse-geocoding-config.md)

- **User Overrides:**  
  [Favorite Locations](./favorite-locations.md)