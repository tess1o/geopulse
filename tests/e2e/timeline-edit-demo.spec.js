import { test, expect } from '../fixtures/isolated-fixture.js';
import { createHash } from 'node:crypto';

test.use({ locale: 'en-US', timezoneId: 'Europe/Kyiv' });
test('E2E-43 populated demo has no edit history or mutation workflows', async ({ page, dbManager: db, testIdentity }) => {
  await page.goto('/login'); await page.getByRole('button', { name: /Login as Kyiv/ }).click();
  await page.waitForURL('**/app/timeline');
  const user = await db.getUserByEmail('kyiv@demo.geopulse.cc'); expect(user).toBeTruthy();
  // Demo authentication selects a fixed persona. Own only tagged rows in a random historical day
  // in the disposable demo deployment; never clear or modify that persona's pre-existing data.
  const hash = createHash('sha256').update(testIdentity.uniqueToken).digest().readUInt32BE(0);
  const day = new Date(Date.UTC(1980, 0, 1) + (hash % 7000) * 86400000).toISOString().slice(0, 10);
  const start = `${day}T06:00:00Z`; const finish = `${day}T10:00:00Z`;
  const before = await db.client.query('SELECT id FROM timeline_stays WHERE user_id=$1 AND timestamp BETWEEN $2 AND $3', [user.id, start, finish]);
  expect(before.rowCount, 'demo fixture interval must be unoccupied').toBe(0);
  const stays = []; let trip; let gap; let edit;
  try {
    await db.client.query('BEGIN');
    for (const [hour, name, coords] of [['06', 'Demo start', 'POINT(30.5 50.4)'], ['09', 'Demo destination', 'POINT(30.6 50.5)']]) {
      const { rows: [row] } = await db.client.query(`INSERT INTO timeline_stays
        (user_id, timestamp, stay_duration, location, location_name, location_source, created_at, last_updated)
        VALUES ($1,$2,3600,ST_GeomFromText($3,4326),$4,'GEOCODING',NOW(),NOW()) RETURNING id`, [user.id, `${day}T${hour}:00:00Z`, coords, name]);
      stays.push(row.id);
    }
    ({ rows: [trip] } = await db.client.query(`INSERT INTO timeline_trips
      (user_id,timestamp,trip_duration,start_point,end_point,distance_meters,movement_type,movement_type_source,created_at,last_updated)
      VALUES ($1,$2,3600,ST_GeomFromText('POINT(30.5 50.4)',4326),ST_GeomFromText('POINT(30.6 50.5)',4326),12000,'CAR','AUTO',NOW(),NOW()) RETURNING id`, [user.id, `${day}T07:00:00Z`]));
    ({ rows: [gap] } = await db.client.query(`INSERT INTO timeline_data_gaps (user_id,start_time,end_time,duration_seconds)
      VALUES ($1,$2,$3,3600) RETURNING id`, [user.id, `${day}T08:00:00Z`, `${day}T09:00:00Z`]));
    ({ rows: [edit] } = await db.client.query(`INSERT INTO timeline_edits
      (user_id,group_id,seq,kind,anchor_start,anchor_end,affected_from,affected_to,payload,status)
      SELECT $1,gen_random_uuid(),COALESCE(max(seq),0)+1,'SET_VISIT_NAME',$2,$3,$2,$3,
        '{"payloadType":"SET_LABEL","visitName":"Demo label"}'::jsonb,'ACTIVE'
      FROM timeline_edits WHERE user_id=$1 RETURNING id`, [user.id, start, `${day}T07:00:00Z`]));
    await db.client.query("INSERT INTO timeline_correction_effects (correction_id,target_type,target_id) VALUES ($1,'STAY',$2)", [edit.id, stays[0]]);
    await db.client.query("UPDATE timeline_stays SET visit_name='Demo label' WHERE id=$1", [stays[0]]);
    await db.client.query('COMMIT');
    await page.goto(`/app/timeline?start=${day}&end=${day}`);
    const requests = [];
    page.on('request', request => { if (request.method() !== 'GET' && /\/timeline\/corrections/.test(request.url())) requests.push(request.url()); });
    for (const selector of ['.timeline-card--stay', '.timeline-card--trip', '.timeline-card--data-gap']) {
      const card = page.locator(selector).first(); await expect(card).toBeVisible(); await card.click({ button: 'right' });
      await expect(page.getByRole('menuitem', { name: /Edit visit|Edit trip|Fill missing|Timeline changes/ })).toHaveCount(0);
      await page.keyboard.press('Escape');
    }
    await expect(page.locator('.visit-label')).toHaveText('Demo label');
    await expect(page.locator('.timeline-edit-trigger, .timeline-history-button, .gap-fill-action')).toHaveCount(0);
    expect(requests).toEqual([]);
  } finally {
    await db.client.query('ROLLBACK');
    if (edit) { await db.client.query('DELETE FROM timeline_correction_effects WHERE correction_id=$1', [edit.id]); await db.client.query('DELETE FROM timeline_edits WHERE id=$1 AND user_id=$2', [edit.id, user.id]); }
    if (trip) await db.client.query('DELETE FROM timeline_trips WHERE id=$1 AND user_id=$2', [trip.id, user.id]);
    if (gap) await db.client.query('DELETE FROM timeline_data_gaps WHERE id=$1 AND user_id=$2', [gap.id, user.id]);
    await db.client.query('DELETE FROM timeline_stays WHERE id=ANY($1::bigint[]) AND user_id=$2', [stays, user.id]);
  }
});
