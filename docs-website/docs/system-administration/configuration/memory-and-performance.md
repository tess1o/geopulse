---
title: Memory and Performance Tuning
description: What uses memory in a GeoPulse installation, the defaults, recommended setups, how to override them, and what to do when something goes wrong.
---

# Memory and Performance Tuning

GeoPulse spends most of its time idle: it receives GPS points and occasionally rebuilds part of a
timeline. The defaults are chosen to keep that idle footprint small while still handling a full
timeline rebuild of a large history. This page explains where memory goes, how to change the
defaults, and how to read the most common errors.

## Where memory goes

| Component | What it holds | Main settings |
|---|---|---|
| Backend heap | Request data, and during a timeline rebuild the stays and trips being built | Container memory limit, `MaximumHeapSizePercent`, `MaxNewSize`, `MaxHeapFree` |
| PostgreSQL connections | Each open connection keeps its own catalog, PostGIS and prepared-statement caches, about **50 MiB** in our measurements, until the connection closes | `GEOPULSE_DB_POOL_*` |
| PostgreSQL shared memory | The shared buffer cache | `GEOPULSE_POSTGRES_SHARED_BUFFERS` |

The number of open database connections usually matters more than the backend process itself. Four
idle connections are about 200 MiB of PostgreSQL memory.

## Defaults

### Backend (native image)

The native image starts with:

```
-XX:MaximumHeapSizePercent=70 -XX:MaxNewSize=33554432 -XX:MaxHeapFree=8388608 -XX:+ExitOnOutOfMemoryError
```

| Option | Meaning |
|---|---|
| `-XX:MaximumHeapSizePercent=70` | Maximum heap is 70% of the container memory limit. **Without a container limit it is 70% of the host's RAM.** |
| `-XX:MaxNewSize=33554432` | Caps the young generation (where short-lived request objects live) at 32 MiB. |
| `-XX:MaxHeapFree=8388608` | After a garbage collection, keep at most 8 MiB of free heap and return the rest to the OS. |
| `-XX:+ExitOnOutOfMemoryError` | If the heap runs out, the process exits (and is restarted by Docker/Kubernetes/systemd) instead of continuing in a broken state. |

The native image's garbage collector only runs when the application allocates memory. It does not
collect on a timer while idle, so memory is returned at the next collection after activity.

### Database connection pool

| Variable | Default | Meaning |
|---|---|---|
| `GEOPULSE_DB_POOL_MAX_SIZE` | `16` | Maximum open connections. |
| `GEOPULSE_DB_POOL_MIN_SIZE` | `0` | Connections kept open even when idle. |
| `GEOPULSE_DB_POOL_IDLE_REMOVAL_INTERVAL` | `1M` | Connections idle this long are closed (checked at the same interval, so closing takes one to two intervals). |
| `GEOPULSE_DB_POOL_MAX_LIFETIME` | `30m` | Connections are replaced after this time, which also releases their PostgreSQL memory. |
| `GEOPULSE_DB_POOL_ACQUISITION_TIMEOUT` | `60s` | How long a request waits for a free connection before failing. |

Durations use Quarkus syntax: `30s`, `1M` (one minute), `30m`, `1H`.

Besides the pool, every running backend keeps one more connection open for its whole lifetime (it
appears as `GeoPulse-<id>` in `pg_stat_activity`). It holds a lock that tells backup restore whether a
backend is running, and stays small because it runs no other queries. So the backend opens at most
`GEOPULSE_DB_POOL_MAX_SIZE + 1` connections, and one while idle.

### System settings cache

Background jobs check admin settings every few seconds. The backend keeps those settings in memory, so
these checks do not query the database and do not keep pool connections from closing while GeoPulse is
idle.

| Variable | Default | Meaning |
|---|---|---|
| `GEOPULSE_SETTINGS_CACHE_ENABLED` | `true` | Set to `false` to read settings from the database on every use. |
| `GEOPULSE_SETTINGS_CACHE_TTL` | `1h` | How long a setting is kept in memory. `0` also turns the cache off. |

Settings changed in GeoPulse (the admin panel, the API or a settings backup import) take effect
immediately. Only rows edited directly in the `system_settings` table wait for the TTL or a backend
restart. If a setting change ever seems not to apply, set `GEOPULSE_SETTINGS_CACHE_ENABLED=false` and
restart the backend.

