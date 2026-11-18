import { test, expect } from '../fixtures/timeline-corrections-fixture.js';
import * as H from '../utils/timeline-corrections/helpers.js';
import { MapEngineHarness } from '../utils/map-engine-harness.js';

test.use({ timezoneId: 'America/New_York', locale: 'en-US', viewport: { width: 1440, height: 1000 } });
test('E2E-47 vector draw route then move vertex and undo restore route edit', async ({ page, dbManager: db, scenario: c }) => {
  test.setTimeout(300000);
  await H.openFill(page);
  await H.activity(page, H.tripSpec('18:30', '19:30', { via: [[49.841, 24.04]], movement: 'Bicycle' }));
  const harness = new MapEngineHarness(page);
  expect(await harness.getActiveMode({ rootSelector: '.missing-activity-editor' })).toBe('VECTOR');
  await H.reviewFill(page); await H.saveFill(page); c.baseline = await H.rows(db, c.user);
  await H.openAt(page, 'trip', '18:30'); await H.action(page, 'Edit trip route').click();
  await expect(H.editor(page).locator('.route-points > li')).toHaveCount(1);
  await H.editor(page).getByRole('button', { name: 'Route point 1', exact: true }).click();
  const point = [49.839, 24.05]; const clicked = await H.mapClick(page, point);
  const host = harness.getMapHostLocator({ rootSelector: '.missing-activity-editor' });
  const bounds = await host.boundingBox(); expect(bounds.width).toBeGreaterThan(100); expect(bounds.height).toBeGreaterThan(100);
  await H.reviewFill(page); const edit = await H.saveFill(page);
  await H.lifecycle(page, db, c, edit, values => {
    const trips = values.filter(v => v.start === H.at('18:30')); expect(trips).toHaveLength(1);
    expect(trips[0].route).toHaveLength(3); expect(trips[0].route[1].latitude).toBeCloseTo(clicked.latitude, 6); expect(trips[0].route[1].longitude).toBeCloseTo(clicked.longitude, 6);
    expect(trips[0]).toMatchObject({ latitude: H.places.Home[0], longitude: H.places.Home[1], end_latitude: H.places.Office[0], end_longitude: H.places.Office[1] });
  });
});
