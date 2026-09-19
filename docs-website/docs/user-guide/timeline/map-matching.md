---
title: Map Matching
description: Clean up trip paths by matching GPS traces to roads and paths.
---

# Map Matching

Map Matching makes trip routes easier to read by aligning noisy GPS points with the roads and paths in OpenStreetMap.
Instead of showing every GPS wobble, GeoPulse can display a cleaner route that follows the way you most likely traveled.

This is a display feature. Your original GPS points remain unchanged, and GeoPulse still uses the raw data for timeline
detection, trip classification, exports, and analytics.

## When to Use It

Map Matching is useful when your trips look jagged, drift into nearby streets, cut corners, or cross buildings because
GPS signal quality was poor. It is especially helpful for walking, running, cycling, motorcycle, and car trips.

It is not used for every movement type. Train, flight, boat, and unknown trips keep their raw GPS paths because snapping
them to a road or footpath would usually be misleading.

## Enabling Map Matching

Your GeoPulse administrator must enable Map Matching for the instance and connect GeoPulse to a Valhalla service first.
Valhalla is the routing service GeoPulse uses to match GPS traces to OpenStreetMap roads and paths.

After the administrator has enabled it:

1. Open **Profile** from your avatar menu.
2. Go to **Display Settings**.
3. Turn on **Map Matching**.
4. Optionally choose movement types under **Show raw GPS for**.
5. Save your changes.

If the switch is disabled, Map Matching is not currently available on your GeoPulse instance. Ask your administrator to
enable Valhalla-backed Map Matching. Administrators can use the
[Valhalla Map Matching setup guide](/docs/system-administration/configuration/valhalla-map-matching).

## What You Will See

When Map Matching is enabled for your profile, trip paths on the timeline map can be shown as matched routes instead of
raw GPS lines.

When you select a matched trip on the map, Map Controls includes a comparison button that can overlay the original raw
GPS path together with the matched path. Use this when you want to inspect how much the displayed route differs from the
recorded GPS trace. The comparison control is only shown when a matched trip is selected.

Some trips may still appear as raw GPS paths:

- Their current movement type is selected under **Show raw GPS for**. This also applies after manually changing a trip's
  movement type. GeoPulse hides matching progress, comparison controls, and Map Matching details for these trips, although
  matching may continue in the background.
- GeoPulse is still processing the match.
- The trip type is not supported for matching.
- Valhalla does not have map data for that region.
- The GPS trace is too short, too sparse, or too disconnected to match confidently.
- The Valhalla service is unavailable or returns an error.

In these cases, GeoPulse keeps the raw GPS path visible. It does not hide the trip or replace it with a low-confidence
route.

To see what happened to a specific trip, right-click the trip card (long-press on a touch device) and choose **Map
matching details...**. It confirms when a route was refined and when that happened, tells you if the trip is still
waiting to be matched, or explains why it was not — for example that the routing engine has no map data for the area or
that the trip type is not supported.

## Future Trips

New trips may appear as raw GPS paths first. If automatic matching is enabled by your administrator, GeoPulse waits until
the trip is stable before sending it to Valhalla. This avoids repeatedly matching a trip that is still changing as new GPS
points arrive.

After processing finishes, the timeline map can update from the raw path to the matched route. If matching fails or is
skipped, the raw path remains.

## Past Trips

Older trips are not always matched immediately when the feature is enabled. Your administrator can run historical backfill
so GeoPulse gradually prepares matched routes for past trips.

If backfill has not reached a trip yet, opening a timeline page may still queue visible trips for matching on demand,
depending on your instance settings. While this happens, the raw path stays visible so the map remains usable.

When your administrator changes how matching works — for example the Valhalla server or the matching limits — past trips
keep the routes they already have. Each trip is matched again the next time you view it, and an administrator can update
all past trips at once instead of waiting for them to come up.

## Raw GPS Still Matters

Map Matching changes how a trip can look on the map, not what GeoPulse stores as your location history.

Your raw GPS data continues to control:

- Stay, trip, and gap detection
- Movement classification
- Timeline rebuilds
- Exports
- Dashboard and journey statistics

Think of Map Matching as a cleaner visual layer over the same underlying trip history.

## Related Guides

- [Understanding Your Timeline](/docs/user-guide/core-features/timeline)
- [Trip Detection](/docs/user-guide/timeline/trip_detection)
- [Travel Classification](/docs/user-guide/timeline/travel_classification)
- [Valhalla Map Matching setup](/docs/system-administration/configuration/valhalla-map-matching)
