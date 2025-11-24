# Admin Panel

GeoPulse includes a built-in Admin Panel for managing users, user invitations, OIDC providers, and system settings through a web interface. This guide covers how to promote the first administrator and use the available admin features.

---

## Promoting the First Administrator

Before accessing the Admin Panel, you need at least one user with the `ADMIN` role. Since there's no admin initially, you must use an environment variable to promote the first administrator.

### Using GEOPULSE_ADMIN_EMAIL

| Environment Variable     | Default | Description                                                    |
|--------------------------|---------|----------------------------------------------------------------|
| `GEOPULSE_ADMIN_EMAIL`   | (none)  | Email address of the user to automatically promote to ADMIN    |

Set this variable to the email address of the user who should become an administrator:

```bash
GEOPULSE_ADMIN_EMAIL=admin@example.com
```

**How it works:**

1. **New user registration:** If a user registers with this email address, they will automatically receive the `ADMIN` role.
2. **Existing user login:** If a user with this email already exists, they will be promoted to `ADMIN` on their next login.

:::warning
If you're already logged in when setting `GEOPULSE_ADMIN_EMAIL`, you must **log out and log back in** for the promotion to take effect. The admin role is assigned during the login process, so an existing valid session won't reflect the change until you re-authenticate.
:::

:::note
Once you have an admin user, they can promote other users to `ADMIN` through the Admin Panel UI.
:::

### Docker Compose Example

```yaml
services:
  geopulse:
    image: tess1o/geopulse:latest
    environment:
      GEOPULSE_ADMIN_EMAIL: admin@example.com
      # ... other environment variables
```

### Kubernetes/Helm Configuration

Configure in `values.yaml`:

```yaml
config:
  admin:
    email: "admin@example.com"
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-configuration-guide#admin-configuration).

---

## Accessing the Admin Panel

Once you have admin privileges, access the Admin Panel at:

```
https://your-geopulse-instance/app/admin
```

The Admin Panel is only visible to users with the `ADMIN` role.

---

## Admin Dashboard

The Admin Dashboard (`/app/admin`) provides a system overview with quick statistics:

| Metric              | Description                                          |
|---------------------|------------------------------------------------------|
| **Total Users**     | Total number of registered users                     |
| **Active Users (24h)** | Users who received GPS data within the last 24 hours |
| **Total GPS Points** | Total GPS data points across all users              |
| **Memory Usage**    | Current backend memory usage in MB                   |

The dashboard also provides quick links to:
- **User Management** - Manage user accounts
- **User Invitations** - Generate invitation links for new users
- **OIDC Providers** - Configure authentication providers
- **System Settings** - Configure system-wide settings

---

## User Management

Navigate to **Admin Dashboard > Manage Users** or `/app/admin/users`.

### Features

| Action            | Description                                                    |
|-------------------|----------------------------------------------------------------|
| **Search Users**  | Search users by email or name                                  |
| **View Details**  | Click on a user email to see detailed user information         |
| **Enable/Disable** | Toggle user account status (disabled users cannot log in)     |
| **Delete User**   | Permanently delete a user and all their data                   |

### User Details Page

Click on a user to view:
- User profile information (email, name, role)
- Account status (active/disabled)
- Registration date
- GPS points count
- Option to promote/demote user role

### Promoting Users to Admin

From the User Details page, administrators can:
- **Promote to ADMIN:** Grant admin privileges to a regular user
- **Demote to USER:** Remove admin privileges (cannot demote yourself)

:::warning
Be cautious when granting admin privileges. Admins can modify system settings, delete users, and access all administrative functions.
:::

---

## User Invitations

Navigate to **Admin Dashboard > User Invitations** or `/app/admin/invitations`.

The User Invitations feature allows administrators to generate secure, one-time registration links for inviting specific users to join your GeoPulse instance. **Invitation links work even when public registration is disabled**, making them ideal for private or controlled-access deployments.

### Key Features

| Feature | Description |
|---------|-------------|
| **Bypass Registration Checks** | Invitation links allow registration even when public registration is disabled |
| **Single-Use Tokens** | Each invitation can only be used once for registration |
| **Customizable Expiry** | Set custom expiration dates (default: 7 days) |
| **Secure Tokens** | 64-character cryptographically secure tokens using SecureRandom |
| **Status Tracking** | Monitor invitation status (Pending, Used, Expired, Revoked) |
| **Audit Logging** | All invitation actions are logged for security |

### Creating an Invitation

1. Click **Create Invitation** button
2. Set the expiration date (or use the default 7-day expiry)
3. Click **Create**
4. Copy the generated link and share it with the intended user

The invitation link format:
```
https://your-geopulse-instance/register/invite/{token}
```

### Invitation Statuses

| Status | Description |
|--------|-------------|
| **Pending** | Invitation is valid and can be used |
| **Used** | Invitation was successfully used for registration |
| **Expired** | Invitation has passed its expiration date |
| **Revoked** | Invitation was manually revoked by an administrator |

### Managing Invitations

The invitations table displays:
- **Token** - First 12 characters of the invitation token
- **Created By** - Administrator who created the invitation
- **Created** - Creation timestamp
- **Expires** - Expiration timestamp
- **Status** - Current invitation status
- **Used By** - User who registered with this invitation (if used)

### Actions

| Action | Description | Availability |
|--------|-------------|--------------|
| **Copy Link** | Copy the invitation URL to clipboard | Pending only |
| **Revoke** | Cancel an unused invitation | Pending only |

### Configuration

Configure the base URL for invitation links:

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `GEOPULSE_INVITATION_BASE_URL` | (empty) | Custom domain for invitation links (e.g., https://geopulse.example.com) |

If not set, the frontend will use `window.location.origin` to construct invitation URLs.

**Docker Compose Example:**
```yaml
services:
  geopulse:
    image: tess1o/geopulse:latest
    environment:
      GEOPULSE_INVITATION_BASE_URL: https://geopulse.example.com
      # ... other environment variables
