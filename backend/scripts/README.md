# Backend Scripts

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
