# Kubernetes Deployment Guide

This guide explains how to deploy GeoPulse to a Kubernetes cluster using Helm.

## Prerequisites

- Kubernetes cluster (1.19+)
- Helm 3.2.0+
- `kubectl` configured to access your cluster
- Persistent volume provisioner (for data storage)
- (Optional) Ingress controller (nginx, traefik, etc.)
- (Optional) cert-manager (for automatic TLS certificates)

## Quick Start

### 1. Install with Default Settings

The simplest way to get started:

```bash
helm install geopulse ./helm/geopulse
```

This will deploy:
- Backend API server
- Frontend web UI
- PostgreSQL with PostGIS
- Persistent storage for database and JWT keys

### 2. Access GeoPulse

After installation, follow the instructions from:

```bash
helm status geopulse
```

For quick testing, use port forwarding:

```bash
kubectl port-forward svc/geopulse-frontend 5555:80
```

Then visit: http://localhost:5555

## Deployment Scenarios

### Minimal Setup (Development/Testing)

For development or testing without persistence:

```bash
helm install geopulse ./helm/geopulse \
  --set postgres.persistence.enabled=false \
  --set keygen.persistence.enabled=false
```

### Production Deployment with Ingress

```bash
helm install geopulse ./helm/geopulse \
  --set ingress.enabled=true \
  --set ingress.hostname=geopulse.yourdomain.com \
  --set ingress.tls.enabled=true \
  --set config.uiUrl=https://geopulse.yourdomain.com \
  --set config.authSecureCookies=true \
  --set config.cookieDomain=.yourdomain.com \
  --set backend.replicaCount=2 \
  --set frontend.replicaCount=2 \
  --set postgres.persistence.size=50Gi
```

### With MQTT Support

Enable the optional MQTT broker for OwnTracks integration:

```bash
helm install geopulse ./helm/geopulse \
  --set mosquitto.enabled=true \
  --set mosquitto.service.type=LoadBalancer
```

### Using Custom Values File

Create a `my-values.yaml`:

```yaml
# Production configuration
ingress:
  enabled: true
  hostname: geopulse.example.com
  tls:
    enabled: true

config:
  uiUrl: "https://geopulse.example.com"
  authSecureCookies: true
  cookieDomain: ".example.com"

backend:
  replicaCount: 2
  resources:
    limits:
      memory: 2Gi
      cpu: 2000m

frontend:
  replicaCount: 2

postgres:
  persistence:
    size: 50Gi
    storageClass: "fast-ssd"

mosquitto:
  enabled: true
```

Install with:

```bash
helm install geopulse ./helm/geopulse -f my-values.yaml
```

## External PostgreSQL

If you already have a PostgreSQL database with PostGIS:

```bash
helm install geopulse ./helm/geopulse \
  --set postgres.enabled=false \
  --set externalPostgres.host=postgres.example.com \
  --set externalPostgres.port=5432 \
  --set externalPostgres.database=geopulse \
  --set externalPostgres.username=geopulse-user \
  --set externalPostgres.password=your-password
```

## Storage Configuration

### Configure Storage Classes

```bash
helm install geopulse ./helm/geopulse \
  --set postgres.persistence.storageClass=fast-ssd \
  --set keygen.persistence.storageClass=standard \
  --set mosquitto.persistence.storageClass=standard
```

### Increase Storage Sizes

```bash
helm install geopulse ./helm/geopulse \
  --set postgres.persistence.size=100Gi \
  --set mosquitto.persistence.data.size=5Gi \
  --set mosquitto.persistence.logs.size=2Gi
```

## OIDC Authentication

Configure SSO providers:

```yaml
# oidc-values.yaml
config:
  oidc:
    enabled: true
    google:
      enabled: true
      clientId: "your-client-id"
      clientSecret: "your-client-secret"
```

```bash
helm install geopulse ./helm/geopulse -f oidc-values.yaml
```

## Resource Management

### Set Resource Limits

```bash
helm install geopulse ./helm/geopulse \
  --set backend.resources.limits.memory=2Gi \
  --set backend.resources.limits.cpu=2000m \
  --set postgres.resources.limits.memory=4Gi \
  --set postgres.resources.limits.cpu=2000m
```

### Horizontal Pod Autoscaling

```yaml
# hpa-values.yaml
autoscaling:
  enabled: true
  backend:
    minReplicas: 2
    maxReplicas: 10
    targetCPUUtilizationPercentage: 70
  frontend:
    minReplicas: 2
    maxReplicas: 5
    targetCPUUtilizationPercentage: 80
```

## Upgrading

Upgrade your deployment:

```bash
helm upgrade geopulse ./helm/geopulse
```

