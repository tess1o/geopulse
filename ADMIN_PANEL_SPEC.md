# GeoPulse Admin Panel - Complete Specification

## Overview

This document specifies the implementation of an Admin Panel for GeoPulse, enabling administrators to manage system settings, users, and OIDC providers through a web interface without server restarts.

---

## Architecture Principles

### Settings Override Pattern

Following the existing `TimelineConfigurationProvider` pattern:

```
┌─────────────────┐     ┌─────────────────┐
│   Env Variable  │     │    Database     │
│   (Default)     │     │   (Override)    │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     ▼
              ┌─────────────┐
              │   Check DB  │
              │   first     │
              └──────┬──────┘
                     │
            ┌────────┴────────┐
            │                 │
      DB has value?     DB empty?
            │                 │
            ▼                 ▼
      Use DB value      Use Env var
```

1. Load defaults from environment variables
2. Check database for overrides
3. If DB has value → use DB value
4. If DB empty → use env var default
5. Admin modifications save to DB
6. "Reset to default" = delete DB entry (reverts to env var)

### Encryption Strategy

Secrets (API keys, OIDC client secrets) are encrypted using the existing `AIEncryptionService` pattern before storing in DB.

---

## Phase 1 Scope (Top Priority)

### 1.1 Admin Role Infrastructure

**Environment Variable:**
```bash
GEOPULSE_ADMIN_EMAIL=admin@example.com  # First admin bootstrap
```

**Role System:**
- `Role` enum: `USER`, `ADMIN`
- First user matching `GEOPULSE_ADMIN_EMAIL` becomes ADMIN during registration/login
- Admins can promote other users to ADMIN role
- Multiple admins supported
- Last admin cannot remove own admin role (self-demotion prevention)

---

### 1.2 Authentication & Registration Settings

| Key | Type | Default Env Var | Description |
|-----|------|-----------------|-------------|
| `auth.registration.enabled` | boolean | `GEOPULSE_AUTH_REGISTRATION_ENABLED` (true) | Global registration toggle |
| `auth.password-registration.enabled` | boolean | `GEOPULSE_AUTH_PASSWORD_REGISTRATION_ENABLED` (true) | Password signup allowed |
| `auth.oidc.registration.enabled` | boolean | `GEOPULSE_AUTH_OIDC_REGISTRATION_ENABLED` (true) | OIDC signup allowed |
| `auth.oidc.auto-link-accounts` | boolean | `GEOPULSE_OIDC_AUTO_LINK_ACCOUNTS` (false) | Auto-link accounts by email |

---

### 1.3 Geocoding Settings

| Key | Type | Default Env Var | Description |
|-----|------|-----------------|-------------|
| `geocoding.primary-provider` | string | `GEOPULSE_GEOCODING_PRIMARY_PROVIDER` (nominatim) | Primary service |
| `geocoding.fallback-provider` | string | `GEOPULSE_GEOCODING_FALLBACK_PROVIDER` (empty) | Fallback service |
| `geocoding.delay-ms` | integer | `GEOPULSE_GEOCODING_DELAY_MS` (1000) | Rate limit delay |
| `geocoding.nominatim.enabled` | boolean | `GEOPULSE_GEOCODING_NOMINATIM_ENABLED` (true) | Enable Nominatim |
| `geocoding.nominatim.url` | string | `GEOPULSE_GEOCODING_NOMINATIM_URL` | Custom Nominatim URL |
| `geocoding.photon.enabled` | boolean | `GEOPULSE_GEOCODING_PHOTON_ENABLED` (false) | Enable Photon |
| `geocoding.photon.url` | string | `GEOPULSE_GEOCODING_PHOTON_URL` | Custom Photon URL |
| `geocoding.googlemaps.enabled` | boolean | `GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED` (false) | Enable Google Maps |
| `geocoding.googlemaps.api-key` | encrypted | `GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY` | API key (encrypted in DB) |
| `geocoding.mapbox.enabled` | boolean | `GEOPULSE_GEOCODING_MAPBOX_ENABLED` (false) | Enable Mapbox |
| `geocoding.mapbox.access-token` | encrypted | `GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN` | Access token (encrypted in DB) |

---

### 1.4 OIDC Provider Management

