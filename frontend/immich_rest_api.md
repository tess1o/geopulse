⏺ Immich REST API Documentation

Overview

The ImmichResource provides REST endpoints for managing user Immich configurations and accessing photos from their self-hosted Immich servers.

Authentication

All endpoints require authentication via JWT token in the Authorization: Bearer <token> header and have @RolesAllowed("USER") security.

  ---
1. Configuration Management

Get User's Immich Configuration

GET /api/users/{userId}/immich-configGET /api/users/me/immich-config (current user shortcut)

Request Parameters

- Path: userId (UUID, optional for /me endpoint)
- Headers: Authorization: Bearer <jwt-token> (required)

Response

Success (200):
{
"success": true,
"data": {
"serverUrl": "http://192.168.1.100:2283",
"enabled": true
}
}

Not Configured (200):
{
"success": true,
"data": null
}

Forbidden (403): User trying to access another user's config
Unauthorized (401): Missing/invalid JWT token

  ---
Update User's Immich Configuration

PUT /api/users/{userId}/immich-configPUT /api/users/me/immich-config (current user shortcut)

Request Parameters

- Path: userId (UUID, optional for /me endpoint)
- Headers:
    - Authorization: Bearer <jwt-token> (required)
    - Content-Type: application/json (required)

Request Body

{
"serverUrl": "http://192.168.1.100:2283",
"apiKey": "NmpwrJSULpVPDLzFrO3OdbvvcRXXlaQtM4o4XCFl3kc",
"enabled": true
}

Field Validation:
- serverUrl: Required, non-blank string (will be normalized by removing trailing slash)
- apiKey: Required, non-blank string
- enabled: Required, boolean

Response

Success (200):
{
"success": true,
"data": "Immich configuration updated successfully"
}

Validation Error (400): Invalid request body
Forbidden (403): User trying to update another user's config
Internal Error (500): Server error during update

  ---
2. Photo Search

Search User's Photos

GET /api/users/{userId}/immich/photos/searchGET /api/users/me/immich/photos/search (current user shortcut)

Request Parameters

- Path: userId (UUID, optional for /me endpoint)
- Headers: Authorization: Bearer <jwt-token> (required)

Query Parameters

- startDate: Required - ISO 8601 datetime (e.g., 2023-01-01T00:00:00Z)
- endDate: Required - ISO 8601 datetime (e.g., 2024-12-31T23:59:59Z)
- latitude: Optional - GPS latitude for location filtering (decimal degrees)
- longitude: Optional - GPS longitude for location filtering (decimal degrees)
- radiusMeters: Optional - Radius in meters for location filtering

Example Request

GET /api/users/me/immich/photos/search?startDate=2023-06-01T00:00:00Z&endDate=2023-06-30T23:59:59Z&latitude=40.7128&longitude=-74.0060&radiusMeters=1000

Response

Success (200):
{
"success": true,
"data": {
"photos": [
{
"id": "550e8400-e29b-41d4-a716-446655440000",
"originalFileName": "IMG_001.jpg",
"takenAt": "2023-06-15T14:30:00Z",
"latitude": 40.7589,
"longitude": -73.9851,
"thumbnailUrl": "/api/users/me/immich/photos/550e8400-e29b-41d4-a716-446655440000/thumbnail",
"downloadUrl": "/api/users/me/immich/photos/550e8400-e29b-41d4-a716-446655440000/download"
}
],
"totalCount": 1
}
}

Photo Object Fields:
- id: Immich asset ID (string)
- originalFileName: Original photo filename (string)
- takenAt: When photo was taken (ISO 8601 datetime, nullable)
- latitude: GPS latitude (decimal degrees, nullable)
- longitude: GPS longitude (decimal degrees, nullable)
- thumbnailUrl: URL to get thumbnail (string)
- downloadUrl: URL to download original (string)

Empty Results (200):
{
"success": true,
"data": {
"photos": [],
"totalCount": 0
}
}

Bad Request (400): Invalid date format or missing required parameters
Internal Error (500): Immich server communication error

  ---
3. Photo Access

Get Photo Thumbnail

