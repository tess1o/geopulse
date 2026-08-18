# Weather processing

GeoPulse weather enrichment uses one sequential, event-driven worker. Database queues make
work restart-safe; in-memory state is only used to coalesce wake-ups in the supported
single-backend-instance deployment.

## Data flow

1. A committed timeline change adds or expands one row in
   `weather_backfill_reconciliations` and wakes the worker.
2. The worker fetches already-created targets first. When none are ready, it converts one
   90-day reconciliation chunk into exact hourly rows in `weather_sample_targets`.
3. Up to 24 targets for the same user, provider, coordinate bucket, and UTC day are claimed
   as one provider request.
4. Normalized hourly results are stored in `weather_samples`; full provider payloads are not
   duplicated into every sample.
5. The worker immediately takes the next group or chunk. It stops only when queues are empty,
   weather is disabled, quota is exhausted, or provider health applies a backoff.

Imports never wait for weather discovery or provider calls. They complete after timeline
generation; the committed timeline event durably queues the affected range. The normal badge
recalculation schedule incorporates weather badges later.

## Wake-ups and recovery

The worker is woken by timeline events, relevant setting changes, startup, admin requests,
and due ongoing collection. Concurrent wake-ups are represented by one rerun flag, so they do
not create one task per event.

A one-minute watchdog performs only indexed due-work checks. It does not scan timeline tables.
The daily cleanup remains an independent scheduled job.

## Quota

`weather_daily_request_usage` stores actual external calls per UTC date. Quota is atomically
reserved immediately before a provider call. Fallbacks and SSL retries are separate calls.
Historical work can consume `daily limit - ongoing reserve`; ongoing collection may consume
the full daily limit.

## Administration

- `GET /api/admin/weather/status` is read-only and returns the worker state, queue aggregates,
  quota usage, and provider health.
- `POST /api/admin/weather/process-now` returns `202 Accepted` after signalling the worker.
- `POST /api/admin/weather/backfill` durably queues ranges, signals the worker, and returns
  `202 Accepted`.
- The Weather settings page refreshes status only when opened or when Refresh is selected.

The internal 90-day discovery chunk and maximum 24-hour provider group are deliberately not
configurable. Provider credentials, ongoing interval, quota/reserve, coordinate precision,
and failed-target retry policy remain runtime settings.
