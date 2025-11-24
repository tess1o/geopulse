# Helm Chart Configuration Guide

This guide explains how to configure GeoPulse when deployed to Kubernetes using the Helm chart. It covers both built-in configuration options and advanced customization techniques.

## Overview

The GeoPulse Helm chart provides two ways to configure the application:

1. **Built-in Values** - Common configuration options available in `values.yaml`
2. **Custom Environment Variables** - For advanced settings not included in the default chart

## Understanding values.yaml

The Helm chart uses a hierarchical `values.yaml` file to configure GeoPulse. When you install or upgrade the chart, you can override these values:

```bash
# Option 1: Command-line flags
helm install geopulse ./helm/geopulse --set config.admin.email="admin@example.com"

# Option 2: Custom values file (recommended)
helm install geopulse ./helm/geopulse -f my-values.yaml
```

## Built-in Configuration Options

The following features are natively supported in the Helm chart and can be configured via `values.yaml`.

### Core Configuration

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| Frontend URL | `config.uiUrl` | `GEOPULSE_UI_URL` | `http://localhost:5555` |
| Cookie Domain | `config.cookieDomain` | `GEOPULSE_COOKIE_DOMAIN` | `""` (empty) |
| Secure Cookies | `config.authSecureCookies` | `GEOPULSE_AUTH_SECURE_COOKIES` | `false` |

### Admin Configuration

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| Admin Email | `config.admin.email` | `GEOPULSE_ADMIN_EMAIL` | `""` |

**Example:**
```yaml
config:
  admin:
    email: "admin@example.com"
```

### Authentication & Registration

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| Registration Enabled | `config.auth.registrationEnabled` | `GEOPULSE_AUTH_REGISTRATION_ENABLED` | `true` |
| Password Registration | `config.auth.passwordRegistrationEnabled` | `GEOPULSE_AUTH_PASSWORD_REGISTRATION_ENABLED` | `true` |
| OIDC Registration | `config.auth.oidcRegistrationEnabled` | `GEOPULSE_AUTH_OIDC_REGISTRATION_ENABLED` | `true` |

**Example:**
```yaml
config:
  auth:
    registrationEnabled: false  # Disable all registration
    passwordRegistrationEnabled: false  # Disable password registration only
    oidcRegistrationEnabled: true  # Allow OIDC registration
```

### JWT Configuration

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| Access Token Lifespan | `config.jwt.accessTokenLifespan` | `GEOPULSE_JWT_ACCESS_TOKEN_LIFESPAN` | `1800` (30 min) |
| Refresh Token Lifespan | `config.jwt.refreshTokenLifespan` | `GEOPULSE_JWT_REFRESH_TOKEN_LIFESPAN` | `604800` (7 days) |
| JWT Issuer | `config.jwt.issuer` | `GEOPULSE_JWT_ISSUER` | `http://localhost:8080` |
| Public Key Location | `config.jwt.publicKeyLocation` | `GEOPULSE_JWT_PUBLIC_KEY_LOCATION` | `file:/app/keys/jwt-public-key.pem` |
| Private Key Location | `config.jwt.privateKeyLocation` | `GEOPULSE_JWT_PRIVATE_KEY_LOCATION` | `file:/app/keys/jwt-private-key.pem` |

**Example:**
```yaml
config:
  jwt:
    accessTokenLifespan: 3600  # 1 hour
    refreshTokenLifespan: 2592000  # 30 days
    issuer: "https://geopulse.example.com"
```

### GPS Filtering

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| Filter Inaccurate Data | `config.gps.filterInaccurateData` | `GEOPULSE_GPS_FILTER_INACCURATE_DATA_ENABLED` | `true` |
| Max Allowed Accuracy | `config.gps.maxAllowedAccuracy` | `GEOPULSE_GPS_MAX_ALLOWED_ACCURACY` | `100` meters |
| Max Allowed Speed | `config.gps.maxAllowedSpeed` | `GEOPULSE_GPS_MAX_ALLOWED_SPEED` | `250` m/s |

**Example:**
```yaml
config:
  gps:
    filterInaccurateData: true
    maxAllowedAccuracy: 50  # More strict filtering
    maxAllowedSpeed: 200
```

