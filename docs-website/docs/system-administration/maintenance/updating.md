---
title: How to Update GeoPulse
description: Update your GeoPulse installation to the latest version.
---

# How to Update GeoPulse

This guide covers updating GeoPulse to the latest version for both Docker Compose and Kubernetes/Helm deployments.

:::tip
Always check the [GitHub Releases](https://github.com/tess1o/geopulse/releases) for release notes and breaking changes before updating.
:::

## Docker Compose Deployments

### Step 1: Update Version in .env File

Edit your `.env` file and update the `GEOPULSE_VERSION` variable to the desired version:

```bash
GEOPULSE_VERSION=1.9.0
```

### Step 2: Pull Images and Restart Services

You can use the following script to update the backend and frontend services:

```bash
#!/bin/bash
set -e

COMPOSE_FILE="docker-compose.yml"
SERVICES="geopulse-backend geopulse-ui"

echo "📥 Pulling latest images for $SERVICES..."
docker compose -f "$COMPOSE_FILE" pull $SERVICES

echo "🛑 Stopping and removing containers for $SERVICES..."
docker compose -f "$COMPOSE_FILE" rm -fsv $SERVICES

echo "🚀 Starting $SERVICES..."
docker compose -f "$COMPOSE_FILE" up -d $SERVICES

echo "✅ Backend and frontend redeployed successfully!"
```

Save this script as `update-geopulse.sh`, make it executable with `chmod +x update-geopulse.sh`, and run it:

```bash
./update-geopulse.sh
```

### Database Migrations

Database migrations are handled automatically when the backend starts up. You don't need to run any manual migration commands.

### CORS/OIDC Variable Migration (Compatibility)

Recent versions introduced explicit CORS and public base URL variables while keeping legacy behavior for existing setups.

| Purpose | New variables | Legacy fallback | Compatibility behavior |
|---|---|---|---|
| CORS enable/disable | `GEOPULSE_CORS_ENABLED` | none | If not set, backend keeps compatibility default (`true`). New `.env.example` sets `false`. |
| CORS origins | `GEOPULSE_CORS_ORIGINS` | `GEOPULSE_UI_URL` | Legacy fallback still works, but is deprecated. |
| OIDC callback base URL | `GEOPULSE_OIDC_CALLBACK_BASE_URL` | `GEOPULSE_PUBLIC_BASE_URL`, then `GEOPULSE_UI_URL` | Existing setups continue working; explicit callback URL is recommended. |

Recommended post-upgrade actions:
1. Set `GEOPULSE_PUBLIC_BASE_URL` to your external URL.
2. Keep `GEOPULSE_CORS_ENABLED=false` for standard nginx same-origin deployments.
3. Use `GEOPULSE_OIDC_CALLBACK_BASE_URL` explicitly for OIDC deployments.

## Kubernetes/Helm Deployments

### Step 1: Update Chart Values

Update your `values.yaml` file or chart repository to point to the new version:

```yaml
image:
  tag: "1.9.0"  # Update to the desired version
```

### Step 2: Upgrade with Helm

Run the Helm upgrade command to deploy the new version:

```bash
helm upgrade geopulse ./helm/geopulse -f your-values.yaml
```

If you're using a remote chart repository:

```bash
helm repo update
helm upgrade geopulse geopulse/geopulse -f your-values.yaml
```

### Step 3: Verify Deployment

Check that the pods are running with the new version:

```bash
kubectl get pods -l app=geopulse
kubectl describe pod <pod-name>
```

Database migrations are handled automatically by the backend on startup.

## Rollback Procedures

### Docker Compose Rollback

If you need to rollback to a previous version:

1. Update the `GEOPULSE_VERSION` in your `.env` file to the previous version
2. Run the update script again to deploy the older version

### Kubernetes/Helm Rollback

Use Helm's built-in rollback functionality:

```bash
# List release history
helm history geopulse

# Rollback to previous revision
helm rollback geopulse

# Rollback to specific revision
helm rollback geopulse <revision-number>
```

## Version Compatibility

GeoPulse follows semantic versioning. When updating:

- **Patch versions** (e.g., 1.9.0 → 1.9.1): Bug fixes, safe to update
- **Minor versions** (e.g., 1.9.0 → 1.10.0): New features, backward compatible
- **Major versions** (e.g., 1.9.0 → 2.0.0): Breaking changes, review release notes carefully

Always review the release notes on [GitHub Releases](https://github.com/tess1o/geopulse/releases) before updating, especially for major and minor version changes.
