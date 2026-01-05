---
title: Helm Chart Deployment & Configuration
sidebar_label: Helm Chart Reference
description: Complete reference for deploying and configuring GeoPulse using Helm on Kubernetes.
---

# GeoPulse Helm Chart Deployment & Configuration

This comprehensive guide covers manual Helm installation, configuration options, and advanced customization for GeoPulse on Kubernetes.

:::tip Quick Start
For an interactive, guided installation experience, see the [Kubernetes Quick Start Guide](./kubernetes-helm.md) which uses automated scripts.
:::

---

## Overview

The GeoPulse Helm chart provides a production-ready deployment with the following features:

- **Full Stack Deployment**: Backend (Java/Quarkus in Native mode), Frontend (Vue.js), PostgreSQL with PostGIS
- **Optional MQTT Support**: Conditional Mosquitto MQTT broker deployment
- **Production Ready**: Health checks, resource limits, persistent storage
- **Flexible Configuration**: Extensive values.yaml for customization
- **Security**: Automatic JWT key generation, secrets management
- **Ingress Support**: Optional ingress with TLS
- **High Availability Ready**: Support for replicas and pod disruption budgets

### Prerequisites

- Kubernetes 1.19+
- Helm 3.2.0+
- PV provisioner support in the underlying infrastructure (for persistence)
- (Optional) Ingress controller for external access
- (Optional) cert-manager for TLS certificates

---

## Manual Installation

This section describes how to install the chart directly with Helm for advanced users or automated workflows.

### 1. Clone the Repository

```bash
git clone https://github.com/tess1o/GeoPulse.git
cd GeoPulse
```

### 2. Install the Chart

You can install the chart using the `helm install` command. You must provide your own values, either with `--set` flags or a custom values file (`-f my-values.yaml`).

```bash
# Example installing with a custom values file from the examples
helm install geopulse ./helm/geopulse -f helm/examples/medium-deployment.yaml
```

### 3. Verify Installation

```bash
# Check pod status
kubectl get pods -l app.kubernetes.io/instance=geopulse

# Run Helm tests
helm test geopulse
```

---

## Configuration

The GeoPulse Helm chart provides two ways to configure the application:

1. **Built-in Values** - Common configuration options available in `values.yaml`
2. **Custom Environment Variables** - For advanced settings not included in the default chart

### Understanding values.yaml

The Helm chart uses a hierarchical `values.yaml` file to configure GeoPulse. When you install or upgrade the chart, you can override these values:

```bash
# Option 1: Command-line flags
helm install geopulse ./helm/geopulse --set config.admin.email="admin@example.com"

# Option 2: Custom values file (recommended)
helm install geopulse ./helm/geopulse -f my-values.yaml
```

### Built-in Configuration Options

#### Core Configuration

| Feature        | values.yaml Path           | Environment Variable           | Default                 |
|----------------|----------------------------|--------------------------------|-------------------------|
| Frontend URL   | `config.uiUrl`             | `GEOPULSE_UI_URL`              | `http://localhost:5555` |
| Cookie Domain  | `config.cookieDomain`      | `GEOPULSE_COOKIE_DOMAIN`       | `""` (empty)            |
| Secure Cookies | `config.authSecureCookies` | `GEOPULSE_AUTH_SECURE_COOKIES` | `false`                 |

#### Admin Configuration

| Feature     | values.yaml Path     | Environment Variable   | Default |
|-------------|----------------------|------------------------|---------|
| Admin Email | `config.admin.email` | `GEOPULSE_ADMIN_EMAIL` | `""`    |

**Example:**

```yaml
config:
  admin:
    email: "admin@example.com"
```

#### Authentication & Registration

| Feature               | values.yaml Path                          | Environment Variable                          | Default |
|-----------------------|-------------------------------------------|-----------------------------------------------|---------|
| Registration Enabled  | `config.auth.registrationEnabled`         | `GEOPULSE_AUTH_REGISTRATION_ENABLED`          | `true`  |
| Password Registration | `config.auth.passwordRegistrationEnabled` | `GEOPULSE_AUTH_PASSWORD_REGISTRATION_ENABLED` | `true`  |
| OIDC Registration     | `config.auth.oidcRegistrationEnabled`     | `GEOPULSE_AUTH_OIDC_REGISTRATION_ENABLED`     | `true`  |