**Behavior:**
- On load: Read providers from env vars (existing behavior)
- Display all providers in UI with their properties
- When admin modifies any provider → save entire provider config to DB (with encrypted secrets)
- Once in DB → always use DB config for that provider
- Admin can add new providers, edit existing, delete providers
- "Reset to default" removes provider from DB, reverts to env var config
- Providers only in env vars show as "Environment" source
- Providers in DB show as "Custom" source

**Provider Properties:**
- `name` - Unique identifier (lowercase, e.g., "google", "keycloak")
- `displayName` - UI display name (e.g., "Google", "Company SSO")
- `enabled` - Provider active toggle
- `clientId` - OAuth client ID
- `clientSecret` - OAuth client secret (encrypted)
- `discoveryUrl` - OIDC discovery endpoint
- `icon` - PrimeIcons class (e.g., "pi pi-google")

**Runtime Behavior:**
- `OidcProviderService` loads from both env vars and DB
- DB providers override env providers with same name
- Provider metadata (endpoints) cached and refreshed as needed
- "Test connection" validates discovery URL and caches metadata

---

### 1.5 User Management

**Capabilities:**

| Action | Description |
|--------|-------------|
| List users | Paginated, searchable by email/name, sortable |
| View details | Full profile, stats, linked providers |
| Enable/disable | Toggle `isActive` flag |
| Delete user | Cascade delete all user data (GPS points, timeline, settings, etc.) |
| Change role | Promote USER → ADMIN or demote ADMIN → USER |
| Reset password | Generate temporary password for user |

**User Details View:**
- Profile info (email, name, avatar, timezone)
- Account status (active, role, email verified)
- Statistics (GPS points count, timeline items, storage estimate)
- Dates (created, updated, last login)
- Linked OIDC providers list
- User preferences summary

---

### 1.6 User Invitation System

**Feature:** Admin generates one-time registration links that work even when public registration is disabled.

**Invitation Properties:**
- Unique cryptographically secure token (64 chars)
- Valid for **7 days**
- Single use (invalidated after successful registration)
- Password-based registration only (no OIDC)
- Can be revoked by admin before use

**Invitation Lifecycle:**
1. Admin creates invitation → generates link
2. Admin shares link with intended user
3. User visits link → registration form (even if registration disabled)
4. User completes registration → invitation marked as used
5. Token cannot be reused

**Admin Capabilities:**
- Create new invitation
- View all invitations (pending, used, expired, revoked)
- Copy invitation link
- Revoke unused invitation

---

### 1.7 GPS Processing Defaults

These are default values for new GPS sources:

| Key | Type | Default Env Var | Description |
|-----|------|-----------------|-------------|
| `gps.filter.inaccurate-data.enabled` | boolean | `GEOPULSE_GPS_FILTER_INACCURATE_DATA_ENABLED` (false) | Default filter toggle |
| `gps.max-allowed-accuracy` | integer | `GEOPULSE_GPS_MAX_ALLOWED_ACCURACY` (100) | Max accuracy in meters |
| `gps.max-allowed-speed` | integer | `GEOPULSE_GPS_MAX_ALLOWED_SPEED` (250) | Max speed in km/h |

---

### 1.8 Import Settings

| Key | Type | Default Env Var | Description |
|-----|------|-----------------|-------------|
| `import.bulk-insert-batch-size` | integer | `GEOPULSE_IMPORT_BULK_INSERT_BATCH_SIZE` (500) | DB batch size |
| `import.merge-batch-size` | integer | `GEOPULSE_IMPORT_MERGE_BATCH_SIZE` (250) | Merge operation batch |
| `import.large-file-threshold-mb` | integer | `GEOPULSE_IMPORT_LARGE_FILE_THRESHOLD_MB` (100) | Temp file threshold |
| `import.temp-file-retention-hours` | integer | `GEOPULSE_IMPORT_TEMP_FILE_RETENTION_HOURS` (24) | Cleanup interval |

---

### 1.9 System Performance

| Key | Type | Default Env Var | Description |
|-----|------|-----------------|-------------|
| `timeline.processing.thread-pool-size` | integer | `GEOPULSE_TIMELINE_PROCESSING_THREADS` (2) | Worker threads |
| `timeline.job.interval` | string | `GEOPULSE_TIMELINE_JOB_INTERVAL` (5m) | Processing frequency |
| `timeline.view.item-limit` | integer | `GEOPULSE_TIMELINE_VIEW_ITEM_LIMIT` (150) | Max timeline items in view |

