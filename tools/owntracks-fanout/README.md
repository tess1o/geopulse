# OwnTracks fan-out

Tiny nginx proxy that sends each OwnTracks HTTP request to two GeoPulse instances:

- **PROD** gets the request and its response goes back to the phone.
- **DEV** gets a copy via nginx's `mirror`; its response and errors are ignored.

Headers (including `Authorization`) and body are forwarded unchanged, so both
instances must accept the same credentials.

## Run

1. Set `PROD_URL` and `DEV_URL` in `docker-compose.yml` to the full ingest URL
   of each instance, e.g. `https://geopulse.example.com/api/v1/gps/ingest/owntracks`.
   The two paths may differ.
2. `docker compose up -d`
3. In OwnTracks, set the HTTP URL to the fan-out, e.g.
   `http://<fanout-host>:8085/`. The path the phone uses is ignored; each
   instance receives its own configured path.

Put it behind your usual TLS reverse proxy if the phone reaches it over the internet.

## Notes

- HTTP mode only; OwnTracks MQTT is not covered.
- If DEV is down, the phone is unaffected. A request that follows on the same
  keep-alive connection can wait up to the DEV timeouts (3s connect, 5s read)
  set in `default.conf.template`.
- If PROD is down, the phone sees the error and OwnTracks retries later; DEV
  still receives the copy each time, so it may get duplicates of those points.
