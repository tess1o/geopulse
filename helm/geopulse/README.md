# GeoPulse Helm Chart

This Helm chart deploys GeoPulse, a self-hosted location tracking and analysis platform, to Kubernetes.

## Features

- **Full Stack Deployment**: Backend (Java/Quarkus in Native mode), Frontend (Vue.js), PostgreSQL with PostGIS
- **Optional MQTT Support**: Conditional Mosquitto MQTT broker deployment
- **Production Ready**: Health checks, resource limits, persistent storage
- **Flexible Configuration**: Extensive values.yaml for customization
- **Security**: Automatic JWT key generation, secrets management
- **Ingress Support**: Optional ingress with TLS
- **High Availability Ready**: Support for replicas and pod disruption budgets

## Prerequisites

- Kubernetes 1.19+
- Helm 3.2.0+
- PV provisioner support in the underlying infrastructure (for persistence)
- (Optional) Ingress controller for external access
- (Optional) cert-manager for TLS certificates

## Manual Installation (Advanced)

This section describes how to install the chart directly with Helm, bypassing the interactive scripts. This is intended for advanced users or automated workflows. For a simpler, interactive setup, please see the **[Kubernetes Deployment Guide](../../docs/KUBERNETES_DEPLOYMENT.md)**.

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

## Configuration

### Common Configuration Examples

#### Minimal Setup (Local Testing)

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

#### Production Setup with Ingress

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
```

```bash
helm install geopulse ./helm/geopulse -f production-values.yaml
```

#### Enable MQTT Broker

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

#### Use External PostgreSQL

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

#### Enable OIDC Authentication

```yaml
# oidc-values.yaml
config:
  oidc:
    enabled: true
    google:
      enabled: true
      clientId: "your-google-client-id"
      clientSecret: "your-google-client-secret"
    microsoft:
      enabled: true
      clientId: "your-microsoft-client-id"
      clientSecret: "your-microsoft-client-secret"
```

```bash
helm install geopulse ./helm/geopulse -f oidc-values.yaml
```

## Configuration Parameters

### Global Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `global.imagePullPolicy` | Image pull policy | `IfNotPresent` |
| `global.imagePullSecrets` | Image pull secrets | `[]` |

### Backend Parameters

| Parameter | Description | Default                   |
|-----------|-------------|---------------------------|
| `backend.image.repository` | Backend image repository | `tess1o/geopulse-backend` |
| `backend.image.tag` | Backend image tag | `1.4.2-native`            |
| `backend.replicaCount` | Number of backend replicas | `1`                       |
| `backend.service.type` | Backend service type | `ClusterIP`               |
| `backend.service.port` | Backend service port | `8080`                    |
| `backend.resources.limits.memory` | Backend memory limit | `1Gi`                     |
| `backend.resources.limits.cpu` | Backend CPU limit | `1000m`                   |

### Frontend Parameters

| Parameter | Description | Default              |
|-----------|-------------|----------------------|
| `frontend.image.repository` | Frontend image repository | `tess1o/geopulse-ui` |
| `frontend.image.tag` | Frontend image tag | `1.4.1`              |
| `frontend.replicaCount` | Number of frontend replicas | `1`                  |
| `frontend.service.type` | Frontend service type | `ClusterIP`          |
| `frontend.service.port` | Frontend service port | `80`                 |

### PostgreSQL Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `postgres.enabled` | Deploy PostgreSQL | `true` |
| `postgres.image.repository` | PostgreSQL image | `postgis/postgis` |
| `postgres.image.tag` | PostgreSQL image tag | `17-3.5` |
| `postgres.persistence.enabled` | Enable persistence | `true` |
| `postgres.persistence.size` | PVC size | `10Gi` |
| `postgres.persistence.storageClass` | Storage class | `""` |
| `postgres.database` | Database name | `geopulse` |
| `postgres.username` | Database username | `geopulse-user` |
| `postgres.config.sharedBuffers` | PostgreSQL shared_buffers | `256MB` |

### MQTT (Mosquitto) Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `mosquitto.enabled` | Deploy MQTT broker | `false` |
| `mosquitto.image.repository` | Mosquitto image | `iegomez/mosquitto-go-auth` |
| `mosquitto.image.tag` | Mosquitto image tag | `3.0.0-mosquitto_2.0.18` |
| `mosquitto.username` | MQTT admin username | `geopulse_mqtt_admin` |
| `mosquitto.persistence.enabled` | Enable persistence | `true` |

### Ingress Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `ingress.enabled` | Enable ingress | `false` |
| `ingress.className` | Ingress class name | `nginx` |
| `ingress.hostname` | Hostname | `geopulse.example.com` |
| `ingress.tls.enabled` | Enable TLS | `false` |
| `ingress.tls.secretName` | TLS secret name | `geopulse-tls` |

### Application Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `config.uiUrl` | Frontend URL (for CORS) | `http://localhost:5555` |
| `config.cookieDomain` | Cookie domain for authentication. **Keep empty for standard nginx-based deployments.** Only set for non-standard deployments without nginx using separate subdomains. See [DEPLOYMENT_GUIDE.md](../../docs/DEPLOYMENT_GUIDE.md#understanding-geopulse_cookie_domain) | `""` |
| `config.authSecureCookies` | Use secure cookies (HTTPS only) | `false` |
| `config.oidc.enabled` | Enable OIDC | `false` |

For a complete list of parameters, see [values.yaml](values.yaml).

## Upgrading

```bash
# Upgrade to a new version
helm upgrade geopulse ./helm/geopulse

# Upgrade with new values
helm upgrade geopulse ./helm/geopulse -f my-values.yaml
```

## Uninstalling

```bash
helm uninstall geopulse
```

**Note**: This will not delete PersistentVolumeClaims. To delete them:

```bash
kubectl delete pvc -l app.kubernetes.io/instance=geopulse
```

## Testing

Run the included Helm tests to verify the deployment:

```bash
helm test geopulse
```

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

## Persistence

GeoPulse uses PersistentVolumeClaims for:

1. **PostgreSQL data** (`10Gi` by default)
2. **JWT keys** (`10Mi`)
3. **MQTT data** (if enabled, `1Gi` for data, logs, config)

Configure storage classes and sizes in `values.yaml`.

## Security

- Passwords are auto-generated if not provided
- JWT keys are generated during installation
- Secrets are stored in Kubernetes Secret objects
- Use existing secrets with: `secrets.useExistingSecret=true`

## Monitoring

Check pod status:
```bash
kubectl get pods -l app.kubernetes.io/instance=geopulse
```

View logs:
```bash
# Backend
kubectl logs -l app.kubernetes.io/component=backend -f

# Frontend
kubectl logs -l app.kubernetes.io/component=frontend -f

# PostgreSQL
kubectl logs -l app.kubernetes.io/component=database -f
```

## Troubleshooting

### Pods not starting

Check events:
```bash
kubectl get events --sort-by='.lastTimestamp'
```

### Database connection issues

Verify PostgreSQL is running:
```bash
kubectl get pods -l app.kubernetes.io/component=database
kubectl logs -l app.kubernetes.io/component=database
```

### JWT key issues

Check keygen job:
```bash
kubectl get jobs
kubectl logs job/geopulse-keygen
```

### MQTT not working

Ensure MQTT is enabled and check logs:
```bash
kubectl logs -l app.kubernetes.io/component=mqtt
```

## Support

- Documentation: https://github.com/tess1o/geopulse/tree/main/docs
- Issues: https://github.com/tess1o/geopulse/issues

## License

AGPL-3.0 with Non-Commercial Use Restriction
