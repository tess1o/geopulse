# Authentication Configuration

## Authentication Cookies

GeoPulse uses HTTP-only cookies for secure authentication. Understanding how cookie domains work with GeoPulse's
architecture is important for proper deployment.

**GeoPulse Architecture Overview:**

- nginx serves the frontend and proxies `/api/*` requests to the backend
- Browser sees all requests as same-origin (e.g., `https://geopulse.yourdomain.com`)
- Frontend assets: `https://geopulse.yourdomain.com/`
- API requests: `https://geopulse.yourdomain.com/api/*`
- nginx internally forwards API requests to `http://geopulse-backend:8080`

**Cookie Domain Configuration:**

| Environment Variable           | Default   | Description                                                |
|--------------------------------|-----------|------------------------------------------------------------|
| `GEOPULSE_COOKIE_DOMAIN`       | _(empty)_ | Cookie domain for authentication. **Keep empty for nginx** |
| `GEOPULSE_AUTH_SECURE_COOKIES` | `false`   | Enable secure cookies (requires HTTPS)                     |

**When to use GEOPULSE_COOKIE_DOMAIN:**

**Standard Deployments (99% of cases) - Keep Empty:**

```bash
# ✅ Recommended for all standard deployments
GEOPULSE_COOKIE_DOMAIN=
```

This works for:

- Localhost: `http://localhost:5555`
- Homelab: `http://192.168.1.100:5555`
- Production: `https://geopulse.yourdomain.com`
- Any deployment using nginx proxy (standard GeoPulse setup)

**Why keep it empty?**

- Nginx creates same-origin context - browser automatically handles cookies
- More secure - cookies won't leak to other subdomains
- Simpler configuration with fewer issues

**Alternative Deployments (rare) - Set Cookie Domain:**

```bash
# ❌ Only for non-standard deployments WITHOUT nginx proxy
# Example: Frontend at app.yourdomain.com, Backend at api.yourdomain.com
GEOPULSE_COOKIE_DOMAIN=.yourdomain.com
```

⚠️ **Warning**: This is NOT a standard GeoPulse deployment. Requires:

- Removing nginx proxy
- Updating CORS configuration
- Modifying frontend to call backend directly
- Security implications (cookies shared across all subdomains)

**Cookie Security:**

For production deployments with HTTPS:

```bash
GEOPULSE_AUTH_SECURE_COOKIES=true
```

This ensures authentication cookies are only transmitted over secure HTTPS connections.

For local/development deployments with HTTP:

```bash
GEOPULSE_AUTH_SECURE_COOKIES=false
```

## Kubernetes / Helm Configuration

Configure in `values.yaml`:

```yaml
config:
  cookieDomain: ""  # Keep empty for standard nginx deployments
  authSecureCookies: true  # Set to true for HTTPS/production
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-deployment#core-configuration).