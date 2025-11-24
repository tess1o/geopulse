# GeoPulse Helm Chart

A self-hosted location tracking and analysis platform with PostGIS and optional MQTT support.

## Quick Start

```bash
# Add the Helm repository
helm repo add geopulse https://tess1o.github.io/geopulse/charts
helm repo update

# Install GeoPulse
helm install geopulse geopulse/geopulse

# Or install with custom values
helm install geopulse geopulse/geopulse -f custom-values.yaml
```

## Documentation

For comprehensive documentation, please visit:

- **[Kubernetes Deployment Guide](https://tess1o.github.io/geopulse/docs/getting-started/deployment/kubernetes-helm)** - Step-by-step deployment instructions
- **[Helm Chart Documentation](https://tess1o.github.io/geopulse/docs/getting-started/deployment/helm)** - Chart overview and examples
- **[Helm Configuration Guide](https://tess1o.github.io/geopulse/docs/getting-started/deployment/helm-configuration-guide)** - Complete configuration reference

## Essential Configuration

### Basic Setup

```yaml
config:
  uiUrl: "https://geopulse.example.com"
  authSecureCookies: true
  admin:
    email: "admin@example.com"

ingress:
  enabled: true
  hostname: geopulse.example.com
  tls:
    enabled: true
```

### Most Common Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `config.uiUrl` | Frontend URL for CORS | `http://localhost:5555` |
| `config.admin.email` | Admin user email | `""` |
| `ingress.enabled` | Enable ingress | `false` |
| `postgres.persistence.enabled` | Enable PostgreSQL persistence | `true` |
| `postgres.persistence.size` | PostgreSQL storage size | `10Gi` |

For the complete list of parameters, see the [values.yaml](values.yaml) file or visit the [Helm Configuration Guide](https://tess1o.github.io/geopulse/docs/getting-started/deployment/helm-configuration-guide).

## Features

- Full stack deployment (Backend, Frontend, PostgreSQL with PostGIS)
- Optional MQTT broker (Mosquitto)
- Production-ready with health checks and resource limits
- Flexible configuration via values.yaml
- Automatic JWT key generation
- Ingress support with TLS
- High availability ready

## Requirements

- Kubernetes 1.19+
- Helm 3.2.0+
- PV provisioner for persistence
- (Optional) Ingress controller
- (Optional) cert-manager for TLS

## Support

- **Documentation**: https://tess1o.github.io/geopulse/docs
- **Issues**: https://github.com/tess1o/geopulse/issues
- **Source Code**: https://github.com/tess1o/geopulse

## License

BSL 1.1
