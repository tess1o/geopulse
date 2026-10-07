---
id: examples
title: API Examples
description: curl examples for reading the timeline and GPS points, and for importing and exporting GPX and OwnTracks files.
---

These examples show common tasks with `curl`. They authenticate with a user [API token](./api-tokens.md) and use
[`jq`](https://jqlang.org/) to read values from JSON responses.

Set the server URL and your token once, so you can copy the commands as they are:

```bash
export GEOPULSE_URL="http://localhost:8080"
export GEOPULSE_TOKEN="<your-api-token>"
```

If a request fails, the response body describes the error. See [Errors](./intro.md#errors) for the format.

## Get the timeline for a date range

`GET /api/v1/timeline` returns the stays, trips, and data gaps between `from` and `to`. Both parameters are ISO-8601
instants in UTC, such as `2026-05-01T00:00:00Z`.

```bash
curl -sS -G "$GEOPULSE_URL/api/v1/timeline" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  --data-urlencode "from=2026-05-01T00:00:00Z" \
  --data-urlencode "to=2026-05-31T23:59:59Z"
```

The response (abbreviated) looks like:

```json
{
  "userId": "3f6c1d2e-8a4b-4c5d-9e0f-1a2b3c4d5e6f",
  "stays": [
    {
      "id": 1201,
      "timestamp": "2026-05-01T06:12:40Z",
      "locationName": "Home",
      "city": "Kyiv",
      "country": "Ukraine",
      "stayDuration": 9360,
      "latitude": 50.4501,
      "longitude": 30.5234
    }
  ],
  "trips": [
    {
      "id": 845,
      "timestamp": "2026-05-01T08:48:40Z",
      "latitude": 50.4501,
      "longitude": 30.5234,
      "endLatitude": 50.4422,
      "endLongitude": 30.5367,
      "tripDuration": 1260,
      "distanceMeters": 4870,
      "movementType": "CAR"
    }
  ],
  "dataGaps": [],
  "staysCount": 1,
  "tripsCount": 1,
  "dataGapsCount": 0
}
```

`stayDuration` and `tripDuration` are in seconds.

## Get raw GPS points for a date range

### As JSON, page by page

`GET /api/v1/gps/points` returns raw GPS points one page at a time. `from` and `to` accept ISO-8601 instants or plain
dates (`2026-05-01`). Plain dates are read in UTC: `from` starts at `00:00:00` and `to` ends at `23:59:59`.

| Parameter       | Default     | Description                      |
|-----------------|-------------|----------------------------------|
| `page`          | `1`         | Page number, starting at 1.      |
| `size`          | `50`        | Points per page, from 1 to 1000. |
| `sortBy`        | `timestamp` | Field to sort by.                |
| `sortDirection` | `desc`      | `asc` or `desc`.                 |

```bash
curl -sS -G "$GEOPULSE_URL/api/v1/gps/points" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  --data-urlencode "from=2026-05-01" \
  --data-urlencode "to=2026-05-31" \
  --data-urlencode "page=1" \
  --data-urlencode "size=1000" \
  --data-urlencode "sortDirection=asc"
```

```json
{
  "items": [
    {
      "id": 982341,
      "timestamp": "2026-05-01T00:00:12Z",
      "coordinates": {
        "lat": 50.4501,
        "lng": 30.5234
      },
      "accuracy": 8.0,
      "battery": 87.0,
      "velocity": 0.0,
      "altitude": 179.0,
      "sourceType": "OWNTRACKS"
    }
  ],
  "page": 1,
  "size": 1000,
  "totalElements": 41250,
  "totalPages": 42
}
```

### As CSV, in one request

`GET /api/v1/gps/points/exports` streams every matching point as CSV, without paging. It takes the same `from`, `to`,
and filter parameters:

```bash
curl -sS -G "$GEOPULSE_URL/api/v1/gps/points/exports" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  --data-urlencode "from=2026-05-01" \
  --data-urlencode "to=2026-05-31" \
  -o gps-points-may-2026.csv
```

## Import a GPX or OwnTracks file

Imports run in the background. Upload the file to `POST /api/v1/imports` as `multipart/form-data`, then check the
import job until it finishes. You can run only one import at a time.

| Form field | Required | Description                                                                           |
|------------|----------|---------------------------------------------------------------------------------------|
| `file`     | Yes      | The file to import.                                                                   |
| `format`   | Yes      | `gpx` or `owntracks`. Other formats: `google-timeline`, `geojson`, `csv`, `geopulse`. |
| `options`  | No       | JSON import options, sent with the `application/json` content type. See below.        |

Accepted file types:

- `gpx`: a single `.gpx` file, or a `.zip` archive of GPX files.
- `owntracks`: a `.json` file in OwnTracks format.

### Upload a GPX file

```bash
curl -sS -X POST "$GEOPULSE_URL/api/v1/imports" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  -F "file=@ride.gpx" \
  -F "format=gpx"
```

### Upload an OwnTracks file

```bash
curl -sS -X POST "$GEOPULSE_URL/api/v1/imports" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  -F "file=@owntracks-export.json" \
  -F "format=owntracks"
```

### Import options

Use `options` to import only part of a file or to replace existing data:

```bash
curl -sS -X POST "$GEOPULSE_URL/api/v1/imports" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  -F "file=@owntracks-export.json" \
  -F "format=owntracks" \
  -F 'options={"startTime":"2026-05-01T00:00:00Z","endTime":"2026-05-31T23:59:59Z","clearDataBeforeImport":true};type=application/json'
```

| Option                  | Description                                                                                                      |
|-------------------------|------------------------------------------------------------------------------------------------------------------|
| `startTime`, `endTime`  | Import only points between these ISO-8601 instants. Send both or neither. Without them, all points are imported. |
| `clearDataBeforeImport` | Delete your existing data in the imported date range before importing. Defaults to `false`.                      |

### Check the import status

The upload response contains the job ID:

```json
{
  "importJobId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "validating",
  "phase": "validating",
  "uploadedFileName": "ride.gpx",
  "fileSizeBytes": 482113,
  "progress": 0
}
```

Poll `GET /api/v1/imports/{importJobId}` until `status` is `completed` or `failed`:

```bash
job_id=$(curl -sS -X POST "$GEOPULSE_URL/api/v1/imports" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  -F "file=@ride.gpx" \
  -F "format=gpx" | jq -r '.importJobId')

while true; do
  job=$(curl -sS "$GEOPULSE_URL/api/v1/imports/$job_id" -H "X-API-Key: $GEOPULSE_TOKEN")
  status=$(echo "$job" | jq -r '.status')
  echo "$status $(echo "$job" | jq -r '.phase') $(echo "$job" | jq -r '.progress')%"
  case "$status" in
    completed) break ;;
    failed) echo "$job" | jq '.error'; exit 1 ;;
  esac
  sleep 5
done
```

After the points are imported, GeoPulse regenerates the timeline for the imported period. The `phase` field shows the
current step, such as `importing` or `timeline_generation`.

:::tip Large files
A reverse proxy in front of GeoPulse, such as Cloudflare or nginx, may reject large uploads. If it does, raise its
upload limit or call the backend directly. The web app works around such limits by uploading files larger than 80 MB in
chunks through internal endpoints.
:::

## Export to GPX or OwnTracks

Exports also run in the background. Create an export job with `POST /api/v1/exports`, wait until it completes, then
download the file.

The request body:

| Field             | Required | Description                                                                                           |
|-------------------|----------|-------------------------------------------------------------------------------------------------------|
| `format`          | No       | `gpx` or `owntracks`. Other formats: `geojson`, `csv`, `geopulse` (default).                          |
| `startTime`       | Yes      | Start of the range, as an ISO-8601 instant.                                                           |
| `endTime`         | Yes      | End of the range, as an ISO-8601 instant.                                                             |
| `gpxLayout`       | No       | GPX only. See [GPX layouts](#create-a-gpx-export).                                                    |
| `owntracksLayout` | No       | OwnTracks only. See [OwnTracks layouts](#create-an-owntracks-export).                                 |
| `dataTypes`       | No       | `geopulse` only: data to include in the archive. Defaults to `["rawgps"]`. Other formats ignore it and always export raw GPS points. |

### Create a GPX export

```bash
curl -sS -X POST "$GEOPULSE_URL/api/v1/exports" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "format": "gpx",
    "startTime": "2026-05-01T00:00:00Z",
    "endTime": "2026-05-31T23:59:59Z"
  }'
```

`gpxLayout` controls how the export is packaged:

| `gpxLayout`        | Result                                                  |
|--------------------|---------------------------------------------------------|
| `single` (default) | One GPX file with all trips and stays.                  |
| `zip-per-trip`     | A ZIP archive with one GPX file per trip or stay.       |
| `zip-per-day`      | A ZIP archive with one GPX file per day.                |

For example, add `"gpxLayout": "zip-per-day"` to the body above.

### Create an OwnTracks export

```bash
curl -sS -X POST "$GEOPULSE_URL/api/v1/exports" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "format": "owntracks",
    "startTime": "2026-05-01T00:00:00Z",
    "endTime": "2026-05-31T23:59:59Z"
  }'
```

`owntracksLayout` controls the JSON shape:

| `owntracksLayout` | Result                                                                   |
|-------------------|--------------------------------------------------------------------------|
| `ocat` (default)  | `{"locations": [...]}`, the format OwnTracks Recorder's `ocat` produces. |
| `array`           | A plain JSON array of location messages.                                 |

### Wait for the export and download it

The create response contains the job ID:

```json
{
  "exportJobId": "a3bb189e-8bf9-3888-9912-ace4e6543002",
  "format": "gpx",
  "status": "processing",
  "progress": 0,
  "dataTypes": ["rawgps"],
  "startTime": "2026-05-01T00:00:00Z",
  "endTime": "2026-05-31T23:59:59Z"
}
```

Poll `GET /api/v1/exports/{exportJobId}` until `status` is `completed`, then download the file from
`GET /api/v1/exports/{exportJobId}/content`. The completed job also includes this path in `downloadUrl`, and
`expiresAt` shows when the download expires.

```bash
job_id=$(curl -sS -X POST "$GEOPULSE_URL/api/v1/exports" \
  -H "X-API-Key: $GEOPULSE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "format": "gpx",
    "startTime": "2026-05-01T00:00:00Z",
    "endTime": "2026-05-31T23:59:59Z"
  }' | jq -r '.exportJobId')

while true; do
  job=$(curl -sS "$GEOPULSE_URL/api/v1/exports/$job_id" -H "X-API-Key: $GEOPULSE_TOKEN")
  status=$(echo "$job" | jq -r '.status')
  echo "$status $(echo "$job" | jq -r '.progress')%"
  case "$status" in
    completed) break ;;
    failed) echo "$job" | jq '.error'; exit 1 ;;
  esac
  sleep 5
done

# -OJ saves the file under the name the server sends, for example geopulse-gpx-export-3f6c1d2e-1780000000.gpx
curl -sS -OJ "$GEOPULSE_URL/api/v1/exports/$job_id/content" -H "X-API-Key: $GEOPULSE_TOKEN"
```

To choose the file name yourself, replace `-OJ` with `-o may-2026.gpx` (or `.json` for OwnTracks, `.zip` for a GPX ZIP
export).