**Example:**

```yaml
config:
  auth:
    registrationEnabled: false  # Disable all registration
    passwordRegistrationEnabled: false  # Disable password registration only
    oidcRegistrationEnabled: true  # Allow OIDC registration
```

#### JWT Configuration

| Feature                | values.yaml Path                  | Environment Variable                  | Default                              |
|------------------------|-----------------------------------|---------------------------------------|--------------------------------------|
| Access Token Lifespan  | `config.jwt.accessTokenLifespan`  | `GEOPULSE_JWT_ACCESS_TOKEN_LIFESPAN`  | `1800` (30 min)                      |
| Refresh Token Lifespan | `config.jwt.refreshTokenLifespan` | `GEOPULSE_JWT_REFRESH_TOKEN_LIFESPAN` | `604800` (7 days)                    |
| JWT Issuer             | `config.jwt.issuer`               | `GEOPULSE_JWT_ISSUER`                 | `http://localhost:8080`              |
| Public Key Location    | `config.jwt.publicKeyLocation`    | `GEOPULSE_JWT_PUBLIC_KEY_LOCATION`    | `file:/app/keys/jwt-public-key.pem`  |
| Private Key Location   | `config.jwt.privateKeyLocation`   | `GEOPULSE_JWT_PRIVATE_KEY_LOCATION`   | `file:/app/keys/jwt-private-key.pem` |

**Example:**

```yaml
config:
  jwt:
    accessTokenLifespan: 3600  # 1 hour
    refreshTokenLifespan: 2592000  # 30 days
    issuer: "https://geopulse.example.com"
```

#### GPS Filtering

| Feature                | values.yaml Path                  | Environment Variable                          | Default      |
|------------------------|-----------------------------------|-----------------------------------------------|--------------|
| Filter Inaccurate Data | `config.gps.filterInaccurateData` | `GEOPULSE_GPS_FILTER_INACCURATE_DATA_ENABLED` | `true`       |
| Max Allowed Accuracy   | `config.gps.maxAllowedAccuracy`   | `GEOPULSE_GPS_MAX_ALLOWED_ACCURACY`           | `100` meters |
| Max Allowed Speed      | `config.gps.maxAllowedSpeed`      | `GEOPULSE_GPS_MAX_ALLOWED_SPEED`              | `250` m/s    |

**Example:**

```yaml
config:
  gps:
    filterInaccurateData: true
    maxAllowedAccuracy: 50  # More strict filtering
    maxAllowedSpeed: 200
```

#### Prometheus Metrics

| Feature            | values.yaml Path                        | Environment Variable                       | Default |
|--------------------|-----------------------------------------|--------------------------------------------|---------|
| Enabled            | `config.prometheus.enabled`             | `GEOPULSE_PROMETHEUS_ENABLED`              | `false` |
| Refresh Interval   | `config.prometheus.refreshInterval`     | `GEOPULSE_PROMETHEUS_REFRESH_INTERVAL`     | `10m`   |
| GPS Points Metrics | `config.prometheus.gpsPoints.enabled`   | `GEOPULSE_PROMETHEUS_GPS_POINTS_ENABLED`   | `true`  |
| User Metrics       | `config.prometheus.userMetrics.enabled` | `GEOPULSE_PROMETHEUS_USER_METRICS_ENABLED` | `true`  |
| Timeline Metrics   | `config.prometheus.timeline.enabled`    | `GEOPULSE_PROMETHEUS_TIMELINE_ENABLED`     | `true`  |
| Favorites Metrics  | `config.prometheus.favorites.enabled`   | `GEOPULSE_PROMETHEUS_FAVORITES_ENABLED`    | `true`  |
| Geocoding Metrics  | `config.prometheus.geocoding.enabled`   | `GEOPULSE_PROMETHEUS_GEOCODING_ENABLED`    | `true`  |
| Memory Metrics     | `config.prometheus.memory.enabled`      | `GEOPULSE_PROMETHEUS_MEMORY_ENABLED`       | `true`  |

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

