# Valhalla map matching

GeoPulse can optionally use a self-hosted Valhalla instance to display trip paths matched to roads and paths.
This is a display-only feature: raw GPS points, timeline detection, movement classification, and exports are unchanged.

## Behavior

- Map matching is off by default globally and per user.
- Timeline path rendering uses one interactive geometry source. It remains the original solid raw GPS path while any trip in the visible page is still being resolved or processed. After every visible trip reaches a terminal state, the source is replaced in one update with matched segments for completed trips and raw segments for failed, skipped, or unavailable trips.
- Highlighting, hover, endpoint markers, and replay all read from that same active source. Matched fragments receive deterministic presentation timestamps distributed monotonically across the trip duration so disconnected fragments do not interleave during hover or replay.
- When a matched trip is highlighted, the map controls provide a comparison toggle that overlays only that trip's raw GPS route as a purple dashed line above its matched geometry.
- If no cached match exists, GeoPulse queues high-priority on-demand work, keeps the raw draft visible, and shows a compact `Refining route...` map cue while polling the lightweight target-status endpoint.
- If Valhalla is unavailable, matching fails, or the trace is skipped, the raw GPS path remains visible.
- Admins can independently enable automatic matching for stable new trips and a resumable historical backfill for all users.
- Only road/path modes are matched: walking, running, bicycle, motorcycle, and car. Train, flight, boat, and unknown trips retain raw GPS paths.
- Raw GPS remains authoritative storage and continues to drive timeline detection, movement classification, exports, cache identity, and terminal fallback. Once the active map source is matched, map hover and replay intentionally follow the matched presentation geometry.

## GeoPulse configuration

Configure **Admin -> System Settings -> Map Matching**. The database-backed Admin settings override environment values and apply to the map matching service at runtime.

Equivalent backend environment variables:

```bash
GEOPULSE_TIMELINE_MAP_MATCHING_ENABLED=true
GEOPULSE_TIMELINE_MAP_MATCHING_PROVIDER=valhalla
GEOPULSE_TIMELINE_MAP_MATCHING_VALHALLA_BASE_URL=http://valhalla:8002
```

Optional tuning:

```bash
GEOPULSE_TIMELINE_MAP_MATCHING_CONNECT_TIMEOUT_SECONDS=3
GEOPULSE_TIMELINE_MAP_MATCHING_READ_TIMEOUT_SECONDS=20
GEOPULSE_TIMELINE_MAP_MATCHING_MAX_INPUT_POINTS=100
GEOPULSE_TIMELINE_MAP_MATCHING_MAX_TRIP_DURATION_HOURS=24
GEOPULSE_TIMELINE_MAP_MATCHING_WORKER_BATCH_SIZE=5
GEOPULSE_TIMELINE_MAP_MATCHING_MAX_ATTEMPTS=3
GEOPULSE_TIMELINE_MAP_MATCHING_AUTOMATIC_ENABLED=false
GEOPULSE_TIMELINE_MAP_MATCHING_BACKFILL_ENABLED=false
GEOPULSE_TIMELINE_MAP_MATCHING_AUTOMATIC_QUIET_PERIOD_MINUTES=15
GEOPULSE_TIMELINE_MAP_MATCHING_QUALITY_MIN_RAW_DISTANCE_METERS=500
GEOPULSE_TIMELINE_MAP_MATCHING_QUALITY_MIN_DISTANCE_COVERAGE_PERCENT=35
GEOPULSE_TIMELINE_MAP_MATCHING_QUALITY_MAX_DISCONTINUITY_PERCENT=10
GEOPULSE_TIMELINE_MAP_MATCHING_QUALITY_MAX_SHORT_DISCONTINUITY_METERS=100
```

The global setting enables the integration. The automatic and backfill settings control precomputation for all users. Users can separately enable **Profile -> Display Settings -> Map Matching** only after the integration is globally enabled and Valhalla is configured; this opt-in displays matched geometry and queues missing visible trips on demand.

## Background processing