---

### 1.10 Audit Log

All admin actions are logged for security and accountability.

**Tracked Events:**

| Action Type | Description |
|-------------|-------------|
| `SETTING_CHANGED` | System setting modified |
| `SETTING_RESET` | Setting reset to default |
| `USER_ENABLED` | User account enabled |
| `USER_DISABLED` | User account disabled |
| `USER_DELETED` | User and all data deleted |
| `USER_ROLE_CHANGED` | User role changed |
| `USER_PASSWORD_RESET` | Admin reset user password |
| `OIDC_PROVIDER_CREATED` | New OIDC provider added |
| `OIDC_PROVIDER_UPDATED` | OIDC provider modified |
| `OIDC_PROVIDER_DELETED` | OIDC provider removed |
| `OIDC_PROVIDER_RESET` | Provider reset to env default |
| `INVITATION_CREATED` | User invitation created |
| `INVITATION_REVOKED` | Invitation revoked |
| `ADMIN_LOGIN` | Admin user logged in |

**Audit Entry Fields:**
- `id` - Unique identifier
- `timestamp` - When action occurred
- `adminUserId` - Admin who performed action
- `actionType` - Type of action (from table above)
- `targetType` - SETTING, USER, OIDC_PROVIDER, INVITATION
- `targetId` - ID of affected entity
- `details` - JSON with context (before/after values, etc.)
- `ipAddress` - Admin's IP address

---

## Phase 2 Scope

### 2.1 Timeline Defaults for New Users

All timeline preference defaults that apply to newly registered users:

**Staypoint Detection:**
- `staypoint.velocity.threshold`
- `staypoint.radius-meters`
- `staypoint.min-duration-minutes`
- `staypoint.use-velocity-accuracy`
- `staypoint.accuracy.threshold`
- `staypoint.min-accuracy-ratio`

**Merge Settings:**
- `staypoint.merge.enabled`
- `staypoint.merge.max-distance-meters`
- `staypoint.merge.max-time-gap-minutes`

**Trip Detection:**
- `trip.detection.algorithm`
- `trip.arrival.min-duration-seconds`
- `trip.sustained-stop.min-duration-seconds`

**Travel Classification:**
- Walking thresholds (max avg/max speed)
- Car thresholds (min avg/max speed)
- Bicycle enabled + thresholds
- Train enabled + thresholds
- Flight enabled + thresholds
- Short distance threshold

**Path Simplification:**
- `path.simplification.enabled`
- `path.simplification.tolerance`
- `path.simplification.max-points`
- `path.simplification.adaptive`

**Data Gap Detection:**
- `data-gap.threshold-seconds`
- `data-gap.min-duration-seconds`
- `gap-stay-inference.enabled`
- `gap-stay-inference.max-gap-hours`

---

### 2.2 Monitoring (Prometheus)

| Key | Type | Default Env Var | Description |
|-----|------|-----------------|-------------|
| `prometheus.enabled` | boolean | `GEOPULSE_PROMETHEUS_ENABLED` (false) | Enable custom metrics |
| `prometheus.refresh-interval` | string | `GEOPULSE_PROMETHEUS_REFRESH_INTERVAL` (10m) | Metrics update frequency |
| `prometheus.gps-points.enabled` | boolean | (true) | GPS points metrics |
| `prometheus.user-metrics.enabled` | boolean | (true) | User metrics |
| `prometheus.timeline.enabled` | boolean | (true) | Timeline metrics |
| `prometheus.favorites.enabled` | boolean | (true) | Favorites metrics |
| `prometheus.geocoding.enabled` | boolean | (true) | Geocoding metrics |
| `prometheus.memory.enabled` | boolean | (true) | Memory metrics |

---

### 2.3 Sharing Settings

| Key | Type | Default Env Var | Description |
|-----|------|-----------------|-------------|
| `share.base-url` | string | `GEOPULSE_SHARE_BASE_URL` (empty) | Custom URL for shared links |

---

### 2.4 Maintenance Operations (Phase 2/3)

