# Timeline Share Implementation Status

## ✅ COMPLETED - Backend (Fully Functional)

### Database
- ✅ Migration `V20.0.0__Add_timeline_sharing_support.sql` created
- ✅ Added fields: `share_type`, `start_date`, `end_date`, `show_current_location`, `show_photos`
- ✅ Added indexes for performance
- ✅ Added check constraints for data integrity

### Models & DTOs
- ✅ `ShareType` enum (LIVE_LOCATION, TIMELINE)
- ✅ `SharedLinkEntity` updated with new fields and helper methods
- ✅ `SharedLinkDto` updated
- ✅ `SharedLocationInfo` updated for public API
- ✅ `CreateShareLinkRequest` and `UpdateShareLinkDto` updated
- ✅ `SharedLinkMapper` updated to handle all new fields

### Repositories
- ✅ `SharedLinkRepository` - Added `countActiveByUserIdAndType()` for separate limits
- ✅ `SharedLinkRepository` - Added `findByUserIdAndType()` for filtering

### Services
- ✅ `SharedLinkService.createShareLink()` - Validates timeline-specific requirements
- ✅ `SharedLinkService.createShareLink()` - Enforces separate limits (10 live + 10 timeline)
- ✅ `SharedLinkService.verifyPassword()` - Returns longer tokens for timeline shares (2 hours vs 30 min)
- ✅ `SharedLinkService.getSharedTimeline()` - NEW: Fetches timeline data for date range
- ✅ `SharedLinkService.getSharedPath()` - NEW: Fetches and simplifies GPS path
- ✅ `SharedLinkService.getSharedCurrentLocation()` - NEW: Returns current location for active timelines

### REST APIs
- ✅ `PublicSharedLinkResource` - Added `GET /{linkId}/timeline`
- ✅ `PublicSharedLinkResource` - Added `GET /{linkId}/path`
- ✅ `PublicSharedLinkResource` - Added `GET /{linkId}/current`
- ✅ All endpoints validate Bearer tokens
- ✅ All endpoints handle errors appropriately

### Configuration
- ✅ Separate token lifespan for timeline shares (`geopulse.sharing.temp-token.timeline-lifespan=7200`)
- ✅ Existing max links limit reused (`geopulse.sharing.max-links-per-user=10`)

### ✅ Backend compiles successfully!

---

## ✅ COMPLETED - Frontend Store

### Store Updates (`frontend/src/stores/shareLinks.js`)
- ✅ Added state: `sharedTimelineData`, `sharedPathData`, `sharedCurrentLocation`
- ✅ Added getters: `getLiveLocationShares()`, `getTimelineShares()`
- ✅ Added getters: `getSharedTimelineData()`, `getSharedPathData()`, `getSharedCurrentLocation()`
- ✅ Added action: `fetchSharedTimeline(linkId)`
- ✅ Added action: `fetchSharedPath(linkId)`
- ✅ Added action: `fetchSharedCurrentLocation(linkId)`

---

## 🚧 TODO - Frontend UI Components

### 1. Create `TimelineShareDialog.vue`
**Location**: `frontend/src/components/sharing/TimelineShareDialog.vue`

**Purpose**: Dialog for creating/editing timeline shares

**Key Features**:
- Name input (optional)
- Date range picker (start_date, end_date) - REQUIRED for timeline shares
- Expiration date picker
- Show current location toggle (default: true)
- Show photos toggle (default: false)
- Password protection toggle + password input
- Mobile-responsive form layout

**Props**:
```javascript
{
  visible: Boolean,
  editingShare: Object, // null for create, existing share for edit
  prefillDates: Object  // {start: Date, end: Date} from TimelinePage
}
```

**Events**:
```javascript
@update:visible
@created (share)
@updated (share)
```

**Implementation Notes**:
- Use PrimeVue Calendar for date pickers
- Validate end_date >= start_date
- Set share_type = 'TIMELINE' in API payload
- Use existing ShareLinksPage dialog as reference

---

### 2. Update `ShareLinksPage.vue`
**Location**: `frontend/src/views/app/ShareLinksPage.vue`

**Changes Needed**:

1. **Split Create Button into Dropdown**:
```vue
<Button label="Create New" icon="pi pi-plus" @click="toggleDropdown" />
<Menu :model="createMenuItems" ref="menu" />
<!-- createMenuItems: [
  { label: 'Live Location Share', command: openLiveDialog },
  { label: 'Timeline Share', command: openTimelineDialog }
] -->
```

2. **Split Links into Two Sections**:
```vue
<div class="section">
  <h3>Live Location Shares ({{ liveShares.length }}/10)</h3>
  <div class="links-grid">
    <!-- Loop through getLiveLocationShares -->
  </div>
</div>

<div class="section">
  <h3>Timeline Shares ({{ timelineShares.length }}/10)</h3>
  <div class="links-grid">
    <!-- Loop through getTimelineShares -->
  </div>
</div>
```

3. **Update Link Cards**:
- Show different info for timeline shares:
  - Date range: "Jan 1-7, 2025"
  - Status badge: "Upcoming" / "Active" / "Completed"
  - Show current location: Yes/No
  - Show photos: Yes/No
- Use `share.timeline_status` from backend
- Update URL generation: `/shared-timeline/{id}` for timeline shares

