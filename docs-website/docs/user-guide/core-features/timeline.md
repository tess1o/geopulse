# Understanding Your Timeline: How It Works

This document explains how GeoPulse automatically transforms the raw GPS data from your devices into a meaningful, easy-to-read timeline of your daily activities.

## The Building Blocks: Stays, Trips, and Gaps

Your timeline is composed of three main types of events:

*   **Stays:** Periods when you are stationary at a specific location, like your home, office, or a restaurant.
*   **Trips:** Periods when you are moving between two locations.
*   **Data Gaps:** Periods when your GPS device was not reporting its location, so your whereabouts are unknown.

The timeline generation process is essentially an algorithm that analyzes your stream of GPS points and decides which of these three events is happening at any given moment.

---

## How We Define a "Stay"

The system identifies a "Stay" by looking for a cluster of GPS points. For a collection of points to be considered a stay, two main conditions must be met:

1.  **Proximity:** All the points in the cluster must be close to each other. They must all fall within a "bubble" of a certain size. This "bubble" size is a configurable setting.
2.  **Duration:** You must remain within this "bubble" for a minimum amount of time. This minimum duration is also a configurable setting.

For example, if your settings are a 100-meter radius and a 10-minute duration, the system will only create a "Stay" event after it sees you have remained within a 100-meter area for at least 10 minutes.

A special rule applies to **Favorite Areas**. If you have defined a Favorite Area (like "University Campus"), all GPS points within that entire area are treated as being at the same location, making it easier to register a single, continuous stay even if you are walking around a large area.

---

## From Stay to Trip: Detecting Movement

The system decides that a stay has ended and a new trip has begun when it receives a GPS point that is **outside the "bubble"** of the current stay location.

Once you have moved a significant distance away from the cluster of points that defined your stay, the algorithm finalizes the stay event and transitions into "trip mode." The first GPS point that was outside the stay area becomes the starting point of your new trip.

---

## From Trip to Stay: Detecting Arrival

Detecting the end of a trip is more complex than starting one. The system needs to be sure you've actually arrived somewhere and aren't just paused temporarily (e.g., stopped at a long traffic light or stuck in traffic).

To solve this, the algorithm becomes stricter when it's in "trip mode." It looks for a **sustained stop**.

Instead of just one or two points, the system generally needs to see **at least 3 consecutive GPS points** that are:
1.  **Spatially Clustered:** All points are very close to each other (within the configured "bubble" radius).
2.  **Slow Moving:** The speed associated with the points is at or near zero.
3.  **Sustained in Time:** These clustered, slow points must span a minimum duration. This is controlled by separate, more sensitive time-based settings specifically for trip-ending detection.

Only when these stricter conditions are met will the algorithm conclude the trip has ended and transition back to "stay mode," beginning a new "Potential Stay."

---

## How We Determine Trip Type (e.g., Car vs. Walking)

Once a trip has been identified, the system analyzes its characteristics to classify the mode of transport. This is primarily based on the trip's **average speed** and **maximum speed**.

The system compares these speeds against configurable thresholds:

*   **Walking:** If the trip's average and maximum speeds are below the defined "walking" thresholds, it's classified as a `WALK`. The algorithm is slightly more lenient for very short trips, allowing for slightly higher speeds that might occur during a brisk walk.
*   **Car:** If the trip's average or maximum speed is above the defined "car" thresholds, it's classified as a `CAR`.
*   **Unknown:** If the speeds fall into a gray area between walking and driving, the trip may be classified as `UNKNOWN`.

This classification happens after the trip is complete and helps provide more context to your travel patterns in dashboards and reports.

---

## What is a "Data Gap"?

A data gap occurs when the system detects a large and unrealistic "jump" between two consecutive GPS points. For example, if one point is at your home at 2:00 PM and the very next point is 50 miles away at 4:00 PM, it's clear that data is missing in between.

The algorithm identifies a gap when the time elapsed between two points exceeds a configured threshold. Instead of drawing an incorrect straight line between these two distant points, it creates a "Data Gap" event on your timeline to signify that your location was not tracked during that period.

---

## Simplifying Your Timeline: Merging and Cleaning Up

After the initial timeline of stays and trips is generated, the system runs an additional "clean-up" step to make the timeline more logical and less cluttered. This is called **Timeline Merging**.

The main purpose is to merge stays at the same named location that are separated by short trips. For example, if your timeline shows:
1.  Stay at "Local Shopping Center"
2.  A 2-minute car trip
3.  Another Stay at "Local Shopping Center"

This likely represents you moving your car to a different parking spot. The merging process will combine these three events into a single, longer stay at the "Local Shopping Center," absorbing the short trip into the stay's duration.