- Trigger geocoding cache cleanup
- Clear expired OIDC sessions
- View/purge import temp files
- View failed timeline jobs
- Re-run geocoding for specific users

---

## Database Schema

### Migration: V19.0.0__Add_admin_panel_support.sql

```sql
-- System settings table
CREATE TABLE system_settings (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL,
    value_type VARCHAR(20) NOT NULL,  -- STRING, BOOLEAN, INTEGER, ENCRYPTED
    category VARCHAR(50) NOT NULL,    -- auth, geocoding, gps, import, system
    description VARCHAR(500),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    updated_by UUID REFERENCES users(id)
);

CREATE INDEX idx_system_settings_category ON system_settings(category);

-- OIDC providers managed via DB
CREATE TABLE oidc_providers (
    name VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    client_id VARCHAR(255) NOT NULL,
    client_secret_encrypted TEXT NOT NULL,
    client_secret_key_id VARCHAR(50),
    discovery_url VARCHAR(500) NOT NULL,
    icon VARCHAR(100),
    -- Cached metadata from discovery
    authorization_endpoint VARCHAR(500),
    token_endpoint VARCHAR(500),
    userinfo_endpoint VARCHAR(500),
    jwks_uri VARCHAR(500),
    issuer VARCHAR(500),
    metadata_cached_at TIMESTAMP WITHOUT TIME ZONE,
    metadata_valid BOOLEAN DEFAULT false,
    -- Audit fields
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    updated_by UUID REFERENCES users(id)
);

-- User invitations
CREATE TABLE user_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    used_at TIMESTAMP WITHOUT TIME ZONE,
    used_by UUID REFERENCES users(id),
    revoked BOOLEAN NOT NULL DEFAULT false,
    revoked_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_user_invitations_token ON user_invitations(token);
CREATE INDEX idx_user_invitations_expires ON user_invitations(expires_at);
CREATE INDEX idx_user_invitations_created_by ON user_invitations(created_by);

-- Audit log
CREATE TABLE audit_log (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    admin_user_id UUID NOT NULL REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(255),
    details JSONB,
    ip_address VARCHAR(45)
);

CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp DESC);
CREATE INDEX idx_audit_log_admin ON audit_log(admin_user_id);
CREATE INDEX idx_audit_log_target ON audit_log(target_type, target_id);
CREATE INDEX idx_audit_log_action ON audit_log(action_type);
```

---

## API Endpoints

### Settings Management

```
GET    /api/admin/settings                    @RolesAllowed("ADMIN")
       - Returns all settings grouped by category
       - Each setting includes: key, value, valueType, category, description, isDefault, defaultValue

GET    /api/admin/settings/{category}         @RolesAllowed("ADMIN")
       - Returns settings for specific category

PUT    /api/admin/settings/{key}              @RolesAllowed("ADMIN")
       - Body: { "value": "newValue" }
       - Validates value type, saves to DB
       - Logs to audit

DELETE /api/admin/settings/{key}              @RolesAllowed("ADMIN")
       - Resets setting to env var default (deletes DB entry)
       - Logs to audit
```

### User Management

```
GET    /api/admin/users                       @RolesAllowed("ADMIN")
       - Query params: page, size, search, sortBy, sortDir
       - Returns paginated user list with summary stats

GET    /api/admin/users/{id}                  @RolesAllowed("ADMIN")
       - Returns full user details including stats

PUT    /api/admin/users/{id}/status           @RolesAllowed("ADMIN")
       - Body: { "active": true/false }
       - Enable/disable user account

PUT    /api/admin/users/{id}/role             @RolesAllowed("ADMIN")
       - Body: { "role": "ADMIN" | "USER" }
       - Change user role
       - Prevents last admin self-demotion

POST   /api/admin/users/{id}/reset-password   @RolesAllowed("ADMIN")
       - Generates temporary password
       - Returns password to admin (display once)
       - User must change on next login (future enhancement)

DELETE /api/admin/users/{id}                  @RolesAllowed("ADMIN")
       - Deletes user and ALL associated data
       - Cascade: GPS points, timeline, settings, OIDC connections, etc.
       - Requires confirmation in UI
```

### User Invitations

