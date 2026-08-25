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
- Leave Map Matching disabled globally until Valhalla responds successfully from the GeoPulse backend network.
