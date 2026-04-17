# Reverse Geocoding

GeoPulse uses geocoding services to convert GPS coordinates into human-readable addresses and location names. You can
configure multiple providers with automatic fallback support. **This configuration applies to all users.**

## Overall Geocoding Configuration

| Property                      | Default | Description                                                                                                                                                            |
|-------------------------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_GEOCODING_DELAY_MS` | `1000`  | A delay in milliseconds between sending requests to geocoding provider.<br/> Nomatim's default rate limit is 1req/s. For selfhosted solutions change it to lower value |

## Resilience Tuning

These settings tune retry and circuit breaker behavior for geocoding provider calls and reconciliation jobs.

| Property                                            | Default                               | Description                                                 |
|-----------------------------------------------------|---------------------------------------|-------------------------------------------------------------|
| `GEOPULSE_GEOCODING_RETRY_MAX_RETRIES`              | `5`                                   | Maximum retry attempts for provider calls                   |
| `GEOPULSE_GEOCODING_RETRY_DELAY_MS`                 | `1250`                                | Base delay between provider retries (milliseconds)          |
| `GEOPULSE_GEOCODING_RETRY_JITTER_MS`                | `250`                                 | Random jitter applied to retry delay (milliseconds)         |
| `GEOPULSE_GEOCODING_CB_FAILURE_RATIO`               | `0.7`                                 | Failure ratio that opens circuit breaker                    |
| `GEOPULSE_GEOCODING_CB_REQUEST_VOLUME`              | `10`                                  | Circuit breaker rolling window size                         |
| `GEOPULSE_GEOCODING_CB_DELAY_SECONDS`               | `20`                                  | Time circuit remains open before half-open                  |
| `GEOPULSE_GEOCODING_CB_SUCCESS_THRESHOLD`           | `2`                                   | Successful calls needed in half-open state to close breaker |
| `GEOPULSE_GEOCODING_RECONCILE_ITEM_MAX_ATTEMPTS`    | `4`                                   | Max attempts per item during reconciliation jobs            |
| `GEOPULSE_GEOCODING_RECONCILE_CIRCUIT_OPEN_WAIT_MS` | `20000`                               | Wait time before retry when circuit is open (milliseconds)  |
| `GEOPULSE_GEOCODING_RECONCILE_INTER_ITEM_DELAY_MS`  | `${GEOPULSE_GEOCODING_DELAY_MS:1000}` | Delay between reconciliation items (milliseconds)           |

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
| `GEOPULSE_GEOCODING_NOMINATIM_LANGUAGE`  | _(empty)_                             | Nominatim language preference (BCP 47: en-US, de, uk, ja). If not set, no Accept-Language header is sent |
| `GEOPULSE_GEOCODING_PHOTON_ENABLED`      | `false`                               | Enable Photon geocoding service                                           |
| `GEOPULSE_GEOCODING_PHOTON_URL`          | `https://photon.komoot.io`            | Photon url                                                                |
| `GEOPULSE_GEOCODING_PHOTON_LANGUAGE`     | _(empty)_                             | Photon language preference (BCP 47: en-US, de, uk, ja). If not set, no Accept-Language header is sent |
| `GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED` | `false`                               | Enable Google Maps geocoding service                                      |
| `GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY` | _(empty)_                             | Google Maps API key (required for Google Maps)                            |
| `GEOPULSE_GEOCODING_GOOGLE_MAPS_LANGUAGE` | _(empty)_                             | Google Maps language code for reverse geocoding responses (optional, query param `language`) |
| `GEOPULSE_GEOCODING_MAPBOX_ENABLED`      | `false`                               | Enable Mapbox geocoding service                                           |
| `GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN` | _(empty)_                             | Mapbox access token (required for Mapbox)                                 |

> **Provider switch behavior:** changing primary/fallback provider affects only new lookups.
> Existing cached geocoding records are reused until you run reconciliation from the Reverse Geocoding Management page.

## Service Setup

**Nominatim (Default)**

- No API key required
- Uses OpenStreetMap data
- Rate limited to 1 request per second
- Best for privacy-conscious users
- Possible to use self-hosted version
- Language can be configured via `GEOPULSE_GEOCODING_NOMINATIM_LANGUAGE` (e.g., "en-US", "de", "uk")

**Photon**

- No API key required
- Rate limited to 2 request per second
- Possible to use self-hosted version
- Language can be configured via `GEOPULSE_GEOCODING_PHOTON_LANGUAGE` (e.g., "en-US", "de", "uk")

**Google Maps**

