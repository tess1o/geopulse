---
title: Upgrading to PostgreSQL 18
description: Move an existing GeoPulse database from PostgreSQL 17 / PostGIS 3.5 to PostgreSQL 18 / PostGIS 3.6.
---

# Upgrading to PostgreSQL 18

New GeoPulse installations use PostgreSQL 18 with PostGIS 3.6. Installations created earlier run PostgreSQL 17 with PostGIS 3.5, and they keep working: GeoPulse supports both versions, including full backups and restores. You can move to PostgreSQL 18 whenever it suits you.

PostgreSQL 18 cannot open the data files of PostgreSQL 17, so the upgrade copies the database: it is dumped with `pg_dump`, restored into a new PostgreSQL 18 database, checked, and only then switched in. GeoPulse provides a script that does all of this for Docker Compose, Unraid and Helm, and keeps the PostgreSQL 17 data so you can go back.

<!-- Screenshot placeholder: terminal output of a successful upgrade-postgres-18.sh run -->

## Do I need to do anything?

| Installation | What to do |
|---|---|
| New installation | Nothing. It already runs PostgreSQL 18. |
| Docker Compose or Unraid, existing | Nothing is required. Your `docker-compose.yml` still pins PostgreSQL 17. Upgrade with the script when convenient. |
| Helm, existing | The chart now defaults to PostgreSQL 18. Run the script **before** upgrading the chart, or pin `postgres.image.tag=17-3.5` in your values. |
| External PostgreSQL (`postgres.enabled=false`, managed database) | Nothing. GeoPulse works with PostgreSQL 17 and 18; upgrade your database server with your provider's tools. |
| Manual / Proxmox LXC | See [Manual installations](#manual-and-proxmox-lxc-installations). |

## How the upgrade works

The new PostgreSQL 18 database is built **next to** the PostgreSQL 17 one, on the same Docker volume, Unraid appdata folder or Kubernetes PVC. Nothing has to be renamed or moved to a new volume.

```text
Docker volume / appdata folder                 Helm PVC (/var/lib/postgresql/data)
before:                                         before:
  PG_VERSION, base/, global/, ...  (PG 17)        pgdata/                          (PG 17)
after:                                          after:
  18/docker/                       (PG 18)        pgdata/                          (PG 18)
  pg17-pre-upgrade-<timestamp>/    (PG 17)        pgdata-pg17-pre-upgrade-<time>/  (PG 17)
                                                  upgrade-<timestamp>/             (dump)
```

The script runs these steps and stops at the first problem:

1. Checks the installation and stops GeoPulse (the backend and the UI; nothing else).
2. Refuses to continue if any other client is still connected to the database, and lists it.
3. Records row counts, sequences, indexes and a few sample queries, then dumps the database.
4. Builds PostgreSQL 18 in a new folder and restores the dump into it.
5. Compares the copy with the original. If anything differs, the copy is removed and GeoPulse starts again on PostgreSQL 17.
6. Moves the PostgreSQL 17 files aside, switches the image (and, for Docker, the volume mount), and starts PostgreSQL 18.
7. Starts GeoPulse and waits until it is healthy. If it is not, the script switches back to PostgreSQL 17 automatically.

Nothing is deleted. The dump, a copy of the database roles and the PostgreSQL 17 files stay until you remove them with `--cleanup`.

If the script is interrupted, run it again. It reads where the previous run stopped and either continues or undoes the unfinished part.

## Before you start

- **Update GeoPulse first** to a version with PostgreSQL 18 support, and keep that version during the database upgrade. The script checks this.
- **Disk space**: the data volume needs room for a second copy of the database, and the backup folder needs room for the dump (usually much smaller than the database). The script shows both numbers before it starts.
- **Downtime**: GeoPulse is offline during the upgrade. A few GB usually take minutes; the counts and the restore grow with the database size.
- **Other tools**: stop anything else that connects to the database, such as Grafana or a database client.
- **Optional extra safety**: create a full backup in **Administration > Settings > Backup**. Backups made on PostgreSQL 17 can be restored after the upgrade.
- Your `keys/` folder (JWT keys and the AI encryption key) is not touched.

## Docker Compose

Run the script on the Docker host, in the folder with your `docker-compose.yml` and `.env`:

```bash
cd /opt/geopulse   # your GeoPulse folder
curl -L -o upgrade-postgres-18.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/upgrade-postgres-18.sh
chmod +x upgrade-postgres-18.sh
./upgrade-postgres-18.sh
```

The script finds the PostgreSQL container, its volume and the compose file through the labels Docker Compose sets. It shows what it found and asks before changing anything. It only needs the `docker` CLI with the compose plugin; PostgreSQL tools on the host are not required.

When it switches to PostgreSQL 18 it edits two lines of the `geopulse-postgres` service and keeps the original as `docker-compose.yml.pg17-pre-upgrade-<timestamp>.bak`:

```yaml
  geopulse-postgres:
    image: postgis/postgis:18-3.6                 # was postgis/postgis:17-3.5
    volumes:
      - postgres-data:/var/lib/postgresql         # was postgres-data:/var/lib/postgresql/data
```

PostgreSQL 18 images keep their data in a version-named subfolder (`18/docker`), so the volume is mounted one level higher than before. If you prefer to edit the file yourself, run the script with `--no-compose-edit`: it stops after moving the files, prints the edits, and continues when you run it again.

The dump and the reports are written to `./postgres-upgrade-backups/`:

| File | Content |
|---|---|
| `geopulse-<timestamp>.dump` | The full database dump (`pg_dump` custom format), with a `.sha256` checksum. |
| `geopulse-<timestamp>.globals.sql` | Database roles and their password hashes. Keep it private. |
| `geopulse-<timestamp>.snapshot.txt` / `.validation.txt` | What was compared, before and after. |

### Options

| Option | Purpose |
|---|---|
| `--yes` | Do not ask for confirmation. |
| `--backup-dir DIR` | Where to write the dump and reports. |
| `--target-image IMAGE` | The PostgreSQL 18 image. By default the tag `17-3.5` is replaced with `18-3.6` in the same repository, so `imresamu/postgis:17-3.5-alpine` becomes `imresamu/postgis:18-3.6-alpine`. |
| `--no-compose-edit` | Print the compose edits instead of making them. |
| `--container NAME` | The PostgreSQL container (default `geopulse-postgres`). |
| `--app-containers LIST`, `--backend NAME` | Containers stopped during the upgrade, and the backend whose health is checked. By default these are the other running containers of the PostgreSQL container's compose project. |
| `--health-timeout SEC` | How long to wait for GeoPulse after the switch (default 600). |
| `--rollback`, `--cleanup` | See [Rolling back](#rolling-back) and [Cleaning up](#cleaning-up). |

### Several instances on one host

If you run more than one GeoPulse (for example prod, dev and a demo) on the same host, upgrade them one at a time and name the PostgreSQL container of the instance you want:

```bash
./upgrade-postgres-18.sh --container geopulse-postgres-dev --backup-dir ./postgres-upgrade-backups-dev
```

The compose file, the service name, the volume and the containers to stop are all read from that container's compose project, so the other instances keep running. The script lists what it will stop before it asks to continue. Use the same `--container` for `--rollback` and `--cleanup`.

For a PostgreSQL container that is not managed by Docker Compose, pass `--app-containers` and `--backend` yourself; the script does not guess.

### ARM64 (Raspberry Pi)

If you use `imresamu/postgis:17-3.5-alpine`, the script picks `imresamu/postgis:18-3.6-alpine`. It verifies that the image exists before doing anything.

## Unraid

Open the Unraid terminal and run the script from the GeoPulse appdata folder, keeping the backups on the array:

```bash
cd /mnt/user/appdata/geopulse
curl -L -o upgrade-postgres-18.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/upgrade-postgres-18.sh
chmod +x upgrade-postgres-18.sh
./upgrade-postgres-18.sh --backup-dir /mnt/user/appdata/geopulse/postgres-upgrade-backups
```

The PostgreSQL 18 files are created inside the existing `/mnt/user/appdata/geopulse/postgres` folder, so no paths change. The script edits the stack's compose file in place; Compose Manager Plus shows the new image and mount afterwards.

<!-- Screenshot placeholder: Compose Manager Plus showing the geopulse stack on postgis/postgis:18-3.6 -->

## Kubernetes / Helm

Run the Helm script from a machine with `kubectl` and `helm` access to the cluster, **before** upgrading to a chart that defaults to PostgreSQL 18:

```bash
curl -L -o upgrade-postgres-18.sh https://raw.githubusercontent.com/tess1o/GeoPulse/main/helm/upgrade-postgres-18.sh
chmod +x upgrade-postgres-18.sh
./upgrade-postgres-18.sh --release geopulse --namespace geopulse \
  --chart geopulse/geopulse --chart-version <your current chart version>
```

The script:

1. Scales the backend to 0 and dumps the database onto the PostgreSQL PVC (`upgrade-<timestamp>/`), and copies it to `./postgres-upgrade-backups/` on your machine (`--skip-local-copy` to skip).
2. Scales PostgreSQL to 0 and starts a temporary PostgreSQL 18 pod on the same PVC, which restores the dump into `pgdata-18-new` and validates it.
3. Renames `pgdata` to `pgdata-pg17-pre-upgrade-<timestamp>` and `pgdata-18-new` to `pgdata`.
4. Runs `helm upgrade --reuse-values --set postgres.image.tag=18-3.6` and waits until the backend is ready. If it is not, it rolls the data and the release back.

The PVC needs free space for the dump plus a second copy of the database. Upgrade state is kept in the ConfigMap `<release>-postgres-pg18-upgrade`.

Without `--chart`, the script changes the StatefulSet image with `kubectl` instead. In that case set `postgres.image.tag: "18-3.6"` in your values before the next `helm upgrade`, otherwise Helm switches the image back (PostgreSQL then refuses to start; no data is lost).

:::warning Upgraded the chart first?
A release that still has PostgreSQL 17 data and is switched to the PostgreSQL 18 image does not start: the postgres pod logs `database files are incompatible with server`. Nothing is lost. Go back to PostgreSQL 17, then run the script:

```bash
helm rollback <release> -n <namespace>          # or set postgres.image.tag=17-3.5
kubectl -n <namespace> delete pod <release>-postgres-0
```

The pod has to be deleted by hand: a StatefulSet does not replace a pod that is stuck on a broken template, even after the template is reverted.
:::

## Rolling back

If something is wrong after the upgrade, switch back to PostgreSQL 17:

```bash
./upgrade-postgres-18.sh --rollback
```

This stops GeoPulse, moves the PostgreSQL 17 files back, restores the original compose file (Docker) or image (Helm), and starts GeoPulse again. The PostgreSQL 18 data is kept as `pg18-failed-<timestamp>/` (Docker) or `pgdata-18-failed-<timestamp>` (Helm).

:::warning
Data saved after the upgrade (new GPS points, timeline changes, settings) exists only in PostgreSQL 18 and is not part of the rollback. Export what you need first.
:::

You can run the upgrade again later; every run uses a new timestamp.

## Cleaning up

Once GeoPulse runs well on PostgreSQL 18, free the space used by the PostgreSQL 17 files:

```bash
./upgrade-postgres-18.sh --cleanup
```

It deletes the kept PostgreSQL 17 files, asks about folders left by rolled-back attempts and, on Helm, about the dump on the PVC. Use the same options (`--container`, `--release`, `--namespace`, ...) as for the upgrade. Dumps in your local backup folder are never deleted by the script; remove them yourself when you no longer need them.

## Checking the result yourself

The script compares, before and after:

- the extensions and the database migration history
- the number of rows in every GeoPulse table
- every sequence position
- the number of indexes and constraints, and that none is invalid
- the latest GPS point per user, the number of points near it (a PostGIS spatial query) and recent stays

To check by hand:

```bash
docker exec geopulse-postgres psql -U geopulse-user -d geopulse -c "SELECT version(), postgis_lib_version()"
docker exec geopulse-postgres psql -U geopulse-user -d geopulse -c "SELECT count(*) FROM gps_points"
```

Then open GeoPulse and look at your timeline and map.

## Manual upgrade with Docker

The script is the recommended way. If you prefer to run the steps yourself, this is the equivalent for the standard compose file. Replace `geopulse_postgres-data` with your volume name (`docker volume ls`).

```bash
# 1. Stop GeoPulse and dump the database
docker compose stop geopulse-backend geopulse-ui
docker exec geopulse-postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > geopulse-pg17.dump
docker exec geopulse-postgres sh -c 'pg_dumpall -U "$POSTGRES_USER" --globals-only' > geopulse-pg17.globals.sql
docker exec -i geopulse-postgres pg_restore --list < geopulse-pg17.dump > /dev/null && echo "dump OK"

# 2. Stop PostgreSQL 17 and move its files aside on the same volume
docker compose stop geopulse-postgres
docker run --rm -v geopulse_postgres-data:/v postgis/postgis:18-3.6 sh -c \
  'cd /v && mkdir pg17-pre-upgrade && mv PG_VERSION base global pg_* postgresql.conf postgresql.auto.conf postmaster.opts pg17-pre-upgrade/'
```

Edit `docker-compose.yml` as shown in [Docker Compose](#docker-compose) (image and volume mount), then:

```bash
# 3. Start PostgreSQL 18 and restore into an empty database
docker compose up -d geopulse-postgres
docker exec geopulse-postgres sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" -T template0 "$POSTGRES_DB"'
docker exec -i geopulse-postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error --no-owner --no-acl' < geopulse-pg17.dump
docker exec geopulse-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT postgis_extensions_upgrade()" -c "ANALYZE"'

# 4. Start GeoPulse
docker compose up -d
```

The PostGIS image creates its extensions in a new database automatically, which is why the database is dropped and created again from `template0` before the restore. To go back, stop PostgreSQL, move the files from `pg17-pre-upgrade/` back to the top of the volume, remove `18/`, and restore the old compose file.

:::warning Do not use `docker exec -t` for dumps
`-t` attaches a terminal, which changes line endings in the output and corrupts binary dumps. Use `docker exec` without `-t` when redirecting to a file.
:::

## Manual and Proxmox LXC installations

On Debian or Ubuntu with the PostgreSQL APT repository, install the PostgreSQL 18 packages and upgrade the cluster in place:

```bash
sudo systemctl stop geopulse-backend
sudo apt-get install -y postgresql-18 postgresql-18-postgis-3
sudo pg_dropcluster 18 main --stop          # the empty cluster created by the package
sudo pg_upgradecluster -m dump 17 main      # dump/restore into a new PostgreSQL 18 cluster
sudo systemctl start geopulse-backend
```

`pg_upgradecluster` keeps the PostgreSQL 17 cluster (stopped, on another port) until you remove it with `sudo pg_dropcluster 17 main`. On RHEL-based systems, use `pg_dump`/`pg_restore` as in the manual Docker steps, or `pg_upgrade` with the `postgis36_18` package installed.

## Troubleshooting

**`Error: in 18+, these Docker images are configured to store database data in a format which is compatible with "pg_ctlcluster"`**

The image was switched to PostgreSQL 18 without migrating the data. Nothing was changed or lost. Restore the previous image and mount in the compose file, or run the upgrade script. PostgreSQL may leave an empty `18/docker` folder on the volume; it is harmless and the script replaces it.

**`database files are incompatible with server` (Helm)**

See [Upgraded the chart first?](#kubernetes--helm).

**"Other clients are still connected"**

Stop the listed clients (for example Grafana or a database tool) and run the script again. GeoPulse itself is stopped by the script.

**"Other database roles exist"**

Roles other than the GeoPulse database user (for example a read-only Grafana user) are not recreated, and grants are not copied. Recreate them from `geopulse-<timestamp>.globals.sql` after the upgrade.

**"Only geopulse is migrated"**

Other databases in the same PostgreSQL server are not copied. Leftovers from GeoPulse restores (`gp_previous_*`, `gp_restore_*`) can be ignored. Your own databases stay in the kept PostgreSQL 17 files; dump them before running `--cleanup`.

**Custom setups**

The script refuses custom tablespaces, a custom `PGDATA`, a `user:` override on the PostgreSQL container, and `POSTGRES_PASSWORD_FILE`. Use the [manual steps](#manual-upgrade-with-docker) for those.

## Backup compatibility

GeoPulse full backups record the PostgreSQL version they were made on.

| Backup made on | Restore onto PostgreSQL 17 | Restore onto PostgreSQL 18 |
|---|---|---|
| PostgreSQL 17 | Yes | Yes |
| PostgreSQL 18 | No | Yes |

Backups are always created with the `pg_dump` matching the server. The GeoPulse images include the PostgreSQL 17 and 18 client tools and pick the right ones automatically. See [Backup & Restore](./backup-restore.md).