This merge only happens if the trip between the two stays is very short, based on configurable time and distance thresholds. This feature helps to produce a cleaner, more intuitive timeline by removing insignificant movements.

---

## The Automatic Timeline Builder

The timeline generation process is fully automatic and designed to be both responsive and accurate.

### The Rebuild Process
A background job runs automatically every few minutes (typically 5 by default, but this is configurable). Instead of just adding new data to the end, this job performs a "smart rebuild":
1.  It finds the last confirmed stay on your timeline.
2.  It removes all timeline events (trips, stays, gaps) that occurred *after* the start of that last stay.
3.  It then re-processes all the GPS data from that point forward.

This "rewind and rebuild" approach is crucial for accuracy. It ensures that if you are in the middle of a long stay or a data gap, the duration of that event is always kept up-to-date. It also allows new GPS points to be correctly incorporated into the most recent activity.

### Other Triggers for Rebuilding
A full timeline rebuild can also be triggered by certain user actions that change how the algorithm interprets your data. This includes:
*   **Changing Timeline Preferences:** If you adjust a setting like Stay Radius or Minimum Stay Duration.
*   **Changing Favorite Locations:** Adding, deleting, or modifying a Favorite Location (especially a large Favorite Area) can change how past stays are detected, so a rebuild is necessary to apply the new context.

### Monitoring Timeline Generation Jobs

When you manually trigger a timeline rebuild (e.g., after changing preferences or managing favorites), a modal window will appear, showing the real-time status and progress of the timeline generation job.

From this modal window, or directly by navigating to `/app/timeline/jobs`, you can access a detailed **Timeline Jobs** page. This page provides:

*   **Current Job Status:** View the progress and details of any timeline generation job currently running.
*   **Job History:** A list of recent jobs that you have manually triggered or that were initiated by your actions (e.g., adding/deleting a favorite).
    *   **Note:** This history **does not** include the regular background jobs that run every few minutes.
    *   Job history is stored in memory for approximately 24 hours. If the application is restarted, this history will be cleared.

---

## Fine-Tuning Your Timeline: Key Settings Explained

You can customize the timeline generation algorithm by adjusting several key parameters in your Timeline Preferences.

*   **Stay Radius (Meters)**
    *   **What it is:** The size of the "bubble" used to detect stays.
    *   **How it affects your timeline:** A smaller radius is more sensitive and can distinguish between close-by locations (like two different shops in the same mall), but may create unwanted multiple stays if your GPS signal drifts. A larger radius is more stable but might merge distinct nearby locations into a single stay.

*   **Minimum Stay Duration (Minutes)**
    *   **What it is:** The minimum time you need to be stationary within the Stay Radius to create a "Stay" event.
    *   **How it affects your timeline:** A shorter duration will detect shorter stops, but may clutter your timeline with insignificant pauses. A longer duration ensures only meaningful stays are recorded.

*   **Trip Arrival Detection Duration (Seconds)**
    *   **What it is:** The minimum duration of a sustained stop required to end a trip.
    *   **How it affects your timeline:** This is a sensitivity setting for ending trips. A shorter duration makes the system end trips more quickly upon stopping, but increases the risk of splitting a single car ride into multiple trips due to traffic stops.

*   **Trip to Stay Point Count**
    *   **What it is:** An advanced setting for the number of stationary GPS points needed to end a trip (default is 3).
    *   **How it affects your timeline:** A lower value (e.g., 2) makes arrival detection more responsive but can mistake traffic stops for arrivals. A higher value (e.g., 4) makes trip-ending more robust but may miss very short stays after a trip.

*   **Enable Stay Merging**
    *   **What it is:** A toggle to turn the timeline merging feature on or off.
    *   **How it affects your timeline:** When enabled, the system will automatically clean up your timeline by merging stays at the same location that are separated by very short trips.

*   **Merge Max Distance (Meters) & Max Time Gap (Minutes)**
    *   **What they are:** The thresholds used by the merging feature. If a trip between two stays at the same location is shorter than this distance OR this time, the stays and trip will be merged.
    *   **How they affect your timeline:** Lower values will make merging less frequent, preserving more detail. Higher values will cause more aggressive merging, resulting in a simpler but less detailed timeline.

*   **Travel Classification Speeds (km/h)**
    *   **What they are:** A set of four advanced speed settings (Walking Max Average, Walking Max Peak, Car Min Average, Car Min Peak).
    *   **How they affect your timeline:** These values determine the speed thresholds used to classify trips as `WALK` or `CAR`. Adjusting them can help correctly classify your activities if your personal walking or driving speeds differ from the defaults.
