# Backend Scripts

## Demo database reset

`demo/geopulse-demo-db.sh` is an external operations helper for a separate public demo instance. It does not run inside GeoPulse.

### How it works

The demo always serves a copy of one saved seed snapshot whose dates have been moved forward:

1. The snapshot is restored into a staging database (`<db>_demo_next`) while the demo keeps serving the current data.
2. Short-lived auth tables are cleared, and every `date`/`timestamp` column is shifted by **whole days** so the latest GPS point lands within the last 24 hours. Times of day are kept (a 08:00 commute stays at 08:00), and no data ends up in the future. The shift is always measured from the untouched snapshot, so resets never drift.
3. Only then is the backend stopped, the staging database renamed to the live name in one transaction, and the backend recreated. Downtime is the backend restart, regardless of how much seed data there is.
4. If the backend does not become healthy, the previous database is swapped back and the failed one is kept as `<db>_demo_next` for inspection. If anything fails before the swap, the running demo is not touched.

`reset --if-due` only does this when the live data's latest GPS point is a full day old, so cron can run it every 15 minutes. That gives one short restart per day, shortly after the seed's last-point time of day (UTC) comes around again. Each daily reset also wipes whatever background jobs wrote since the last one.

### Setup

```bash
# 1. Prepare the demo users and their data (see "Seed data" below), then save the seed:
COMPOSE_PROJECT_DIR=/srv/projects/demo/geopulse \
  backend/scripts/demo/geopulse-demo-db.sh snapshot

# 2. Test a reset immediately:
COMPOSE_PROJECT_DIR=/srv/projects/demo/geopulse \
  backend/scripts/demo/geopulse-demo-db.sh reset

# 3. Install the "reset --if-due" job (every 15 minutes) in /etc/cron.d/geopulse-demo-reset:
sudo env COMPOSE_PROJECT_DIR=/srv/projects/demo/geopulse \
  backend/scripts/demo/geopulse-demo-db.sh install-cron

# Any time: show the age of the live data and whether a reset is due
COMPOSE_PROJECT_DIR=/srv/projects/demo/geopulse \
  backend/scripts/demo/geopulse-demo-db.sh status
```

After taking a new snapshot, run `reset` once by hand; `--if-due` only looks at data age, not at whether the snapshot changed. Re-running `install-cron` overwrites the existing cron file, which also replaces the old midnight job.

Requirements:

- The database user must be able to create, rename, and drop databases. The default compose setup qualifies, because `POSTGRES_USER` is a superuser.
- Free disk space for a second copy of the demo database during a reset.

Defaults:

- Snapshot: `$COMPOSE_PROJECT_DIR/demo-seed/geopulse-demo.snapshot.dump`
- Compose services: `geopulse-postgres`, `geopulse-backend`
- Services stopped during the swap: `DEMO_APP_SERVICES` (default: the backend only; the UI stays up)
- Cron schedule: `DEMO_CRON_SCHEDULE="*/15 * * * *"`
- Backend health wait: `DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS=120`

### Seed data

- **Length:** about 60 days of everyday data per persona. A week is too short for the demo to show much: week/month comparisons, "last month", monthly digests, badges, and place visit counts all need repetition across weeks. Sixty days keeps the whole previous calendar month plus the current month-to-date in range at all times. A few sparse older trips spread over the past year make yearly stats and coverage look realistic too. Because only the restart is downtime, a larger seed only makes the background preparation step slower.
- **Last point:** end every persona's data at roughly the same UTC time of day. The data rolls forward at that time each day. Between a persona's local midnight and that moment, "today" is still empty and yesterday is the latest full day.
- **Default view:** in each demo user's profile, set the default timeline date range to *Last week* before taking the snapshot, so the first screen never opens on an empty "today".
- **Noise:** turn off GPS-silence and similar notifications for the demo users, because no new GPS points will arrive.
- **Authoring:** prepare the data with `GEOPULSE_DEMO_MODE=false` (temporarily on the demo instance, or on another instance with the same GeoPulse version), then take the snapshot.
- **Upgrades:** an older snapshot still works after a GeoPulse upgrade, because Flyway migrates it when the backend starts. That adds the migration time to every restart, so take a fresh snapshot after an upgrade.

Recommended demo instance environment:

```bash
GEOPULSE_VERSION=demo
GEOPULSE_DEMO_MODE=true
GEOPULSE_DEMO_ADMIN_READ_ONLY_ENABLED=true
GEOPULSE_AUTH_REGISTRATION_ENABLED=false
GEOPULSE_AUTH_PASSWORD_REGISTRATION_ENABLED=false
GEOPULSE_AUTH_OIDC_REGISTRATION_ENABLED=false
```

Use `.github/workflows/demo-tag.yml` to promote an already-built Docker Hub version to the permanent demo tags consumed by `GEOPULSE_VERSION=demo`: `tess1o/geopulse-ui:demo` and `tess1o/geopulse-backend:demo-native`. This keeps normal dev builds separate from the demo instance; `dev-build.yml` still only updates the `dev` tags.

If the demo compose file uses different service names, set `POSTGRES_SERVICE` and `BACKEND_SERVICE`. If other services connect to the database, include them in `DEMO_APP_SERVICES` so they are stopped while the databases are swapped.

## Maintainer water dataset artifact

Boat setup imports a versioned `geopulse-water-surfaces-v1.copy.gz` artifact at runtime. Normal users should not run shapefile import scripts.

Maintainer flow:

```bash
PGHOST=localhost PGPORT=5432 PGDATABASE=geopulse PGUSER=postgres \
  backend/scripts/maintainer/import-water-source-data.sh

PGHOST=localhost PGPORT=5432 PGDATABASE=geopulse PGUSER=postgres \
  backend/scripts/maintainer/export-water-surface-artifact.sh
```

Publish both files from `dist/water-dataset/` to a GitHub Release:

- `geopulse-water-surfaces-v1.copy.gz`
- `geopulse-water-surfaces-v1.manifest.json`

Then configure production with:

```bash
GEOPULSE_WATER_DATASET_URL=https://github.com/tess1o/GeoPulse/releases/download/water-surfaces-v1/geopulse-water-surfaces-v1.copy.gz
GEOPULSE_WATER_DATASET_SHA256=<manifest sha256>
```

Optional timeout overrides for slow networks or proxies:

```bash
GEOPULSE_WATER_DATASET_CONNECT_TIMEOUT_SECONDS=30
GEOPULSE_WATER_DATASET_DOWNLOAD_TIMEOUT_HOURS=6
GEOPULSE_WATER_DATASET_DOWNLOAD_STALL_TIMEOUT_SECONDS=120
GEOPULSE_WATER_DATASET_SETUP_START_TIMEOUT_MINUTES=5
```

Offline installs can mount the artifact and set:

```bash
GEOPULSE_WATER_DATASET_LOCAL_PATH=/data/geopulse-water-surfaces-v1.copy.gz
GEOPULSE_WATER_DATASET_SHA256=<manifest sha256>
```

HydroLAKES is distributed under CC-BY 4.0. Natural Earth data is public domain.