```
GET    /api/admin/invitations                 @RolesAllowed("ADMIN")
       - Returns all invitations with status
       - Filterable by status: pending, used, expired, revoked

POST   /api/admin/invitations                 @RolesAllowed("ADMIN")
       - Creates new invitation
       - Returns: { id, token, link, expiresAt }

DELETE /api/admin/invitations/{id}            @RolesAllowed("ADMIN")
       - Revokes unused invitation

GET    /api/auth/invitation/{token}           PUBLIC
       - Validates token, returns: { valid, expired, used, revoked }

POST   /api/auth/register/invitation          PUBLIC
       - Body: { token, email, password, fullName, timezone }
       - Registers user via invitation
       - Works even when registration is disabled
```

### OIDC Providers

```
GET    /api/admin/oidc/providers              @RolesAllowed("ADMIN")
       - Returns all providers (env + DB)
       - Each includes source indicator (ENVIRONMENT | DATABASE)

GET    /api/admin/oidc/providers/{name}       @RolesAllowed("ADMIN")
       - Returns single provider details
       - clientSecret masked (never returned)

POST   /api/admin/oidc/providers              @RolesAllowed("ADMIN")
       - Body: full provider config
       - Encrypts clientSecret before storing
       - Validates discovery URL

PUT    /api/admin/oidc/providers/{name}       @RolesAllowed("ADMIN")
       - Body: updated provider config
       - If provider was from env, saves to DB (becomes custom)
       - clientSecret only updated if provided (not empty)

DELETE /api/admin/oidc/providers/{name}       @RolesAllowed("ADMIN")
       - If from DB: deletes, reverts to env (if exists)
       - If only in env: returns error

POST   /api/admin/oidc/providers/{name}/test  @RolesAllowed("ADMIN")
       - Tests discovery URL connectivity
       - Returns: { success, endpoints, error }

POST   /api/admin/oidc/providers/{name}/reset @RolesAllowed("ADMIN")
       - Resets to env var configuration
       - Deletes DB entry for this provider
```

### Audit Log

```
GET    /api/admin/audit                       @RolesAllowed("ADMIN")
       - Query params: page, size, actionType, targetType, adminUserId, from, to
       - Returns paginated audit entries
       - Sortable by timestamp (default: newest first)
```

---

## Frontend Structure

### New Routes

```javascript
// router/index.js additions

// Admin guard
const requireAdmin = async (to, from, next) => {
    const authStore = useAuthStore()

    if (!authStore.user) {
        await authStore.checkAuth()
    }

    if (!authStore.isAuthenticated) {
        next('/login')
    } else if (!authStore.isAdmin) {
        next('/app/timeline')  // Redirect non-admins
    } else {
        next()
    }
}

// Admin routes
{
    path: '/app/admin',
    component: AdminLayout,
    beforeEnter: requireAdmin,
    children: [
        { path: '', redirect: '/app/admin/dashboard' },
        { path: 'dashboard', component: AdminDashboardPage },
        { path: 'settings', component: AdminSettingsPage },
        { path: 'users', component: AdminUsersPage },
        { path: 'users/:id', component: AdminUserDetailsPage },
        { path: 'oidc', component: AdminOidcProvidersPage },
        { path: 'invitations', component: AdminInvitationsPage },
        { path: 'audit', component: AdminAuditLogPage },
    ]
}

// Public invitation registration
{
    path: '/register/invite/:token',
    component: InvitationRegisterPage
}
```

### Auth Store Updates

```javascript
// stores/auth.js additions

const isAdmin = computed(() => {
    return user.value?.role === 'ADMIN'
})

// Export isAdmin
```

### Admin Navigation

Sidebar section visible only when `authStore.isAdmin`:

```
Admin
├── Dashboard       (overview stats, quick actions)
├── Settings        (tabbed: Auth, Geocoding, GPS, Import, System)
├── Users           (user management table)
├── OIDC Providers  (provider management)
├── Invitations     (invitation management)
└── Audit Log       (action history)
```

---

### Settings Page UI Design

**Tabbed Interface:**

1. **Authentication** tab
   - Registration enabled (toggle)
   - Password registration (toggle)
   - OIDC registration (toggle)
   - Auto-link accounts (toggle with warning)