### Prometheus Metrics

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| Enabled | `config.prometheus.enabled` | `GEOPULSE_PROMETHEUS_ENABLED` | `false` |
| Refresh Interval | `config.prometheus.refreshInterval` | `GEOPULSE_PROMETHEUS_REFRESH_INTERVAL` | `10m` |
| GPS Points Metrics | `config.prometheus.gpsPoints.enabled` | `GEOPULSE_PROMETHEUS_GPS_POINTS_ENABLED` | `true` |
| User Metrics | `config.prometheus.userMetrics.enabled` | `GEOPULSE_PROMETHEUS_USER_METRICS_ENABLED` | `true` |
| Timeline Metrics | `config.prometheus.timeline.enabled` | `GEOPULSE_PROMETHEUS_TIMELINE_ENABLED` | `true` |
| Favorites Metrics | `config.prometheus.favorites.enabled` | `GEOPULSE_PROMETHEUS_FAVORITES_ENABLED` | `true` |
| Geocoding Metrics | `config.prometheus.geocoding.enabled` | `GEOPULSE_PROMETHEUS_GEOCODING_ENABLED` | `true` |
| Memory Metrics | `config.prometheus.memory.enabled` | `GEOPULSE_PROMETHEUS_MEMORY_ENABLED` | `true` |

**Example:**
```yaml
config:
  prometheus:
    enabled: true
    refreshInterval: "5m"
    # Optionally disable specific metric classes
    memory:
      enabled: false
```

### Geocoding Configuration

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| Primary Provider | `config.geocoding.primaryProvider` | `GEOPULSE_GEOCODING_PRIMARY_PROVIDER` | `nominatim` |
| Fallback Provider | `config.geocoding.fallbackProvider` | `GEOPULSE_GEOCODING_FALLBACK_PROVIDER` | `""` |
| Delay (ms) | `config.geocoding.delayMs` | `GEOPULSE_GEOCODING_DELAY_MS` | `1000` |
| Nominatim Enabled | `config.geocoding.nominatim.enabled` | `GEOPULSE_GEOCODING_NOMINATIM_ENABLED` | `true` |
| Nominatim URL | `config.geocoding.nominatim.url` | `GEOPULSE_GEOCODING_NOMINATIM_URL` | `https://nominatim.openstreetmap.org` |
| Photon Enabled | `config.geocoding.photon.enabled` | `GEOPULSE_GEOCODING_PHOTON_ENABLED` | `false` |
| Photon URL | `config.geocoding.photon.url` | `GEOPULSE_GEOCODING_PHOTON_URL` | `https://photon.komoot.io` |
| Google Maps Enabled | `config.geocoding.googleMaps.enabled` | `GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED` | `false` |
| Google Maps API Key | `config.geocoding.googleMaps.apiKey` | `GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY` | `""` (secret) |
| Mapbox Enabled | `config.geocoding.mapbox.enabled` | `GEOPULSE_GEOCODING_MAPBOX_ENABLED` | `false` |
| Mapbox Access Token | `config.geocoding.mapbox.accessToken` | `GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN` | `""` (secret) |

**Example:**
```yaml
config:
  geocoding:
    primaryProvider: "googlemaps"
    fallbackProvider: "nominatim"
    delayMs: 500
    googleMaps:
      enabled: true
      apiKey: "your-api-key-here"  # Stored in Kubernetes Secret
    nominatim:
      enabled: true
```

### Location Sharing

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| Base URL Override | `config.share.baseUrl` | `GEOPULSE_SHARE_BASE_URL` | `""` (uses uiUrl) |

**Example:**
```yaml
config:
  share:
    baseUrl: "https://share.geopulse.example.com"
```

### OwnTracks

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| Ping Timestamp Override | `config.owntracks.pingTimestampOverride` | `GEOPULSE_OWNTRACKS_PING_TIMESTAMP_OVERRIDE` | `false` |

**Example:**
```yaml
config:
  owntracks:
    pingTimestampOverride: true
```

### OIDC / SSO Configuration

| Feature | values.yaml Path | Environment Variable | Default |
|---------|------------------|----------------------|---------|
| OIDC Enabled | `config.oidc.enabled` | `GEOPULSE_OIDC_ENABLED` | `false` |
| Auto-Link Accounts | `config.oidc.autoLinkAccounts` | `GEOPULSE_OIDC_AUTO_LINK_ACCOUNTS` | `false` |
| Cleanup Enabled | `config.oidc.cleanupEnabled` | `GEOPULSE_OIDC_CLEANUP_ENABLED` | `true` |

