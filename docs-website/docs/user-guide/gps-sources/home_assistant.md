---
title: Using Home Assistant with GeoPulse
description: Connect Home Assistant to GeoPulse with the GeoPulse integration, or send locations with a manual rest_command.
---

# Using Home Assistant with GeoPulse

There are two ways to connect Home Assistant to GeoPulse:

- **[GeoPulse integration](#option-1-geopulse-integration-recommended) (recommended).** A custom integration that works in both directions with one token:
  - forwards your `device_tracker` and `person` entities to GeoPulse, with a queue on disk that retries during outages and a service to backfill recorder history;
  - shows your current stay or trip, place, today's totals and friends in Home Assistant, and fires events for automations (geofence enter/leave, stay/trip started/ended, tracking lost/resumed).
- **[Manual `rest_command`](#option-2-manual-rest_command) setup.** A few lines of YAML that only send locations to GeoPulse.

---

## Option 1: GeoPulse integration (recommended)

Requirements: GeoPulse 2.0 or newer, Home Assistant 2026.3 or newer, and [HACS](https://hacs.xyz/).

### Step 1: Install the integration

1.  In Home Assistant, open **HACS**, choose **⋮ → Custom repositories**, and add `https://github.com/tess1o/geopulse-homeassistant` with type **Integration**.
2.  Find **GeoPulse** in HACS, install it, and restart Home Assistant.

### Step 2: Create a token in GeoPulse

1.  In GeoPulse, open **Location Sources** and click **Add New Source**.
2.  Choose **Home Assistant → GeoPulse integration → Create a new token**, and click **Add Source**.
3.  Copy the **URL** and the **Token** from the dialog. The token is shown only once.

GeoPulse creates an API token with the `read` and `gps:ingest` scopes and connects it to a new Home Assistant location
source. Locations sent with the token are stored under that source.

:::tip Using a token you already have
A token created under **Profile → Security → API tokens** with **Home Assistant** access can be used instead: choose
**Use an existing token** in step 2. Without a connected location source, the token can read your data but can't send
locations.
:::

### Step 3: Add the integration in Home Assistant

1.  Open **Settings → Devices & services → Add integration** and choose **GeoPulse**, or use
    [this link](https://my.home-assistant.io/redirect/config_flow_start/?domain=geopulse_ha).
2.  Paste the URL and the token.
3.  Choose the trackers to forward.

One integration entry is one GeoPulse account, which is one person, so choose only trackers that belong to that
person. For other family members, add the integration again with their own GeoPulse account and token.

To confirm data is arriving, open **Location Sources** in GeoPulse. The source card shows when data was last received
and why the last skipped point was skipped, if any.

The [integration's README](https://github.com/tess1o/geopulse-homeassistant#readme) lists all entities, events,
services and options, with automation examples.

---

## Option 2: Manual `rest_command`

This setup only sends locations from Home Assistant to GeoPulse. Locations aren't queued, so a point sent while
GeoPulse is unreachable is lost.

### Step 1: Configure GeoPulse

1.  In GeoPulse, open **Location Sources** and click **Add New Source**.
2.  Choose **Home Assistant → Source token**.
3.  Enter a secure and unique **Source token**. Home Assistant uses it to authorize its requests.
4.  Click **Add Source**.

---

### Step 2: Configure Home Assistant

Add the following to your Home Assistant configuration files (`configuration.yaml` and `automations.yaml`) to send
location data to GeoPulse.

### 2.1: Update `configuration.yaml`

Add the following `rest_command` entry to your `configuration.yaml` file. This defines a service that Home Assistant can call to send data to GeoPulse.

```yaml
rest_command:
  send_gps_data:
    url: "https://geopulse.yourdomain.com/api/v1/gps/ingest/home-assistant"
    method: POST
    headers:
      content-type: "application/json"
      Authorization: Bearer YOUR_CONFIGURED_TOKEN
    payload: >
      {
        "device_id": "iphone_16",
        "timestamp": "{{ now().isoformat() }}",
        "location": {
          "latitude": {{ state_attr('device_tracker.iphone_16', 'latitude') }},
          "longitude": {{ state_attr('device_tracker.iphone_16', 'longitude') }},
          "accuracy": {{ state_attr('device_tracker.iphone_16', 'gps_accuracy') | tojson }},
          "altitude": {{ state_attr('device_tracker.iphone_16', 'altitude') | tojson }},
          "speed": {{ state_attr('device_tracker.iphone_16', 'speed') | tojson }}
        },
        "battery": {
          "level": {{ (state_attr('device_tracker.iphone_16', 'battery_level') or states('sensor.iphone_16_battery_level')) | int(none) | tojson }}
        }
      }
```

**Important Replacements:**

*   Replace `https://geopulse.yourdomain.com` with the actual domain of your GeoPulse server.
*   Replace `YOUR_CONFIGURED_TOKEN` with the source token you entered in GeoPulse in Step 1.
*   Replace `iphone_16` with the actual `entity_id` of your device tracker in Home Assistant.

`latitude` and `longitude` are required. A location without them is skipped. `accuracy`, `altitude`, `speed` and `battery` are optional: the `tojson` filter sends `null` when Home Assistant doesn't report a value, and GeoPulse stores the field as empty. Avoid replacing missing values with `0`. An accuracy of `0` claims a perfect GPS fix and gets past the accuracy filter, and a speed of `0` makes every point look stationary.

:::tip Test the template first
Paste the `payload` template into **Developer Tools → Template** in Home Assistant to check that it renders valid JSON for your device before restarting.
:::

### 2.2: Update `automations.yaml`

Next, add an automation that triggers the `send_gps_data` service whenever your device tracker's state changes.

```yaml
- alias: Send GPS data to GeoPulse
  trigger:
    - platform: state
      entity_id: device_tracker.iphone_16 # Replace with your device tracker's entity_id
  action:
    - service: rest_command.send_gps_data
```

### 2.3: Restart Home Assistant

After modifying `configuration.yaml` and `automations.yaml`, you must restart your Home Assistant server for the changes to take effect.

Once Home Assistant restarts, it will begin sending location updates to your GeoPulse account, which will be automatically processed to build your timeline.

To confirm data is arriving, open **Location Sources** in GeoPulse. The Home Assistant card shows when data was last received and why the last skipped point was skipped, if any.

---

## Sending missed points in bulk

The GeoPulse integration already queues locations and sends them in batches. For your own scripts: the automation above sends one location at a time, and Home Assistant doesn't retry a request that fails. Clients that queue locations while GeoPulse is unreachable, or that backfill history from the Home Assistant recorder, can send them in one request:

```http
POST /api/v1/gps/ingest/home-assistant/batch
Authorization: Bearer YOUR_CONFIGURED_TOKEN
Content-Type: application/json

[
  {
    "device_id": "iphone_16",
    "timestamp": "2025-06-07T09:15:30+00:00",
    "location": { "latitude": 49.8419, "longitude": 24.0315, "accuracy": 8 },
    "battery": { "level": 82 }
  },
  {
    "device_id": "iphone_16",
    "timestamp": "2025-06-07T09:16:30+00:00",
    "location": { "latitude": 49.8426, "longitude": 24.0331, "accuracy": 12, "speed": 3.1 }
  }
]
```

*   Each item uses the same format as a single location, but `timestamp` is required. Items without one are skipped, since "now" would misplace a queued point.
*   Up to 1000 items per request. Larger requests are refused with `413`; split them into several requests.
*   Points already stored are skipped as duplicates, so it's safe to resend a batch after a failed request.
*   The response reports what happened to the batch:

```json
{ "accepted": 2, "saved": 2, "duplicate": 0, "filtered": 0 }
```
