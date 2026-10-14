---
title: Using Home Assistant with GeoPulse
description: A step-by-step guide on how to configure Home Assistant to send location data to GeoPulse.
---

# Using Home Assistant with GeoPulse

This integration allows you to leverage Home Assistant's powerful device tracking capabilities to send location updates to GeoPulse. This is particularly useful if you already have device trackers configured in Home Assistant (e.g., for mobile apps, router-based tracking, etc.).

This guide will walk you through the required configuration in both GeoPulse and your Home Assistant setup.

---

## Step 1: Configure GeoPulse

First, you need to create a new location source in GeoPulse that will receive the data from Home Assistant.

1.  In your GeoPulse account, navigate to **Settings -> Location Sources**.
2.  Click **Add New Source** and select **Home Assistant**.
3.  Enter a secure and unique **Token**. This token will be used to authorize requests from Home Assistant.
4.  Click **Save** to create the new location source. Make sure to copy the generated token for the next step.

---

## Step 2: Configure Home Assistant

After setting up the source in GeoPulse, you will need to modify your Home Assistant configuration files (`configuration.yaml` and `automations.yaml`) to send location data to GeoPulse.

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
*   Replace `YOUR_CONFIGURED_TOKEN` with the token you generated in GeoPulse in Step 1.
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

The automation above sends one location at a time, and Home Assistant doesn't retry a request that fails. Integrations that queue locations while GeoPulse is unreachable, or that backfill history from the Home Assistant recorder, can send them in one request:

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
