# Timeline Share Feature Specification

## 1. Overview

### 1.1 Problem Statement

The current "Share Links" feature allows users to share their **current location** with optional recent history (last X hours). However, users need the ability to share **historical timeline data** for specific date ranges - for example, sharing a week-long vacation trip with friends and family.

### 1.2 Solution

Introduce a new "Timeline Share" capability that allows users to:
- Share full timeline views (map + timeline cards with stays/trips) for specific date ranges
- Pre-create shares for future trips that auto-populate as data is recorded
- Optionally show current location during active trips
- Protect shares with passwords and set expiration dates

### 1.3 Key Differences

| Feature | Live Location Share | Timeline Share |
|---------|--------------------|-----------------|
| **Use Case** | "Where am I now?" | "Where was I during my vacation?" |
| **Time Scope** | Current + last X hours | Custom start/end dates |
| **UI Shown** | Marker + path only | Full timeline (map + cards with stays/trips) |
| **Data Nature** | Always current | Historical or updating during date range |
| **Updates** | Real-time | Only during active date range |

---

## 2. User Stories

### 2.1 Primary Use Cases

**UC1: Share Past Trip**
> As a user, I want to share my vacation timeline (Jan 1-7) with family so they can see all the places I visited.

**UC2: Share Ongoing Trip**
> As a user, I want to create a share for my current road trip (started yesterday, ends in 3 days) so friends can follow along in real-time.

**UC3: Pre-share Future Trip**
> As a user, I want to create a share link for my upcoming vacation (starts next week) so I can send the link to family before I leave.

**UC4: Password Protection**
> As a user, I want to password-protect my shared timeline so only people I give the password to can view it.

**UC5: Quick Share from Timeline**
> As a user viewing my timeline for a specific date range, I want to quickly share that exact view with one click.

---

## 3. UI/UX Design

### 3.1 Share Links Page (Updated)

The existing Share Links page (`/app/share-links`) will be updated to show both share types in separate sections:

```
┌─────────────────────────────────────────────────┐
│  Share Links                    [+ Create New ▾]│
├─────────────────────────────────────────────────┤
│                                                 │
│  ── Live Location Shares (2/10) ──────────────  │
│  ┌─────────────┐  ┌─────────────┐              │
│  │ Family      │  │ Emergency   │              │
│  │ Active      │  │ Active      │              │
│  │ 24h history │  │ Current only│              │
│  └─────────────┘  └─────────────┘              │
│                                                 │
│  ── Timeline Shares (1/10) ────────────────────  │
│  ┌─────────────────────────────────────┐       │
│  │ Italy Vacation                      │       │
│  │ Jan 1 - Jan 7, 2025                │       │
│  │ Status: Completed | Expires: Feb 1  │       │
│  │ [Copy Link] [Edit] [Delete]         │       │
│  └─────────────────────────────────────┘       │
│                                                 │
└─────────────────────────────────────────────────┘
```

**Create Button Dropdown:**
- "Live Location Share" - Opens existing create dialog
- "Timeline Share" - Opens new timeline share dialog

### 3.2 Timeline Share Creation Dialog

Accessible from:
1. Share Links page → "Create New" → "Timeline Share"
2. Timeline page → "Share Timeline" button (pre-fills current date range)

```
┌─────────────────────────────────────────────────┐
│  Create Timeline Share                      [X] │
├─────────────────────────────────────────────────┤
│                                                 │
│  Name (optional)                                │
│  ┌─────────────────────────────────────────┐   │
│  │ Italy Vacation 2025                     │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  Trip Dates                                     │
│  ┌──────────────┐    ┌──────────────┐          │
│  │ Jan 1, 2025  │ to │ Jan 7, 2025  │          │
│  └──────────────┘    └──────────────┘          │
│                                                 │
│  ☑ Show current location during trip           │
│    (Only visible when viewing during the trip) │
│                                                 │
│  Link Expiration                                │
│  ┌─────────────────────────────────────────┐   │
│  │ Feb 1, 2025                             │   │
│  └─────────────────────────────────────────┘   │
│  ℹ️ When the link becomes inaccessible          │
│                                                 │
│  ☐ Password protect this link                  │
│                                                 │
│              [Cancel]  [Create Link]            │
└─────────────────────────────────────────────────┘
```

**Field Details:**

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| Name | No | "Timeline Share" | Display name for the share |
| Start Date | Yes | Current date (or from TimelinePage) | Trip start date |
| End Date | Yes | Current date (or from TimelinePage) | Trip end date |
| Show Current Location | No | Enabled | Only shown during active trips (start ≤ now ≤ end) |
| Link Expiration | No | 30 days after end date | When the link stops working |
| Password | No | None | Optional password protection |

