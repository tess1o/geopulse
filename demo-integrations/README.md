# Demo integrations: fake Immich and fake Memos

Two small read-only servers that give the GeoPulse demo personas a photo library and a notes
app to connect to, without running real Immich or Memos. Both run in one container:

| Server      | Port | Implements (only what GeoPulse calls)                                                                                      |
|-------------|------|----------------------------------------------------------------------------------------------------------------------------|
| fake Immich | 2283 | `POST /api/search/metadata`, `GET /api/albums`, `GET /api/albums/{id}`, `GET /api/assets/{id}/thumbnail`, `GET /api/assets/{id}/original` |
| fake Memos  | 5230 | `GET /api/v1/memos` (with GeoPulse's `created_ts` / tag filter), `GET /api/v1/memos/{uid}`, public `/memos/{uid}` page |

Every write returns 403. The 70 photos (Kyiv, London, New York and everyday shots) are CC0 or
public domain from Wikimedia Commons and are baked into the image, so the demo needs no
attribution UI. `internal/catalog/photos.json` lists the source and author of each one.

## How the shifted demo dates are handled

The fake servers keep no dates of their own. They read each persona's `timeline_stays`
from the demo database and place photos and memos inside those stays, always the same way:

- A stay is identified by how long after the user's first GPS point it starts, not by its
  database id. The timeline job replaces stays (with new ids) when it rebuilds them.
- Which stays get a photo or memo, which picture or line is used, and the exact minute are
  all derived from a hash of the user and that stay key. The output is identical on every
  request and in every container.
- `geopulse-demo-db.sh reset` shifts the GPS points and the stays by the same whole number of
  days, so every stay keeps its key. A photo that sat 12 minutes into a coffee stay is still
  12 minutes into the same coffee stay, just N days later, with the same id.
- Rebuilding a stay changes at most the photos and memos in that stay's week. The servers
  avoid reusing the same picture or line too often, and they track that per week, so a
  rebuilt stay can't affect any other week.
- The servers check each user's stays every 5 seconds (`count`, `max(id)`, `max(timestamp)`)
  and rebuild the library when they change. Resets and timeline rebuilds need no restart and
  no coordination. While the databases are being swapped, the last library keeps being served.

Photos are only placed between 07:30 and 23:00 local time. Sunset and night shots only appear
in the evening and breakfast shots in the morning. Coffee places get coffee photos, parks get
park photos, and each city gets its own landmarks. Unnamed places such as "Location 3" get city
street and landmark shots.

## Setup on the demo server

1. Add the service from `docker-compose.example.yml` to the demo's compose file and start it.
   It connects with the `GEOPULSE_POSTGRES_*` variables from `.env` (or `DEMO_DATABASE_URL`)
   and only runs `SELECT`s.

2. Point the demo personas at it, then take a new snapshot so every reset keeps the settings:

   ```bash
   docker compose exec -T geopulse-postgres psql -U "$GEOPULSE_POSTGRES_USERNAME" -d "$GEOPULSE_POSTGRES_DB" \
     -v immich_url=http://geopulse-demo-integrations:2283 \
     -v memos_url=http://geopulse-demo-integrations:5230 \
     < demo-integrations/sql/configure-demo-users.sql
   backend/scripts/demo/geopulse-demo-db.sh snapshot
   ```

   The script gives each persona the keys `demo-immich-<persona>` and `demo-memos-<persona>`.
   The fake servers look up whichever key is stored in `users.immich_preferences` /
   `users.memos_preferences`, so there is no key list to keep in sync.

3. Optional: GeoPulse turns memo links into `<memos_url>/memos/<uid>`. To make them clickable
   for visitors, publish port 5230 through the reverse proxy and use that public URL as
   `memos_url`. The fake server renders a simple page for each memo.

`geopulse-demo-db.sh` itself needs no changes.

## Development

```bash
go test ./...                                   # placement, date-shift and filter tests
DEMO_DATABASE_URL=postgres://user:pass@localhost:5432/geopulse?sslmode=disable \
  PORT=2283 go run ./cmd/fake-immich
docker build -t geopulse-demo-integrations .
```

Environment: `DEMO_DATABASE_URL` or `GEOPULSE_POSTGRES_{HOST,PORT,DB,USERNAME,PASSWORD,SSLMODE}`,
`IMMICH_PORT` (2283), `MEMOS_PORT` (5230) and `LOG_REQUESTS=true` for fake Immich request logs.

### Changing the photos

Edit `scripts/photo-sources.json` (a Commons file title, region, category, caption and
optional `timeOfDay`), then run:

```bash
python3 -m venv .venv && .venv/bin/pip install pillow
.venv/bin/python scripts/fetch-photos.py
```

The script refuses any file that is not CC0 or public domain, resizes it to a 1280 px preview
and a 480 px thumbnail, and rewrites `internal/catalog/photos.json`.
