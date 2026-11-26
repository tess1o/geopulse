import { expect } from '@playwright/test';
import { createHash } from 'node:crypto';
import { MapEngineHarness } from '../map-engine-harness.js';
import { TimelinePage } from '../../pages/TimelinePage.js';
import { TestSetupHelper } from '../test-setup-helper.js';
import { buildManagedUser } from '../isolated-user-helper.js';
import { GpsDataFactory } from '../gps-data-factory.js';
import { UserSettingsApi } from '../user-settings-api.js';
import { resolvePageMapMode } from '../map-mode-registry.js';

const STAYS = '.timeline-card--stay, .timeline-card--overnight-stay';
const DAY = '2026-09-15';
const at = time => `${DAY}T${time}:00.000Z`;
const places = { Home: [49.842, 24.031], Bakery: [49.8385, 24.053], Pharmacy: [49.8366, 24.064], Office: [49.835, 24.075] };
const drawer = page => page.locator('.timeline-edit-drawer');
const action = (page, name) => drawer(page).locator('.edit-action-row').filter({ has: page.locator('.edit-action-text > span', { hasText: new RegExp(`^${name}$`) }) });
const iso = value => new Date(value).toISOString();

async function waitJob(page, result, previousJobs = null, committedAt = null) {
  if (result.timelineJobQueued) {
    expect(previousJobs, 'queued wait needs the job inventory taken before the action').not.toBeNull();
    expect(committedAt, 'queued wait needs the commit time of the action').not.toBeNull();
    await expect.poll(async () => {
      const response = await page.request.get('/api/v1/timeline/jobs/history');
      expect(response.ok()).toBe(true);
      const history = await response.json();
      // Only a job that started after the commit is guaranteed to include this correction.
      return history.some(job => job.status === 'COMPLETED' && !previousJobs.includes(job.jobId)
        && Date.parse(job.startTime) >= committedAt);
    }, { timeout: 90000, message: 'queued correction receives a completed successor job' }).toBe(true);
    return;
  }
  const id = result.timelineJobId || result.jobId;
  expect(id).toBeTruthy();
  await expect.poll(async () => {
    const response = await page.request.get(`/api/v1/timeline/jobs/${id}`);
    expect(response.ok()).toBe(true);
    return (await response.json()).status;
  }, { timeout: 90000, message: 'real timeline regeneration completes' }).toBe('COMPLETED');
}

async function rows(db, user) {
  const result = await db.client.query(`
    SELECT 'stay' AS type, timestamp AS start, timestamp + stay_duration * interval '1 second' AS finish,
           location_name AS name, visit_name AS label, favorite_id AS favorite, geocoding_id AS geocoding,
           ST_Y(location) AS latitude, ST_X(location) AS longitude,
           NULL::text AS movement, NULL::text AS source, NULL::bigint AS distance, NULL::float8 AS end_latitude, NULL::float8 AS end_longitude, NULL::jsonb AS route, false AS open_tail
      FROM timeline_stays WHERE user_id = $1
    UNION ALL
    SELECT 'trip', timestamp, timestamp + trip_duration * interval '1 second', NULL, NULL, NULL, NULL,
           ST_Y(start_point), ST_X(start_point), movement_type, movement_type_source, distance_meters, ST_Y(end_point), ST_X(end_point), manual_route, false
      FROM timeline_trips WHERE user_id = $1
    UNION ALL
    SELECT 'gap', start_time, end_time, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
           -- Only the trailing gap after all recorded/manual activity ends at the advancing server clock.
           (SELECT max(timestamp) FROM gps_points WHERE user_id=$1) IS NOT NULL
           AND end_time > GREATEST(
             (SELECT max(timestamp) FROM gps_points WHERE user_id=$1),
             (SELECT max(timestamp + stay_duration * interval '1 second') FROM timeline_stays WHERE user_id=$1),
             (SELECT max(timestamp + trip_duration * interval '1 second') FROM timeline_trips WHERE user_id=$1))
      FROM timeline_data_gaps WHERE user_id = $1 AND end_time IS NOT NULL
    ORDER BY start, type`, [user]);
  return result.rows.map(({ open_tail, ...row }) => ({ ...row, start: iso(row.start), finish: open_tail ? 'ONGOING' : iso(row.finish) }));
}

