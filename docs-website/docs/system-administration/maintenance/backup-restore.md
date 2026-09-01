---
title: Backup and Restore
description: Back up and restore your GeoPulse data and configuration.
---

# Backup and Restore

Regular backups are essential for protecting your location tracking data. This guide covers everything you need to know about backing up and restoring your GeoPulse installation.

## What Gets Backed Up?

GeoPulse stores all your data in a PostgreSQL database with PostGIS extensions. This includes:

- **Location points** - all GPS coordinates and timestamps
- **Visits and trips** - processed location data and stay points
- **User accounts** - authentication and preferences
- **Reverse geocoding cache** - address lookups to reduce API calls
- **Settings** - application configuration
- **GPS sources** - source tokens, device IDs, filtering, duplicate detection, and OwnTracks payload encryption settings
- **Relationships and permissions** - friends, sharing links, and friend location permissions

:::tip JWT Keys Don't Need Backup
GeoPulse automatically generates JWT keys on first startup if they don't exist. You don't need to back up the keys from `/app/keys` - if lost, new keys will be generated automatically. Existing users will simply need to log in again.
:::

:::warning Database Backups Need Encryption Keys
If you back up GeoPulse with `pg_dump`, also back up the AI encryption key configured by `GEOPULSE_AI_ENCRYPTION_KEY_LOCATION` (default: `/app/keys/ai-encryption-key.txt`). Without the same key, encrypted database values such as user AI settings and OwnTracks payload encryption secrets cannot be decrypted after restore.
:::

## Encrypted full backups from the admin UI

Use **Administration > Settings > Backup** for complete, password-encrypted `.gpb` backups. GeoPulse streams a native PostgreSQL custom-format dump from one consistent snapshot. The application remains usable during backup. Files appear in the backup list only after encryption and writing succeed; retention runs after successful publication.

A full backup preserves application tables, IDs, credentials, API tokens, relationships, spatial data, sequences, Flyway history, and encrypted settings. Its encrypted manifest records the application version, database schema, PostgreSQL/PostGIS versions, and checksums. It also includes the source installation encryption key **inside the encrypted archive**. It excludes transient OIDC login states and mobile authentication codes. Deployment files, external files, PostgreSQL roles, and JWT keys are not included.

### Passwords

Configure a 12–1024 character backup password before using **Run Backup Now**, **Download Full Backup**, or scheduled backups. GeoPulse stores this password encrypted with the installation key and never returns it in configuration responses. Changing it affects new backups only. Restoration always asks for the password used to create that particular backup. Restore accepts any non-empty password up to 1024 characters so archives created with an older, shorter password remain recoverable.

:::warning Keep the password outside GeoPulse
Save the backup password in a password manager or another secure location independent of this installation. There is no password recovery or bypass. Losing it makes the archive unusable, even if you still have the destination installation's key. A settings export intentionally does not contain this password.
:::

The `.gpb` envelope uses a random Tink `AES256_GCM_HKDF_1MB` Streaming AEAD key. Argon2id derives its password-wrapping key with a fresh salt, 64 MiB memory, three iterations, and one lane. The entire archive, manifest, and source key are authenticated and encrypted. Unsupported envelope versions and parameters are rejected.

### Restore and restart workflow

1. Upload a trusted `.gpb` archive or select a local backup, then enter the password used to create it.
2. GeoPulse authenticates and extracts the archive and restores it into a separate database on the same PostgreSQL server. The application, HTTP ingestion, MQTT, and background jobs remain operational. Every page shows: **“Restoration is being prepared in the background. GeoPulse remains available, but data and changes newer than this backup will be replaced when restoration activates.”**
3. GeoPulse converts all restored secrets to the destination's existing encryption key, preserves the destination backup configuration/password, and validates the replacement. The destination key file is never changed and may be mounted read-only.
4. Once staging is valid, activation starts automatically. Exactly one backend instance must remain at this point. GeoPulse gives browsers three seconds to observe the activation state, pauses the Quarkus scheduler and MQTT ingestion, and then closes its database pool. Requests or background work already in progress may fail during this short cutover.
5. GeoPulse closes its application connection pool and, through a separate connection to the maintenance database, terminates clients connected to the live and staged databases. It renames both databases in one PostgreSQL transaction, disables connections to the retained previous database, records the committed swap, and exits normally.
6. A container or configured service manager may restart the backend automatically. Watch the backend logs and confirm that GeoPulse starts again. A directly launched JAR has no supervisor and normally remains stopped. If the backend does not return, start or restart **only the GeoPulse backend** manually and leave PostgreSQL running. The connection URL remains unchanged. After startup confirms the staged database OID, every browser logs out without refreshing its old token and returns to `/login`.

