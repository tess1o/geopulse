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

## 10. Open questions

These decisions were found reviewing this design against the current generation, real-time and safety-net code. Each
changes the schema, the applier, the API or the rollout, so it should be settled before Phase 1 implementation. The
recommendation is a starting point, not a decision.

| # | Question | Blocks |
|---|---|---|
| 10.1 | Anchor interval vs target interval | Schema, undo, chained edits |
| 10.2 | Is the applier a fold or a match pass? | Applier contract, nesting |
| 10.3 | May attributes target structurally produced events? | Conflict rules, split flow |
| 10.4 | Does `DELETE` revert or delete? | Undo/restore, schema |
| 10.5 | Is `SET_PLACE` attribute or structural? | Ordering, merge locks |
| 10.6 | How are several edits on one event represented? | Schema, DTOs, panel |
| 10.7 | Do merge locks belong to the domain model? | Merge pass, tests |
| 10.8 | Payload versioning and interval-less rules | Schema evolution, Phase 4 |
| 10.9 | How are manual events built without GPS points? | Phase 2 feasibility |
| 10.10 | How do structural edits interact with data gaps? | Pass ordering, gap service |
| 10.11 | How are edits filtered to the regeneration window? | Performance, repository |
| 10.12 | How is the safety net made edits-aware? | Test strategy, data loss |
| 10.13 | How does the parked/latest-only path preserve edits? | Real-time correctness |
| 10.14 | Is the undo regeneration durable? | Write path, restart |
| 10.15 | One explicit pass request? | Phase 1 files, testability |
| 10.16 | Migration and coexistence per phase | Phase 1/2 scope, §7 |
| 10.17 | Importing edits into an existing user | §6 importer |
| 10.18 | Authorization of edit-adjacent helpers | §4, §5.5 |

### 10.1 Anchor interval vs target interval

`interval_start/end` is defined as "the time the user acted on", but `SET_TIMES` has a source interval and target times,
`MERGE` covers several events, and `REMOVE_EVENT` changes neighbours outside the event's interval. Re-applying against
the target instead of the source changes which event matches after regeneration; chained edits (edit times twice, then
name) have the same problem.

- Options: store the source/anchor interval and put target values in `payload`; add `anchor_*` and `target_*` columns;
  store a snapshot of the source events.
- Recommendation: store `anchor_start/end` in engine time, target values in `payload`, and an `affected_from/to` range
  for undo and restart. State that re-anchoring always uses the anchor, never the target.
- Decides: schema, §3.4, §3.5, undo range, chained edits.

### 10.2 Is the applier a fold or a match pass?

§3.4 says to re-read the events the engine produced and match each edit against them. If every edit matches the engine
output independently, the second edit on an already-edited event (a second `SET_TIMES`, a name on a split-produced stay,
a nested split) anchors against an event that no longer exists or against the wrong one. This is the generalized W2.

- Options: match every edit against engine events; apply edits as a `seq`-ordered fold over a mutable event list.
- Recommendation: fold. Each edit anchors against the list as left by the previous edits. Document this as the applier
  contract, not an implementation detail.
- Decides: applier design, conflict rules, test matrix.

### 10.3 May attributes target structurally produced events?

§3.6 returns `409` when a new edit overlaps an older `ACTIVE` structural edit. Read literally, naming or placing the
stay produced by `FORCE_STAY`/`SPLIT_STAY` would be rejected — that is the existing split-with-stay flow.

- Options: keep the rule and require `supersede=true` (which would remove the split); define an explicit matrix.
- Recommendation: structural × structural conflicts/supersedes; attribute edits may target a structurally produced
  event and order after it; same-field attributes on the same event supersede; an attribute whose target is consumed
  by a later structural edit becomes `applied=false` or conflicts explicitly.
- Decides: §3.6, API error semantics, UI flows.

### 10.4 Does `DELETE` revert or delete?

§3.1 has a `REVERTED` status and §4 has `POST /{id}/restore`, but `DELETE /timeline/edits/{id}` says it deletes the edit.
A deleted row cannot be restored.

- Options: soft revert (`status=REVERTED`, keep the row, hard-delete after a retention period); explicit `/revert` and
  `/restore` endpoints with `DELETE` reserved for cleanup; drop restore.