GET /api/users/{userId}/immich/photos/{photoId}/thumbnailGET /api/users/me/immich/photos/{photoId}/thumbnail (current user shortcut)

Request Parameters

- Path:
    - userId (UUID, optional for /me endpoint)
    - photoId (string, required) - Immich asset ID
- Headers: Authorization: Bearer <jwt-token> (required)

Response

Success (200):
- Content-Type: image/jpeg
- Headers:
    - Cache-Control: max-age=3600
- Body: Binary image data (thumbnail)

Not Found (404): Photo not found or user doesn't have access
Forbidden (403): User trying to access another user's photos

  ---
Download Original Photo

GET /api/users/{userId}/immich/photos/{photoId}/downloadGET /api/users/me/immich/photos/{photoId}/download (current user shortcut)

Request Parameters

- Path:
    - userId (UUID, optional for /me endpoint)
    - photoId (string, required) - Immich asset ID
- Headers: Authorization: Bearer <jwt-token> (required)

Response

Success (200):
- Content-Type: image/jpeg
- Headers:
    - Content-Disposition: attachment; filename="photo_{photoId}.jpg"
- Body: Binary image data (original resolution)

Not Found (404): Photo not found or user doesn't have access
Forbidden (403): User trying to access another user's photos

  ---
Error Responses

All endpoints use consistent error response format:

{
"success": false,
"error": "Error message description"
}

Location Filtering Logic

When latitude, longitude, and radiusMeters are provided:
1. Photos are fetched from Immich server by date range
2. Backend filtering applied using Haversine distance formula
3. Only photos within the specified radius are returned
4. Photos without GPS coordinates are excluded from location-filtered results

Security Notes

- Users can only access their own Immich configurations and photos
- API keys are stored encrypted in database
- All endpoints validate user ownership before processing requests
- Immich server URLs are validated to prevent SSRF attacks

Frontend Implementation Notes

1. Configuration Flow: Check config → Update if needed → Search photos
2. Error Handling: Handle cases where Immich is disabled/not configured
3. Caching: Thumbnail responses include cache headers for performance
4. Location Filtering: All three location parameters (lat, lng, radius) must be provided together
5. Date Format: Use ISO 8601 format with timezone (e.g., 2023-06-15T00:00:00Z)

---
## Frontend UI/UX Implementation

### Photo Display on Timeline Map

**Map Markers:**
- Single photos: Camera icon
- Photo clusters (2+ photos at same location): Numbered cluster icon
- Markers positioned at GPS coordinates from photo metadata

**Interaction Patterns:**
- **Hover/Mouse Over**: Shows quick preview popup with thumbnail(s) and basic info
  - Single photo: Thumbnail + filename + date
  - Cluster: Photo grid (up to 4 thumbnails) + total count + date range
  - Popup appears after brief delay, disappears when mouse leaves
  
- **Click**: Always opens full PhotoViewerDialog for complete functionality
  - Single photo: Dialog with 1 photo, navigation disabled
  - Cluster: Dialog with all photos, navigation enabled between photos
  - Provides download, full-screen viewing, and photo metadata

**Image Loading Strategy:**
- Primary: Use `thumbnailUrl` with cookies for authentication (reliable)
- Secondary: Attempt `downloadUrl` for higher resolution when possible
- Fallback: Display error message if both fail

**Responsive Design:**
- Desktop: Hover previews, click for full viewer
- Mobile: Tap for preview, tap again or use viewer button for full dialog
- Touch-friendly controls and appropriate sizing for mobile devices

### PhotoViewerDialog Features

**Core Functionality:**
- Image display with loading states and error handling
- Navigation between photos (for clusters)
- Download original photo functionality
- Photo metadata display (filename, date, GPS coordinates)
- Keyboard shortcuts (arrow keys, Esc to close)
- Mobile-responsive design with touch gestures

**Image Resolution Priority:**
1. First attempt: Display `downloadUrl` for full resolution
2. Fallback: Use `thumbnailUrl` if download fails
3. Error state: Show retry options and error message

**Authentication:**
- Leverages browser cookies for seamless authentication
- No complex auth header management required
- Backend handles proxy authentication to Immich server

