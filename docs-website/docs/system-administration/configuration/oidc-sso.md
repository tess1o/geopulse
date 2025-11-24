# OIDC Authentication

GeoPulse supports OpenID Connect (OIDC) authentication for single sign-on with popular providers like Google, Microsoft,
Keycloak, Auth0, and many others. All OIDC providers are configured using environment variables with a unified pattern.

## ⚠️ Prerequisites

**OIDC must be globally enabled first:**

```bash
# REQUIRED: Enable OIDC authentication globally
GEOPULSE_OIDC_ENABLED=true
```

Without this setting, all provider configurations will be ignored and OIDC authentication will not be available.

## Configuration Pattern

All OIDC providers use the same environment variable pattern:

```bash
GEOPULSE_OIDC_PROVIDER_{NAME}_{PROPERTY}=value
```

### Required Properties

| Property        | Description                 | Example                                                 |
|-----------------|-----------------------------|---------------------------------------------------------|
| `ENABLED`       | Enable/disable the provider | `true` or `false`                                       |
| `NAME`          | Display name shown in UI    | `Google`, `Company SSO`, etc.                           |
| `CLIENT_ID`     | OAuth 2.0 client ID         | Provider-specific client ID                             |
| `CLIENT_SECRET` | OAuth 2.0 client secret     | Provider-specific secret                                |
| `DISCOVERY_URL` | OIDC discovery endpoint     | `https://provider.com/.well-known/openid-configuration` |

### Optional Properties

| Property | Description           | Example                           |
|----------|-----------------------|-----------------------------------|
| `ICON`   | CSS icon class for UI | `pi pi-google`, `pi pi-microsoft` |

## OIDC Callback URL

For all OIDC providers, you must configure a callback URL. This is the URL that the provider will redirect to after
authentication. All providers should use the callback URL: `http://your-ip-address:port/oidc/callback` or
`https://geopulse.mydomain.com/oidc/callback`.

Additionally you might need to update `GEOPULSE_OIDC_CALLBACK_BASE_URL` environment variable to match your frontend
URL. By default it's set to `GEOPULSE_UI_URL` but in case if you have multiple domains you might need to change it to a
single one.

## Supported Providers

The system supports any OIDC-compliant provider. Here are configuration examples for popular providers:

### Google

```bash
GEOPULSE_OIDC_PROVIDER_GOOGLE_ENABLED=true
GEOPULSE_OIDC_PROVIDER_GOOGLE_NAME=Google
GEOPULSE_OIDC_PROVIDER_GOOGLE_CLIENT_ID=your-google-client-id
GEOPULSE_OIDC_PROVIDER_GOOGLE_CLIENT_SECRET=your-google-client-secret
GEOPULSE_OIDC_PROVIDER_GOOGLE_DISCOVERY_URL=https://accounts.google.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_GOOGLE_ICON=pi pi-google
```

### Microsoft Azure AD

```bash
GEOPULSE_OIDC_PROVIDER_MICROSOFT_ENABLED=true
GEOPULSE_OIDC_PROVIDER_MICROSOFT_NAME=Microsoft
GEOPULSE_OIDC_PROVIDER_MICROSOFT_CLIENT_ID=your-azure-client-id
GEOPULSE_OIDC_PROVIDER_MICROSOFT_CLIENT_SECRET=your-azure-client-secret
GEOPULSE_OIDC_PROVIDER_MICROSOFT_DISCOVERY_URL=https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_MICROSOFT_ICON=pi pi-microsoft
```

### Keycloak

```bash
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_ENABLED=true
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_NAME=Company SSO
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_CLIENT_ID=geopulse-client
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_CLIENT_SECRET=your-keycloak-secret
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_DISCOVERY_URL=https://keycloak.company.com/auth/realms/master/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_ICON=pi pi-key
```

### Auth0

```bash
GEOPULSE_OIDC_PROVIDER_AUTH0_ENABLED=true
GEOPULSE_OIDC_PROVIDER_AUTH0_NAME=Auth0
GEOPULSE_OIDC_PROVIDER_AUTH0_CLIENT_ID=your-auth0-client-id
GEOPULSE_OIDC_PROVIDER_AUTH0_CLIENT_SECRET=your-auth0-client-secret
GEOPULSE_OIDC_PROVIDER_AUTH0_DISCOVERY_URL=https://your-domain.auth0.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_AUTH0_ICON=pi pi-shield
```

