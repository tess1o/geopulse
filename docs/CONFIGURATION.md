# GeoPulse Configuration

## 🔧 Advanced Configuration

GeoPulse provides extensive configuration options to fine-tune timeline generation and GPS data processing. There are
two types of configurations:

- **🔧 User-Customizable Settings**: Timeline processing settings that can be customized per-user through the web
  interface. Environment variables set system-wide defaults, but users can override these in their personal settings.
- **⚙️ System-Wide Settings**: Application settings that apply to all users and cannot be customized individually (
  geocoding, favorites, processing, etc.).

## 🔧 User-Customizable Timeline Settings

*These settings can be overridden per-user through the web interface. Environment variables set the default values for
new users.*

### Staypoint Detection

Controls how GeoPulse identifies when you're stationary at a location. *Users can customize these settings
individually.*

| Property                                            | Default | Description                                                             |
|-----------------------------------------------------|---------|-------------------------------------------------------------------------|
| `geopulse.timeline.staypoint.radius_meters`         | `50`    | Radius (in meters) used for stay point detection and clustering         |
| `geopulse.timeline.staypoint.min_duration_minutes`  | `7`     | Minimum duration (in minutes) for a stay point to be confirmed          |
| `geopulse.timeline.staypoint.use_velocity_accuracy` | `true`  | Whether to consider velocity accuracy in filtering                      |
| `geopulse.timeline.staypoint.velocity.threshold`    | `2.5`   | Velocity threshold (m/s) below which movement is considered a staypoint |
| `geopulse.timeline.staypoint.accuracy.threshold`    | `60.0`  | Maximum GPS accuracy (meters) for staypoint detection                   |
| `geopulse.timeline.staypoint.min_accuracy_ratio`    | `0.5`   | Minimum accuracy ratio required for staypoint consideration             |

### Trip Classification

Controls how movement is classified into different transportation modes based on speed analysis. *Users can customize
these settings individually.*

| Property                                                        | Default  | Description                                                                                                                 |
|-----------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------|
| `geopulse.timeline.trip.detection.algorithm`                    | `single` | Algorithm used for trip detection. Possible values: `single` and `multiple`                                                 |
| `geopulse.timeline.trip.arrival.min_duration_seconds`           | `90`     | Minimum duration (seconds) for arrival detection. GPS points must be clustered and slow for this duration to detect arrival |
| `geopulse.timeline.trip.sustained_stop.min_duration_seconds`    | `60`     | Minimum duration (seconds) for sustained stop detection. Filters out brief stops like traffic lights                        |
| `geopulse.timeline.travel_classification.walking.max_avg_speed` | `6.0`    | Maximum sustained speed (km/h) for walking classification. Trips above this are classified as non-walking                   |
| `geopulse.timeline.travel_classification.walking.max_max_speed` | `8.0`    | Maximum instantaneous speed (km/h) for walking trips. Brief bursts above this reclassify the entire trip                    |
| `geopulse.timeline.travel_classification.car.min_avg_speed`     | `8.0`    | Minimum sustained speed (km/h) required for car classification. Trips below this won't be classified as driving             |
| `geopulse.timeline.travel_classification.car.min_max_speed`     | `15.0`   | Minimum peak speed (km/h) required for car classification. Trips never reaching this speed won't be classified as driving   |
| `geopulse.timeline.travel_classification.short_distance_km`     | `1.0`    | Distance threshold (km) for applying relaxed walking speed detection to account for GPS inaccuracies in short trips         |

### Staypoint Merging

Controls how nearby staypoints are combined to reduce timeline fragmentation. *Users can customize these settings
individually.*

| Property                                                 | Default | Description                                       |
|----------------------------------------------------------|---------|---------------------------------------------------|
| `geopulse.timeline.staypoint.merge.enabled`              | `true`  | Whether to merge nearby staypoints                |
| `geopulse.timeline.staypoint.merge.max_distance_meters`  | `400`   | Maximum distance (meters) for merging staypoints  |
| `geopulse.timeline.staypoint.merge.max_time_gap_minutes` | `15`    | Maximum time gap (minutes) for merging staypoints |

### GPS Path Simplification

