import { test, expect } from '../fixtures/timeline-corrections-fixture.js';
import * as H from '../utils/timeline-corrections/helpers.js';
import { emptySeed } from '../utils/timeline-corrections/seeds.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';

async function cascade(page, edit, accept) {
  const entry = await H.historyEntry(page, edit);
  await entry.getByRole('button', { name: 'Undo', exact: true }).click();
  const dialog = page.locator('.p-confirmdialog');
  await expect(dialog).toContainText('Undo later changes too?');
  if (!accept) { await dialog.getByRole('button', { name: 'No', exact: true }).click(); return; }
  const refreshed = H.frontendRefresh(page);
  const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(`/${edit.groupId}/undo`));
  await dialog.getByRole('button', { name: 'Yes', exact: true }).click();
  const r = await response; expect(r.ok()).toBe(true); expect(new URL(r.url()).searchParams.get('cascade')).toBe('true');
  await H.waitJob(page, await r.json());
  await H.finishFrontendRefresh(page, refreshed);
  await H.closeHistory(page);
}
async function dependentFill(page) {
  const fill = await H.fill(page, [H.staySpec('18:30', '19:30')]);
  const place = await H.detail(page, 'stay', '18:30', 'Location', 'Pharmacy');
  const label = await H.detail(page, 'stay', '18:30', 'Visit label', 'Dependent label');
  const history = await H.corrections(page);
  expect(history.find(e => e.id === place.id).beforeValues.prerequisiteSequences).toContain(fill.seq);
  expect(history.find(e => e.id === label.id).beforeValues.prerequisiteSequences).toContain(fill.seq);
  return { fill, place, label };
}

