---
title: Import Configuration
description: Configure data import settings including chunked uploads for large files.
---

# Import Configuration

GeoPulse provides configurable import settings to handle files of various sizes efficiently. This page covers all import-related environment variables.

## Overview

The import system supports:
- **Streaming parsers** - Process files of any size with minimal memory usage
- **Chunked uploads** - Bypass CDN upload limits (e.g., Cloudflare's 100MB limit) by splitting large files
- **Temporary file storage** - Handle large files without loading them entirely into memory

## Environment Variables

### Batch Processing

| Variable | Default | Description |
|----------|---------|-------------|
| `GEOPULSE_IMPORT_BULK_INSERT_BATCH_SIZE` | `500` | Number of GPS points to insert in a single database batch operation |
| `GEOPULSE_IMPORT_MERGE_BATCH_SIZE` | `250` | Batch size when merging data with duplicate detection |
| `GEOPULSE_IMPORT_GEOJSON_STREAMING_BATCH_SIZE` | `500` | Batch size for streaming GeoJSON parser |
| `GEOPULSE_IMPORT_GOOGLETIMELINE_STREAMING_BATCH_SIZE` | `500` | Batch size for streaming Google Timeline parser |
| `GEOPULSE_IMPORT_GPX_STREAMING_BATCH_SIZE` | `500` | Batch size for streaming GPX parser |
| `GEOPULSE_IMPORT_CSV_STREAMING_BATCH_SIZE` | `500` | Batch size for streaming CSV parser |
| `GEOPULSE_IMPORT_OWNTRACKS_STREAMING_BATCH_SIZE` | `500` | Batch size for streaming OwnTracks parser |

### Temporary File Storage

For large files, GeoPulse uses temporary file storage to avoid memory issues:

| Variable | Default | Description |
|----------|---------|-------------|
| `GEOPULSE_IMPORT_TEMP_DIR` | `/tmp/geopulse/imports` | Directory for temporary import files |
| `GEOPULSE_IMPORT_LARGE_FILE_THRESHOLD_MB` | `100` | Files larger than this are stored as temp files instead of in memory |
| `GEOPULSE_IMPORT_TEMP_FILE_RETENTION_HOURS` | `24` | How long to keep temporary import files before cleanup |

### Chunked Uploads

Chunked uploads allow importing files larger than CDN limits (e.g., Cloudflare's 100MB limit). Files are split into smaller chunks on the frontend and reassembled on the backend.

| Variable | Default | Description |
|----------|---------|-------------|
| `GEOPULSE_IMPORT_CHUNK_SIZE_MB` | `50` | Size of each upload chunk in megabytes |
| `GEOPULSE_IMPORT_MAX_FILE_SIZE_GB` | `10` | Maximum file size allowed for imports |
| `GEOPULSE_IMPORT_UPLOAD_TIMEOUT_HOURS` | `2` | How long an upload session remains valid |
| `GEOPULSE_IMPORT_UPLOAD_CLEANUP_MINUTES` | `15` | Interval for cleaning up expired upload sessions |
| `GEOPULSE_IMPORT_CHUNKS_DIR` | `/tmp/geopulse/chunks` | Directory for storing upload chunks during assembly |

## Configuration Examples

### Docker Compose

```yaml
services:
  geopulse-backend:
    environment:
      # Allow larger files (up to 20GB)
      GEOPULSE_IMPORT_MAX_FILE_SIZE_GB: 20

      # Increase chunk size for faster uploads on good connections
      GEOPULSE_IMPORT_CHUNK_SIZE_MB: 100

      # Longer timeout for slow connections
      GEOPULSE_IMPORT_UPLOAD_TIMEOUT_HOURS: 4

      # Custom temp directories (useful for persistent storage)
      GEOPULSE_IMPORT_TEMP_DIR: /data/geopulse/imports
      GEOPULSE_IMPORT_CHUNKS_DIR: /data/geopulse/chunks
```

### Kubernetes / Helm

In your `values.yaml`:

```yaml
backend:
  env:
    GEOPULSE_IMPORT_MAX_FILE_SIZE_GB: "20"
    GEOPULSE_IMPORT_CHUNK_SIZE_MB: "100"
    GEOPULSE_IMPORT_UPLOAD_TIMEOUT_HOURS: "4"
```

## Performance Tuning

### For Large Imports (>1GB)

If you regularly import large files:

1. **Increase max file size:**
   ```
   GEOPULSE_IMPORT_MAX_FILE_SIZE_GB=20
   ```
2. **Increase upload timeout** for slow connections:
   ```
   GEOPULSE_IMPORT_UPLOAD_TIMEOUT_HOURS=4
   ```

### For High-Throughput Systems

If you have many concurrent imports:

1. **Increase batch sizes** for faster processing:
   ```
   GEOPULSE_IMPORT_BULK_INSERT_BATCH_SIZE=1000
   GEOPULSE_IMPORT_MERGE_BATCH_SIZE=500
   ```

2. **More frequent cleanup** to free disk space:
   ```
   GEOPULSE_IMPORT_UPLOAD_CLEANUP_MINUTES=5
   ```

### For Memory-Constrained Systems

If running on limited RAM:

1. **Lower the large file threshold** to use temp files more often:
   ```
   GEOPULSE_IMPORT_LARGE_FILE_THRESHOLD_MB=50
   ```

2. **Reduce batch sizes:**
   ```
   GEOPULSE_IMPORT_BULK_INSERT_BATCH_SIZE=250
   ```

:::tip Cleanup
Temporary files are automatically cleaned up after successful imports. Failed or abandoned uploads are cleaned up based on `GEOPULSE_IMPORT_UPLOAD_CLEANUP_MINUTES` and `GEOPULSE_IMPORT_TEMP_FILE_RETENTION_HOURS`.
:::

## Troubleshooting

### Upload fails with "Request Entity Too Large"

This is typically a frontend (Nginx) limit, not a backend limit. See [Frontend Configuration](/docs/system-administration/configuration/frontend) to increase `CLIENT_MAX_BODY_SIZE`.

### Upload times out

Increase the upload timeout:
```
GEOPULSE_IMPORT_UPLOAD_TIMEOUT_HOURS=4
```

### Disk space fills up during imports

1. Ensure cleanup is running frequently:
   ```
   GEOPULSE_IMPORT_UPLOAD_CLEANUP_MINUTES=5
   ```

2. Reduce temp file retention:
   ```
   GEOPULSE_IMPORT_TEMP_FILE_RETENTION_HOURS=12
   ```

3. Use a dedicated volume with sufficient space for temp directories.
