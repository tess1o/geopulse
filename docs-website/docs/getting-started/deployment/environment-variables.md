---
title: Environment Variables Reference
description: Canonical reference with defaults, comments, restrictions, and restart behavior for GeoPulse environment variables.
---

# Environment Variables Reference

This page is the canonical environment variable reference for GeoPulse. Every listed variable includes default value, comments, restrictions, and restart guidance.

- Frontend runtime source: `frontend/docker-entrypoint.sh`
- Backend runtime source: `backend/src/main/resources/application.properties`
- Deployment source: `.env.example`, `docker-compose*.yml`, and Helm templates

## Frontend Runtime Vars

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_BACKEND_URL` | `http://geopulse-backend:8080` | Backend upstream URL injected into Nginx reverse-proxy config. | Reachable HTTP URL from frontend container network. | Frontend container restart |
| `CLIENT_MAX_BODY_SIZE` | `200M` | Nginx \`client_max_body_size\` for uploads. | Nginx size format (for example \`200M\`, \`1G\`). | Frontend container restart |
| `OSM_RESOLVER` | `Auto-detected (\`127.0.0.11 8.8.8.8\` in Docker)` | DNS resolvers used by Nginx for OpenStreetMap tile hosts. | Space-separated resolver IPs reachable from container. | Frontend container restart |

## Backend Runtime Vars

Backend runtime currently includes **185** distinct env vars.

Notes:
- `GEOPULSE_AUTH_SIGN_UP_ENABLED` is deprecated but still supported for backward compatibility.
- Runtime env changes require backend restart to take effect.

### Core and Database (8)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_CORS_ENABLED` | `true` | Enables/disables backend CORS handling. Property: `quarkus.http.cors.enabled`. New `.env.example` sets this to `false` for same-origin nginx deployments. | `true` or `false`. | Backend restart |
| `GEOPULSE_CORS_ORIGINS` | `(falls back to GEOPULSE_UI_URL)` | Comma-separated CORS origins when CORS is enabled. Property: `quarkus.http.cors.origins`. | One URL or comma-separated URLs. | Backend restart |
| `GEOPULSE_DATABASE_TRANSACTION_TIMEOUT_MINUTES` | `60` | Transaction configuration for large imports Property: \`quarkus.transaction-manager.default-transaction-timeout\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_POSTGRES_PASSWORD` | `(required/no default)` | PostgreSQL configuration Property: \`quarkus.datasource.password\`. | Sensitive secret. Store in secret manager; do not commit to VCS. | Backend restart |
| `GEOPULSE_POSTGRES_URL` | `(required/no default)` | PostgreSQL configuration Property: \`quarkus.datasource.jdbc.url\`. | Valid PostgreSQL JDBC URL (\`jdbc:postgresql://...\`). | Backend restart |
| `GEOPULSE_POSTGRES_USERNAME` | `(required/no default)` | PostgreSQL configuration Property: \`quarkus.datasource.username\`. | Required; no default value is provided. | Backend restart |
| `GEOPULSE_PUBLIC_BASE_URL` | `(empty)` | Public base URL used for callback/link generation. Property: `geopulse.public-base-url`. | Valid URL. | Backend restart |
| `GEOPULSE_UI_URL` | `http://localhost:5555` | Legacy fallback variable for CORS origins and OIDC callback fallback. Deprecated: use `GEOPULSE_CORS_ORIGINS` and `GEOPULSE_PUBLIC_BASE_URL`. | One URL or comma-separated URLs. | Backend restart |

### Authentication and Access (18)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_ADMIN_EMAIL` | `(empty)` | Admin configuration Property: \`geopulse.admin.email\`. | Valid email address. | Backend restart |
| `GEOPULSE_AUTH_ADMIN_LOGIN_BYPASS_ENABLED` | `true` | Property: \`geopulse.auth.admin-login-bypass.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AUTH_GUEST_ROOT_REDIRECT_TO_LOGIN_ENABLED` | `false` | Redirect signed-out users from `/` to `/login` instead of rendering Home. Property: \`geopulse.auth.guest-root-redirect-to-login.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AUTH_LOGIN_ENABLED` | `true` | Property: \`geopulse.auth.login.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AUTH_OIDC_LOGIN_ENABLED` | `true` | Property: \`geopulse.auth.oidc.login.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AUTH_OIDC_REGISTRATION_ENABLED` | `true` | Property: \`geopulse.auth.oidc.registration.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AUTH_PASSWORD_LOGIN_ENABLED` | `true` | Property: \`geopulse.auth.password-login.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AUTH_PASSWORD_REGISTRATION_ENABLED` | `true` | Property: \`geopulse.auth.password-registration.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AUTH_REGISTRATION_ENABLED` | `true` | Property: \`geopulse.auth.registration.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AUTH_SECURE_COOKIES` | `false` | Property: \`geopulse.auth.secure-cookies\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AUTH_SIGN_UP_ENABLED` | `true` | Deprecated property, replaced by geopulse.auth.password-registration.enabled Property: \`geoupuse.auth.sign-up-enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_COOKIE_DOMAIN` | `(empty)` | Property: \`geopulse.auth.cookie-domain\`. | Optional; empty value uses fallback behavior. | Backend restart |
| `GEOPULSE_INVITATION_BASE_URL` | `(empty)` | User invitation configuration Base URL for generating invitation links (e.g., https://geopulse.example.com) If empty, frontend will use window.location.origin Property: \`geopulse.invitation.base-url\`. | Valid URL. | Backend restart |
| `GEOPULSE_JWT_ACCESS_TOKEN_LIFESPAN` | `1800` | JWT Property: \`smallrye.jwt.new-token.lifespan\`. | Integer seconds. | Backend restart |
| `GEOPULSE_JWT_ISSUER` | `http://localhost:8080` | Property: \`smallrye.jwt.new-token.issuer\`. | String value. Follow subsystem documentation. | Backend restart |
| `GEOPULSE_JWT_PRIVATE_KEY_LOCATION` | `file:/app/keys/jwt-private-key.pem` | Property: \`smallrye.jwt.sign.key.location\`. | Readable path/URI to private key file. | Backend restart |
| `GEOPULSE_JWT_PUBLIC_KEY_LOCATION` | `file:/app/keys/jwt-public-key.pem` | Property: \`mp.jwt.verify.publickey.location\`. | Readable path/URI in container filesystem. | Backend restart |
| `GEOPULSE_JWT_REFRESH_TOKEN_LIFESPAN` | `604800` | JWT Property: \`jwt.refresh-token.lifespan\`. | Integer value. | Backend restart |

### OIDC Core (5)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_OIDC_AUTO_LINK_ACCOUNTS` | `false` | Account Linking Security When true, automatically links OIDC accounts to existing users with matching emails WARNING: Only enable this if you fully trust your OIDC providers to... Property: \`geopulse.oidc.auto-link-accounts\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_OIDC_CALLBACK_BASE_URL` | `(empty)` | OIDC callback base URL. Fallback order: `GEOPULSE_PUBLIC_BASE_URL`, then legacy `GEOPULSE_UI_URL`. Property: `geopulse.oidc.callback-base-url`. | Valid URL. | Backend restart |
| `GEOPULSE_OIDC_CLEANUP_ENABLED` | `true` | OIDC Cleanup Configuration Property: \`geopulse.oidc.cleanup.session-states.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_OIDC_ENABLED` | `false` | OIDC Configuration Property: \`geopulse.oidc.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_OIDC_JWKS_CACHE_TTL_HOURS` | `24` | JWKS (signing keys) caching Lower TTL recommended to handle provider key rotation Property: \`geopulse.oidc.jwks-cache.ttl-hours\`. | Non-negative numeric value. | Backend restart |

### AI and Immich (7)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_AI_CHAT_MEMORY_MAX_MESSAGES` | `10` | AI Feature Configuration Property: \`geopulse.ai.chat-memory.max-messages\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_AI_ENCRYPTION_KEY_LOCATION` | `file:/app/keys/ai-encryption-key.txt` | AI Feature Configuration Property: \`geopulse.ai.encryption.key.location\`. | Readable path/URI in container filesystem. | Backend restart |
| `GEOPULSE_AI_LOGGING_ENABLED` | `false` | AI Feature Configuration Property: \`geopulse.ai.logging.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_AI_TOOL_RESULT_MAX_LENGTH` | `12000` | AI Feature Configuration Property: \`geopulse.ai.tool-result.max-length\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMMICH_GEONAMES_NORMALIZATION_MAX_DISTANCE_METERS` | `50000` | Immich search cache Property: \`immich.photos.geonames-normalization.max-distance-meters\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMMICH_PHOTO_SEARCH_CACHE_MAX_ENTRIES` | `200` | Immich search cache Property: \`immich.photos.search-cache-max-entries\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMMICH_PHOTO_SEARCH_CACHE_TTL_SECONDS` | `300` | Immich search cache Property: \`immich.photos.search-cache-ttl-seconds\`. | Non-negative numeric value. | Backend restart |

### Geocoding and GeoNames (27)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_GEOCODING_DELAY_MS` | `1000` | Delay between geocoding requests (milliseconds) Property: \`geocoding.provider.delay.ms\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_GEOCODING_FALLBACK_PROVIDER` | `(empty)` | Fallback geocoding provider (optional) Property: \`geocoding.provider.fallback\`. | Empty or one of \`nominatim\`, \`photon\`, \`googlemaps\`, \`mapbox\`. | Backend restart |
| `GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY` | `(empty)` | API Keys (can also be set via encrypted storage in admin panel) Property: \`geocoding.googlemaps.api-key\`. | Sensitive secret. Store in secret manager; do not commit to VCS. | Backend restart |
| `GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED` | `false` | Property: \`geocoding.provider.googlemaps.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN` | `(empty)` | Property: \`geocoding.mapbox.access-token\`. | Sensitive secret. Store in secret manager; do not commit to VCS. | Backend restart |
| `GEOPULSE_GEOCODING_MAPBOX_ENABLED` | `false` | Property: \`geocoding.provider.mapbox.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GEOCODING_NOMINATIM_ENABLED` | `true` | Provider availability flags Property: \`geocoding.provider.nominatim.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GEOCODING_NOMINATIM_LANGUAGE` | `(empty)` | Provider availability flags Nominatim geocoding language preference (BCP 47 format: en-US, de, uk, ja, etc.) If not set, no Accept-Language header will be sent (existing behavior) Property: \`geocoding.nominatim.language\`. | BCP 47 language tag (for example \`en-US\`) or empty. | Backend restart |
| `GEOPULSE_GEOCODING_NOMINATIM_URL` | `https://nominatim.openstreetmap.org` | Provider availability flags Property: \`quarkus.rest-client.nominatim-api.url\`. | Valid URL. | Backend restart |
| `GEOPULSE_GEOCODING_PHOTON_ENABLED` | `false` | Property: \`geocoding.provider.photon.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GEOCODING_PHOTON_LANGUAGE` | `(empty)` | Photon geocoding language preference (BCP 47 format: en-US, de, uk, ja, etc.) If not set, no Accept-Language header will be sent (existing behavior) Property: \`geocoding.photon.language\`. | BCP 47 language tag (for example \`en-US\`) or empty. | Backend restart |
| `GEOPULSE_GEOCODING_PHOTON_URL` | `https://photon.komoot.io` | Property: \`quarkus.rest-client.photon-api.url\`. | Valid URL. | Backend restart |
| `GEOPULSE_GEOCODING_PRIMARY_PROVIDER` | `nominatim` | Primary geocoding provider (nominatim, photon, googlemaps, mapbox) Property: \`geocoding.provider.primary\`. | One of \`nominatim\`, \`photon\`, \`googlemaps\`, \`mapbox\`. | Backend restart |
| `GEOPULSE_GEONAMES_COUNTRY_IMPORT_BATCH_SIZE` | `200` | GeoNames country dataset import (ISO2 -> country metadata) Property: \`geopulse.geonames.country-import.batch-size\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_GEONAMES_COUNTRY_IMPORT_CONNECT_TIMEOUT_SECONDS` | `20` | GeoNames country dataset import (ISO2 -> country metadata) Property: \`geopulse.geonames.country-import.connect-timeout-seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_GEONAMES_COUNTRY_IMPORT_ENABLED` | `true` | GeoNames country dataset import (ISO2 -> country metadata) Property: \`geopulse.geonames.country-import.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GEONAMES_COUNTRY_IMPORT_FORCE_REFRESH` | `false` | GeoNames country dataset import (ISO2 -> country metadata) Property: \`geopulse.geonames.country-import.force-refresh\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GEONAMES_COUNTRY_IMPORT_MIN_ROW_THRESHOLD` | `200` | GeoNames country dataset import (ISO2 -> country metadata) Property: \`geopulse.geonames.country-import.min-row-threshold\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_GEONAMES_COUNTRY_IMPORT_READ_TIMEOUT_SECONDS` | `120` | GeoNames country dataset import (ISO2 -> country metadata) Property: \`geopulse.geonames.country-import.read-timeout-seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_GEONAMES_COUNTRY_IMPORT_URL` | `https://download.geonames.org/export/dump/countryInfo.txt` | GeoNames country dataset import (ISO2 -> country metadata) Property: \`geopulse.geonames.country-import.url\`. | Valid URL. | Backend restart |
| `GEOPULSE_GEONAMES_IMPORT_BATCH_SIZE` | `1000` | GeoNames cities dataset import (used for city normalization) Property: \`geopulse.geonames.import.batch-size\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_GEONAMES_IMPORT_CONNECT_TIMEOUT_SECONDS` | `20` | GeoNames cities dataset import (used for city normalization) Property: \`geopulse.geonames.import.connect-timeout-seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_GEONAMES_IMPORT_ENABLED` | `true` | GeoNames cities dataset import (used for city normalization) Property: \`geopulse.geonames.import.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GEONAMES_IMPORT_FORCE_REFRESH` | `false` | GeoNames cities dataset import (used for city normalization) Property: \`geopulse.geonames.import.force-refresh\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GEONAMES_IMPORT_MIN_ROW_THRESHOLD` | `100000` | GeoNames cities dataset import (used for city normalization) Property: \`geopulse.geonames.import.min-row-threshold\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_GEONAMES_IMPORT_READ_TIMEOUT_SECONDS` | `300` | GeoNames cities dataset import (used for city normalization) Property: \`geopulse.geonames.import.read-timeout-seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_GEONAMES_IMPORT_URL` | `https://download.geonames.org/export/dump/cities500.zip` | GeoNames cities dataset import (used for city normalization) Property: \`geopulse.geonames.import.url\`. | Valid URL. | Backend restart |

### Import (17)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_IMPORT_BULK_INSERT_BATCH_SIZE` | `500` | Import batch size configuration Property: \`geopulse.import.bulk-insert-batch-size\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMPORT_CHUNK_SIZE_MB` | `50` | Chunked upload configuration (for bypassing Cloudflare's 100MB upload limit) Files >80MB are split into chunks on the frontend and reassembled on the backend Property: \`geopulse.import.chunk-size-mb\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMPORT_CHUNKS_DIR` | `/tmp/geopulse/chunks` | Chunked upload configuration (for bypassing Cloudflare's 100MB upload limit) Files >80MB are split into chunks on the frontend and reassembled on the backend Property: \`geopulse.import.chunks-directory\`. | Readable path/URI in container filesystem. | Backend restart |
| `GEOPULSE_IMPORT_DROP_FOLDER_ENABLED` | `false` | Drop folder import configuration (server-side file pickup) Property: \`geopulse.import.drop-folder.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_IMPORT_DROP_FOLDER_GEOPULSE_MAX_SIZE_MB` | `200` | Drop folder import configuration (server-side file pickup) Property: \`geopulse.import.drop-folder.geopulse-max-size-mb\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMPORT_DROP_FOLDER_PATH` | `/data/geopulse-import` | Drop folder import configuration (server-side file pickup) Property: \`geopulse.import.drop-folder.path\`. | Readable path/URI in container filesystem. | Backend restart |
| `GEOPULSE_IMPORT_DROP_FOLDER_POLL_INTERVAL_SECONDS` | `10` | Drop folder import configuration (server-side file pickup) Property: \`geopulse.import.drop-folder.poll-interval-seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_IMPORT_DROP_FOLDER_STABLE_AGE_SECONDS` | `10` | Drop folder import configuration (server-side file pickup) Property: \`geopulse.import.drop-folder.stable-age-seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_IMPORT_GEOJSON_STREAMING_BATCH_SIZE` | `500` | GeoJSON streaming parser configuration Batch size for streaming GeoJSON imports - aligns with bulk insert batch size for optimal performance During streaming, batches are flushe... Property: \`geopulse.import.geojson.streaming-batch-size\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMPORT_GOOGLETIMELINE_STREAMING_BATCH_SIZE` | `500` | Google Timeline streaming parser configuration Batch size for streaming Google Timeline imports - aligns with bulk insert batch size for optimal performance During streaming, ba... Property: \`geopulse.import.googletimeline.streaming-batch-size\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMPORT_LARGE_FILE_THRESHOLD_MB` | `100` | Import temp file configuration (for large file handling) Files larger than this threshold are saved to temp directory instead of loading into memory This prevents OOM errors wit... Property: \`geopulse.import.large-file-threshold-mb\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMPORT_MAX_FILE_SIZE_GB` | `10` | Chunked upload configuration (for bypassing Cloudflare's 100MB upload limit) Files >80MB are split into chunks on the frontend and reassembled on the backend Property: \`geopulse.import.max-file-size-gb\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMPORT_MERGE_BATCH_SIZE` | `250` | Import batch size configuration Property: \`geopulse.import.merge-batch-size\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_IMPORT_TEMP_DIR` | `/tmp/geopulse/imports` | Import temp file configuration (for large file handling) Files larger than this threshold are saved to temp directory instead of loading into memory This prevents OOM errors wit... Property: \`geopulse.import.temp-directory\`. | Readable path/URI in container filesystem. | Backend restart |
| `GEOPULSE_IMPORT_TEMP_FILE_RETENTION_HOURS` | `24` | Import temp file configuration (for large file handling) Files larger than this threshold are saved to temp directory instead of loading into memory This prevents OOM errors wit... Property: \`geopulse.import.temp-file-retention-hours\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_IMPORT_UPLOAD_CLEANUP_MINUTES` | `15` | Chunked upload configuration (for bypassing Cloudflare's 100MB upload limit) Files >80MB are split into chunks on the frontend and reassembled on the backend Property: \`geopulse.import.upload-cleanup-minutes\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_IMPORT_UPLOAD_TIMEOUT_HOURS` | `2` | Chunked upload configuration (for bypassing Cloudflare's 100MB upload limit) Files >80MB are split into chunks on the frontend and reassembled on the backend Property: \`geopulse.import.upload-timeout-hours\`. | Non-negative numeric value. | Backend restart |

### Export (8)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_EXPORT_BATCH_SIZE` | `1000` | Batch sizes for streaming exports Property: \`geopulse.export.batch-size\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_EXPORT_CONCURRENT_JOBS_LIMIT` | `3` | Export configuration Job management settings Property: \`geopulse.export.concurrent-jobs-limit\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_EXPORT_JOB_EXPIRY_HOURS` | `24` | Export configuration Job management settings Property: \`geopulse.export.job-expiry-hours\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_EXPORT_MAX_JOBS_PER_USER` | `5` | Export configuration Job management settings Property: \`geopulse.export.max-jobs-per-user\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_EXPORT_SCHEDULER_INTERVAL` | `2s` | Export configuration Job management settings Scheduler interval for processing export jobs (uses Quarkus time expression format) Note: This setting requires application restart... Property: \`geopulse.export.scheduler-interval\`. | Duration format (for example \`1s\`, \`5m\`, \`1h\`). | Backend restart |
| `GEOPULSE_EXPORT_TEMP_DIR` | `/tmp/geopulse/exports` | Export temp file configuration (for large file handling) Export files are written to temp directory and streamed to clients to prevent OOM Property: \`geopulse.export.temp-directory\`. | Readable path/URI in container filesystem. | Backend restart |
| `GEOPULSE_EXPORT_TEMP_FILE_RETENTION_HOURS` | `24` | Export temp file configuration (for large file handling) Export files are written to temp directory and streamed to clients to prevent OOM Property: \`geopulse.export.temp-file-retention-hours\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_EXPORT_TRIP_POINT_LIMIT` | `10000` | Batch sizes for streaming exports Property: \`geopulse.export.trip-point-limit\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |

### Timeline and Trip Intelligence (64)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_TIMELINE_BICYCLE_ENABLED` | `false` | Bicycle (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.bicycle.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_BICYCLE_MAX_AVG_SPEED` | `25.0` | Bicycle (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.bicycle.max_avg_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_BICYCLE_MAX_MAX_SPEED` | `35.0` | Bicycle (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.bicycle.max_max_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_BICYCLE_MIN_AVG_SPEED` | `8.0` | Bicycle (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.bicycle.min_avg_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_CAR_MIN_AVG_SPEED` | `10.0` | Car (mandatory) - updated default from 8.0 to 10.0 Property: \`geopulse.timeline.travel.classification.car.min_avg_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_CAR_MIN_MAX_SPEED` | `15.0` | Car (mandatory) - updated default from 8.0 to 10.0 Property: \`geopulse.timeline.travel.classification.car.min_max_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_DATA_GAP_MIN_DURATION_SECONDS` | `1800` | Data Gap Detection Configuration Property: \`geopulse.timeline.data_gap.min_duration_seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_TIMELINE_DATA_GAP_THRESHOLD_SECONDS` | `10800` | Data Gap Detection Configuration Property: \`geopulse.timeline.data_gap.threshold_seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_TIMELINE_FLIGHT_ENABLED` | `false` | Flight (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.flight.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_FLIGHT_MIN_AVG_SPEED` | `400.0` | Flight (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.flight.min_avg_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_FLIGHT_MIN_MAX_SPEED` | `500.0` | Flight (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.flight.min_max_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_GAP_STAY_INFERENCE_ENABLED` | `false` | Gap Stay Inference Configuration When enabled, infers a stay instead of creating a data gap when points before/after gap are at same location Property: \`geopulse.timeline.gap_stay_inference.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_GAP_STAY_INFERENCE_MAX_GAP_HOURS` | `24` | Gap Stay Inference Configuration When enabled, infers a stay instead of creating a data gap when points before/after gap are at same location Property: \`geopulse.timeline.gap_stay_inference.max_gap_hours\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_ENABLED` | `false` | Gap Trip Inference Configuration When enabled, infers a trip instead of creating a data gap when distance between points exceeds threshold Useful for detecting flights, long-dis... Property: \`geopulse.timeline.gap_trip_inference.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_MAX_GAP_HOURS` | `24` | Gap Trip Inference Configuration When enabled, infers a trip instead of creating a data gap when distance between points exceeds threshold Useful for detecting flights, long-dis... Property: \`geopulse.timeline.gap_trip_inference.max_gap_hours\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_MIN_DISTANCE_METERS` | `100000` | Gap Trip Inference Configuration When enabled, infers a trip instead of creating a data gap when distance between points exceeds threshold Useful for detecting flights, long-dis... Property: \`geopulse.timeline.gap_trip_inference.min_distance_meters\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_GAP_TRIP_INFERENCE_MIN_GAP_HOURS` | `1` | Gap Trip Inference Configuration When enabled, infers a trip instead of creating a data gap when distance between points exceeds threshold Useful for detecting flights, long-dis... Property: \`geopulse.timeline.gap_trip_inference.min_gap_hours\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_TIMELINE_JOB_DELAY` | `1m` | Real-time Timeline Processing Configuration Property: \`geopulse.timeline.job.delay\`. | Duration format (for example \`1s\`, \`5m\`, \`1h\`). | Backend restart |
| `GEOPULSE_TIMELINE_JOB_INTERVAL` | `5m` | Real-time Timeline Processing Configuration Property: \`geopulse.timeline.job.interval\`. | Duration format (for example \`1s\`, \`5m\`, \`1h\`). | Backend restart |
| `GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_ADAPTIVE` | `true` | GPS Path Simplification Configuration Property: \`geopulse.timeline.path.simplification.adaptive\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_ENABLED` | `true` | GPS Path Simplification Configuration Property: \`geopulse.timeline.path.simplification.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_MAX_POINTS` | `100` | GPS Path Simplification Configuration Property: \`geopulse.timeline.path.simplification.max_points\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_TOLERANCE` | `15.0` | GPS Path Simplification Configuration Property: \`geopulse.timeline.path.simplification.tolerance\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_PROCESSING_THREADS` | `2` | Real-time Timeline Processing Configuration Property: \`geopulse.timeline.processing.thread-pool-size\`. | Integer value. | Backend restart |
| `GEOPULSE_TIMELINE_RUNNING_ENABLED` | `false` | Running (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.running.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_RUNNING_MAX_AVG_SPEED` | `14.0` | Running (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.running.max_avg_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_RUNNING_MAX_MAX_SPEED` | `18.0` | Running (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.running.max_max_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_RUNNING_MIN_AVG_SPEED` | `7.0` | Running (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.running.min_avg_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_SHORT_DISTANCE_KM` | `1.0` | Car (mandatory) - updated default from 8.0 to 10.0 Property: \`geopulse.timeline.travel.classification.short_distance_km\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_STAYPOINT_ACCURACY_THRESHOLD` | `60.0` | Default timeline configs. They can be overwritten by each user individually or via ENV variables Property: \`geopulse.timeline.staypoint.accuracy.threshold\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_STAYPOINT_MERGE_ENABLED` | `true` | Merge staypoints Property: \`geopulse.timeline.staypoint.merge.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_STAYPOINT_MERGE_MAX_DISTANCE_METERS` | `400` | Merge staypoints Property: \`geopulse.timeline.staypoint.merge.max_distance_meters\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_STAYPOINT_MERGE_MAX_TIME_GAP_MINUTES` | `15` | Merge staypoints Property: \`geopulse.timeline.staypoint.merge.max_time_gap_minutes\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_TIMELINE_STAYPOINT_MIN_ACCURACY_RATIO` | `0.5` | Default timeline configs. They can be overwritten by each user individually or via ENV variables Property: \`geopulse.timeline.staypoint.min_accuracy_ratio\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_STAYPOINT_MIN_DURATION_MINUTES` | `7` | Default timeline configs. They can be overwritten by each user individually or via ENV variables Property: \`geopulse.timeline.staypoint.min_duration_minutes\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_TIMELINE_STAYPOINT_RADIUS_METERS` | `50` | Default timeline configs. They can be overwritten by each user individually or via ENV variables Property: \`geopulse.timeline.staypoint.radius_meters\`. | Integer value. | Backend restart |
| `GEOPULSE_TIMELINE_STAYPOINT_USE_VELOCITY_ACCURACY` | `true` | Default timeline configs. They can be overwritten by each user individually or via ENV variables Property: \`geopulse.timeline.staypoint.use_velocity_accuracy\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_STAYPOINT_VELOCITY_THRESHOLD` | `2.5` | Default timeline configs. They can be overwritten by each user individually or via ENV variables Property: \`geopulse.timeline.staypoint.velocity.threshold\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_TRAIN_ENABLED` | `false` | Train (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.train.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_TRAIN_MAX_AVG_SPEED` | `150.0` | Train (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.train.max_avg_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_TRAIN_MAX_MAX_SPEED` | `180.0` | Train (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.train.max_max_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_TRAIN_MAX_SPEED_VARIANCE` | `15.0` | Train (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.train.max_speed_variance\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_TRAIN_MIN_AVG_SPEED` | `30.0` | Train (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.train.min_avg_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_TRAIN_MIN_MAX_SPEED` | `80.0` | Train (optional - disabled by default) Property: \`geopulse.timeline.travel.classification.train.min_max_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_ARRIVAL_MIN_DURATION_SECONDS` | `90` | Travel Classification Configuration Property: \`geopulse.timeline.trip.arrival.min_duration_seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_ARRIVAL_MIN_POINTS` | `3` | Travel Classification Configuration Property: \`geopulse.timeline.trip.arrival.min_points\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_DETECTION_ALGORITHM` | `single` | Trip Property: \`geopulse.timeline.trip.detection.algorithm\`. | String value. Follow subsystem documentation. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_MOVEMENT_OVERRIDE_MAX_DISTANCE_RATIO` | `1.8` | Manual trip movement override matching (re-apply overrides after timeline rebuild) Property: \`geopulse.timeline.trip.movement_override.matching.max_distance_ratio\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_MOVEMENT_OVERRIDE_MAX_DURATION_RATIO` | `1.8` | Manual trip movement override matching (re-apply overrides after timeline rebuild) Property: \`geopulse.timeline.trip.movement_override.matching.max_duration_ratio\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_MOVEMENT_OVERRIDE_MAX_POINT_DISTANCE_METERS` | `350.0` | Manual trip movement override matching (re-apply overrides after timeline rebuild) Property: \`geopulse.timeline.trip.movement_override.matching.max_point_distance_meters\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_MOVEMENT_OVERRIDE_MAX_TIMESTAMP_DELTA_SECONDS` | `2700` | Manual trip movement override matching (re-apply overrides after timeline rebuild) Property: \`geopulse.timeline.trip.movement_override.matching.max_timestamp_delta_seconds\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_MOVEMENT_OVERRIDE_MIN_DISTANCE_RATIO` | `0.6` | Manual trip movement override matching (re-apply overrides after timeline rebuild) Property: \`geopulse.timeline.trip.movement_override.matching.min_distance_ratio\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_MOVEMENT_OVERRIDE_MIN_DURATION_RATIO` | `0.6` | Manual trip movement override matching (re-apply overrides after timeline rebuild) Property: \`geopulse.timeline.trip.movement_override.matching.min_duration_ratio\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TIMELINE_TRIP_SUSTAINED_STOP_MIN_DURATION_SECONDS` | `60` | Travel Classification Configuration Property: \`geopulse.timeline.trip.sustained_stop.min_duration_seconds\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_TIMELINE_VIEW_ITEM_LIMIT` | `150` | Timeline View Item Limit - maximum number of items to load on Timeline page For larger datasets, users will be guided to use Timeline Reports Property: \`geopulse.timeline.view.item-limit\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_WALKING_MAX_AVG_SPEED` | `6.0` | Walking (mandatory) Property: \`geopulse.timeline.travel.classification.walking.max_avg_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TIMELINE_WALKING_MAX_MAX_SPEED` | `8.0` | Walking (mandatory) Property: \`geopulse.timeline.travel.classification.walking.max_max_speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TRIP_SUMMARY_PLACES_PAGE` | `1` | Trip summary aggregation Property: \`geopulse.trip.summary.places.page\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TRIP_SUMMARY_PLACES_PAGE_SIZE` | `10000` | Trip summary aggregation Property: \`geopulse.trip.summary.places.page-size\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TRIP_VISIT_MATCHING_AUTO_APPLY_ON_REGENERATION` | `true` | Trip visit auto-matching Property: \`geopulse.trip.visit-matching.auto-apply-on-timeline-regeneration\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_TRIP_VISIT_MATCHING_AUTO_THRESHOLD` | `0.85` | Trip visit auto-matching Property: \`geopulse.trip.visit-matching.auto-threshold\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TRIP_VISIT_MATCHING_EXACT_NAME_BOOST_DISTANCE_METERS` | `120` | Trip visit auto-matching Property: \`geopulse.trip.visit-matching.exact-name-boost-distance-meters\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TRIP_VISIT_MATCHING_MAX_DISTANCE_METERS` | `400` | Trip visit auto-matching Property: \`geopulse.trip.visit-matching.max-distance-meters\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_TRIP_VISIT_MATCHING_SUGGEST_THRESHOLD` | `0.55` | Trip visit auto-matching Property: \`geopulse.trip.visit-matching.suggest-threshold\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |

### GPS Filtering and Deduplication (6)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_GPS_DUPLICATE_DETECTION_ENABLED` | `false` | Per-source duplicate detection defaults (for new sources) Property: \`geopulse.gps.duplicate-detection.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GPS_DUPLICATE_DETECTION_LOCATION_TIME_THRESHOLD_MINUTES` | `2` | GPS Point Duplicate Detection Configuration DEPRECATED: This setting is used for fallback when per-source threshold is NULL For new sources, use the per-source settings below in... Property: \`geopulse.gps.duplicate-detection.location-time-threshold-minutes\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_GPS_DUPLICATE_DETECTION_THRESHOLD_MINUTES` | `2` | Per-source duplicate detection defaults (for new sources) Property: \`geopulse.gps.duplicate-detection.threshold-minutes\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_GPS_FILTER_INACCURATE_DATA_ENABLED` | `false` | GPS Filtering Configuration (per-source defaults) These values are used as defaults when creating new GPS sources Property: \`geopulse.gps.filter.inaccurate-data.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_GPS_MAX_ALLOWED_ACCURACY` | `100` | GPS Filtering Configuration (per-source defaults) These values are used as defaults when creating new GPS sources Property: \`geopulse.gps.max-allowed-accuracy\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_GPS_MAX_ALLOWED_SPEED` | `250` | GPS Filtering Configuration (per-source defaults) These values are used as defaults when creating new GPS sources Property: \`geopulse.gps.max-allowed-speed\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |

### MQTT (14)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_MQTT_BROKER_HOST` | `geopulse-mosquitto` | MQTT Configuration (optional - only active when GEOPULSE_MQTT_ENABLED true) Property: \`geopulse.mqtt.broker.host\`. | String value. Follow subsystem documentation. | Backend restart |
| `GEOPULSE_MQTT_BROKER_PORT` | `1883` | MQTT Configuration (optional - only active when GEOPULSE_MQTT_ENABLED true) Property: \`geopulse.mqtt.broker.port\`. | Integer in range \`1-65535\`. | Backend restart |
| `GEOPULSE_MQTT_ENABLED` | `false` | MQTT Configuration (optional - only active when GEOPULSE_MQTT_ENABLED true) Property: \`geopulse.mqtt.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_MQTT_PASSWORD` | `geopulse_mqtt_pass_123` | MQTT Configuration (optional - only active when GEOPULSE_MQTT_ENABLED true) Property: \`geopulse.mqtt.password\`. | Sensitive secret. Store in secret manager; do not commit to VCS. | Backend restart |
| `GEOPULSE_MQTT_TLS_ENABLED` | `false` | Enables TLS for external MQTT broker connections. Property: \`geopulse.mqtt.tls.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_MQTT_TLS_PROTOCOL` | `TLSv1.2` | TLS protocol used by MQTT client when TLS is enabled. Property: \`geopulse.mqtt.tls.protocol\`. | Supported JVM TLS protocol (for example \`TLSv1.2\`, \`TLSv1.3\`). | Backend restart |
| `GEOPULSE_MQTT_TLS_TRUSTSTORE_PATH` | `(empty)` | Optional truststore path for private/self-signed broker cert validation. Property: \`geopulse.mqtt.tls.truststore.path\`. | Readable path in backend container filesystem. | Backend restart |
| `GEOPULSE_MQTT_TLS_TRUSTSTORE_PASSWORD` | `(empty)` | Truststore password for MQTT TLS. Property: \`geopulse.mqtt.tls.truststore.password\`. | Sensitive secret. Store in secret manager; do not commit to VCS. | Backend restart |
| `GEOPULSE_MQTT_TLS_TRUSTSTORE_TYPE` | `PKCS12` | Truststore format used by MQTT TLS. Property: \`geopulse.mqtt.tls.truststore.type\`. | Typically \`PKCS12\` or \`JKS\`. | Backend restart |
| `GEOPULSE_MQTT_TLS_KEYSTORE_PATH` | `(empty)` | Optional client certificate keystore path (required for mTLS brokers). Property: \`geopulse.mqtt.tls.keystore.path\`. | Readable path in backend container filesystem. | Backend restart |
| `GEOPULSE_MQTT_TLS_KEYSTORE_PASSWORD` | `(empty)` | Keystore password for MQTT TLS client certificate. Property: \`geopulse.mqtt.tls.keystore.password\`. | Sensitive secret. Store in secret manager; do not commit to VCS. | Backend restart |
| `GEOPULSE_MQTT_TLS_KEYSTORE_TYPE` | `PKCS12` | Keystore format for MQTT TLS client cert keypair. Property: \`geopulse.mqtt.tls.keystore.type\`. | Typically \`PKCS12\` or \`JKS\`. | Backend restart |
| `GEOPULSE_MQTT_TLS_INSECURE_SKIP_HOSTNAME_VERIFICATION` | `false` | Disables TLS hostname verification for MQTT (debugging only). Property: \`geopulse.mqtt.tls.insecure-skip-hostname-verification\`. | \`true\` or \`false\`; keep \`false\` for production. | Backend restart |
| `GEOPULSE_MQTT_USERNAME` | `geopulse_mqtt_admin` | MQTT Configuration (optional - only active when GEOPULSE_MQTT_ENABLED true) Property: \`geopulse.mqtt.username\`. | String value. Follow subsystem documentation. | Backend restart |

### Notifications / Apprise (8)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_NOTIFICATIONS_APPRISE_ENABLED` | `false` | Enables/disables external Apprise delivery for geofence alerts. Property: \`geopulse.notifications.apprise.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_NOTIFICATIONS_APPRISE_API_URL` | `(empty)` | Base URL for Apprise API service. Property: \`geopulse.notifications.apprise.api-url\`. | Valid URL (for example `http://apprise-api:8000`). | Backend restart |
| `GEOPULSE_NOTIFICATIONS_APPRISE_AUTH_TOKEN` | `(empty)` | Optional API token for Apprise. Property: \`geopulse.notifications.apprise.auth-token\`. | Sensitive secret. Store in secret manager; do not commit to VCS. | Backend restart |
| `GEOPULSE_NOTIFICATIONS_APPRISE_TIMEOUT_MS` | `5000` | HTTP timeout for Apprise requests in milliseconds. Property: \`geopulse.notifications.apprise.timeout-ms\`. | Non-negative numeric value. | Backend restart |
| `GEOPULSE_NOTIFICATIONS_APPRISE_VERIFY_TLS` | `true` | Whether TLS certificate verification is enabled for Apprise calls. Property: \`geopulse.notifications.apprise.verify-tls\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_NOTIFICATIONS_GEOFENCE_EVENTS_CLEANUP_ENABLED` | `true` | Enables/disables scheduled cleanup of geofence notification events. Property: \`geopulse.notifications.geofence-events.cleanup.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_NOTIFICATIONS_GEOFENCE_EVENTS_CLEANUP_SCHEDULER_CADENCE` | `12h` | Scheduler cadence for cleanup job ticks. Property: \`geopulse.notifications.geofence-events.cleanup.scheduler-cadence\`. | Quarkus duration expression (for example \`30m\`, \`12h\`, \`1d\`). | Backend restart |
| `GEOPULSE_NOTIFICATIONS_GEOFENCE_EVENTS_RETENTION_DAYS` | `90` | Deletes geofence events older than this many days. Property: \`geopulse.notifications.geofence-events.retention-days\`. | Integer >= 1. | Backend restart |

### Prometheus (8)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_PROMETHEUS_ENABLED` | `false` | Custom Prometheus Metrics Configuration Note: The Prometheus endpoint is always available at build time. Use GEOPULSE_PROMETHEUS_ENABLED to control custom metrics collection at... Property: \`geopulse.prometheus.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_PROMETHEUS_FAVORITES_ENABLED` | `true` | Per-metric-class control (optional - all enabled by default) Property: \`geopulse.prometheus.favorites.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_PROMETHEUS_GEOCODING_ENABLED` | `true` | Per-metric-class control (optional - all enabled by default) Property: \`geopulse.prometheus.geocoding.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_PROMETHEUS_GPS_POINTS_ENABLED` | `true` | Per-metric-class control (optional - all enabled by default) Property: \`geopulse.prometheus.gps-points.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_PROMETHEUS_MEMORY_ENABLED` | `true` | Per-metric-class control (optional - all enabled by default) Property: \`geopulse.prometheus.memory.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_PROMETHEUS_REFRESH_INTERVAL` | `10m` | Custom Prometheus Metrics Configuration Note: The Prometheus endpoint is always available at build time. Use GEOPULSE_PROMETHEUS_ENABLED to control custom metrics collection at... Property: \`geopulse.prometheus.refresh-interval\`. | Duration format (for example \`1s\`, \`5m\`, \`1h\`). | Backend restart |
| `GEOPULSE_PROMETHEUS_TIMELINE_ENABLED` | `true` | Per-metric-class control (optional - all enabled by default) Property: \`geopulse.prometheus.timeline.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_PROMETHEUS_USER_METRICS_ENABLED` | `true` | Per-metric-class control (optional - all enabled by default) Property: \`geopulse.prometheus.user-metrics.enabled\`. | \`true\` or \`false\`. | Backend restart |

### Sharing and OwnTracks (2)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_OWNTRACKS_PING_TIMESTAMP_OVERRIDE` | `false` | Property: \`geopulse.owntracks.ping.timestamp.override\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_SHARE_BASE_URL` | `(empty)` | Sharing Property: \`geopulse.share.base-url\`. | Valid URL. | Backend restart |

### Warmup and Background Jobs (5)

| Variable | Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_BADGES_CALCULATION_DELAY` | `5m` | Badge Calculation Scheduler Configuration Interval for running badge calculations (uses Quarkus time expression format: 1s, 5m, 1h, etc.) Note: This setting requires application... Property: \`geopulse.badges.calculation.delay\`. | Duration format (for example \`1s\`, \`5m\`, \`1h\`). | Backend restart |
| `GEOPULSE_BADGES_CALCULATION_INTERVAL` | `30m` | Badge Calculation Scheduler Configuration Interval for running badge calculations (uses Quarkus time expression format: 1s, 5m, 1h, etc.) Note: This setting requires application... Property: \`geopulse.badges.calculation.interval\`. | Duration format (for example \`1s\`, \`5m\`, \`1h\`). | Backend restart |
| `GEOPULSE_WARMUP_ENABLED` | `true` | Warmup Configuration Enable aggressive warmup on startup (loads real data, then forces GC) Trade-off: Longer startup time (5-8s) for safe first-request handling in 512MB containers Property: \`geopulse.warmup.enabled\`. | \`true\` or \`false\`. | Backend restart |
| `GEOPULSE_WARMUP_MAX_ITEMS` | `5000` | Maximum number of items to convert to DTOs during warmup (default: 5000) Prevents memory spikes from users with excessive data (100K+ timeline items) Queries still execute fully... Property: \`geopulse.warmup.max-items\`. | Numeric value; keep positive unless documented otherwise. | Backend restart |
| `GEOPULSE_WARMUP_SAMPLE_DAYS` | `30` | Number of days of timeline data to load during warmup (default: 30) Higher more thorough warmup, but longer startup Lower faster startup, but less comprehensive warmup Set to 0... Property: \`geopulse.warmup.sample-days\`. | Non-negative numeric value. | Backend restart |

## Deployment Vars

These vars are primarily for deployment wiring and image/runtime composition.

### Core Deployment and Service Wiring

| Variable | Typical Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_VERSION` | `1.22.0` | Version tag used for backend/frontend image selection in Compose manifests. | Must match published image tags. | Redeploy backend/postgres services |
| `GEOPULSE_POSTGRES_HOST` | `geopulse-postgres` | Postgres hostname used to build JDBC URL and service wiring. | Resolvable host inside deployment network. | Redeploy backend/postgres services |
| `GEOPULSE_POSTGRES_PORT` | `5432` | Postgres TCP port used for service wiring. | Integer in range \`1-65535\`. | Redeploy backend/postgres services |
| `GEOPULSE_POSTGRES_DB` | `geopulse` | Postgres database name used by app and database container initialization. | Valid PostgreSQL database identifier. | Redeploy backend/postgres services |
| `GEOPULSE_POSTGRES_USERNAME` | `geopulse-user` | Database username used by backend and Postgres initialization. | Non-empty PostgreSQL role name. | Redeploy backend/postgres services |
| `GEOPULSE_POSTGRES_PASSWORD` | `change-this-secure-password` | Database password used by backend and Postgres initialization. | Use strong secret; do not commit real value to VCS. | Redeploy backend/postgres services |

### Predefined OIDC Provider Deployment Vars

| Variable | Typical Default | Comment | Restrictions | Restart |
|---|---|---|---|---|
| `GEOPULSE_OIDC_PROVIDER_GOOGLE_ENABLED` | `false` | Enable predefined Google provider in deployment templates. | \`true\` or \`false\`. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_GOOGLE_CLIENT_ID` | `(empty)` | Google OIDC client ID. | Required when Google provider is enabled. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_GOOGLE_CLIENT_SECRET` | `(empty)` | Google OIDC client secret. | Sensitive secret. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_GOOGLE_DISCOVERY_URL` | `https://accounts.google.com/.well-known/openid-configuration` | Google OIDC discovery URL. | Must be valid discovery endpoint URL. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_GENERIC_ENABLED` | `false` | Enable predefined generic OIDC provider. | \`true\` or \`false\`. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_GENERIC_NAME` | `Custom OIDC` | Display name for generic provider. | Non-empty label when enabled. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_GENERIC_CLIENT_ID` | `(empty)` | Generic OIDC client ID. | Required when generic provider is enabled. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_GENERIC_CLIENT_SECRET` | `(empty)` | Generic OIDC client secret. | Sensitive secret. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_GENERIC_DISCOVERY_URL` | `(empty)` | Generic OIDC discovery URL. | Valid \`https://.../.well-known/openid-configuration\` URL. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_MICROSOFT_ENABLED` | `(from Helm values)` | Enable predefined Microsoft provider in Helm templates. | \`true\` or \`false\`. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_MICROSOFT_CLIENT_ID` | `(from Helm values)` | Microsoft OIDC client ID. | Required when Microsoft provider is enabled. | Backend redeploy |
| `GEOPULSE_OIDC_PROVIDER_MICROSOFT_CLIENT_SECRET` | `(from Helm values)` | Microsoft OIDC client secret. | Sensitive secret. | Backend redeploy |

## PostgreSQL Tuning Vars

These variables are passed to the PostgreSQL container as startup parameters.

| Variable | Standard Default | Dev Default | Comment | Restrictions | Restart |
|---|---|---|---|---|---|
| `GEOPULSE_POSTGRES_SHARED_BUFFERS` | `256MB` | `128MB` | Main PostgreSQL buffer cache size. | PostgreSQL memory unit (for example \`128MB\`, \`1GB\`). | Postgres container restart |
| `GEOPULSE_POSTGRES_WORK_MEM` | `8MB` | `6MB` | Memory per sort/hash operation per query node. | PostgreSQL memory unit. | Postgres container restart |
| `GEOPULSE_POSTGRES_MAINTENANCE_WORK_MEM` | `64MB` | `32MB` | Memory for maintenance operations (vacuum/index build). | PostgreSQL memory unit. | Postgres container restart |
| `GEOPULSE_POSTGRES_EFFECTIVE_CACHE_SIZE` | `1GB` | `512MB` | Planner estimate for available OS cache. | PostgreSQL memory unit. | Postgres container restart |
| `GEOPULSE_POSTGRES_MAX_WAL_SIZE` | `512MB` | `256MB` | Maximum WAL size before checkpoints are forced. | PostgreSQL memory unit. | Postgres container restart |
| `GEOPULSE_POSTGRES_CHECKPOINT_TARGET` | `0.9` | `0.9` | Target fraction of checkpoint interval used for checkpoint completion. | Decimal \`0.0\` to \`1.0\`. | Postgres container restart |
| `GEOPULSE_POSTGRES_WAL_BUFFERS` | `16MB` | `8MB` | Memory used for WAL buffering. | PostgreSQL memory unit. | Postgres container restart |
| `GEOPULSE_POSTGRES_RANDOM_PAGE_COST` | `1.1` | `1.1` | Planner random I/O cost estimate (often lower on SSD). | Positive decimal. | Postgres container restart |
| `GEOPULSE_POSTGRES_IO_CONCURRENCY` | `100` | `50` | Expected concurrent disk I/O operations. | Positive integer. | Postgres container restart |
| `GEOPULSE_POSTGRES_AUTOVACUUM_NAPTIME` | `60s` | `60s` | Delay between autovacuum runs. | PostgreSQL duration value. | Postgres container restart |
| `GEOPULSE_POSTGRES_VACUUM_SCALE_FACTOR` | `0.2` | `0.2` | Tuple-update fraction threshold before autovacuum. | Decimal greater than or equal to \`0\`. | Postgres container restart |
| `GEOPULSE_POSTGRES_LOG_SLOW_QUERIES` | `5000` | `1000` | Minimum duration (ms) before query is logged as slow. | Integer milliseconds. | Postgres container restart |
| `GEOPULSE_POSTGRES_LOG_STATEMENT` | `(not set)` | `ddl` | Statement logging level for dev compose profile. | One of PostgreSQL accepted values (\`none\`, \`ddl\`, \`mod\`, \`all\`). | Postgres container restart |
| `GEOPULSE_POSTGRES_LOG_CHECKPOINTS` | `(not set)` | `on` | Checkpoint logging flag for dev profile. | \`on\` or \`off\`. | Postgres container restart |
| `GEOPULSE_POSTGRES_LOG_AUTOVACUUM` | `(not set)` | `0` | Autovacuum logging threshold in milliseconds for dev profile. | Integer milliseconds (or \`-1\` to disable). | Postgres container restart |
| `GEOPULSE_POSTGRES_TRACK_FUNCTIONS` | `(not set)` | `all` | Function stats collection level for dev profile. | One of \`none\`, \`pl\`, \`all\`. | Postgres container restart |

## Pattern-Based Vars

### Dynamic OIDC Provider Pattern

Pattern: `GEOPULSE_OIDC_PROVIDER_{PROVIDER}_{PROPERTY}`

| Pattern Part | Comment | Restrictions |
|---|---|---|
| `{PROVIDER}` | Provider identifier (for example `GOOGLE`, `COMPANY`, `KEYCLOAK`). | Uppercase letters, numbers, underscore recommended. Converted to lowercase internally. |
| `{PROPERTY}=ENABLED` | Provider activation flag. | Must be `true` to activate provider. |
| `{PROPERTY}=CLIENT_ID` | OIDC client ID. | Required when provider is enabled. |
| `{PROPERTY}=CLIENT_SECRET` | OIDC client secret. | Required when provider is enabled; treat as secret. |
| `{PROPERTY}=DISCOVERY_URL` | OIDC discovery endpoint. | Required when provider is enabled; valid discovery URL expected. |
| `{PROPERTY}=NAME` | Login button display name. | Optional string. |
| `{PROPERTY}=ICON` | Icon class or URL for provider branding. | Optional string/URL. |
| `{PROPERTY}=SCOPES` | OAuth scopes requested during auth. | Optional, defaults to `openid profile email`. |

## Related Docs

- [Docker Deployment](./docker-compose.md)
- [Helm Chart Deployment & Configuration](./helm-deployment.md)
- [Frontend Nginx Configuration](/docs/system-administration/configuration/frontend)
- [Authentication Configuration](/docs/system-administration/configuration/authentication)
- [OIDC / SSO Configuration](/docs/system-administration/configuration/oidc-sso)
