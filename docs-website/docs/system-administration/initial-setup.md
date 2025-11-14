# Initial Setup

After deploying GeoPulse, you may want to configure system-wide settings that affect all users.  
This section provides a guided overview of the most important configuration areas, including authentication, geocoding,
data processing, AI integration, and UI behavior.

In future releases, these settings may become manageable through a dedicated Admin UI.

---

## What to Configure Next

Depending on your environment and the features you want to enable, review the following configuration areas.

---

## Authentication & Security

### Authentication Options

Configure authentication cookies behavior.  
➡️ [Authentication](./configuration/authentication)

### User Registration

Control whether new users can register via email/password or OIDC.  
➡️ [User Registration](./configuration/user-registration)

### OIDC Single Sign-On

Enable login via Keycloak, Auth0, Azure AD, or any OpenID Connect identity provider.  
➡️ [OIDC / SSO](./configuration/oidc-sso)

---

## Location & Mapping

### Reverse Geocoding

Configure providers such as Nominatim or Photon for converting GPS coordinates into place names.
This is optional, by default Nominatim is used for reverse geocoding.  
➡️ [Reverse Geocoding](./configuration/reverse-geocoding)

### GPS Data Filtering

Adjust smoothing, noise reduction, accuracy filtering, and other GPS processing options.
This is optional, each user can customize their own settings via UI.  
➡️ [GPS Data Filtering](./configuration/gps-data-filtering)

---

## AI & Automation

### AI Assistant

Optionally configure AI encryption key that will be used to encrypt AI settings in DB.
GeoPulse creates this key automatically during initial setup.  
➡️ [AI Assistant Configuration](./configuration/ai-assistant)

---

## User Experience

### Frontend Nginx Settings

Set global options like Max Upload File Size, OSM Resolver DNS Servers.  
➡️ [Frontend Settings](./configuration/frontend)

### Location Sharing

Override default base URL for sharing links.  
➡️ [Location Sharing](./configuration/location-sharing)

---

## GPS Source Integrations

### OwnTracks Additional Configuration

Fine-tune how OwnTracks payloads are processed beyond the default supported settings.  
➡️ [OwnTracks Advanced Configuration](./configuration/owntracks-additional-config)

---

## Recommended Path for New Deployments

1. Deploy GeoPulse (Docker Compose / Kubernetes / Helm).
2. Configure Authentication cookies and optionally OIDC/SSO and User Registration settings.
3. Configure Reverse Geocoding for place names.
4. Create your first user and log in.