# GeoPulse Helm Chart - Quick Start Guide

## Prerequisites

- Kubernetes cluster (1.19+)
- Helm 3.2.0+
- `kubectl` configured

## Installation Options

### 1. Standard Installation (with persistence)

**Default configuration with persistent storage:**

```bash
helm install geopulse ./helm/geopulse
```

This includes:
- PostgreSQL with 10Gi persistent storage
- JWT keys with persistent storage
- Backend API
- Frontend UI

**Access the application:**
```bash
kubectl port-forward svc/geopulse-frontend 5555:80
```
Then visit: http://localhost:5555

---

### 2. Installation with MQTT Broker

**Enable MQTT for OwnTracks/Overland integration:**

```bash
helm install geopulse ./helm/geopulse \
  --set mosquitto.enabled=true
```

**Or using a values file:**

Create `mqtt-values.yaml`:
```yaml
mosquitto:
  enabled: true

  # Optional: Customize MQTT settings
  username: mqtt_admin
  service:
    type: LoadBalancer  # Expose MQTT externally

  persistence:
    enabled: true
    data:
      size: 5Gi
    logs:
      size: 2Gi
```

Install:
```bash
helm install geopulse ./helm/geopulse -f mqtt-values.yaml
```

**MQTT Broker Access:**
- **Internal**: `geopulse-mosquitto:1883` (from within cluster)
- **External**: Use LoadBalancer or expose via port-forward

Get MQTT password:
```bash
kubectl get secret geopulse-secrets -o jsonpath='{.data.GEOPULSE_MQTT_PASSWORD}' | base64 -d
```

---

### 3. Production Installation

**With ingress, TLS, and high availability:**

Create `production-values.yaml`:
```yaml
# Ingress configuration
ingress:
  enabled: true
  hostname: geopulse.yourdomain.com
  className: nginx
  tls:
    enabled: true
    secretName: geopulse-tls
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"

# Application config
config:
  uiUrl: "https://geopulse.yourdomain.com"
  authSecureCookies: true
  cookieDomain: ".yourdomain.com"

# High availability
backend:
  replicaCount: 2
  resources:
    limits:
      memory: 2Gi
      cpu: 2000m
    requests:
      memory: 1Gi
      cpu: 500m

frontend:
  replicaCount: 2

# Database
postgres:
  persistence:
    size: 50Gi
    storageClass: "fast-ssd"
  resources:
    limits:
      memory: 4Gi
      cpu: 2000m

# Optional: Enable MQTT
mosquitto:
  enabled: true
  service:
    type: LoadBalancer
```

Install:
```bash
helm install geopulse ./helm/geopulse -f production-values.yaml
```

---

### 4. Development/Testing (No Persistence)

**Quick ephemeral installation:**

```bash
helm install geopulse ./helm/geopulse \
  --set postgres.persistence.enabled=false \
  --set keygen.persistence.enabled=false \
  --set mosquitto.persistence.enabled=false
```

⚠️ **Warning**: All data will be lost when pods restart!

---

## Common Commands

### Check Deployment Status

```bash
# View all pods
kubectl get pods -l app.kubernetes.io/instance=geopulse

# Wait for all pods to be ready
kubectl wait --for=condition=ready pod \
  -l app.kubernetes.io/instance=geopulse \
  --timeout=300s

# Check specific component logs
kubectl logs -l app.kubernetes.io/component=backend -f
kubectl logs -l app.kubernetes.io/component=frontend -f
kubectl logs -l app.kubernetes.io/component=database -f
kubectl logs -l app.kubernetes.io/component=mqtt -f  # If MQTT enabled
```

### Get Credentials

```bash
# PostgreSQL password
kubectl get secret geopulse-secrets \
  -o jsonpath='{.data.GEOPULSE_POSTGRES_PASSWORD}' | base64 -d

# MQTT password (if enabled)
kubectl get secret geopulse-secrets \
  -o jsonpath='{.data.GEOPULSE_MQTT_PASSWORD}' | base64 -d
```

### Access Application

