---
title: Weather
description: Admin guide for enabling, tuning, monitoring, and troubleshooting the GeoPulse Weather integration.
---

# Weather

GeoPulse can attach weather samples to timeline stays and trips, then show those conditions on the Timeline map, timeline cards, Journey Insights, and weather-related badges. Weather is a system-wide integration managed by administrators.

Weather is enabled by default for ongoing timeline activity. Historical weather backfill is disabled by default, so existing timeline history is not sent for weather enrichment unless an administrator opts in. Open-Meteo is the default provider. Pirate Weather can be enabled with an API key and selected as the primary provider or as a secondary fallback provider.

## Admin Setup

Open **Admin Dashboard > System Settings > Weather**. Admin Settings are stored in the database and override environment defaults.

Configure the provider first:

| Admin Setting | Default | Description |
|---|---|---|
| **Enable Weather** | On | Master switch for weather collection and display data. |
| **Primary Provider** | Open-Meteo | Provider used first for new weather targets. |
| **Secondary Provider** | None | Optional fallback provider used when the primary provider cannot return a sample. |
| **Enable Open-Meteo** | On | Keep Open-Meteo available as a weather provider. |
| **Forecast URL** | `https://api.open-meteo.com` | Open-Meteo forecast and current-weather API base URL. |
| **Archive URL** | `https://archive-api.open-meteo.com` | Open-Meteo historical archive API base URL. |
| **Open-Meteo API Key** | Empty | Optional API key. Values saved in Admin Settings are encrypted. |
| **Enable Pirate Weather** | Off | Enables Pirate Weather as a selectable provider. Requires an API key. |
| **Pirate Weather Forecast URL** | `https://api.pirateweather.net` | Pirate Weather forecast API base URL. |
| **Pirate Weather Time Machine URL** | `https://timemachine.pirateweather.net` | Pirate Weather historical time machine API base URL. |
| **Pirate Weather API Key** | Empty | Encrypted Pirate Weather API key. |

Use **Test Connection** to verify the configured forecast and historical endpoints before starting production collection.

:::note
Open-Meteo public endpoints do not require an API key for normal free usage. Add one only when your Open-Meteo account or deployment requires it.
:::

## How Collection Works

Weather collection has two independent phases:

1. Discovery checks timeline stays and trips and creates missing weather targets.
2. The fetch worker claims pending targets and stores samples while respecting quota and provider health.

Targets are deduplicated by user, provider, coordinate bucket, and hour. The default coordinate precision of `2` keeps roughly neighborhood-level buckets and reduces duplicate provider calls.

Timeline rebuilds and imports record affected user ranges after their timeline transaction commits. Historical reconciliation processes those ranges incrementally without loading a complete user timeline into backend memory:

- Each chunk covers at most 90 days for one user.
- Each run processes a global maximum number of chunks across all users.
- A user's pending range remains queued until its complete time range has been checked.
- Remaining work resumes on later scheduled runs, backend startup, or relevant timeline/settings events.
- The newest two hours wait before becoming eligible for historical processing.

Stored weather samples remain available until the owning user data is deleted. Target cleanup removes queue records, not stored samples.

## Sampling And Backfill

| Admin Setting | Default | Description |
|---|---|---|
| **Ongoing Weather** | On | Create targets for each active user's latest stay or trip. |
| **Ongoing Interval** | `60` minutes | Minimum interval between ongoing samples. Minimum: `30`. |
| **Historical Weather Backfill** | Off | Discover historical targets from existing timeline stays and trips. |
| **Historical Backfill Chunks per Run** | `4` | Global maximum of 90-day user-range chunks checked by one reconciliation run. Minimum: `1`. |
| **Coordinate Precision** | `2` | Decimal places used for location buckets. Valid range: `0` to `5`. |
| **Retry Failed Targets** | On | Retry stale failed targets after their cooldown. |
| **Failed Retry Cooldown** | `24` hours | Time before a failed target can be retried. |

Short stays and trips get one sample near the midpoint. Longer timeline items are sampled about every two hours, with caps that keep target creation bounded.

