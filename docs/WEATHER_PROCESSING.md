# Weather Processing Architecture

This document explains how timeline data becomes weather samples, what each scheduled job
does, and where work is bounded. The main rule is that target discovery and provider fetching
are separate stages.

## Data Flow

```text
timeline_stays / timeline_trips
             |
             | ongoing discovery or historical reconciliation
             v
weather_sample_targets (durable provider work queue)
             |
             | sample fetch
             v
Open-Meteo -> weather_samples (final weather data)
```

Historical reconciliation has an additional durable queue:

```text
TimelineDataChangedEvent or coverage-changing setting
             |
             v
weather_backfill_reconciliations (one dirty range per user)
             |
             | bounded 90-day chunks
             v
weather_sample_targets
```

The tables have different responsibilities:

| Table | Responsibility |
| --- | --- |
| `timeline_stays`, `timeline_trips` | Source timeline produced from GPS data. |
| `weather_backfill_reconciliations` | Durable ranges that must be checked for missing historical targets. |
| `weather_sample_targets` | Durable queue of exact `(user, provider, coordinate bucket, hour)` samples to fetch. |
| `weather_samples` | Successfully fetched weather data used by the application. |

## Jobs

| Job | Default trigger | Settings gate | Responsibility |
| --- | --- | --- | --- |
| `WeatherHistoricalReconciliationJob` | Timeline/settings events, startup, every 30 minutes | `weather.enabled` and `weather.backfill.enabled` | Drains persisted historical dirty ranges into exact sample targets. |
| `WeatherOngoingDiscoveryJob` | Every 15 minutes | `weather.enabled` and `weather.ongoing.enabled` | Finds the latest active stay/trip and creates a current target for each active user. |
| `WeatherSampleFetchJob` | Every 10 minutes | `weather.enabled` | Claims pending targets, calls the configured weather provider, and stores samples. |
| `WeatherProviderHealthProbeJob` | Every 10 minutes | `weather.enabled` | Probes only when provider health says a retry is due. |
| `WeatherTargetCleanupJob` | Daily at 03:30 | `weather.enabled` | Deletes old completed, skipped, and failed target queue records. It does not delete samples. |

All jobs return after logging a disabled message when their settings gate is closed.

## Historical Reconciliation

Historical reconciliation proves that all expected timeline sample points have either a stored
sample or a queued target. It is incremental rather than a periodic full-history scan.

1. A successful timeline rebuild publishes `TimelineDataChangedEvent` with the affected user
   and time range.
2. After the timeline transaction commits, the event observer synchronously upserts that range
   into `weather_backfill_reconciliations` in a new transaction. The separate transaction keeps
   optional weather failures from rolling back an import or timeline rebuild, and commits the
   durable range before asynchronous work starts.
3. Multiple ranges for one user are coalesced using the earliest start and latest end. If an
   earlier range arrives, the cursor is rewound. This can intentionally recheck already known
   targets; inserts remain idempotent.
4. A worker locks one range with `FOR UPDATE SKIP LOCKED` and processes at most 90 days in a
   separate transaction.
5. Only primitive stay/trip projections overlapping that chunk are loaded. The complete user
   timeline and Hibernate entity graph are not loaded into memory.
6. The sampling policy calculates only sample times inside the current chunk. Trip coordinates
   use two indexed GPS point seeks around each target time, with start/end interpolation as a
   fallback.
7. Candidate targets are deduplicated in the chunk and inserted in SQL batches of 250. Existing
   samples and targets are ignored by database checks and uniqueness constraints.
8. On success, the cursor advances. The row is deleted when its complete range is reconciled.
   Failed transactions leave the cursor unchanged, so a later run retries the chunk.

Before draining ranges, the worker also resets stale in-progress targets and failed targets whose
configured retry cooldown has elapsed. The sample fetcher performs the same recovery check before
claiming targets, so interrupted fetch work is eventually resumed even when no new targets exist.

Automatic reconciliation ignores the newest two hours. A range whose tail is too recent stays
in the queue and becomes eligible on a later scheduled run. This avoids historical processing
competing with ongoing weather discovery.

Each run processes at most four chunks by default, so one invocation covers at most 360 days of
timeline data. Remaining rows are reported as `pendingUserRanges` and resumed by later runs.

