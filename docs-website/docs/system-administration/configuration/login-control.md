# Login Control Management

GeoPulse provides a flexible, hierarchical system to control user login. You can disable all login globally or control email/password and OIDC login independently. This is useful for maintenance periods, enforcing specific authentication methods, or restricting access.

## Enable/Disable Login

| Environment Variable                      | Default | Description                                                                                                                                                                                          |
|-------------------------------------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_AUTH_LOGIN_ENABLED`             | `true`  | **Master switch for all user logins.** If set to `false`, all login methods (email/password and OIDC) will be disabled for non-admin users, regardless of the specific settings below              |
| `GEOPULSE_AUTH_PASSWORD_LOGIN_ENABLED`    | `true`  | Enables or disables user login via email and password. This is a specific switch that is only effective if the master switch is enabled.                                                            |
| `GEOPULSE_AUTH_OIDC_LOGIN_ENABLED`        | `true`  | Enables or disables user login via OIDC providers. This is a specific switch that is only effective if the master switch is enabled                                                                 |

## How it Works

The login control system checks both the master switch and the specific login method switch:

- To disable **all** user logins (maintenance mode):
  ```bash
  GEOPULSE_AUTH_LOGIN_ENABLED=false
  ```

- To allow OIDC login but disable email/password login:
  ```bash
  GEOPULSE_AUTH_LOGIN_ENABLED=true
  GEOPULSE_AUTH_PASSWORD_LOGIN_ENABLED=false
  GEOPULSE_AUTH_OIDC_LOGIN_ENABLED=true
  ```

- To allow only password login (disable OIDC):
  ```bash
  GEOPULSE_AUTH_LOGIN_ENABLED=true
  GEOPULSE_AUTH_PASSWORD_LOGIN_ENABLED=true
  GEOPULSE_AUTH_OIDC_LOGIN_ENABLED=false
  ```

If the master `GEOPULSE_AUTH_LOGIN_ENABLED` is `false`, no new logins are allowed for regular users, regardless of the other settings. The UI will display an informative message explaining that login is disabled.

## Admin Bypass

**Important:** Admin users **always bypass login restrictions** to prevent system lockout.

### How Admin Bypass Works

1. **Frontend**: When login is disabled, users see a message with an "Administrator Access" button
2. **Click button**: Reveals the login form with a notice "Administrator access - login restrictions bypassed"
3. **Backend validation**:
   - Admin users (determined by `GEOPULSE_ADMIN_EMAIL` or ADMIN role) can log in successfully
   - Non-admin users receive a 403 Forbidden error even if they try to bypass the UI

This prevents scenarios where administrators accidentally lock themselves out of the system during maintenance or configuration changes.

## Behavior

### Existing Sessions
- **Remain valid** when login is disabled
- Only **NEW logins** are blocked
- Users with valid sessions can continue working
- Token refresh continues to work for existing sessions

### Frontend Display
When login is disabled, the login page shows:
- **All login disabled**: Warning message with "Administrator Access" button
- **Password only disabled**: Info message explaining OIDC is available, plus admin button
- **OIDC only disabled**: Password form is shown, OIDC buttons hidden

## Use Cases

### Maintenance Mode
Disable all new logins during system maintenance:
```bash
GEOPULSE_AUTH_LOGIN_ENABLED=false
```
**Result:** No new logins allowed, but existing sessions remain valid. Admins can still log in via the admin access button.

### Force OIDC Authentication Only
Require all users to use corporate SSO/OIDC:
```bash
GEOPULSE_AUTH_LOGIN_ENABLED=true
GEOPULSE_AUTH_PASSWORD_LOGIN_ENABLED=false
GEOPULSE_AUTH_OIDC_LOGIN_ENABLED=true
```
**Result:** Users must authenticate via OIDC providers (Google, Microsoft, etc.). Password login disabled.

### Disable External Authentication
Restrict to internal password authentication only:
```bash
GEOPULSE_AUTH_LOGIN_ENABLED=true
GEOPULSE_AUTH_PASSWORD_LOGIN_ENABLED=true
GEOPULSE_AUTH_OIDC_LOGIN_ENABLED=false
```
**Result:** Only password-based login available. Useful for air-gapped or internal-only deployments.

### Scheduled Maintenance Window
Disable login during backup/upgrade:
```bash
# Before maintenance
GEOPULSE_AUTH_LOGIN_ENABLED=false