```bash
# Port forward to frontend
kubectl port-forward svc/geopulse-frontend 5555:80

# Port forward to backend API
kubectl port-forward svc/geopulse-backend 8080:8080

# Port forward to MQTT (if enabled)
kubectl port-forward svc/geopulse-mosquitto 1883:1883
```

---

## Upgrade

```bash
# Upgrade with new values
helm upgrade geopulse ./helm/geopulse -f my-values.yaml

# Enable MQTT on existing installation
helm upgrade geopulse ./helm/geopulse \
  --set mosquitto.enabled=true
```

---

## Uninstall

```bash
# Uninstall release
helm uninstall geopulse

# Clean up persistent volumes (optional - deletes data!)
kubectl delete pvc -l app.kubernetes.io/instance=geopulse
```

---

## Storage Configuration

### Default Storage

Uses cluster's default StorageClass. Check available classes:
```bash
kubectl get storageclass
```

### Custom Storage Class

```bash
helm install geopulse ./helm/geopulse \
  --set postgres.persistence.storageClass="fast-ssd" \
  --set keygen.persistence.storageClass="standard"
```

### Storage Sizes

```yaml
postgres:
  persistence:
    size: 50Gi  # Database size

keygen:
  persistence:
    size: 10Mi  # JWT keys (small)

mosquitto:
  persistence:
    data:
      size: 5Gi   # MQTT data
    logs:
      size: 2Gi   # MQTT logs
    config:
      size: 100Mi # MQTT config
```

---

## Troubleshooting

### Pods Not Starting

```bash
# Check events
kubectl get events --sort-by='.lastTimestamp' | tail -20

# Describe pod
kubectl describe pod <pod-name>

# Check logs
kubectl logs <pod-name>
```

### PostgreSQL Issues

```bash
# Check PostgreSQL logs
kubectl logs geopulse-postgres-0

# Connect to PostgreSQL
kubectl exec -it geopulse-postgres-0 -- \
  psql -U geopulse-user -d geopulse
```

### MQTT Issues

```bash
# Check MQTT logs
kubectl logs -l app.kubernetes.io/component=mqtt

# Test MQTT connection (from within cluster)
kubectl run mqtt-test --rm -it --image=eclipse-mosquitto:latest -- \
  mosquitto_sub -h geopulse-mosquitto -p 1883 \
  -u mqtt_admin -P <password> -t '#'
```

### Reset Installation

If you need to completely reset:

```bash
# Uninstall
helm uninstall geopulse

# Delete PVCs (THIS DELETES ALL DATA!)
kubectl delete pvc -l app.kubernetes.io/instance=geopulse

# Delete secrets
kubectl delete secret geopulse-secrets

# Reinstall
helm install geopulse ./helm/geopulse
```

---

## Examples

### Minimal Local Testing

```bash
helm install geopulse ./helm/geopulse \
  --set postgres.persistence.enabled=false \
  --set keygen.persistence.enabled=false
```

### With MQTT and Custom Resources

```bash
helm install geopulse ./helm/geopulse \
  --set mosquitto.enabled=true \
  --set backend.resources.limits.memory=2Gi \
  --set postgres.persistence.size=20Gi
```

### Production with All Features

```bash
helm install geopulse ./helm/geopulse \
  --set ingress.enabled=true \
  --set ingress.hostname=geopulse.example.com \
  --set ingress.tls.enabled=true \
  --set mosquitto.enabled=true \
  --set backend.replicaCount=3 \
  --set frontend.replicaCount=2 \
  --set postgres.persistence.size=100Gi
```

---

## Next Steps

1. ✅ Install GeoPulse
2. ✅ Access the web interface
3. ✅ Create your first user account
4. ✅ Configure GPS tracking apps:
   - OwnTracks (HTTP or MQTT)
   - Overland
   - Dawarich
   - Home Assistant
5. ✅ Start tracking!

For detailed configuration options, see:
- [values.yaml](./geopulse/values.yaml) - All configuration options
- [README.md](./geopulse/README.md) - Complete documentation
- [KUBERNETES_DEPLOYMENT.md](../docs/KUBERNETES_DEPLOYMENT.md) - Advanced deployment guide