The chunk limit is global, not per user. With the default value, one invocation checks at most four 90-day chunks in total. Increasing it clears a backlog faster but increases CPU, database work, and memory pressure during each run. Admin changes apply to the next reconciliation run without a backend restart.

## Quota

| Admin Setting | Default | Description |
|---|---|---|
| **Daily Request Limit** | `10000` | GeoPulse-side provider request cap per UTC day. |
| **Ongoing Reserve** | `500` | Requests reserved for ongoing samples before backfill can consume quota. |

Ongoing targets can use the full remaining daily quota. Historical targets can use only the quota left after the ongoing reserve.

If the daily limit is reached, fetching pauses until shortly after the next UTC day starts. If the provider reports quota or rate-limit exhaustion, GeoPulse pauses calls until the provider retry time or daily reset. Administrators receive in-app notifications when collection is limited and restored.

## Status Panel

The Weather tab refreshes its status automatically.

| Status | Meaning |
|---|---|
| **State** | `Disabled`, `Not configured`, or `Enabled`. |
| **Requests today** | Requests used compared with the configured daily limit. |
| **Samples stored** | Total stored weather samples. |
| **Pending targets** | Targets waiting to be fetched. |
| **Claimable pending** | Pending targets currently eligible for a worker. |
| **Fetch status** | Reason fetching is ready or blocked. |
| **Provider health** | Current provider circuit status. |
| **In progress targets** | Targets currently claimed by a worker. |
| **Completed targets** | Queue records that produced a sample. |
| **Skipped targets** | Queue records skipped because data existed or was unavailable. |
| **Failed targets** | Targets that exhausted retries or hit an unrecoverable error. |

## Manual Backfill

Enable **Historical Weather Backfill** before running automatic or targeted historical discovery. An administrator can queue a specific range with:

```http
POST /api/admin/weather/backfill
Content-Type: application/json

{
  "startTime": "2026-01-01T00:00:00Z",
  "endTime": "2026-01-31T23:59:59Z"
}
```

Add `userId` to limit the request to one user. The response reports targets created, already known, and skipped. Samples are fetched asynchronously under quota and provider-health rules.

## Environment Variables

Environment variables provide first-boot defaults and support immutable deployments. A value saved in Admin Settings takes precedence for settings marked **Admin configurable**. Changing an environment variable requires a backend restart.

### Admin Configurable

| Variable | Default | Admin Setting |
|---|---|---|
| `GEOPULSE_WEATHER_ENABLED` | `true` | **Enable Weather** |
| `GEOPULSE_WEATHER_PRIMARY_PROVIDER` | `OPEN_METEO` | **Primary Provider** |
| `GEOPULSE_WEATHER_SECONDARY_PROVIDER` | Empty | **Secondary Provider** |
| `GEOPULSE_WEATHER_OPEN_METEO_ENABLED` | `true` | **Enable Open-Meteo** |
| `GEOPULSE_WEATHER_OPEN_METEO_FORECAST_URL` | `https://api.open-meteo.com` | **Forecast URL** |
| `GEOPULSE_WEATHER_OPEN_METEO_ARCHIVE_URL` | `https://archive-api.open-meteo.com` | **Archive URL** |
| `GEOPULSE_WEATHER_OPEN_METEO_API_KEY` | Empty | **Open-Meteo API Key** |
| `GEOPULSE_WEATHER_PIRATE_ENABLED` | `false` | **Enable Pirate Weather** |
| `GEOPULSE_WEATHER_PIRATE_BASE_URL` | `https://api.pirateweather.net` | **Pirate Weather Forecast URL** |
| `GEOPULSE_WEATHER_PIRATE_TIME_MACHINE_URL` | `https://timemachine.pirateweather.net` | **Pirate Weather Time Machine URL** |
| `GEOPULSE_WEATHER_PIRATE_API_KEY` | Empty | **Pirate Weather API Key** |
| `GEOPULSE_WEATHER_ONGOING_ENABLED` | `true` | **Ongoing Weather** |
| `GEOPULSE_WEATHER_ONGOING_INTERVAL_MINUTES` | `60` | **Ongoing Interval** |
| `GEOPULSE_WEATHER_BACKFILL_ENABLED` | `false` | **Historical Weather Backfill** |
| `GEOPULSE_WEATHER_QUOTA_DAILY_REQUEST_LIMIT` | `10000` | **Daily Request Limit** |
| `GEOPULSE_WEATHER_QUOTA_ONGOING_RESERVE` | `500` | **Ongoing Reserve** |
| `GEOPULSE_WEATHER_COORDINATE_PRECISION` | `2` | **Coordinate Precision** |
| `GEOPULSE_WEATHER_FAILED_TARGET_RETRY_ENABLED` | `true` | **Retry Failed Targets** |
| `GEOPULSE_WEATHER_FAILED_TARGET_RETRY_COOLDOWN_HOURS` | `24` | **Failed Retry Cooldown** |

