---
id: home-assistant-integration
title: Home Assistant Integration API
description: The endpoints a Home Assistant integration (or any home automation client) uses to send locations to GeoPulse and read the user's current status, timeline, geofence events and friends.
---

This page describes the API contract for home automation clients. The
[GeoPulse integration for Home Assistant](https://github.com/tess1o/geopulse-homeassistant) is built on it; if you
only want to use Home Assistant, see [Using Home Assistant with GeoPulse](/docs/user-guide/gps-sources/home_assistant)
instead. The API works in both directions:

- **To GeoPulse:** send locations from device trackers, one at a time or in batches.
- **From GeoPulse:** read what the user is doing now, detect stays, trips and geofence events, and show friends on a
  map.

Everything uses **one personal API token**. All paths below are relative to the GeoPulse URL, for example
`https://geopulse.example.com`.

## Credentials

Users get the token from **Location Sources → Add → Home Assistant → GeoPulse integration**. That creates an API token
with the scopes `read` and `gps:ingest` (or connects one they already have) together with the Home Assistant location
source, and shows the token once. Tokens can also be created under **Profile > Security > API tokens** with the
**Home Assistant** preset, but such a token can only send locations after it's connected to a source in Location
Sources (*Use an existing token*). See [API Tokens](./api-tokens.md).

Both scopes are only needed for both directions. A client that only reads from GeoPulse, for example because the
user's phone already sends locations through OwnTracks, needs a token with just `read`, created under
**Profile > Security > API tokens** with the **Read only** preset, and no location source. Such a token gets
`403 API_TOKEN_SCOPE_REQUIRED` on the location endpoints below, so check `scopes` during setup and don't offer to
send locations. A client that only sends needs just `gps:ingest`.

Send the token on every request, either way:

```http
Authorization: Bearer gp_sa_...
X-API-Key: gp_sa_...
```

### Validate the token during setup

```http
GET /api/v1/api-tokens/current
```

This works with a token of any scope, including a send-only (`gps:ingest`) token. It returns:

- `id`: the token's id;
- `userId`: the account the token belongs to. It's stable across token replacements, so use it as the unique id of
  the setup, and check it during re-authentication;
- `name`, `scopes` and `expiresAt`.

Use `scopes` to tell the user exactly what is missing, for example *"this token cannot send locations; it needs the
`gps:ingest` scope"*.

```http
GET /api/v1/users/me
```

This needs `read`. It returns the user's `fullName` and `timezone`.

## Sending locations

```http
POST /api/v1/gps/ingest/home-assistant
Content-Type: application/json

{
  "device_id": "pixel_8",
  "timestamp": "2026-10-14T09:15:30+00:00",
  "location": {
    "latitude": 49.8419,
    "longitude": 24.0315,
    "accuracy": 8,
    "altitude": 296,
    "speed": 2.7
  },
  "battery": { "level": 82 }
}
```

- `latitude` and `longitude` are required; a location without them is skipped.
- `accuracy` (meters), `altitude` (meters), `speed` (m/s) and `battery.level` (percent) are optional. Send `null`, or
  leave them out, when unknown. Never send `0` as a placeholder: an accuracy of `0` claims a perfect fix, and a speed
  of `0` makes every point look stationary.
- `timestamp` is the time of the GPS fix (ISO-8601). If it's missing, GeoPulse uses the time of arrival.
- The response is `200` with an empty body.

### Batches

```http
POST /api/v1/gps/ingest/home-assistant/batch
Content-Type: application/json

[ { ...location... }, { ...location... } ]
```

- Up to 1000 locations per request; more returns `413`.
- Every item needs a `timestamp`; items without one are skipped.
- Points that are already stored are skipped as duplicates, so resending a batch after a failed request is safe.
  Use this for a retry queue and for backfilling history.
- The response reports what happened to the batch:

```json
{ "accepted": 120, "saved": 118, "duplicate": 2, "filtered": 0 }
```

### Where the points go

Points sent with an API token are stored under the **Home Assistant location source connected to that token** in
Location Sources. GeoPulse never creates that source on its own: a token that isn't connected to a source gets
`403 GPS_SOURCE_NOT_CONNECTED`, and the user has to add it under Location Sources. The user sees the source there as
*API token: ‹name›*, where they can tune filters, disable it, and see when data last arrived and why points were
skipped. Deleting the source offers to revoke the token as well; if the token isn't revoked, it can still read data
but no longer send locations.

To read that status from the integration:

```http
GET /api/v1/gps/sources/current
```

This returns the source connected to the calling token, without any credentials, and works with `read` or
`gps:ingest`. It returns `404` (`GPS_SOURCE_NOT_FOUND`) while no source is connected to the token yet.

```json
{
  "id": "8a2f…",
  "type": "HOME_ASSISTANT",
  "active": true,
  "ingestStatus": {
    "lastReceivedAt": "2026-10-14T09:15:31Z",
    "lastSavedAt": "2026-10-14T09:15:31Z",
    "lastSavedPointTimestamp": "2026-10-14T09:15:30Z",
    "lastRejectedAt": "2026-10-14T08:02:11Z",
    "lastRejectionCode": "ACCURACY_EXCEEDED",
    "lastRejectionParams": { "accuracy": 120.0, "threshold": 100 }
  }
}
```

Rejection codes: `ACCURACY_EXCEEDED`, `MISSING_ACCURACY`, `NEGATIVE_ACCURACY`, `SPEED_EXCEEDED`, `DUPLICATE`,
`INVALID_PAYLOAD`.

Don't use `GET /api/v1/gps/sources` for this. It lists all of the user's sources, and for tokens without full access
the other sources' credentials are left out (`token` is `null`).

## Current status

```http
GET /api/v1/users/me/status
```

```json
{
  "generatedAt": "2026-10-14T10:00:00Z",
  "timezone": "Europe/Kyiv",
  "state": "STAY",
  "stay": {
    "placeName": "Gym",
    "favoriteId": 12,
    "geocodingId": null,
    "locationSource": "FAVORITE",
    "since": "2026-10-14T08:20:00Z",
    "durationSeconds": 6000,
    "latitude": 49.84,
    "longitude": 24.03,
    "city": "Lviv",
    "country": "Ukraine"
  },
  "trip": null,
  "dataGap": null,
  "lastPoint": {
    "timestamp": "2026-10-14T09:58:00Z",
    "receivedAt": "2026-10-14T09:58:01Z",
    "ageSeconds": 120,
    "latitude": 49.84,
    "longitude": 24.03,
    "accuracy": 8.0,
    "battery": 81.0,
    "sourceType": "HOME_ASSISTANT"
  },
  "today": {
    "date": "2026-10-14",
    "distanceMeters": 38120,
    "tripCount": 4,
    "stayCount": 3,
    "uniquePlaces": 3,
    "movingSeconds": 3900
  }
}
```

**`state`** is one of:

| State | Meaning |
|---|---|
| `STAY` | At a place; `stay` is set. |
| `TRIP` | Moving; `trip` is set (`since`, `durationSeconds`, `distanceMeters`, `movementType`, start coordinates). |
| `DATA_GAP` | No GPS data for longer than the data gap threshold; `dataGap.since` is the last point's time. |
| `UNKNOWN` | GPS data exists, but the timeline has not been generated yet. |
| `NO_DATA` | The user has no GPS data. |

Notes:

- **`durationSeconds` is counted to now**, so it keeps growing between timeline updates.
- **The timeline is updated every few minutes** (5 by default), so a new stay or trip appears with up to that delay.
  `lastPoint` is always current.
- **`today` uses the user's timezone** and counts trips and stays that started today. Polling once a minute is plenty.

## Detecting stays, trips and geofence events

GeoPulse doesn't push events. A client that wants to react to "stay started", "trip ended" or "entered a geofence"
derives them from the endpoints above and keeps its own state between polls.

### Stays, trips and tracking lost/resumed

1. **Poll `GET /api/v1/users/me/status`** every minute or so, and keep the previous response.
2. **Something changed** when `state` differs, or `stay.since`/`trip.since` moved by more than about 2 minutes (the
   timeline is rebuilt every few minutes, which can shift a start time slightly), or `stay.placeName`/`favoriteId`
   changed.
3. **Fill in what happened in between.** A short trip or stay can start and end between two polls. On a change, read
   ```http
   GET /api/v1/timeline?from=<previous since>&to=<now>
   ```
   and walk `stays` and `trips` in order of `timestamp` (each has `stayDuration`/`tripDuration` in seconds,
   `locationName`, `distanceMeters`, `movementType`). Report each item that started after the previous one as
   ended/started pairs.
4. **Tracking lost/resumed:** `state` becomes `DATA_GAP`, or stops being it.

Rules worth keeping:

- **First poll:** only store the state. Don't report anything, since there's nothing to compare with.
- **Rebuilt history:** if the current item starts *earlier* than the one you stored (the user imported older data or
  edited the timeline), store it silently.
- **Limit the catch-up:** if the previous poll is long ago (for example more than a day) or there are many items in
  between, report only the latest change.
- **Staleness:** stay and trip start times are only known after the fact. A stay is confirmed after the user's
  minimum stay duration (7 minutes by default), then picked up by the next timeline update (every 5 minutes), so a
  fresh "stay started" is often 10–15 minutes old. Don't trigger automations for items much older than that, for
  example after the client was offline.

Rebuilding the timeline gives stays and trips **new ids**, so identify them by start time and kind (plus place for
stays), never by `id`.

### Geofence enter/leave

```http
GET /api/v1/geofences/events?afterId=<largest id seen>
```

- With `afterId`, only newer events are returned, oldest first, and the sort parameters are ignored. Page through
  `items` (and `totalElements`) as usual.
- On the first call, take the largest `id` from the latest events (`?size=1`, newest first by default) and don't
  report them.
- Each event has `id`, `eventType` (`ENTER`/`LEAVE`), `occurredAt`, `ruleId`, `ruleName`, `subjectUserId`,
  `subjectDisplayName`, `pointLat` and `pointLon`. The subject is who entered or left: the user, or a friend tracked by
  one of the user's geofences (compare `subjectUserId` with your own `userId`).
- Geofence events are recorded as soon as the GPS point arrives, so they're only delayed by your polling interval.

**Value formats** used across these endpoints:

- Times are ISO-8601 UTC strings; ids are numbers, user ids are UUID strings.
- `movementType` is one of `WALK`, `BICYCLE`, `RUNNING`, `CAR`, `MOTORCYCLE`, `PUBLIC_TRANSPORT`, `TRAIN`, `FLIGHT`,
  `BOAT`, `UNKNOWN`.
- `locationSource` is `FAVORITE` or `GEOCODING`.

## Friends

```http
GET /api/v1/friends
```

The response has one entry per friend:

- `friendId`: the friend's user id; use it as the stable id. `userId` is the caller.
- `fullName`, `email`, `avatar`.
- `lastSeen`: ISO-8601 UTC timestamp string, for example `2026-03-06T17:05:34Z` (fractional seconds when present).
- `lastLatitude`, `lastLongitude`, `lastAccuracy` (meters), `lastBattery`.
- `latestActivityType`: `STAY`, `TRIP` or `null`, plus `latestActivityDurationSeconds`.
- `friendSharesLiveLocation` and `friendSharesTimeline`.

Location, accuracy, battery and activity are only filled in for friends who share their live location with the user.
This is enough to create a `device_tracker` per friend.

## Server health and version

These endpoints don't need a token:

- `GET /api/v1/system/health`: `{ "status": "UP", "database": "UP" }`, or `503` when the database is down.
- `GET /api/v1/system/version/status`: `currentVersion`, `latestVersion`, `updateAvailable` and `releaseUrl`.

## Errors

| Status | Meaning |
|---|---|
| `401` | The token is invalid, expired or revoked. Ask the user for a new token. |
| `403` with code `API_TOKEN_SCOPE_REQUIRED` | The token lacks the scope named in `requiredScope`. |
| `403` with code `GPS_SOURCE_NOT_CONNECTED` | Location requests only: the token is valid but no Home Assistant location source is connected to it. Tell the user to add one under Location Sources → Add → Home Assistant → GeoPulse integration → *Use an existing token*. |
| `403` with code `GPS_SOURCE_DISABLED` | Location requests only: the token is fine, but the user disabled its location source in GeoPulse. Tell the user to enable it under Location Sources; don't ask for a new token. |
| `413` | A batch had more than 1000 locations. |

Errors use the [problem details](./intro.md#errors) format.
