# Reverse Geocoding

GeoPulse uses geocoding services to convert GPS coordinates into human-readable addresses and location names. You can
configure multiple providers with automatic fallback support. **This configuration applies to all users.**

## Overall Geocoding Configuration

| Property                      | Default | Description                                                                                                                                                            |
|-------------------------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_GEOCODING_DELAY_MS` | `1000`  | A delay in milliseconds between sending requests to geocoding provider.<br/> Nomatim's default rate limit is 1req/s. For selfhosted solutions change it to lower value |

## Available Providers

| Provider        | Free Tier                   | API Key Required | Rate Limits                 |
|-----------------|-----------------------------|------------------|-----------------------------|
| **Nominatim**   | Yes                         | No               | 1 request/second            |
| **Photon**      | Yes                         | No               | Unknown                     |
| **Google Maps** | Limited                     | Yes              | 40,000 requests/month free  |
| **Mapbox**      | Limited, see ToS note below | Yes              | 100,000 requests/month free |

## Geocoding Configuration

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

## Service Setup

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

> ⚖️Terms of Service & Free Tier Notice for Mapbox
>
> Mapbox usage in this app is optional and requires your own access token.
>
> By providing a Mapbox token, you agree to the Mapbox Terms of Service and are responsible for complying with them,
> including any usage, caching, or data-storage restrictions.
>
> The Mapbox free tier includes a limited number of monthly requests and is intended for development and light personal
> use.
>
> If you exceed those limits or need to store geocoding results long-term — for example, saving reverse-geocoded
> addresses in your database (which this app does by default) — you may need a **paid** or **commercial** plan that
> supports permanent data storage.

# Configuration

## Docker / Docker Compose

```bash
# Geocoding Service Configuration
GEOPULSE_GEOCODING_PRIMARY_PROVIDER=nominatim
GEOPULSE_GEOCODING_FALLBACK_PROVIDER=googlemaps

# Service Enablement
GEOPULSE_GEOCODING_NOMINATIM_ENABLED=true
GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED=true
GEOPULSE_GEOCODING_MAPBOX_ENABLED=false

# Custom URLs
GEOPULSE_GEOCODING_NOMINATIM_URL=https://nominatim.openstreetmap.org
GEOPULSE_GEOCODING_PHOTON_URL=https://photon.komoot.io

# API Credentials (if using paid services)
GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY=your_google_api_key_here
GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN=your_mapbox_token_here

#Delay between requests to geocoding provider
GEOPULSE_GEOCODING_DELAY_MS=1000
```

## Kubernetes / Helm

Configure geocoding in `values.yaml`:

```yaml
config:
  geocoding:
    primaryProvider: "nominatim"
    fallbackProvider: "googlemaps"
    delayMs: 1000
    nominatim:
      enabled: true
      url: "https://nominatim.openstreetmap.org"
    googleMaps:
      enabled: true
      apiKey: "your-api-key"  # Stored as Kubernetes Secret
    mapbox:
      enabled: false
      accessToken: ""
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-configuration-guide#geocoding-configuration).