2. **Geocoding** tab
   - Primary provider (dropdown)
   - Fallback provider (dropdown)
   - Request delay (number input)
   - Provider toggles section:
     - Nominatim: enabled + custom URL
     - Photon: enabled + custom URL
     - Google Maps: enabled + API key (masked)
     - Mapbox: enabled + access token (masked)

3. **GPS Processing** tab
   - Filter inaccurate data (toggle)
   - Max allowed accuracy (number)
   - Max allowed speed (number)

4. **Import** tab
   - Batch sizes
   - File thresholds
   - Retention settings

5. **System** tab
   - Thread pool size
   - Job interval
   - View item limit

**Setting Component Features:**
- Label and description
- Current value display
- Edit control (appropriate type)
- "Using default" indicator when not overridden
- "Reset to default" button when overridden
- Validation feedback

**Encrypted Field Handling:**
- Display: "••••••••" or "Not set"
- Edit via modal with password input
- "Update secret" / "Clear secret" actions
- Never display actual value

---

### OIDC Provider Page UI

**Provider List Table:**
- Columns: Name, Display Name, Enabled, Source, Status, Actions
- Source badge: "Environment" (blue) or "Custom" (green)
- Status: Valid metadata indicator
- Actions: Edit, Test, Delete/Reset

**Add/Edit Provider Modal:**
- Name (readonly if editing)
- Display Name
- Enabled toggle
- Client ID
- Client Secret (password field, optional on edit)
- Discovery URL
- Icon (dropdown with preview)
- Test Connection button

**Provider Actions:**
- Edit → opens modal
- Test → calls test endpoint, shows result
- Delete (custom) → confirmation dialog
- Reset (env-based custom) → reverts to env config

---

### User Management Page UI

**User List:**
- Search box (email, name)
- DataTable with columns:
  - Email
  - Full Name
  - Role (badge)
  - Status (Active/Disabled badge)
  - Created
  - Last Login
  - GPS Points
  - Actions
- Pagination controls
- Sort by any column

**Row Actions:**
- View details
- Enable/Disable toggle
- Change role
- Reset password
- Delete (danger)

**User Details Page:**
- Header: Avatar, name, email, role badge
- Stats cards: GPS points, Timeline items, Storage
- Info section: Created, updated, last login, timezone
- OIDC Connections list
- Danger Zone: Disable, Delete

---

### Invitations Page UI

**Create Invitation:**
- "Create Invitation" button
- Dialog shows generated link with copy button
- Displays expiry date

**Invitations Table:**
- Columns: Token (partial), Created By, Created, Expires, Status, Actions
- Status badges: Pending, Used, Expired, Revoked
- Filter by status
- Actions: Copy Link (pending), Revoke (pending), View Details

---

### Audit Log Page UI

**Filters:**
- Date range picker
- Action type dropdown
- Target type dropdown
- Admin user dropdown

**Audit Table:**
- Timestamp
- Admin (name/email)
- Action (with icon)
- Target
- Details (expandable)

**Details Expansion:**
- JSON viewer for before/after values
- IP address
- Full context

---

## Security Considerations

### 1. Role Enforcement
- All admin endpoints use `@RolesAllowed("ADMIN")`
- Frontend guards prevent navigation
- API validates role on every request

### 2. Secret Encryption
- Use existing `AIEncryptionService` infrastructure
- Encryption key from `GEOPULSE_AI_ENCRYPTION_KEY_LOCATION`
- Secrets never returned to frontend
- Key ID stored with encrypted value for rotation support

### 3. Audit Trail
- All admin actions logged
- Includes before/after values where applicable
- IP address captured
- Cannot be modified or deleted

### 4. Self-Protection
- Admin cannot disable own account
- Last admin cannot demote self
- Last admin cannot delete self

### 5. Invitation Security
- Cryptographically secure tokens (SecureRandom)
- Single use enforcement
- Expiry enforcement
- Revocation support

### 6. Input Validation
- All settings validated by type
- URLs validated for format
- Numeric ranges enforced
- OIDC discovery URL tested before save

---

## Implementation Order

### Phase 1A - Foundation (Week 1)
1. Database migration with all tables
2. `Role` enum and model updates
3. Admin email check in `UserService.registerUser()`
4. Admin email check in `OidcAuthenticationService`
5. `SystemSettingsService` with env fallback pattern
6. `SystemSettingsRepository` and entity

