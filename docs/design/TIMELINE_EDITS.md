# Timeline edits: one layer for every manual correction

Status: design proposal. The defects found in the existing per-type overrides are being fixed separately on
`fix/timeline-edit-defects`; the unified layer described here is not implemented yet.

Today a timeline correction is implemented once per type: a movement-type override, a trip split, a data gap to
stay conversion, a per-stay name, and a global place rename. Every new manipulation (remove a stay, merge stays,
change times, split a stay, ...) would need its own table, service, re-apply pass, export/import path and undo
endpoint — roughly ten touch points each. This document proposes one `timeline_edits` layer that all of them share,
and lists the manipulations it should unlock.

## 1. Current architecture

### 1.1 Generation pipeline

`StreamingTimelineGenerationService` builds the timeline in one pass:

1. Load GPS points from the requested start (a full rebuild starts at the user's first point).
2. Run the timeline state machine (`TimelineEvent` stay/trip detection).
3. Post-process trips (`tripPostProcessor`).
4. Merge consecutive stays at the same place (`MovementTimelineMergerImpl` / `TimelineMergeAnalysisService`).
5. Persist stays, trips and data gaps.
6. Re-apply manual corrections, in this order:
   - trip → stay → trip splits (`TripStaySplitOverrideService.reapplyManualOverrides`);
   - movement types (`TripMovementTypeOverrideService.reapplyManualOverrides`);
   - data gap → stay conversions (`DataGapStayOverrideService.reapplyManualOverrides`);
   - names given to single stays (`StayNameOverrideService.reapplyManualOverrides`);
   - notes are re-anchored (`TimelineNoteService.reattachAnchoredNotes`).

Every manual correction lives beside the generated timeline and is re-applied *after* persistence. That is the root
of most of the defects found in the audit: the regeneration already decided which stays and trips exist before the
correction can influence them.

### 1.2 Existing correction tables

| Correction | Table | Matching on re-apply |
|---|---|---|
| Movement type | `timeline_trip_movement_overrides` | Trip sharing > 50% of the corrected trip's time, and vice versa, with both ends within 2 km |
| Trip split | `timeline_trip_stay_split_overrides` | Stay near the stored stay interval (±45 min), 0.6–1.8 duration/distance ratios, endpoints within 350 m; replayed in creation order |
| Data gap → stay | `timeline_data_gap_stay_overrides` | Gap boundaries within the tolerance window; merge into a neighbouring stay at the same place |
| Single stay name | `timeline_stay_name_overrides` | Stay sharing > 50% of the named interval, and vice versa |
| Place rename | favourites and geocoding rows | Copy-on-write plus bulk update of generated stays; override names follow the place unless typed for the stay |

Generated stays keep a place snapshot (place id, name and coordinates) so they can be recreated after the place
itself changes or is deleted. Corrections are also exported in
`timeline_overrides.json`, restored by the snapshot importer, and shifted/anonymized by the debug export.

### 1.3 What the audit showed

The post-persist re-apply model causes:

- a restart inside a correction-produced stay (W1) and nested splits being matched against the wrong event (W2);
- an undo that has to regenerate synchronously (W8) and races with the real-time job (W9);
- orphaned corrections that are invisible and keep loading the whole trip history (W7);
- place renames and deletions that never reach correction-owned stays (W5/W6);
- one stay exposing only the first of several corrections (W12).

These are fixed for the existing correction types, but the pattern remains: a new type means another post-persist
pass, another anchor rule and another undo path. The layer below removes that duplication.

## 2. Missing manipulations (ranked by user value)

1. **Reassign a stay to another place.** Pick a nearby favourite, visit or geocoded place.
   `GET /api/v1/timeline/location-lookup` already returns candidates.
2. **Remove a stay or a trip as noise.** Today the only way is destructive deletion of GPS points.
3. **Per-visit rename.** Rename is always global; a stay with a fallback or `HISTORICAL` name cannot be renamed at
   all.
4. **Merge adjacent stays** (`A · trip · A → A`).
5. **Edit stay or trip start/end times.**
6. **Data gap → trip or flight** (airplane mode).
7. **Corrections list and undo history.**
8. **Merge two trips across a false stop.**
9. **Add a manual stay or trip** without synthetic GPS points.
10. **Split a stay.**
11. **Rules**, e.g. “never a stay here” or “A ↔ B is TRAIN”.

## 3. The unified `timeline_edits` layer

### 3.1 Data model

```sql
CREATE TABLE timeline_edits (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind            VARCHAR(40) NOT NULL,
    seq             BIGINT NOT NULL,              -- creation order; edits apply in this order
    interval_start  TIMESTAMPTZ NOT NULL,         -- the time the user acted on
    interval_end    TIMESTAMPTZ NOT NULL,
    payload         JSONB NOT NULL,               -- kind-specific fields
    place_favorite_id   BIGINT REFERENCES favorite_locations(id) ON DELETE SET NULL,
    place_geocoding_id  BIGINT REFERENCES reverse_geocoding_location(id) ON DELETE SET NULL,
    place_latitude      DOUBLE PRECISION,
    place_longitude     DOUBLE PRECISION,
    place_name          VARCHAR(500),
    status          VARCHAR(20) NOT NULL,         -- ACTIVE, SUPERSEDED, REVERTED
    superseded_by   BIGINT REFERENCES timeline_edits(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

`seq` is assigned by a sequence or `max(seq) + 1` per user under the user's timeline lock. `payload` is validated
per kind by a sealed Java type (`MovementTypeEdit`, `PlaceEdit`, `VisitNameEdit`, `ForceStayEdit`, ...), not by an
untyped map.

Generated `timeline_stays` and `timeline_trips` gain a nullable `edit_id`/`edit_ids` reference (an event produced by
an edit records which edit made it). Events the engine produced keep it null.

### 3.2 Edit kinds

| Kind | Class | Meaning |
|---|---|---|
| `SET_MOVEMENT_TYPE` | attribute | Force a trip's movement type |
| `SET_PLACE` | attribute (structural when it changes the event source) | Reassign a stay to a place; can turn a generated stay into a manual one |
| `SET_VISIT_NAME` | attribute | Name this visit (not the place) |
| `FORCE_STAY` | structural | This interval is a stay, whatever the engine produced |
| `FORCE_TRIP` | structural | This interval is a trip (optionally a flight) |
| `REMOVE_EVENT` | structural | Delete a stay/trip and resolve the neighbours |
| `SPLIT_STAY` | structural | Split a stay into stay · trip · stay |
| `SET_TIMES` | structural | Move a stay's or trip's boundaries |
| `MERGE` | structural | Merge adjacent events of the same type |

Existing corrections migrate to these kinds (see §7): movement override → `SET_MOVEMENT_TYPE`, trip split → a
`FORCE_STAY` over the stay interval, gap conversion → `FORCE_STAY` over the gap interval, stay name →
`SET_VISIT_NAME`.

### 3.3 Structural vs attribute edits

- **Structural** edits change which events exist. They run after the trip post-processor and **before** merge, so the
  engine and the merge pass see the corrected timeline.
- **Attribute** edits only change fields of existing events. They run after merge and before persistence.
- A structural edit may also produce attributes (a `FORCE_STAY` carries a place and a name): they are applied when
  the event is built.

Applying structural edits before merge means ordinary merge rules apply to edited events. Merge locks are stored on
the edit: a stay produced by `FORCE_STAY`/`MERGE` has `merge_locked` and is skipped by
`TimelineMergeAnalysisService.canMergeStays`, so an explicit stay is never silently absorbed.

### 3.4 Interval anchoring

Every edit anchors to a time interval, never to a row id:

1. Re-read the events the engine produced for `[interval_start, interval_end]`.
2. For a structural edit, carve the interval out of the produced events. The remainder is rebuilt with
   `TimelineEventFinalizationService` (as `TripStaySplitOverrideService.buildTrip` does today).
3. For an attribute edit, match the event sharing more than 50% of the interval in both directions. Movement types
   additionally require the stored start/end coordinates within 2 km: regeneration and older engines can start a
   trip several hundred metres earlier (see the issue #631 replay), while another trip elsewhere in the same window
   is not the corrected one. If nothing matches, keep the edit `ACTIVE` but mark it `applied = false` in the API; it
   applies again if the event returns.

This is deliberately the same anchor rule the existing movement override uses, so a regenerated stay that moved by a
few minutes still matches, while a different stay never does.

### 3.5 Re-apply and restart

- Edits are applied inside the generation pass, not after persistence. There are no post-persist re-apply calls.
- An edit-produced event is never a restart point: the restart search steps back to the last generated event and
  includes the source interval the edit was made from (the same guard as the fixed trip-split restart).
- Cleanup keeps edit-produced stays until the pass has generated events to replace them with. A pass that produces
  no events (no usable points) leaves them in place instead of deleting the user's work.
- The real-time job replays live batches through the same pass. `immediate apply`, `fullRebuild` and `replayLive`
  must produce identical snapshots; that is the acceptance invariant (§8).

### 3.6 Conflicts and superseding

- Edits apply in `seq` order. A new edit overlapping an older `ACTIVE` structural edit returns `409` with the
  conflicting edit id and interval.
- `supersede=true` marks the older edit `SUPERSEDED` and sets `superseded_by`, then applies the new one.
- Attribute edits on different fields of one event coexist. A second edit on the same field of the same event
  supersedes the first.
- `REMOVE_EVENT` must say how to resolve the hole: `MERGE` (join neighbours of the same type), `EXTEND_PREVIOUS`,
  `EXTEND_NEXT`, or `GAP` (leave a data gap). Without a resolution it is rejected.

The whole write path (`create`, `revert`, `restore`) takes `TimelineUserLock.tryLock` and returns
`409 TIMELINE_REGENERATION_IN_PROGRESS` when the user's timeline is being regenerated.

## 4. API

All endpoints are owner-only and live under `/api/v1`.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/timeline/edits` | Every edit of the signed-in user, oldest first, with `applied` and the event it currently targets |
| `POST` | `/timeline/edits/preview` | Validate an edit and return the resulting event skeleton without saving |
| `POST` | `/timeline/edits` | Create an edit; body has `kind`, interval, payload, `supersede` |
| `DELETE` | `/timeline/edits/{id}` | Revert: delete the edit and schedule regeneration from its interval |
| `POST` | `/timeline/edits/{id}/restore` | Restore a reverted edit |
| `POST` | `/timeline/stays/{id}/edits` | Event-scoped helper: derive the interval, then create |
| `POST` | `/timeline/trips/{id}/edits` | Same for a trip |
| `POST` | `/timeline/data-gaps/{id}/edits` | Same for a data gap |

The existing endpoints become thin adapters over the layer so older clients keep working:

- `PUT /trips/{tripId}/timeline/movement-type` → `SET_MOVEMENT_TYPE`;
- `POST /timeline/trips/{tripId}/split-with-stay` → `FORCE_STAY`;
- `PUT /timeline/data-gaps/{gapId}/stay-conversion` → `FORCE_STAY`;
- `DELETE /timeline/stay-split-overrides/{id}`, `DELETE /timeline/data-gap-overrides/{id}/stay-conversion` →
  `DELETE /timeline/edits/{id}`.

`GET /timeline/overrides` remains available as the corrections list until the UI moves to `/timeline/edits`.

A create request:

```json
{
  "kind": "FORCE_STAY",
  "interval": { "start": "2026-10-08T12:10:00Z", "end": "2026-10-08T12:50:00Z" },
  "place": { "favoriteId": 42, "name": null },
  "payload": { "locationName": "Grandma's" },
  "supersede": false
}
```

A list item:

```json
{
  "id": 17,
  "kind": "FORCE_STAY",
  "interval": { "start": "2026-10-08T12:10:00Z", "end": "2026-10-08T12:50:00Z" },
  "name": "Grandma's",
  "status": "ACTIVE",
  "applied": true,
  "target": { "type": "STAY", "id": 381 }
}
```

## 5. UX

### 5.1 Stay card → “Edit visit”

One dialog with tabs/sections, all backed by the same edit endpoint:

- **Place** — choose a nearby favourite/visit/geocoded place (`GET /timeline/location-lookup`) or pin a point.
- **Name** — “this visit only” (`SET_VISIT_NAME`) or “all visits to this place” (existing place rename).
- **Times** — start/end (`SET_TIMES`).
- **Split** — split this stay into stay · trip · stay (`SPLIT_STAY`).
- **Remove** — with the four hole resolutions (`REMOVE_EVENT`).
- **Merge** — with the next/previous adjacent stay (`MERGE`).

### 5.2 Trip card

- Change movement type (existing).
- Split with a stay (existing).
- Remove as noise.
- Edit start/end times.
- Merge with the next trip across a false stop.

### 5.3 Data gap card

- Convert to stay (existing).
- Convert to trip/flight (`FORCE_TRIP`).

### 5.4 Corrections panel and undo

- A panel lists every edit with its interval, kind, status and `applied`; orphaned edits are visible and removable.
- Creating or reverting an edit shows an undo toast. Reverting is the existing `DELETE`, so undo is immediate in the
  model and refreshes once the background regeneration finishes (the `timelineJobId` pattern).
- A stay with several edits shows one action per edit, not one action per stay.

### 5.5 Read-only rules

Edit controls are rendered only for the owner:

- public share links and the shared timeline page are always read-only;
- a trip workspace is read-only for collaborators with the `VIEW` role;
- the demo user is read-only;
- API endpoints enforce the same rule; hiding a control is not authorization.

## 6. Export, import, snapshot and debug export

- Export `timeline_edits.json` (`ExportImportConstants.FileNames.TIMELINE_EDITS`) with ids, `seq`, kinds, intervals,
  payloads and place snapshots. Export in `seq` order; import in the same order so nested structural edits stay
  valid.
- Keep exporting `timeline_overrides.json` for one compatibility window. The importer converts it to edits when
  `timeline_edits.json` is absent, then the next export writes only the new file.
- Snapshot restore imports timeline rows first, then edits, then lets the next regeneration re-apply them. The
  snapshot clearer deletes `timeline_edits` with the other timeline tables.
- Debug export shifts and anonymizes edit intervals with the other timeline data and anonymizes place fields and
  visit names exactly as it does stay names today.
- A debug export must round-trip to the same timeline, including edits.

## 7. Migration from the existing override tables

`V36.80.0` already carries the defect fixes (`source_trip_movement_type`, `stay_location_name_custom`,
`timeline_stay_name_overrides`). The move to the unified layer is a later migration:

1. Create `timeline_edits` and add `edit_id` to generated events.
2. Copy corrections in this order:
   - `timeline_trip_stay_split_overrides` → `FORCE_STAY` on `[stay_start_time, stay_end_time]`, carrying the place
     snapshot and the movement type; the split's source trip interval is kept in the payload so undo can restore it;
   - `timeline_data_gap_stay_overrides` → `FORCE_STAY` on the converted gap interval;
   - `timeline_trip_movement_overrides` → `SET_MOVEMENT_TYPE` on the source trip interval;
   - `timeline_stay_name_overrides` → `SET_VISIT_NAME` on the named interval.
3. Mark nested splits by applying parent splits first (`seq` from the old ids), which preserves the fix for W2.
4. Run a full regeneration.
5. Keep the old tables read-only for one release, then drop them and delete the three post-persist re-apply services.

Do not create a legacy alias on the API (`/stay-split-overrides` etc. are removed after the adapter window), matching
the project rule that clients migrate to the versioned API rather than keeping aliases indefinitely.

## 8. Test strategy

For every edit kind:

- **Invariant:** immediate apply == `fullRebuild` == `replayLive` with batch sizes 1, 5 and 15.
- **Anchoring:** a regeneration that moves an event by a few minutes still applies the edit; a different event at a
  similar time does not.
- **Nested edits:** parent and child structural edits both survive a rebuild.
- **Revert/restore:** reverting removes only that edit; restoring brings it back; an edit on a stay with several
  edits reverts independently.
- **Conflicts:** overlapping edits return `409` unless `supersede=true`; after superseding only the newer edit is
  applied.
- **Orphans:** an edit whose event disappeared is listed with `applied=false`, does not load all trips/gaps on every
  regeneration, and can be removed.
- **Places:** renaming and deleting a favourite/geocoding result reaches all edits; a name typed for one visit is
  kept.
- **Read-only:** shared timelines, `VIEW` collaborators and the demo user get no edit controls and `403` from the
  API.
- **Transfer:** export → import into a fresh user reproduces the timeline; snapshot restore and debug-export
  shift/anonymize preserve edits.
- **Frontend:** vitest component/contract tests for the edit dialog and read-only gating; no browser-level test is
  required for these paths.

Existing defect suites (`ManualStayRestartIntegrationTest`, `NestedTripSplitIntegrationTest`,
`SplitTripMovementTypeIntegrationTest`, `ImportedTimelineEditsIntegrationTest`,
`RenamedPlaceInManualStaysIntegrationTest`, `DeletedPlaceInManualStaysIntegrationTest`,
`OrphanedOverridesIntegrationTest`, `UndoInBackgroundIntegrationTest`,
`ManualEditDuringRegenerationIntegrationTest`, `TimelineReconstructionIntegrationTest`,
`SharedTimelineCorrectionsIntegrationTest`, `StayWithSeveralCorrectionsIntegrationTest`) must stay green while the
layer replaces the services.

## 9. Phased rollout

### Phase 0 — defect fixes (in progress)

The existing corrections are fixed on their current tables: restart guard, replay order, movement type through a
split, import order, place rename/delete, bounded candidate queries, asynchronous undo, lock + change event,
reconstruction names and movement types, shared DTO hardening, multiple corrections per stay.

### Phase 1 — the layer and the first two edits

- Add `timeline_edits`, `TimelineEditApplier`, the API and the adapters.
- Port `SET_MOVEMENT_TYPE` and `SET_VISIT_NAME`.
- Add “Edit visit” with Place and Name; add the corrections panel.
- Critical files:
  - new `backend/.../streaming/edits/*`;
  - `StreamingTimelineGenerationService`, `StreamingTimelineAggregator`;
  - `TimelineStayRepository`, `TimelineTripRepository`;
  - `GeoPulseImportStrategy`, `GeoPulseTimelineDetailsImporter`, `GeoPulseTimelineSnapshotImporter`;
  - `frontend/src/components/timeline/{StayCard,TimelineContainer}.vue`,
    `frontend/src/stores/timeline.js`.

### Phase 2 — structural edits

- `FORCE_STAY`, `FORCE_TRIP`, `REMOVE_EVENT`, `SPLIT_STAY`, `SET_TIMES`, `MERGE`.
- Apply before merge; add merge locks, conflict/supersede rules and hole resolutions.
- Migrate the split and gap overrides, then retire their services.
- Add remove/merge/edit-times actions and gap → trip/flight.

### Phase 3 — cheap undo everywhere

- Apply edits and undo on a bounded window synchronously; keep the background job for large ranges.
- Add the undo toast and the corrections panel filters.

### Phase 4 — rules

- Area rules (“never a stay here”) and route rules (“A ↔ B is TRAIN”).
- Rules are stored as edits with a rule kind and a scope, and are evaluated in the same applier pass.
