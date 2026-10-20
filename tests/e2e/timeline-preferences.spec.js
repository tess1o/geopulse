import { test, expect } from '../fixtures/isolated-fixture.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';
import { GpsDataFactory } from '../utils/gps-data-factory.js';
import { TimelinePreferencesPage } from '../pages/TimelinePreferencesPage.js';

// Pick a value inside the slider range that differs from the current one.
const differentValue = (current, candidates) => candidates.find((value) => value !== current);

const loginAndOpenPreferences = async (page, dbManager, isolatedUsers, { seedGps = false, storedPreferences, tab } = {}) => {
  const testUser = createManagedUser(isolatedUsers);
  const { user } = await TestSetupHelper.createAndLoginUser(page, dbManager, testUser);

  if (seedGps) {
    const gpsData = GpsDataFactory.generateTestData(user.id, 'prefs-device');
    await GpsDataFactory.insertGpsData(dbManager, gpsData.allPoints);
  }
  if (storedPreferences !== undefined) {
    await TimelinePreferencesPage.setStoredPreferences(dbManager, user.id, storedPreferences);
  }

  const preferencesPage = new TimelinePreferencesPage(page);
  await preferencesPage.navigate(tab);
  await preferencesPage.waitForPageLoad();

  return { user, preferencesPage };
};