#### Geocoding Configuration

| Feature             | values.yaml Path                      | Environment Variable                     | Default                               |
|---------------------|---------------------------------------|------------------------------------------|---------------------------------------|
| Primary Provider    | `config.geocoding.primaryProvider`    | `GEOPULSE_GEOCODING_PRIMARY_PROVIDER`    | `nominatim`                           |
| Fallback Provider   | `config.geocoding.fallbackProvider`   | `GEOPULSE_GEOCODING_FALLBACK_PROVIDER`   | `""`                                  |
| Delay (ms)          | `config.geocoding.delayMs`            | `GEOPULSE_GEOCODING_DELAY_MS`            | `1000`                                |
| Nominatim Enabled   | `config.geocoding.nominatim.enabled`  | `GEOPULSE_GEOCODING_NOMINATIM_ENABLED`   | `true`                                |
| Nominatim URL       | `config.geocoding.nominatim.url`      | `GEOPULSE_GEOCODING_NOMINATIM_URL`       | `https://nominatim.openstreetmap.org` |
| Photon Enabled      | `config.geocoding.photon.enabled`     | `GEOPULSE_GEOCODING_PHOTON_ENABLED`      | `false`                               |
| Photon URL          | `config.geocoding.photon.url`         | `GEOPULSE_GEOCODING_PHOTON_URL`          | `https://photon.komoot.io`            |
| Google Maps Enabled | `config.geocoding.googleMaps.enabled` | `GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED` | `false`                               |
| Google Maps API Key | `config.geocoding.googleMaps.apiKey`  | `GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY` | `""` (secret)                         |
| Mapbox Enabled      | `config.geocoding.mapbox.enabled`     | `GEOPULSE_GEOCODING_MAPBOX_ENABLED`      | `false`                               |
| Mapbox Access Token | `config.geocoding.mapbox.accessToken` | `GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN` | `""` (secret)                         |

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
      language: "en-US"  # Optional: BCP 47 language tag
    photon:
      language: "en-US"  # Optional: BCP 47 language tag
```

#### Location Sharing

| Feature           | values.yaml Path       | Environment Variable      | Default                            |
|-------------------|------------------------|---------------------------|------------------------------------|
| Base URL Override | `config.share.baseUrl` | `GEOPULSE_SHARE_BASE_URL` | `""` (uses window.location.origin) |

**Example:**

```yaml
config:
  share:
    baseUrl: "https://share.geopulse.example.com"
```

#### User Invitation

| Feature           | values.yaml Path            | Environment Variable           | Default                            |
|-------------------|-----------------------------|--------------------------------|------------------------------------|
| Base URL Override | `config.invitation.baseUrl` | `GEOPULSE_INVITATION_BASE_URL` | `""` (uses window.location.origin) |

**Example:**

```yaml
config:
  invitation:
    baseUrl: "https://invite.geopulse.example.com"
```

#### OwnTracks

| Feature                 | values.yaml Path                         | Environment Variable                         | Default |
|-------------------------|------------------------------------------|----------------------------------------------|---------|
| Ping Timestamp Override | `config.owntracks.pingTimestampOverride` | `GEOPULSE_OWNTRACKS_PING_TIMESTAMP_OVERRIDE` | `false` |

**Example:**

```yaml
config:
  owntracks:
    pingTimestampOverride: true
```

#### OIDC / SSO Configuration

| Feature            | values.yaml Path               | Environment Variable               | Default |
|--------------------|--------------------------------|------------------------------------|---------|
| OIDC Enabled       | `config.oidc.enabled`          | `GEOPULSE_OIDC_ENABLED`            | `false` |
| Auto-Link Accounts | `config.oidc.autoLinkAccounts` | `GEOPULSE_OIDC_AUTO_LINK_ACCOUNTS` | `false` |
| Cleanup Enabled    | `config.oidc.cleanupEnabled`   | `GEOPULSE_OIDC_CLEANUP_ENABLED`    | `true`  |

##### Google OIDC

```yaml
config:
  oidc:
    enabled: true
    google:
      enabled: true
      clientId: "your-google-client-id.apps.googleusercontent.com"
      clientSecret: "your-google-client-secret"  # Stored in Kubernetes Secret
