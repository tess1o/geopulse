import { test, expect } from '../fixtures/timeline-corrections-fixture.js';
import * as H from '../utils/timeline-corrections/helpers.js';
import { DataExportImportPage } from '../pages/DataExportImportPage.js';
import { TimelinePage } from '../pages/TimelinePage.js';
import { buildManagedUser } from '../utils/isolated-user-helper.js';
import { manualTrip } from '../utils/timeline-corrections/seeds.js';

const semantic = values => values.map(({ favorite, geocoding, ...v }) => v);
const groupShape = edits => {
  const groups = new Map();
  for (const e of edits) {
    if (!groups.has(e.groupId)) groups.set(e.groupId, []);
    groups.get(e.groupId).push({ op: e.op, status: e.status, start: e.anchorStart, end: e.anchorEnd });
  }
  return [...groups.values()].map(g => g.sort((a,b) => a.start.localeCompare(b.start))).sort((a,b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));
};

test.use({ timezoneId: 'America/New_York', locale: 'en-US', viewport: { width: 1440, height: 1000 } });
test('E2E-48 export real corrected archive then import into empty isolated account', async ({ page, browser, dbManager: db, isolatedUsers, scenario: c }, testInfo) => {
  test.setTimeout(360000);
  await H.fill(page, [manualTrip]);
  await H.fill(page, [H.staySpec('19:00', '19:15', 'Car Park', { point: [49.839, 24.053] })]);
  const label = await H.detail(page, 'stay', '19:00', 'Visit label', 'Portable label');
  await H.historyChange(page, label, 'Undo'); await H.closeHistory(page);
  const original = await H.fill(page, [H.staySpec('20:15', '20:30'), H.staySpec('20:30', '20:45', 'Office')]);
  await H.openFill(page); await H.activity(page, H.staySpec('20:00', '20:55', 'Pharmacy')); await H.reviewFill(page); await H.replaceFill(page, original.groupId); await H.saveFill(page);
  const expected = semantic(await H.rows(db, c.user)); const history = await H.corrections(page);
  const exportPage = new DataExportImportPage(page); await exportPage.navigate();
  await page.locator('label[for="geopulse"]').click();
  for (const type of ['rawgps', 'favorites', 'timelineedits']) await page.locator(`#${type}`).check();
  await page.getByRole('button', { name: 'All Time', exact: true }).click();
  await exportPage.clickStartExport();
  await expect(page.locator(exportPage.selectors.export.jobStatus)).toHaveText(/completed/i, { timeout: 90000 });
  const downloading = page.waitForEvent('download'); await page.locator(exportPage.selectors.export.downloadButton).click();
  const download = await downloading; const archive = testInfo.outputPath('corrected-timeline.zip'); await download.saveAs(archive);
  const targetContext = await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const target = await targetContext.newPage(); const user = buildManagedUser(isolatedUsers, { timezone: 'UTC', dateFormat: 'YMD', timeFormat: '24h' });
    await new TimelinePage(target).loginAndNavigate(user, { dbManager: db, mapMode: 'RASTER' });
    const importedUser = await db.getUserByEmail(user.email);
    expect(await H.rows(db, importedUser.id)).toEqual([]); expect((await H.gpsFingerprint(db, importedUser.id)).count).toBe(0);
    const importer = new DataExportImportPage(target); await importer.navigate();
    await target.locator(importer.selectors.tabs.import).click(); await target.locator('label[for="import-geopulse"]').click();
    await target.locator(importer.selectors.import.fileUpload).setInputFiles(archive);
    for (const type of ['rawgps', 'favorites', 'timelineedits']) await target.locator(`#import-${type}`).check();
    await importer.clickStartImport();
    await expect(target.locator(importer.selectors.import.jobStatus)).toHaveText(/completed/i, { timeout: 120000 });
    await H.regenerate(target);
    expect(semantic(await H.rows(db, importedUser.id))).toEqual(expected);
    const importedHistory = await H.corrections(target); expect(groupShape(importedHistory)).toEqual(groupShape(history));
    // Corrections get new ids in the target account; their group ids are kept, like any other backed-up value.
    expect(importedHistory.every(e => !history.some(old => old.id === e.id))).toBe(true);
    expect(new Set(importedHistory.map(e => e.groupId))).toEqual(new Set(history.map(e => e.groupId)));
    expect(await H.gpsFingerprint(db, c.user)).toEqual(c.gps); // the export left the source recording untouched
    // The import carries every recorded field; only the server receipt time is new.
    expect(await H.gpsFingerprint(db, importedUser.id, { portable: true }))
      .toEqual(await H.gpsFingerprint(db, c.user, { portable: true }));
    await target.goto(c.url);
    const group = importedHistory.find(e => e.op === 'PLACE_TRIP' && e.status === 'IN_EFFECT');
    await H.historyChange(target, group, 'Undo'); await H.historyChange(target, group, 'Redo');
    expect(semantic(await H.rows(db, importedUser.id))).toEqual(expected);
  } finally { await targetContext.close(); }
});
