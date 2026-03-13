# Trip Workspace

Trip Workspace is the per-trip execution page where you plan stops, track visit progress, and compare plan vs actual timeline data.

Route: `/app/trips/:tripId`

## Page structure

Trip Workspace includes:

- Header with trip name, status, date range
- Date range picker scoped to trip range
- Map + timeline/planning panel
- Plan table (`Planned Stops` or `Plan vs Actual` depending on trip status)

## Status-aware behavior

### Upcoming trip

For future trips:

- Only **Plan** tab is shown.
- Planning callout is shown (`Future trip planning mode`).
- Table title is **Planned Stops**.
- `Matched Stay` column is hidden.

Primary workflow:

1. Right-click map.
2. Click **Plan to visit here**.
3. Confirm/edit suggested title.
4. Save plan item.

### Active trip

For in-progress trips:

- **Overview** and **Plan** tabs are available.
- Plan tab shows active planning callout.
- You can continue adding planned stops from map.
- Visit states can be manually adjusted per item.

### Completed trip

For finished trips:

- Plan table title is **Plan vs Actual**.
- `Matched Stay` column is visible.
- Rows show matched stay evidence:
  - stay title
  - timestamp
  - confidence badge (`High`, `Medium`, `Low`)

## Plan item actions

Each plan item supports:

- **Edit**
- **Delete**
- **Mark visited**
- **Mark not visited**
- **Reset** (remove manual override and return to automatic logic)

## Map interactions

- Planned stops are shown on map.
- Right-click planned marker to edit/delete.
- Click row title to focus item on map.

## Visit matching model (user-facing)

GeoPulse continuously matches planned stops with timeline stays (precomputed server-side).

- **Status** column is decision/output.
- **Matched Stay** column is supporting evidence.
- Automatic results can be overridden manually, then reset.

## Photos and timeline context

If Immich integration is enabled and trip range has photos:

- Trip photos can be displayed in workspace context.
- Map photo layer can be used for spatial review.

If Immich is disabled, photo-specific UI is hidden.

## Related pages

- [Trip Plans Management](/docs/user-guide/core-features/trip-plans)
- [Timeline Labels](/docs/user-guide/core-features/timeline-labels)
- [Timeline Overview](/docs/user-guide/core-features/timeline)
