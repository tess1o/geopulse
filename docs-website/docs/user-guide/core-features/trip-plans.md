# Trip Plans Management

Trip Plans are planning workspaces connected to your timeline data. They combine planned stops with actual visits detected from timeline stays.

Open **Trip Plans** from the main menu (`/app/trips`).

## What you can do on Trip Plans page

### Create Trip Plan

1. Click **Create Trip Plan**.
2. Set plan name, date range, color, and optional notes.
3. Save.

When created manually, GeoPulse also creates a managed Timeline Label and links it to the trip.

### Create from Timeline Label

Use **From Timeline Label** to convert an existing completed label into a Trip Plan.

### Search and filters

- Search by name/notes.
- Filter by status:
  - `All statuses`
  - `Upcoming`
  - `Active`
  - `Completed`
  - `Cancelled`

### Row actions

- **Open trip planner**: open workspace (`/app/trips/:tripId`)
- **Open timeline label**: jump to linked label (if linked)
- **Unlink timeline label**
- **Edit trip plan**
- **Delete trip plan**

## Linked label synchronization

If a trip is linked with a Timeline Label:

- Name/date/color stay synchronized.
- Editing trip updates linked label.
- Editing linked label updates trip.

## Delete modes for linked trips

When deleting a linked trip:

- **Delete Trip Plan Only**: removes trip, keeps label unlinked.
- **Delete Trip Plan + Label**: removes both.

## Recommended usage model

- Use **Timeline Labels** for lightweight event/time annotation.
- Use **Trip Plans** when you need planned stops, progress tracking, and plan-vs-actual workflows.

## Related pages

- [Timeline Labels](/docs/user-guide/core-features/timeline-labels)
- [Trip Workspace](/docs/user-guide/core-features/trip-workspace)