# After maintenance
GEOPULSE_AUTH_LOGIN_ENABLED=true
```
**Result:** Prevents new users from starting work during the maintenance window. Existing users can finish their work.

## Admin Panel Configuration

Login settings can also be configured via the Admin Panel (requires admin privileges):

1. Navigate to **Settings** → **Authentication**
2. Find the "Login Control" section with three toggles:
   - **Login Enabled** (master switch)
   - **Password Login** (specific control)
   - **OIDC Login** (specific control)
3. Toggle settings as needed
4. Changes take effect **immediately** (no restart required)

Settings configured via Admin Panel override environment variables and are stored in the database.

## Kubernetes / Helm Configuration

Configure in `values.yaml`:

```yaml
config:
  auth:
    # Login Control
    loginEnabled: true              # Master switch
    passwordLoginEnabled: true      # Email/password login
    oidcLoginEnabled: true          # OIDC login
```

### Example: Maintenance Mode

```yaml
config:
  auth:
    loginEnabled: false  # Disable all login during maintenance
    passwordLoginEnabled: true
    oidcLoginEnabled: true
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-deployment#authentication--registration).

## Relationship with Registration Controls

Login controls are **independent** from [registration controls](/docs/system-administration/configuration/user-registration):

- **Registration controls** affect new user account creation
- **Login controls** affect existing user authentication

Example scenarios:

| Registration | Login | Result |
|--------------|-------|--------|
| Enabled | Enabled | Normal operation - users can register and login |
| Disabled | Enabled | Closed system - only existing users can login |
| Enabled | Disabled | Users can register but can't login immediately (unusual) |
| Disabled | Disabled | Complete lockdown - no registration or login (admins can still login) |

## Security Considerations

### Admin Role Protection
- Only users with the ADMIN role can bypass login restrictions
- Admin role is assigned via `GEOPULSE_ADMIN_EMAIL` environment variable
- Cannot be escalated through registration or normal user operations

### Token Refresh
- Existing sessions can be refreshed even when login is disabled
- This is intentional - allows graceful degradation
- To force logout, disable user accounts individually (Admin Panel → Users)

### Audit Trail
All login attempts (successful and failed) are logged for security auditing.

## Troubleshooting

### "Login is currently disabled" - I'm an admin!
**Solution:** Click the "Administrator Access" button to reveal the login form. The backend will validate your admin status.

### Admin can't login after disabling
**Possible causes:**
1. User doesn't have ADMIN role (check `GEOPULSE_ADMIN_EMAIL` matches exactly)
2. Database role is not set to ADMIN (check Admin Panel → Users)
3. Frontend shows admin button but backend rejects (check backend logs)

### Settings not taking effect
**Solutions:**
1. If using environment variables: Restart backend service
2. If using Admin Panel: Settings are immediate, no restart needed
3. Check database `system_settings` table - database values override environment variables

### Want to force logout all users
Login controls only prevent **NEW logins**. To force logout:
1. Admin Panel → Users → Disable specific users
2. Or change JWT private key (forces all sessions invalid)

## Related Documentation

- [User Registration Management](/docs/system-administration/configuration/user-registration) - Control new account creation
- [Authentication Configuration](/docs/system-administration/configuration/authentication) - Cookie and JWT settings
- [OIDC/SSO Configuration](/docs/system-administration/configuration/oidc-sso) - Configure external identity providers
- [Admin Panel Guide](/docs/system-administration/configuration/admin-panel) - Managing settings via UI