- Recommendation: soft revert. Keep the row for the corrections panel and undo; purge after a retention window or on
  export cleanup.
- Decides: schema, §4, undo semantics.

### 10.5 Is `SET_PLACE` attribute or structural?

§3.2 calls it "attribute (structural when it changes the event source)". Attributes run after merge, but changing the
event source can turn a generated stay into a manual, merge-locked one, which merge must see.

- Options: always structural (runs before merge); attribute without changing merge identity; split into `SET_PLACE`
  (attribute) and `MAKE_MANUAL` (structural).
- Recommendation: always structural if it can change the event source/merge lock; otherwise keep it attribute and state
  that place changes never affect merging.
- Decides: §3.2, §3.3, applier ordering.

### 10.6 How are several edits on one event represented?

§3.1 adds `edit_id`/`edit_ids` to generated events, but does not say what an id means when a structural edit produces an
event and an attribute edit later changes it, or when a merge combines edited events.

- Options: one owning edit (`edit_id`, the event producer) plus a computed `edited_by` list in the API; a join table;
  an `edit_ids` array on the row.
- Recommendation: `edit_id` = the edit that produced the event (null for engine events); the API exposes every active
  edit targeting it. Keep storage simple and derive the list.
- Decides: schema, DTOs, corrections panel.

### 10.7 Do merge locks belong to the domain model?

§3.3 says a `FORCE_STAY`/`MERGE` stay is `merge_locked` and is skipped by `TimelineMergeAnalysisService.canMergeStays`,
but that method only sees domain `Stay` objects, which have no such flag.

- Options: add a flag to `Stay` (and maybe `Trip`); keep a side set of locked event identities passed to the merge pass;
  lock by edit interval in the merger.
- Recommendation: add `mergeLocked` to the domain event (excluded from equality) and pass it through merge; specify what
  happens when merge is disabled in config.
- Decides: merge pass, domain model, tests.

### 10.8 Payload versioning and interval-less rules

`payload` is JSONB validated by sealed types, but has no schema version; Phase 4 rules have no natural interval, while
`interval_start/end` is `NOT NULL`.

- Options: add `payload_version`; keep a kind-specific version inside the payload; make the interval nullable for rules
  or store rules in a separate table.
- Recommendation: add `payload_version` now; either make the interval nullable for rule kinds or give rules a scope that
  maps to an interval. Do not overload the payload silently across releases.
- Decides: schema evolution, Phase 4.

### 10.9 How are manual events built without GPS points?

`FORCE_TRIP` and manual stays have no GPS path, but persistence expects rich `Trip` data: distance, duration, GPS
statistics, altitude, water classification and movement type (`StreamingPersistenceManager.persistTripsWithGpsStats`).
`TimelineEventFinalizationService.finalizeTripForGap` can build a trip from gap boundary points, but not from an
arbitrary user-drawn interval.

- Options: derive what is possible from points inside the interval and use explicit unknown/zero values otherwise;
  require at least boundary points; store a manual flag and render statistics as unavailable.
- Recommendation: specify the fallbacks and mark manual events explicitly in the API; spike this before Phase 2, since
  it is the largest unknown in `FORCE_TRIP`.
- Decides: Phase 2 feasibility, DTOs, UI.

### 10.10 How do structural edits interact with data gaps?

Data gaps are already `TimelineEvent`s in the event list (`RawTimeline.fromEvents` partitions `DATA_GAP`), so structural
edits can carve or replace them. The remaining gap is the ongoing gap: `checkAndCreateOngoingDataGap` runs after
persistence and can recreate a gap over a `FORCE_STAY`/`FORCE_TRIP`, or over a `REMOVE_EVENT` resolved as `GAP`.

- Options: make the ongoing-gap check edit-aware and exclude edited intervals; let the applier create the gap event for
  `REMOVE_EVENT`/`GAP`; run the ongoing-gap check before structural edits and let edits carve it.
- Recommendation: keep gap events in the pass, have `REMOVE_EVENT`/`GAP` create a gap event, and make the post-persist
  ongoing-gap check skip intervals claimed by an active edit.
- Decides: pass ordering, data gap service, tests.

### 10.11 How are edits filtered to the regeneration window?

