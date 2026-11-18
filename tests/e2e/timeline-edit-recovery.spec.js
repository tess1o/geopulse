import { test, expect } from '../fixtures/timeline-corrections-fixture.js';
import * as H from '../utils/timeline-corrections/helpers.js';
import { randomUUID } from 'node:crypto';
import { TimelinePreferencesPage } from '../pages/TimelinePreferencesPage.js';

const commitPath = '**/api/v1/timeline/corrections';
const responseFor = page => page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/timeline/corrections');
async function timeDraft(page) {
  await H.openAt(page, 'stay', '08:46'); await H.action(page, 'Time').click();
  await H.drawer(page).getByRole('combobox', { name: 'Start', exact: true }).fill(`${H.DAY} 08:50`);
  await H.review(page);
}

test.describe('Timeline correction validation and recovery', () => {
  test.setTimeout(300000);
  test.use({ timezoneId: 'America/New_York', locale: 'en-US', viewport: { width: 1440, height: 1000 } });

  test('E2E-36 invalid times invalidate review and dirty draft can be retained or discarded', async ({ page, dbManager: db, scenario: c }) => {
    const trigger = page.locator('.timeline-card--stay').nth(1).getByRole('button', { name: 'Edit visit…' });
    await trigger.click(); await H.action(page, 'Time').click();
    const start = H.drawer(page).getByRole('combobox', { name: 'Start', exact: true });
    await start.fill(`${H.DAY} 13:00`); await start.press('Escape');
    await expect(H.drawer(page).getByRole('button', { name: 'Review changes' })).toBeDisabled();
    await expect(H.drawer(page).getByRole('status').last()).not.toBeEmpty();
    await start.fill(`${H.DAY} 08:50`); await H.review(page);
    await start.fill(`${H.DAY} 08:51`); await start.press('Escape');
    await expect(H.drawer(page).locator('.edit-preview')).toHaveCount(0);
    await expect(H.drawer(page).getByRole('button', { name: 'Save changes', exact: true })).toHaveCount(0);
    page.once('dialog', d => d.dismiss());
    await H.drawer(page).getByRole('button', { name: 'Cancel', exact: true }).click();
    await expect(start).toHaveValue(`${H.DAY} 08:51`);
    page.once('dialog', d => d.accept());
    await H.drawer(page).getByRole('button', { name: 'Cancel', exact: true }).click();
    await expect(H.drawer(page)).toBeHidden(); await expect(trigger).toBeFocused();
    expect(await H.rows(db, c.user)).toEqual(c.baseline); expect(await H.corrections(page)).toEqual([]);
  });

  test('E2E-37 stale preview from second authenticated tab requires explicit reconfirmation', async ({ page, dbManager: db, scenario: c }) => {
    await timeDraft(page);
    const second = await page.context().newPage();
    try {
      await second.goto(c.url); await H.detail(second, 'stay', '08:46', 'Visit label', 'Tab B label');
      const rejected = responseFor(page);
      await H.drawer(page).getByRole('button', { name: 'Save changes', exact: true }).click();
      expect((await rejected).status()).toBe(409);
      await expect(H.drawer(page).getByRole('alert')).toContainText(/review|changed/i);
      await expect(H.drawer(page).getByRole('combobox', { name: 'Start', exact: true })).toHaveValue(`${H.DAY} 08:50`);
      await expect(H.drawer(page).getByRole('button', { name: 'Save changes', exact: true })).toBeEnabled();
      expect(await H.corrections(page)).toHaveLength(1);
      await H.save(page);
      await H.durable(page, db, c, values => expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: H.at('08:50'), label: 'Tab B label' })));
    } finally { await second.close(); }
  });

  test('E2E-38 detector settings change invalidates another tabs reviewed draft', async ({ page, dbManager: db, scenario: c }) => {
    await timeDraft(page); const settings = await page.context().newPage();
    try {
      await H.changeMinimum(settings, 8);
      const rejected = responseFor(page); await H.drawer(page).getByRole('button', { name: 'Save changes', exact: true }).click();
      expect((await rejected).status()).toBe(409);
      await expect(H.drawer(page).getByRole('alert')).toContainText(/review|changed/i);
      expect(await H.corrections(page)).toEqual([]);
      await expect(H.drawer(page).getByRole('combobox', { name: 'Start', exact: true })).toHaveValue(`${H.DAY} 08:50`);
      await H.save(page);
      await H.durable(page, db, c, values => expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: H.at('08:50'), name: 'Office' })));
    } finally { await settings.close(); }
  });

  test('E2E-39 busy generation preserves the draft and a later retry saves once', async ({ page, dbManager: db, scenario: c, generationGate }) => {
    await H.openAt(page, 'stay', '08:46'); await H.action(page, 'Visit label').click();
    const input = H.drawer(page).getByRole('textbox');
    await input.fill('Retry after generation');
    const gate = await generationGate(c.user);
    try {
      const started = await page.request.post('/api/v1/timeline/jobs'); expect(started.ok()).toBe(true);
      const active = await started.json(); await gate.blockedPid();
      const response = responseFor(page); await H.drawer(page).getByRole('button', { name: 'Save changes', exact: true }).click();
      const rejected = await response;
      expect(rejected.status()).toBe(409);
      expect((await rejected.json()).code).toBe('TIMELINE_REGENERATION_IN_PROGRESS');
      await expect(H.drawer(page).getByRole('alert')).toContainText(/update in progress/i);
      await expect(input).toHaveValue('Retry after generation');
      expect(await H.corrections(page)).toEqual([]);
      expect(await H.gpsFingerprint(db, c.user)).toEqual(c.gps);
      await gate.release(); await H.waitJob(page, active);
      await expect(input).toHaveValue('Retry after generation');
      const edit = await H.save(page);
      const history = await H.corrections(page);
      expect(history).toHaveLength(1); expect(history[0].groupId).toBe(edit.groupId);
      await H.durable(page, db, c, values => expect(values).toContainEqual(expect.objectContaining({
        type: 'stay', start: H.at('08:46'), label: 'Retry after generation',
      })));
    } finally { await gate.release(); }
  });

  test('E2E-40 delayed real response locks Save against double activation', async ({ page, dbManager: db, scenario: c }) => {
    await timeDraft(page);
    let release; const hold = new Promise(resolve => { release = resolve; });
    let received; const backendDone = new Promise(resolve => { received = resolve; });
    let commits = 0;
    await page.route(commitPath, async route => {
      if (route.request().method() !== 'POST') return route.continue();
      commits++; const response = await route.fetch(); received(response); await hold; await route.fulfill({ response });
    });
    try {
      const button = H.drawer(page).getByRole('button', { name: 'Save changes', exact: true });
      const box = await button.boundingBox(); await button.click();
      const backend = await backendDone; expect(backend.ok()).toBe(true);
      await expect(button).toBeDisabled(); await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
      expect(commits).toBe(1); expect(await H.corrections(page)).toHaveLength(1);
      release(); const result = await backend.json(); await H.waitJob(page, result);
      await expect(H.drawer(page)).toBeHidden();
      await H.durable(page, db, c, values => expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: H.at('08:50') })));
      expect(await H.corrections(page)).toHaveLength(1);
    } finally { release(); await page.unroute(commitPath); }
  });

  test('E2E-41 drop response after confirmed commit then reconcile by reload', async ({ page, dbManager: db, scenario: c }) => {
    await timeDraft(page); let committed;
    await page.route(commitPath, async route => {
      if (route.request().method() !== 'POST') return route.continue();
      const response = await route.fetch(); expect(response.ok()).toBe(true); committed = await response.json();
      expect((await db.client.query('SELECT id FROM timeline_edits WHERE user_id=$1 AND group_id=$2', [c.user, committed.groupId])).rowCount).toBe(1);
      await route.abort('connectionreset');
    });
    try {
      await H.drawer(page).getByRole('button', { name: 'Save changes', exact: true }).click();
      await expect(H.drawer(page).getByRole('alert')).not.toBeEmpty();
      await expect(H.drawer(page).getByRole('combobox', { name: 'Start', exact: true })).toHaveValue(`${H.DAY} 08:50`);
      expect(committed).toBeTruthy(); await H.waitJob(page, committed);
      await page.unroute(commitPath); await H.reload(page, c);
      await expect(page.locator('.timeline-card--stay').nth(1)).toContainText('08:50');
      expect(await H.corrections(page)).toHaveLength(1);
      await H.durable(page, db, c, values => expect(values).toContainEqual(expect.objectContaining({ type: 'stay', start: H.at('08:50') })));
    } finally { await page.unroute(commitPath); }
  });

  test.describe('ongoing gap with server-relative clock', () => {
    test.use({ timezoneId: 'UTC', timelineSeed: { recipe: async ({ page, stay, places }) => {
      const response = await page.request.get('/api/v1/timeline/jobs/current');
      const serverNow = Date.parse(response.headers().date);
      expect(Number.isFinite(serverNow)).toBe(true);
      const anchor = Math.floor(serverNow / 3600000) * 3600000;
      await page.clock.setFixedTime(serverNow);
      stay(places.Home, H.iso(anchor - 5 * 3600000), H.iso(anchor - 3 * 3600000));
    } } });
    test('E2E-49 fill completed slice of ongoing gap then ingest one later recording', async ({ page, dbManager: db, scenario: c }) => {
      test.setTimeout(480000); // The default real-time scheduler runs every five minutes.
      const recorded = (await db.client.query('SELECT min(timestamp) AS first, max(timestamp) AS last FROM gps_points WHERE user_id=$1', [c.user])).rows[0];
      const serverNow = Date.parse((await page.request.get('/api/v1/timeline/jobs/current')).headers().date);
      const last = H.iso(recorded.last);
      const start = H.iso(Date.parse(last) + 3600000), end = H.iso(Date.parse(last) + 5400000);
      const later = H.iso(Date.parse(last) + 7200000);
      const fromDay = H.iso(recorded.first).slice(0, 10), toDay = H.iso(serverNow).slice(0, 10);
      c.url = `/app/timeline?start=${fromDay}&end=${toDay}`; await page.goto(c.url);
      const fingerprintBeforeEdit = await H.gpsFingerprint(db, c.user);
      const points = async () => (await db.client.query('SELECT id, to_jsonb(p) AS value FROM gps_points p WHERE user_id=$1 ORDER BY id', [c.user])).rows;
      const beforePoints = await points();
      const publicTimeline = async () => {
        const response = await page.request.get(`/api/v1/timeline?from=${fromDay}T00:00:00Z&to=${toDay}T23:59:59.999Z`);
        expect(response.ok()).toBe(true); return response.json();
      };
      const gapShape = timeline => timeline.dataGaps.map(g => ({ start: H.iso(g.startTime),
        end: g.ongoing ? 'ONGOING' : H.iso(g.endTime), ongoing: g.ongoing }));
      const idle = async () => (await db.client.query(`SELECT timeline_status AS status,
        NOT EXISTS (SELECT 1 FROM timeline_rebuild_requests WHERE user_id=$1) AS current
        FROM users WHERE id=$1`, [c.user])).rows[0];
      const rawBounds = async () => (await db.client.query('SELECT start_time AS start, end_time AS finish FROM timeline_data_gaps WHERE user_id=$1 ORDER BY start_time', [c.user])).rows;
      const initial = await publicTimeline();
      expect(gapShape(initial)).toEqual([{ start: last, end: 'ONGOING', ongoing: true }]);
      expect(await idle()).toEqual({ status: 'IDLE', current: true });
      expect(await H.corrections(page)).toEqual([]);

      const edit = await H.fill(page, [{ type: 'stay', start, end, name: 'Bakery' }]);
      expect(await H.gpsFingerprint(db, c.user), 'the edit itself cannot alter GPS before the intentional recording').toEqual(fingerprintBeforeEdit);
      const saved = await publicTimeline();
      expect(gapShape(saved)).toEqual([
        { start: last, end: start, ongoing: false }, { start: end, end: 'ONGOING', ongoing: true },
      ]);
      expect(saved.stays.filter(stay => H.iso(stay.timestamp) === start)).toEqual([
        expect.objectContaining({ stayDuration: 1800, locationName: 'Bakery', favoriteId: Number(c.favorites.Bakery) }),
      ]);
      await expect(page.locator('.timeline-card--stay').filter({ hasText: 'Bakery' })).toHaveCount(1);
      const historyBeforeIngest = await H.corrections(page);
      expect(historyBeforeIngest).toHaveLength(1); expect(historyBeforeIngest[0]).toMatchObject({ id: edit.id, groupId: edit.groupId, status: 'IN_EFFECT' });
      await expect.poll(idle).toEqual({ status: 'IDLE', current: true });

      // Use the real ingestion path: it must enqueue work for the real-time processor.
      const username = `e2e-live-${c.user}`, password = randomUUID();
      const source = await page.request.post('/api/v1/gps/sources', { data: {
        type: 'OWNTRACKS', connectionType: 'HTTP', username, password, deviceId: 'timeline-edit-live',
        filterInaccurateData: false, enableDuplicateDetection: false,
      } });
      expect(source.ok()).toBe(true);
      const ingested = await page.request.post('/api/v1/gps/ingest/owntracks', {
        headers: { Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`, 'X-Limit-D': 'timeline-edit-live' },
        data: { _type: 'location', lat: H.places.Office[0], lon: H.places.Office[1], tst: Date.parse(later) / 1000, acc: 5, vel: 0, alt: 280, batt: 90 },
      });
      expect(ingested.ok()).toBe(true);
      await expect.poll(async () => (await points()).length).toBe(beforePoints.length + 1);
      const afterPoints = await points();
      expect(afterPoints.filter(p => beforePoints.some(old => old.id === p.id))).toEqual(beforePoints);
      const [added] = afterPoints.filter(p => !beforePoints.some(old => old.id === p.id));
      expect(added.value).toMatchObject({ device_id: 'timeline-edit-live', source_type: 'OWNTRACKS', accuracy: 5, velocity: 0, battery: 90, altitude: 280 });
      expect(H.iso(added.value.timestamp)).toBe(later);
      const coordinate = (await db.client.query('SELECT ST_Y(coordinates) AS lat, ST_X(coordinates) AS lon FROM gps_points WHERE id=$1', [added.id])).rows[0];
      expect(coordinate).toEqual({ lat: H.places.Office[0], lon: H.places.Office[1] });
      c.gps = await H.gpsFingerprint(db, c.user); // Already proved exactly the expected new point and unchanged old rows.

      // No /timeline/jobs call here: the later recording must advance the real-time timeline on its own.
      await expect.poll(async () => gapShape(await publicTimeline()), { timeout: 360000 }).toEqual([
        { start: last, end: start, ongoing: false }, { start: end, end: later, ongoing: false },
        { start: later, end: 'ONGOING', ongoing: true },
      ]);
      await expect.poll(idle).toEqual({ status: 'IDLE', current: true });
      const gaps = await rawBounds();
      expect(gaps).toHaveLength(3); expect(Date.parse(gaps[2].finish)).toBeGreaterThan(Date.parse(later));
      expect(Date.parse(gaps[2].finish)).toBeGreaterThanOrEqual(serverNow);
      const effective = (await H.rows(db, c.user)).filter(row => row.finish !== 'ONGOING' && row.start < row.finish);
      for (let i = 1; i < effective.length; i++) expect(Date.parse(effective[i - 1].finish)).toBeLessThanOrEqual(Date.parse(effective[i].start));
      expect(effective.filter(row => row.type === 'stay' && row.start === start)).toEqual([
        expect.objectContaining({ finish: end, name: 'Bakery', favorite: c.favorites.Bakery }),
      ]);
      const finalHistory = await H.corrections(page);
      expect(finalHistory).toHaveLength(1); expect(finalHistory[0]).toMatchObject({ id: edit.id, groupId: edit.groupId, status: 'IN_EFFECT' });
      expect(await H.gpsFingerprint(db, c.user)).toEqual(c.gps);
      await H.reload(page, c); await expect(page.locator('.timeline-card--stay').filter({ hasText: 'Bakery' })).toHaveCount(1);
      await H.assertSegment(db, c, 'stay', start, end, { name: 'Bakery' });

    });
  });

  test('E2E-50 real generation failure retains committed correction and supported retry recovers once', async ({ page, dbManager: db, scenario: c, generationGate }) => {
    await timeDraft(page); const gate = await generationGate(c.user);
    const response = responseFor(page); await H.drawer(page).getByRole('button', { name: 'Save changes', exact: true }).click();
    const saved = await response; expect(saved.ok()).toBe(true); const result = await saved.json();
    await gate.blockedPid(); await gate.failBlockedJob();
    await expect.poll(async () => (await (await page.request.get(`/api/v1/timeline/jobs/${result.timelineJobId}`)).json()).status).toBe('FAILED');
    await gate.release(); expect(await H.corrections(page)).toHaveLength(1);
    await expect(page.getByText(/failed|could not.*updat/i).first()).toBeVisible();
    const preferences = new TimelinePreferencesPage(page); await preferences.navigate(); await preferences.waitForPageLoad();
    await preferences.regenerateButton().click();
    const dialog = page.locator('.p-confirmdialog'); await expect(dialog).toBeVisible();
    const retry = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/v1/timeline/jobs');
    await dialog.getByRole('button', { name: /Regenerate|Yes|Confirm/ }).click();
    const retried = await retry; expect(retried.ok()).toBe(true); await H.waitJob(page, await retried.json());
    await H.reload(page, c); expect(await H.corrections(page)).toHaveLength(1);
    await H.assertSegment(db, c, 'stay', H.at('08:50'), c.baseline.find(v => v.start === H.at('08:46')).finish);
    await expect(page.locator('.timeline-card--stay').nth(1)).toContainText('08:50');
  });
});