### Measured effect of the defaults

Synthetic single-user test, 300,000 GPS points, backend limit 512 MiB, idle for six minutes after
browsing the timeline, statistics and places pages:

| | Previous defaults | Current defaults |
|---|--:|--:|
| Backend memory right after startup | 65 MiB | 28 MiB |
| Backend memory after browsing, idle | 71 MiB | 50 MiB |
| Open database connections, idle | 4 | 1 |
| PostgreSQL private memory, idle | 250 MiB | 29 MiB |
| Full rebuild of 2,000,000 points | 12.2 s | 12.8–13.4 s |

The smaller young generation costs about 6% on a full timeline rebuild. Day-to-day work (receiving
points, incremental timeline updates, browsing) is not affected in a measurable way.

## Recommended setups

These are starting points, not guarantees: memory needs grow with the number of users, the size of
their histories and how many jobs run at once.

### Small host (Raspberry Pi, 1–2 GB RAM, one or two users)

- Keep the backend limit at `512m`.
- `GEOPULSE_DB_POOL_MAX_SIZE=8`
- `GEOPULSE_POSTGRES_SHARED_BUFFERS=128MB`, `GEOPULSE_POSTGRES_WORK_MEM=4MB`

### Typical installation (default Docker Compose)

The defaults: backend limit `512m`, pool of 16 with one-minute idle removal.

### Large histories or frequent big imports

A full rebuild keeps the stays and trips it builds in memory until it saves them. In our
2,000,000-point test (40,000 stays and 40,000 trips) the rebuild needed about 175 MiB of live heap.

- Raise the backend limit to `1g` (Compose `mem_limit`, Helm `backend.resources.limits.memory`).
  The heap follows the limit automatically.
- For faster full rebuilds, allow a bigger young generation:
  `NATIVE_IMAGE_OPTS_EXTRA=-XX:MaxNewSize=67108864`

### Several active users

- Increase `GEOPULSE_DB_POOL_MAX_SIZE` (for example `24`) and make sure PostgreSQL's
  `max_connections` is higher than the pool size plus any other clients.
- Consider `GEOPULSE_DB_POOL_MIN_SIZE=2` to avoid reconnecting under steady load. Each kept
  connection costs PostgreSQL memory permanently.

## How to override settings

### Docker Compose

Add the variables to your `.env` file (the backend loads it), then recreate the backend container
with `docker compose up -d`:

```bash
# .env
GEOPULSE_DB_POOL_MAX_SIZE=8
GEOPULSE_DB_POOL_IDLE_REMOVAL_INTERVAL=2M
NATIVE_IMAGE_OPTS_EXTRA=-XX:MaxNewSize=67108864
```

Change the container limit with `mem_limit` on the `geopulse-backend` service.

### Helm

Use `backend.extraEnv` and `backend.resources`:

```yaml
backend:
  resources:
    limits:
      memory: 1Gi
  extraEnv:
    - name: GEOPULSE_DB_POOL_MAX_SIZE
      value: "24"
    - name: NATIVE_IMAGE_OPTS_EXTRA
      value: "-XX:MaxNewSize=67108864"
```

### Manual installation (systemd)

The binary takes the options on its command line, and the `GEOPULSE_DB_POOL_*` variables in its
environment file. Without a container, `MaximumHeapSizePercent` is a share of the whole host's RAM,
so set an explicit heap size instead:

```ini
# /etc/geopulse/geopulse.env
GEOPULSE_DB_POOL_MAX_SIZE=8

# geopulse-backend.service
ExecStart=/opt/geopulse/backend/geopulse-backend -Dquarkus.http.host=0.0.0.0 -Xmx384m -XX:MaxNewSize=33554432 -XX:MaxHeapFree=8388608 -XX:+ExitOnOutOfMemoryError
```

### Runtime options: replace or append

| Variable | Effect |
|---|---|
| `NATIVE_IMAGE_OPTS_EXTRA` | Appended after the defaults. A later option overrides an earlier one, so use this to change one setting and keep the rest. |
| `NATIVE_IMAGE_OPTS` | Replaces the defaults entirely. Include every option you still want. |
| `JAVA_OPTS_EXTRA` / `JAVA_OPTS` | The same for the JVM image. |