### PocketID

```bash
GEOPULSE_OIDC_PROVIDER_POCKETID_ENABLED=true
GEOPULSE_OIDC_PROVIDER_POCKETID_NAME=PocketID
GEOPULSE_OIDC_PROVIDER_POCKETID_CLIENT_ID=your-pocketid-client-id
GEOPULSE_OIDC_PROVIDER_POCKETID_CLIENT_SECRET=your-pocketid-secret
GEOPULSE_OIDC_PROVIDER_POCKETID_DISCOVERY_URL=https://pocketid.example.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_POCKETID_ICON=pi pi-id-card
```

### GitLab

```bash
GEOPULSE_OIDC_PROVIDER_GITLAB_ENABLED=true
GEOPULSE_OIDC_PROVIDER_GITLAB_NAME=GitLab
GEOPULSE_OIDC_PROVIDER_GITLAB_CLIENT_ID=your-gitlab-client-id
GEOPULSE_OIDC_PROVIDER_GITLAB_CLIENT_SECRET=your-gitlab-secret
GEOPULSE_OIDC_PROVIDER_GITLAB_DISCOVERY_URL=https://gitlab.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_GITLAB_ICON=pi pi-code
```

### GitHub (via OIDC proxy)

```bash
GEOPULSE_OIDC_PROVIDER_GITHUB_ENABLED=true
GEOPULSE_OIDC_PROVIDER_GITHUB_NAME=GitHub
GEOPULSE_OIDC_PROVIDER_GITHUB_CLIENT_ID=your-github-client-id
GEOPULSE_OIDC_PROVIDER_GITHUB_CLIENT_SECRET=your-github-secret
GEOPULSE_OIDC_PROVIDER_GITHUB_DISCOVERY_URL=https://your-oidc-proxy.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_GITHUB_ICON=pi pi-github
```

## Icon Support

The system provides smart icon detection for common providers. If no icon is specified, it will automatically detect
icons based on the provider name:

| Provider Pattern     | Auto-detected Icon |
|----------------------|--------------------|
| `google`             | `pi pi-google`     |
| `microsoft`, `azure` | `pi pi-microsoft`  |
| `keycloak`           | `pi pi-key`        |
| `auth0`, `okta`      | `pi pi-shield`     |
| `gitlab`             | `pi pi-code`       |
| `github`             | `pi pi-github`     |
| `pocketid`, `pocket` | `pi pi-id-card`    |
| `authentik`          | `pi pi-lock`       |
| `discord`            | `pi pi-discord`    |
| `facebook`, `meta`   | `pi pi-facebook`   |
| `twitter`, `x.com`   | `pi pi-twitter`    |
| `linkedin`           | `pi pi-linkedin`   |
| `apple`              | `pi pi-apple`      |
| `amazon`, `aws`      | `pi pi-amazon`     |
| Custom/Unknown       | `pi pi-sign-in`    |

## Global OIDC Configuration

**Required Settings:**

```bash
# ⚠️ REQUIRED: Enable OIDC authentication globally
GEOPULSE_OIDC_ENABLED=true

# ⚠️ REQUIRED: Callback URL base (should match your frontend URL)
GEOPULSE_UI_URL=https://your-domain.com
```

**Optional Settings:**

```bash
# Session and security settings
GEOPULSE_OIDC_STATE_TOKEN_LENGTH=32
GEOPULSE_OIDC_STATE_TOKEN_EXPIRY_MINUTES=10

# Provider metadata caching
GEOPULSE_OIDC_METADATA_CACHE_TTL_HOURS=24
GEOPULSE_OIDC_METADATA_CACHE_MAX_SIZE=10

# Cleanup settings
GEOPULSE_OIDC_CLEANUP_ENABLED=true

# Account Linking Security
# ⚠️ WARNING: Only enable this if you fully trust your OIDC providers to verify email ownership
# When enabled, automatically links OIDC accounts to existing users with matching emails
# Default: false (requires manual verification via password or existing OIDC provider)
# Recommended for self-hosted environments with centralized SSO (Keycloak, Authentik, etc.)
GEOPULSE_OIDC_AUTO_LINK_ACCOUNTS=false
```

## Multiple Providers

You can configure multiple providers simultaneously. Users will see all enabled providers as login options:

