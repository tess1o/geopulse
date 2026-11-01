import { test, expect } from '../fixtures/isolated-fixture.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';

test('demo persona cannot open edit history or mutate a populated timeline', async ({ page, dbManager }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: /Login as Kyiv/ }).click();
  await page.waitForURL('**/app/timeline');
  const user = await dbManager.getUserByEmail('kyiv@demo.geopulse.cc');
  expect(user).toBeTruthy();
  const id = await TestSetupHelper.createTimelineStay(dbManager, user.id, { locationName: 'Demo visit', durationSeconds: 3600 });
  const { rows: [stay] } = await dbManager.client.query('SELECT timestamp FROM timeline_stays WHERE id=$1', [id]);
  const { rows: [edit] } = await dbManager.client.query(`INSERT INTO timeline_edits
    (user_id, seq, kind, anchor_start, anchor_end, affected_from, affected_to, payload, status)
    VALUES ($1, 1, 'SET_VISIT_NAME', $2, $2::timestamptz + interval '1 hour', $2, $2::timestamptz + interval '1 hour',
      '{"visitName":"Demo label"}'::jsonb, 'ACTIVE') RETURNING id`, [user.id, stay.timestamp]);
  await dbManager.client.query("INSERT INTO timeline_edit_applications (edit_id, target_type, target_id) VALUES ($1,'STAY',$2)", [edit.id, id]);
  await dbManager.client.query("UPDATE timeline_stays SET visit_name='Demo label' WHERE id=$1", [id]);
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Kyiv' }).format(new Date(stay.timestamp));
  await page.goto(`/app/timeline?start=${date}&end=${date}`);
  await expect(page.locator('.timeline-card').filter({ hasText: 'Demo visit' })).toBeVisible();
  const mutations = [];
  page.on('request', request => { if (request.method() !== 'GET' && request.url().includes('/timeline/edits')) mutations.push(request.url()); });
  await expect(page.locator('.timeline-edit-trigger, .timeline-history-button')).toHaveCount(0);
  await page.locator('.timeline-card').filter({ hasText: 'Demo visit' }).click({ button: 'right' });
  await expect(page.getByRole('menuitem', { name: /Edit visit|Timeline changes/ })).toHaveCount(0);
  expect(mutations).toEqual([]);
  for (const suffix of ['', '/preview', `/${edit.id}/revert`, `/${edit.id}/restore`]) {
    const response = await page.request.post(`/api/v1/timeline/edits${suffix}`, { data: {
      kind: 'SET_VISIT_NAME', anchorStart: new Date(stay.timestamp).toISOString(),
      anchorEnd: new Date(Date.parse(stay.timestamp) + 3600000).toISOString(), payload: { visitName: 'Forbidden' }
    } });
    expect(response.status()).toBe(403);
    expect(response.headers()['x-geopulse-demo-blocked']).toBe('true');
  }
  expect((await dbManager.client.query('SELECT count(*)::int AS n FROM timeline_edits WHERE user_id=$1', [user.id])).rows[0].n).toBe(1);
});