With new values:

```bash
helm upgrade geopulse ./helm/geopulse -f my-values.yaml
```

## Uninstalling

Remove GeoPulse:

```bash
helm uninstall geopulse
```

Clean up persistent volumes:

```bash
kubectl delete pvc -l app.kubernetes.io/instance=geopulse
```

## Monitoring and Troubleshooting

### Check Status

```bash
# Overall status
helm status geopulse

# Pod status
kubectl get pods -l app.kubernetes.io/instance=geopulse

# Detailed pod info
kubectl describe pod -l app.kubernetes.io/instance=geopulse
```

### View Logs

```bash
# Backend logs
kubectl logs -l app.kubernetes.io/component=backend -f

# Frontend logs
kubectl logs -l app.kubernetes.io/component=frontend -f

# Database logs
kubectl logs -l app.kubernetes.io/component=database -f

# MQTT logs (if enabled)
kubectl logs -l app.kubernetes.io/component=mqtt -f
```

### Access Database

```bash
# Get database password
DB_PASSWORD=$(kubectl get secret geopulse-secrets -o jsonpath='{.data.GEOPULSE_POSTGRES_PASSWORD}' | base64 -d)

# Port forward to database
kubectl port-forward svc/geopulse-postgres 5432:5432

# Connect with psql
psql -h localhost -U geopulse-user -d geopulse
```

### Run Tests

Verify the deployment:

```bash
helm test geopulse
```

## Common Issues

### Pods Not Starting

1. Check PVC status:
   ```bash
   kubectl get pvc
   ```

2. Check pod events:
   ```bash
   kubectl get events --sort-by='.lastTimestamp'
   ```

3. Describe pod for details:
   ```bash
   kubectl describe pod <pod-name>
   ```

### Database Connection Failed

1. Verify PostgreSQL is running:
   ```bash
   kubectl get pods -l app.kubernetes.io/component=database
   ```

2. Check PostgreSQL logs:
   ```bash
   kubectl logs -l app.kubernetes.io/component=database
   ```

3. Test database connection:
   ```bash
   kubectl run -it --rm debug --image=postgres:17 --restart=Never -- \
     psql -h geopulse-postgres -U geopulse-user -d geopulse
   ```

### JWT Key Issues

Check keygen job logs:
```bash
kubectl get jobs
kubectl logs job/geopulse-keygen
```

### Ingress Not Working

1. Verify ingress controller is installed:
   ```bash
   kubectl get pods -n ingress-nginx
   ```

2. Check ingress status:
   ```bash
   kubectl get ingress
   kubectl describe ingress geopulse
   ```

3. Verify DNS points to ingress controller's external IP:
   ```bash
   kubectl get svc -n ingress-nginx
   ```

## Advanced Configuration

### Custom JVM Options

```bash
helm install geopulse ./helm/geopulse \
  --set backend.javaOpts="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+UseG1GC"
```

### PostgreSQL Tuning

Adjust PostgreSQL performance parameters:

```yaml
postgres:
  config:
    sharedBuffers: "512MB"
    workMem: "16MB"
    maintenanceWorkMem: "128MB"
    effectiveCacheSize: "2GB"
    maxWalSize: "1GB"
```

### Network Policies

Enable network policies for security:

```bash
helm install geopulse ./helm/geopulse \
  --set networkPolicy.enabled=true
```

## Best Practices

1. **Use Persistent Storage**: Always enable persistence in production
2. **Set Resource Limits**: Define appropriate CPU and memory limits
3. **Enable TLS**: Use HTTPS with valid certificates
4. **Backup Database**: Regular backups of PostgreSQL data
5. **Monitor Resources**: Use monitoring tools (Prometheus, Grafana)
6. **Use Secrets**: Don't commit passwords to values.yaml
7. **Regular Updates**: Keep images and chart up to date
8. **Health Checks**: Ensure probes are properly configured

## Production Checklist

- [ ] Persistence enabled for all stateful components
- [ ] Ingress configured with valid hostname
- [ ] TLS certificates configured (cert-manager)
- [ ] Resource limits set appropriately
- [ ] Backup strategy in place
- [ ] Monitoring configured
- [ ] Security contexts applied
- [ ] Network policies enabled (if required)
- [ ] OIDC/SSO configured (if needed)
- [ ] Database tuning applied
- [ ] High availability (multiple replicas)
- [ ] Pod disruption budgets configured

## Support

For more information:
- [Helm Chart README](../helm/geopulse/README.md)
- [Main Documentation](./DEPLOYMENT_GUIDE.md)
- [GitHub Issues](https://github.com/tess1o/geopulse/issues)