- Automatic matching consumes committed timeline-change events and waits for the configured quiet period (15 minutes by default). Repeated changes extend the quiet period so provisional trips are not repeatedly sent to Valhalla.
- Historical backfill scans all trip owners oldest-first. Per-user cursors and inspected/total trip counters are stored in PostgreSQL, so disabling the option or restarting GeoPulse pauses rather than loses progress.
- Visible on-demand work has priority over automatic recent work, which has priority over historical work.
- Long traces are split into contiguous chunks with a one-point overlap at size boundaries. Recording gaps remain separate rendered segments, and a trip is published only after every eligible chunk succeeds. If Valhalla only matches a disconnected or very small portion of a continuous chunk, GeoPulse skips that match and keeps the raw GPS path visible. The partial-match quality thresholds are available in **Admin -> System Settings -> Map Matching -> Advanced configuration**.
- HTTP 408/429 responses, provider 5xx responses, and transport failures retry with increasing delays up to the configured attempt limit. Deterministic provider 4xx responses fail immediately and retain raw GPS as the display fallback. Processing claims older than 15 minutes are recovered after a restart or interrupted worker run.
- **Admin -> System Settings -> Map Matching -> Processing Status** shows durable historical scan progress, active queue depth, recent activity, and expandable diagnostics. Queue changes wake the worker immediately and a watchdog checks for due work every 15 seconds.
- External trip states are `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED`, `SKIPPED`, and `UNAVAILABLE`.
- The initial `/api/map-matching/resolve` call computes cache identity and queues missing work. Subsequent `/api/map-matching/status` polling reads owned target state only and returns geometry once when complete.
- Structured worker logs and `geopulse.map_matching.*` metrics report queue depth, target outcomes, stale recovery, worker duration, and Valhalla latency/outcomes.

## Running Valhalla

Use a Geofabrik extract for the area you actually travel in. Avoid planet data unless you have a machine sized for it.

Example outline using the `gis-ops/docker-valhalla` image:

```bash
mkdir -p valhalla
cd valhalla

# Example: replace with the extract that matches your region.
curl -L -o region.osm.pbf https://download.geofabrik.de/europe/ukraine-latest.osm.pbf

# Build config, tiles, and tile archive. The image entrypoint accepts build_tiles,
# not direct valhalla_build_* commands.
docker run --rm \
  -v "$PWD:/custom_files" \
  -e tile_urls=/custom_files/region.osm.pbf \
  -e serve_tiles=False \
  ghcr.io/gis-ops/docker-valhalla/valhalla:latest build_tiles

# Serve the generated tiles.
docker run -d \
  --name valhalla \
  -p 8002:8002 \
  -v "$PWD:/custom_files" \
  ghcr.io/gis-ops/docker-valhalla/valhalla:latest
```

The exact Valhalla image and setup flags can vary by deployment. Confirm the container responds:

```bash
curl http://localhost:8002/status
```

## Hardware guidance

Approximate requirements depend mainly on extract size:

- City or small region: 2 CPU, 2-4 GB RAM, 5-20 GB SSD.
- Country or large region: 4 CPU, 8-16 GB RAM, 30-150 GB SSD.
- Planet-scale builds: 4-16 CPU, 32-64 GB RAM for build, and hundreds of GB of fast SSD.

## Cache operations

- Stable new trips are queued automatically for all users when the admin automatic setting is enabled.
- Timeline views queue missing visible trips on demand.
- Historical backfill is controlled globally and is safely resumable.
- Cached entries are keyed by user, provider, Valhalla costing profile, relevant map-matching config, and raw GPS input hash. The current timeline trip id is only an attachment for API ownership and UI status.
- GPS database point ids are excluded from the input hash. Regenerating timeline rows, or recreating identical GPS rows with different database ids, can still reuse the same matched geometry.
- Cache identity includes the Valhalla base URL and an algorithm revision. If raw GPS, provider configuration, or the matching algorithm changes, the old cache is ignored and a new target is queued.
- Full timeline regeneration does not drop reusable map-matching data. Deleted trips detach their cache rows; regenerated trips reattach matching rows when the stable cache key is the same.
- Detached terminal cache rows are cleaned up after 30 days. Detached pending or processing rows are cleaned up after one hour because they cannot be claimed until a current trip reattaches them.