Optimizes trip paths by reducing GPS points while preserving route accuracy. *Users can customize these settings
individually.*

| Property                                           | Default | Description                                                     |
|----------------------------------------------------|---------|-----------------------------------------------------------------|
| `geopulse.timeline.path.simplification.enabled`    | `true`  | Whether GPS path simplification is enabled                      |
| `geopulse.timeline.path.simplification.tolerance`  | `15.0`  | Base tolerance (meters) for path simplification                 |
| `geopulse.timeline.path.simplification.max_points` | `100`   | Maximum GPS points to retain in simplified paths (0 = no limit) |
| `geopulse.timeline.path.simplification.adaptive`   | `true`  | Enable adaptive simplification based on trip characteristics    |

### Data Gap Detection

Detects and handles gaps in GPS data that might indicate missing location information. *Users can customize these
settings individually.*

| Property                                          | Default | Description                                                     |
|---------------------------------------------------|---------|-----------------------------------------------------------------|
| `geopulse.timeline.data_gap.threshold_seconds`    | `10800` | Time threshold (seconds) to consider a gap in data (3 hours)    |
| `geopulse.timeline.data_gap.min_duration_seconds` | `1800`  | Minimum duration (seconds) for a gap to be significant (30 min) |

## ⚙️ System-Wide Application Settings

*These settings apply to all users and cannot be customized individually.*

### Authentication Configuration

#### Authentication Cookies

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

### Disabling Sign Up via email/password

If you do not want to allow users to sign up via email/password, you can disable it by setting the following environment
variable:
`GEOPULSE_AUTH_SIGN_UP_ENABLED=false`. By default, sign up is enabled.

### OIDC Authentication

GeoPulse supports OpenID Connect (OIDC) authentication for single sign-on with popular providers like Google, Microsoft,
Keycloak, Auth0, and many others. All OIDC providers are configured using environment variables with a unified pattern.

#### ⚠️ Prerequisites

**OIDC must be globally enabled first:**

```bash
# REQUIRED: Enable OIDC authentication globally
GEOPULSE_OIDC_ENABLED=true
```

Without this setting, all provider configurations will be ignored and OIDC authentication will not be available.

#### Configuration Pattern

All OIDC providers use the same environment variable pattern:

```bash
GEOPULSE_OIDC_PROVIDER_{NAME}_{PROPERTY}=value
```

#### Required Properties

| Property        | Description                 | Example                                                 |
|-----------------|-----------------------------|---------------------------------------------------------|
| `ENABLED`       | Enable/disable the provider | `true` or `false`                                       |
| `NAME`          | Display name shown in UI    | `Google`, `Company SSO`, etc.                           |
| `CLIENT_ID`     | OAuth 2.0 client ID         | Provider-specific client ID                             |
| `CLIENT_SECRET` | OAuth 2.0 client secret     | Provider-specific secret                                |
| `DISCOVERY_URL` | OIDC discovery endpoint     | `https://provider.com/.well-known/openid-configuration` |

#### Optional Properties

| Property | Description           | Example                           |
|----------|-----------------------|-----------------------------------|
| `ICON`   | CSS icon class for UI | `pi pi-google`, `pi pi-microsoft` |

### OIDC Callback URL

For all OIDC providers, you must configure a callback URL. This is the URL that the provider will redirect to after
authentication. All providers should use the callback URL: `http://your-ip-address:port/oidc/callback` or
`https://geopulse.mydomain.com/oidc/callback`.

Additionally you might need to update `GEOPULSE_OIDC_CALLBACK_BASE_URL` environment variable to match your frontend
URL. By default it's set to `GEOPULSE_UI_URL` but in case if you have multiple domains you might need to change it to a
single one.

#### Supported Providers

The system supports any OIDC-compliant provider. Here are configuration examples for popular providers:

##### Google

```bash
GEOPULSE_OIDC_PROVIDER_GOOGLE_ENABLED=true
GEOPULSE_OIDC_PROVIDER_GOOGLE_NAME=Google
GEOPULSE_OIDC_PROVIDER_GOOGLE_CLIENT_ID=your-google-client-id
GEOPULSE_OIDC_PROVIDER_GOOGLE_CLIENT_SECRET=your-google-client-secret
GEOPULSE_OIDC_PROVIDER_GOOGLE_DISCOVERY_URL=https://accounts.google.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_GOOGLE_ICON=pi pi-google
```

