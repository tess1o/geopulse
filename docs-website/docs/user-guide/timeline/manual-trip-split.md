---
title: Split a Trip with a Stay
description: Insert a missed intermediate stay into a trip as Trip -> Stay -> Trip.
---

# Split a Trip with a Stay

Use a manual split when GeoPulse recorded one continuous trip but you actually stopped somewhere meaningful. It replaces
that trip with **Trip -> Stay -> Trip** and saves a manual override, so the inserted stay is retained when GeoPulse
regenerates the timeline.

## Create a Split

1. Open the Timeline or Trip Workspace and open a trip's context menu (right-click, or long-press on touch devices).
2. Select **Split trip with stay...**.
3. Click the intermediate stop on the trip map. GeoPulse snaps the selection to nearby recorded GPS data and proposes a
   stay window.
4. Review or adjust the stay start, stay end, and optional place name. The dialog previews the resulting three events.
5. Select **Save Split**.

GeoPulse immediately replaces the original trip with the two trips and the inserted stay. Leave the place name empty to
let GeoPulse resolve it automatically when possible.

## Selection Rules

The selected stop must be an intermediate point in the trip, not its start or end. It must be close to recorded GPS data
within the configured stay radius (with a minimum of 150 metres), and the selected point must fall inside the stay time
window. The stay must be at least 60 seconds long, remain inside the original trip, and not overlap another stay or data
gap.

These checks prevent a manual split from inventing a stop that the GPS trace cannot support.

## Undo a Split

Open the inserted stay's context menu and choose **Undo Manual Trip Split**. Confirm the action to remove the override
and regenerate the affected timeline segment using normal automatic detection.

## When to Use It

This is best for a real stop missed because the track was sparse or detection thresholds were conservative. For changing
how a trip was travelled, use the trip's **Change movement type...** action instead. For a broad detection issue, adjust
your timeline preferences and regenerate rather than splitting many trips by hand.