test.describe('Timeline Preferences', () => {
  test.describe('Initial State', () => {
    test('should show server defaults with nothing to save for a user without overrides', async ({ page, isolatedUsers, dbManager }) => {
      const { user, preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers);

      expect(await TimelinePreferencesPage.getStoredPreferences(dbManager, user.id)).toBeNull();

      const response = await page.request.get('/api/v1/timeline/preferences');
      expect(response.ok()).toBe(true);
      const effective = await response.json();

      expect(await preferencesPage.getNumberValue('staypointRadiusMeters')).toBe(effective.staypointRadiusMeters);
      expect(await preferencesPage.getNumberValue('staypointMinDurationMinutes')).toBe(effective.staypointMinDurationMinutes);
      await expect(preferencesPage.saveButton()).toBeDisabled();
      await expect(preferencesPage.unsavedWarning()).toBeHidden();
    });

    test('should fall back to the stay point tab for an unknown tab parameter', async ({ page, isolatedUsers, dbManager }) => {
      const { preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers, { tab: 'does-not-exist' });

      await expect(page).toHaveURL(/tab=staypoints/);
      await expect(preferencesPage.numberInput('staypointMinDurationMinutes')).toBeVisible();
    });

    test('should show a stored override instead of the default', async ({ page, isolatedUsers, dbManager }) => {
      const { preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers, {
        storedPreferences: { staypointMinDurationMinutes: 42 }
      });

      expect(await preferencesPage.getNumberValue('staypointMinDurationMinutes')).toBe(42);
    });
  });

  test.describe('Saving', () => {
    test('should save a structural change, regenerate the timeline and keep the value after reload', async ({ page, isolatedUsers, dbManager }) => {
      const { user, preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers, { seedGps: true });

      // SQL-seeded GPS points have no rebuild request, so no timeline exists until we regenerate.
      expect(await TimelinePreferencesPage.getStayCount(dbManager, user.id)).toBe(0);

      const current = await preferencesPage.getNumberValue('staypointMinDurationMinutes');
      const target = differentValue(current, [15, 20]);
      await preferencesPage.setNumberValue('staypointMinDurationMinutes', target);

      await expect(preferencesPage.unsavedWarning()).toBeVisible();
      await expect(preferencesPage.saveButton()).toBeEnabled();

      await preferencesPage.clickSave();
      await preferencesPage.confirmDialog('Save Timeline Preferences', 'Save & Regenerate');
      await preferencesPage.waitForRegenerationToFinish();

      const stored = await TimelinePreferencesPage.getStoredPreferences(dbManager, user.id);
      expect(stored?.staypointMinDurationMinutes).toBe(target);
      expect(await TimelinePreferencesPage.getStayCount(dbManager, user.id)).toBeGreaterThan(0);

      await page.reload();
      await preferencesPage.waitForPageLoad();
      expect(await preferencesPage.getNumberValue('staypointMinDurationMinutes')).toBe(target);
      await expect(preferencesPage.unsavedWarning()).toBeHidden();
      await expect(preferencesPage.saveButton()).toBeDisabled();
    });

    test('should not save when the save confirmation is cancelled', async ({ page, isolatedUsers, dbManager }) => {
      const { user, preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers);

      const current = await preferencesPage.getNumberValue('staypointRadiusMeters');
      await preferencesPage.setNumberValue('staypointRadiusMeters', differentValue(current, [120, 150]));

      await preferencesPage.clickSave();
      await preferencesPage.confirmDialog('Save Timeline Preferences', 'Cancel');

      await expect(page.locator(preferencesPage.selectors.regenerationModal)).toBeHidden();
      await expect(preferencesPage.unsavedWarning()).toBeVisible();
      expect(await TimelinePreferencesPage.getStoredPreferences(dbManager, user.id)).toBeNull();
    });

    test('should update trip classification without a timeline regeneration', async ({ page, isolatedUsers, dbManager }) => {
      const { user, preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers, { tab: 'trips' });

      const current = await preferencesPage.getNumberValue('walkingMaxAvgSpeed');
      const target = current - 0.5 >= 3 ? current - 0.5 : current + 0.5;
      await preferencesPage.setNumberValue('walkingMaxAvgSpeed', target);

      await preferencesPage.clickSave();
      await preferencesPage.confirmDialog('Update Trip Classifications', 'Update Classifications');
      await preferencesPage.waitForToast(preferencesPage.selectors.successToast, 'Trip classifications updated successfully.');

      await expect(page.locator(preferencesPage.selectors.regenerationModal)).toBeHidden();
      await expect(preferencesPage.unsavedWarning()).toBeHidden();

      const stored = await TimelinePreferencesPage.getStoredPreferences(dbManager, user.id);
      expect(stored?.walkingMaxAvgSpeed).toBeCloseTo(target, 5);
    });
  });

  test.describe('Unsaved Changes', () => {
    test('should discard unsaved changes', async ({ page, isolatedUsers, dbManager }) => {
      const { preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers);

      const original = await preferencesPage.getNumberValue('staypointMinDurationMinutes');
      await preferencesPage.setNumberValue('staypointMinDurationMinutes', differentValue(original, [25, 30]));
      await expect(preferencesPage.unsavedWarning()).toBeVisible();

      await preferencesPage.clickDiscard();
      await preferencesPage.waitForToast(preferencesPage.selectors.infoToast, 'Changes Discarded');

      expect(await preferencesPage.getNumberValue('staypointMinDurationMinutes')).toBe(original);
      await expect(preferencesPage.unsavedWarning()).toBeHidden();
      await expect(preferencesPage.saveButton()).toBeDisabled();
    });

    test('should ask before leaving the page with unsaved changes', async ({ page, isolatedUsers, dbManager }) => {
      const { user, preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers);

      const original = await preferencesPage.getNumberValue('staypointMinDurationMinutes');
      const edited = differentValue(original, [25, 30]);
      await preferencesPage.setNumberValue('staypointMinDurationMinutes', edited);

      // "Stay" keeps the user on the page with the edit intact.
      await preferencesPage.clickProfileInUserMenu();
      await preferencesPage.confirmDialog('Unsaved Changes', 'Stay');
      await expect(page).toHaveURL(/\/app\/timeline\/preferences/);
      expect(await preferencesPage.getNumberValue('staypointMinDurationMinutes')).toBe(edited);

      // "Leave without saving" navigates away and nothing is stored.
      await preferencesPage.clickProfileInUserMenu();
      await preferencesPage.confirmDialog('Unsaved Changes', 'Leave without saving');
      await expect(page).toHaveURL(/\/app\/profile/);
      expect(await TimelinePreferencesPage.getStoredPreferences(dbManager, user.id)).toBeNull();
    });
  });

  test.describe('Reset to Defaults', () => {
    test('should clear stored overrides and regenerate the timeline', async ({ page, isolatedUsers, dbManager }) => {
      const { user, preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers, {
        seedGps: true,
        storedPreferences: { staypointMinDurationMinutes: 42 }
      });
      expect(await preferencesPage.getNumberValue('staypointMinDurationMinutes')).toBe(42);

      await preferencesPage.openMoreMenuItem('Reset to Defaults');
      await preferencesPage.confirmDialog('Reset to Defaults', 'Reset');
      await preferencesPage.waitForRegenerationToFinish();

      expect(await TimelinePreferencesPage.getStoredPreferences(dbManager, user.id)).toBeNull();
      expect(await TimelinePreferencesPage.getStayCount(dbManager, user.id)).toBeGreaterThan(0);

      const response = await page.request.get('/api/v1/timeline/preferences');
      const defaults = await response.json();
      expect(defaults.staypointMinDurationMinutes).not.toBe(42);
      await expect.poll(() => preferencesPage.getNumberValue('staypointMinDurationMinutes'))
        .toBe(defaults.staypointMinDurationMinutes);
    });
  });

  test.describe('Timeline Jobs', () => {
    test('should show no jobs for a new user', async ({ page, isolatedUsers, dbManager }) => {
      const { preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers);

      await preferencesPage.navigateToJobsList();
      await expect(page.locator(preferencesPage.selectors.noActiveJobCard)).toContainText('No timeline jobs are running now');
      await expect(page.getByText('No recent timeline jobs found.')).toBeVisible();
    });

    test('should list a finished manual regeneration and open its details', async ({ page, isolatedUsers, dbManager }) => {
      const { user, preferencesPage } = await loginAndOpenPreferences(page, dbManager, isolatedUsers, { seedGps: true });

      await preferencesPage.regenerateButton().click();
      await preferencesPage.confirmDialog('Regenerate Complete Timeline', 'Regenerate Timeline');
      await preferencesPage.waitForRegenerationToFinish();
      expect(await TimelinePreferencesPage.getStayCount(dbManager, user.id)).toBeGreaterThan(0);

      await preferencesPage.navigateToJobsList();
      await expect(page.locator(preferencesPage.selectors.noActiveJobCard)).toBeVisible();

      const jobCard = preferencesPage.historyJobCards().first();
      await expect(jobCard).toBeVisible({ timeout: 10000 });
      await expect(jobCard.locator('.status-text')).toHaveText('COMPLETED');

      await jobCard.click();
      await expect(page).toHaveURL(/\/app\/timeline\/jobs\/[0-9a-f-]{36}$/);
      const jobId = page.url().split('/').pop();

      await page.waitForSelector(preferencesPage.selectors.jobDetailsTitle, { timeout: 10000 });
      await expect(page.locator(preferencesPage.selectors.jobStatusTitle)).toContainText('Completed Successfully');
      await expect(page.locator(preferencesPage.selectors.jobIdLabel)).toContainText(jobId);
    });

    test('should not show another user\'s job', async ({ page, isolatedUsers, dbManager }) => {
      const owner = createManagedUser(isolatedUsers);
      const { user: ownerUser } = await TestSetupHelper.createAndLoginUser(page, dbManager, owner);
      const gpsData = GpsDataFactory.generateTestData(ownerUser.id, 'prefs-device');
      await GpsDataFactory.insertGpsData(dbManager, gpsData.allPoints);

      const startResponse = await page.request.post('/api/v1/timeline/jobs');
      expect(startResponse.ok()).toBe(true);
      const { jobId } = await startResponse.json();
      expect(jobId).toBeTruthy();

      await TestSetupHelper.leaveAppAndClearCookies(page);
      const other = createManagedUser(isolatedUsers);
      await TestSetupHelper.createAndLoginUser(page, dbManager, other);

      const response = await page.request.get(`/api/v1/timeline/jobs/${jobId}`);
      expect(response.ok()).toBe(false);
      expect([403, 404]).toContain(response.status());
    });
  });
});