#### Google OIDC

```yaml
config:
  oidc:
    enabled: true
    google:
      enabled: true
      clientId: "your-google-client-id.apps.googleusercontent.com"
      clientSecret: "your-google-client-secret"  # Stored in Kubernetes Secret
```

#### Microsoft OIDC

```yaml
config:
  oidc:
    enabled: true
    microsoft:
      enabled: true
      clientId: "your-microsoft-client-id"
      clientSecret: "your-microsoft-client-secret"  # Stored in Kubernetes Secret
```

#### Generic OIDC (Keycloak, Authentik, Okta, etc.)

```yaml
config:
  oidc:
    enabled: true
    generic:
      enabled: true
      name: "Keycloak"  # Display name in UI
      clientId: "geopulse"
      clientSecret: "your-keycloak-client-secret"  # Stored in Kubernetes Secret
      discoveryUrl: "https://keycloak.example.com/realms/master/.well-known/openid-configuration"
```

## Advanced Configuration: Custom Environment Variables

Some GeoPulse features are not exposed in the default Helm chart `values.yaml`. For these advanced settings, you can add custom environment variables to the backend deployment.

### Method 1: Using extraEnv in values.yaml

This is the recommended approach for most custom configuration needs.

**Step 1:** Create a custom values file (`custom-values.yaml`):

```yaml
backend:
  extraEnv:
    - name: GEOPULSE_TIMELINE_PROCESSING_THREADS
      value: "4"
    - name: GEOPULSE_TIMELINE_VIEW_ITEM_LIMIT
      value: "200"
    - name: GEOPULSE_IMPORT_BULK_INSERT_BATCH_SIZE
      value: "1000"
```

**Step 2:** Apply the configuration:

```bash
helm upgrade geopulse ./helm/geopulse -f custom-values.yaml
```

### Method 2: Using ConfigMap for Multiple Variables

For many custom variables, create a dedicated ConfigMap:

**Step 1:** Create `custom-config.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: geopulse-custom-config
  namespace: default
data:
  GEOPULSE_TIMELINE_PROCESSING_THREADS: "4"
  GEOPULSE_TIMELINE_VIEW_ITEM_LIMIT: "200"
  GEOPULSE_IMPORT_BULK_INSERT_BATCH_SIZE: "1000"
  GEOPULSE_TIMELINE_STAYPOINT_RADIUS_METERS: "75"
```

**Step 2:** Apply the ConfigMap:

```bash
kubectl apply -f custom-config.yaml
```

**Step 3:** Reference it in your values file:

```yaml
backend:
  extraEnvFrom:
    - configMapRef:
        name: geopulse-custom-config
```

### Method 3: Using Secrets for Sensitive Data

For sensitive configuration like API keys:

**Step 1:** Create a Kubernetes Secret:

```bash
kubectl create secret generic geopulse-extra-secrets \
  --from-literal=GEOPULSE_AI_SOME_API_KEY='your-secret-key'
```

**Step 2:** Reference it in values:

```yaml
backend:
  extraEnv:
    - name: GEOPULSE_AI_SOME_API_KEY
      valueFrom:
        secretKeyRef:
          name: geopulse-extra-secrets
          key: GEOPULSE_AI_SOME_API_KEY
```

## Managing Secrets in Kubernetes

### Best Practices

1. **Never commit secrets to Git** - Use `.gitignore` for values files with secrets
2. **Use Kubernetes Secrets** - Store sensitive data in Secret objects, not ConfigMaps
3. **Consider External Secret Managers** - For production, use AWS Secrets Manager, HashiCorp Vault, or similar
4. **Rotate secrets regularly** - Update secrets periodically and restart pods

### Using Sealed Secrets

For GitOps workflows, consider using [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets):

```bash
# Install Sealed Secrets controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/controller.yaml

# Create a sealed secret
echo -n 'your-secret-value' | kubectl create secret generic mysecret --dry-run=client --from-file=api-key=/dev/stdin -o yaml | \
  kubeseal -o yaml > sealed-secret.yaml

# Commit sealed-secret.yaml to Git safely
```