```bash
# Enable multiple providers
GEOPULSE_OIDC_PROVIDER_GOOGLE_ENABLED=true
GEOPULSE_OIDC_PROVIDER_MICROSOFT_ENABLED=true  
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_ENABLED=true
GEOPULSE_OIDC_PROVIDER_AUTH0_ENABLED=true

# Each with their own configuration like name, client id, client secret, discovery url, etc...
```

#### .env file example

```shell
# ⚠️ REQUIRED: Enable OIDC globally first
GEOPULSE_OIDC_ENABLED: "true"

# Google OAuth
GEOPULSE_OIDC_PROVIDER_GOOGLE_ENABLED: "true"
GEOPULSE_OIDC_PROVIDER_GOOGLE_NAME: "Google"
GEOPULSE_OIDC_PROVIDER_GOOGLE_CLIENT_ID: "${GOOGLE_CLIENT_ID}"
GEOPULSE_OIDC_PROVIDER_GOOGLE_CLIENT_SECRET: "${GOOGLE_CLIENT_SECRET}"
GEOPULSE_OIDC_PROVIDER_GOOGLE_DISCOVERY_URL: "https://accounts.google.com/.well-known/openid-configuration"

# Company Keycloak
GEOPULSE_OIDC_PROVIDER_COMPANY_ENABLED: "true"
GEOPULSE_OIDC_PROVIDER_COMPANY_NAME: "Company SSO"
GEOPULSE_OIDC_PROVIDER_COMPANY_CLIENT_ID: "${COMPANY_SSO_CLIENT_ID}"
GEOPULSE_OIDC_PROVIDER_COMPANY_CLIENT_SECRET: "${COMPANY_SSO_CLIENT_SECRET}"
GEOPULSE_OIDC_PROVIDER_COMPANY_DISCOVERY_URL: "https://sso.company.com/auth/realms/employees/.well-known/openid-configuration"
GEOPULSE_OIDC_PROVIDER_COMPANY_ICON: "pi pi-building"

```

#### Notes

- **IMPORTANT**: `GEOPULSE_OIDC_ENABLED=true` must be set or OIDC will not work at all
- Provider names in environment variables must be unique
- Discovery URLs must be accessible from the GeoPulse backend
- All providers must support the OIDC standard with discovery endpoints
- Icons use PrimeIcons CSS classes
- Provider configuration is validated at startup

## Account Linking Behavior

When a user tries to login via OIDC with an email that already exists in GeoPulse, the system behavior depends on the `GEOPULSE_OIDC_AUTO_LINK_ACCOUNTS` setting:

### Default Behavior (Auto-Link Disabled)

By default (`GEOPULSE_OIDC_AUTO_LINK_ACCOUNTS=false`), GeoPulse requires manual verification before linking a new OIDC provider to an existing account. The user must verify their identity through:

- **Password verification**: If the account has a password set
- **OIDC verification**: Login via an already-linked OIDC provider

This is the **recommended security setting for public-facing deployments** or when you don't fully control the OIDC providers.

**Example scenario:**
1. User has account with email `user@example.com` created via password registration
2. User tries to login via Google OIDC with the same email `user@example.com`
3. System prompts user to verify identity (enter password OR login via another linked OIDC provider)
4. After verification, Google is linked to the account

### Auto-Link Enabled

When `GEOPULSE_OIDC_AUTO_LINK_ACCOUNTS=true`, the system automatically links OIDC accounts to existing users with matching emails **without requiring verification**.

**⚠️ Security Implications:**
- Only enable this if you **fully trust your OIDC providers** to verify email ownership
- Recommended **only for self-hosted environments** with centralized SSO (Keycloak, Authentik, etc.)
- If an attacker can control an OIDC provider or compromise one, they could gain access to any account by claiming the email

**Use cases for enabling auto-link:**
- Self-hosted environments with enterprise SSO
- Scenarios where all OIDC providers are managed by the same organization
- When seamless user experience is prioritized over defense-in-depth

**Example scenario:**
1. User has account with email `user@company.com` created via password registration
2. User tries to login via Company Keycloak OIDC with the same email
3. System automatically links Keycloak to the account and logs the user in
4. No verification required (trusting that Keycloak verified the email)

## Kubernetes / Helm Configuration

The GeoPulse Helm chart provides native support for OIDC configuration via `values.yaml`. This is the recommended approach for Kubernetes deployments.