### Triggers

- **Timeline change:** queues only the event's affected range, including a full timeline
  regeneration range. It does not immediately scan unrelated history.
- **Weather or backfill enabled, or coordinate precision changed:** queues full coverage for all
  active users because the definition of a known sample may have changed.
- **Startup:** resumes rows already in `weather_backfill_reconciliations`; it does not create a
  full scan.
- **Scheduled run:** resumes persisted rows; it does not create a full scan and does not directly
  invoke provider fetching.

Event-driven and startup reconciliation may invoke the fetcher immediately when they create new
targets. The independently scheduled fetch job also consumes any remaining targets.

## Ongoing Processing

Ongoing discovery is intentionally small. For each active user it reads the latest stay and trip,
selects an item that is still active, rounds the target time to the configured interval/hour, and
queues one target if needed. It does not inspect historical timeline ranges.

This path continues to work when historical backfill is disabled, as long as both
`weather.enabled` and `weather.ongoing.enabled` are enabled.

## Fetching, Priority, and Quota

Discovery never calls Open-Meteo itself. It creates `PENDING` rows in
`weather_sample_targets`. Fetching claims those rows safely and processes them in priority order:

| Source | Priority |
| --- | ---: |
| Ongoing | 100 |
| Explicit admin backfill | 80 |
| Automatic historical reconciliation | 70 |

The fetcher reserves configured daily capacity for ongoing targets, respects provider/internal
quota state, and stops or defers work when provider health blocks requests. Stale in-progress and
eligible failed targets can be reset for retry.

## Explicit Admin Backfill

`POST /api/admin/weather/backfill` is an explicit request to discover the complete requested
range immediately. It uses the same 90-day chunk logic and batched target insertion, but it
processes all chunks in the HTTP request instead of spreading them across scheduled runs. This
path can therefore consume more CPU and database time than automatic reconciliation for a large
range. Provider calls still happen through the target fetch pipeline.

## Idle Behavior

With no dirty ranges and no pending targets:

- Historical reconciliation performs settings checks, target retry recovery, and a small queue
  claim/count query.
- Ongoing discovery reads only the latest stay/trip per active user.
- Sample fetching checks quota, health, and whether claimable targets exist, then returns.
- Health probing performs no external request unless a retry is due.
- Cleanup runs only once per day.

There is no scheduled full timeline scan. A full scan is queued only by an explicit coverage
change, the one-time migration seed, or a timeline event whose affected range is itself the full
timeline.

## Configuration

The historical configuration keys retain `backfill.discovery` in their names for deployment
compatibility, but they control the reconciliation worker:

| Property | Default | Effect |
| --- | --- | --- |
| `geopulse.weather.backfill.discovery.job.interval` | `30m` | How often persisted historical work is resumed. |
| `geopulse.weather.backfill.discovery.job.delay` | `5m` | Initial scheduler delay after startup. |
| `geopulse.weather.backfill.discovery.chunks-per-run` | `4` | Maximum 90-day chunks processed by one drain. |
| `geopulse.weather.ongoing.job.interval` | `15m` | Ongoing target discovery frequency. |
| `geopulse.weather.sample-fetch.job.interval` | `10m` | Pending target fetch frequency. |
| `geopulse.weather.health.probe.job.interval` | `10m` | Provider health retry check frequency. |
| `geopulse.weather.target-cleanup.job.cron` | `0 30 3 * * ?` | Target queue cleanup schedule. |

## Reading Reconciliation Logs

Example:

```text
Weather historical reconciliation completed on scheduled: durationMs=420,
chunks=4, created=12, known=340, skipped=0, pendingUserRanges=1
```

- `durationMs`: total reconciliation time, including retry reset and all chunks.
- `chunks`: number of committed 90-day-or-smaller chunks.
- `created`: new provider targets inserted.
- `known`: candidates already represented by a sample or target, plus duplicates in the chunk.
- `skipped`: candidates rejected because their coordinates were invalid.
- `pendingUserRanges`: user ranges still present, including a recent tail waiting for eligibility.

`created=0` does not mean the run did no work. It means reconciliation checked the reported
chunks and proved that their candidates were already known. The bounded chunk count prevents
that proof from becoming an unbounded periodic CPU or memory spike.