```

##### Microsoft OIDC

```yaml
config:
  oidc:
    enabled: true
    microsoft:
      enabled: true
      clientId: "your-microsoft-client-id"
      clientSecret: "your-microsoft-client-secret"  # Stored in Kubernetes Secret
```

##### Generic OIDC (Keycloak, Authentik, Okta, etc.)

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

---

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

---

## Configuration Examples

### Minimal Setup (Local Testing)

```yaml
# minimal-values.yaml
postgres:
  persistence:
    enabled: false

keygen:
  persistence:
    enabled: false
```

```bash
helm install geopulse ./helm/geopulse -f minimal-values.yaml
```

### Production Setup with Ingress

```yaml
# production-values.yaml
# Resource allocation for production
backend:
  replicaCount: 2
  resources:
    limits:
      memory: 2Gi
      cpu: 2000m
    requests:
      memory: 1Gi
      cpu: 1000m

frontend:
  replicaCount: 2

postgres:
  persistence:
    enabled: true
    size: 50Gi
    storageClass: "fast-ssd"
  resources:
    limits:
      memory: 4Gi
      cpu: 2000m

# Ingress configuration
ingress:
  enabled: true
  className: nginx
  hostname: geopulse.example.com
  tls:
    enabled: true
    secretName: geopulse-tls
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"

# Application configuration
config:
  uiUrl: "https://geopulse.example.com"
  cookieDomain: ".example.com"
  authSecureCookies: true
  admin:
    email: "admin@example.com"
  oidc:
    enabled: true
    google:
      enabled: true
      clientId: "..."
      clientSecret: "..."
```

```bash
helm install geopulse ./helm/geopulse -f production-values.yaml
```

### Enable MQTT Broker

```yaml
# mqtt-values.yaml
mosquitto:
  enabled: true
  username: mqtt_admin
  service:
    type: LoadBalancer  # Or NodePort for external access
  persistence:
    enabled: true

config:
  # Update UI URL if needed
  uiUrl: "http://your-domain:5555"
```

```bash
helm install geopulse ./helm/geopulse -f mqtt-values.yaml
```

### Use External PostgreSQL

```yaml
# external-db-values.yaml
postgres:
  enabled: false

externalPostgres:
  host: postgres.example.com
  port: 5432
  database: geopulse
  username: geopulse-user
  password: "your-secure-password"
```

```bash
helm install geopulse ./helm/geopulse -f external-db-values.yaml
```

### Self-Hosted Geocoding

```yaml
config:
  geocoding:
    primaryProvider: "nominatim"
    nominatim:
      enabled: true
      url: "https://nominatim.mycompany.internal"
      language: "de"  # Optional: BCP 47 language tag
    # Self-hosted Photon as fallback
    fallbackProvider: "photon"
    photon:
      enabled: true
      url: "https://photon.mycompany.internal"
      language: "de"  # Optional: BCP 47 language tag
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

---

## Parameters Reference

### Global Parameters

| Parameter                 | Description        | Default        |
|---------------------------|--------------------|----------------|
| `global.imagePullPolicy`  | Image pull policy  | `IfNotPresent` |
| `global.imagePullSecrets` | Image pull secrets | `[]`           |

### Backend Parameters

| Parameter                         | Description                                                                                                                                                                                                    | Default                   |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------|
| `backend.image.repository`        | Backend image repository                                                                                                                                                                                       | `tess1o/geopulse-backend` |
| `backend.image.tag`               | Backend image tag. Use `1.9.0-native` for optimized builds (modern CPUs). Use `1.9.0-native-compat` for older CPUs (x86-64-v2) or Raspberry Pi 3/4. See CPU Compatibility section below for troubleshooting. | `1.9.0-native`            |
| `backend.replicaCount`            | Number of backend replicas                                                                                                                                                                                     | `1`                       |
| `backend.service.type`            | Backend service type                                                                                                                                                                                           | `ClusterIP`               |
| `backend.service.port`            | Backend service port                                                                                                                                                                                           | `8080`                    |
| `backend.resources.limits.memory` | Backend memory limit                                                                                                                                                                                           | `1Gi`                     |
| `backend.resources.limits.cpu`    | Backend CPU limit                                                                                                                                                                                              | `1000m`                   |

