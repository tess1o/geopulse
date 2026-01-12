# User Registration Management

GeoPulse provides a flexible, hierarchical system to control new user registration. You can disable all registration
globally or control email/password and OIDC registration independently.

## Enable/Disable Registration

| Environment Variable                          | Default | Description                                                                                                                                                                                              |
|-----------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_AUTH_REGISTRATION_ENABLED`          | `true`  | **Master switch for all new user registrations.** If set to `false`, all registration methods (email/password and OIDC) will be disabled, regardless of the specific settings below                      |
| `GEOPULSE_AUTH_PASSWORD_REGISTRATION_ENABLED` | `true`  | Enables or disables user registration via email and password. This is a specific switch that is only effective if the master switch is enabled.                                                          |
| `GEOPULSE_AUTH_OIDC_REGISTRATION_ENABLED`     | `true`  | Enables or disables new user registration via OIDC providers. Existing users can still log in and link their accounts. This is a specific switch that is only effective if the master  switch is enabled |

## Deprecated Property

| Environment Variable            | Replaced By                                   | Description                                                                                                                                                             |
|---------------------------------|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_AUTH_SIGN_UP_ENABLED` | `GEOPULSE_AUTH_PASSWORD_REGISTRATION_ENABLED` | **(Deprecated)** This property is now deprecated. It still works for backward compatibility but will be removed in a future release. If used, a warning will be logged. |

## How it Works

The registration system checks both the master switch and the specific registration method switch:

- To disable **all** new user registrations:
  ```bash
  GEOPULSE_AUTH_REGISTRATION_ENABLED=false
  ```
- To allow OIDC registration but disable email/password registration:
  ```bash
  GEOPULSE_AUTH_REGISTRATION_ENABLED=true
  GEOPULSE_AUTH_PASSWORD_REGISTRATION_ENABLED=false
  GEOPULSE_AUTH_OIDC_REGISTRATION_ENABLED=true
  ```

If the master `GEOPULSE_AUTH_REGISTRATION_ENABLED` is `false`, no new users can register, regardless of the other
settings. The UI will hide all registration buttons and links.

## Kubernetes / Helm Configuration

Configure in `values.yaml`:

```yaml
config:
  auth:
    registrationEnabled: false  # Disable all registration
    passwordRegistrationEnabled: true
    oidcRegistrationEnabled: true
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-deployment#authentication--registration).

## Relationship with Login Controls

Registration controls are **independent** from [login controls](/docs/system-administration/configuration/login-control):

- **Registration controls** (this page) affect new user account creation
- **Login controls** affect existing user authentication

Example scenarios:

| Registration | Login | Result |
|--------------|-------|--------|
| Enabled | Enabled | Normal operation - users can register and login |
| Disabled | Enabled | Closed system - only existing users can login |
| Enabled | Disabled | Users can register but can't login immediately (unusual) |
| Disabled | Disabled | Complete lockdown - no registration or login (admins can still login) |

For controlling who can **log in** (not register), see the [Login Control Management](/docs/system-administration/configuration/login-control) guide.

## Related Documentation

- [Login Control Management](/docs/system-administration/configuration/login-control) - Control user login access
- [Authentication Configuration](/docs/system-administration/configuration/authentication) - Cookie and JWT settings
- [OIDC/SSO Configuration](/docs/system-administration/configuration/oidc-sso) - Configure external identity providers  