Preparation errors leave the original application and data available without a restart. If the backend stops during preparation, the next startup discards incomplete staging. If activation cannot acquire its exclusive lock or is interrupted before the pool closes, the original application resumes and the staged database can be retried or discarded from the admin page. If the connection pool was already closed, GeoPulse exits; startup identifies whether PostgreSQL committed or rolled back the cutover by comparing recorded database OIDs.

While activation or identity recovery blocks the application, backend admission control returns `503 Service Unavailable` with `X-GeoPulse-Restore-Blocked: true`. Health/version, maintenance status, logout, and the required admin retry/discard/status endpoints remain available. `/api/maintenance/status` is explicitly non-cacheable and exposes only public lifecycle information.

:::danger Trusted backups only
PostgreSQL dumps execute SQL. Only restore archives created by trusted administrators from trusted installations. Knowing an archive password authenticates its bytes, not the safety of its SQL. Legacy full-backup ZIPs and user-export ZIPs are not accepted by this restore feature.
:::

### Supported versions and database permissions

Version 1 requires matching application database schemas (including Flyway migration history) and matching PostgreSQL major versions. Every recorded extension version must be available on the destination server. The standard images bundle PostgreSQL 17 client tools, matching the bundled PostgreSQL server. No cross-version migration or legacy importer is provided. Databases using the standard libc locale are supported; ICU databases are currently rejected during preparation.

The application role needs read access to the entire source database for backup and ownership/access to all restored application objects. The restore role must connect to the maintenance database, create databases from `template0`, create the required PostGIS extensions, terminate database sessions, change database connection permissions, rename the live/staging databases, and assume the application role (`SET ROLE`). A dedicated restore role can be supplied; otherwise the application credentials are used. Default Compose PostgreSQL credentials have the required privileges. Managed PostgreSQL services may require administrator configuration or may not support this workflow. Activation deliberately disconnects **every client of the live and staged GeoPulse databases**; unrelated databases are untouched.

### Persistent storage and runtime configuration

Full backup behavior is configured from **Administration > Settings > Backup**. These values are stored in `system_settings`; when no database value exists, GeoPulse falls back to the matching runtime property/environment variable. After you save a value in the admin UI, that database value takes precedence over the environment fallback.

| Admin setting | Property / environment variable | Default | Purpose |
| --- | --- | --- | --- |
| `backup.password` | `geopulse.backup.password` / `GEOPULSE_BACKUP_PASSWORD` | Empty | Password used for new encrypted `.gpb` backups. Saved values are encrypted in GeoPulse; environment values are ordinary deployment secrets. |
| `backup.scheduled.enabled` | `geopulse.backup.scheduled.enabled` / `GEOPULSE_BACKUP_SCHEDULED_ENABLED` | `false` | Enables scheduled full backups. |
| `backup.scheduled.cron` | `geopulse.backup.scheduled.cron` / `GEOPULSE_BACKUP_SCHEDULED_CRON` | `0 0 3 * * ?` | Quarkus cron expression for scheduled full backups. |
| `backup.local.path` | `geopulse.backup.local.path` / `GEOPULSE_BACKUP_LOCAL_PATH` | `/data/geopulse-backups` | Folder where encrypted full backup files are published and listed. |
| `backup.retention.count` | `geopulse.backup.retention.count` / `GEOPULSE_BACKUP_RETENTION_COUNT` | `7` | Number of local encrypted full backups to retain after a successful backup. |
| `backup.operation.timeout-minutes` | `geopulse.backup.operation.timeout-minutes` / `GEOPULSE_BACKUP_OPERATION_TIMEOUT_MINUTES` | `120` | Maximum duration for full backup and restore operations. |

The working directory must survive **process, container, and pod replacement**, independently of database-stored backup-folder settings. Keep it private to the backend OS user, on storage supporting atomic file replacement and durable fsync. Never delete or relocate its journal during an operation. All replicas of one deployment must use the same working storage; restoration still requires reducing to one instance.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `GEOPULSE_BACKUP_WORK_PATH` | `/data/geopulse-backups/.work` | External restore journal, encrypted upload, temporary extracted dump |
| `GEOPULSE_BACKUP_BINARY_DIRECTORY` | PATH (JVM); `/usr/pgsql-17/bin` (native image) | Matching-major `pg_dump` and `pg_restore` binaries |
| `GEOPULSE_BACKUP_MAINTENANCE_DATABASE` | `postgres` | Database used for coordination and activation |
| `GEOPULSE_BACKUP_RESTORE_USERNAME` | Application username | Optional dedicated restore role |
| `GEOPULSE_BACKUP_RESTORE_PASSWORD` | Application password | Dedicated restore role password |