##### Microsoft Azure AD

```bash
GEOPULSE_OIDC_PROVIDER_MICROSOFT_ENABLED=true
GEOPULSE_OIDC_PROVIDER_MICROSOFT_NAME=Microsoft
GEOPULSE_OIDC_PROVIDER_MICROSOFT_CLIENT_ID=your-azure-client-id
GEOPULSE_OIDC_PROVIDER_MICROSOFT_CLIENT_SECRET=your-azure-client-secret
GEOPULSE_OIDC_PROVIDER_MICROSOFT_DISCOVERY_URL=https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_MICROSOFT_ICON=pi pi-microsoft
```

##### Keycloak

```bash
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_ENABLED=true
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_NAME=Company SSO
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_CLIENT_ID=geopulse-client
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_CLIENT_SECRET=your-keycloak-secret
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_DISCOVERY_URL=https://keycloak.company.com/auth/realms/master/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_ICON=pi pi-key
```

##### Auth0

```bash
GEOPULSE_OIDC_PROVIDER_AUTH0_ENABLED=true
GEOPULSE_OIDC_PROVIDER_AUTH0_NAME=Auth0
GEOPULSE_OIDC_PROVIDER_AUTH0_CLIENT_ID=your-auth0-client-id
GEOPULSE_OIDC_PROVIDER_AUTH0_CLIENT_SECRET=your-auth0-client-secret
GEOPULSE_OIDC_PROVIDER_AUTH0_DISCOVERY_URL=https://your-domain.auth0.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_AUTH0_ICON=pi pi-shield
```

##### PocketID

```bash
GEOPULSE_OIDC_PROVIDER_POCKETID_ENABLED=true
GEOPULSE_OIDC_PROVIDER_POCKETID_NAME=PocketID
GEOPULSE_OIDC_PROVIDER_POCKETID_CLIENT_ID=your-pocketid-client-id
GEOPULSE_OIDC_PROVIDER_POCKETID_CLIENT_SECRET=your-pocketid-secret
GEOPULSE_OIDC_PROVIDER_POCKETID_DISCOVERY_URL=https://pocketid.example.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_POCKETID_ICON=pi pi-id-card
```

##### GitLab

```bash
GEOPULSE_OIDC_PROVIDER_GITLAB_ENABLED=true
GEOPULSE_OIDC_PROVIDER_GITLAB_NAME=GitLab
GEOPULSE_OIDC_PROVIDER_GITLAB_CLIENT_ID=your-gitlab-client-id
GEOPULSE_OIDC_PROVIDER_GITLAB_CLIENT_SECRET=your-gitlab-secret
GEOPULSE_OIDC_PROVIDER_GITLAB_DISCOVERY_URL=https://gitlab.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_GITLAB_ICON=pi pi-code
```

##### GitHub (via OIDC proxy)

```bash
GEOPULSE_OIDC_PROVIDER_GITHUB_ENABLED=true
GEOPULSE_OIDC_PROVIDER_GITHUB_NAME=GitHub
GEOPULSE_OIDC_PROVIDER_GITHUB_CLIENT_ID=your-github-client-id
GEOPULSE_OIDC_PROVIDER_GITHUB_CLIENT_SECRET=your-github-secret
GEOPULSE_OIDC_PROVIDER_GITHUB_DISCOVERY_URL=https://your-oidc-proxy.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_GITHUB_ICON=pi pi-github
```

#### Icon Support

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

#### Global OIDC Configuration

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
```

#### Multiple Providers

You can configure multiple providers simultaneously. Users will see all enabled providers as login options:

```bash
# Enable multiple providers
GEOPULSE_OIDC_PROVIDER_GOOGLE_ENABLED=true
GEOPULSE_OIDC_PROVIDER_MICROSOFT_ENABLED=true  
GEOPULSE_OIDC_PROVIDER_KEYCLOAK_ENABLED=true
GEOPULSE_OIDC_PROVIDER_AUTH0_ENABLED=true

