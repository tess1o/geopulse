# OwnTracks fan-out

Tiny nginx proxy that sends each OwnTracks HTTP request to up to three instances:

- **PROD** gets the request and its response goes back to the phone.
- **DEV** and the optional **THIRD** get copies via nginx's `mirror`. Their
  responses and errors are ignored.

All configured instances receive the same request body and `Authorization`
header, so each must accept the same credentials.

## Run

1. Set `PROD_URL` and `DEV_URL` in `docker-compose.yml` to the full ingest URL
   of each instance, e.g. `https://geopulse.example.com/api/v1/gps/ingest/owntracks`.
   To enable a third destination, set `THIRD_URL` to its full ingest URL in
   `docker-compose.yml` or in a `.env` file next to it. Each URL can use a
   different path.
2. `docker compose up -d`
3. In OwnTracks, set the HTTP URL to the fan-out, e.g.
   `http://<fanout-host>:8085/`. The path the phone uses is ignored; each
   instance receives its own configured path.

Put it behind your usual TLS reverse proxy if the phone reaches it over the internet.

## Notes

- HTTP mode only; OwnTracks MQTT is not covered.
- If a mirror is down, the phone still gets PROD's response. A request that
  follows on the same keep-alive connection can wait for the mirror timeouts
  (3s connect, 5s read)
  set in `default.conf.template`.
- If PROD is down, the phone sees the error and OwnTracks retries later. The
  mirrors still get a copy of each attempt, so they may get duplicate points.