Set these and the datasource/key settings through environment variables or `-D` JVM properties. Connection details must point directly to a single PostgreSQL server, not a transaction-pooling endpoint. Budget filesystem space for encrypted uploads plus the extracted native dump, and PostgreSQL space for the live, staged, and retained previous databases plus WAL/index construction. The operation timeout is configured in the admin page. Streaming avoids loading whole backups into the Java heap; Argon2 additionally needs 64 MiB per operation.

Compose mounts `./backups` at `/data/geopulse-backups`. Unraid uses the `backups` directory under `GEOPULSE_APPDATA`. For the default backend UID, prepare host permissions with:

```bash
mkdir -p backups
sudo chown -R 1001:0 backups
sudo chmod -R g+rwX backups
```

If you override the container user, use that UID instead. Do not make the working directory world-readable. Manual/Proxmox installations must install matching PostgreSQL clients and configure a persistent writable working directory.

Helm enables `backend.backupPersistence` by default with a 20 GiB PVC mounted at `/data/geopulse-backups`; `GEOPULSE_BACKUP_WORK_PATH` defaults to `/data/geopulse-backups/.work`. Configure `existingClaim`, `storageClass`, `size`, `mountPath`, and `workPath` as needed. Do not use `emptyDir` for restoration. Backup password, schedule, retention, backup folder, and operation timeout are normally configured in **Administration > Settings > Backup**; their environment variables are only startup defaults/fallbacks and are overridden once saved in GeoPulse. Before activation, reduce backend replicas to one. Keep PostgreSQL running and keep the health probe available. Requests already in flight may fail while the database pool closes and the backend process exits. If the backend does not return automatically, restart or replace only the backend pod while keeping the backup PVC attached.

### Backend restart and manual fallback

GeoPulse calls `Quarkus.asyncExit(0)` after a successful cutover. This stops the backend process; GeoPulse does not start a replacement process itself. Containers and pods restart only when their configured restart policy does so. A directly launched JAR remains stopped unless an external supervisor starts it again.

Watch the backend logs after activation and confirm that GeoPulse completes startup. If it does not return, use the normal control for the backend only:

- Compose: `docker compose restart geopulse-backend`.
- Kubernetes: restart or replace the single backend pod while keeping the backup PVC attached.
- Unraid: restart the GeoPulse backend container from the Docker page.
- Direct JAR: run the same backend start command again.
- systemd/Proxmox/other supervisors: restart only the backend process or service.

**Do not restart PostgreSQL.** GeoPulse exits its own process; it does not call Docker, Kubernetes, systemd, or hypervisor APIs.

#### Why can `/api/maintenance/status` log an error while the backend exits?

The maintenance page polls this endpoint while waiting for the backend to return. One poll can reach Quarkus after shutdown has begun but before the HTTP socket has closed, producing a `500` or an `Error Occurred After Shutdown` message. This shutdown race does not by itself mean the database swap failed. Check the subsequent backend logs and health status after restart. If no new backend process starts, restart the GeoPulse backend manually.

### Recovery and previous-database cleanup

The private `restore-state.json` records operation state, database names/OIDs, destination key fingerprint, and sanitized errors. It contains no restore password. Completed operation journals move into the private `.work/history` directory when a later restore starts, preserving the exact retained database name for cleanup. Activation records `ACTIVATING` before renaming; if commit acknowledgement is lost, normal startup connects through the unchanged application URL and compares OIDs to recognize whether the swap committed. It never repeats database renames during startup.

After activation, the original database remains named `gp_previous_<operation-id-without-hyphens>` with connections disabled. No automatic rollback occurs once users can write to restored data. Confirm that the restore is correct, then an administrator can connect to the maintenance database and explicitly `DROP DATABASE` the exact previous database recorded in the journal. Dropping it permanently removes that recovery copy; do not use `CASCADE` schema deletion or wildcard cleanup. The current archive retention policy never deletes previous databases.

