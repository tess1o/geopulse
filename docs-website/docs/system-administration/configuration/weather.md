---
title: Weather
description: Admin guide for enabling, tuning, monitoring, and troubleshooting the GeoPulse Weather integration.
---

# Weather

GeoPulse can attach weather samples to timeline stays and trips, then show those conditions on the Timeline map, timeline cards, Journey Insights, and weather-related badges. Weather is a system-wide integration managed by administrators.

Weather is enabled by default for ongoing/current timeline activity. Historical backfill is disabled by default, so existing timeline history is not sent for weather enrichment unless an administrator opts in. GeoPulse uses Open-Meteo-compatible forecast and archive endpoints to collect samples.

## Admin Setup

Go to:

`Admin Dashboard > System Settings > Weather`

Configure the provider settings first:

| Setting | Default | Description |
|---|---|---|
| `weather.enabled` | `true` | Master switch for weather collection and weather display data. |
| `weather.open-meteo.forecast-url` | `https://api.open-meteo.com` | Forecast/current weather API base URL. |
| `weather.open-meteo.archive-url` | `https://archive-api.open-meteo.com` | Historical archive API base URL. |
| `weather.open-meteo.api-key` | _(empty)_ | Optional Open-Meteo API key. Values saved from the Admin UI are encrypted. |

Use **Test Connection** before enabling production collection. The test calls the configured forecast endpoint and reports the provider, URL, status code, and message.

:::note
Open-Meteo public endpoints do not require an API key for normal free usage. Add an API key only when your Open-Meteo account or deployment requires one.
:::

## How Collection Works

Weather collection has two phases:

1. GeoPulse discovers weather targets from timeline stays and trips.
2. A fetch job claims pending targets and stores samples while respecting quota and provider health state.

Targets are de-duplicated by user, provider, coordinate bucket, and hour. The default coordinate precision is `2`, which keeps roughly neighborhood-level buckets and reduces duplicate provider calls.

GeoPulse stores these values for each sample:

| Field Group | Values |
|---|---|
| Location and time | Requested coordinates, provider coordinates, coordinate bucket, observed hour, fetched time, timezone |
| Conditions | Weather code, temperature, apparent temperature, humidity, precipitation, rain, snowfall, cloud cover |
| Wind and pressure | Wind speed, wind gust, wind direction, pressure |
| Audit/debug | Provider, source, and raw provider payload |

Stored weather samples remain available for timeline and insight views until the owning user data is deleted. Weather target cleanup only removes queue records, not completed weather samples.

## Sampling Settings

| Setting | Default | Description |
|---|---|---|
| `weather.ongoing.enabled` | `true` | Create targets for each active user's most recent stay or trip. |
| `weather.ongoing.interval-minutes` | `60` | Minimum interval between ongoing samples. Must be at least `30`. |
| `weather.backfill.enabled` | `false` | Discover historical targets from existing timeline stays and trips. Enable this only when you want GeoPulse to backfill past timeline weather. |
| `weather.coordinate-precision` | `2` | Decimal places used for coordinate buckets. Lower values reduce requests; higher values are more location-specific. Valid range: `0` to `5`. |
| `weather.failed-target-retry.enabled` | `true` | Retry failed targets after they become stale. |
| `weather.failed-target-retry.cooldown-hours` | `24` | Time before a failed target can be retried. |

Short stays/trips get one sample near the midpoint. Longer items are sampled about every two hours, with caps to keep backfills bounded.

## Quota Settings

| Setting | Default | Description |
|---|---|---|
| `weather.quota.daily-request-limit` | `10000` | GeoPulse-side request cap per UTC day. |
| `weather.quota.ongoing-reserve` | `500` | Daily request reserve kept for ongoing samples before backfill work can consume quota. |

Ongoing targets can use the full remaining daily quota. Historical and admin backfill targets can use only the quota left after the ongoing reserve.

If the GeoPulse daily limit is reached, weather fetching pauses until shortly after the next UTC day starts. If the provider reports quota/rate-limit exhaustion, GeoPulse opens the provider health circuit until the provider retry time or the next daily reset.

Administrators receive in-app notifications when weather quota is reached and when collection is restored.

## Status Panel

The Weather tab includes a status panel that refreshes automatically.

| Status | Meaning |
|---|---|
| **State** | `Disabled`, `Not configured`, or `Enabled`. |
| **Requests today** | Used requests compared with the configured daily limit. |
| **Samples stored** | Total stored weather samples. |
| **Pending targets** | Targets waiting to be fetched. |
| **In progress targets** | Targets currently claimed by a worker. |
| **Completed targets** | Queue records that produced a sample. |
| **Skipped targets** | Queue records skipped because data already exists or provider data was unavailable. |
| **Failed targets** | Queue records that exhausted retries or hit unrecoverable errors. |

