# GeoPulse Helm Chart Examples

This directory contains example configuration files for common deployment scenarios.

## Available Examples

### 1. **mqtt-enabled.yaml** - With MQTT Broker

Enable MQTT broker for OwnTracks, Overland, and other MQTT-based GPS apps.

```bash
helm install geopulse ./helm/geopulse -f helm/examples/mqtt-enabled.yaml
```

**Features:**
- MQTT broker (Mosquitto) enabled
- LoadBalancer service for external MQTT access
- Persistent storage for MQTT data
- Increased backend resources

**After installation:**
```bash
# Get MQTT password
kubectl get secret geopulse-secrets -o jsonpath='{.data.GEOPULSE_MQTT_PASSWORD}' | base64 -d

# Get MQTT external IP/port
kubectl get svc geopulse-mosquitto
```

---

### 2. **minimal-testing.yaml** - Quick Testing

Minimal configuration for development/testing without persistence.

```bash
helm install geopulse ./helm/geopulse -f helm/examples/minimal-testing.yaml
```

**Features:**
- No persistent storage (fast startup)
- Minimal resource usage
- Perfect for local testing

⚠️ **Warning:** All data is lost when pods restart!

---

### 3. **large-deployment.yaml** - Production Scale

Configuration for large deployments with high availability.

```bash
helm install geopulse ./helm/geopulse -f helm/examples/large-deployment.yaml
```

**Features:**
- 3 backend replicas
- 2 frontend replicas
- 100Gi PostgreSQL storage
- Production-grade database tuning
- High resource allocations
- Optional MQTT enabled

**Suitable for:** 50+ users, high data volumes

---

## Combining Examples

You can combine multiple values files:

```bash
helm install geopulse ./helm/geopulse \
  -f helm/examples/mqtt-enabled.yaml \
  -f helm/examples/large-deployment.yaml
```

Or override specific values:

```bash
helm install geopulse ./helm/geopulse \
  -f helm/examples/mqtt-enabled.yaml \
  --set postgres.persistence.size=200Gi \
  --set backend.replicaCount=5
```

## Quick Reference

### Access Application

```bash
# Port forward to frontend
kubectl port-forward svc/geopulse-frontend 5555:80

# Visit: http://localhost:5555
```

### Check Status

```bash
# View all pods
kubectl get pods -l app.kubernetes.io/instance=geopulse

# View logs
kubectl logs -l app.kubernetes.io/component=backend -f
```

### Get Credentials

```bash
# PostgreSQL password
kubectl get secret geopulse-secrets -o jsonpath='{.data.GEOPULSE_POSTGRES_PASSWORD}' | base64 -d

# MQTT password (if enabled)
kubectl get secret geopulse-secrets -o jsonpath='{.data.GEOPULSE_MQTT_PASSWORD}' | base64 -d
```

## Need More Help?

- [Quick Start Guide](../QUICK_START.md)
- [Complete Documentation](../geopulse/README.md)
- [All Configuration Options](../geopulse/values.yaml)