For activation failure, stop every backend instance, retain a copy of the journal, and inspect the database names/OIDs through the maintenance database. If the transaction rolled back, restart GeoPulse and use **Retry Activation** or **Discard Prepared Restore**. If the rename committed but startup cannot be repaired, a database administrator may restore the previous database name in a transaction after ensuring both databases have no clients, re-enable connections to the recovered original, and archive/remove the failed operation journal **before** restarting. Preserve the failed restored database for investigation. Never rename based only on guessed names, and never roll back this way after normal writes have resumed without an explicit data-recovery decision.

#### What if the backend crashes between the two rename statements?

Both `ALTER DATABASE ... RENAME` statements run in one PostgreSQL transaction. If the backend disappears before commit, PostgreSQL rolls the whole transaction back and the original database keeps its configured name. On restart, GeoPulse recognizes the original OID and reports **Activation Retryable**. If PostgreSQL committed but the backend lost the commit response, the configured name has the staged OID and the retained previous name has the original OID; startup recognizes that as a completed swap. Do not guess which case occurred from names alone: use the OIDs stored in `restore-state.json`. An unexpected third identity is treated as **Activation Failed** and requires the recovery procedure above.

### Settings and user exports remain separate

**Admin settings export** is JSON for global settings and provider configuration. It can contain plaintext provider credentials; handle it as sensitive. It excludes the backup password, runtime infrastructure, and user data. Ordinary per-user export/import is unchanged and is not a full-database restore.

## Manual PostgreSQL backups (separate from `.gpb`)

The commands below are independent DBA workflows, not files accepted by the admin restore UI. Unlike `.gpb`, raw dumps require a separate copy of the source encryption key and separate encryption/storage protection.


### Creating a Backup

The simplest way to back up your GeoPulse database directly is using `pg_dump`:

```bash
# Create a compressed backup with current timestamp
docker exec -t geopulse-postgres pg_dump \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  -F c \
  -f /tmp/backup.dump

# Copy the backup from container to your host
docker cp geopulse-postgres:/tmp/backup.dump \
  ./geopulse-backup-$(date +%Y%m%d-%H%M%S).dump
```

Or create a plain SQL backup:

```bash
# Plain SQL format (larger but human-readable)
docker exec -t geopulse-postgres pg_dump \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  > geopulse-backup-$(date +%Y%m%d-%H%M%S).sql
```

:::info Backup Formats
- **Custom format (-F c)**: Compressed, faster to restore, supports selective restore
- **Plain SQL**: Larger files but can be inspected and edited with a text editor
:::

:::warning Back Up `/app/keys/ai-encryption-key.txt`
`pg_dump` backs up encrypted database values as encrypted text. To restore those values on another server, copy the AI encryption key from the source server too. With the default Docker Compose setup, this file is mounted from `./keys/ai-encryption-key.txt` on the host to `/app/keys/ai-encryption-key.txt` in the backend container.

JWT private/public keys are different: they are not required for data recovery, and replacing them only signs users out.
:::

### Restoring from Backup

Before restoring a `pg_dump` backup onto a new server, place the source server's AI encryption key at the path configured by `GEOPULSE_AI_ENCRYPTION_KEY_LOCATION` (default: `/app/keys/ai-encryption-key.txt`). If the key is missing or different, encrypted AI settings and OwnTracks payload encryption secrets from the database backup will not be readable.

#### Restore Custom Format Backup

```bash
# Stop GeoPulse services
docker compose stop geopulse-backend geopulse-ui

# Copy backup into container
docker cp ./geopulse-backup.dump geopulse-postgres:/tmp/

# Drop existing database and recreate (WARNING: destroys current data)
docker exec -i geopulse-postgres psql -U ${GEOPULSE_POSTGRES_USERNAME} -d postgres <<EOF
DROP DATABASE ${GEOPULSE_POSTGRES_DB};
CREATE DATABASE ${GEOPULSE_POSTGRES_DB};
EOF

# Restore the backup
docker exec -i geopulse-postgres pg_restore \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  -F c \
  -v \
  /tmp/backup.dump

# Restart GeoPulse services
docker compose start geopulse-backend geopulse-ui
```

#### Restore SQL Format Backup

```bash
# Stop GeoPulse services
docker compose stop geopulse-backend geopulse-ui

# Drop existing database and recreate
docker exec -i geopulse-postgres psql -U ${GEOPULSE_POSTGRES_USERNAME} -d postgres <<EOF
DROP DATABASE ${GEOPULSE_POSTGRES_DB};
CREATE DATABASE ${GEOPULSE_POSTGRES_DB};
EOF

# Restore from SQL file
docker exec -i geopulse-postgres psql \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  < geopulse-backup.sql

# Restart GeoPulse services
docker compose start geopulse-backend geopulse-ui
```