### 3.3 Timeline Share Card (in Share Links page)

```
┌─────────────────────────────────────────────────┐
│  Italy Vacation 2025                   [Active] │
│                                                 │
│  📅 Jan 1 - Jan 7, 2025                        │
│  ⏱️ Expires: Feb 1, 2025                        │
│  🔒 Password Protected: Yes                     │
│  👁️ Views: 42                                   │
│  📍 Current Location: Enabled                   │
│                                                 │
│  Share URL:                                     │
│  ┌─────────────────────────────────────┐ [📋]  │
│  │ https://app.com/shared-timeline/abc │       │
│  └─────────────────────────────────────┘       │
│                                                 │
│                          [Edit] [Delete]        │
└─────────────────────────────────────────────────┘
```

**Status Badges:**

| Status | Condition | Color |
|--------|-----------|-------|
| Upcoming | now < start_date | Blue |
| Active | start_date ≤ now ≤ end_date | Green |
| Completed | now > end_date | Gray |
| Expired | now > expires_at | Red |

### 3.4 Shared Timeline Page (Public View)

Route: `/shared-timeline/:linkId`

**States:**

1. **Loading State**
   - Spinner with "Loading shared timeline..."

2. **Password Required**
   - Password input form (same as current SharedLocationPage)

3. **Error States**
   - "Link not found" - Invalid link ID
   - "Link expired" - Past expiration date
   - "Access denied" - Wrong password

4. **Trip Not Started**
   ```
   ┌─────────────────────────────────────┐
   │  📅 Italy Vacation                  │
   │                                     │
   │  This trip hasn't started yet.      │
   │  Trip begins: Jan 1, 2025           │
   │  (in 5 days)                        │
   │                                     │
   │  Check back after the trip starts   │
   │  to see the timeline.               │
   └─────────────────────────────────────┘
   ```

5. **Main Timeline View**

```
┌─────────────────────────────────────────────────────────────┐
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 🌍 GeoPulse                              [🌙 Theme] │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Italy Vacation 2025                                     │ │
│ │ Shared by: John | Jan 1-7, 2025 | Status: Completed    │ │
│ │ Expires: Feb 1, 2025                                    │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ┌───────────────────────────────┬─────────────────────────┐ │
│ │                               │                         │ │
│ │        MAP VIEW               │    TIMELINE CARDS       │ │
│ │   (with paths, markers,       │    (stays, trips,      │ │
│ │    current location*)         │     data gaps)         │ │
│ │                               │                         │ │
│ │   [🔄 Refresh]                │                         │ │
│ │                               │                         │ │
│ └───────────────────────────────┴─────────────────────────┘ │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Powered by GeoPulse          [Get GeoPulse]            │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

* Current location marker shown only during active trips
```

**Info Card Details:**

| Field | Description |
|-------|-------------|
| Share Name | "Italy Vacation 2025" |
| Shared By | Username of the owner |
| Date Range | "Jan 1 - Jan 7, 2025" |
| Status | Upcoming / Active / Completed |
| Expires | "Feb 1, 2025" or "In 5 days" |

**Status-specific UI:**

- **Upcoming**: Show "Trip starts in X days" message, no map/timeline
- **Active**: Show map + timeline + current location marker (if enabled) + refresh button
- **Completed**: Show map + timeline, no current location marker, refresh still works

### 3.5 TimelinePage Enhancement

Add a "Share" button to the TimelinePage header:

```
┌─────────────────────────────────────────────────┐
│  Timeline              Jan 1 - Jan 7 ▾  [Share] │
├─────────────────────────────────────────────────┤
│  ...                                            │
```

Clicking "Share" opens the Timeline Share dialog with:
- Start date pre-filled with current date range start
- End date pre-filled with current date range end

---

## 4. Data Model

### 4.1 Database Schema

**Option A: Extend existing `shared_links` table**

Add new columns to support timeline shares:

```sql
ALTER TABLE shared_links ADD COLUMN share_type VARCHAR(20) DEFAULT 'live_location';
ALTER TABLE shared_links ADD COLUMN start_date TIMESTAMP;
ALTER TABLE shared_links ADD COLUMN end_date TIMESTAMP;
ALTER TABLE shared_links ADD COLUMN show_current_location BOOLEAN DEFAULT true;

-- Existing columns still used:
-- id, user_id, name, expires_at, password_hash, has_password, is_active, view_count, created_at
-- show_history, history_hours (only for live_location type)
```

**Option B: New `timeline_shares` table** (if cleaner separation preferred)