1. Get API key from [Google Cloud Console](https://console.cloud.google.com/)
2. Enable Geocoding API
3. Set billing account (free tier available)
4. Optional: set `GEOPULSE_GEOCODING_GOOGLE_MAPS_LANGUAGE` to force localized reverse geocoding results (Google `language` query param)

Supported Google Maps language codes:

| Language Code | Language | Language Code | Language |
|---------------|----------|---------------|----------|
| `af` | Afrikaans | `ja` | Japanese |
| `sq` | Albanian | `kn` | Kannada |
| `am` | Amharic | `kk` | Kazakh |
| `ar` | Arabic | `km` | Khmer |
| `hy` | Armenian | `ko` | Korean |
| `az` | Azerbaijani | `ky` | Kyrgyz |
| `eu` | Basque | `lo` | Lao |
| `be` | Belarusian | `lv` | Latvian |
| `bn` | Bengali | `lt` | Lithuanian |
| `bs` | Bosnian | `mk` | Macedonian |
| `bg` | Bulgarian | `ms` | Malay |
| `my` | Burmese | `ml` | Malayalam |
| `ca` | Catalan | `mr` | Marathi |
| `zh` | Chinese | `mn` | Mongolian |
| `zh-CN` | Chinese (Simplified) | `ne` | Nepali |
| `zh-HK` | Chinese (Hong Kong) | `no` | Norwegian |
| `zh-TW` | Chinese (Traditional) | `pl` | Polish |
| `hr` | Croatian | `pt` | Portuguese |
| `cs` | Czech | `pt-BR` | Portuguese (Brazil) |
| `da` | Danish | `pt-PT` | Portuguese (Portugal) |
| `nl` | Dutch | `pa` | Punjabi |
| `en` | English | `ro` | Romanian |
| `en-AU` | English (Australian) | `ru` | Russian |
| `en-GB` | English (Great Britain) | `sr` | Serbian (Cyrillic) |
| `et` | Estonian | `sr-Latn` | Serbian (Latin script) |
| `fa` | Farsi | `si` | Sinhalese |
| `fi` | Finnish | `sk` | Slovak |
| `fil` | Filipino | `sl` | Slovenian |
| `fr` | French | `es` | Spanish |
| `fr-CA` | French (Canada) | `es-419` | Spanish (Latin America) |
| `gl` | Galician | `sw` | Swahili |
| `ka` | Georgian | `sv` | Swedish |
| `de` | German | `ta` | Tamil |
| `el` | Greek | `te` | Telugu |
| `gu` | Gujarati | `th` | Thai |
| `iw` | Hebrew | `tr` | Turkish |
| `hi` | Hindi | `uk` | Ukrainian |
| `hu` | Hungarian | `ur` | Urdu |
| `is` | Icelandic | `uz` | Uzbek |
| `id` | Indonesian | `vi` | Vietnamese |
| `it` | Italian | `zu` | Zulu |

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

# Language preferences (optional - if not set, providers return local language)
GEOPULSE_GEOCODING_NOMINATIM_LANGUAGE=en-US
GEOPULSE_GEOCODING_PHOTON_LANGUAGE=en-US
GEOPULSE_GEOCODING_GOOGLE_MAPS_LANGUAGE=en

# API Credentials (if using paid services)
GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY=your_google_api_key_here
GEOPULSE_GEOCODING_MAPBOX_ACCESS_TOKEN=your_mapbox_token_here

#Delay between requests to geocoding provider
GEOPULSE_GEOCODING_DELAY_MS=1000

# Resilience tuning (optional)
GEOPULSE_GEOCODING_RETRY_MAX_RETRIES=5
GEOPULSE_GEOCODING_RETRY_DELAY_MS=1250
GEOPULSE_GEOCODING_RETRY_JITTER_MS=250
GEOPULSE_GEOCODING_CB_FAILURE_RATIO=0.7
GEOPULSE_GEOCODING_CB_REQUEST_VOLUME=10
GEOPULSE_GEOCODING_CB_DELAY_SECONDS=20
GEOPULSE_GEOCODING_CB_SUCCESS_THRESHOLD=2
GEOPULSE_GEOCODING_RECONCILE_ITEM_MAX_ATTEMPTS=4
GEOPULSE_GEOCODING_RECONCILE_CIRCUIT_OPEN_WAIT_MS=20000
GEOPULSE_GEOCODING_RECONCILE_INTER_ITEM_DELAY_MS=1000
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
      language: "en-US"  # Optional
    photon:
      enabled: false
      url: "https://photon.komoot.io"
      language: "en-US"  # Optional
    googleMaps:
      enabled: true
      apiKey: "your-api-key"  # Stored as Kubernetes Secret
      language: "en"  # Optional (Google supported language code)
    mapbox:
      enabled: false
      accessToken: ""
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-deployment#geocoding-configuration).