## Automated Backups

### Using Cron (Linux/macOS)

Create a backup script:

```bash
#!/bin/bash
# /usr/local/bin/geopulse-backup.sh

BACKUP_DIR="/var/backups/geopulse"
RETENTION_DAYS=30

mkdir -p "$BACKUP_DIR"

# Create backup filename with timestamp
BACKUP_FILE="$BACKUP_DIR/geopulse-$(date +%Y%m%d-%H%M%S).dump"

# Perform backup
docker exec -t geopulse-postgres pg_dump \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  -F c \
  > "$BACKUP_FILE"

# Verify backup was created
if [ $? -eq 0 ] && [ -f "$BACKUP_FILE" ]; then
    echo "Backup created successfully: $BACKUP_FILE"

    # Delete backups older than retention period
    find "$BACKUP_DIR" -name "geopulse-*.dump" -mtime +$RETENTION_DAYS -delete
    echo "Cleaned up backups older than $RETENTION_DAYS days"
else
    echo "Backup failed!"
    exit 1
fi
```

Make it executable and add to crontab:

```bash
# Make script executable
chmod +x /usr/local/bin/geopulse-backup.sh

# Edit crontab
crontab -e

# Add daily backup at 2 AM
0 2 * * * /usr/local/bin/geopulse-backup.sh >> /var/log/geopulse-backup.log 2>&1
```

Remember to back up the AI encryption key alongside scheduled `pg_dump` files. The database dump alone is not enough for a complete restore of encrypted app settings.

### Using Docker Container (Cross-platform)

Add a backup service to your `docker-compose.yml`:

```yaml
services:
  geopulse-backup:
    image: prodrigestivill/postgres-backup-local:17
    container_name: geopulse-backup
    restart: unless-stopped
    environment:
      POSTGRES_HOST: geopulse-postgres
      POSTGRES_DB: ${GEOPULSE_POSTGRES_DB}
      POSTGRES_USER: ${GEOPULSE_POSTGRES_USERNAME}
      POSTGRES_PASSWORD: ${GEOPULSE_POSTGRES_PASSWORD}
      SCHEDULE: "@daily"  # Run daily at midnight
      BACKUP_KEEP_DAYS: 30
      BACKUP_KEEP_WEEKS: 4
      BACKUP_KEEP_MONTHS: 6
      HEALTHCHECK_PORT: 8080
    volumes:
      - ./backups:/backups
    depends_on:
      - geopulse-postgres
```

Then start the backup service:

```bash
docker compose up -d geopulse-backup
```

This service backs up PostgreSQL only. Keep a separate secure copy of the AI encryption key file from the source server.

## Verification and Testing

Always verify your backups are working:

```bash
# Check backup file size (should not be 0)
ls -lh geopulse-backup.dump

# Verify backup integrity (custom format)
docker exec -i geopulse-postgres pg_restore --list /tmp/backup.dump

# Test restore to a temporary database
docker exec -i geopulse-postgres psql -U ${GEOPULSE_POSTGRES_USERNAME} -d postgres <<EOF
CREATE DATABASE geopulse_test;
EOF

docker exec -i geopulse-postgres pg_restore \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d geopulse_test \
  -F c \
  /tmp/backup.dump

# If successful, clean up test database
docker exec -i geopulse-postgres psql -U ${GEOPULSE_POSTGRES_USERNAME} -d postgres <<EOF
DROP DATABASE geopulse_test;
EOF
```

## Backup Best Practices

### Storage Recommendations

1. **Keep backups off-server**: Store backups on a different machine or cloud storage
2. **Use 3-2-1 rule**: 3 copies, 2 different media types, 1 offsite
3. **Encrypt sensitive backups**: Use GPG or similar tools for backups containing location data

Example encrypted backup:

```bash
# Create and encrypt backup
docker exec -t geopulse-postgres pg_dump \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  -F c | gpg --encrypt --recipient your@email.com \
  > geopulse-backup-$(date +%Y%m%d).dump.gpg

# Restore encrypted backup
gpg --decrypt geopulse-backup.dump.gpg | \
  docker exec -i geopulse-postgres pg_restore \
    -U ${GEOPULSE_POSTGRES_USERNAME} \
    -d ${GEOPULSE_POSTGRES_DB} \
    -F c
```