### Phase 1B - Core Settings (Week 2)
7. Authentication settings integration
8. Geocoding settings integration (with encryption)
9. GPS processing settings
10. Import settings
11. System performance settings
12. `AdminSettingsResource` with full CRUD

### Phase 1C - User Management (Week 2-3)
13. `AdminUserService` with all operations
14. `AdminUserResource` endpoints
15. User cascade delete implementation
16. Password reset functionality

### Phase 1D - Invitations (Week 3)
17. `UserInvitationEntity` and repository
18. `UserInvitationService`
19. `AdminInvitationResource`
20. Public invitation registration endpoint
21. Integration with registration flow

### Phase 1E - OIDC Management (Week 3-4)
22. `OidcProviderEntity` and repository
23. `OidcProviderManagementService`
24. Refactor `OidcProviderService` for hybrid loading
25. `AdminOidcProviderResource`
26. Secret encryption integration

### Phase 1F - Audit & Logging (Week 4)
27. `AuditLogEntity` and repository
28. `AuditLogService`
29. Integration with all admin services
30. `AdminAuditResource`

### Phase 1G - Frontend (Week 4-5)
31. Admin routes and guards
32. Auth store `isAdmin` property
33. Admin navigation
34. Dashboard page
35. Settings pages (all tabs)
36. User management pages
37. OIDC provider management
38. Invitations page
39. Audit log viewer
40. Invitation registration page

---

## Files to Create

### Backend - New Package Structure

```
org/github/tess1o/geopulse/admin/
├── model/
│   ├── Role.java                          # Enum: USER, ADMIN
│   ├── SystemSettingsEntity.java
│   ├── OidcProviderEntity.java
│   ├── UserInvitationEntity.java
│   └── AuditLogEntity.java
├── repository/
│   ├── SystemSettingsRepository.java
│   ├── OidcProviderRepository.java
│   ├── UserInvitationRepository.java
│   └── AuditLogRepository.java
├── service/
│   ├── SystemSettingsService.java         # Settings with env fallback
│   ├── AdminUserService.java              # User management operations
│   ├── UserInvitationService.java         # Invitation lifecycle
│   ├── OidcProviderManagementService.java # Provider CRUD
│   └── AuditLogService.java               # Audit logging
├── rest/
│   ├── AdminSettingsResource.java
│   ├── AdminUserResource.java
│   ├── AdminInvitationResource.java
│   ├── AdminOidcProviderResource.java
│   └── AdminAuditResource.java
└── dto/
    ├── SystemSettingResponse.java
    ├── UpdateSettingRequest.java
    ├── UserListResponse.java
    ├── UserDetailsResponse.java
    ├── UpdateUserStatusRequest.java
    ├── UpdateUserRoleRequest.java
    ├── InvitationResponse.java
    ├── CreateInvitationResponse.java
    ├── InvitationRegisterRequest.java
    ├── OidcProviderResponse.java
    ├── CreateOidcProviderRequest.java
    ├── UpdateOidcProviderRequest.java
    ├── TestOidcProviderResponse.java
    └── AuditLogResponse.java
```

### Backend - Modified Files

| File | Changes |
|------|---------|
| `UserEntity.java` | Change `role` from String to `Role` enum |
| `UserService.java` | Add admin email check in `registerUser()` |
| `OidcAuthenticationService.java` | Add admin email check during OIDC registration/login |
| `OidcProviderService.java` | Refactor for hybrid env/DB loading |
| `GeocodingConfig.java` | Use `SystemSettingsService` for values |
| `AuthConfigurationService.java` | Use `SystemSettingsService` for values |
| `application.properties` | Add `GEOPULSE_ADMIN_EMAIL` property |
| `UserMapper.java` | Map Role enum |
| `UserResponse.java` | Include role in response |

### Frontend - New Files

```
src/views/app/admin/
├── AdminDashboardPage.vue
├── AdminSettingsPage.vue
├── AdminUsersPage.vue
├── AdminUserDetailsPage.vue
├── AdminOidcProvidersPage.vue
├── AdminInvitationsPage.vue
└── AdminAuditLogPage.vue

src/views/
└── InvitationRegisterPage.vue

src/components/admin/
├── SettingItem.vue
├── SettingsSection.vue
├── SecretInput.vue
├── UserTable.vue
├── UserDetailsCard.vue
├── OidcProviderTable.vue
├── OidcProviderDialog.vue
├── InvitationTable.vue
├── CreateInvitationDialog.vue
├── AuditLogTable.vue
└── AuditDetailsDialog.vue

src/services/
└── adminService.js              # API calls for admin endpoints
```

