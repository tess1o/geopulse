import { test, expect } from '../fixtures/timeline-corrections-fixture.js';
import * as H from '../utils/timeline-corrections/helpers.js';
import { weekDays, weekSeed } from '../utils/timeline-corrections/seeds.js';
import { randomUUID } from 'node:crypto';

/**
 * A correction changes no GPS point, so the server rebuilds only the period it affects and keeps every other row as
 * it is. Each test edits the third of seven recorded days and checks three things: the edit is visible, rows of the
 * other days keep their ids (they were not rebuilt), and a forced full regeneration produces exactly the same rows.
 */

/** Row ids by kind and start, for rows more than a day away from the edited day. */
async function farRowIds(db, user, day = H.DAY) {
  const from = new Date(Date.parse(`${day}T00:00:00.000Z`) - 86400000).toISOString();
  const to = new Date(Date.parse(`${day}T00:00:00.000Z`) + 2 * 86400000).toISOString();
  const result = await db.client.query(`
    SELECT 'stay' AS type, id, timestamp AS start FROM timeline_stays
     WHERE user_id = $1 AND (timestamp < $2 OR timestamp >= $3)
    UNION ALL
    SELECT 'trip', id, timestamp FROM timeline_trips
     WHERE user_id = $1 AND (timestamp < $2 OR timestamp >= $3)
    ORDER BY start, type`, [user, from, to]);
  return result.rows.map(row => `${row.type} ${H.iso(row.start)} #${row.id}`);
}

async function showDay(page, context, day) {
  context.url = `/app/timeline?start=${day}&end=${day}`;
  await page.goto(context.url);
  await page.waitForLoadState('networkidle');
  await expect(page.locator('.timeline-history-button')).toBeVisible();
}

/** The edit stays, its far rows were never rebuilt, and undo, redo and a full regeneration all agree. */
async function windowLifecycle(page, db, context, edit, far, assertChanged) {
  const changed = await H.rows(db, context.user);
  await assertChanged(changed);
  expect(changed).not.toEqual(context.baseline);
  expect(await farRowIds(db, context.user), 'rows of other days are kept, not rebuilt').toEqual(far);

  await H.historyChange(page, edit, 'Undo');
  expect(await H.rows(db, context.user), 'undo restores the recorded week').toEqual(context.baseline);
  expect(await farRowIds(db, context.user)).toEqual(far);

  await H.historyChange(page, edit, 'Redo');
  expect(await H.rows(db, context.user)).toEqual(changed);

  await H.regenerate(page);
  expect(await H.rows(db, context.user), 'a full regeneration gives the same week').toEqual(changed);
  await assertChanged(await H.rows(db, context.user));
}