async function setup(page, db, users, { timezone = 'UTC', overnight = false, zeroDuration = false, recipe = null, date = DAY, endDate = date, preferences = {} } = {}) {
  const timeline = new TimelinePage(page);
  const managed = buildManagedUser(users, { timezone });
  // The seeded range is opened once the recording and its timeline exist.
  await timeline.loginAndNavigate(managed, { dbManager: db, mapMode: await resolvePageMapMode(page), landing: null });
  await UserSettingsApi.updateCurrentUserProfile(page, { timezone, dateFormat: 'YMD', timeFormat: '24h' });
  const user = await db.getUserByEmail(managed.email);
  preferences = { staypointRadiusMeters: 50, staypointMinDurationMinutes: 7, tripDetectionAlgorithm: 'single',
    isMergeEnabled: true, mergeMaxDistanceMeters: 150, mergeMaxTimeGapMinutes: 10, pathSimplificationEnabled: false,
    useVelocityAccuracy: true, staypointVelocityThreshold: 9, staypointMaxAccuracyThreshold: 60,
    gapStayInferenceEnabled: false, gapTripInferenceEnabled: false, dataGapThresholdSeconds: 1800, dataGapMinDurationSeconds: 1800, ...preferences };
  // Keep missing hours as a gap for the manual activity editor.
  await db.client.query(`UPDATE users SET timeline_preferences = COALESCE(timeline_preferences, '{}'::jsonb)
    || $2::jsonb WHERE id=$1`, [user.id, JSON.stringify(preferences)]);
  const favorites = {};
  for (const [name, [latitude, longitude]] of Object.entries(places)) {
    favorites[name] = await TestSetupHelper.createFavoritePoint(db, user.id, { name, latitude, longitude });
  }
  const points = [];
  const point = (position, time, speed = 0) => points.push(GpsDataFactory.createGpsPoint({
    userId: user.id, deviceId: 'timeline-edit-fixture', lat: position[0], lon: position[1], timestamp: time,
    accuracy: 5, battery: 90, velocity: speed, altitude: 280, sourceType: 'OWNTRACKS'
  }));
  const stay = (position, start, end) => {
    for (let time = Date.parse(start); time <= Date.parse(end); time += 60000) point(position, iso(time));
  };
  const drive = (from, to, start, end) => {
    const duration = Date.parse(end) - Date.parse(start);
    for (let offset = 0; offset <= duration; offset += 60000) {
      const ratio = offset / duration;
      point([from[0] + (to[0] - from[0]) * ratio, from[1] + (to[1] - from[1]) * ratio], iso(Date.parse(start) + offset), 45);
    }
  };
  if (recipe) await recipe({ point, stay, drive, places, favorites, db, page, user: user.id });
  else {
  stay(places.Home, at('06:00'), at('08:00'));
  if (zeroDuration) {
    // Different coordinates at the same timestamp leave no recorded travel interval between the stays.
    stay(places.Office, at('08:00'), at('12:00'));
  } else {
    drive(places.Home, places.Bakery, at('08:01'), at('08:15'));
    stay(places.Bakery, at('08:16'), at('08:20'));
    drive(places.Bakery, places.Pharmacy, at('08:21'), at('08:26'));
    stay(places.Pharmacy, at('08:27'), at('08:31'));
    drive(places.Pharmacy, places.Office, at('08:32'), at('08:45'));
    stay(places.Office, at('08:46'), at('12:00'));
    drive(places.Office, places.Home, at('12:01'), at('12:45'));
    stay(places.Home, at('12:46'), at('18:00'));
    stay(places.Office, at(overnight ? '20:00' : '21:00'), overnight ? '2026-09-16T02:00:00.000Z' : at('22:00'));
  }
  }
  // Keep the realtime worker from reading a partially seeded day.
  await db.client.query('BEGIN');
  try {
    await GpsDataFactory.insertGpsData(db, points);
    await db.client.query('COMMIT');
  } catch (error) {
    await db.client.query('ROLLBACK');
    throw error;
  }
  const generated = await page.request.post('/api/v1/timeline/jobs');
  expect(generated.ok()).toBe(true);
  await waitJob(page, await generated.json());
  const url = `/app/timeline?start=${date}&end=${overnight ? '2026-09-16' : endDate}`;
  await page.goto(url);
  await page.waitForLoadState('networkidle');
  if (!recipe) await expect(page.locator(STAYS)).toHaveCount(zeroDuration ? 2 : overnight ? 5 : 4);
  const baseline = await rows(db, user.id);
  if (!recipe) expect(baseline.filter(row => row.type === 'trip')).toHaveLength(zeroDuration ? 1 : 2);
  if (!recipe && !zeroDuration && !overnight) {
    expect(baseline.map(({type, start, finish, name}) => [type, start, finish, name])).toEqual([
      ['stay', at('06:00'), at('08:01'), 'Home'], ['trip', at('08:02'), at('08:46'), null],
      ['stay', at('08:46'), at('12:01'), 'Office'], ['trip', at('12:02'), at('12:46'), null],
      ['stay', at('12:46'), at('18:00'), 'Home'], ['gap', at('18:00'), at('21:00'), null],
      ['stay', at('21:00'), at('22:00'), 'Office'],
      ['gap', at('22:00'), 'ONGOING', null],
    ]);
  }
  return { user: user.id, favorites, baseline, url, timezone, overnight, gps: await gpsFingerprint(db, user.id), date, endDate, preferences };
}

