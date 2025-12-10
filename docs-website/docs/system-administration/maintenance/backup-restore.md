---
title: Backup and Restore
description: Back up and restore your GeoPulse data and configuration.
---

# Backup and Restore

:::info Coming Soon
This documentation is currently being written. Check back soon for updates.
:::

## What This Will Cover

- **Database backups** - PostgreSQL dump and restore procedures
- **Configuration backups** - saving JWT keys, secrets, and settings
- **Automated backup strategies** - scheduling regular backups
- **Disaster recovery** - full system restoration procedures
- **Data migration** - moving GeoPulse between servers

## In the Meantime

For manual PostgreSQL backups:
```bash
# Backup
docker exec -t <postgres-container> pg_dump -U geopulse-user geopulse > backup.sql

# Restore
docker exec -i <postgres-container> psql -U geopulse-user geopulse < backup.sql
```

Remember to also back up:
- JWT keys (from persistent volume or `/app/keys`)
- Environment configurations
- Custom configurations

## Stay Updated

Track progress on this documentation: [GitHub Issues](https://github.com/tess1o/geopulse/issues)