```

**Kubernetes/Helm Configuration:**
```yaml
config:
  invitation:
    baseUrl: "https://geopulse.example.com"
```

### User Registration Flow

When a user receives an invitation link:

1. User clicks the invitation link
2. GeoPulse validates the token (checks if it's valid, not expired, not used, not revoked)
3. User completes the registration form (email, password, name, timezone)
4. Account is created and invitation is marked as used
5. User is automatically logged in and redirected to onboarding

:::tip Use Case: Private Deployments
Invitations are perfect for private GeoPulse instances where you want to control who can register. Simply disable public registration and use invitation links to selectively allow specific users to join.
:::

:::warning Security Note
- Invitation tokens are cryptographically secure and cannot be guessed
- Each token is 64 characters long and can only be used once
- Expired or revoked invitations cannot be used for registration
- All invitation actions are logged to the audit log
:::

---

## OIDC Provider Management

Navigate to **Admin Dashboard > OIDC Providers** or `/app/admin/oidc-providers`.

This page allows you to manage OAuth/OIDC authentication providers through the UI instead of environment variables.

### Provider List

The table displays all configured providers with:

| Column          | Description                                              |
|-----------------|----------------------------------------------------------|
| **Name**        | Provider identifier (e.g., `google`, `keycloak`)         |
| **Display Name** | User-friendly name shown on login page                  |
| **Enabled**     | Whether the provider is active                           |
| **Source**      | `Environment` (from env vars) or `Custom` (from database)|
| **Metadata**    | Whether OIDC discovery metadata is cached                |
| **Client ID**   | The OAuth client ID                                      |

### Actions

| Action             | Description                                              |
|--------------------|----------------------------------------------------------|
| **Add Provider**   | Create a new OIDC provider configuration                 |
| **Edit Provider**  | Modify provider settings                                 |
| **Enable/Disable** | Toggle provider availability                             |
| **Test Connection** | Verify provider connectivity and discover endpoints     |
| **Delete Provider** | Remove provider (environment-only providers cannot be deleted) |

### Provider Sources

- **Environment:** Provider configured via environment variables. These cannot be deleted, only customized.
- **Custom (Database):** Provider created through the Admin UI. These can be fully managed.

When a provider exists in both environment and database, the database configuration takes precedence. Deleting a custom configuration for an environment provider reverts it to the environment defaults.

:::note
For initial OIDC setup via environment variables, see [OIDC / SSO Configuration](./oidc-sso).
:::

---

## System Settings

Navigate to **Admin Dashboard > System Settings** or `/app/admin/settings`.

The System Settings page provides UI-based configuration for various system options, organized into tabs.

### Authentication Tab

Configure user registration behavior:

| Setting                  | Description                                           | Default |
|--------------------------|-------------------------------------------------------|---------|
| **Registration Enabled** | Master switch for all new user registrations          | `true`  |
| **Password Registration** | Allow registration with email/password               | `true`  |
| **OIDC Registration**    | Allow registration via OIDC providers                 | `true`  |
| **Auto-Link OIDC Accounts** | Automatically link OIDC accounts by email          | `false` |

:::warning
**Auto-Link OIDC Accounts** poses a security risk if OIDC providers don't verify email addresses. Only enable if you trust all configured providers.
:::

### Geocoding Tab

Configure reverse geocoding providers:

**General Settings:**
- **Primary Provider:** Main geocoding service (Nominatim, Photon, Google Maps, Mapbox)
- **Fallback Provider:** Backup provider if primary fails
- **Request Delay:** Milliseconds between geocoding requests

**Provider Availability:**
- Enable/disable individual geocoding providers

**Provider Configuration:**
- Custom URLs for self-hosted providers (Nominatim, Photon)
- API keys for commercial providers (Google Maps, Mapbox)

### Coming Soon

The following tabs show planned features:

- **GPS Processing:** Stay detection algorithms, accuracy filtering, batch processing
- **Import:** Import job limits and concurrent processing settings
- **System:** Performance tuning, database maintenance, metrics configuration

---

## Settings Hierarchy

Settings in the Admin Panel follow this precedence:

1. **Database (Custom):** Settings changed via Admin UI are stored in the database and take highest priority
2. **Environment Variables:** Default configuration from deployment
3. **Application Defaults:** Built-in defaults if nothing else is configured

Each setting shows whether it's using the default value or a custom value. Custom values can be reset to defaults using the **Reset** button.

---