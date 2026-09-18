---
title: Valhalla Map Matching
description: Configure Valhalla so GeoPulse can display trips matched to OpenStreetMap roads and paths.
---

# Valhalla Map Matching

GeoPulse uses Valhalla as the self-hosted routing service behind Map Matching. Valhalla reads OpenStreetMap data and
provides an API that can match GPS traces to roads and paths.

For users, this makes trip routes cleaner on the timeline map. For the system, it remains display-only: raw GPS points
stay authoritative, and timeline generation, movement classification, exports, and analytics continue to use the original
GPS data.

For the user-facing feature explanation, see [Map Matching](/docs/user-guide/timeline/map-matching).

## How It Fits Together

To enable Map Matching in GeoPulse:

1. Run a Valhalla service with map data for the areas your users travel in.
2. Configure GeoPulse to use that Valhalla base URL.
3. Enable Map Matching in **Admin -> System Settings -> Map Matching**.
4. Users can then enable **Profile -> Display Settings -> Map Matching** for their own view.

GeoPulse falls back to raw GPS paths when Valhalla is unavailable, a route cannot be matched confidently, or a trip type
is not suitable for road/path matching.

## GeoPulse Settings

The minimal environment configuration is:

```bash
GEOPULSE_TIMELINE_MAP_MATCHING_ENABLED=true
GEOPULSE_TIMELINE_MAP_MATCHING_PROVIDER=valhalla
GEOPULSE_TIMELINE_MAP_MATCHING_VALHALLA_BASE_URL=http://valhalla:8002
```

The same settings can be managed from **Admin -> System Settings -> Map Matching**. Admin UI values are stored in the
database, override environment defaults, and apply at runtime.

Common operational toggles:

```bash
GEOPULSE_TIMELINE_MAP_MATCHING_AUTOMATIC_ENABLED=true
GEOPULSE_TIMELINE_MAP_MATCHING_BACKFILL_ENABLED=true
GEOPULSE_TIMELINE_MAP_MATCHING_AUTOMATIC_QUIET_PERIOD_MINUTES=15
```

- Automatic matching prepares stable new trips after the quiet period.
- Historical backfill gradually prepares matched routes for older trips.
- Users can still trigger matching for visible trips on demand when they view the timeline, depending on system state.

For every supported environment variable, see the
[Environment Variables Reference](/docs/getting-started/deployment/environment-variables).

## Running Valhalla

Use an OpenStreetMap extract for the area your users actually travel in. Full Europe data is large, so a country or
sub-region extract is usually a better first deployment unless your users regularly travel across many countries.

Useful sources:

- [Valhalla documentation](https://valhalla.github.io/valhalla/)
- [Valhalla Map Matching API reference](https://valhalla.github.io/valhalla/api/map-matching/)
- [Geofabrik download server](https://download.geofabrik.de)

The example below uses a Europe continent extract path. Select the continent, country, or sub-region extract that matches
where your users actually travel.

Example outline using the `gis-ops/docker-valhalla` image:

```bash
mkdir -p valhalla
cd valhalla

# Full Europe is large. Prefer a country or sub-region extract when possible.
curl -L -o region.osm.pbf https://download.geofabrik.de/europe/germany-latest.osm.pbf

# One-time container to build config, tiles, and tile archive. The image entrypoint
# accepts build_tiles, not direct valhalla_build_* commands.
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

Confirm the service responds:

```bash
curl http://localhost:8002/status
```

If GeoPulse and Valhalla run in the same Docker Compose network, set
`GEOPULSE_TIMELINE_MAP_MATCHING_VALHALLA_BASE_URL` to the Valhalla service name, for example
`http://valhalla:8002`. If GeoPulse runs outside that network, use a URL it can reach, such as
`http://localhost:8002` only when both processes share the same host network context.

## Europe Extract Choices

Geofabrik provides one full Europe extract and many country/sub-region extracts. Choose the smallest extract that covers
your users' expected travel area:

- City or small region: fastest build, lowest disk and memory usage.
- Country or large region: good default for personal and family instances.
- Full Europe: only use when you have enough disk, memory, and build time.

Approximate requirements depend mainly on extract size:

| Area | Suggested resources |
|------|---------------------|
| City or small region | 2 CPU, 2-4 GB RAM, 5-20 GB SSD |
| Country or large region | 4 CPU, 8-16 GB RAM, 30-150 GB SSD |
| Full Europe or planet-scale | 4-16 CPU, 32-64 GB RAM for build, hundreds of GB of fast SSD |

## Operational Notes

- Keep Valhalla map data aligned with where users travel. Trips outside the loaded extract fall back to raw GPS paths.
- Rebuild Valhalla tiles when you update the OpenStreetMap extract.
- Start with conservative worker settings, then raise batch size or backfill activity after confirming Valhalla has enough
  CPU and memory.

## Maintaining Historical Matches

The **Processing Status** card in **Admin → Settings → Map Matching** has one maintenance action, **Re-run Map
Matching**, which asks how much of the history to cover:

- **Retry failed and skipped trips** (default) moves every trip whose match failed or was skipped back into the queue and
  restarts the historical scan. Trips that are already matched keep their stored geometry, so this only spends Valhalla
  calls on the trips that need them. Run it after fixing a Valhalla problem, for example when tiles were still building
  while the earlier attempts were made.
- **Re-match all trips** deletes every stored result, matched routes included, and matches the whole history again. Use it
  when the map data changed and you want existing matches recomputed. Routes fall back to their raw GPS paths until they
  are matched again.

Changing a setting that affects matching (the Valhalla URL, input point limit, duration limit, or a quality threshold)
does **not** re-match past trips by itself. Trips keep the routes they already have: each one is matched again when it is
next viewed in the timeline, so nothing stays permanently out of date. To update every past trip in one go, use the
**Re-run Map Matching** prompt that appears after saving such a setting, or the button in the same card. Setting the URL through
environment variables behaves the same way — after changing it, re-run matching if you want history updated in bulk
rather than trip by trip.

## Troubleshooting

Find a trip that still shows its raw GPS path and right-click it (long-press on touch devices): **Map matching
details...** shows what map matching did with that trip — when a refined route was produced, that it is still queued, or
why it was not refined. Administrators also get a button straight to this settings page so they can re-run matching.

| Reason | What it means |
|--------|---------------|
| The routing engine could not find a road or path for this trip. | Valhalla could not snap the trace to the loaded map data. Verify the extract covers the trip area and that tile building finished. |
| The routing engine has no map data for this area. | The trip lies outside the loaded extract. Build tiles for a wider region. |
| The routing engine rejected this trip's GPS trace. | Valhalla returned an error for this specific trace. |
| The routing engine was temporarily unavailable. | Connection or server-side failure. Matching retries until `map-matching.max-attempts` is reached. |
| Trip has fewer than two eligible GPS points. | The timeline accuracy filter or a short trip left too few points to match. |
| Trip exceeds configured map-matching duration limit. | The trip is longer than `map-matching.max-trip-duration-hours`. |
| Movement type is not supported by road/path map matching. | Train, flight, boat, and unknown trips keep their raw GPS paths. |

If the cause has been fixed since the trip was attempted, use **Re-run Map Matching → Retry failed and skipped trips** to
re-queue it.
- Leave Map Matching disabled globally until Valhalla responds successfully from the GeoPulse backend network.
