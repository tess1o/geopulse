import { test, expect } from '../fixtures/timeline-corrections-fixture.js';
import * as H from '../utils/timeline-corrections/helpers.js';

test.use({ timezoneId: 'America/New_York', locale: 'en-US' });
test('E2E-46 mobile calendar edit traps restores focus and persists accessible result', async ({ page, dbManager: db, scenario: c }) => {
  test.setTimeout(240000);
  const trigger = page.locator('.timeline-card--stay').nth(1).getByRole('button', { name: 'Edit visit…' });
  await trigger.focus(); await page.keyboard.press('Enter'); await expect(H.drawer(page)).toBeVisible();
  const bounds = await H.drawer(page).boundingBox();
  // The drawer must fit the phone's own viewport (Pixel 7: 412 CSS px), not a fixed device width.
  expect(Math.round(bounds.width)).toBeLessThanOrEqual(page.viewportSize().width); expect(bounds.x).toBeGreaterThanOrEqual(-.5);
  expect(await H.drawer(page).evaluate(e => e.scrollWidth <= e.clientWidth + 1)).toBe(true);
  for (let i = 0; i < 15; i++) {
    await page.keyboard.press('Tab'); expect(await H.drawer(page).evaluate(e => e.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press('Escape'); await expect(H.drawer(page)).toBeHidden(); await expect(trigger).toBeFocused();
  await page.keyboard.press('Enter'); await H.action(page, 'Time').click();
  const start = H.drawer(page).getByRole('combobox', { name: 'Start', exact: true });
  await start.click(); await expect(page.getByRole('dialog', { name: 'Choose Date' })).toBeVisible();
  await start.press('Escape'); await start.fill(`${H.DAY} 13:00`); await start.press('Escape');
  await expect(H.drawer(page).getByRole('status').last()).not.toBeEmpty();
  await expect(H.drawer(page).getByRole('button', { name: 'Review changes' })).toBeDisabled();
  await start.fill(`${H.DAY} 08:50`); await start.press('Escape'); await H.review(page);
  await expect(H.drawer(page).locator('.edit-preview')).toBeInViewport();
  await expect(H.drawer(page).getByRole('button', { name: 'Save changes', exact: true })).toBeInViewport();
  await H.save(page); await H.reload(page, c);
  await H.assertSegment(db, c, 'stay', H.at('08:50'), c.baseline.find(v => v.start === H.at('08:46')).finish);
  await H.open(page, 'stay', 1); await page.keyboard.press('Escape'); await expect(H.drawer(page)).toBeHidden();
});