### Frontend - Modified Files

| File | Changes |
|------|---------|
| `router/index.js` | Add admin routes, requireAdmin guard |
| `stores/auth.js` | Add `isAdmin` computed property |
| `App.vue` or layout | Add admin navigation section |
| Sidebar component | Conditionally show admin menu |

### Database

```
src/main/resources/db/migration/
└── V19.0.0__Add_admin_panel_support.sql
```

---

## Response DTOs

### SystemSettingResponse
```json
{
  "key": "geocoding.primary-provider",
  "value": "nominatim",
  "valueType": "STRING",
  "category": "geocoding",
  "description": "Primary geocoding provider",
  "isDefault": true,
  "defaultValue": "nominatim",
  "updatedAt": null,
  "updatedBy": null
}
```

### OidcProviderResponse
```json
{
  "name": "google",
  "displayName": "Google",
  "enabled": true,
  "clientId": "xxx.apps.googleusercontent.com",
  "hasClientSecret": true,
  "discoveryUrl": "https://accounts.google.com/.well-known/openid-configuration",
  "icon": "pi pi-google",
  "source": "ENVIRONMENT",
  "metadataValid": true,
  "metadataCachedAt": "2024-03-20T10:30:00Z"
}
```

### UserListResponse
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "fullName": "John Doe",
      "role": "USER",
      "isActive": true,
      "createdAt": "2024-01-15T10:30:00Z",
      "lastLoginAt": "2024-03-20T14:22:00Z",
      "gpsPointsCount": 15420,
      "linkedOidcProviders": ["google"]
    }
  ],
  "totalElements": 150,
  "totalPages": 15,
  "page": 0,
  "size": 10
}
```

### InvitationResponse
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "token": "abc123...",
  "link": "https://geopulse.example.com/register/invite/abc123...",
  "createdBy": {
    "id": "...",
    "email": "admin@example.com",
    "fullName": "Admin User"
  },
  "createdAt": "2024-03-20T10:00:00Z",
  "expiresAt": "2024-03-27T10:00:00Z",
  "status": "PENDING",
  "usedAt": null,
  "usedBy": null
}
```

### AuditLogResponse
```json
{
  "id": 12345,
  "timestamp": "2024-03-20T14:30:00Z",
  "adminUser": {
    "id": "...",
    "email": "admin@example.com",
    "fullName": "Admin User"
  },
  "actionType": "SETTING_CHANGED",
  "targetType": "SETTING",
  "targetId": "geocoding.primary-provider",
  "details": {
    "oldValue": "nominatim",
    "newValue": "googlemaps"
  },
  "ipAddress": "192.168.1.100"
}
```

---

## Testing Considerations

### Unit Tests
- `SystemSettingsService` - env fallback logic
- `OidcProviderManagementService` - hybrid loading
- `UserInvitationService` - lifecycle validation
- `AuditLogService` - event logging
- Encryption/decryption of secrets

### Integration Tests
- Admin role promotion flow
- Settings CRUD with DB persistence
- OIDC provider create/update/delete
- User invitation registration flow
- Cascade delete verification
- Audit log capture

### E2E Tests
- Admin login and navigation
- Settings modification workflow
- User management operations
- OIDC provider configuration
- Invitation creation and usage

---

## Future Enhancements (Beyond Phase 2)

1. **Email Notifications**
   - Invitation emails
   - Password reset emails
   - Admin alerts

2. **User Quotas**
   - Max GPS points per user
   - Storage limits
   - API rate limits

3. **Bulk Operations**
   - Bulk user import
   - Bulk enable/disable
   - Bulk delete

4. **Dashboard Widgets**
   - Real-time stats
   - Charts and graphs
   - System health monitoring

5. **Export/Import Settings**
   - Export all settings to JSON
   - Import settings from backup
   - Settings migration between instances

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2024-03-20 | Claude | Initial specification |