```sql
CREATE TABLE timeline_shares (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    password_hash VARCHAR(255),
    has_password BOOLEAN DEFAULT false,
    show_current_location BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    view_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Recommendation**: Option A (extend existing table) for simpler codebase and unified management.

### 4.2 Entity Changes

```java
@Entity
@Table(name = "shared_links")
public class SharedLinkEntity {
    // Existing fields...

    // New fields for timeline shares
    @Column(name = "share_type")
    @Enumerated(EnumType.STRING)
    private ShareType shareType = ShareType.LIVE_LOCATION;

    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "show_current_location")
    private Boolean showCurrentLocation = true;
}

public enum ShareType {
    LIVE_LOCATION,  // Current location + optional history
    TIMELINE        // Date range with full timeline
}
```

---

## 5. API Specification

### 5.1 Authenticated Endpoints (User's own shares)

#### List All Shares
```
GET /api/share-links
```

Response now includes both types:
```json
{
  "data": {
    "links": [
      {
        "id": "uuid",
        "share_type": "live_location",
        "name": "Family",
        "show_history": true,
        "history_hours": 24,
        "expires_at": "2025-02-01T00:00:00Z",
        "has_password": false,
        "is_active": true,
        "view_count": 10,
        "created_at": "2025-01-01T00:00:00Z"
      },
      {
        "id": "uuid",
        "share_type": "timeline",
        "name": "Italy Vacation",
        "start_date": "2025-01-01T00:00:00Z",
        "end_date": "2025-01-07T23:59:59Z",
        "show_current_location": true,
        "expires_at": "2025-02-01T00:00:00Z",
        "has_password": true,
        "is_active": true,
        "view_count": 42,
        "created_at": "2025-01-01T00:00:00Z"
      }
    ],
    "max_links": 10,
    "base_url": "https://geopulse.app"
  }
}
```

#### Create Timeline Share
```
POST /api/share-links
```

Request:
```json
{
  "share_type": "timeline",
  "name": "Italy Vacation",
  "start_date": "2025-01-01T00:00:00Z",
  "end_date": "2025-01-07T23:59:59Z",
  "show_current_location": true,
  "expires_at": "2025-02-01T00:00:00Z",
  "password": "optional-password"
}
```

#### Update Timeline Share
```
PUT /api/share-links/{id}
```

Request (same fields as create).

#### Delete Share
```
DELETE /api/share-links/{id}
```

### 5.2 Public Endpoints (Viewing shared content)

#### Get Share Info
```
GET /api/shared/{linkId}/info
```

Response (extended for timeline shares):
```json
{
  "share_type": "timeline",
  "name": "Italy Vacation",
  "shared_by": "John",
  "start_date": "2025-01-01T00:00:00Z",
  "end_date": "2025-01-07T23:59:59Z",
  "show_current_location": true,
  "expires_at": "2025-02-01T00:00:00Z",
  "has_password": true,
  "status": "completed"  // "upcoming", "active", "completed"
}
```

#### Verify Password
```
POST /api/shared/{linkId}/verify
```

Request:
```json
{
  "password": "user-password"
}
```

Response:
```json
{
  "access_token": "jwt-token",
  "expires_in": 3600
}
```

#### Get Timeline Data (New endpoint)
```
GET /api/shared/{linkId}/timeline
Authorization: Bearer {access_token}
```

Response:
```json
{
  "stays": [...],
  "trips": [...],
  "dataGaps": [...]
}
```

#### Get Path Data (New endpoint)
```
GET /api/shared/{linkId}/path
Authorization: Bearer {access_token}
```

Response:
```json
{
  "points": [
    {
      "latitude": 45.464,
      "longitude": 9.190,
      "timestamp": "2025-01-01T10:00:00Z"
    },
    ...
  ]
}
```

#### Get Current Location (For active trips)
```
GET /api/shared/{linkId}/current
Authorization: Bearer {access_token}
```

Response:
```json
{
  "latitude": 45.464,
  "longitude": 9.190,
  "timestamp": "2025-01-05T14:30:00Z"
}
```

Returns 404 if:
- Trip is not active (upcoming or completed)
- `show_current_location` is disabled

---

## 6. Frontend Implementation

### 6.1 Store Updates

#### shareLinks.js (Extended)

New state:
```javascript
state: () => ({
  // Existing...
  links: [],  // Now contains both types

  // For viewing shared timeline
  sharedTimelineData: null,  // { stays, trips, dataGaps }
  sharedPathData: null,      // { points: [...] }
})
```

New actions:
```javascript
// Create timeline share
async createTimelineShare(data) {
  return this.createShareLink({
    share_type: 'timeline',
    ...data
  })
}