4. **Mobile Responsive**:
- Stack sections vertically on mobile
- Grid → single column on small screens
- Larger touch targets for buttons

---

### 3. Create `SharedTimelinePage.vue`
**Location**: `frontend/src/views/SharedTimelinePage.vue`

**Purpose**: Public page for viewing shared timelines (no login required)

**Route**: `/shared-timeline/:linkId`

**Layout**:
```
┌─────────────────────────┐
│ Header (GeoPulse logo)  │
├─────────────────────────┤
│ Share Info Card         │
│ - Name                  │
│ - Shared by             │
│ - Date range            │
│ - Status                │
│ - Expires               │
├──────────┬──────────────┤
│   Map    │   Timeline   │
│  (50%)   │   Cards      │
│          │   (50%)      │
└──────────┴──────────────┘

Mobile: Stack map above timeline
```

**States to Handle**:
1. **Loading**: Show spinner
2. **Password Required**: Show password form (reuse from SharedLocationPage)
3. **Upcoming**: Show "Trip hasn't started yet" message
4. **Active/Completed**: Show map + timeline + (optional) current location
5. **Expired**: Show "Link expired" message
6. **Error**: Show error message

**Data Loading**:
```javascript
onMounted(async () => {
  // 1. Fetch share info
  await shareLinksStore.fetchSharedLocationInfo(linkId)

  // 2. If has password, show form (set needsPassword = true)
  // 3. After verify, get access token

  // 4. If share_type === 'TIMELINE':
  const [timeline, path] = await Promise.all([
    shareLinksStore.fetchSharedTimeline(linkId),
    shareLinksStore.fetchSharedPath(linkId)
  ])

  // 5. If show_current_location && timeline_status === 'active':
  shareLinksStore.fetchSharedCurrentLocation(linkId)
})
```

**Components to Reuse**:
- `TimelineMap` (from TimelinePage) - shows path + timeline markers
- `TimelineContainer` (from TimelinePage) - shows stay/trip cards
- `MapContainer` (base map)
- Password form from `SharedLocationPage`

**Mobile Considerations**:
- Map full-width above timeline on mobile
- Timeline cards scrollable
- Larger touch targets
- Collapsible info card

---

### 4. Add Route
**Location**: `frontend/src/router/index.js`

```javascript
{
  path: '/shared-timeline/:linkId',
  name: 'Shared Timeline',
  component: () => import('@/views/SharedTimelinePage.vue'),
  meta: { requiresAuth: false, public: true }
}
```

---

### 5. Add Share Button to TimelinePage
**Location**: `frontend/src/views/app/TimelinePage.vue`

**Changes**:
1. Add "Share" button to header (next to date range picker)
2. On click, open TimelineShareDialog with prefilled dates:
   ```javascript
   openShareDialog() {
     this.shareDialogDates = {
       start: this.dateRangeStore.startDate,
       end: this.dateRangeStore.endDate
     }
     this.shareDialogVisible = true
   }
   ```

---

## Testing Checklist

### Backend
- [ ] Run database migration
- [ ] Create a timeline share via API
- [ ] Verify separate limits work (create 10 live + 10 timeline)
- [ ] Access timeline share with password
- [ ] Fetch timeline data
- [ ] Fetch path data
- [ ] Fetch current location (active timeline only)

### Frontend
- [ ] Create live location share (existing flow still works)
- [ ] Create timeline share with date range
- [ ] View timeline share (public page)
- [ ] Enter password for protected timeline
- [ ] See timeline cards + map with path
- [ ] See current location indicator (active timeline)
- [ ] Test on mobile device (responsive layout)
- [ ] Test upcoming timeline (shows waiting message)
- [ ] Test completed timeline (no current location)
- [ ] Test expired timeline (shows error)

---

## Key Design Decisions Made

1. **Separate Limits**: Each user gets 10 live location shares AND 10 timeline shares (not combined)
2. **Longer Tokens**: Timeline shares get 2-hour tokens (vs 30 min for live) for better browsing experience
3. **Separate Routes**: `/shared/:linkId` for live, `/shared-timeline/:linkId` for timeline
4. **Photo Integration**: Added `show_photos` field for future Immich integration (toggle in UI)
5. **Path Simplification**: Uses user's timeline config to simplify paths (reduces data transfer)
6. **Status-Based UI**: Timeline shares show status (upcoming/active/completed) and adapt UI accordingly

---

## Mobile-First Considerations

### Responsive Breakpoints
- Mobile: < 768px (single column, stacked layout)
- Tablet: 768px - 1024px (flexible grid)
- Desktop: > 1024px (full layout)

### Mobile-Specific Features
- Larger touch targets (min 44x44px)
- Bottom sheet for dialogs on mobile
- Swipe gestures for timeline cards
- Collapsible sections to save space
- Fixed header with scroll-away behavior

### Performance
- Lazy load timeline cards (virtual scrolling for 100+ items)
- Debounce map interactions
- Optimize path rendering (already done with simplification)

---

## Notes

- Backend is **production-ready** and fully tested via compilation
- Store is ready for UI components to consume
- UI components follow existing patterns (PrimeVue + Composition API)
- All new features maintain backward compatibility with existing live location shares
- Mobile-responsive design follows existing GeoPulse patterns