### Frontend Parameters

| Parameter                   | Description                 | Default              |
|-----------------------------|-----------------------------|----------------------|
| `frontend.image.repository` | Frontend image repository   | `tess1o/geopulse-ui` |
| `frontend.image.tag`        | Frontend image tag          | `1.9.0`              |
| `frontend.replicaCount`     | Number of frontend replicas | `1`                  |
| `frontend.service.type`     | Frontend service type       | `ClusterIP`          |
| `frontend.service.port`     | Frontend service port       | `80`                 |

### PostgreSQL Parameters

| Parameter                           | Description               | Default           |
|-------------------------------------|---------------------------|-------------------|
| `postgres.enabled`                  | Deploy PostgreSQL         | `true`            |
| `postgres.image.repository`         | PostgreSQL image          | `postgis/postgis` |
| `postgres.image.tag`                | PostgreSQL image tag      | `17-3.5`          |
| `postgres.persistence.enabled`      | Enable persistence        | `true`            |
| `postgres.persistence.size`         | PVC size                  | `10Gi`            |
| `postgres.persistence.storageClass` | Storage class             | `""`              |
| `postgres.database`                 | Database name             | `geopulse`        |
| `postgres.username`                 | Database username         | `geopulse-user`   |
| `postgres.config.sharedBuffers`     | PostgreSQL shared_buffers | `256MB`           |

### MQTT (Mosquitto) Parameters

| Parameter                       | Description         | Default                     |
|---------------------------------|---------------------|-----------------------------|
| `mosquitto.enabled`             | Deploy MQTT broker  | `false`                     |
| `mosquitto.image.repository`    | Mosquitto image     | `iegomez/mosquitto-go-auth` |
| `mosquitto.image.tag`           | Mosquitto image tag | `3.0.0-mosquitto_2.0.18`    |
| `mosquitto.username`            | MQTT admin username | `geopulse_mqtt_admin`       |
| `mosquitto.persistence.enabled` | Enable persistence  | `true`                      |

### Ingress Parameters

| Parameter                | Description        | Default                |
|--------------------------|--------------------|------------------------|
| `ingress.enabled`        | Enable ingress     | `false`                |
| `ingress.className`      | Ingress class name | `nginx`                |
| `ingress.hostname`       | Hostname           | `geopulse.example.com` |
| `ingress.tls.enabled`    | Enable TLS         | `false`                |
| `ingress.tls.secretName` | TLS secret name    | `geopulse-tls`         |

---

## Upgrading

```bash
# Upgrade to a new version
helm upgrade geopulse ./helm/geopulse

# Upgrade with new values
helm upgrade geopulse ./helm/geopulse -f my-values.yaml

# See what will change
helm diff upgrade geopulse ./helm/geopulse -f my-values.yaml
```

---

## Uninstalling

```bash
helm uninstall geopulse
```

**Note**: This will not delete PersistentVolumeClaims. To delete them:

```bash
kubectl delete pvc -l app.kubernetes.io/instance=geopulse
```

---

## Accessing GeoPulse

### With Ingress

Access via the configured hostname:

```
https://geopulse.example.com
```

### Without Ingress (Port Forward)

```bash
kubectl port-forward svc/geopulse-frontend 5555:80
```

Then visit: http://localhost:5555

### With LoadBalancer

```bash
kubectl get svc geopulse-frontend
# Note the EXTERNAL-IP and access via http://EXTERNAL-IP
```

---

## Persistence

GeoPulse uses PersistentVolumeClaims for:

1. **PostgreSQL data** (`10Gi` by default)
2. **JWT keys** (`10Mi`)
3. **MQTT data** (if enabled, `1Gi` for data, logs, config)

