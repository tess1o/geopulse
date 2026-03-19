# Geofences

Geofences let you monitor when a tracked subject enters or leaves a rectangle area and generate notifications.

## Notification Model (Read First)

Every geofence event is always an **in-app notification**.

- In-app events are always saved and visible in the bell inbox and Events tab.
- Apprise delivery is optional and additive.
- If Apprise is configured and a template has destination URL(s), GeoPulse sends externally too.
- If Apprise is not configured (or delivery fails), in-app notifications still work.

In short:

- **No destination URL configured** -> in-app only
- **Destination URL(s) configured + Apprise enabled** -> in-app + external delivery

For Apprise setup, see [Apprise Notifications](../../system-administration/configuration/apprise-notifications).

## Quick Start (Recommended Order)

1. Create templates (optional but recommended).
2. Create a rule and draw the rectangle.
3. Trigger movement across area boundary (enter/leave).
4. Check bell inbox and Events tab.
5. Optionally add Apprise destinations for external channels.

## What You Can Track

A geofence rule always has:

- One owner (you)
- One tracked subject (`Me` or one friend)
- One rectangle area
- Event types (`Enter`, `Leave`, or both)

## Friend Access Requirement

To track a friend in a geofence rule, that friend must grant you **Live location sharing** permission.

- If a friend does not share live location with you, they do not appear in the rule subject selector.
- Existing friend-based rules can stop triggering if live sharing is revoked.

For permission setup details, see [Friends](../social-and-sharing/friends).

## Rules Tab

Use the **Rules** tab to create and manage geofence rules.

### Rule fields

- **Name**: user-friendly rule name (for example `Home`, `Office`).
- **Subject**: who is tracked (`Me` or an eligible friend).
- **Area Picker**: draw a rectangle on the map.
- **Monitor Enter / Monitor Leave**: choose which transitions to monitor.
- **Cooldown (seconds)**: minimum delay between notification emissions for the same rule.
- **Enter Template / Leave Template**: optional templates for each event type.
- **Status**: `Active` or `Paused`.

### Rule behavior

- Rules are evaluated on new incoming GPS points.
- Transition events are emitted when state changes between outside and inside.
- If first observed point is inside and enter monitoring is enabled, an initial enter event can be emitted.
- `Paused` rules are not evaluated.

## Templates Tab

Use the **Templates** tab to control event message rendering and optional external delivery destinations.

### Destination URLs

- `Destination URL(s)` is optional.
- One URL per line.
- Multiple destinations are supported (newline-delimited list).
- Empty destination means **in-app only** for that template.

Example (multiple destinations):

```text
tgram://BOT_TOKEN/CHAT_ID
discord://WEBHOOK_ID/WEBHOOK_TOKEN
```

Behavior with these examples:

- Event is still stored in-app.
- If Apprise is enabled and reachable, GeoPulse also sends to all listed destinations.

### Default templates

- You can set one default template for `Enter` and one for `Leave`.
- If a rule does not explicitly select a template, default template (if enabled) is used.
- If no default is available, GeoPulse falls back to built-in in-app rendering.

### Supported macros

- `{{subjectName}}`
- `{{eventCode}}` (`ENTER` / `LEAVE`)
- `{{eventVerb}}` (`entered` / `left`)
- `{{geofenceName}}`
- `{{timestamp}}` (owner-local timezone/date format)
- `{{timestampUtc}}` (UTC ISO-8601)
- `{{lat}}`
- `{{lon}}`

## Events Tab

The **Events** tab is the full history/inbox view for geofence events.

You can:

- Filter unread items
- Mark individual events as seen
- Mark all events as seen
- Review delivery status metadata (`PENDING`, `SENT`, `FAILED`, `SKIPPED`)

## In-App Experience

- Navbar bell shows unread count and latest notifications.
- In-app toasts appear for new events while app tab is visible/focused.
- Side menu badge mirrors unread count.
- Events stay available until marked seen (and later cleaned up by retention policy).

## Optional External Delivery with Apprise

Apprise is additive: events remain in-app and can also be sent to external channels.

Setup guides:

- [Apprise Notifications](../../system-administration/configuration/apprise-notifications)
- [Docker Compose Deployment (Apprise overlay)](../../getting-started/deployment/docker-compose#optional-add-apprise-geofence-external-notifications)

## Recommended Setup Flow

1. Create or verify friend permissions (live sharing for friend-tracking use cases).
2. Configure templates (including defaults for enter/leave).
3. Create rules and draw areas.
4. Verify new events in the bell + Events tab.
5. Optionally enable Apprise for external channels.
