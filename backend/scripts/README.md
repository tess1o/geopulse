# Backend Scripts

## Demo database reset

`demo/geopulse-demo-db.sh` is an external operations helper for a separate public demo instance. It does not run inside GeoPulse. The intended flow is:

```bash
# On the demo VPS, after manually preparing 1-10 demo users and their data:
COMPOSE_PROJECT_DIR=/srv/projects/demo/geopulse \
  backend/scripts/demo/geopulse-demo-db.sh snapshot

# Test a restore immediately:
COMPOSE_PROJECT_DIR=/srv/projects/demo/geopulse \
  backend/scripts/demo/geopulse-demo-db.sh reset

# Install a midnight daily reset in /etc/cron.d/geopulse-demo-reset:
sudo env COMPOSE_PROJECT_DIR=/srv/projects/demo/geopulse \
  backend/scripts/demo/geopulse-demo-db.sh install-cron
```

Defaults:

- Snapshot: `$COMPOSE_PROJECT_DIR/demo-seed/geopulse-demo.snapshot.dump`
- User limit: `DEMO_MAX_USERS=10`
- Compose services: `geopulse-postgres`, `geopulse-backend`, `geopulse-ui`
- Backend health wait: `DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS=120`

The reset command stops the app services, restores the saved snapshot, trims users above `DEMO_MAX_USERS`, shifts all `date`/`timestamp` columns so the latest `gps_points.timestamp` date becomes `DEMO_TARGET_DATE` (default: the host date), clears short-lived auth/API-token tables, recreates the backend container, starts the remaining app services, and waits for the backend Docker healthcheck to become healthy.

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

If the demo compose file uses different service names, set `POSTGRES_SERVICE`, `BACKEND_SERVICE`, and `UI_SERVICE`. If additional public-facing services can write or queue writes during reset, include them in `DEMO_APP_SERVICES`.

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