Configure storage classes and sizes in `values.yaml`.

---

## Security

- Passwords are auto-generated if not provided
- JWT keys are generated during installation
- Secrets are stored in Kubernetes Secret objects
- Use existing secrets with: `secrets.useExistingSecret=true`

### Managing Secrets

#### Best Practices

1. **Never commit secrets to Git** - Use `.gitignore` for values files with secrets
2. **Use Kubernetes Secrets** - Store sensitive data in Secret objects, not ConfigMaps
3. **Consider External Secret Managers** - For production, use AWS Secrets Manager, HashiCorp Vault, or similar
4. **Rotate secrets regularly** - Update secrets periodically and restart pods

#### Using Sealed Secrets

For GitOps workflows, consider using [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets):

```bash
# Install Sealed Secrets controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/controller.yaml

# Create a sealed secret
echo -n 'your-secret-value' | kubectl create secret generic mysecret --dry-run=client --from-file=api-key=/dev/stdin -o yaml | \
  kubeseal -o yaml > sealed-secret.yaml

# Commit sealed-secret.yaml to Git safely
```

---

## Monitoring

### Check Pod Status

```bash
kubectl get pods -l app.kubernetes.io/instance=geopulse
```

### View Logs

```bash
# Backend
kubectl logs -l app.kubernetes.io/component=backend -f

# Frontend
kubectl logs -l app.kubernetes.io/component=frontend -f

# PostgreSQL
kubectl logs -l app.kubernetes.io/component=database -f
```

### Run Tests

```bash
helm test geopulse
```

---

## Troubleshooting

### CPU Compatibility Issues

If your backend pods are crashing immediately with errors about missing CPU features, you have an older CPU that needs the compatible image.

**Check pod logs:**
```bash
kubectl logs -l app.kubernetes.io/component=backend
```

**Look for errors like:**
- `[AVX2, BMI1, BMI2, FMA, F16C, LZCNT]` (AMD64 CPUs)
- `[FP, ASIMD, CRC32, LSE]` (ARM64/Raspberry Pi)

**Solution:** Override the image tag in your values file:

```yaml
# values-compat.yaml
backend:
  image:
    tag: "1.9.0-native-compat"  # Use compatible image for old CPUs
```

Then upgrade your deployment:
```bash
helm upgrade geopulse ./helm/geopulse -f values-compat.yaml
```

**Who needs the compatible image?**
- **Old x86 CPUs**: Intel pre-Haswell (before 2013), AMD pre-Excavator (before 2015)
  - Examples: Intel Core i5-3470T (Ivy Bridge), Intel Xeon E5-2670 (Sandy Bridge)
- **Raspberry Pi 3/4**: ARM Cortex-A53/A72 processors

**Check node CPU features:**
```bash
# For x86/AMD64 nodes:
kubectl debug node/YOUR-NODE-NAME -it --image=ubuntu -- lscpu | grep -i flags
# Look for: avx2, bmi1, bmi2, fma, f16c

# For ARM64 nodes:
kubectl debug node/YOUR-NODE-NAME -it --image=ubuntu -- cat /proc/cpuinfo | grep Features
# Look for: asimd, crc32, atomics
```

### Pods Not Starting

Check events:

```bash
kubectl get events --sort-by='.lastTimestamp'
```

### Database Connection Issues

Verify PostgreSQL is running:

```bash
kubectl get pods -l app.kubernetes.io/component=database
kubectl logs -l app.kubernetes.io/component=database
```

### JWT Key Issues

Check keygen job:

```bash
kubectl get jobs
kubectl logs job/geopulse-keygen
```

### MQTT Not Working

Ensure MQTT is enabled and check logs:

```bash
kubectl logs -l app.kubernetes.io/component=mqtt
```

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

---

## Related Documentation

For feature-specific configuration details, see:

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

---

## Support

- Documentation: https://github.com/tess1o/geopulse/tree/main/docs
- Issues: https://github.com/tess1o/geopulse/issues

## License

AGPL-3.0 with Non-Commercial Use Restriction
