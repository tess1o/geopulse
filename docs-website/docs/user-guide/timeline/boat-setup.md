# Boat Setup

Boat detection needs a global water-surface dataset before GeoPulse can classify trips as **BOAT**.

When you enable Boat detection, GeoPulse downloads a versioned dataset artifact, imports it into PostGIS, and pre-calculates whether your GPS points are on water. The download is about 800-900 MB, and the imported database table is larger because it includes geometry indexes.

## Online Setup

1. Open **Timeline Preferences**.
2. Enable **Boat** in **Trip Classification**.
3. Confirm the setup warning.
4. Keep GeoPulse running while the setup progress completes.

Timeline generation waits until Boat setup is ready. GPS inserts continue normally; missing water evidence is filled in by background processing.

## Offline Setup

Download the release artifact from the GeoPulse GitHub Release on a machine with internet access:

- `geopulse-water-surfaces-v1.copy.gz`
- `geopulse-water-surfaces-v1.manifest.json`

Copy the `.copy.gz` file to the server running GeoPulse and mount it into the backend container, for example:

```yaml
services:
  geopulse-backend:
    volumes:
      - ./water-dataset/geopulse-water-surfaces-v1.copy.gz:/data/geopulse-water-surfaces-v1.copy.gz:ro
```

Set the environment variables from the manifest:

```bash
GEOPULSE_WATER_DATASET_LOCAL_PATH=/data/geopulse-water-surfaces-v1.copy.gz
GEOPULSE_WATER_DATASET_SHA256=<sha256 from manifest>
```

Restart the backend, then enable Boat detection again from Timeline Preferences.

## Download Timeouts

Online setup downloads a large artifact. The defaults are intentionally conservative:

```bash
GEOPULSE_WATER_DATASET_CONNECT_TIMEOUT_SECONDS=30
GEOPULSE_WATER_DATASET_DOWNLOAD_TIMEOUT_HOURS=6
GEOPULSE_WATER_DATASET_DOWNLOAD_STALL_TIMEOUT_SECONDS=120
GEOPULSE_WATER_DATASET_SETUP_START_TIMEOUT_MINUTES=5
```

Increase these values if the server has a slow network connection or downloads through a proxy.

## Troubleshooting

- If setup fails with a download error, use the offline setup path above.
- If setup fails with a checksum error, re-download the artifact and confirm the SHA-256 value matches the manifest.
- If setup is interrupted, start it again from Timeline Preferences. GeoPulse reuses completed work where possible.
