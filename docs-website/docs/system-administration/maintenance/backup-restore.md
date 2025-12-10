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

:::tip JWT Keys Don't Need Backup
GeoPulse automatically generates JWT keys on first startup if they don't exist. You don't need to back up the keys from `/app/keys` - if lost, new keys will be generated automatically. Existing users will simply need to log in again.
:::

## Manual Backups

### Creating a Backup

The simplest way to back up your GeoPulse database is using `pg_dump`:

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

### Restoring from Backup

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
# Copy your .env file and docker-compose.yml to new server
scp .env docker-compose.yml newserver:/opt/geopulse/
```

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
