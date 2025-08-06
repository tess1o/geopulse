# Immich Integration Requirements

## Overview
Add Immich support to GeoPulse application to display user photos alongside their timeline data. Each user should be able to configure their own Immich server connection and view photos from selected dates and locations.

## Requirements

### 1. User Configuration Storage
- Add Immich configuration to `UserEntity` similar to existing `timelinePreferences`
- Store per-user Immich server URL and API key
- Configuration should be optional (nullable)

### 2. Immich Configuration Model
- Create `ImmichPreferences` model with:
  - `serverUrl`: The Immich server URL
  - `apiKey`: API key for authentication
  - `enabled`: Boolean flag to enable/disable Immich integration

### 3. Immich Client Service
- HTTP client to communicate with Immich API
- Support for search metadata endpoint: `POST /api/search/metadata`
- Support for asset download endpoints
- Handle authentication via `x-api-key` header

### 4. Search API Features
- Search images by date range (`takenAfter`, `takenBefore`)
- Filter by image type (`IMAGE`)
- Include EXIF data (`withExif: true`)
- Backend filtering by GPS coordinates (latitude, longitude, radius)

### 5. REST API Endpoints

#### Configuration Management
- `PUT /api/users/{userId}/immich-config` - Update Immich configuration
- `GET /api/users/{userId}/immich-config` - Get current Immich configuration (without API key)

#### Photo Search
- `GET /api/users/{userId}/immich/photos/search` - Search photos with parameters:
  - `startDate`: Start date for search (ISO format)
  - `endDate`: End date for search (ISO format)
  - `latitude` (optional): GPS latitude for location filtering
  - `longitude` (optional): GPS longitude for location filtering
  - `radiusMeters` (optional): Radius in meters for location filtering

#### Photo Access
- `GET /api/users/{userId}/immich/photos/{photoId}` - Get photo metadata
- `GET /api/users/{userId}/immich/photos/{photoId}/thumbnail` - Get photo thumbnail
- `GET /api/users/{userId}/immich/photos/{photoId}/download` - Download full photo

### 6. Models and DTOs

#### Request/Response Models
- `UpdateImmichConfigRequest`: For updating user's Immich configuration
- `ImmichConfigResponse`: Response with server URL and enabled status (no API key)
- `ImmichPhotoSearchRequest`: Search parameters
- `ImmichPhotoDto`: Photo metadata response
- `ImmichPhotoSearchResponse`: Search results with list of photos

#### Internal Models
- `ImmichSearchRequest`: Internal model for Immich API requests
- `ImmichAsset`: Immich API response model
- `ImmichExifInfo`: EXIF data model

### 7. Services

#### ImmichService
- Business logic for photo operations
- Coordinate filtering logic
- Integration with user preferences
- Error handling and validation

#### ImmichClient
- Low-level HTTP client for Immich API
- Authentication handling
- Request/response mapping
- Connection testing

### 8. Security Considerations
- API keys should be encrypted in database
- Users can only access their own Immich configuration
- Validate Immich server URLs to prevent SSRF
- Rate limiting for external API calls

### 9. Error Handling
- Handle Immich server connectivity issues
- Invalid API key scenarios
- Missing EXIF data
- GPS coordinate validation

### 10. Testing Requirements
- Unit tests for all services with mocked Immich responses
- Integration tests with real database
- Mock external HTTP calls to Immich server
- Test GPS coordinate filtering logic
- Test configuration CRUD operations

## Implementation Structure

```
src/main/java/org/github/tess1o/geopulse/immich/
├── model/
│   ├── ImmichPreferences.java
│   ├── ImmichPhotoDto.java
│   ├── ImmichAsset.java
│   ├── ImmichExifInfo.java
│   ├── UpdateImmichConfigRequest.java
│   ├── ImmichConfigResponse.java
│   ├── ImmichPhotoSearchRequest.java
│   └── ImmichPhotoSearchResponse.java
├── client/
│   └── ImmichClient.java
├── service/
│   └── ImmichService.java
└── rest/
    └── ImmichResource.java

src/test/java/org/github/tess1o/geopulse/immich/
├── service/
│   ├── ImmichServiceTest.java
│   └── ImmichClientTest.java
└── rest/
    └── ImmichResourceTest.java
```

## Database Changes
- Update `UserEntity` to include `ImmichPreferences immichPreferences` field
- Use JSONB column similar to `timelinePreferences`

## API Examples

### Update Immich Configuration
```http
PUT /api/users/me/immich-config
Content-Type: application/json

{
  "serverUrl": "http://100.120.112.63:2283",
  "apiKey": "NmpwrJSULpVPDLzFrO3OdbvvcRXXlaQtM4o4XCFl3kc",
  "enabled": true
}
```

### Search Photos
```http
GET /api/users/me/immich/photos/search?startDate=2023-01-01T00:00:00Z&endDate=2024-12-31T23:59:59Z&latitude=40.7128&longitude=-74.0060&radiusMeters=1000
```

### Response Example
```json
{
  "photos": [
    {
      "id": "photo-uuid",
      "originalFileName": "IMG_001.jpg",
      "takenAt": "2023-06-15T14:30:00Z",
      "latitude": 40.7589,
      "longitude": -73.9851,
      "thumbnailUrl": "/api/users/me/immich/photos/photo-uuid/thumbnail",
      "downloadUrl": "/api/users/me/immich/photos/photo-uuid/download"
    }
  ],
  "totalCount": 1
}
```