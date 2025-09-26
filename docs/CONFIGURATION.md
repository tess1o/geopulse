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

| Property                                            | Default    | Description                                                                       |
|-----------------------------------------------------|------------|-----------------------------------------------------------------------------------|
| `geopulse.timeline.staypoint.radius_meters`         | `50`       | Radius (in meters) used for stay point detection and clustering                   |
| `geopulse.timeline.staypoint.min_duration_minutes`  | `7`        | Minimum duration (in minutes) for a stay point to be confirmed                    |
| `geopulse.timeline.staypoint.use_velocity_accuracy` | `true`     | Whether to consider velocity accuracy in filtering                                |
| `geopulse.timeline.staypoint.velocity.threshold`    | `2.5`      | Velocity threshold (m/s) below which movement is considered a staypoint           |
| `geopulse.timeline.staypoint.accuracy.threshold`    | `60.0`     | Maximum GPS accuracy (meters) for staypoint detection                             |
| `geopulse.timeline.staypoint.min_accuracy_ratio`    | `0.5`      | Minimum accuracy ratio required for staypoint consideration                       |

### Trip Classification

Controls how movement is classified into different transportation modes based on speed analysis. *Users can customize these settings individually.*

| Property                                          | Default  | Description                                                                                                |
|---------------------------------------------------|----------|------------------------------------------------------------------------------------------------------------|
| `geopulse.timeline.trip.detection.algorithm`     | `single` | Algorithm used for trip detection. Possible values: `single` and `multiple`                               |
| `geopulse.timeline.travel_classification.walking.max_avg_speed` | `6.0`    | Maximum sustained speed (km/h) for walking classification. Trips above this are classified as non-walking |
| `geopulse.timeline.travel_classification.walking.max_max_speed` | `8.0`    | Maximum instantaneous speed (km/h) for walking trips. Brief bursts above this reclassify the entire trip  |
| `geopulse.timeline.travel_classification.car.min_avg_speed`     | `8.0`    | Minimum sustained speed (km/h) required for car classification. Trips below this won't be classified as driving |
| `geopulse.timeline.travel_classification.car.min_max_speed`     | `15.0`   | Minimum peak speed (km/h) required for car classification. Trips never reaching this speed won't be classified as driving |
| `geopulse.timeline.travel_classification.short_distance_km`     | `1.0`    | Distance threshold (km) for applying relaxed walking speed detection to account for GPS inaccuracies in short trips |

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

### OIDC Authentication

GeoPulse supports OpenID Connect (OIDC) authentication for single sign-on with popular providers like Google, Microsoft, Keycloak, Auth0, and many others. All OIDC providers are configured using environment variables with a unified pattern.

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

| Property | Description | Example |
|----------|-------------|---------|
| `ENABLED` | Enable/disable the provider | `true` or `false` |
| `NAME` | Display name shown in UI | `Google`, `Company SSO`, etc. |
| `CLIENT_ID` | OAuth 2.0 client ID | Provider-specific client ID |
| `CLIENT_SECRET` | OAuth 2.0 client secret | Provider-specific secret |
| `DISCOVERY_URL` | OIDC discovery endpoint | `https://provider.com/.well-known/openid-configuration` |

#### Optional Properties

| Property | Description | Example |
|----------|-------------|---------|
| `ICON` | CSS icon class for UI | `pi pi-google`, `pi pi-microsoft` |

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

The system provides smart icon detection for common providers. If no icon is specified, it will automatically detect icons based on the provider name:

| Provider Pattern | Auto-detected Icon |
|------------------|-------------------|
| `google` | `pi pi-google` |
| `microsoft`, `azure` | `pi pi-microsoft` |
| `keycloak` | `pi pi-key` |
| `auth0`, `okta` | `pi pi-shield` |
| `gitlab` | `pi pi-code` |
| `github` | `pi pi-github` |
| `pocketid`, `pocket` | `pi pi-id-card` |
| `authentik` | `pi pi-lock` |
| `discord` | `pi pi-discord` |
| `facebook`, `meta` | `pi pi-facebook` |
| `twitter`, `x.com` | `pi pi-twitter` |
| `linkedin` | `pi pi-linkedin` |
| `apple` | `pi pi-apple` |
| `amazon`, `aws` | `pi pi-amazon` |
| Custom/Unknown | `pi pi-sign-in` |

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

### Available Providers

| Provider        | Free Tier | API Key Required | Rate Limits                 |
|-----------------|-----------|------------------|-----------------------------|
| **Nominatim**   | Yes       | No               | 1 request/second            |
| **Google Maps** | Limited   | Yes              | 40,000 requests/month free  |
| **Mapbox**      | Limited   | Yes              | 100,000 requests/month free |

### Geocoding Configuration

| Property                                | Default     | Description                                                     |
|-----------------------------------------|-------------|-----------------------------------------------------------------|
| `geocoding.provider.primary`            | `nominatim` | Primary geocoding service (`nominatim`, `googlemaps`, `mapbox`) |
| `geocoding.provider.fallback`           | _(empty)_   | Fallback service if primary fails                               |
| `geocoding.provider.nominatim.enabled`  | `true`      | Enable Nominatim geocoding service                              |
| `geocoding.provider.googlemaps.enabled` | `false`     | Enable Google Maps geocoding service                            |
| `geocoding.provider.mapbox.enabled`     | `false`     | Enable Mapbox geocoding service                                 |
| `geocoding.googlemaps.api-key`          | _(empty)_   | Google Maps API key (required for Google Maps)                  |
| `geocoding.mapbox.access-token`         | _(empty)_   | Mapbox access token (required for Mapbox)                       |

### Service Setup

**Nominatim (Default)**

- No API key required
- Uses OpenStreetMap data
- Rate limited to 1 request per second
- Best for privacy-conscious users

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

### AI Chat Configuration

GeoPulse includes an AI chat assistant that can analyze your location data and provide intelligent insights.

#### AI Encryption Key

The AI encryption key is automatically generated during the initial setup process and stored securely in the keys directory. For advanced users who need to customize the key location:

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

### Daily Timeline Processing

Controls batch processing of timeline generation. **Applies to all users.**

| Property                                            | Default | Description                                        |
|-----------------------------------------------------|---------|----------------------------------------------------|
| `geopulse.timeline.daily-processing.enabled`        | `true`  | Whether daily batch timeline processing is enabled |
| `geopulse.timeline.daily-processing.batch-size`     | `20`    | Number of users processed in each batch            |
| `geopulse.timeline.daily-processing.batch-delay-ms` | `1000`  | Delay (milliseconds) between processing each batch |

### GPS Data Processing

Controls GPS point duplicate detection and processing. **Applies to all users.**

| Property                                                           | Default | Description                                                       |
|--------------------------------------------------------------------|---------|-------------------------------------------------------------------|
| `geopulse.gps.duplicate-detection.location-time-threshold-minutes` | `2`     | Time threshold (minutes) for considering GPS points as duplicates |

### Environment Variables Reference

```bash
# System-Wide Settings (apply to all users)

# Favorites Configuration
GEOPULSE_FAVORITES_MAX_DISTANCE_FROM_POINT=75
GEOPULSE_FAVORITES_MAX_DISTANCE_FROM_AREA=15

# GPS Data Processing
GEOPULSE_GPS_DUPLICATE_DETECTION_LOCATION_TIME_THRESHOLD_MINUTES=2
```

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