### Retention Strategy

Configure retention based on your needs:

- **Daily backups**: Keep for 7-30 days
- **Weekly backups**: Keep for 3-6 months
- **Monthly backups**: Keep for 1-2 years
- **Before upgrades**: Always create a backup before updating GeoPulse

## Disaster Recovery

### Complete System Restoration

If you need to restore GeoPulse on a new server:

1. **Install Docker and Docker Compose** on the new server

2. **Clone your GeoPulse configuration**:
```bash
# Copy your .env, docker-compose.yml, and encryption keys to new server
scp .env docker-compose.yml newserver:/opt/geopulse/
scp -r keys newserver:/opt/geopulse/
```

At minimum, the copied `keys` folder must include the AI encryption key used by the source database. JWT key files are optional for disaster recovery; preserving them keeps already-issued JWTs valid, while replacing them signs users in again.

3. **Start PostgreSQL only**:
```bash
cd /opt/geopulse
docker compose up -d geopulse-postgres
```

4. **Restore your backup**:
```bash
# Copy backup to new server
scp geopulse-backup.dump newserver:/opt/geopulse/

# Restore database
docker cp geopulse-backup.dump geopulse-postgres:/tmp/
docker exec -i geopulse-postgres pg_restore \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  -F c \
  /tmp/geopulse-backup.dump
```

5. **Start all services**:
```bash
docker compose up -d
```

6. **Verify restoration**:
```bash
# Check services are running
docker compose ps

# Check database connectivity
docker exec geopulse-postgres psql \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  -c "SELECT COUNT(*) FROM points;"
```

## Migration Between Servers

To move GeoPulse from one server to another:

1. **Create backup on source server** (see Manual Backups above)
2. **Transfer backup file** to destination server
3. **Follow disaster recovery steps** to restore on new server
4. **Update DNS/firewall** rules to point to new server
5. **Verify everything works** before decommissioning old server

## Troubleshooting

### Save Failed: Backup Folder Is Not Writable

If saving Backup settings fails with an error like:

```text
Backup folder is not writable: /data/geopulse-backups
```

the host directory mounted into the backend container is not writable by the GeoPulse backend user. In the default Docker Compose setup, `/data/geopulse-backups` maps to `./backups` next to your `docker-compose.yml`.

Check the host directory:

```bash
ls -lah backups
```

If it is owned by `root:root` and has permissions like `drwxr-xr-x`, only root can write to it. Fix ownership and group write permissions:

```bash
sudo chown -R 1001:0 backups
sudo chmod -R g+rwX backups
```

For example, if GeoPulse is installed in `/srv/projects/dev/geopulse-dev`:

```bash
cd /srv/projects/dev/geopulse-dev
sudo chown -R 1001:0 backups
sudo chmod -R g+rwX backups
```

You can verify write access from inside the backend container:

```bash
docker compose exec geopulse-backend sh -c 'touch /data/geopulse-backups/.write-test && rm /data/geopulse-backups/.write-test'
```

After the verification command succeeds, save the Backup settings again.

### Backup Taking Too Long

If backups are slow with large databases:

```bash
# Use parallel dump for faster backups (PostgreSQL 17+)
docker exec -t geopulse-postgres pg_dump \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  -F d \
  -j 4 \
  -f /tmp/backup-dir

# Copy directory format backup
docker cp geopulse-postgres:/tmp/backup-dir ./geopulse-backup/
```

### Out of Disk Space During Restore

Monitor disk space during operations:

```bash
# Check available space
docker exec geopulse-postgres df -h /var/lib/postgresql/data

# Increase docker volume size if needed
docker volume inspect postgres-data
```

### PostGIS Extensions Issues

If restore fails with PostGIS errors:

```bash
# Ensure PostGIS extension exists before restore
docker exec -i geopulse-postgres psql \
  -U ${GEOPULSE_POSTGRES_USERNAME} \
  -d ${GEOPULSE_POSTGRES_DB} \
  -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

## Additional Resources

- [PostgreSQL Backup Documentation](https://www.postgresql.org/docs/current/backup.html)
- [PostGIS Backup Best Practices](https://postgis.net/docs/manual-3.5/using_postgis_dbmanagement.html#backup_restore)
- [Docker Volume Backup Strategies](https://docs.docker.com/storage/volumes/#back-up-restore-or-migrate-data-volumes)