# Each with their own configuration like name, client id, client secret, discovery url, etc...
```

#### Docker Compose Example

```yaml
services:
  geopulse-backend:
    environment:
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

#### Troubleshooting

**OIDC providers not showing on login page:**

- Verify `GEOPULSE_OIDC_ENABLED=true` is set
- Check that individual provider `_ENABLED=true` settings are configured
- Ensure all required properties (client-id, client-secret, discovery-url) are provided
- Check application logs for provider initialization errors

### Geocoding Services

GeoPulse uses geocoding services to convert GPS coordinates into human-readable addresses and location names. You can
configure multiple providers with automatic fallback support. **This configuration applies to all users.**

#### Overall Geocoding Configuration

| Property                      | Default | Description                                                                                                                                                            |
|-------------------------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_GEOCODING_DELAY_MS` | `1000`  | A delay in milliseconds between sending requests to geocoding provider.<br/> Nomatim's default rate limit is 1req/s. For selfhosted solutions change it to lower value |

### Available Providers

| Provider        | Free Tier | API Key Required | Rate Limits                 |
|-----------------|-----------|------------------|-----------------------------|
| **Nominatim**   | Yes       | No               | 1 request/second            |
| **Photon**      | Yes       | No               | Unknown                     |
| **Google Maps** | Limited   | Yes              | 40,000 requests/month free  |
| **Mapbox**      | Limited   | Yes              | 100,000 requests/month free |

### Geocoding Configuration

| Property                                 | Default                               | Description                                                               |
|------------------------------------------|---------------------------------------|---------------------------------------------------------------------------|
| `GEOPULSE_GEOCODING_PRIMARY_PROVIDER`    | `nominatim`                           | Primary geocoding service (`nominatim`, `googlemaps`, `mapbox`, `photon`) |
| `GEOPULSE_GEOCODING_FALLBACK_PROVIDER`   | _(empty)_                             | Fallback service if primary fails                                         |
| `GEOPULSE_GEOCODING_NOMINATIM_ENABLED`   | `true`                                | Enable Nominatim geocoding service                                        |
| `GEOPULSE_GEOCODING_NOMINATIM_URL`       | `https://nominatim.openstreetmap.org` | Nominatim url                                                             |
| `GEOPULSE_GEOCODING_PHOTON_ENABLED`      | `false`                               | Enable Photon geocoding service                                           |
| `GEOPULSE_GEOCODING_PHOTON_URL`          | `https://photon.komoot.io`            | Photon url                                                                |                                                                 |
| `GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED` | `false`                               | Enable Google Maps geocoding service                                      |
| `GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY` | _(empty)_                             | Google Maps API key (required for Google Maps)                            |
| `GEOPULSE_GEOCODING_MAPBOX_ENABLED`      | `false`                               | Enable Mapbox geocoding service                                           |
| `GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN` | _(empty)_                             | Mapbox access token (required for Mapbox)                                 |

### Service Setup

**Nominatim (Default)**

- No API key required
- Uses OpenStreetMap data
- Rate limited to 1 request per second
- Best for privacy-conscious users
- Possible to use self-hosted version

**Photon**

- No API key required
- Rate limited to 2 request per second
- Possible to use self-hosted version

**Google Maps**