The Admin Dashboard also shows weather provider health and remaining daily request capacity.

## Manual Backfill

Automatic historical discovery runs on a schedule only when `weather.backfill.enabled` is on. The default is off, so administrators must opt in before GeoPulse queues weather targets for existing timeline history. GeoPulse also exposes an admin-only REST endpoint for targeted backfills:

```http
POST /api/admin/weather/backfill
Content-Type: application/json

{
  "startTime": "2026-01-01T00:00:00Z",
  "endTime": "2026-01-31T23:59:59Z"
}
```

Add `userId` to backfill one user:

```json
{
  "userId": "00000000-0000-0000-0000-000000000000",
  "startTime": "2026-01-01T00:00:00Z",
  "endTime": "2026-01-31T23:59:59Z"
}
```

The response reports targets created, already known, and skipped. Target creation does not mean all samples are fetched immediately; the fetch job still processes the queue under quota and provider-health rules.

## Environment Defaults

Admin Settings are the preferred way to manage Weather after initial setup. Environment variables are useful for first boot, immutable deployments, or setting defaults before any database override exists.

```bash
GEOPULSE_WEATHER_ENABLED=true
GEOPULSE_WEATHER_OPEN_METEO_FORECAST_URL=https://api.open-meteo.com
GEOPULSE_WEATHER_OPEN_METEO_ARCHIVE_URL=https://archive-api.open-meteo.com
GEOPULSE_WEATHER_OPEN_METEO_API_KEY=
GEOPULSE_WEATHER_ONGOING_ENABLED=true
GEOPULSE_WEATHER_ONGOING_INTERVAL_MINUTES=60
GEOPULSE_WEATHER_BACKFILL_ENABLED=false
GEOPULSE_WEATHER_QUOTA_DAILY_REQUEST_LIMIT=10000
GEOPULSE_WEATHER_QUOTA_ONGOING_RESERVE=500
GEOPULSE_WEATHER_COORDINATE_PRECISION=2
GEOPULSE_WEATHER_FAILED_TARGET_RETRY_ENABLED=true
GEOPULSE_WEATHER_FAILED_TARGET_RETRY_COOLDOWN_HOURS=24
```

For the full list of Weather environment variables, including scheduler and cleanup settings, see the [Environment Variables Reference](/docs/getting-started/deployment/environment-variables#weather-26).

## Operational Tuning

Use the defaults for small personal instances. Adjust only when you see quota pressure, a large backfill backlog, or provider connectivity problems.

| Goal | Suggested Change |
|---|---|
| Reduce provider requests | Keep `weather.backfill.enabled=false`, lower `weather.coordinate-precision`, or lower `weather.quota.daily-request-limit`. |
| Protect current timeline weather | Increase `weather.quota.ongoing-reserve`. |
| Slow ongoing collection | Increase `weather.ongoing.interval-minutes`. |
| Recover from transient provider errors | Keep failed target retry enabled and leave the default cooldown. |
| Populate historical weather | Set `weather.backfill.enabled=true`; optionally use the admin backfill endpoint for a targeted date range. |

## Troubleshooting

| Symptom | Checks |
|---|---|
| Weather state is `Disabled` | `weather.enabled` has been turned off by env or Admin Settings. Enable it and save changes if you want weather collection. |
| Weather state is `Not configured` | Verify both forecast and archive URLs are non-empty. |
| Test Connection fails | Check outbound network access from the backend container, custom URL validity, proxy/firewall rules, and API key requirements. |
| Requests stop before the daily limit | Check `weather.quota.ongoing-reserve`; backfill pauses once only the reserve remains. |
| Many failed targets | Inspect provider health, recent error messages, and whether the archive endpoint has data for the requested range. |
| Historical weather does not appear | Enable `weather.backfill.enabled` or run a targeted admin backfill. Ongoing weather only covers current/latest activity. |
| Backfill creates targets but samples do not appear immediately | Wait for the sample fetch job and check quota/provider health. The queue is processed asynchronously. |

## Related Settings

- [Environment Variables Reference](/docs/getting-started/deployment/environment-variables#weather-26)
- [Admin Panel](/docs/system-administration/configuration/admin-panel)
- [Journey Insights](/docs/user-guide/core-features/journey-insights)
