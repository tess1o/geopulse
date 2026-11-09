import { test, expect } from '../fixtures/isolated-fixture.js';
import { TimelinePage } from '../pages/TimelinePage.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { buildManagedUser } from '../utils/isolated-user-helper.js';
import { GpsDataFactory } from '../utils/gps-data-factory.js';
import { UserSettingsApi } from '../utils/user-settings-api.js';
import { resolvePageMapMode } from '../utils/map-mode-registry.js';
import { ShareLinkFactory } from '../utils/share-link-factory.js';

const STAYS = '.timeline-card--stay, .timeline-card--overnight-stay';
const DAY = '2026-09-15';
const at = time => `${DAY}T${time}:00.000Z`;
const places = { Home: [49.842, 24.031], Bakery: [49.8385, 24.053], Pharmacy: [49.8366, 24.064], Office: [49.835, 24.075] };
const drawer = page => page.locator('.timeline-edit-drawer');
const action = (page, name) => drawer(page).locator('.edit-action-row').filter({ has: page.locator('.edit-action-text > span', { hasText: new RegExp(`^${name}$`) }) });
const iso = value => new Date(value).toISOString();

async function waitJob(page, result) {
  expect(result.timelineJobQueued, 'isolated edit should start a regeneration job').not.toBe(true);
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
           NULL::text AS movement, NULL::text AS source, NULL::bigint AS distance, NULL::float8 AS end_latitude, NULL::float8 AS end_longitude
      FROM timeline_stays WHERE user_id = $1
    UNION ALL
    SELECT 'trip', timestamp, timestamp + trip_duration * interval '1 second', NULL, NULL, NULL, NULL,
           ST_Y(start_point), ST_X(start_point), movement_type, movement_type_source, distance_meters, ST_Y(end_point), ST_X(end_point)
      FROM timeline_trips WHERE user_id = $1
    UNION ALL
    SELECT 'gap', start_time, end_time, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
      FROM timeline_data_gaps WHERE user_id = $1 AND end_time <= $2
    ORDER BY start, type`, [user, at('23:59')]);
  return result.rows.map(row => ({ ...row, start: iso(row.start), finish: iso(row.finish) }));
}

async function setup(page, db, users, { timezone = 'UTC', overnight = false, zeroDuration = false } = {}) {
  const timeline = new TimelinePage(page);
  const managed = buildManagedUser(users, { timezone });
  await timeline.loginAndNavigate(managed, { dbManager: db, mapMode: await resolvePageMapMode(page) });
  await UserSettingsApi.updateCurrentUserProfile(page, { timezone, dateFormat: 'YMD', timeFormat: '24h' });
  const user = await db.getUserByEmail(managed.email);
  // Keep missing hours as a gap for the manual activity editor.
  await db.client.query(`UPDATE users SET timeline_preferences = COALESCE(timeline_preferences, '{}'::jsonb)
    || '{"gapStayInferenceEnabled":false,"gapTripInferenceEnabled":false,"dataGapThresholdSeconds":1800}'::jsonb WHERE id=$1`, [user.id]);
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
  const url = `/app/timeline?start=${DAY}&end=${overnight ? '2026-09-16' : DAY}`;
  await page.goto(url);
  await page.waitForLoadState('networkidle');
  await expect(page.locator(STAYS)).toHaveCount(zeroDuration ? 2 : overnight ? 5 : 4);
  const baseline = await rows(db, user.id);
  expect(baseline.filter(row => row.type === 'trip')).toHaveLength(zeroDuration ? 1 : 2);
  return { user: user.id, favorites, baseline, url, timezone, overnight };
}

async function open(page, kind, index = 0) {
  const selector = { stay: '.timeline-card--stay', trip: '.timeline-card--trip' }[kind];
  await page.locator(selector).nth(index).getByRole('button', { name: { stay: 'Edit visit…', trip: 'Edit trip…' }[kind] }).click();
  await expect(drawer(page)).toBeVisible();
}
async function choosePlace(page, name) {
  if (await page.getByRole('dialog', { name: 'Choose Date', exact: true }).last().isVisible()) await page.keyboard.press('Escape');
  await drawer(page).getByRole('textbox', { name: 'Find a saved or visited place' }).fill(name);
  await drawer(page).getByRole('button', { name, exact: true }).click();
  await expect(drawer(page).locator('.place-picker-selected')).toContainText(name);
}
async function review(page) {
  if (await page.getByRole('dialog', { name: 'Choose Date', exact: true }).last().isVisible()) await page.keyboard.press('Escape');
  await expect(drawer(page).getByRole('button', { name: 'Save changes', exact: true })).toHaveCount(0);
  const response = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/v1/timeline/edits/preview');
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
  const response = page.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/v1/timeline/edits');
  await button.click();
  const saved = await response;
  expect(saved.ok()).toBe(true);
  const result = await saved.json();
  expect(result.edit.id).toBeTruthy();
  expect(result.edit.status).toBe('ACTIVE');
  expect(Date.parse(result.edit.anchorEnd)).toBeGreaterThanOrEqual(Date.parse(result.edit.anchorStart));
  if (Date.parse(result.edit.anchorEnd) === Date.parse(result.edit.anchorStart)) {
    expect(['SET_MOVEMENT_TYPE', 'SET_TIMES', 'REMOVE_EVENT']).toContain(result.edit.kind);
  }
  await waitJob(page, result);
  await expect(page.locator('.timeline-edit-drawer:visible, .trip-stay-split-dialog:visible')).toHaveCount(0);
  return result.edit;
}
async function historyChange(page, edit, operation) {
  await page.locator('.timeline-history-button').click();
  const history = page.locator('.timeline-edit-history');
  const entry = history.locator(`[data-edit-id="${edit.id}"]`);
  await expect(entry).toBeAttached();
  await expect(entry.locator('.change-value')).not.toHaveCount(0);
  // The history drawer uses the same list, including the collapsed reverted entries.
  const details = entry.locator('xpath=ancestor::details');
  await expect(async () => {
    if (await details.count() && await details.getAttribute('open') === null) await details.locator('summary').click();
    await expect(entry).toBeVisible({ timeout: 1000 });
  }).toPass({ timeout: 10000 });
  const response = page.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname === `/api/v1/timeline/${edit.groupId ? `activity-corrections/${edit.groupId}` : `edits/${edit.id}`}/${operation === 'Undo' ? 'revert' : 'restore'}`);
  await entry.getByRole('button', { name: operation, exact: true }).click();
  const changed = await response;
  expect(changed.ok()).toBe(true);
  await waitJob(page, await changed.json());
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
  await expect.poll(async () => (await db.client.query('SELECT count(*)::int AS n FROM timeline_edit_applications WHERE edit_id=$1', [edit.id])).rows[0].n).toBe(1);
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
  await assertChanged(await rows(db, context.user));
  await assertRendered(page, context, changed);
}

const scenarios = ['movement', 'place', 'label', 'times', 'split stay',
  'merge previous', 'merge next', 'remove gap', 'remove previous', 'remove next', 'remove merge', 'trip stop'];

test.describe('Timeline Edit desktop release checks', () => {
  test.setTimeout(240000);
  test.use({ timezoneId: 'America/New_York', viewport: { width: 1440, height: 1000 } });
  for (const correction of ['movement', 'times', 'remove']) {
    test(`zero-duration transition: ${correction} survives regeneration, reload, undo and redo`, async ({ page, dbManager, isolatedUsers }, testInfo) => {
      const context = await setup(page, dbManager, isolatedUsers, { zeroDuration: true });
      const trip = context.baseline.find(row => row.type === 'trip');
      expect(trip).toMatchObject({ start: at('08:00'), finish: at('08:00'), movement: 'UNKNOWN', source: 'AUTO' });
      expect(Number(trip.distance)).toBeGreaterThan(0);
      const generated = await dbManager.client.query('SELECT explanation FROM timeline_trips WHERE user_id=$1', [context.user]);
      expect(generated.rows[0].explanation.start.code).toBe('CONTINUITY');
      const card = page.locator('.timeline-card--unrecorded');
      await expect(card).toHaveCount(1);
      await expect(card).toContainText('Unrecorded transition');
      await expect(card).toContainText('Duration: Unknown');
      await expect(card).toContainText('Estimated distance:');
      await expect(card.getByRole('note')).toContainText('no travel was recorded');
      await expect(card.locator('.trip-hint')).toHaveCount(0);
      await testInfo.attach('unrecorded-transition', { body: await card.screenshot(), contentType: 'image/png' });
      await open(page, 'trip');
      await expect(action(page, 'Add a stop')).toHaveCount(0);
      await expect(action(page, 'Merge')).toHaveCount(0);
      if (correction === 'movement') {
        await action(page, 'Movement type').click();
        await drawer(page).getByRole('combobox', { name: 'Movement type' }).click();
        await page.getByRole('option', { name: 'Walk', exact: true }).click();
      } else if (correction === 'times') {
        await action(page, 'Time').click();
        await drawer(page).getByRole('combobox', { name: 'Start', exact: true }).fill(`${DAY} 07:59`);
        await drawer(page).getByRole('combobox', { name: 'End', exact: true }).fill(`${DAY} 08:01`);
        await review(page);
      } else {
        await action(page, 'Remove').click();
        await expect(drawer(page)).toContainText('without changing either stay');
        await expect(drawer(page).getByRole('combobox')).toHaveCount(0);
        await review(page);
      }
      const edit = await save(page);
      expect(edit.anchorStart).toBe(edit.anchorEnd);
      if (correction === 'remove') expect(edit.payload.resolution).toBe('DISMISS');
      const saved = await rows(dbManager, context.user);
      const regenerated = await page.request.post('/api/v1/timeline/jobs');
      expect(regenerated.ok()).toBe(true);
      await waitJob(page, await regenerated.json());
      expect(await rows(dbManager, context.user)).toEqual(saved);
      await page.reload(); await page.waitForLoadState('networkidle');
      await lifecycle(page, dbManager, context, edit, async values => {
        const trips = values.filter(row => row.type === 'trip');
        const stays = values.filter(row => row.type === 'stay');
        expect(values.filter(row => row.type === 'gap')).toHaveLength(0);
        if (correction === 'movement') {
          expect(trips).toEqual([{ ...trip, movement: 'WALK', source: 'MANUAL' }]);
          expect(stays).toEqual(context.baseline.filter(row => row.type === 'stay'));
          await expect(card).toContainText('Walk');
          await expect(card).toContainText('Duration: Unknown');
        } else if (correction === 'times') {
          expect(trips).toHaveLength(1);
          expect(trips[0]).toMatchObject({ start: at('07:59'), finish: at('08:01') });
          expect(stays[0].finish).toBe(at('07:59'));
          expect(stays[1].start).toBe(at('08:01'));
          await expect(card).toHaveCount(0);
        } else {
          expect(trips).toHaveLength(0);
          expect(stays).toEqual(context.baseline.filter(row => row.type === 'stay'));
          await expect(card).toHaveCount(0);
        }
      });
    });
  }
  for (const scenario of scenarios) {
    test(`${scenario}: real save, persisted values, reload, undo and redo`, async ({ page, dbManager, isolatedUsers }) => {
      const context = await setup(page, dbManager, isolatedUsers);
      const office = context.baseline.find(row => row.type === 'stay' && row.start === at('08:46'));
      const trip = context.baseline.find(row => row.type === 'trip');
      const lastTrip = context.baseline.filter(row => row.type === 'trip').at(-1);
      let check;
      if (scenario === 'trip stop') {
        await open(page, 'trip'); await action(page, 'Add a stop').click();
        const dialog = page.locator('.trip-stay-split-dialog');
        const mode = await resolvePageMapMode(page);
        await expect(dialog.locator(`[data-testid="map-host-${mode.toLowerCase()}"]`)).toBeVisible();
        await expect(dialog.getByText('Loading trip path...', { exact: true })).toHaveCount(0);
        if (mode === 'RASTER') await expect(dialog.locator('.leaflet-overlay-pane path').first()).toBeAttached();
        // Project the fixture's known stop into screen coordinates, then perform a real mouse click.
        await expect.poll(() => page.evaluate(() => {
          const host = document.querySelector('.trip-stay-split-dialog [data-testid^="map-host-"]');
          return Boolean(host && window.__GP_E2E_MAPS?.[host.id]);
        })).toBe(true);
        const position = await page.evaluate(([lat, lng]) => {
          const host = document.querySelector('.trip-stay-split-dialog [data-testid^="map-host-"]');
          const registered = window.__GP_E2E_MAPS[host.id];
          const map = registered.map || registered;
          const point = map.latLngToContainerPoint ? map.latLngToContainerPoint([lat, lng]) : map.project([lng, lat]);
          const rect = map.getContainer().getBoundingClientRect();
          return { x: rect.left + point.x, y: rect.top + point.y };
        }, places.Bakery);
        await page.mouse.click(position.x, position.y);
        await dialog.getByRole('combobox', { name: 'Stay start' }).fill(`${DAY} 08:15:00`);
        await dialog.getByRole('combobox', { name: 'Stay start' }).press('Tab');
        await dialog.getByRole('combobox', { name: 'Stay end' }).fill(`${DAY} 08:21:00`);
        await dialog.getByRole('combobox', { name: 'Stay end' }).press('Tab');
        await expect(dialog.locator('.place-picker-selected')).toContainText('Bakery');
        await expect(dialog.locator('.split-preview')).toBeVisible();
        const edit = await save(page, dialog.getByRole('button', { name: 'Save Split' }));
        await lifecycle(page, dbManager, context, edit, async values => {
          expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: at('08:15'), finish: at('08:21'), name: 'Bakery' }));
          const legs = values.filter(row => row.type === 'trip' && row.start < office.start);
          expect(legs).toHaveLength(2);
          expect(legs[0].finish).toBe(at('08:15')); expect(legs[1].start).toBe(at('08:21'));
          expect(legs[0]).toMatchObject({ latitude: trip.latitude, longitude: trip.longitude, end_latitude: places.Bakery[0], end_longitude: places.Bakery[1] });
          expect(legs[1]).toMatchObject({ latitude: places.Bakery[0], longitude: places.Bakery[1], end_latitude: places.Office[0], end_longitude: places.Office[1] });
          for (const [leg, from, to] of [[legs[0], trip.start, at('08:15')], [legs[1], at('08:21'), office.start]]) {
            const measured = await dbManager.client.query(`WITH points AS (
              SELECT timestamp, coordinates, lag(coordinates) OVER (ORDER BY timestamp) AS previous
              FROM gps_points WHERE user_id=$1 AND timestamp BETWEEN $2 AND $3)
              SELECT COALESCE(sum(ST_DistanceSphere(previous, coordinates)),0) AS distance FROM points`,
            [context.user, from, to]);
            expect(Number(measured.rows[0].distance)).toBeGreaterThan(100);
            expect(Math.abs(Number(leg.distance) - Number(measured.rows[0].distance))).toBeLessThanOrEqual(10);
          }
        });
        return;
      }
      if (scenario === 'movement' || scenario.startsWith('merge')) await open(page, 'trip', scenario === 'merge previous' ? 1 : 0);
      else await open(page, 'stay', 1);
      if (scenario === 'movement') {
        await action(page, 'Movement type').click();
        await drawer(page).getByRole('combobox', { name: 'Movement type' }).click();
        await page.getByRole('option', { name: 'Train', exact: true }).click();
        check = async values => expect(values.find(row => row.start === trip.start)).toMatchObject({ movement: 'TRAIN', source: 'MANUAL', finish: trip.finish, distance: trip.distance });
      } else if (scenario === 'label') {
        await action(page, 'Visit label').click(); await drawer(page).getByRole('textbox').fill('Release planning');
        check = async values => {
          expect(values.find(row => row.start === office.start)).toMatchObject({ label: 'Release planning', name: 'Office', favorite: context.favorites.Office });
          await expect(page.locator('.visit-label')).toContainText(['Release planning']);
        };
      } else if (scenario === 'place') {
        await action(page, 'Location').click(); await choosePlace(page, 'Bakery');
        check = async values => expect(values.find(row => row.start === office.start)).toMatchObject({ name: 'Bakery', favorite: context.favorites.Bakery, latitude: places.Bakery[0], longitude: places.Bakery[1] });
      } else if (scenario === 'times') {
        await action(page, 'Time').click();
        await drawer(page).getByRole('combobox', { name: 'Start', exact: true }).fill(`${DAY} 08:50`);
        check = async values => {
          expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: at('08:50'), finish: office.finish }));
          expect(values.find(row => row.start === trip.start).finish).toBe(at('08:50'));
        };
      } else if (scenario === 'split stay') {
        await action(page, 'Split stay').click();
        await drawer(page).getByRole('combobox', { name: 'Split at', exact: true }).fill(`${DAY} 10:00`);
        await choosePlace(page, 'Bakery');
        check = async values => {
          expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: office.start, finish: at('10:00'), name: 'Office' }));
          expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: at('10:00'), finish: office.finish, name: 'Bakery', favorite: context.favorites.Bakery }));
        };
      } else if (scenario.startsWith('merge')) {
        await action(page, 'Merge').click();
        await drawer(page).getByRole('radio', { name: scenario === 'merge previous' ? /Merge with the previous trip/ : /Merge with the next trip/ }).click();
        check = async values => {
          expect(values.filter(row => row.type === 'trip')).toHaveLength(1);
          expect(values).toContainEqual(expect.objectContaining({ type: 'trip', start: trip.start, finish: lastTrip.finish }));
          expect(values.some(row => row.start === office.start && row.type === 'stay')).toBe(false);
        };
      } else {
        await action(page, 'Remove').click();
        await drawer(page).getByRole('combobox').click();
        const option = { 'remove gap': 'Leaving a data gap', 'remove previous': /Extending the previous event/, 'remove next': /Extending the next event/, 'remove merge': 'Merging compatible neighbours' }[scenario];
        await page.getByRole('option', { name: option }).click();
        check = async values => {
          expect(values.some(row => row.type === 'stay' && row.start === office.start)).toBe(false);
          if (scenario === 'remove gap') expect(values).toContainEqual(expect.objectContaining({ type: 'gap', start: office.start, finish: office.finish }));
          if (scenario === 'remove previous') expect(values.find(row => row.start === trip.start).finish).toBe(office.finish);
          if (scenario === 'remove next') expect(values).toContainEqual(expect.objectContaining({ type: 'trip', start: office.start, finish: lastTrip.finish }));
          if (scenario === 'remove merge') expect(values.filter(row => row.type === 'trip')).toEqual([expect.objectContaining({ start: trip.start, finish: lastTrip.finish })]);
        };
      }
      if (!['movement', 'place', 'label'].includes(scenario)) await review(page);
      const edit = await save(page);
      await lifecycle(page, dbManager, context, edit, check);
    });
  }

  for (const scenario of ['stay', 'trip', 'group']) {
    test(`missing activity: ${scenario} saves through the editor, reloads, and undoes/restores the group`, async ({ page, dbManager, isolatedUsers }, testInfo) => {
      if (testInfo.project.name.includes('mobile')) await page.setViewportSize({ width: 390, height: 844 });
      const context = await setup(page, dbManager, isolatedUsers);
      const gps = async () => (await dbManager.client.query(
        'SELECT id, timestamp, ST_AsText(coordinates) AS coordinates FROM gps_points WHERE user_id=$1 ORDER BY timestamp, id', [context.user])).rows;
      const originalGps = await gps();
      if (scenario === 'trip') await page.getByRole('button', { name: 'Add missing timeline data', exact: true }).click();
      else await page.locator('.timeline-card--data-gap').getByRole('button', { name: 'Fill missing time…', exact: true }).click();
      const editor = page.locator('.missing-activity-editor');
      await expect(editor).toBeVisible();
      const fillTimes = async (form, start, end) => {
        await form.getByRole('combobox', { name: 'From · Time', exact: true }).fill(`${start}:00`);
        await form.getByRole('combobox', { name: 'From · Time', exact: true }).press('Escape');
        await form.getByRole('combobox', { name: 'To · Time', exact: true }).fill(`${end}:00`);
        await form.getByRole('combobox', { name: 'To · Time', exact: true }).press('Escape');
      };
      const selectPlace = async (picker, name) => {
        const search = picker.getByRole('textbox', { name: 'Find a saved or visited place' });
        if (!await search.isVisible()) await picker.getByRole('button', { name: 'Choose location', exact: true }).click();
        await search.fill(name);
        await picker.getByRole('button', { name, exact: true }).click();
        await expect(picker.locator('.place-picker-selected')).toContainText(name);
      };
      const chooseType = async (form, type) => {
        await form.getByRole('combobox', { name: 'What happened?' }).click();
        await page.getByRole('option', { name: type, exact: true }).click();
      };
      if (scenario !== 'trip') {
        const form = editor.locator('.activity-form').first();
        await fillTimes(form, '18:30', '19:00');
        await chooseType(form, 'Stayed at a place');
        await selectPlace(form.locator('.place-picker'), 'Bakery');
        await form.getByRole('button', { name: 'Add a visit label', exact: true }).click();
        await form.getByRole('textbox', { name: 'Visit label (optional)' }).fill('Missing visit');
      }
      if (scenario !== 'stay') {
        if (scenario === 'group') await editor.getByRole('button', { name: 'Add another activity', exact: true }).click();
        const form = editor.locator('.activity-form').last();
        await fillTimes(form, '19:15', '20:15');
        await chooseType(form, 'Travelled');
        await selectPlace(form.getByRole('group', { name: 'Start', exact: true }), 'Home');
        await selectPlace(form.getByRole('group', { name: 'Destination', exact: true }), 'Office');
        await form.getByRole('combobox').last().click();
        await page.getByRole('option', { name: 'Walk', exact: true }).click();
        await form.getByRole('button', { name: 'Add route points', exact: true }).click();
        const host = editor.locator('[data-testid^="map-host-"]');
        await expect(host).toBeVisible();
        const bounds = await host.boundingBox();
        await host.click({ position: { x: bounds.width * 0.55, y: bounds.height * 0.75 } });
        await expect(form.getByRole('button', { name: 'Route point 1', exact: true })).toBeVisible();
        await editor.getByRole('button', { name: 'Done placing points', exact: true }).click();
      }
      const preview = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/timeline/activity-corrections/preview');
      await editor.getByRole('button', { name: 'Review changes', exact: true }).click();
      expect((await preview).ok()).toBe(true);
      await expect(editor.locator('.activity-review')).toContainText('Still unknown');
      expect((await dbManager.client.query('SELECT id FROM timeline_edits WHERE user_id=$1', [context.user])).rowCount).toBe(0);
      if (scenario === 'stay') {
        const end = editor.getByRole('combobox', { name: 'To · Time', exact: true });
        await end.fill('19:01:00'); await editor.locator('.p-dialog-title').click();
        await expect(editor.locator('.activity-review')).toHaveCount(0);
        await expect(editor.getByRole('button', { name: 'Save changes', exact: true })).toHaveCount(0);
        await end.fill('19:00:00'); await editor.locator('.p-dialog-title').click();
        const refreshed = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/timeline/activity-corrections/preview');
        await editor.getByRole('button', { name: 'Review changes', exact: true }).click();
        expect((await refreshed).ok()).toBe(true);
      }
      const response = page.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname === '/api/v1/timeline/activity-corrections');
      await editor.getByRole('button', { name: 'Save changes', exact: true }).click();
      const saved = await response; expect(saved.ok()).toBe(true);
      const result = await saved.json();
      expect(result.edits).toHaveLength(scenario === 'group' ? 2 : 1);
      expect(result.edits.every(edit => edit.groupId === result.groupId)).toBe(true);
      await waitJob(page, result);
      await expect(editor).not.toBeVisible();
      await lifecycle(page, dbManager, context, result.edits[0], async values => {
        expect(await gps()).toEqual(originalGps);
        if (scenario !== 'trip') expect(values).toContainEqual(expect.objectContaining({
          type: 'stay', start: at('18:30'), finish: at('19:00'), name: 'Bakery', label: 'Missing visit', favorite: context.favorites.Bakery
        }));
        if (scenario !== 'stay') {
          expect(values).toContainEqual(expect.objectContaining({ type: 'trip', start: at('19:15'), finish: at('20:15'), movement: 'WALK', source: 'MANUAL' }));
          const manual = await dbManager.client.query('SELECT manual_route, distance_meters FROM timeline_trips WHERE user_id=$1 AND timestamp=$2', [context.user, at('19:15')]);
          expect(manual.rows[0].manual_route).toHaveLength(3);
          expect(Number(manual.rows[0].distance_meters)).toBeGreaterThan(100);
          await expect(page.locator('.manual-route-legend')).toContainText('Manual route');
        }
      });
      const card = scenario === 'trip'
        ? page.locator('.timeline-card--trip').filter({ hasText: '19:15' })
        : page.locator('.timeline-card--stay').filter({ hasText: 'Missing visit' });
      await card.getByRole('button', { name: scenario === 'trip' ? 'Edit trip…' : 'Edit visit…' }).click();
      await expect(drawer(page).getByRole('note')).toContainText('undo the correction group');
      for (const name of ['Time', 'Split stay', 'Add a stop', 'Merge', 'Remove']) await expect(action(page, name)).toHaveCount(0);
      await expect(action(page, scenario === 'trip' ? 'Movement type' : 'Visit label')).toBeVisible();
    });
  }

  test('preview invalidates after editing; invalid time and discard do not write', async ({ page, dbManager, isolatedUsers }) => {
    const context = await setup(page, dbManager, isolatedUsers);
    await open(page, 'stay', 1); await action(page, 'Time').click();
    const start = drawer(page).getByRole('combobox', { name: 'Start', exact: true });
    await start.fill('bad date');
    await expect(drawer(page).getByRole('button', { name: 'Review changes' })).toBeDisabled();
    await expect(drawer(page).getByRole('status').last()).not.toBeEmpty();
    await start.fill(`${DAY} 08:50`); await review(page);
    await start.fill(`${DAY} 08:51`);
    await expect(drawer(page).locator('.edit-preview')).toHaveCount(0);
    await expect(drawer(page).getByRole('button', { name: 'Save changes', exact: true })).toHaveCount(0);
    page.once('dialog', dialog => dialog.accept());
    await drawer(page).getByRole('button', { name: 'Cancel', exact: true }).click();
    expect(await rows(dbManager, context.user)).toEqual(context.baseline);
    expect((await dbManager.client.query('SELECT id FROM timeline_edits WHERE user_id=$1', [context.user])).rowCount).toBe(0);
  });

  test('keyboard access and label removal use the same history', async ({ page, dbManager, isolatedUsers }) => {
    const context = await setup(page, dbManager, isolatedUsers);
    const trigger = page.locator('.timeline-card--stay').nth(1).getByRole('button', { name: 'Edit visit…' });
    await trigger.focus(); await page.keyboard.press('Enter');
    await action(page, 'Visit label').click(); await drawer(page).getByRole('textbox').fill('Keyboard label');
    const edit = await save(page);
    await expect(page.locator('.visit-label')).toHaveText('Keyboard label');
    await open(page, 'stay', 1); await action(page, 'Visit label').click();
    const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === `/api/v1/timeline/edits/${edit.id}/revert`);
    await drawer(page).getByRole('button', { name: 'Remove label', exact: true }).click();
    await waitJob(page, await (await response).json());
    await expect(page.locator('.visit-label')).toHaveCount(0);
    expect(await rows(dbManager, context.user)).toEqual(context.baseline);
  });

  test('overnight time edits respect user timezone rather than browser timezone', async ({ page, dbManager, isolatedUsers }) => {
    const context = await setup(page, dbManager, isolatedUsers, { timezone: 'Europe/Kyiv', overnight: true });
    await page.locator(STAYS).filter({ hasText: 'Office' }).last().getByRole('button', { name: 'Edit visit…' }).click();
    await action(page, 'Time').click();
    // September Kyiv is UTC+3; the browser uses New York.
    await drawer(page).getByRole('combobox', { name: 'Start', exact: true }).fill('2026-09-15 23:10');
    await review(page);
    const edit = await save(page);
    await lifecycle(page, dbManager, context, edit, async values => {
      expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: at('20:10'), finish: '2026-09-16T02:00:00.000Z', name: 'Office' }));
    });
  });

  test('structural conflict keeps the form and links to the saved change', async ({ page, dbManager, isolatedUsers }) => {
    const context = await setup(page, dbManager, isolatedUsers);
    await open(page, 'stay', 1); await action(page, 'Split stay').click();
    await drawer(page).getByRole('combobox', { name: 'Split at', exact: true }).fill(`${DAY} 10:00`);
    await choosePlace(page, 'Bakery'); await review(page);
    const first = await save(page);
    await page.reload(); await page.waitForLoadState('networkidle');
    await open(page, 'stay', 1); await action(page, 'Remove').click();
    await drawer(page).getByRole('combobox').click();
    await page.getByRole('option', { name: 'Leaving a data gap' }).click(); await review(page);
    const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/timeline/edits');
    await drawer(page).getByRole('button', { name: 'Save changes', exact: true }).click();
    expect((await response).status()).toBe(409);
    await expect(drawer(page).getByRole('alert')).toContainText('overlaps');
    await expect(drawer(page).getByRole('button', { name: 'View change', exact: true })).toBeVisible();
    page.once('dialog', dialog => dialog.accept());
    await drawer(page).getByRole('button', { name: 'View change', exact: true }).click();
    await expect(drawer(page).locator(`[data-edit-id="${first.id}"]`)).toBeFocused();
    expect((await dbManager.client.query('SELECT id FROM timeline_edits WHERE user_id=$1', [context.user])).rowCount).toBe(1);
  });

  test('server validation leaves the form editable and a corrected preview can save', async ({ page, dbManager, isolatedUsers }) => {
    await setup(page, dbManager, isolatedUsers);
    await open(page, 'stay', 1); await action(page, 'Time').click();
    const start = drawer(page).getByRole('combobox', { name: 'Start', exact: true });
    await start.fill(`${DAY} 06:00`);
    const rejected = page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/timeline/edits/preview');
    await drawer(page).getByRole('button', { name: 'Review changes' }).click();
    expect((await rejected).status()).toBe(400);
    await expect(drawer(page).getByRole('alert')).not.toBeEmpty();
    await expect(start).toBeEnabled();
    await start.fill(`${DAY} 08:50`); await review(page); await save(page);
  });

  test('incompatible stay neighbours do not offer removal merge', async ({ page, dbManager, isolatedUsers }) => {
    await setup(page, dbManager, isolatedUsers);
    // The outbound trip is between Home and Office, which have different favorite identities.
    await open(page, 'trip'); await action(page, 'Remove').click();
    await drawer(page).getByRole('combobox').click();
    await expect(page.getByRole('option', { name: 'Merging compatible neighbours' })).toHaveCount(0);
    await expect(page.getByRole('option', { name: 'Leaving a data gap' })).toBeVisible();
  });

  test('stale history survives preference changes, recovers, and remains undoable without a target', async ({ page, dbManager, isolatedUsers }) => {
    const context = await setup(page, dbManager, isolatedUsers);
    const preferences = await page.request.get('/api/v1/timeline/preferences');
    expect(preferences.ok()).toBe(true);
    const originalMinimum = (await preferences.json()).staypointMinDurationMinutes;
    // The final open stay is retained below the minimum; use the completed Office stay.
    await open(page, 'stay', 1); await action(page, 'Visit label').click();
    await drawer(page).getByRole('textbox').fill('Late visit');
    const edit = await save(page);
    const changeMinimum = async minutes => {
      const changed = await page.request.put('/api/v1/preferences/timeline', { data: { staypointMinDurationMinutes: minutes } });
      expect(changed.status()).toBe(200);
      await waitJob(page, await changed.json());
      await page.reload(); await page.waitForLoadState('networkidle');
      await expect(page.locator('.timeline-history-button')).toBeVisible();
    };
    await changeMinimum(240);
    await expect(page.locator('.visit-label').filter({ hasText: 'Late visit' })).toHaveCount(0);
    await page.locator('.timeline-history-button').click();
    const stale = page.locator(`.timeline-edit-history [data-edit-id="${edit.id}"]`);
    await expect(stale).toContainText('Not currently applied');
    await expect(stale.getByRole('button', { name: 'Undo', exact: true })).toBeEnabled();
    await changeMinimum(originalMinimum);
    await expect(page.locator('.visit-label')).toHaveText('Late visit');
    await changeMinimum(240);
    await historyChange(page, edit, 'Undo');
    expect((await dbManager.client.query('SELECT status FROM timeline_edits WHERE id=$1 AND user_id=$2', [edit.id, context.user])).rows[0].status).toBe('REVERTED');
    await changeMinimum(originalMinimum);
    await expect(page.locator('.visit-label').filter({ hasText: 'Late visit' })).toHaveCount(0);
    await historyChange(page, edit, 'Redo');
    await page.reload(); await page.waitForLoadState('networkidle');
    await expect(page.locator('.visit-label')).toHaveText('Late visit');
  });

  test('public timeline has no edit controls or history', async ({ page, dbManager, isolatedUsers }) => {
    const context = await setup(page, dbManager, isolatedUsers);
    await open(page, 'stay', 1); await action(page, 'Visit label').click();
    await drawer(page).getByRole('textbox').fill('Shared visit');
    const edit = await save(page);
    const link = await ShareLinkFactory.createTimeline(dbManager, context.user, { dateRange: {
      startDate: new Date(at('00:00')), endDate: new Date(at('23:59')), expiresAt: new Date(Date.now() + 86400000)
    } });
    await page.goto(`/shared-timeline/${link.id}`);
    await expect(page.locator('.timeline-card').first()).toBeVisible();
    await expect(page.locator('.visit-label')).toHaveText('Shared visit');
    await expect(page.locator('.timeline-edit-trigger, .timeline-history-button, .timeline-edit-count, .gap-fill-action')).toHaveCount(0);
    const viewer = await page.context().browser().newContext({ baseURL: new URL(page.url()).origin });
    try {
      const access = await viewer.request.post(`/api/v1/public/share-links/${link.id}/access-tokens`, { data: {} });
      expect(access.ok()).toBe(true);
      const { access_token: token } = await access.json();
      const api = await viewer.request.get(`/api/v1/public/share-links/${link.id}/timeline`, { headers: { Authorization: `Bearer ${token}` } });
      expect(api.ok()).toBe(true);
      const body = await api.json();
      for (const event of [...body.stays, ...body.trips, ...body.dataGaps]) expect(event.activeEditCount || 0).toBe(0);
      const rejected = await viewer.request.post(`/api/v1/timeline/edits/${edit.id}/revert`, { headers: { Authorization: `Bearer ${token}` } });
      expect(rejected.status()).toBe(401);
    } finally { await viewer.close(); }
  });
  test('view-only trip collaborator sees edited content without owner history', async ({ page, dbManager, isolatedUsers }) => {
    const context = await setup(page, dbManager, isolatedUsers);
    await open(page, 'stay', 1); await action(page, 'Visit label').click();
    await drawer(page).getByRole('textbox').fill('Collaborative visit');
    const edit = await save(page);
    const trip = await TestSetupHelper.createTrip(dbManager, context.user, {
      name: 'Read-only trip', startTime: new Date(at('00:00')), endTime: new Date(at('23:59')), status: 'COMPLETED'
    });
    const browser = await page.context().browser().newContext({ baseURL: new URL(page.url()).origin });
    try {
      const viewer = await browser.newPage();
      const managed = buildManagedUser(isolatedUsers, { timezone: 'UTC' });
      await new TimelinePage(viewer).loginAndNavigate(managed, { dbManager, mapMode: 'RASTER' });
      const user = await dbManager.getUserByEmail(managed.email);
      await TestSetupHelper.setupFriendship(dbManager, context.user, user.id);
      await dbManager.client.query(`INSERT INTO trip_collaborators (trip_id, collaborator_user_id, access_role, created_at, updated_at)
        VALUES ($1,$2,'VIEW',NOW(),NOW())`, [trip, user.id]);
      const response = await viewer.request.get(`/api/v1/trips/${trip}/timeline`);
      expect(response.ok()).toBe(true);
      const timeline = await response.json();
      expect(timeline.stays.some(stay => stay.visitName === 'Collaborative visit')).toBe(true);
      for (const event of [...timeline.stays, ...timeline.trips, ...timeline.dataGaps]) expect(event.activeEditCount || 0).toBe(0);
      await viewer.goto(`/app/trips/${trip}`);
      await viewer.locator('.trip-rail-lens').getByText('Timeline', { exact: true }).click();
      await expect(viewer.locator('.visit-label')).toHaveText('Collaborative visit');
      await expect(viewer.locator('.timeline-edit-trigger, .timeline-history-button, .gap-fill-action')).toHaveCount(0);
      const before = await rows(dbManager, context.user);
      for (const action of ['revert', 'restore']) expect((await viewer.request.post(`/api/v1/timeline/edits/${edit.id}/${action}`)).status()).toBe(404);
      expect(await rows(dbManager, context.user)).toEqual(before);
    } finally { await browser.close(); }
  });

  for (const [day, uncertain, original] of [
    ['2026-03-29', '03:30', '02:00'], ['2026-10-25', '03:30', '03:00']
  ]) {
    test(`clock change ${day}: reject uncertain input and preserve unchanged boundaries`, async ({ page, dbManager, isolatedUsers }) => {
      const context = await setup(page, dbManager, isolatedUsers, { timezone: 'Europe/Kyiv', overnight: true });
      const delta = Date.parse(`${day}T00:00:00Z`) - Date.parse(at('06:00'));
      await dbManager.client.query("UPDATE gps_points SET timestamp=timestamp + $1 * interval '1 millisecond' WHERE user_id=$2", [delta, context.user]);
      const generated = await page.request.post('/api/v1/timeline/jobs');
      expect(generated.ok()).toBe(true); await waitJob(page, await generated.json());
      await page.goto(`/app/timeline?start=${day}&end=${day}`);
      await open(page, 'stay', 0); await action(page, 'Time').click();
      const start = drawer(page).getByRole('combobox', { name: 'Start', exact: true });
      await start.fill(`${day} ${uncertain}`);
      await expect(drawer(page).getByRole('button', { name: 'Review changes' })).toBeDisabled();
      await expect(drawer(page).getByRole('status').last()).not.toBeEmpty();
      expect((await dbManager.client.query('SELECT id FROM timeline_edits WHERE user_id=$1', [context.user])).rowCount).toBe(0);
      await start.fill(`${day} ${original}`);
      const finish = day === '2026-03-29' ? '05:10' : '04:10';
      await drawer(page).getByRole('combobox', { name: 'End', exact: true }).fill(`${day} ${finish}`);
      await review(page); const edit = await save(page);
      const values = await rows(dbManager, context.user);
      expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: `${day}T00:00:00.000Z`, finish: `${day}T02:10:00.000Z` }));
      expect(iso(edit.payload.start)).toBe(`${day}T00:00:00.000Z`);
    });
  }

  test('mobile: drawer fits, traps focus, restores focus and saves through the calendar', async ({ page, dbManager, isolatedUsers }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await setup(page, dbManager, isolatedUsers);
    const trigger = page.locator('.timeline-card--stay').nth(1).getByRole('button', { name: 'Edit visit…' });
    await trigger.click(); await expect(drawer(page)).toBeVisible();
    const bounds = await drawer(page).boundingBox();
    expect(Math.round(bounds.width)).toBeLessThanOrEqual(390); expect(bounds.x).toBeGreaterThanOrEqual(-0.5);
    expect(await drawer(page).evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
    for (let i = 0; i < 15; i++) {
      await page.keyboard.press('Tab');
      expect(await drawer(page).evaluate(element => element.contains(document.activeElement))).toBe(true);
    }
    await page.keyboard.press('Escape');
    await expect(drawer(page)).not.toBeVisible(); await expect(trigger).toBeFocused();
    await trigger.click(); await action(page, 'Time').click();
    const start = drawer(page).getByRole('combobox', { name: 'Start', exact: true });
    await start.click(); await expect(page.getByRole('dialog', { name: 'Choose Date' })).toBeVisible();
    await start.press('Escape');
    await start.fill(`${DAY} 08:50`); await start.press('Tab');
    await expect(page.getByRole('dialog', { name: 'Choose Date' })).toHaveCount(1);
    await expect(page.getByRole('dialog', { name: 'Choose Date' })).toBeVisible();
    await drawer(page).getByRole('combobox', { name: 'End', exact: true }).press('Escape');
    await expect(page.getByRole('dialog', { name: 'Choose Date' })).toHaveCount(0);
    await review(page);
    await expect(drawer(page).locator('.edit-preview')).toBeInViewport();
    await expect(drawer(page).getByRole('button', { name: 'Save changes', exact: true })).toBeInViewport();
    await save(page);
    await expect(page.locator('.timeline-card--stay').nth(1).locator('.timeline-timestamp')).toContainText('08:50');
  });

});