## Troubleshooting

### The backend container restarts during a timeline rebuild or import

The heap ran out and `-XX:+ExitOnOutOfMemoryError` stopped the process so it could restart cleanly.
The job that was running is not saved; the timeline keeps its previous state.

1. Check the last log lines before the restart for `OutOfMemoryError`.
2. Raise the backend memory limit (the heap follows it), or set a larger heap explicitly, for example
   `NATIVE_IMAGE_OPTS_EXTRA=-XX:MaximumHeapSizePercent=80`.
3. Start the rebuild again.

### The container is killed with exit code 137 (`OOMKilled: true`)

The container used more memory than its limit. Heap is only part of the process (code, thread
stacks and native buffers come on top), so a heap percentage close to 100 leaves no room.

- Lower `MaximumHeapSizePercent` (for example to `60`) or raise the container limit.
- Check with `docker inspect geopulse-backend --format '{{.State.OOMKilled}} {{.State.ExitCode}}'`.

### A timeline job stays at the same percentage and never finishes

Older versions could stall here when the heap ran out. With the current defaults the backend restarts
instead. If you replaced the defaults with `NATIVE_IMAGE_OPTS`, make sure it still contains
`-XX:+ExitOnOutOfMemoryError`, then follow the steps for restarts above.

### `Acquisition timeout` / "Unable to acquire JDBC Connection"

All pool connections were busy for longer than `GEOPULSE_DB_POOL_ACQUISITION_TIMEOUT`.

- Increase `GEOPULSE_DB_POOL_MAX_SIZE` if several users or jobs work at the same time.
- If it happens with a single user, look for a long-running job (rebuild, import, export) in the
  backend logs; the pool may be fine and the database slow.

### `FATAL: sorry, too many clients already`

PostgreSQL's `max_connections` is lower than the connections everyone is trying to open. Lower
`GEOPULSE_DB_POOL_MAX_SIZE` or raise `max_connections` in PostgreSQL. The backend needs the pool size
plus one, and other clients (backups, admin tools) count too.

### PostgreSQL uses a lot of memory while GeoPulse is idle

Usually open connections. Check how many there are:

```sql
SELECT usename, state, count(*) FROM pg_stat_activity GROUP BY 1, 2;
```

With the defaults, idle pool connections close within about two minutes, leaving the one
`GeoPulse-<id>` lock connection. If more stay open, check that `GEOPULSE_DB_POOL_MIN_SIZE` is `0` and
`GEOPULSE_DB_POOL_IDLE_REMOVAL_INTERVAL` is short.
`GEOPULSE_POSTGRES_SHARED_BUFFERS` is shared memory PostgreSQL keeps regardless of connections.

### The backend uses more memory than expected while idle

Memory is returned to the OS at garbage collections, and collections only happen when the application
allocates. Right after heavy work (an import or a full rebuild) memory can stay higher until the next
activity. `-XX:MaxHeapFree` controls how much free heap is kept after each collection.

### PostgreSQL JIT compilation

GeoPulse turns off PostgreSQL JIT compilation on its own database connections, for every PostgreSQL
version and also with an external PostgreSQL. The planner's cost estimates for PostGIS queries are high,
so PostgreSQL compiled queries that run in milliseconds, and compiling often took longer than the query:
a year of statistics loaded in 2.1 s with JIT and 1.0 s without. PostgreSQL 19 turns JIT off by
default for the same reason. Your server's `jit` setting is unchanged for other clients.

The setting is part of `quarkus.datasource.jdbc.new-connection-sql`, the SQL each new connection runs.
If you override that property (`QUARKUS_DATASOURCE_JDBC_NEW_CONNECTION_SQL`), keep both statements:

```properties
quarkus.datasource.jdbc.new-connection-sql=SET TIME ZONE 'UTC'; SET jit = off
```

## Related docs

- [Environment Variables Reference](/docs/getting-started/deployment/environment-variables)
- [Docker Compose Deployment](/docs/getting-started/deployment/docker-compose)
- [Helm Chart Deployment & Configuration](/docs/getting-started/deployment/helm-deployment)
- [Manual Installation](/docs/getting-started/deployment/manual-installation)
