import { test, expect } from '../fixtures/isolated-fixture.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';
import {buildManagedUser} from '../utils/isolated-user-helper.js';

test.describe('Geocoding Management Page - Visibility and Copy-on-Write', () => {
  const hashString = (value) => {
    let hash = 0;
    for (let i = 0; i < value.length; i += 1) {
      hash = ((hash << 5) - hash) + value.charCodeAt(i);
      hash |= 0;
    }
    return Math.abs(hash);
  };

  const createUniqueCoordsFactory = (testIdentity) => {
    let index = 0;
    return () => {
      const seed = hashString(`${testIdentity.uniqueToken}-${index++}`);
      const lon = -160 + ((seed % 3000000) / 100000);
      const lat = -70 + ((Math.floor(seed / 97) % 1400000) / 100000);
      return `POINT(${lon.toFixed(6)} ${lat.toFixed(6)})`;
    };
  };

  test.describe('Geocoding Results Visibility', () => {
    test('should show geocoding results that belong to the current user', async ({ page, isolatedUsers, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));

      // Create geocoding results owned by the user (user_id = current user)
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'User Owned Location',
        city: 'New York',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify the user's own geocoding result is shown
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(1);

      const rowData = await geocodingPage.getTableRowData(0);
      expect(rowData.name).toContain('User Owned Location');
    });

    test('should show original geocoding results used in user timeline stays', async ({ page, isolatedUsers, testIdentity, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));
      const nextCoords = createUniqueCoordsFactory(testIdentity);
      const originalCoords = nextCoords();

      // Create an original geocoding result (user_id = null)
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, null, {
        coords: originalCoords,
        displayName: 'Original Location',
        city: 'New York',
        country: 'USA'
      });

      // Create a timeline stay that uses this geocoding result
      await TestSetupHelper.createTimelineStay(dbManager, user.id, {
        geocodingId,
        coords: originalCoords,
        locationName: 'Original Location',
        timestampOffset: '2 hours'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify the original geocoding result is shown (because it's used in timeline_stay)
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(1);

      const rowData = await geocodingPage.getTableRowData(0);
      expect(rowData.name).toContain('Original Location');
    });

    test('should NOT show original geocoding results NOT used in user timeline stays', async ({ page, isolatedUsers, testIdentity, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));
      const nextCoords = createUniqueCoordsFactory(testIdentity);
      const originalCoords = nextCoords();

      // Create an original geocoding result (user_id = null) that is NOT used in any timeline_stay
      await TestSetupHelper.createGeocodingResult(dbManager, null, {
        coords: originalCoords,
        displayName: 'Unused Original Location',
        city: 'New York',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify the table is empty (original result not used in timeline_stays is not shown)
      expect(await geocodingPage.isTableEmpty()).toBe(true);
    });

    test('should NOT show geocoding results that belong to other users', async ({ page, isolatedUsers, dbManager}) => {
      const {geocodingPage} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));

      // Create another user
      const otherUser = await isolatedUsers.create(page);
      const otherUserRecord = await dbManager.getUserByEmail(otherUser.email);
      const otherUserId = otherUserRecord.id;

      // Create a geocoding result owned by the other user
      await TestSetupHelper.createGeocodingResult(dbManager, otherUserId, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Other User Location',
        city: 'New York',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify the other user's geocoding result is NOT shown
      expect(await geocodingPage.isTableEmpty()).toBe(true);
    });

    test('should show both user-owned and original results used in timeline', async ({ page, isolatedUsers, testIdentity, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));
      const nextCoords = createUniqueCoordsFactory(testIdentity);
      const userOwnedCoords = nextCoords();
      const originalCoords = nextCoords();

      // Create a user-owned geocoding result
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: userOwnedCoords,
        displayName: 'User Owned',
        city: 'New York',
        country: 'USA'
      });

      // Create an original geocoding result used in timeline
      const originalGeocodingId = await TestSetupHelper.createGeocodingResult(dbManager, null, {
        coords: originalCoords,
        displayName: 'Original in Timeline',
        city: 'Boston',
        country: 'USA'
      });

      await TestSetupHelper.createTimelineStay(dbManager, user.id, {
        geocodingId: originalGeocodingId,
        coords: originalCoords,
        locationName: 'Original in Timeline'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify both results are shown
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(2);
    });
  });

  test.describe('Copy-on-Write Behavior', () => {
    test('should create copy when user edits original geocoding result used in timeline', async ({ page, isolatedUsers, testIdentity, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));
      const nextCoords = createUniqueCoordsFactory(testIdentity);
      const originalCoords = nextCoords();

      // Create an original geocoding result (user_id = null)
      const originalGeocodingId = await TestSetupHelper.createGeocodingResult(dbManager, null, {
        coords: originalCoords,
        displayName: 'Original Location',
        city: 'Old City',
        country: 'Old Country'
      });

      // Create timeline stay that uses this original geocoding result
      const timelineStayId = await TestSetupHelper.createTimelineStay(dbManager, user.id, {
        geocodingId: originalGeocodingId,
        coords: originalCoords,
        locationName: 'Original Location'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Edit the geocoding result
      await geocodingPage.clickEditInTable(0);
      await geocodingPage.fillEditDialog('Updated Location', 'New City', 'New Country');
      await geocodingPage.submitEditDialog();
      await geocodingPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify: Original geocoding result should remain unchanged
      const originalResult = await TestSetupHelper.getGeocodingResultById(dbManager, originalGeocodingId);
      expect(originalResult).not.toBeNull();
      expect(originalResult.display_name).toBe('Original Location');
      expect(originalResult.city).toBe('Old City');
      expect(originalResult.country).toBe('Old Country');
      expect(originalResult.user_id).toBeNull(); // Still original

      // Verify: A new copy was created for the user
      const userCopyResult = await dbManager.client.query(`
        SELECT id, display_name, city, country, user_id
        FROM reverse_geocoding_location
        WHERE user_id = $1 AND display_name = $2
      `, [user.id, 'Updated Location']);

      expect(userCopyResult.rows.length).toBe(1);
      const userCopy = userCopyResult.rows[0];
      expect(userCopy.display_name).toBe('Updated Location');
      expect(userCopy.city).toBe('New City');
      expect(userCopy.country).toBe('New Country');
      expect(userCopy.user_id).toBe(user.id);

      // Verify: Timeline stay now references the new copy (not the original)
      const updatedTimelineStay = await TestSetupHelper.getTimelineStayById(dbManager, timelineStayId);
      expect(updatedTimelineStay.geocoding_id).toBe(userCopy.id);
      expect(updatedTimelineStay.geocoding_id).not.toBe(originalGeocodingId);
    });

    test('should update in-place when user edits their own geocoding result', async ({ page, isolatedUsers, testIdentity, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));
      const nextCoords = createUniqueCoordsFactory(testIdentity);
      const userOwnedCoords = nextCoords();

      // Create a geocoding result owned by the user
      const userGeocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: userOwnedCoords,
        displayName: 'User Location',
        city: 'Old City',
        country: 'Old Country'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Count this user's geocoding results before edit
      const initialCount = await TestSetupHelper.countGeocodingResults(dbManager, user.id);

      // Edit the user's own geocoding result
      await geocodingPage.clickEditInTable(0);
      await geocodingPage.fillEditDialog('Updated User Location', 'New City', 'New Country');
      await geocodingPage.submitEditDialog();
      await geocodingPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify: No new copy was created for this user (count remains the same)
      const finalCount = await TestSetupHelper.countGeocodingResults(dbManager, user.id);
      expect(finalCount).toBe(initialCount);

      // Verify: The original record was updated in-place
      const updatedResult = await TestSetupHelper.getGeocodingResultById(dbManager, userGeocodingId);
      expect(updatedResult.display_name).toBe('Updated User Location');
      expect(updatedResult.city).toBe('New City');
      expect(updatedResult.country).toBe('New Country');
      expect(updatedResult.user_id).toBe(user.id); // Still owned by same user
    });

    test('should keep original unchanged when multiple users have timeline stays', async ({ page, isolatedUsers, testIdentity, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));
      const nextCoords = createUniqueCoordsFactory(testIdentity);
      const sharedCoords = nextCoords();

      // Create another user
      const otherUser = await isolatedUsers.create(page);
      const otherUserRecord = await dbManager.getUserByEmail(otherUser.email);
      const otherUserId = otherUserRecord.id;

      // Create an original geocoding result (user_id = null)
      const originalGeocodingId = await TestSetupHelper.createGeocodingResult(dbManager, null, {
        coords: sharedCoords,
        displayName: 'Shared Original',
        city: 'Original City',
        country: 'Original Country'
      });

      // Both users have timeline stays using this original geocoding result
      const otherUserStayId = await TestSetupHelper.createTimelineStay(dbManager, otherUserId, {
        geocodingId: originalGeocodingId,
        coords: sharedCoords,
        locationName: 'Shared Original',
        timestampOffset: '4 hours'
      });

      await TestSetupHelper.createTimelineStay(dbManager, user.id, {
        geocodingId: originalGeocodingId,
        coords: sharedCoords,
        locationName: 'Shared Original',
        timestampOffset: '2 hours'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Current user edits the original geocoding result
      await geocodingPage.clickEditInTable(0);
      await geocodingPage.fillEditDialog('Current User Custom', 'Custom City', 'Custom Country');
      await geocodingPage.submitEditDialog();
      await geocodingPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify: Original geocoding result remains unchanged
      const originalResult = await TestSetupHelper.getGeocodingResultById(dbManager, originalGeocodingId);
      expect(originalResult.display_name).toBe('Shared Original');
      expect(originalResult.city).toBe('Original City');
      expect(originalResult.country).toBe('Original Country');

      // Verify: Other user's timeline stay still references the original
      const otherUserStayAfter = await TestSetupHelper.getTimelineStayById(dbManager, otherUserStayId);
      expect(otherUserStayAfter.geocoding_id).toBe(originalGeocodingId);

      // Verify: Current user's timeline stay now references their custom copy
      const currentUserStays = await TestSetupHelper.getTimelineStaysByUser(dbManager, user.id);
      expect(currentUserStays[0].geocoding_id).not.toBe(originalGeocodingId);

      // Verify the custom copy exists and has correct data
      const customCopy = await TestSetupHelper.getGeocodingResultById(dbManager, currentUserStays[0].geocoding_id);
      expect(customCopy.display_name).toBe('Current User Custom');
      expect(customCopy.city).toBe('Custom City');
      expect(customCopy.country).toBe('Custom Country');
      expect(customCopy.user_id).toBe(user.id);
    });

    test('should handle multiple timeline stays for same user correctly', async ({ page, isolatedUsers, testIdentity, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));
      const nextCoords = createUniqueCoordsFactory(testIdentity);
      const originalCoords = nextCoords();

      // Create an original geocoding result
      const originalGeocodingId = await TestSetupHelper.createGeocodingResult(dbManager, null, {
        coords: originalCoords,
        displayName: 'Original Location',
        city: 'Original City',
        country: 'Original Country'
      });

      // Create multiple timeline stays for the same user using the same geocoding result
      await TestSetupHelper.createMultipleTimelineStays(dbManager, user.id, [
        {geocodingId: originalGeocodingId, coords: originalCoords, locationName: 'Original Location', timestampOffset: '6 hours'},
        {geocodingId: originalGeocodingId, coords: originalCoords, locationName: 'Original Location', timestampOffset: '4 hours'},
        {geocodingId: originalGeocodingId, coords: originalCoords, locationName: 'Original Location', timestampOffset: '2 hours'}
      ]);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Edit the geocoding result
      await geocodingPage.clickEditInTable(0);
      await geocodingPage.fillEditDialog('Updated Location', 'New City', 'New Country');
      await geocodingPage.submitEditDialog();
      await geocodingPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify: All timeline stays for this user now reference the new copy
      const distinctGeocodingIds = await TestSetupHelper.getDistinctGeocodingIdsForUser(dbManager, user.id);

      // Should have only 1 distinct geocoding_id (the new copy)
      expect(distinctGeocodingIds.length).toBe(1);
      expect(distinctGeocodingIds[0]).not.toBe(originalGeocodingId);

      // Verify the new copy has the updated data
      const newCopy = await TestSetupHelper.getGeocodingResultById(dbManager, distinctGeocodingIds[0]);
      expect(newCopy.display_name).toBe('Updated Location');
      expect(newCopy.user_id).toBe(user.id);

      // Verify original is unchanged
      const original = await TestSetupHelper.getGeocodingResultById(dbManager, originalGeocodingId);
      expect(original.display_name).toBe('Original Location');
      expect(original.user_id).toBeNull();
    });
  });

  test.describe('Bulk Edit Copy-on-Write', () => {
    test('should create copies for original geocoding results in bulk edit', async ({ page, isolatedUsers, testIdentity, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager, buildManagedUser(isolatedUsers));
      const nextCoords = createUniqueCoordsFactory(testIdentity);
      const originalCoords1 = nextCoords();
      const originalCoords2 = nextCoords();

      // Create two original geocoding results
      const original1Id = await TestSetupHelper.createGeocodingResult(dbManager, null, {
        coords: originalCoords1,
        displayName: 'Original 1',
        city: 'Old City 1',
        country: 'Old Country 1'
      });

      const original2Id = await TestSetupHelper.createGeocodingResult(dbManager, null, {
        coords: originalCoords2,
        displayName: 'Original 2',
        city: 'Old City 2',
        country: 'Old Country 2'
      });

      // Create timeline stays for both
      await TestSetupHelper.createMultipleTimelineStays(dbManager, user.id, [
        {geocodingId: original1Id, coords: originalCoords1, locationName: 'Original 1', timestampOffset: '2 hours'},
        {geocodingId: original2Id, coords: originalCoords2, locationName: 'Original 2', timestampOffset: '4 hours'}
      ]);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Select both rows
      await geocodingPage.selectTableRow(0);
      await geocodingPage.selectTableRow(1);

      // Perform bulk edit
      await geocodingPage.clickBulkEdit();
      await geocodingPage.waitForBulkEditDialog();

      const dialog = page.locator('.p-dialog:has(.p-dialog-title:text("Bulk Edit"))');

      // Enable and fill city field
      const cityCheckbox = dialog.locator('label:has-text("City")').locator('..').locator('input[type="checkbox"]');
      await cityCheckbox.check();
      await page.waitForTimeout(300);

      const cityInput = dialog.locator('.p-autocomplete input.p-autocomplete-input[placeholder*="city"]');
      await cityInput.click();
      await cityInput.fill('Bulk Updated City');
      await page.waitForTimeout(500);

      const dialogHeader = dialog.locator('.p-dialog-header');
      await dialogHeader.click();
      await page.waitForTimeout(300);

      const updateButton = dialog.locator('button:has-text("Update")');
      await updateButton.click();

      await page.waitForTimeout(1000);

      // Handle possible typo detection dialog
      const continueButton = page.locator('button:has-text("Continue Anyway")');
      if (await continueButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await continueButton.click();
        await page.waitForTimeout(300);
      }

      await geocodingPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify: Both originals remain unchanged
      const original1 = await TestSetupHelper.getGeocodingResultById(dbManager, original1Id);
      expect(original1.city).toBe('Old City 1');
      expect(original1.user_id).toBeNull();

      const original2 = await TestSetupHelper.getGeocodingResultById(dbManager, original2Id);
      expect(original2.city).toBe('Old City 2');
      expect(original2.user_id).toBeNull();

      // Verify: Copies were created for the user
      const userCopies = await dbManager.client.query(`
        SELECT id, display_name, city, user_id
        FROM reverse_geocoding_location
        WHERE user_id = $1 AND city = $2
      `, [user.id, 'Bulk Updated City']);

      expect(userCopies.rows.length).toBe(2);

      // Verify: Timeline stays now reference the new copies
      const geocodingIds = await TestSetupHelper.getDistinctGeocodingIdsForUser(dbManager, user.id);
      expect(geocodingIds).not.toContain(original1Id);
      expect(geocodingIds).not.toContain(original2Id);
    });
  });
});