## Common Configuration Scenarios

### Production Setup with HTTPS

```yaml
# production-values.yaml
config:
  uiUrl: "https://geopulse.example.com"
  authSecureCookies: true
  admin:
    email: "admin@example.com"
  oidc:
    enabled: true
    google:
      enabled: true
      clientId: "..."
      clientSecret: "..."

ingress:
  enabled: true
  className: "nginx"
  hostname: geopulse.example.com
  tls:
    enabled: true
    secretName: geopulse-tls
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
```

### Self-Hosted Geocoding

```yaml
config:
  geocoding:
    primaryProvider: "nominatim"
    nominatim:
      enabled: true
      url: "https://nominatim.mycompany.internal"
    # Self-hosted Photon as fallback
    fallbackProvider: "photon"
    photon:
      enabled: true
      url: "https://photon.mycompany.internal"
```

### High-Performance Import Configuration

```yaml
backend:
  resources:
    limits:
      memory: 4Gi
      cpu: 2000m
  extraEnv:
    - name: GEOPULSE_IMPORT_BULK_INSERT_BATCH_SIZE
      value: "2000"
    - name: GEOPULSE_IMPORT_MERGE_BATCH_SIZE
      value: "1000"
    - name: GEOPULSE_IMPORT_LARGE_FILE_THRESHOLD_MB
      value: "200"
    - name: GEOPULSE_TIMELINE_PROCESSING_THREADS
      value: "4"
```

### Monitoring with Prometheus

```yaml
config:
  prometheus:
    enabled: true
    refreshInterval: "5m"
    # All metrics enabled by default

# Optional: If using Prometheus Operator
serviceMonitor:
  enabled: true
  interval: 30s
  path: /api/prometheus/metrics
```

## Feature Configuration Reference

For detailed configuration of specific features, see:

- [Admin Panel Configuration](/docs/system-administration/configuration/admin-panel)
- [Authentication Configuration](/docs/system-administration/configuration/authentication)
- [User Registration Management](/docs/system-administration/configuration/user-registration)
- [OIDC/SSO Configuration](/docs/system-administration/configuration/oidc-sso)
- [GPS Data Filtering](/docs/system-administration/configuration/gps-data-filtering)
- [Prometheus Monitoring](/docs/system-administration/monitoring/prometheus)
- [Reverse Geocoding](/docs/system-administration/configuration/reverse-geocoding)
- [Location Sharing](/docs/system-administration/configuration/location-sharing)
- [OwnTracks Configuration](/docs/system-administration/configuration/owntracks-additional-config)
- [Timeline Global Configuration](/docs/system-administration/configuration/timeline-global-config)

## Troubleshooting

### Configuration Not Applied

If your configuration changes aren't taking effect:

1. **Verify the values are set:**
   ```bash
   helm get values geopulse
   ```

2. **Check the ConfigMap:**
   ```bash
   kubectl get configmap geopulse-config -o yaml
   ```

3. **Restart the backend pod:**
   ```bash
   kubectl rollout restart deployment geopulse-backend
   ```

### Finding Environment Variables

To see all environment variables in the running backend:

```bash
kubectl exec -it deployment/geopulse-backend -- env | grep GEOPULSE | sort
```

### Validating Secrets

Check if secrets are properly set:

```bash
# List secret keys (values are base64 encoded)
kubectl get secret geopulse-secret -o jsonpath='{.data}' | jq 'keys'

# Decode a specific secret (be careful with sensitive data)
kubectl get secret geopulse-secret -o jsonpath='{.data.GEOPULSE_ADMIN_EMAIL}' | base64 -d
```

## Upgrading the Chart

When upgrading to a new version:

```bash
# See what will change
helm diff upgrade geopulse ./helm/geopulse -f my-values.yaml

# Upgrade the release
helm upgrade geopulse ./helm/geopulse -f my-values.yaml

# Rollback if needed
helm rollback geopulse
```

## Additional Resources

- [Kubernetes Deployment Guide](/docs/getting-started/deployment/kubernetes-helm)
- [Docker Compose Deployment](/docs/getting-started/deployment/docker-compose) (for comparison)
- [Kubernetes Documentation](https://kubernetes.io/docs/home/)
- [Helm Documentation](https://helm.sh/docs/)