The applier must load only edits intersecting the regeneration window. The existing re-apply methods iterate all
corrections for the user (`TripStaySplitOverrideService.reapplyManualOverrides`,
`StayNameOverrideService.reapplyManualOverrides`), so a real-time pass would otherwise replay all history.

- Options: query edits by interval overlap; maintain a per-window cache; apply all and filter in memory.
- Recommendation: query by overlap with an index on `(user_id, interval_end, interval_start)`, and widen by one event on
  each side for structural effects. Add this to §3.5 and the test plan.
- Decides: performance, index, repository API.

### 10.12 How is the safety net made edits-aware?

The edits invariant names immediate, `fullRebuild` and `replayLive`. The nightly safety net is a third generation path
that rebuilds and compares snapshots. For an edited timeline it must report `UNCHANGED`; if the applier misses an edit,
the safety net will "correct" the timeline by removing the edit's effect.

- Options: exclude edited users from the safety net; include edit ownership in the snapshot and fail loudly; add a
  dedicated invariant test and keep the safety net.
- Recommendation: keep the safety net, add the `UNCHANGED` invariant, and include edit ids/place/name/movement in the
  snapshot so edit loss is detected rather than silently corrected.
- Decides: §8 test strategy, safety net changes.

### 10.13 How does the parked/latest-only path preserve edits?

When a rebuild request keeps failing, `RealtimeTimelineProcessor` rebuilds only from `now` with
`includePendingRebuild=false`. Edits anchored near the present must still apply in that path, and an edit-produced stay
must not be deleted by cleanup.

- Recommendation: apply edits in every pass, including the parked/latest-only path; add a test for an edit anchored in
  the latest window while a request is parked.
- Decides: §3.5 cleanup rules, real-time tests.

### 10.14 Is the undo regeneration durable?

Creating, reverting and restoring an edit must write the durable `timeline_rebuild_requests` row in the same
transaction, not only schedule an async job; otherwise a restart between commit and scheduling loses the regeneration.

- Recommendation: state this explicitly for all three write paths, matching the existing GPS edit/delete and override
  reset flows.
- Decides: §3.6, §4, reliability.

### 10.15 One explicit pass request?

`StreamingTimelineGenerationService` has six overloads with implicit `restartPoint`/`includePendingRebuild` defaults; the
real-time job and safety net use `BEFORE_PREVIOUS_STAY`, while manual/async jobs use `LATEST_STAY`. The edits layer adds
more call sites and makes this divergence harder to see.

- Options: keep the overloads; add a single `TimelinePassRequest` record with required policy fields and pass the loaded
  edits through it.
- Recommendation: consolidate before Phase 1. Make restart policy, `includePendingRebuild`, replay mode and the edit set
  explicit.
- Decides: Phase 1 critical files, testability.

### 10.16 Migration and coexistence per phase

Phase 1 ports `SET_MOVEMENT_TYPE` and `SET_VISIT_NAME` while split/gap stay on the old tables; §7 later copies all four.
The document does not say which model is authoritative during the window, what happens to legacy movement/name rows, or
how relative order across the old tables is reconstructed. Current runtime order is split → movement → gap → name; the
proposed copy order is split → gap → movement → name.

- Options: keep all old tables authoritative until Phase 2 and introduce the new table read-only; per-kind backfill with
  dual-write and snapshot comparison; migrate all four in one step.
- Recommendation: infrastructure + one structural slice in Phase 1, old tables authoritative; migrate all four in
  Phase 2 with a seeded equivalence test and a full regeneration.
- Decides: Phase 1/2 scope, §7, rollback plan.

### 10.17 Importing edits into an existing user

`timeline_edits.json` is imported in `seq` order into a fresh user, but the document does not define import into an
existing user, id remapping, or conflicts with existing edits.

- Recommendation: define merge-vs-replace semantics, remap ids, preserve `seq` order, and reject/flag conflicts the same
  way as create.
- Decides: §6, importer.

### 10.18 Authorization of edit-adjacent helpers

The owner-only rule is stated, but the event-scoped helpers (`POST /timeline/stays/{id}/edits`, etc.) and
`GET /timeline/location-lookup` also need the same owner/shared-timeline checks.

- Recommendation: reuse one authorization helper and add contract tests for shared links, `VIEW` collaborators and the
  demo user.
- Decides: §4, §5.5.
