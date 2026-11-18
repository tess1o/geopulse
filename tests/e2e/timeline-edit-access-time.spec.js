import { test, expect } from '../fixtures/timeline-corrections-fixture.js';
import * as H from '../utils/timeline-corrections/helpers.js';
import { dstSeed } from '../utils/timeline-corrections/seeds.js';
import { ShareLinkFactory } from '../utils/share-link-factory.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { TimelinePage } from '../pages/TimelinePage.js';
import { buildManagedUser } from '../utils/isolated-user-helper.js';

const noControls = async page => expect(page.locator('.timeline-edit-trigger, .timeline-history-button, .timeline-edit-count, .gap-fill-action')).toHaveCount(0);

test.describe('Timeline correction visibility and time inputs', () => {
  test.setTimeout(240000);
  test.use({ timezoneId: 'UTC', locale: 'en-US', viewport: { width: 1440, height: 1000 } });

  test('E2E-42 collaborator and public viewer see corrected content with private owner history', async ({ page, browser, dbManager: db, isolatedUsers, scenario: c }) => {
    const edit = await H.detail(page, 'stay', '08:46', 'Visit label', 'Shared corrected visit');
    const trip = await TestSetupHelper.createTrip(db, c.user, { name: 'Read-only timeline', startTime: H.at('00:00'), endTime: H.at('23:59'), status: 'COMPLETED' });
    const link = await ShareLinkFactory.createTimeline(db, c.user, { dateRange: { startDate: new Date(H.at('00:00')), endDate: new Date(H.at('23:59')), expiresAt: new Date(Date.now() + 86400000) } });
    const origin = new URL(page.url()).origin;
    const viewerContext = await browser.newContext({ baseURL: origin });
    const collaboratorContext = await browser.newContext({ baseURL: origin });
    const unrelatedContext = await browser.newContext({ baseURL: origin });
    try {
      const publicPage = await viewerContext.newPage(); await publicPage.goto(`/shared-timeline/${link.id}`);
      await expect(publicPage.locator('.visit-label')).toHaveText('Shared corrected visit'); await noControls(publicPage);
      const collaborator = await collaboratorContext.newPage();
      const managed = buildManagedUser(isolatedUsers, { timezone: 'UTC' });
      await new TimelinePage(collaborator).loginAndNavigate(managed, { dbManager: db, mapMode: 'RASTER' });
      const user = await db.getUserByEmail(managed.email);
      await TestSetupHelper.setupFriendship(db, c.user, user.id);
      await db.client.query(`INSERT INTO trip_collaborators (trip_id, collaborator_user_id, access_role, created_at, updated_at)
        VALUES ($1,$2,'VIEW',NOW(),NOW())`, [trip, user.id]);
      await collaborator.goto(`/app/trips/${trip}`); await collaborator.locator('.trip-rail-lens').getByText('Timeline', { exact: true }).click();
      await expect(collaborator.locator('.visit-label')).toHaveText('Shared corrected visit'); await noControls(collaborator);
      const other = await unrelatedContext.newPage();
      await new TimelinePage(other).loginAndNavigate(buildManagedUser(isolatedUsers), { dbManager: db, mapMode: 'RASTER' });
      await other.goto(`/app/trips/${trip}`); await expect(other.locator('.trip-access-error, .p-message-error')).toBeVisible();
      const unauthorizedHistory = await other.request.get('/api/v1/timeline/corrections');
      expect(unauthorizedHistory.ok()).toBe(true); expect(await unauthorizedHistory.json()).toEqual([]);
      expect((await other.request.post(`/api/v1/timeline/corrections/${edit.groupId}/undo`)).status()).toBe(404);
      await H.reload(page, c); await H.openAt(page, 'stay', '08:46'); await expect(H.action(page, 'Visit label')).toBeVisible();
    } finally { await Promise.all([viewerContext.close(), collaboratorContext.close(), unrelatedContext.close()]); }
  });

  for (const [id, day, valid, instant] of [
    ['44', '2026-03-29', '03:30', '2026-03-29T01:30:00.000Z'],
    ['45', '2026-10-25', '03:30', '2026-10-25T02:30:00.000Z'],
  ]) {
    test.describe(`Berlin ${day}`, () => {
      test.use({ timelineSeed: dstSeed(day) });
      test(`E2E-${id} reject ${id === '44' ? 'nonexistent' : 'ambiguous'} 02:30 then save unique instant`, async ({ page, dbManager: db, scenario: c }) => {
        expect(c.baseline.filter(v => v.type === 'stay')).toHaveLength(1);
        const original = c.baseline.find(v => v.type === 'stay');
        expect(original.start).toBe(`${day}T00:00:17.000Z`);
        await H.open(page, 'stay'); await H.action(page, 'Time').click();
        const start = H.drawer(page).getByRole('combobox', { name: 'Start', exact: true });
        await start.fill(`${day} 02:30`); await start.press('Escape');
        await expect(H.drawer(page).getByRole('button', { name: 'Review changes', exact: true })).toBeDisabled();
        await expect(H.drawer(page).getByRole('status').last()).not.toBeEmpty();
        expect(await H.corrections(page)).toEqual([]);
        await start.fill(`${day} ${valid}`); await start.press('Escape'); await H.review(page);
        const edit = await H.save(page); expect(H.iso(edit.payload.start)).toBe(instant); expect(H.iso(edit.payload.end)).toBe(original.finish);
        await H.durable(page, db, c, values => expect(values.filter(v => v.type === 'stay')).toEqual([{ ...original, start: instant }]));
        await H.open(page, 'stay'); await H.action(page, 'Time').click();
        await expect(H.drawer(page).getByRole('combobox', { name: 'Start', exact: true })).toHaveValue(`${day} ${valid}`);
      });
    });
  }
});
