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

## Sessions

Every sign-in is a session stored in the database. The browser holds only an opaque, HTTP-only `gp_session`
cookie; API sessions (`POST /api/v1/auth/api-sessions`) and the mobile app get an access token and a refresh token
instead. Because the server checks the session on every request, ending a session takes effect immediately:

- **Sign out** ends the current session.
- **Changing your password** signs out all your other sessions.
- **Disabling a user** or **resetting a user's password** in the Admin Panel signs that user out everywhere.
- **OIDC single logout**: signing out at the identity provider can sign the user out of GeoPulse too, see
  [OIDC Single Logout](/docs/system-administration/configuration/oidc-sso#single-logout).

| Environment Variable                     | Default | Description                                                                  |
|------------------------------------------|---------|------------------------------------------------------------------------------|
| `GEOPULSE_AUTH_SESSION_IDLE_TIMEOUT`     | `7d`    | A session ends after this long without use                                   |
| `GEOPULSE_AUTH_SESSION_ABSOLUTE_TIMEOUT` | `90d`   | A session ends at the latest this long after sign-in                         |
| `GEOPULSE_AUTH_ACCESS_TOKEN_LIFESPAN`    | `30m`   | Access token lifetime for API sessions and the mobile app (renewed by refresh) |

API refresh tokens work once: every refresh returns a new pair. If an old refresh token is used again, GeoPulse
treats it as stolen and ends the session.

## Kubernetes / Helm Configuration

Configure in `values.yaml`:

```yaml
config:
  cookieDomain: ""  # Keep empty for standard nginx deployments
  authSecureCookies: true  # Set to true for HTTPS/production
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-deployment#core-configuration).

## Login Controls

GeoPulse provides granular control over login methods to restrict access during maintenance or enforce specific authentication patterns.

### Environment Variables

#### Global Login Control
- **Variable:** `GEOPULSE_AUTH_LOGIN_ENABLED`
- **Default:** `true`
- **Description:** Master switch for all login methods

#### Password Login Control
- **Variable:** `GEOPULSE_AUTH_PASSWORD_LOGIN_ENABLED`
- **Default:** `true`
- **Description:** Controls email/password login

#### OIDC Login Control
- **Variable:** `GEOPULSE_AUTH_OIDC_LOGIN_ENABLED`
- **Default:** `true`
- **Description:** Controls OIDC provider login

#### OIDC Auto-Launch
- **Variable:** `GEOPULSE_AUTH_OIDC_AUTO_LAUNCH_ENABLED`
- **Default:** `false`
- **Description:** When password login is disabled and exactly one OIDC provider is enabled, `/login` goes straight to that provider instead of showing a single "Continue with" button. After a sign-out the login page waits for a click (so the provider does not sign the user straight back in) until the user presses **Sign in** again. The login page is also shown after a failed sign-in and whenever it is opened as `/login?autoLaunch=0`, which is how administrators reach password login with the admin bypass

#### Admin Login Bypass Control
- **Variable:** `GEOPULSE_AUTH_ADMIN_LOGIN_BYPASS_ENABLED`
- **Default:** `true`
- **Description:** Controls whether admins can bypass login restrictions

#### Guest Root Redirect Control
- **Variable:** `GEOPULSE_AUTH_GUEST_ROOT_REDIRECT_TO_LOGIN_ENABLED`
- **Default:** `false`
- **Description:** When `true`, signed-out users opening `/` are redirected to `/login` instead of seeing the Home page

### Hierarchical Structure

Login controls use a hierarchical pattern:
- Password login requires: `GEOPULSE_AUTH_LOGIN_ENABLED=true` **AND** `GEOPULSE_AUTH_PASSWORD_LOGIN_ENABLED=true`
- OIDC login requires: `GEOPULSE_AUTH_LOGIN_ENABLED=true` **AND** `GEOPULSE_AUTH_OIDC_LOGIN_ENABLED=true`

### Admin Bypass

**Configurable:** Admin bypass behavior is controlled by `GEOPULSE_AUTH_ADMIN_LOGIN_BYPASS_ENABLED`.

By default (`true`), admin users bypass login restrictions to prevent system lockout. When set to `false`, admins are subject to the same restrictions as regular users.

**WARNING:** Disabling admin bypass can result in complete system lockout if login is disabled globally. Only use this setting in high-security environments where you have alternative access methods.

### Admin Panel Configuration

Login settings can also be configured via the Admin Panel:
1. Navigate to **Settings** → **Authentication**
2. Toggle login controls and guest root redirect behavior as needed
3. Changes take effect immediately

### Behavior

- **Existing sessions:** Remain valid when login is disabled. Only NEW logins are blocked.
- **Session renewal:** Existing sessions keep renewing while they are used, up to the absolute session timeout.
- **Frontend:** Shows informative messages explaining why login is disabled.
- **Guest root navigation:** By default, signed-out users see Home on `/`. If guest root redirect is enabled, they are sent to `/login`.

### Use Cases

**Maintenance Mode:**
```bash
GEOPULSE_AUTH_LOGIN_ENABLED=false
```
Blocks all new logins while allowing existing users to continue working. Admins can still log in.

**Force OIDC Authentication:**
```bash
GEOPULSE_AUTH_PASSWORD_LOGIN_ENABLED=false
GEOPULSE_AUTH_OIDC_LOGIN_ENABLED=true
```
Requires all users to authenticate via OIDC providers. With a single provider, add
`GEOPULSE_AUTH_OIDC_AUTO_LAUNCH_ENABLED=true` to skip the login page and go straight to it.

**Disable External Authentication:**
```bash
GEOPULSE_AUTH_OIDC_LOGIN_ENABLED=false
```
Only allows password-based login for internal users.

**Login-First Guest Entry:**
```bash
GEOPULSE_AUTH_GUEST_ROOT_REDIRECT_TO_LOGIN_ENABLED=true
```
Signed-out users opening `/` are redirected directly to `/login`.