### Supported Providers in Helm

The Helm chart supports three OIDC providers out of the box:
- **Google** (`config.oidc.google`)
- **Microsoft** (`config.oidc.microsoft`)
- **Generic OIDC** (`config.oidc.generic`) - for Keycloak, Authentik, Okta, Auth0, etc.

### Google OIDC Configuration

```yaml
# values.yaml or custom-values.yaml
config:
  oidc:
    enabled: true
    google:
      enabled: true
      clientId: "your-google-client-id.apps.googleusercontent.com"
      clientSecret: "your-google-client-secret"
```

Apply with:
```bash
helm upgrade geopulse ./helm/geopulse -f custom-values.yaml
```

### Microsoft Azure AD Configuration

```yaml
config:
  oidc:
    enabled: true
    microsoft:
      enabled: true
      clientId: "your-azure-client-id"
      clientSecret: "your-azure-client-secret"
```

### Keycloak / Authentik / Generic OIDC Configuration

```yaml
config:
  oidc:
    enabled: true
    autoLinkAccounts: false  # See security note above
    cleanupEnabled: true
    generic:
      enabled: true
      name: "Company SSO"  # Display name in UI
      clientId: "geopulse"
      clientSecret: "your-keycloak-client-secret"
      discoveryUrl: "https://keycloak.example.com/realms/master/.well-known/openid-configuration"
```

### Multiple Providers in Helm

You can enable multiple providers simultaneously:

```yaml
config:
  oidc:
    enabled: true
    google:
      enabled: true
      clientId: "..."
      clientSecret: "..."
    microsoft:
      enabled: true
      clientId: "..."
      clientSecret: "..."
    generic:
      enabled: true
      name: "Company SSO"
      clientId: "..."
      clientSecret: "..."
      discoveryUrl: "..."
```

### Additional OIDC Providers (Advanced)

If you need to configure OIDC providers beyond the three built-in options (e.g., Auth0, GitLab, multiple Keycloak realms), use custom environment variables:

```yaml
backend:
  extraEnv:
    - name: GEOPULSE_OIDC_PROVIDER_AUTH0_ENABLED
      value: "true"
    - name: GEOPULSE_OIDC_PROVIDER_AUTH0_NAME
      value: "Auth0"
    - name: GEOPULSE_OIDC_PROVIDER_AUTH0_CLIENT_ID
      value: "your-auth0-client-id"
    - name: GEOPULSE_OIDC_PROVIDER_AUTH0_DISCOVERY_URL
      value: "https://your-domain.auth0.com/.well-known/openid-configuration"
    - name: GEOPULSE_OIDC_PROVIDER_AUTH0_CLIENT_SECRET
      valueFrom:
        secretKeyRef:
          name: geopulse-oidc-secrets
          key: auth0-client-secret
```

First, create the secret:
```bash
kubectl create secret generic geopulse-oidc-secrets \
  --from-literal=auth0-client-secret='your-auth0-client-secret'
```

### Managing OIDC Secrets in Kubernetes

**Security Best Practices:**

1. **Use Kubernetes Secrets** - Never commit client secrets to Git
2. **Use Sealed Secrets** - For GitOps workflows, encrypt secrets with Sealed Secrets or External Secrets Operator
3. **Rotate secrets regularly** - Update secrets periodically and restart pods

**Example using kubectl:**
```bash
# Create OIDC secrets
kubectl create secret generic geopulse-oidc \
  --from-literal=google-client-secret='...' \
  --from-literal=microsoft-client-secret='...' \
  --from-literal=keycloak-client-secret='...'

# Update values.yaml to reference secrets
# (Note: The Helm chart already handles this automatically)
```

For more details on Helm configuration, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-configuration-guide).

## Troubleshooting

**OIDC providers not showing on login page:**

- Verify `GEOPULSE_OIDC_ENABLED=true` is set (or `config.oidc.enabled: true` in Helm)
- Check that individual provider `_ENABLED=true` settings are configured
- Ensure all required properties (client-id, client-secret, discovery-url) are provided
- Check application logs for provider initialization errors
- For Kubernetes: Verify ConfigMap and Secret contain the OIDC configuration

**Account linking errors:**

- If auto-link is disabled, users must verify identity before linking new OIDC providers
- Check that the email from OIDC provider matches the existing account email exactly
- Review application logs for security warnings related to account linking