test.describe('Timeline corrections rebuild only their period', () => {
  test.use({ timelineSeed: weekSeed });

  test.beforeEach(async ({ page, scenario: c }) => {
    await showDay(page, c, H.DAY);
  });

  test('E2E-W1 visit label on day three', async ({ page, dbManager: db, scenario: c }) => {
    const far = await farRowIds(db, c.user);
    const edit = await H.detail(page, 'stay', '08:46', 'Visit label', 'Window label');
    await windowLifecycle(page, db, c, edit, far, rows => {
      expect(rows.filter(row => row.label === 'Window label')).toHaveLength(1);
    });
  });

  test('E2E-W2 resize the office stay on day three', async ({ page, dbManager: db, scenario: c }) => {
    const far = await farRowIds(db, c.user);
    const edit = await H.resize(page, 'stay', '08:46', `${H.DAY} 09:00`, `${H.DAY} 11:30`);
    await windowLifecycle(page, db, c, edit, far, async () => {
      await H.assertSegment(db, c, 'stay', H.at('09:00'), H.at('11:30'), { name: 'Office' });
    });
  });

  test('E2E-W3 remove the trip home on day three extending the office stay', async ({ page, dbManager: db, scenario: c }) => {
    const far = await farRowIds(db, c.user);
    const edit = await H.remove(page, 'trip', '12:02', 'EXTEND_PREVIOUS');
    await windowLifecycle(page, db, c, edit, far, rows => {
      const office = rows.find(row => row.type === 'stay' && row.name === 'Office' && row.start.startsWith(H.DAY));
      expect(office.finish).toBe(H.at('12:46'));
    });
  });

  test('E2E-W4 insert a stop into the morning trip of day three', async ({ page, dbManager: db, scenario: c }) => {
    const far = await farRowIds(db, c.user);
    const edit = await H.fill(page, [H.staySpec('08:16', '08:31', 'Pharmacy')]);
    await windowLifecycle(page, db, c, edit, far, async () => {
      await H.assertSegment(db, c, 'stay', H.at('08:16'), H.at('08:31'), { name: 'Pharmacy' });
    });
  });

  test('E2E-W5 corrections on two days saved back to back', async ({ page, dbManager: db, scenario: c }) => {
    await H.detail(page, 'stay', '08:46', 'Visit label', 'Day three');
    await showDay(page, c, weekDays[5]);
    await H.detail(page, 'stay', '08:46', 'Visit label', 'Day six');
    const both = await H.rows(db, c.user);
    expect(both.filter(row => row.label).map(row => [row.start, row.label])).toEqual([
      [H.at('08:46'), 'Day three'], [`${weekDays[5]}T08:46:00.000Z`, 'Day six'],
    ]);
    await H.regenerate(page);
    expect(await H.rows(db, c.user)).toEqual(both);
  });

  test('E2E-W6 correction of the latest stay of the recording', async ({ page, dbManager: db, scenario: c }) => {
    const last = weekDays.at(-1);
    await showDay(page, c, last);
    await H.detail(page, 'stay', '12:46', 'Visit label', 'Latest stay');
    const changed = await H.rows(db, c.user);
    expect(changed.filter(row => row.label === 'Latest stay').map(row => row.start)).toEqual([`${last}T12:46:00.000Z`]);
    await H.regenerate(page);
    expect(await H.rows(db, c.user)).toEqual(changed);
  });

  test('E2E-W7 a phone point arriving between review and save keeps the review valid', async ({ page, dbManager: db, scenario: c }) => {
    const lastDay = `${weekDays.at(-1)}T00:00:00.000Z`;
    const earlier = (await farRowIds(db, c.user)).filter(row => row.split(' ')[1] < lastDay);
    await H.openFill(page);
    await H.activity(page, H.staySpec('08:16', '08:31', 'Pharmacy'));
    await H.reviewFill(page);

    // The real ingestion path, as a tracking phone sends: a point after the whole recorded week.
    const username = `e2e-window-${c.user}`, password = randomUUID();
    const source = await page.request.post('/api/v1/gps/sources', { data: {
      type: 'OWNTRACKS', connectionType: 'HTTP', username, password, deviceId: 'timeline-window-live',
      filterInaccurateData: false, enableDuplicateDetection: false,
    } });
    expect(source.ok()).toBe(true);
    const later = `${weekDays.at(-1)}T18:30:00.000Z`;
    const ingested = await page.request.post('/api/v1/gps/ingest/owntracks', {
      headers: { Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`, 'X-Limit-D': 'timeline-window-live' },
      data: { _type: 'location', lat: H.places.Home[0], lon: H.places.Home[1], tst: Date.parse(later) / 1000, acc: 5, vel: 0, alt: 280, batt: 90 },
    });
    expect(ingested.ok()).toBe(true);
    c.gps = await H.gpsFingerprint(db, c.user); // The new point is expected; the fixture compares against it.

    const edit = await H.saveFill(page); // Rejected as "timeline changed" before the review token was scoped.
    expect(edit.status).toBe('IN_EFFECT');
    await H.assertSegment(db, c, 'stay', H.at('08:16'), H.at('08:31'), { name: 'Pharmacy' });
    const kept = (await farRowIds(db, c.user)).filter(row => row.split(' ')[1] < lastDay);
    expect(kept, 'days before the newest point keep their rows').toEqual(earlier);
  });
});