test.describe('Timeline correction history and groups', () => {
  test.setTimeout(300000);
  test.use({ timezoneId: 'America/New_York', locale: 'en-US', viewport: { width: 1440, height: 1000 } });

  test('E2E-27 nested trip stays effective when containing fill is undone', async ({ page, dbManager: db, scenario: c }) => {
    const parent = await H.fill(page, [H.staySpec('18:00', '20:00')]);
    const child = await H.fill(page, [H.tripSpec('18:30', '19:00')]);
    const nested = await H.rows(db, c.user);
    expect(nested.filter(v => v.type === 'stay' && v.favorite === c.favorites.Bakery)).toHaveLength(2);
    const effects = await db.client.query('SELECT target_type FROM timeline_correction_effects WHERE correction_id=$1', [parent.id]);
    expect(effects.rows.filter(e => e.target_type === 'STAY')).toHaveLength(2);
    await H.historyChange(page, parent, 'Undo');
    await H.assertSegment(db, c, 'trip', H.at('18:30'), H.at('19:00'), { source: 'MANUAL' });
    expect((await H.corrections(page)).find(e => e.id === child.id).status).toBe('IN_EFFECT');
    await H.historyChange(page, parent, 'Redo'); expect(await H.rows(db, c.user)).toEqual(nested);
  });

  test('E2E-28 crossing overlap retains draft until adjusted to touching range', async ({ page, dbManager: db, scenario: c }) => {
    const first = await H.fill(page, [H.staySpec('18:00', '19:00')]);
    const before = await H.rows(db, c.user);
    await H.openFill(page); await H.activity(page, H.tripSpec('18:30', '19:30'));
    const preview = await H.reviewFill(page);
    expect(preview.warnings).toContainEqual(expect.objectContaining({ code: 'PARTIAL_OVERLAP' }));
    await expect(H.editor(page).getByRole('button', { name: 'Save changes', exact: true })).toBeDisabled();
    expect(await H.rows(db, c.user)).toEqual(before); expect(await H.corrections(page)).toHaveLength(1);
    await H.activityTimes(H.editor(page).locator('.activity-form'), H.at('19:00'), H.at('19:30'));
    await H.reviewFill(page); await H.saveFill(page);
    await H.assertSegment(db, c, 'stay', H.at('18:00'), H.at('19:00'), { name: 'Bakery' });
    await H.assertSegment(db, c, 'trip', H.at('19:00'), H.at('19:30'));
    expect((await H.corrections(page)).find(e => e.id === first.id).status).toBe('IN_EFFECT');
  });

  test('E2E-29 replace whole two-activity group with cancel undo and restore', async ({ page, dbManager: db, scenario: c }) => {
    const original = await H.fill(page, [H.staySpec('18:15', '18:45'), H.tripSpec('18:45', '19:00')]);
    const before = await H.rows(db, c.user);
    const draft = async () => { await H.openFill(page); await H.activity(page, H.staySpec('18:00', '20:00', 'Pharmacy')); await H.reviewFill(page); };
    await draft(); await expect(H.replacementButton(page, original.groupId)).toBeVisible();
    await H.editor(page).getByRole('button', { name: 'Cancel', exact: true }).click();
    expect(await H.rows(db, c.user)).toEqual(before);
    await draft(); await H.replaceFill(page, original.groupId); const replacement = await H.saveFill(page);
    const members = (await H.corrections(page)).filter(e => e.groupId === original.groupId);
    expect(members).toHaveLength(2); expect(members.every(e => e.status === 'REPLACED')).toBe(true);
    const replaced = await H.rows(db, c.user);
    await H.historyChange(page, replacement, 'Undo'); expect(await H.rows(db, c.user)).toEqual(before);
    await H.historyChange(page, replacement, 'Redo'); expect(await H.rows(db, c.user)).toEqual(replaced);
  });

  test('E2E-30 cancel then confirm cascade undo and restore dependent details', async ({ page, dbManager: db, scenario: c }) => {
    const chain = await dependentFill(page); const before = await H.rows(db, c.user);
    await cascade(page, chain.fill, false); expect(await H.rows(db, c.user)).toEqual(before);
    await cascade(page, chain.fill, true); expect(await H.rows(db, c.user)).toEqual(c.baseline);
    let history = await H.corrections(page);
    for (const edit of Object.values(chain)) expect(history.find(e => e.id === edit.id).status).toBe('UNDONE');
    await H.historyChange(page, chain.fill, 'Redo'); expect(await H.rows(db, c.user)).toEqual(before);
    history = await H.corrections(page);
    for (const edit of Object.values(chain)) expect(history.find(e => e.id === edit.id).status).toBe('IN_EFFECT');
  });

  test('E2E-31 cascade restore preserves voluntarily undone detail', async ({ page, dbManager: db, scenario: c }) => {
    const chain = await dependentFill(page);
    await H.historyChange(page, chain.label, 'Undo'); const before = await H.rows(db, c.user);
    await cascade(page, chain.fill, true); expect(await H.rows(db, c.user)).toEqual(c.baseline);
    await H.historyChange(page, chain.fill, 'Redo'); expect(await H.rows(db, c.user)).toEqual(before);
    expect((await H.corrections(page)).find(e => e.id === chain.label.id).status).toBe('UNDONE');
    await expect(page.locator('.visit-label')).toHaveCount(0);
  });

  test('E2E-32 dormant correction reactivates after detector preference recovery', async ({ page, dbManager: db, scenario: c }) => {
    const edit = await H.detail(page, 'stay', '08:46', 'Visit label', 'Dormant label');
    await H.changeMinimum(page, 240); await H.reload(page, c);
    await expect(page.locator('.visit-label')).toHaveCount(0);
    await expect(await H.historyEntry(page, edit)).toContainText('Not applying:');
    await H.closeHistory(page); await H.changeMinimum(page, 7); await H.reload(page, c);
    await expect(page.locator('.visit-label')).toHaveText('Dormant label');
    await H.historyChange(page, edit, 'Undo'); await H.closeHistory(page);
    expect(await H.rows(db, c.user)).toEqual(c.baseline);
  });

  test.describe('plan scoped reconstruction', () => {
    test.use({ timelineSeed: emptySeed });
    async function plan(page, db, c, future = false) {
      const day = future ? '2099-09-15' : H.DAY;
      expect(c.baseline).toEqual([]); expect(c.gps.count).toBe(0);
      const id = await TestSetupHelper.createTrip(db, c.user, { name: 'Correction plan', startTime: `${day}T06:00:00Z`, endTime: `${day}T22:00:00Z`, status: future ? 'UPCOMING' : 'COMPLETED' });
      c.url = `/app/trips/${id}`; await page.goto(c.url);
      await expect(page.getByRole('button', { name: 'Add missing activity…', exact: true })).toBeVisible();
      const specs = [H.staySpec('08:00', '09:00', 'Home'), H.tripSpec('09:00', '10:00'), H.staySpec('10:00', '11:00', 'Office')]
        .map(s => ({ ...s, start: s.start.replace(H.DAY, day), end: s.end.replace(H.DAY, day) }));
      return { id, day, specs };
    }
    async function draftPlan(page, specs) {
      await page.getByRole('button', { name: 'Add missing activity…', exact: true }).click();
      for (const [index, spec] of specs.entries()) await H.activity(page, spec, index);
      return H.reviewFill(page);
    }
    test('E2E-33 edit and recommit existing plan reconstruction with explicit replacement', async ({ page, dbManager: db, scenario: c }) => {
      const p = await plan(page, db, c); await draftPlan(page, p.specs); const original = await H.saveFill(page);
      await H.openWorkspaceTimeline(page);
      const before = await H.rows(db, c.user);
      const updated = p.specs.map((s, i) => ({ ...s, ...(i === 0 ? { name: 'Bakery' } : i === 1 ? { movement: 'Bicycle' } : {}) }));
      await draftPlan(page, updated); await H.editor(page).getByRole('button', { name: 'Cancel', exact: true }).click();
      expect(await H.rows(db, c.user)).toEqual(before);
      await draftPlan(page, updated); await H.replaceFill(page, original.groupId); const replacement = await H.saveFill(page);
      expect((await H.corrections(page)).filter(e => e.groupId === original.groupId).every(e => e.status === 'REPLACED')).toBe(true);
      expect((await H.rows(db, c.user)).filter(v => v.type !== 'gap')).toHaveLength(3);
      await H.assertSegment(db, c, 'stay', H.at('08:00'), H.at('09:00'), { name: 'Bakery' });
      await H.assertSegment(db, c, 'trip', H.at('09:00'), H.at('10:00'), { movement: 'BICYCLE' });
      await H.historyChange(page, replacement, 'Undo'); expect(await H.rows(db, c.user)).toEqual(before);
    });
    test('E2E-34 future plan with no GPS persists grouped stay trip stay', async ({ page, dbManager: db, scenario: c }) => {
      const p = await plan(page, db, c, true); await draftPlan(page, p.specs); const edit = await H.saveFill(page);
      await H.openWorkspaceTimeline(page);
      const check = values => {
        const events = values.filter(v => v.type !== 'gap'); expect(events).toHaveLength(3);
        events.forEach((v, i) => expect(v).toMatchObject({ type: p.specs[i].type, start: p.specs[i].start, finish: p.specs[i].end }));
      };
      await H.durable(page, db, c, check);
      const members = (await H.corrections(page)).filter(e => e.groupId === edit.groupId);
      expect(members).toHaveLength(3); expect(members.every(e => Number(e.payload.tripId) === Number(p.id))).toBe(true);
      await H.historyChange(page, edit, 'Undo'); expect((await H.rows(db, c.user)).filter(v => v.type !== 'gap')).toEqual([]);
      c.url = `/app/timeline?start=${p.day}&end=${p.day}`; await H.reload(page, c);
      await H.historyChange(page, edit, 'Redo'); check(await H.rows(db, c.user));
    });
    test('E2E-35 out-of-bounds plan draft cannot partially replace existing group', async ({ page, dbManager: db, scenario: c }) => {
      const p = await plan(page, db, c); await draftPlan(page, p.specs); const original = await H.saveFill(page);
      await H.openWorkspaceTimeline(page);
      const before = await H.rows(db, c.user); const history = await H.corrections(page);
      const invalid = p.specs.map((s, i) => i === 2 ? { ...s, end: H.at('23:00') } : s);
      await page.getByRole('button', { name: 'Add missing activity…', exact: true }).click();
      for (const [i, spec] of invalid.entries()) await H.activity(page, spec, i);
      await H.editor(page).getByRole('button', { name: 'Review changes', exact: true }).click();
      await expect(H.editor(page).getByRole('alert')).not.toBeEmpty();
      expect(await H.rows(db, c.user)).toEqual(before); expect(await H.corrections(page)).toEqual(history);
      await H.activityTimes(H.editor(page).locator('.activity-form').last(), H.at('10:00'), H.at('11:30'));
      await H.reviewFill(page); await H.replaceFill(page, original.groupId); await H.saveFill(page);
      expect((await H.corrections(page)).filter(e => e.groupId === original.groupId).every(e => e.status === 'REPLACED')).toBe(true);
      await H.assertSegment(db, c, 'stay', H.at('10:00'), H.at('11:30'), { name: 'Office' });
    });
  });
});