1. Get API key from [Google Cloud Console](https://console.cloud.google.com/)
2. Enable Geocoding API
3. Set billing account (free tier available)

**Mapbox**

1. Create account at [Mapbox](https://account.mapbox.com/)
2. Generate access token
3. Monitor usage in dashboard

### Geocoding Environment Variables

```bash
# Geocoding Service Configuration
GEOPULSE_GEOCODING_PRIMARY_PROVIDER=nominatim
GEOPULSE_GEOCODING_FALLBACK_PROVIDER=googlemaps

# Service Enablement
GEOPULSE_GEOCODING_NOMINATIM_ENABLED=true
GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED=true
GEOPULSE_GEOCODING_MAPBOX_ENABLED=false

# API Credentials (if using paid services)
GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY=your_google_api_key_here
GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN=your_mapbox_token_here
```

**Recommended Setup:**

- **Privacy-focused**: Use Nominatim only
- **High-volume**: Use Google Maps or Mapbox with Nominatim as fallback
- **Reliability**: Configure both primary and fallback services

### Custom Map Tiles

GeoPulse allows you to customize the map appearance by using tiles from different providers. By default, GeoPulse uses
OpenStreetMap tiles, but you can switch to satellite imagery, different map styles, or any custom tile provider that
supports the standard XYZ tile format.

#### Why Use Custom Map Tiles?

- **Satellite imagery** - See actual satellite photos of your locations
- **Different map styles** - Choose from hundreds of map designs (dark mode, terrain, outdoors, etc.)
- **Personal preference** - Pick a style that works best for you

#### Setting Up Custom Tiles (MapTiler Example)

MapTiler offers a free tier that's perfect for personal use. Here's how to set it up:

**Step 1: Create a Free MapTiler Account**

1. Go to [MapTiler.com](https://www.maptiler.com/) and sign up for a free account
2. The free tier includes 100,000 tile requests per month - more than enough for regular personal use

**Step 2: Choose Your Map Style**

1. Navigate to [MapTiler Maps](https://cloud.maptiler.com/maps/)
2. Browse through the available map styles:
    - **Satellite** - Aerial imagery
    - **Streets** - Classic street map
    - **Outdoor** - Topographic style
    - **Hybrid** - Satellite with street labels
    - And many more!
3. Click on the map style you want to use

**Step 3: Get Your Tile URL**

1. On the map style page, scroll down to find the **"Raster tiles"** section
2. Look for the **XYZ tiles** URL format
3. Copy the URL - it should look like this:
   ```
   https://api.maptiler.com/maps/satellite/{z}/{x}/{y}.jpg?key=YOUR_PERSONAL_KEY_HERE
   ```
4. **Important:** Make sure the URL contains `{z}/{x}/{y}` placeholders - these are required!

**Step 4: Configure GeoPulse**

1. In GeoPulse, go to **Profile** → **Custom Map Tile URL**
2. Paste your MapTiler tile URL (with your API key included)
3. Click **Save**
4. Navigate to any map page - your new tiles will load automatically!

**Step 5: Switching Back to Default**

To return to the default OpenStreetMap tiles, simply:

1. Go to **Profile** → **Custom Map Tile URL**
2. Clear the field (leave it empty)
3. Click **Save**

#### Security Note

⚠️ **Never share your tile URL with anyone!** The URL contains your personal API key. If someone else uses your key, it
will count against your quota and could exhaust your free tier limits.

#### Supported Tile Providers

GeoPulse works with any tile provider that supports the standard XYZ tile format. Some popular options:

- **[MapTiler](https://www.maptiler.com/)** - Free tier available, satellite imagery, many styles
- **[Mapbox](https://www.mapbox.com/)** - Free tier available, beautiful styles
- **[Thunderforest](https://www.thunderforest.com/)** - Specialized maps (cycling, transport, outdoors)
- **[Stadia Maps](https://stadiamaps.com/)** - Free tier for non-commercial use
- **ESRI World Imagery** - Free satellite imagery (no API key required)
  ```
  https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}
  ```

#### Troubleshooting

**Tiles not loading after changing URL:**

- Hard refresh your browser (Ctrl+Shift+R or Cmd+Shift+R) to clear cached tiles
- Verify the URL contains `{z}`, `{x}`, and `{y}` placeholders
- Check that your API key is valid and hasn't expired

**Mixed tile styles appearing:**

- This is a browser cache issue - hard refresh to clear cached tiles
- The version parameter in the URL should prevent this, but may require a cache clear on first use

**Tiles load slowly:**

- Free tier providers may have rate limits
- Consider using a provider with better free tier limits
- Check your internet connection

### AI Chat Configuration

GeoPulse includes an AI chat assistant that can analyze your location data and provide intelligent insights.

#### AI Encryption Key

The AI encryption key is automatically generated during the initial setup process and stored securely in the keys
directory. For advanced users who need to customize the key location:

```bash
# Optional: Custom AI encryption key location (advanced users only)
GEOPULSE_AI_ENCRYPTION_KEY_LOCATION=file:/app/keys/ai-encryption-key.txt
```

#### AI Configuration

AI features are configured through the web interface and support any OpenAI-compatible API:

- API key and model selection
- Custom API URL (for OpenAI-compatible services) or leave empty for OpenAI
- AI assistant preferences and settings
- All AI settings are encrypted using the automatically generated encryption key

**Supported AI Providers:**

- **OpenAI** (default) - Leave API URL empty to use OpenAI's official API
- **OpenAI-compatible services** - Specify custom API URL for services like:
    - Local AI deployments (Ollama, LocalAI, etc.)
    - Alternative providers with OpenAI-compatible APIs
    - Self-hosted LLM services

**Setup Process:**

1. Configure API key in Profile → AI Assistant
2. (Optional) Set custom API URL for OpenAI-compatible services
3. Select preferred AI model (GPT-3.5-turbo, GPT-4, or custom models)
4. Start asking questions about your location data

## Environment Variables Reference

### User-Customizable Timeline Settings

*Environment variables set defaults for new users. Each user can override these settings in their personal preferences.*

```bash
# Staypoint Detection
GEOPULSE_TIMELINE_STAYPOINT_RADIUS_METERS=50
GEOPULSE_TIMELINE_STAYPOINT_MIN_DURATION_MINUTES=7
GEOPULSE_TIMELINE_STAYPOINT_USE_VELOCITY_ACCURACY=true
GEOPULSE_TIMELINE_STAYPOINT_VELOCITY_THRESHOLD=2.5
GEOPULSE_TIMELINE_STAYPOINT_ACCURACY_THRESHOLD=60.0
GEOPULSE_TIMELINE_STAYPOINT_MIN_ACCURACY_RATIO=0.5

# Trip Detection
GEOPULSE_TIMELINE_TRIP_DETECTION_ALGORITHM=single
GEOPULSE_TIMELINE_TRIP_ARRIVAL_MIN_DURATION_SECONDS=90
GEOPULSE_TIMELINE_TRIP_SUSTAINED_STOP_MIN_DURATION_SECONDS=60

# Travel Classification
GEOPULSE_TIMELINE_WALKING_MAX_AVG_SPEED=6.0
GEOPULSE_TIMELINE_WALKING_MAX_MAX_SPEED=8.0
GEOPULSE_TIMELINE_CAR_MIN_AVG_SPEED=8.0
GEOPULSE_TIMELINE_CAR_MIN_MAX_SPEED=15.0
GEOPULSE_TIMELINE_SHORT_DISTANCE_KM=1.0

# Staypoint Merging
GEOPULSE_TIMELINE_STAYPOINT_MERGE_ENABLED=true
GEOPULSE_TIMELINE_STAYPOINT_MERGE_MAX_DISTANCE_METERS=400
GEOPULSE_TIMELINE_STAYPOINT_MERGE_MAX_TIME_GAP_MINUTES=15

# GPS Path Simplification
GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_ENABLED=true
GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_TOLERANCE=15.0
GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_MAX_POINTS=100
GEOPULSE_TIMELINE_PATH_SIMPLIFICATION_ADAPTIVE=true
```

### Data Gap Detection

```bash
GEOPULSE_TIMELINE_DATA_GAP_THRESHOLD_SECONDS=10800
GEOPULSE_TIMELINE_DATA_GAP_MIN_DURATION_SECONDS=1800
```

### Favorites Configuration

Controls location matching for favorite places. **Applies to all users.**

| Property                                     | Default | Description                                                        |
|----------------------------------------------|---------|--------------------------------------------------------------------|
| `geopulse.favorites.max-distance-from-point` | `50`    | Maximum distance (meters) to match a GPS point to a favorite place |
| `geopulse.favorites.max-distance-from-area`  | `15`    | Maximum distance (meters) to match a GPS point to a favorite area  |

### Realtime Timeline Job

Controls processing of timeline generation. **Applies to all users.**

| Property                               | Default | Description                                                                           |
|----------------------------------------|---------|---------------------------------------------------------------------------------------|
| `GEOPULSE_TIMELINE_PROCESSING_THREADS` | `2`     | Number of threads used in timeline job processing. Increase it if you have many users |
| `GEOPULSE_TIMELINE_JOB_INTERVAL`       | `5m`    | Execute timeline job each X minutes                                                   |
| `GEOPULSE_TIMELINE_JOB_DELAY`          | `1m`    | Initial delay (after server start) for timeline job processing                        |

### GPS Data Processing

Controls GPS point duplicate detection and processing. **Applies to all users.**

| Property                                                           | Default | Description                                                                                   |
|--------------------------------------------------------------------|---------|-----------------------------------------------------------------------------------------------|
| `geopulse.gps.duplicate-detection.location-time-threshold-minutes` | `2`     | Time threshold (minutes) for considering GPS points as duplicates (with the same coordinates) |

### Environment Variables Reference

```bash
# System-Wide Settings (apply to all users)

# Favorites Configuration
GEOPULSE_FAVORITES_MAX_DISTANCE_FROM_POINT=75
GEOPULSE_FAVORITES_MAX_DISTANCE_FROM_AREA=15

# GPS Data Processing
GEOPULSE_GPS_DUPLICATE_DETECTION_LOCATION_TIME_THRESHOLD_MINUTES=2

# Realtime Timeline Job
GEOPULSE_TIMELINE_PROCESSING_THREADS=2
GEOPULSE_TIMELINE_JOB_INTERVAL=5m
GEOPULSE_TIMELINE_JOB_DELAY=1m
```

## GeoPulse Frontend Configuration

To increase max upload file size from default 200MB change the following environment variable: `CLIENT_MAX_BODY_SIZE`.

`OSM_RESOLVER` variable defines which DNS servers Nginx should use to resolve the OpenStreetMap tile subdomains (
a.tile.openstreetmap.org, b.tile.openstreetmap.org, c.tile.openstreetmap.org). Default value `127.0.0.11 8.8.8.8`.
If you want to use your own DNS servers, you can set it to your DNS servers, otherwise default value will be used.

## How Configuration Works

### 🔧 User-Customizable Timeline Settings

1. **Environment Variables**: Set system-wide default values for all new users (and existing users if they haven't
   overwritten these settings yet)
2. **Web Interface**: Users can customize these settings for their personal timeline generation
3. **User Overrides**: Once a user changes settings in the UI, their personal preferences are saved and will not be
   affected by future changes to environment variable defaults
4. **New Users**: Will inherit the current environment variable defaults when they first use the system

### ⚙️ System-Wide Application Settings

1. **Environment Variables Only**: These settings can only be configured via environment variables
2. **Global Application**: Apply to all users and cannot be customized individually
3. **Restart Required**: Changes require restarting the GeoPulse application (and docker container re-creation)

## Configuration Notes

- **Environment Variable Format**: Convert dots (`.`) to underscores (`_`) and use uppercase letters
- **Restart Required**: Changes to environment variables require re-creating the GeoPulse containers
- **User Preferences**: Changes made in the web interface are stored per-user and persist even if environment defaults
  change
- **Validation**: Invalid values will fall back to defaults with warnings in the logs

**For Higher Accuracy** (more sensitive detection):

- Decrease `geopulse.timeline.staypoint.velocity.threshold` (e.g., 3-5 m/s)
- Decrease `geopulse.timeline.staypoint.accuracy.threshold` (e.g., 30-40 meters)
- Decrease `geopulse.timeline.staypoint.radius_meters` (e.g., 25-30 meters)

**For Less Noise** (less sensitive detection):

- Increase `geopulse.timeline.staypoint.velocity.threshold` (e.g., 10-15 m/s)
- Increase `geopulse.timeline.staypoint.radius_meters` (e.g., 100-200 meters)
- Increase `geopulse.timeline.staypoint.min_duration_minutes` (e.g., 10-15 minutes)

**For Battery/Storage Optimization**:

- Enable path simplification with higher tolerance (20-30 meters)
- Set lower `geopulse.timeline.path.simplification.max_points` (50-75 points)
- Enable adaptive simplification