### Runtime Only

These controls are available only as environment variables and apply after a backend restart.

| Variable | Default | Description |
|---|---|---|
| `GEOPULSE_WEATHER_OPEN_METEO_CONNECT_TIMEOUT_SECONDS` | `5` | Provider connection timeout. |
| `GEOPULSE_WEATHER_OPEN_METEO_READ_TIMEOUT_SECONDS` | `15` | Provider response timeout. |
| `GEOPULSE_WEATHER_TARGET_CLEANUP_JOB_CRON` | `0 30 3 * * ?` | Queue cleanup schedule. |
| `GEOPULSE_WEATHER_TARGETS_COMPLETED_RETENTION_DAYS` | `7` | Retention for completed and skipped target records. |
| `GEOPULSE_WEATHER_TARGETS_FAILED_RETENTION_DAYS` | `30` | Retention for failed target records. |
| `GEOPULSE_WEATHER_TARGETS_IN_PROGRESS_TIMEOUT_MINUTES` | `60` | Age after which interrupted in-progress targets become retryable. |

See the [Environment Variables Reference](/docs/getting-started/deployment/environment-variables#weather-27) for restrictions and restart behavior.

## Operational Tuning

Use the defaults for small personal instances. Adjust them only for quota pressure, a large backlog, or provider connectivity problems.

| Goal | Suggested Change |
|---|---|
| Minimize idle CPU and database load | Keep **Historical Backfill Chunks per Run** low. Increase the reconciliation interval only if slower backlog processing is acceptable. |
| Clear a historical backlog faster | Increase **Historical Backfill Chunks per Run** gradually while monitoring backend and PostgreSQL CPU. |
| Reduce provider requests | Disable **Historical Weather Backfill**, lower **Coordinate Precision**, or lower **Daily Request Limit**. |
| Protect current timeline weather | Increase **Ongoing Reserve**. |
| Slow ongoing collection | Increase **Ongoing Interval**. |
| Recover from transient provider errors | Keep **Retry Failed Targets** enabled and retain the default cooldown. |

## Troubleshooting

| Symptom | Checks |
|---|---|
| Weather state is `Disabled` | Turn on **Enable Weather** and save changes. |
| Weather state is `Not configured` | Verify **Forecast URL** and **Archive URL** are not empty. |
| Test Connection fails | Check backend outbound access, URL validity, proxy/firewall rules, and API key requirements. |
| Requests stop before the daily limit | Check **Ongoing Reserve**; historical work pauses when only the reserve remains. |
| Many failed targets | Inspect provider health, recent errors, and whether the archive endpoint covers the requested dates. |
| Historical weather does not appear | Turn on **Historical Weather Backfill** or run a targeted admin backfill. |
| Backlog drains too slowly | Review **Historical Backfill Chunks per Run**, the reconciliation interval, quota, and provider health. |
| CPU spikes during reconciliation | Lower **Historical Backfill Chunks per Run**. Each unit permits another 90-day user-range chunk in that run. |

## Related Settings

- [Environment Variables Reference](/docs/getting-started/deployment/environment-variables#weather-27)
- [Admin Panel](/docs/system-administration/configuration/admin-panel)
- [Journey Insights](/docs/user-guide/core-features/journey-insights)
