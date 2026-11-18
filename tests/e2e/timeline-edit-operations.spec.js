import { test, expect } from '../fixtures/timeline-corrections-fixture.js';
import * as H from '../utils/timeline-corrections/helpers.js';
import { manualTrip } from '../utils/timeline-corrections/seeds.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { TimelineReportsPage } from '../pages/TimelineReportsPage.js';

test.describe('Timeline correction operations', () => {
  test.setTimeout(240000);
  test.use({ timezoneId: 'America/New_York', locale: 'en-US', viewport: { width: 1440, height: 1000 } });

  test('E2E-14 change actual place while retaining visit label', async ({ page, dbManager: db, scenario: c }) => {
    await H.detail(page, 'stay', '08:46', 'Visit label', 'Independent label');
    c.baseline = await H.rows(db, c.user);
    const edit = await H.detail(page, 'stay', '08:46', 'Location', 'Bakery');
    await H.lifecycle(page, db, c, edit, async values => {
      expect(values.find(v => v.start === H.at('08:46'))).toMatchObject({ name: 'Bakery', favorite: c.favorites.Bakery,
        label: 'Independent label', latitude: H.places.Bakery[0], longitude: H.places.Bakery[1], finish: H.at('12:01') });
    });
  });

  test('E2E-15 set replace and clear label without reviving old text', async ({ page, dbManager: db, scenario: c }) => {
    const first = await H.detail(page, 'stay', '08:46', 'Visit label', '<b>Label A</b>');
    await expect(page.locator('.visit-label')).toHaveText('<b>Label A</b>');
    await expect(page.locator('.visit-label b')).toHaveCount(0);
    const second = await H.detail(page, 'stay', '08:46', 'Visit label', 'Label B');
    const cleared = await H.detail(page, 'stay', '08:46', 'Visit label', '');
    await expect(page.locator('.visit-label')).toHaveCount(0);
    const history = await H.corrections(page);
    expect(history.find(e => e.id === first.id).status).toBe('REPLACED');
    expect(history.find(e => e.id === second.id).status).toBe('REPLACED');
    await H.historyChange(page, cleared, 'Undo');
    // Undoing the clear reactivates its immediate predecessor as a whole group.
    await expect(page.locator('.visit-label')).toHaveText('Label B');
    expect((await H.corrections(page)).find(e => e.id === second.id).status).toBe('IN_EFFECT');
    await H.historyChange(page, cleared, 'Redo');
    await H.durable(page, db, c, values => expect(values.find(v => v.start === H.at('08:46'))).toMatchObject({ name: 'Office', label: null }));
  });

  test('E2E-16 report quick edit uses corrections and survives full lifecycle', async ({ page, dbManager: db, scenario: c }) => {
    const reports = new TimelineReportsPage(page);
    await page.goto(`/app/timeline-reports?start=${H.DAY}&end=${H.DAY}`);
    await reports.switchToTab('Trips');
    await page.locator('.row-actions button').filter({ has: page.locator('.pi-pencil') }).first().click();
    const dialog = page.getByRole('dialog').filter({ has: page.locator('.movement-edit-content') });
    await dialog.getByRole('combobox').click(); await page.getByRole('option', { name: 'Walk', exact: true }).click();
    const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/timeline/corrections');
    await dialog.getByRole('button', { name: 'Save', exact: true }).click();
    const saved = await response; expect(saved.ok()).toBe(true);
    const result = await saved.json(); await H.waitJob(page, result); await H.reload(page, c);
    await H.open(page, 'trip'); await H.action(page, 'Movement type').click();
    await expect(H.drawer(page).getByRole('combobox', { name: 'Movement type' })).toContainText('Walk');
    await H.drawer(page).getByRole('button', { name: 'Cancel', exact: true }).click();
    await H.lifecycle(page, db, c, result.edits[0], values => {
      const original = c.baseline.find(v => v.type === 'trip');
      expect(values.find(v => v.start === original.start)).toEqual({ ...original, movement: 'WALK', source: 'MANUAL' });
    });
  });

  test('E2E-17 resize both stay boundaries and retain correct neighbours', async ({ page, dbManager: db, scenario: c }) => {
    const edit = await H.resize(page, 'stay', '08:46', `${H.DAY} 08:50`, `${H.DAY} 11:50`);
    await H.lifecycle(page, db, c, edit, values => {
      expect(values).toContainEqual(expect.objectContaining({ type: 'stay', name: 'Office', start: H.at('08:50'), finish: H.at('11:50') }));
      expect(values.filter(v => v.type === 'trip').map(v => [v.start, v.finish])).toEqual([
        [H.at('08:02'), H.at('08:50')], [H.at('11:50'), H.at('12:46')],
      ]);
      expect(values.filter(v => v.type === 'gap')).toEqual(c.baseline.filter(v => v.type === 'gap'));
    });
  });

  test.describe('overnight trip', () => {
    test.use({ timelineSeed: { timezone: 'Europe/Kyiv', date: H.DAY, endDate: '2026-09-16', recipe: ({ stay, drive, places }) => {
      stay(places.Home, '2026-09-15T18:00:00Z', '2026-09-15T20:00:00Z');
      drive(places.Home, places.Office, '2026-09-15T20:01:00Z', '2026-09-15T22:59:00Z');
      stay(places.Office, '2026-09-15T23:00:00Z', '2026-09-16T03:00:00Z');
    } } });
    test('E2E-18 resize overnight trip using profile dates and retain start', async ({ page, dbManager: db, scenario: c }) => {
      expect(c.baseline.filter(v => v.type === 'trip')).toHaveLength(1);
      const original = c.baseline.find(v => v.type === 'trip');
      expect(original.start).toBe('2026-09-15T20:04:00.000Z');
      const edit = await H.resize(page, 'trip', '23:04', null, '2026-09-16 02:10');
      expect(H.iso(edit.payload.start)).toBe(original.start);
      await H.durable(page, db, c, values => expect(values).toContainEqual(expect.objectContaining({ type: 'trip', start: original.start, finish: '2026-09-15T23:10:00.000Z' })));
      await H.openAt(page, 'trip', '23:04'); await H.action(page, 'Time').click();
      await expect(H.drawer(page).getByRole('combobox', { name: 'End', exact: true })).toHaveValue('2026-09-16 02:10');
    });
  });

  test('E2E-19 split stay then resize a resulting part', async ({ page, dbManager: db, scenario: c }) => {
    await H.openAt(page, 'stay', '08:46'); await H.action(page, 'Split stay').click();
    await H.drawer(page).getByRole('combobox', { name: 'Split at', exact: true }).fill(`${H.DAY} 10:00`);
    await H.choosePlace(page, 'Bakery'); await H.review(page); const split = await H.save(page);
    await H.assertSegment(db, c, 'stay', H.at('08:46'), H.at('10:00'), { name: 'Office' });
    const afterSplit = await H.rows(db, c.user);
    const resized = await H.resize(page, 'stay', '10:00', `${H.DAY} 10:05`, null);
    await H.assertSegment(db, c, 'stay', H.at('10:05'), H.at('12:01'), { name: 'Bakery' });
    await H.historyChange(page, resized, 'Undo'); expect(await H.rows(db, c.user)).toEqual(afterSplit);
    await H.historyChange(page, split, 'Undo'); expect(await H.rows(db, c.user)).toEqual(c.baseline);
  });

  test('E2E-20 insert Pharmacy stop through route map', async ({ page, dbManager: db, scenario: c }) => {
    await H.open(page, 'trip'); await H.action(page, 'Add a stop').click();
    const dialog = page.locator('.trip-stay-split-dialog');
    await expect(dialog.getByText('Loading trip path...', { exact: true })).toHaveCount(0);
    await H.mapClick(page, H.places.Pharmacy, '.trip-stay-split-dialog');
    for (const [name, time] of [['Stay start', '08:26:00'], ['Stay end', '08:32:00']]) {
      await dialog.getByRole('combobox', { name, exact: true }).fill(`${H.DAY} ${time}`);
      await dialog.getByRole('combobox', { name, exact: true }).press('Escape');
    }
    await H.pick(page, dialog.locator('.place-picker'), 'Pharmacy', { searchable: false });
    await expect(dialog.locator('.place-picker-selected')).toContainText('Pharmacy');
    await expect(dialog.locator('.split-preview')).toBeVisible();
    const edit = await H.save(page, dialog.getByRole('button', { name: 'Save Split', exact: true }));
    await H.lifecycle(page, db, c, edit, values => {
      expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: H.at('08:26'), finish: H.at('08:32'), name: 'Pharmacy' }));
      const legs = values.filter(v => v.type === 'trip' && v.start < H.at('08:46'));
      expect(legs.map(v => [v.start, v.finish])).toEqual([[H.at('08:02'), H.at('08:26')], [H.at('08:32'), H.at('08:46')]]);
      expect(legs[0]).toMatchObject({ end_latitude: H.places.Pharmacy[0], end_longitude: H.places.Pharmacy[1] });
      expect(legs[1]).toMatchObject({ latitude: H.places.Pharmacy[0], longitude: H.places.Pharmacy[1] });
      expect(legs.every(v => Number(v.distance) > 0)).toBe(true);
      expect(legs.every(v => Number(v.distance) < Number(c.baseline.find(v => v.type === 'trip').distance))).toBe(true);
    });
  });

  for (const [id, resolution] of [['21', 'EXTEND_PREVIOUS'], ['22', 'EXTEND_NEXT']]) {
    test(`E2E-${id} remove stay using ${resolution}`, async ({ page, dbManager: db, scenario: c }) => {
      const nextTrip = c.baseline.filter(v => v.type === 'trip')[1];
      const { rows: [recordedStart] } = await db.client.query(`SELECT ST_Y(coordinates) AS latitude,
        ST_X(coordinates) AS longitude FROM gps_points WHERE user_id=$1 AND timestamp=$2 ORDER BY id LIMIT 1`,
      [c.user, nextTrip.start]);
      expect(recordedStart, 'fixture has the next trip origin recording').toBeTruthy();
      const edit = await H.remove(page, 'stay', '08:46', resolution);
      await H.lifecycle(page, db, c, edit, values => {
        expect(values.filter(v => v.type === 'stay')).toEqual(c.baseline.filter(v => v.type === 'stay' && v.start !== H.at('08:46')));
        const before = c.baseline.filter(v => v.type === 'trip'); const after = values.filter(v => v.type === 'trip');
        expect(after).toHaveLength(2);
        if (resolution === 'EXTEND_PREVIOUS') {
          expect(after[0]).toMatchObject({ start: before[0].start, finish: H.at('12:01'), movement: before[0].movement });
          // Removing the origin stay removes its display snap; the trip returns to its actual GPS origin.
          expect(after[1]).toEqual({ ...before[1], latitude: Number(recordedStart.latitude.toFixed(8)), longitude: Number(recordedStart.longitude.toFixed(8)) });
        } else {
          expect(after[0]).toEqual(before[0]); expect(after[1]).toMatchObject({ start: H.at('08:46'), finish: before[1].finish, movement: before[1].movement });
        }
      });
    });
  }

  test('E2E-23 join neighbours after overlapping time correction', async ({ page, dbManager: db, scenario: c }) => {
    await H.resize(page, 'stay', '08:46', `${H.DAY} 08:50`, null);
    c.baseline = await H.rows(db, c.user);
    const edit = await H.remove(page, 'stay', '08:50', 'MERGE');
    await H.lifecycle(page, db, c, edit, values => {
      expect(values.filter(v => v.type === 'trip')).toHaveLength(1);
      expect(values).toContainEqual(expect.objectContaining({ type: 'trip', start: H.at('08:02'), finish: H.at('12:46') }));
    });
  });

  test.describe('touching stays', () => {
    test.use({ timelineSeed: { zeroDuration: true } });
    test('E2E-24 dismiss zero duration connector without changing stays', async ({ page, dbManager: db, scenario: c }) => {
      const trip = c.baseline.find(v => v.type === 'trip');
      expect(trip.start).toBe(trip.finish); expect(Number(trip.distance)).toBeGreaterThan(0);
      const edit = await H.remove(page, 'trip', '08:00', 'DISMISS');
      await H.lifecycle(page, db, c, edit, values => expect(values).toEqual(c.baseline.filter(v => v.type !== 'trip')));
    });
  });

  test('E2E-25 edit complete route preserving explicit endpoints', async ({ page, dbManager: db, scenario: c }) => {
    const original = c.baseline.find(v => v.type === 'trip');
    await TestSetupHelper.createFavoritePoint(db, c.user, { name: 'Nearby endpoint', latitude: original.latitude + .00002, longitude: original.longitude + .00002 });
    await H.open(page, 'trip'); await H.action(page, 'Edit trip route').click();
    await expect(H.editor(page).getByRole('button', { name: 'Route point 1', exact: true })).toBeVisible();
    await H.editor(page).getByRole('button', { name: 'Route point 1', exact: true }).click();
    const moved = [49.8405, 24.04]; const clicked = await H.mapClick(page, moved);
    await H.reviewFill(page); const edit = await H.saveFill(page);
    await H.lifecycle(page, db, c, edit, values => {
      const trip = values.find(v => v.start === original.start);
      expect(trip.route.length).toBeGreaterThan(2);
      expect(trip.route[0]).toMatchObject({ latitude: original.latitude, longitude: original.longitude });
      expect(trip.route[1].latitude).toBeCloseTo(clicked.latitude, 6); expect(trip.route[1].longitude).toBeCloseTo(clicked.longitude, 6);
      expect(trip.route.at(-1)).toMatchObject({ latitude: original.end_latitude, longitude: original.end_longitude });
      expect(trip.finish).toBe(original.finish);
      expect(Number(trip.distance)).not.toBe(Number(original.distance));
    });
  });

  test('E2E-26 carve manual route and resize a surviving fragment', async ({ page, dbManager: db, scenario: c }) => {
    await H.fill(page, [manualTrip]);
    const full = (await H.rows(db, c.user)).find(v => v.start === H.at('18:30'));
    await H.fill(page, [H.staySpec('19:00', '19:15', 'Pharmacy')]);
    const carved = (await H.rows(db, c.user)).filter(v => v.type === 'trip' && v.start >= H.at('18:30'));
    expect(carved.map(v => [v.start, v.finish])).toEqual([[H.at('18:30'), H.at('19:00')], [H.at('19:15'), H.at('20:00')]]);
    for (const leg of carved) {
      expect(leg.route).not.toEqual(full.route); expect(Number(leg.distance)).toBeGreaterThan(0); expect(Number(leg.distance)).toBeLessThan(Number(full.distance));
    }
    // Independent geographic oracle for the fixture route, uniform progress over 90 minutes.
    // One third ends at 19:00; one half starts at 19:15. Values are precomputed with spherical distance.
    expect(carved[0].route.at(-1).latitude).toBeCloseTo(49.8388454493, 4);
    expect(carved[0].route.at(-1).longitude).toBeCloseTo(24.0453113912, 4);
    expect(carved[1].route[0].latitude).toBeCloseTo(49.8374597381, 4);
    expect(carved[1].route[0].longitude).toBeCloseTo(24.0525863749, 4);
    expect(Math.abs(Number(carved[0].distance) - 1088)).toBeLessThan(10);
    expect(Math.abs(Number(carved[1].distance) - 1632)).toBeLessThan(10);
    await H.resize(page, 'trip', '18:30', null, `${H.DAY} 18:55`);
    await H.durable(page, db, c, values => expect(values.find(v => v.start === H.at('18:30'))).toMatchObject({ route: carved[0].route, finish: H.at('18:55'), distance: carved[0].distance }));
  });
});
