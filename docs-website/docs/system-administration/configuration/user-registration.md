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