// Fetch timeline data for shared view
async fetchSharedTimeline(linkId) {
  const response = await apiService.getWithCustomHeaders(
    `/shared/${linkId}/timeline`,
    { 'Authorization': `Bearer ${this.sharedAccessToken}` }
  )
  this.sharedTimelineData = response
  return response
}

// Fetch path data for shared view
async fetchSharedPath(linkId) {
  const response = await apiService.getWithCustomHeaders(
    `/shared/${linkId}/path`,
    { 'Authorization': `Bearer ${this.sharedAccessToken}` }
  )
  this.sharedPathData = response
  return response
}
```

New getters:
```javascript
// Filter by share type
getLiveLocationShares: (state) => state.links.filter(l => l.share_type !== 'timeline'),
getTimelineShares: (state) => state.links.filter(l => l.share_type === 'timeline'),

// Get share status
getShareStatus: () => (share) => {
  if (share.share_type !== 'timeline') return null
  const now = new Date()
  const start = new Date(share.start_date)
  const end = new Date(share.end_date)
  if (now < start) return 'upcoming'
  if (now > end) return 'completed'
  return 'active'
}
```

### 6.2 New Components

#### TimelineShareDialog.vue

Props:
- `visible: Boolean` - Dialog visibility
- `editingShare: Object` - Share being edited (null for create)
- `prefillDates: Object` - `{ start, end }` for pre-filling from TimelinePage

Events:
- `@update:visible` - Dialog closed
- `@created` - Share created successfully
- `@updated` - Share updated successfully

#### TimelineShareCard.vue

Props:
- `share: Object` - Timeline share data

Events:
- `@edit` - Edit button clicked
- `@delete` - Delete button clicked
- `@copy` - Copy link button clicked

### 6.3 Modified Components

#### ShareLinksPage.vue

Changes:
- Add dropdown to "Create New" button
- Split link list into two sections
- Add TimelineShareDialog
- Update empty state for each section

#### TimelinePage.vue

Changes:
- Add "Share" button to header
- Import and use TimelineShareDialog
- Pass current date range as prefill

### 6.4 New Page

#### SharedTimelinePage.vue

Route: `/shared-timeline/:linkId`

Features:
- Password verification flow
- Trip status handling (upcoming/active/completed)
- Reuse TimelineMap and TimelineContainer components
- Current location marker (conditional)
- Refresh functionality
- Responsive layout

---

## 7. Implementation Phases

### Phase 1: Backend Foundation
1. Database migration for new columns
2. Update entity and DTOs
3. Update existing endpoints to support timeline type
4. Add new public endpoints for timeline/path data

### Phase 2: Frontend Store & Dialog
1. Extend shareLinks store with timeline support
2. Create TimelineShareDialog component
3. Update ShareLinksPage with two sections
4. Add create dropdown menu

### Phase 3: Shared Timeline Viewer
1. Create SharedTimelinePage
2. Implement all view states
3. Integrate TimelineMap and TimelineContainer
4. Add current location logic

### Phase 4: TimelinePage Integration
1. Add Share button to TimelinePage
2. Connect to TimelineShareDialog with prefill

### Phase 5: Polish & Testing
1. Error handling
2. Loading states
3. Mobile responsiveness
4. E2E testing

---

## 8. Security Considerations

### 8.1 Access Control
- Timeline data only accessible with valid access token
- Token expires (default 1 hour, refreshable)
- Rate limiting on public endpoints

### 8.2 Data Exposure
- Only expose data within the specified date range
- Don't expose exact user ID, only display name
- Current location only during active trips and if enabled

### 8.3 Password Protection
- Passwords hashed with bcrypt
- No password hints or recovery

---

## 9. Future Enhancements

### 9.1 Potential Features
- **Collaborative trips**: Multiple users contribute to same timeline
- **Comments/annotations**: Viewers can leave comments
- **Photo integration**: Show Immich photos on timeline
- **Export**: Download shared timeline as GPX/PDF
- **Analytics**: Detailed view statistics

### 9.2 Performance Optimizations
- Lazy load timeline cards
- Tile-based path rendering for large datasets
- Caching for frequently accessed shares

---

## 10. Open Questions

1. **Share limits**: Should timeline shares count against same limit as live location shares, or have separate limit?
   - **Recommendation**: Same limit (10 total), but could be configurable

2. **Notification**: Should owner be notified when share is viewed?
   - **Recommendation**: Optional, implement in future phase

3. **Revocation**: Should individual view sessions be revocable?
   - **Recommendation**: No, just delete/deactivate the share

4. **Edit restrictions**: Can dates be changed after creation?
   - **Recommendation**: Yes, with warning if narrowing removes data from views
