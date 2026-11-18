import { test, expect } from '../fixtures/timeline-corrections-fixture.js';
import * as H from '../utils/timeline-corrections/helpers.js';
import { berlinSeed, holeSeed, prefixSeed, threeTripsRecipe, yorkRecipe, yorkActivities, yorkOracle } from '../utils/timeline-corrections/seeds.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';

const within = (values, start, end) => values.filter(v => v.start >= start && v.finish <= end);

test.describe('Timeline correction issue regressions', () => {
  test.setTimeout(300000);
  test.use({ timezoneId: 'America/New_York', locale: 'en-US', viewport: { width: 1440, height: 1000 } });

  test('E2E-01 convert closed gap into favorite stay', async ({ page, dbManager: db, scenario: c }) => {
    const gap = c.baseline.find(v => v.type === 'gap' && v.start === H.at('18:00'));
    expect(gap).toMatchObject({ finish: H.at('21:00') });
    await page.locator('.timeline-card--data-gap').filter({ hasText: '18:00' }).getByRole('button', { name: 'Fill missing time…' }).click();
    await H.activity(page, H.staySpec('18:30', '19:30'));
    const preview = await H.reviewFill(page);
    expect(preview.events.map(event => ({ ...event, start: H.iso(event.start), end: H.iso(event.end) }))).toContainEqual(expect.objectContaining({ type: 'STAY', start: H.at('18:30'), end: H.at('19:30'), locationName: 'Bakery' }));
    const edit = await H.saveFill(page);
    await H.lifecycle(page, db, c, edit, values => {
      expect(within(values, H.at('18:00'), H.at('21:00')).map(v => [v.type, v.start, v.finish])).toEqual([
        ['gap', H.at('18:00'), H.at('18:30')], ['stay', H.at('18:30'), H.at('19:30')], ['gap', H.at('19:30'), H.at('21:00')],
      ]);
      expect(values.find(v => v.start === H.at('18:30'))).toMatchObject({ name: 'Bakery', favorite: c.favorites.Bakery });
    });
  });

  test('E2E-02 draw missing bicycle trip inside closed gap', async ({ page, dbManager: db, scenario: c }) => {
    const via = [[49.841, 24.04], [49.838, 24.06]];
    const edit = await H.fill(page, [H.tripSpec('18:30', '19:00', { movement: 'Bicycle', via })]);
    await H.lifecycle(page, db, c, edit, values => {
      const trip = values.find(v => v.start === H.at('18:30'));
      expect(trip).toMatchObject({ type: 'trip', finish: H.at('19:00'), movement: 'BICYCLE', source: 'MANUAL',
        latitude: H.places.Home[0], longitude: H.places.Home[1], end_latitude: H.places.Office[0], end_longitude: H.places.Office[1] });
      expect(trip.route).toHaveLength(4);
      edit.routeClicks[0].forEach((clicked, i) => { expect(trip.route[i + 1].latitude).toBeCloseTo(clicked.latitude, 6); expect(trip.route[i + 1].longitude).toBeCloseTo(clicked.longitude, 6); });
      expect(Number(trip.distance)).toBeGreaterThan(3000);
      expect(values).toContainEqual(expect.objectContaining({ type: 'gap', start: H.at('19:00'), finish: H.at('21:00') }));
      expect(values).toContainEqual(expect.objectContaining({ type: 'gap', start: H.at('18:00'), finish: H.at('18:30') }));
    });
  });

  test.describe('converted afternoon stay', () => {
    test.use({ timelineSeed: { recipe: ({ stay, places }) => {
      stay(places.Home, H.at('12:00'), H.at('14:00')); stay(places.Office, H.at('16:00'), H.at('18:00'));
    } } });
    test('E2E-03 insert trip into shortened converted stay then edit fragment #665', async ({ page, dbManager: db, scenario: c }) => {
      expect(c.baseline).toContainEqual(expect.objectContaining({ type: 'gap', start: H.at('14:00'), finish: H.at('16:00') }));
      await H.fill(page, [H.staySpec('14:00', '16:00')]);
      await H.resize(page, 'stay', '14:00', `${H.DAY} 14:24`, `${H.DAY} 15:24`);
      const shortened = await H.rows(db, c.user);
      const inserted = await H.fill(page, [H.tripSpec('14:40', '14:50')]);
      expect(within(await H.rows(db, c.user), H.at('14:24'), H.at('15:24')).map(v => [v.type, v.start, v.finish])).toEqual([
        ['stay', H.at('14:24'), H.at('14:40')], ['trip', H.at('14:40'), H.at('14:50')], ['stay', H.at('14:50'), H.at('15:24')],
      ]);
      const location = await H.detail(page, 'stay', '14:50', 'Location', 'Pharmacy');
      await H.assertSegment(db, c, 'stay', H.at('14:24'), H.at('14:40'), { name: 'Bakery' });
      await H.assertSegment(db, c, 'stay', H.at('14:50'), H.at('15:24'), { name: 'Pharmacy' });
      await H.historyChange(page, location, 'Undo'); await H.historyChange(page, inserted, 'Undo');
      expect(await H.rows(db, c.user)).toEqual(shortened);
    });
  });

  test.describe('multi-day diagnostic', () => {
    test.use({ timelineSeed: berlinSeed });
    test('E2E-04 edit actual automatic multi-day stay with earlier fill and undone history #665', async ({ page, dbManager: db, scenario: c }) => {
      expect(c.baseline).toContainEqual(expect.objectContaining({ type: 'stay', start: '2026-10-08T17:22:56.000Z', finish: '2026-10-10T09:00:30.000Z' }));
      // Valid modern equivalent of the earlier migrated FORCE_STAY; no legacy schema manipulation.
      const seed = await page.request.post('/api/v1/timeline/corrections', { data: { op: 'PLACE_STAY',
        anchorStart: '2026-10-07T17:11:23Z', anchorEnd: '2026-10-08T01:00:07Z',
        payload: { favoriteId: c.favorites.Office } } });
      expect(seed.ok()).toBe(true); await H.waitJob(page, await seed.json());
      const reverted = [];
      for (const [op, start, end, payload] of [
        ['PLACE_STAY', '2026-10-09T13:11:43Z', '2026-10-10T09:00:30Z', { favoriteId: c.favorites.Bakery }],
        ['PLACE_STAY', '2026-10-09T11:11:43Z', '2026-10-10T09:00:30Z', { favoriteId: c.favorites.Bakery }],
        ['RESIZE', '2026-10-08T17:22:56Z', '2026-10-10T09:00:30Z', { start: '2026-10-08T17:22:56Z', end: '2026-10-09T10:00:00Z' }],
        ['RESIZE', '2026-10-08T17:22:56Z', '2026-10-10T09:00:30Z', { start: '2026-10-08T17:22:56Z', end: '2026-10-09T12:00:00Z' }],
        ['SET_TRIP_TYPE', '2026-10-10T09:00:31Z', '2026-10-10T09:21:30Z', { movementType: 'CAR', sourceType: 'TRIP' }],
      ]) {
        const r = await page.request.post('/api/v1/timeline/corrections', { data: { op, anchorStart: start, anchorEnd: end, payload } });
        expect(r.ok()).toBe(true); const saved = await r.json(); await H.waitJob(page, saved);
        const undo = await page.request.post(`/api/v1/timeline/corrections/${saved.groupId}/undo`);
        expect(undo.ok()).toBe(true); await H.waitJob(page, await undo.json()); reverted.push(saved.edits[0].id);
      }
      await H.reload(page, c);
      await page.goto('/app/timeline?start=2026-10-09&end=2026-10-10');
      const before = (await H.rows(db, c.user)).filter(v => v.finish <= '2026-10-08T17:22:56.000Z');
      await H.fill(page, [{ type: 'trip', start: '2026-10-09T16:00:00Z', end: '2026-10-09T16:20:00Z', from: 'Home', to: 'Office', movement: 'Walk' }]);
      // Activity inputs use Berlin wall time, whereas stored corrections use UTC.
      await H.assertSegment(db, c, 'trip', '2026-10-09T14:00:00.000Z', '2026-10-09T14:20:00.000Z');
      await H.resize(page, 'trip', '16:00', null, '2026-10-09 16:25');
      await H.durable(page, db, c, values => {
        expect(values.filter(v => v.finish <= '2026-10-08T17:22:56.000Z')).toEqual(before);
        expect(values).toContainEqual(expect.objectContaining({ type: 'trip', start: '2026-10-09T14:00:00.000Z', finish: '2026-10-09T14:25:00.000Z' }));
      });
      expect((await H.corrections(page)).filter(e => reverted.includes(e.id)).every(e => e.status === 'UNDONE')).toBe(true);
    });
  });

  test('E2E-05 resize stay then merge overlapping affected trip range #666', async ({ page, dbManager: db, scenario: c }) => {
    const resized = await H.resize(page, 'stay', '08:46', `${H.DAY} 08:50`, null);
    const timeFixed = await H.rows(db, c.user); await H.reload(page, c);
    const merged = await H.merge(page, '08:02');
    expect((await H.rows(db, c.user)).filter(v => v.type === 'trip')).toEqual([expect.objectContaining({ start: H.at('08:02'), finish: H.at('12:46') })]);
    expect((await H.corrections(page)).find(e => e.id === resized.id).status).toBe('IN_EFFECT');
    await H.historyChange(page, merged, 'Undo'); expect(await H.rows(db, c.user)).toEqual(timeFixed);
  });

  test.describe('three compatible trips', () => {
    test.use({ timelineSeed: { recipe: threeTripsRecipe } });
    test('E2E-06 merge A+B then AB+C through regenerated rows #666', async ({ page, dbManager: db, scenario: c }) => {
      expect(c.baseline.filter(v => v.type === 'trip')).toHaveLength(3);
      const first = await H.merge(page, '08:02'); await H.reload(page, c);
      c.baseline = await H.rows(db, c.user);
      expect(c.baseline.filter(v => v.type === 'trip')).toHaveLength(2);
      const second = await H.merge(page, '08:02');
      const history = await H.corrections(page);
      expect(history.find(e => e.id === second.id).beforeValues.prerequisiteSequences).toContain(first.seq);
      await H.lifecycle(page, db, c, second, values => expect(values.filter(v => v.type === 'trip')).toEqual([
        expect.objectContaining({ start: H.at('08:02'), finish: H.at('13:01') }),
      ]));
    });
  });

  test.describe('synthetic York recording #666', () => {
    test.use({ timelineSeed: { recipe: yorkRecipe, preferences: { staypointMinDurationMinutes: 20 } } });
    const assertYork = values => {
      const outing = within(values, H.at('10:01'), H.at('11:31'));
      expect(outing).toHaveLength(9);
      outing.forEach((row, i) => expect(row).toMatchObject(yorkOracle[i]));
      expect(outing.filter(v => v.name === "King's Street Cycle Parking")).toHaveLength(2);
    };
    test('E2E-07 reconstruct all nine York activities in one atomic group', async ({ page, dbManager: db, scenario: c }) => {
      expect(c.baseline.filter(v => v.type === 'trip')).toHaveLength(1);
      const edit = await H.fill(page, yorkActivities);
      const members = (await H.corrections(page)).filter(e => e.groupId === edit.groupId);
      expect(members).toHaveLength(9); expect(members.every(e => e.status === 'IN_EFFECT')).toBe(true);
      await H.durable(page, db, c, assertYork);
      await H.historyChange(page, edit, 'Undo'); expect(await H.rows(db, c.user)).toEqual(c.baseline);
      await H.historyChange(page, edit, 'Redo'); assertYork(await H.rows(db, c.user));
    });
    test('E2E-08 sequential York stops and travel detail edits preserve all nine activities', async ({ page, dbManager: db, scenario: c }) => {
      expect(c.baseline.filter(v => v.type === 'trip')).toHaveLength(1);
      for (const [index, stop] of yorkActivities.filter(a => a.type === 'stay').entries()) {
        await H.fill(page, [stop]);
        await H.assertSegment(db, c, 'stay', stop.start, stop.end, { name: stop.name });
        if (index === 1) await H.reload(page, c);
      }
      let last;
      for (const leg of yorkActivities.filter(a => a.type === 'trip')) {
        last = await H.detail(page, 'trip', leg.start.slice(11, 16), 'Movement type', leg.movement);
      }
      assertYork(await H.rows(db, c.user)); const complete = await H.rows(db, c.user);
      await H.historyChange(page, last, 'Undo');
      expect((await H.rows(db, c.user)).filter(v => v.type === 'stay')).toEqual(complete.filter(v => v.type === 'stay'));
      await H.historyChange(page, last, 'Redo'); await H.durable(page, db, c, assertYork);
    });
  });

  test('E2E-09 name one-off Knowle Lane stay Car Park without creating favorite #667', async ({ page, dbManager: db, scenario: c }) => {
    const coordinate = [54.20377578050423, -1.7261908173302964];
    const seeded = await page.request.post('/api/v1/timeline/corrections', { data: { op: 'PLACE_STAY',
      anchorStart: H.at('18:30'), anchorEnd: H.at('19:30'), payload: {
        latitude: coordinate[0], longitude: coordinate[1], locationName: 'Knowle Lane', locationNameCustom: true, resolveLocationName: false,
      } } });
    expect(seeded.ok()).toBe(true); await H.waitJob(page, await seeded.json()); await H.reload(page, c);
    await H.assertSegment(db, c, 'stay', H.at('18:30'), H.at('19:30'), { name: 'Knowle Lane', favorite: null, geocoding: null });
    const before = await db.client.query('SELECT id FROM favorite_locations WHERE user_id=$1 ORDER BY id', [c.user]);
    await H.openAt(page, 'stay', '18:30');
    await expect(H.action(page, 'Rename location everywhere')).toHaveCount(0);
    const explanation = H.drawer(page).getByRole('note');
    await expect(explanation).toContainText("isn't linked to a Favorite or geocoding record");
    await expect(explanation).toContainText('Use Location to name it for this stay');
    await H.action(page, 'Location').click();
    await H.drawer(page).getByRole('textbox', { name: 'Place name', exact: true }).fill('Car Park');
    await H.save(page); await H.detail(page, 'stay', '18:30', 'Visit label', 'Parking spot');
    await H.durable(page, db, c, values => {
      const stay = values.find(v => v.start === H.at('18:30'));
      expect(stay).toMatchObject({ name: 'Car Park', label: 'Parking spot', favorite: null, geocoding: null });
      expect(stay.latitude).toBeCloseTo(coordinate[0], 4); expect(stay.longitude).toBeCloseTo(coordinate[1], 4);
      expect(values.filter(v => v.start < H.at('18:00'))).toEqual(c.baseline.filter(v => v.start < H.at('18:00')));
    });
    expect((await db.client.query('SELECT id FROM favorite_locations WHERE user_id=$1 ORDER BY id', [c.user])).rows).toEqual(before.rows);
    await H.openAt(page, 'stay', '18:30'); await H.action(page, 'Location').click();
    await expect(H.drawer(page).getByRole('textbox', { name: 'Place name', exact: true })).toHaveValue('Car Park');
  });

  test('E2E-10 rename linked place from manual stay updates all visits #667', async ({ page, dbManager: db, scenario: c }) => {
    await H.fill(page, [H.staySpec('18:30', '19:30', 'Home', { label: 'Keep this label' })]);
    const unrelated = await TestSetupHelper.createFavoritePoint(db, c.user, { name: 'Home annex', latitude: 49.9, longitude: 24.1 });
    await H.openAt(page, 'stay', '18:30'); await H.action(page, 'Rename location everywhere').click();
    const confirmation = page.locator('.p-confirmdialog'); await expect(confirmation).toBeVisible();
    await confirmation.getByRole('button', { name: 'Yes', exact: true }).click();
    const dialog = page.getByRole('dialog').filter({ has: page.getByRole('textbox') }).last();
    await expect(dialog).toBeVisible(); await dialog.locator('#name').fill('Renamed home');
    await dialog.getByRole('button', { name: /Save|Rename/, exact: false }).click();
    await expect(page.locator(H.STAYS).filter({ hasText: 'Renamed home' })).toHaveCount(3);
    await H.durable(page, db, c, values => {
      const linked = values.filter(v => v.favorite === c.favorites.Home);
      expect(linked).toHaveLength(3); expect(linked.every(v => v.name === 'Renamed home')).toBe(true);
      expect(linked.find(v => v.start === H.at('18:30')).label).toBe('Keep this label');
    });
    expect((await db.client.query('SELECT name FROM favorite_locations WHERE id=$1', [unrelated])).rows[0].name).toBe('Home annex');
  });

  test.describe('recording hole', () => {
    test.use({ timelineSeed: holeSeed });
    test('E2E-11 fill over detected trip despite 90-minute recording hole #675', async ({ page, dbManager: db, scenario: c }) => {
      expect(c.baseline.filter(v => v.type === 'trip')).toHaveLength(1);
      expect((await db.client.query('SELECT count(*)::int AS n FROM gps_points WHERE user_id=$1 AND timestamp > $2 AND timestamp < $3', [c.user, H.at('07:01'), H.at('08:31')])).rows[0].n).toBe(0);
      const edit = await H.fill(page, [H.tripSpec('07:15', '08:15', { via: [[49.84, 24.05]] })]);
      const values = await H.rows(db, c.user);
      expect(values.filter(v => v.type === 'trip').map(v => [v.start, v.finish])).toEqual([
        [H.at('07:01'), H.at('07:15')], [H.at('07:15'), H.at('08:15')], [H.at('08:15'), H.at('08:32')],
      ]);
      await H.historyChange(page, edit, 'Undo'); expect(await H.rows(db, c.user)).toEqual(c.baseline);
    });
    test('E2E-12 clear detected trip then fill part of cleared gap #675', async ({ page, dbManager: db, scenario: c }) => {
      const clear = await H.remove(page, 'trip', '07:01', 'GAP');
      const cleared = await H.rows(db, c.user);
      expect(cleared).toContainEqual(expect.objectContaining({ type: 'gap', start: H.at('07:01'), finish: H.at('08:32') }));
      const fill = await H.fill(page, [H.tripSpec('07:15', '08:15')]);
      await H.assertSegment(db, c, 'trip', H.at('07:15'), H.at('08:15'), { source: 'MANUAL' });
      await H.historyChange(page, fill, 'Undo'); expect(await H.rows(db, c.user)).toEqual(cleared);
      await H.regenerate(page); expect(await H.rows(db, c.user)).toEqual(cleared);
      await H.historyChange(page, clear, 'Undo'); expect(await H.rows(db, c.user)).toEqual(c.baseline);
      await H.historyChange(page, clear, 'Redo'); expect(await H.rows(db, c.user)).toEqual(cleared);
    });
  });

  test.describe('missing prefix', () => {
    test.use({ timelineSeed: prefixSeed });
    test('E2E-13 replace missing prefix with one complete route and time #675', async ({ page, dbManager: db, scenario: c }) => {
      expect(c.baseline.filter(v => v.type === 'trip')).toEqual([expect.objectContaining({ start: H.at('07:30'), finish: H.at('08:00') })]);
      const prefix = await H.fill(page, [H.tripSpec('07:00', '07:30', { to: 'Bakery', movement: 'Bicycle' })]);
      const intermediate = await H.rows(db, c.user); expect(intermediate.filter(v => v.type === 'trip')).toHaveLength(2);
      await H.openFill(page);
      await H.activity(page, H.tripSpec('07:00', '08:00', { movement: 'Bicycle', via: [[49.839, 24.053]] }));
      await H.reviewFill(page);
      await H.replaceFill(page, prefix.groupId);
      const replacement = await H.saveFill(page);
      expect((await H.corrections(page)).find(e => e.id === prefix.id).status).toBe('REPLACED');
      await H.durable(page, db, c, values => {
        const trips = values.filter(v => v.type === 'trip'); expect(trips).toHaveLength(1);
        expect(trips[0]).toMatchObject({ start: H.at('07:00'), finish: H.at('08:00'), movement: 'BICYCLE' });
        expect(trips[0].route).toHaveLength(3); expect(trips[0].route[1].longitude).toBeCloseTo(24.053, 4);
      });
      await H.historyChange(page, replacement, 'Undo'); expect(await H.rows(db, c.user)).toEqual(intermediate);
    });
  });
});