async function open(page, kind, index = 0) {
  await closeHistory(page);
  const selector = { stay: '.timeline-card--stay', trip: '.timeline-card--trip' }[kind];
  await page.locator(selector).nth(index).getByRole('button', { name: { stay: 'Edit visit…', trip: 'Edit trip…' }[kind] }).click();
  await expect(drawer(page)).toBeVisible();
}
async function choosePlace(page, name) {
  if (await page.getByRole('dialog', { name: 'Choose Date', exact: true }).last().isVisible()) await page.keyboard.press('Escape');
  const picker = drawer(page).locator('.place-picker');
  if (!await picker.getByRole('textbox', { name: 'Find a saved or visited place' }).isVisible()) await picker.getByRole('button', { name: 'Change', exact: true }).click();
  await picker.getByRole('textbox', { name: 'Find a saved or visited place' }).fill(name);
  await drawer(page).getByRole('button', { name, exact: true }).click();
  await expect(drawer(page).locator('.place-picker-selected')).toContainText(name);
}
async function review(page) {
  if (await page.getByRole('dialog', { name: 'Choose Date', exact: true }).last().isVisible()) await page.keyboard.press('Escape');
  await expect(drawer(page).getByRole('button', { name: 'Save changes', exact: true })).toHaveCount(0);
  const response = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/v1/timeline/corrections/preview');
  await drawer(page).getByRole('button', { name: 'Review changes', exact: true }).click();
  const preview = await response;
  expect(preview.ok()).toBe(true);
  const { events } = await preview.json();
  expect(events.length).toBeGreaterThan(0);
  for (const event of events) {
    expect(['STAY', 'TRIP', 'DATA_GAP']).toContain(event.type);
    if (event.type === 'TRIP') expect(Date.parse(event.end)).toBeGreaterThanOrEqual(Date.parse(event.start));
    else expect(Date.parse(event.end)).toBeGreaterThan(Date.parse(event.start));
  }
  await expect(drawer(page).locator('.edit-preview')).toContainText('After');
  await expect(drawer(page).locator('.edit-preview')).toBeFocused();
  await expect(drawer(page).getByRole('button', { name: 'Save changes', exact: true })).toBeEnabled();
}
async function save(page, button = drawer(page).getByRole('button', { name: 'Save changes', exact: true })) {
  const refreshed = frontendRefresh(page);
  const previousJobs = await jobHistoryIds(page);
  const response = page.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/v1/timeline/corrections');
  await button.click();
  const saved = await response;
  expect(saved.ok()).toBe(true);
  const committedAt = Date.parse(saved.headers().date);
  const result = await saved.json();
  const edit = result.edits[0];
  expect(edit.id).toBeTruthy();
  expect(edit.status).toBe('IN_EFFECT');
  expect(Date.parse(edit.anchorEnd)).toBeGreaterThanOrEqual(Date.parse(edit.anchorStart));
  if (Date.parse(edit.anchorEnd) === Date.parse(edit.anchorStart)) {
    expect(['SET_TRIP_TYPE', 'RESIZE', 'CLEAR']).toContain(edit.op);
  }
  await waitJob(page, result, previousJobs, committedAt);
  await finishFrontendRefresh(page, refreshed);
  await expect(page.locator('.timeline-edit-drawer:visible, .trip-stay-split-dialog:visible')).toHaveCount(0);
  return edit;
}
async function historyChange(page, edit, operation) {
  const refreshed = frontendRefresh(page);
  const previousJobs = await jobHistoryIds(page);
  if (!await page.locator('.timeline-edit-history').isVisible()) await page.locator('.timeline-history-button').click();
  const history = page.locator('.timeline-edit-history');
  const entry = history.locator(`[data-edit-id="${edit.id}"]`);
  await expect(entry).toBeAttached();

  // The history drawer uses the same list, including the collapsed reverted entries.
  const details = entry.locator('xpath=ancestor::details');
  await expect(async () => {
    if (await details.count() && await details.getAttribute('open') === null) await details.locator('summary').click();
    await expect(entry.getByRole('button', { name: operation, exact: true })).toBeVisible({ timeout: 1000 });
  }).toPass({ timeout: 10000 });
  const response = page.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname === `/api/v1/timeline/corrections/${edit.groupId}/${operation === 'Undo' ? 'undo' : 'restore'}`);
  await entry.getByRole('button', { name: operation, exact: true }).click();
  const changed = await response;
  expect(changed.ok()).toBe(true);
  await waitJob(page, await changed.json(), previousJobs, Date.parse(changed.headers().date));
  await finishFrontendRefresh(page, refreshed);
}
async function assertRendered(page, context, expected) {
  const stays = expected.filter(row => row.type === 'stay');
  const trips = expected.filter(row => row.type === 'trip');
  if (!context.overnight) {
    await expect(page.locator(STAYS)).toHaveCount(stays.length);
    await expect(page.locator('.timeline-card--trip')).toHaveCount(trips.length);
    await expect(page.locator(`${STAYS.split(', ').join(' .location-name, ')} .location-name`)).toHaveText(stays.map(row => row.name));
  }
  for (const row of [...stays, ...trips]) {
    const time = new Intl.DateTimeFormat('en-GB', { timeZone: context.timezone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(row.start));
    const selector = row.type === 'stay' ? STAYS : '.timeline-card--trip, .timeline-card--overnight-trip';
    const card = page.locator(selector).filter({ has: page.locator('.timeline-timestamp', { hasText: time }) }).first();
    await expect(card).toBeVisible();
    if (row.type === 'stay') {
      await expect(card.locator('.location-name')).toHaveText(row.name);
      if (row.label) await expect(card.locator('.visit-label')).toHaveText(row.label);
      else await expect(card.locator('.visit-label')).toHaveCount(0);
    } else await expect(card).toContainText(new RegExp(row.movement.replaceAll('_', ' '), 'i'));
  }
}

async function lifecycle(page, db, context, edit, assertChanged) {
  expect((await corrections(page)).find(e => e.id === edit.id).status).toBe('IN_EFFECT');
  const changed = await rows(db, context.user);
  await assertChanged(changed);
  await assertRendered(page, context, changed);
  expect(changed).not.toEqual(context.baseline);
  await page.reload(); await page.waitForLoadState('networkidle');
  await expect(page.locator('.timeline-history-button')).toBeVisible();
  expect(await rows(db, context.user)).toEqual(changed);
  await assertRendered(page, context, changed);
  await historyChange(page, edit, 'Undo');
  expect(await rows(db, context.user)).toEqual(context.baseline);
  await assertRendered(page, context, context.baseline);
  await page.reload(); await page.waitForLoadState('networkidle');
  await historyChange(page, edit, 'Redo');
  expect(await rows(db, context.user)).toEqual(changed);
  await assertRendered(page, context, changed);
  await page.reload(); await page.waitForLoadState('networkidle');
  const regenerated = await page.request.post('/api/v1/timeline/jobs');
  expect(regenerated.ok()).toBe(true);
  await waitJob(page, await regenerated.json());
  expect(await rows(db, context.user)).toEqual(changed);
  expect(await gpsFingerprint(db, context.user)).toEqual(context.gps);
  await assertChanged(await rows(db, context.user));
  await assertRendered(page, context, changed);
}


export { STAYS, DAY, at, places, drawer, action, iso, waitJob, rows, setup, open, choosePlace, review, save, historyChange, assertRendered, lifecycle };

/**
 * Every recorded GPS column except identity and owner. With portable: true it is what an export carries, as the app
 * stores it: created_at is left out (the receipt time, which an import sets to the import time), and coordinates are
 * compared at the 8 decimals GeoUtils.createPoint stores. The fixture inserts points by SQL with more decimals than any
 * point the app ever stores, so an import of them is normalised like every other point.
 */
export async function gpsFingerprint(db, user, { portable = false } = {}) {
  const point = portable
    ? `to_jsonb(p) - 'user_id' - 'id' - 'created_at' - 'coordinates'
       || jsonb_build_object('latitude', round(ST_Y(p.coordinates)::numeric, 8), 'longitude', round(ST_X(p.coordinates)::numeric, 8))`
    : `to_jsonb(p) - 'user_id' - 'id'`;
  const { rows } = await db.client.query(`SELECT ${point} AS point
    FROM gps_points p WHERE user_id=$1 ORDER BY timestamp, id`, [user]);
  return { count: rows.length, sha256: createHash('sha256').update(JSON.stringify(rows)).digest('hex') };
}
export async function corrections(page) {
  const response = await page.request.get('/api/v1/timeline/corrections');
  expect(response.ok()).toBe(true);
  return response.json();
}
export async function regenerate(page) {
  const response = await page.request.post('/api/v1/timeline/jobs');
  expect(response.ok()).toBe(true);
  await waitJob(page, await response.json());
}
export async function reload(page, context) {
  await page.goto(context.url);
  if (context.url.startsWith('/app/trips/')) await openWorkspaceTimeline(page);
  await expect(page.locator('.timeline-history-button')).toBeVisible();
}
export async function openAt(page, kind, time) {
  await closeHistory(page);
  const selector = kind === 'stay' ? STAYS : '.timeline-card--trip, .timeline-card--overnight-trip';
  const card = page.locator(selector).filter({ has: page.locator('.timeline-timestamp', { hasText: time }) }).first();
  await card.getByRole('button', { name: kind === 'stay' ? 'Edit visit…' : 'Edit trip…', exact: true }).click();
  await expect(drawer(page)).toBeVisible();
}
export async function detail(page, kind, time, name, value) {
  await openAt(page, kind, time); await action(page, name).click();
  if (name === 'Location') await choosePlace(page, value);
  else if (name === 'Movement type') {
    await drawer(page).getByRole('combobox', { name: 'Movement type' }).click();
    await page.getByRole('option', { name: value, exact: true }).click();
  } else await drawer(page).getByRole('textbox').fill(value);
  return save(page);
}
export async function resize(page, kind, time, start, end) {
  await openAt(page, kind, time); await action(page, 'Time').click();
  for (const [name, value] of [['Start', start], ['End', end]]) if (value) {
    await drawer(page).getByRole('combobox', { name, exact: true }).fill(value);
    await drawer(page).getByRole('combobox', { name, exact: true }).press('Escape');
  }
  await review(page); return save(page);
}
export async function remove(page, kind, time, resolution) {
  await openAt(page, kind, time); await action(page, 'Remove').click();
  if (resolution !== 'DISMISS') {
    await drawer(page).getByRole('combobox').click();
    await page.getByRole('option', { name: { GAP: 'Leaving a data gap', EXTEND_PREVIOUS: /Extending the previous event/, EXTEND_NEXT: /Extending the next event/, MERGE: 'Merging compatible neighbours' }[resolution] }).click();
  }
  await review(page); return save(page);
}
export async function merge(page, time, direction = 'NEXT') {
  await openAt(page, 'trip', time); await action(page, 'Merge').click();
  await drawer(page).getByRole('radio', { name: direction === 'NEXT' ? /Merge with the next trip/ : /Merge with the previous trip/ }).click();
  await review(page); return save(page);
}
export const editor = page => page.locator('.missing-activity-editor');
const fillPreviews = new WeakMap();
export async function openFill(page) {
  await closeHistory(page);
  await page.getByRole('button', { name: 'Add missing timeline data', exact: true }).click();
  await expect(editor(page)).toBeVisible();
}
export async function pick(page, picker, name, { searchable = true } = {}) {
  const search = picker.getByRole('textbox', { name: 'Find a saved or visited place' });
  if (!await search.isVisible()) {
    const change = picker.getByRole('button', { name: 'Change', exact: true });
    if (await change.isVisible()) await change.click();
    else await picker.getByRole('button', { name: 'Choose location', exact: true }).click();
  }
  if (searchable) await search.fill(name);
  await picker.getByRole('button', { name, exact: true }).click();
  await expect(picker.locator('.place-picker-selected')).toContainText(name);
}
export async function dismissDatePickers(page) {
  // Escape only an expanded DatePicker input; an unguarded Escape closes the activity dialog.
  const inputs = page.locator('.p-datepicker input[aria-expanded="true"]');
  while (await inputs.count()) {
    const input = inputs.first();
    await input.press('Escape');
  }
  await expect(page.locator('.p-datepicker-panel:visible')).toHaveCount(0);
}
export async function activityTimes(form, start, end) {
  const page = form.page();
  await dismissDatePickers(page);
  for (const [prefix, value] of [['From', start], ['To', end]]) {
    const [date, time] = value.split('T');
    const dateField = form.getByRole('combobox', { name: `${prefix} · Date`, exact: true });
    const timeField = form.getByRole('combobox', { name: `${prefix} · Time`, exact: true });
    await dateField.fill(date); await dismissDatePickers(page); await dateField.blur();
    await expect(dateField).toHaveValue(date);
    const wallTime = time.replace(/\.\d+Z$|Z$/, '');
    await timeField.fill(wallTime); await dismissDatePickers(page); await timeField.blur();
    await expect(timeField).toHaveValue(wallTime);
  }
}
export async function mapClick(page, point, rootSelector = '.missing-activity-editor') {
  const harness = new MapEngineHarness(page);
  await harness.waitForMapReady({ rootSelector, settleMs: 0 });
  const host = harness.getMapHostLocator({ rootSelector });
  await host.scrollIntoViewIfNeeded();
  const fit = page.locator(rootSelector).getByRole('button', { name: 'Show entire route', exact: true });
  if (await fit.isVisible()) await fit.click();
  const id = await host.getAttribute('id');
  await expect.poll(() => page.evaluate(id => Boolean(window.__GP_E2E_MAPS?.[id]), id)).toBe(true);
  // The GPS inspector changes the map's width. Wait for ResizeObserver and camera
  // animation before projecting; coordinates from the previous width can be offscreen.
  const settle = async () => {
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    await expect.poll(() => page.evaluate(id => {
      const registered = window.__GP_E2E_MAPS[id], map = registered.map || registered;
      const container = map.getContainer(), size = map.getSize?.();
      return !map.isMoving?.() && !map._panAnim?._inProgress && !map._animatingZoom
        && (!size || Math.abs(size.x - container.clientWidth) < 2 && Math.abs(size.y - container.clientHeight) < 2);
    }, id)).toBe(true);
  };
  const project = () => page.evaluate(({ id, point }) => {
    const registered = window.__GP_E2E_MAPS[id]; const map = registered.map || registered;
    const projected = map.latLngToContainerPoint ? map.latLngToContainerPoint(point) : map.project([point[1], point[0]]);
    const container = map.getContainer(), rect = container.getBoundingClientRect();
    const x = Math.round(projected.x), y = Math.round(projected.y);
    return { x, y, width: rect.width, height: rect.height };
  }, { id, point });
  await settle();
  let position = await project();
  const inside = p => p.x > 8 && p.x < p.width - 8 && p.y > 8 && p.y < p.height - 8;
  // A new vertex may lie outside the existing route. Use the same zoom gesture as
  // the user, keeping the desired location intact rather than clamping the click.
  for (let attempt = 0; !inside(position) && attempt < 8; attempt++) {
    const zoom = await page.evaluate(id => {
      const registered = window.__GP_E2E_MAPS[id]; return (registered.map || registered).getZoom();
    }, id);
    await host.hover({ position: { x: position.width / 2, y: position.height / 2 } });
    await page.mouse.wheel(0, 300);
    await expect.poll(() => page.evaluate(id => {
      const registered = window.__GP_E2E_MAPS[id]; return (registered.map || registered).getZoom();
    }, id)).toBeLessThan(zoom);
    await settle(); position = await project();
  }
  expect(inside(position), 'Requested geographic point must be visible before clicking').toBe(true);
  const hitsCluster = () => host.evaluate((container, { id, position }) => {
    const registered = window.__GP_E2E_MAPS[id], map = registered.map || registered;
    const rect = container.getBoundingClientRect();
    const target = document.elementFromPoint(
      rect.left + container.clientLeft + position.x,
      rect.top + container.clientTop + position.y
    );
    if (target?.closest('.raw-gps-cluster-icon')) return true;
    return Boolean(map.queryRenderedFeatures?.([position.x, position.y]).some(feature =>
      feature.layer?.id?.startsWith('gp-raw-gps') && feature.properties?.cluster_id != null));
  }, { id, position });
  // Anything Leaflet made clickable under the pixel takes the click instead of the map: a raw GPS cluster zooms (its
  // icon wrapper does not always carry raw-gps-cluster-icon), a start/end or route marker starts a drag. Zoom in on
  // the requested location until the map itself is under it.
  const hitsMarker = () => host.evaluate((container, position) => {
    const rect = container.getBoundingClientRect();
    const target = document.elementFromPoint(
      rect.left + container.clientLeft + position.x,
      rect.top + container.clientTop + position.y
    );
    return Boolean(target?.closest('.leaflet-interactive, .leaflet-marker-icon, .raw-gps-cluster, .maplibregl-marker'));
  }, position);
  for (let attempt = 0; (await hitsCluster() || await hitsMarker()) && attempt < 8; attempt++) {
    const zoom = await page.evaluate(id => {
      const registered = window.__GP_E2E_MAPS[id]; return (registered.map || registered).getZoom();
    }, id);
    await host.hover({ position: { x: position.x, y: position.y } });
    await page.mouse.wheel(0, -300);
    await expect.poll(() => page.evaluate(id => {
      const registered = window.__GP_E2E_MAPS[id]; return (registered.map || registered).getZoom();
    }, id)).toBeGreaterThan(zoom);
    await settle(); position = await project();
    expect(inside(position), 'Requested point remains visible while expanding GPS clusters').toBe(true);
  }
  expect(await hitsCluster(), 'GPS clusters must not intercept the geometry click').toBe(false);
  const route = page.locator(rootSelector).locator('.route-points');
  const routeState = () => route.evaluateAll(nodes => nodes.map(node => node.innerHTML));
  const before = await routeState();
  // Observe the native event before the application handles it. Playwright can
  // round fractional viewport pixels or scroll during actionability checks.
  const observed = await host.evaluateHandle((container, { id, point: requested }) => {
    const registered = window.__GP_E2E_MAPS[id], map = registered.map || registered;
    const state = { point: null };
    const capture = event => {
      const rect = container.getBoundingClientRect();
      const pixel = [
        (event.clientX - rect.left) / (rect.width / container.offsetWidth) - container.clientLeft,
        (event.clientY - rect.top) / (rect.height / container.offsetHeight) - container.clientTop
      ];
      const point = map.mouseEventToLatLng ? map.mouseEventToLatLng(event) : map.unproject(pixel);
      state.point = { latitude: point.lat, longitude: point.lng };
      const target = map.latLngToContainerPoint ? map.latLngToContainerPoint(requested) : map.project([requested[1], requested[0]]);
      state.pixelError = Math.hypot(pixel[0] - target.x, pixel[1] - target.y);
    };
    container.addEventListener('click', capture, { capture: true, once: true });
    state.cleanup = () => container.removeEventListener('click', capture, true);
    return state;
  }, { id, point });
  try {
    await host.click({ position: { x: position.x, y: position.y } });
    const clicked = await observed.evaluate(state => state.point);
    expect(clicked, 'a native map click supplies the independent geographic oracle').not.toBeNull();
    expect(await observed.evaluate(state => state.pixelError), 'the click lands within two pixels of the requested location').toBeLessThanOrEqual(2);
    await settle();
    // Recorded markers open an inspector instead of propagating to the map.
    const select = page.locator(rootSelector).locator('.gps-inspector').getByRole('button', { name: 'Use this GPS point', exact: true });
    if (JSON.stringify(await routeState()) === JSON.stringify(before) && await select.isVisible()) {
      const coordinates = await page.locator(rootSelector).locator('.gps-inspector .raw-gps-popup-grid .gp-map-popup-value').first()
        .locator('.gp-map-popup-value-line').allTextContents();
      await select.click();
      return { latitude: Number(coordinates[0]), longitude: Number(coordinates[1]) };
    }
    return clicked;
  } finally {
    await observed.evaluate(state => state.cleanup());
    await observed.dispose();
  }
}
export async function activity(page, spec, index = 0) {
  const routeClicks = [];
  await dismissDatePickers(page);
  if (index) await editor(page).getByRole('button', { name: 'Add another activity', exact: true }).click();
  const form = editor(page).locator('.activity-form').nth(index);
  await activityTimes(form, spec.start, spec.end);
  await dismissDatePickers(page);
  await form.getByRole('combobox', { name: 'What happened?' }).click();
  await page.getByRole('option', { name: spec.type === 'stay' ? 'Stayed at a place' : 'Travelled', exact: true }).click();
  if (spec.type === 'stay') {
    if (spec.point) {
      await form.getByRole('button', { name: 'Choose on the map', exact: true }).click();
      await mapClick(page, spec.point);
      await form.getByRole('textbox', { name: 'Place name', exact: true }).fill(spec.name);
    } else await pick(page, form.locator('.place-picker'), spec.name);
    if (spec.label) {
      await form.getByRole('button', { name: 'Add a visit label', exact: true }).click();
      await form.getByRole('textbox', { name: 'Visit label (optional)' }).fill(spec.label);
    }
  } else {
    await pick(page, form.getByRole('group', { name: 'Start', exact: true }), spec.from || 'Home');
    await pick(page, form.getByRole('group', { name: 'Destination', exact: true }), spec.to || 'Office');
    await form.getByRole('combobox').last().click();
    await page.getByRole('option', { name: spec.movement || 'Walk', exact: true }).click();
    if (spec.via?.length) {
      await form.getByRole('button', { name: 'Add route points', exact: true }).click();
      for (const [index, point] of spec.via.entries()) {
        routeClicks.push(await mapClick(page, point));
        await expect(form.locator('.route-points > li')).toHaveCount(index + 1);
      }
      await editor(page).getByRole('button', { name: 'Done placing points', exact: true }).click();
      await expect(form.locator('.route-points > li')).toHaveCount(spec.via.length);
    }
  }
  return routeClicks;
}
export async function reviewFill(page) {
  await dismissDatePickers(page);
  const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/timeline/corrections/preview');
  await editor(page).getByRole('button', { name: 'Review changes', exact: true }).click();
  const preview = await response; expect(preview.ok()).toBe(true);
  await expect(editor(page).locator('.activity-review')).toBeVisible();
  const result = await preview.json();
  fillPreviews.set(page, result);
  return result;
}
export async function saveFill(page) {
  const refreshed = frontendRefresh(page);
  const previousJobs = await jobHistoryIds(page);
  const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/timeline/corrections');
  await editor(page).getByRole('button', { name: 'Save changes', exact: true }).click();
  const saved = await response; expect(saved.ok()).toBe(true);
  const result = await saved.json();
  expect(new Set(result.edits.map(e => e.groupId))).toEqual(new Set([result.groupId]));
  await waitJob(page, result, previousJobs, Date.parse(saved.headers().date)); await finishFrontendRefresh(page, refreshed); await expect(editor(page)).toBeHidden();
  return result.edits[0];
}
export async function fill(page, specs) {
  await openFill(page);
  const routeClicks = [];
  for (const [index, spec] of specs.entries()) routeClicks.push(await activity(page, spec, index));
  await reviewFill(page); return { ...await saveFill(page), routeClicks };
}
export const staySpec = (start, end, name = 'Bakery', extra = {}) => ({ type: 'stay', start: at(start), end: at(end), name, ...extra });
export const tripSpec = (start, end, extra = {}) => ({ type: 'trip', start: at(start), end: at(end), from: 'Home', to: 'Office', movement: 'Walk', ...extra });
export async function assertSegment(db, context, type, start, end, extra = {}) {
  expect(await rows(db, context.user)).toContainEqual(expect.objectContaining({ type, start, finish: end, ...extra }));
}
export async function durable(page, db, context, check) {
  await check(await rows(db, context.user));
  const saved = await rows(db, context.user);
  await reload(page, context); await check(await rows(db, context.user));
  await regenerate(page); expect(await rows(db, context.user)).toEqual(saved);
  expect(await gpsFingerprint(db, context.user)).toEqual(context.gps);
}

export async function historyEntry(page, edit) {
  if (!await page.locator('.timeline-edit-history').isVisible()) await page.locator('.timeline-history-button').click();
  const entry = page.locator(`.timeline-edit-history [data-edit-id="${edit.id}"]`);
  await expect(entry).toBeAttached();
  const details = entry.locator('xpath=ancestor::details');
  if (await details.count() && await details.getAttribute('open') === null) await details.locator('summary').click();
  await expect(entry).toBeVisible();
  return entry;
}
export async function closeHistory(page) {
  if (await page.locator('.timeline-edit-history').isVisible()) {
    await page.keyboard.press('Escape'); await expect(page.locator('.timeline-edit-history')).toBeHidden();
  }
}
export function replacementButton(page, groupId) {
  expect(groupId, 'replacement must identify the intended whole group').toBeTruthy();
  const preview = fillPreviews.get(page);
  expect(preview, 'review the draft before confirming replacement').toBeTruthy();
  const groups = (preview.warnings || [])
    .filter(warning => warning.groupId && ['REPLACES_CORRECTION', 'PARTIAL_OVERLAP'].includes(warning.code))
    .map(warning => warning.groupId);
  expect(new Set(groups), 'all replacement warnings must target the intended group').toEqual(new Set([groupId]));
  // The UI renders a warning per member; these buttons all confirm the same atomic group.
  return editor(page).getByRole('button', { name: 'Replace that correction', exact: true }).first();
}
export async function replaceFill(page, groupId) {
  const button = replacementButton(page, groupId);
  const [response] = await Promise.all([
    page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/timeline/corrections/preview'),
    button.click(),
  ]);
  expect(response.request().postDataJSON().replacesGroupId).toBe(groupId);
  expect(response.ok()).toBe(true);
  fillPreviews.set(page, await response.json());
  await expect(editor(page).getByRole('button', { name: 'Save changes', exact: true })).toBeEnabled();
}
export async function changeMinimum(page, minutes) {
  const { TimelinePreferencesPage } = await import('../../pages/TimelinePreferencesPage.js');
  const preferences = new TimelinePreferencesPage(page);
  await preferences.navigate(); await preferences.waitForPageLoad();
  await preferences.setNumberValue('staypointMinDurationMinutes', minutes);
  const response = page.waitForResponse(r => r.request().method() === 'PUT' && new URL(r.url()).pathname === '/api/v1/preferences/timeline');
  await preferences.clickSave();
  // A regeneration confirmation is part of the settings workflow.
  const confirm = page.locator('.p-confirmdialog');
  await expect(confirm).toBeVisible();
  await confirm.getByRole('button', { name: /Save|Confirm|Yes|Apply/, exact: false }).click();
  const saved = await response; expect(saved.ok()).toBe(true);
  await waitJob(page, await saved.json());
}

export async function jobHistoryIds(page) {
  const response = await page.request.get('/api/v1/timeline/jobs/history');
  expect(response.ok()).toBe(true);
  const history = await response.json();
  const current = await page.request.get('/api/v1/timeline/jobs/current');
  expect([200, 204]).toContain(current.status());
  return [...history.map(job => job.jobId), ...(current.status() === 200 ? [(await current.json()).jobId] : [])];
}

export async function openWorkspaceTimeline(page) {
  const lens = page.locator('.trip-rail-lens');
  await expect(lens, 'timeline lens appears after reconstruction creates actual data').toBeVisible();
  await lens.getByText('Timeline', { exact: true }).click();
}

export function frontendRefresh(page) {
  return page.waitForResponse(response => response.request().method() === 'GET'
    && /^\/api\/v1\/(?:timeline|trips\/\d+\/timeline)$/.test(new URL(response.url()).pathname), { timeout: 90000 })
    .then(response => ({ response }), error => ({ error }));
}
export async function finishFrontendRefresh(page, pending) {
  const { response, error } = await pending;
  if (error) throw error;
  expect(response.ok(), 'the browser receives the regenerated timeline').toBe(true);
  const jobDialog = page.locator('.timeline-generation-dialog');
  if (await jobDialog.isVisible()) {
    const close = jobDialog.locator('.p-dialog-footer').getByRole('button', { name: 'Close', exact: true });
    await expect(close).toBeEnabled({ timeout: 30000 }); await close.click();
    await expect(jobDialog).toBeHidden();
  }
}
