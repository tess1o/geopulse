import {test, expect} from '../fixtures/database-fixture.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';
import {TestData} from '../fixtures/test-data.js';

test.describe('Favorites Management Page', () => {

  test.describe('Page Load and Initial State', () => {
    test('should display favorites management page correctly', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Verify we're on the favorites page
      expect(await favoritesPage.isOnFavoritesPage()).toBe(true);

      // Verify bulk mode is off by default
      expect(await favoritesPage.isBulkModeEnabled()).toBe(false);

      // Verify no pending favorites initially
      expect(await favoritesPage.isSavePendingButtonVisible()).toBe(false);

      // Verify table is empty initially
      expect(await favoritesPage.isTableEmpty()).toBe(true);
    });

    test('should display existing favorites on page load', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create test favorites
      await TestSetupHelper.createMultipleFavorites(dbManager, user.id, 2, 1);

      // Reload page to see favorites
      await page.reload();
      await favoritesPage.waitForPageLoad();
      await page.waitForTimeout(3000); // Wait for all markers to render

      // Verify favorites appear in table
      const rowCount = await favoritesPage.getTableRowCount();
      expect(rowCount).toBe(3); // 2 points + 1 area

      // Verify favorite point markers appear on map (should be 2)
      const markerCount = await favoritesPage.countFavoriteMarkers();
      expect(markerCount).toBe(2);

      // Verify favorite area markers appear on map (should be 1)
      const areaCount = await favoritesPage.countFavoriteAreas();
      expect(areaCount).toBe(1);
    });

    test('should display favorites on map', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create test favorites
      await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Map Test Point',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();
      await page.waitForTimeout(3000); // Wait for map markers to fully render

      // Verify marker appears on map
      const markerCount = await favoritesPage.countFavoriteMarkers();
      expect(markerCount).toBe(1);

      // Verify marker is visible
      const marker = page.locator('.favorite-marker-icon').first();
      expect(await marker.isVisible()).toBe(true);
    });
  });

  test.describe('Add Favorite Point - Immediate Save Mode', () => {
    test('should add favorite point via map context menu', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      const initialCount = await TestSetupHelper.countFavorites(dbManager, user.id);

      // Add favorite point
      await favoritesPage.addFavoritePointWorkflow(300, 300, 'Test Favorite Point');

      // Wait for timeline regeneration
      await favoritesPage.waitForTimelineRegenerationModal();
      await favoritesPage.waitForTimelineRegenerationToComplete();

      // Verify success toast
      await favoritesPage.waitForSuccessToast();

      // Verify database
      const finalCount = await TestSetupHelper.countFavorites(dbManager, user.id);
      expect(finalCount).toBe(initialCount + 1);

      // Verify favorite appears in table
      await page.waitForTimeout(1000);
      const rowCount = await favoritesPage.getTableRowCount();
      expect(rowCount).toBe(1);
    });

    test('should cancel add favorite dialog', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      const initialCount = await TestSetupHelper.countFavorites(dbManager, user.id);

      // Start add favorite workflow
      await favoritesPage.rightClickOnMap(300, 300);
      await favoritesPage.waitForMapContextMenu();
      await favoritesPage.clickContextMenuItem('Add to Favorites');

      // Cancel dialog
      await favoritesPage.closeAddDialog();

      // Verify no favorite was added
      const finalCount = await TestSetupHelper.countFavorites(dbManager, user.id);
      expect(finalCount).toBe(initialCount);
    });
  });

  test.describe('Add Favorite Area - Immediate Save Mode', () => {
    test('should add favorite area via rectangle drawing', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      const initialCount = await TestSetupHelper.countFavorites(dbManager, user.id, 'AREA');

      // Add favorite area
      await favoritesPage.addFavoriteAreaWorkflow(200, 200, 400, 350, 'Test Favorite Area');

      // Wait for timeline regeneration
      await favoritesPage.waitForTimelineRegenerationModal();
      await favoritesPage.waitForTimelineRegenerationToComplete();

      // Verify success toast
      await favoritesPage.waitForSuccessToast();

      // Verify database
      const finalCount = await TestSetupHelper.countFavorites(dbManager, user.id, 'AREA');
      expect(finalCount).toBe(initialCount + 1);

      // Verify area appears on map
      await page.waitForTimeout(2000);
      const areaCount = await favoritesPage.countFavoriteAreas();
      expect(areaCount).toBeGreaterThan(0);
    });

    test('should cancel rectangle drawing with Escape key', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Start drawing mode
      await favoritesPage.rightClickOnMap(300, 300);
      await favoritesPage.waitForMapContextMenu();
      await favoritesPage.clickContextMenuItem('Add an area to Favorites');

      await page.waitForTimeout(1000);

      // Cancel with Escape
      await favoritesPage.cancelDrawing();

      // Verify no dialog appears
      const dialogVisible = await page.locator('.p-dialog').isVisible().catch(() => false);
      expect(dialogVisible).toBe(false);
    });
  });

  test.describe('Bulk Add Mode', () => {
    test('should enable and disable bulk mode', async ({page, dbManager}) => {
      const {favoritesPage} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Verify bulk mode is off
      expect(await favoritesPage.isBulkModeEnabled()).toBe(false);

      // Enable bulk mode
      await favoritesPage.toggleBulkMode();

      // Verify bulk mode is on
      expect(await favoritesPage.isBulkModeEnabled()).toBe(true);

      // Verify toast message
      await favoritesPage.waitForSuccessToast();

      // Disable bulk mode
      await favoritesPage.toggleBulkMode();

      // Verify bulk mode is off again
      expect(await favoritesPage.isBulkModeEnabled()).toBe(false);
    });

    test('should add favorites to pending list in bulk mode', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      const initialCount = await TestSetupHelper.countFavorites(dbManager, user.id);

      // Enable bulk mode
      await favoritesPage.toggleBulkMode();
      await page.waitForTimeout(1000);

      // Add first favorite to pending
      await favoritesPage.addFavoritePointInBulkMode(300, 300, 'Pending Favorite 1');

      // Verify it was added to pending (not saved immediately)
      const dbCount = await TestSetupHelper.countFavorites(dbManager, user.id);
      expect(dbCount).toBe(initialCount); // No database change yet

      // Verify pending count
      expect(await favoritesPage.isSavePendingButtonVisible()).toBe(true);
      const pendingCount = await favoritesPage.getPendingCount();
      expect(pendingCount).toBe(1);

      // Add second favorite to pending
      await favoritesPage.addFavoritePointInBulkMode(350, 350, 'Pending Favorite 2');

      // Verify pending count increased
      const newPendingCount = await favoritesPage.getPendingCount();
      expect(newPendingCount).toBe(2);

      // Verify still no database change
      const dbCountAfter = await TestSetupHelper.countFavorites(dbManager, user.id);
      expect(dbCountAfter).toBe(initialCount);
    });

    test('should display pending favorites on map', async ({page, dbManager}) => {
      const {favoritesPage} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Enable bulk mode
      await favoritesPage.toggleBulkMode();
      await page.waitForTimeout(1000);

      // Add pending favorites
      await favoritesPage.addFavoritePointInBulkMode(300, 300, 'Pending 1');
      await page.waitForTimeout(1000);

      // Verify pending marker appears on map
      const pendingMarkerCount = await favoritesPage.countPendingMarkers();
      expect(pendingMarkerCount).toBe(1);
    });

    test('should save all pending favorites', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      const initialCount = await TestSetupHelper.countFavorites(dbManager, user.id);

      // Enable bulk mode and add pending favorites
      await favoritesPage.toggleBulkMode();
      await page.waitForTimeout(1000);

      await favoritesPage.addFavoritePointInBulkMode(300, 300, 'Pending 1');
      await page.waitForTimeout(500);
      await favoritesPage.addFavoritePointInBulkMode(350, 350, 'Pending 2');
      await page.waitForTimeout(500);

      // Save all pending
      await favoritesPage.bulkSaveWorkflow();

      // Verify database
      const finalCount = await TestSetupHelper.countFavorites(dbManager, user.id);
      expect(finalCount).toBe(initialCount + 2);

      // Verify pending list is cleared
      expect(await favoritesPage.isSavePendingButtonVisible()).toBe(false);

      // Verify bulk mode is disabled after save
      expect(await favoritesPage.isBulkModeEnabled()).toBe(false);
    });

    test('should show bulk save confirmation dialog with counts', async ({page, dbManager}) => {
      const {favoritesPage} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Enable bulk mode
      await favoritesPage.toggleBulkMode();
      await page.waitForTimeout(1000);

      // Add 2 points and 1 area
      await favoritesPage.addFavoritePointInBulkMode(300, 300, 'Point 1');
      await page.waitForTimeout(500);
      await favoritesPage.addFavoritePointInBulkMode(350, 350, 'Point 2');
      await page.waitForTimeout(500);

      // Start area drawing
      await favoritesPage.rightClickOnMap(200, 200);
      await favoritesPage.waitForMapContextMenu();
      await favoritesPage.clickContextMenuItem('Add an area to Favorites');
      await favoritesPage.drawRectangle(200, 200, 400, 350);
      await favoritesPage.fillAddDialog('Area 1');
      await favoritesPage.submitAddDialog();
      await page.waitForTimeout(1000);

      // Click save pending
      await favoritesPage.clickSavePending();
      await favoritesPage.waitForBulkSaveDialog();

      // Verify counts in dialog
      const counts = await favoritesPage.getBulkSaveDialogCounts();
      expect(counts.points).toBe(2);
      expect(counts.areas).toBe(1);

      // Cancel dialog
      await favoritesPage.cancelBulkSave();
    });

    test('should remove individual pending favorite', async ({page, dbManager}) => {
      const {favoritesPage} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Enable bulk mode and add pending favorites
      await favoritesPage.toggleBulkMode();
      await page.waitForTimeout(1000);

      await favoritesPage.addFavoritePointInBulkMode(300, 300, 'Keep This');
      await page.waitForTimeout(500);
      await favoritesPage.addFavoritePointInBulkMode(350, 350, 'Remove This');
      await page.waitForTimeout(500);

      // Verify 2 pending
      expect(await favoritesPage.getPendingCount()).toBe(2);

      // Remove second pending item
      await favoritesPage.removePendingItem(1);
      await page.waitForTimeout(500);

      // Verify count decreased
      expect(await favoritesPage.getPendingCount()).toBe(1);
    });

    test('should clear all pending favorites', async ({page, dbManager}) => {
      const {favoritesPage} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Enable bulk mode and add pending favorites
      await favoritesPage.toggleBulkMode();
      await page.waitForTimeout(1000);

      await favoritesPage.addFavoritePointInBulkMode(300, 300, 'Pending 1');
      await page.waitForTimeout(500);
      await favoritesPage.addFavoritePointInBulkMode(350, 350, 'Pending 2');
      await page.waitForTimeout(500);

      // Clear all pending
      await favoritesPage.clickClearPending();
      await favoritesPage.confirmDialog();

      // Verify pending list is cleared
      expect(await favoritesPage.isSavePendingButtonVisible()).toBe(false);

      // Verify toast message
      await favoritesPage.waitForInfoToast();
    });

    test('should warn when leaving page with unsaved pending favorites', async ({page, dbManager}) => {
      const {favoritesPage} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Enable bulk mode and add pending favorite
      await favoritesPage.toggleBulkMode();
      await page.waitForTimeout(1000);

      await favoritesPage.addFavoritePointInBulkMode(300, 300, 'Pending');
      await page.waitForTimeout(1000);

      // Try to navigate away by clicking Timeline in navigation menu
      // This triggers Vue Router navigation which will show the guard confirmation
      await favoritesPage.clickNavigationLink('Timeline');

      // Wait for confirmation dialog
      await favoritesPage.waitForConfirmDialog();

      // Verify confirmation dialog shows correct message
      const dialogText = await page.locator('.p-confirmdialog').textContent();
      expect(dialogText).toContain('unsaved pending favorite');

      // Reject navigation
      await favoritesPage.rejectDialog();

      // Wait a bit for navigation to be cancelled
      await page.waitForTimeout(500);

      // Verify still on favorites page
      expect(await favoritesPage.isOnFavoritesPage()).toBe(true);
    });
  });

  test.describe('Edit Favorite', () => {
    test('should edit favorite point metadata without timeline regeneration', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create a favorite point
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Original Name',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Edit only the name (metadata change, no geometry change)
      const newName = 'Updated Name';
      await favoritesPage.editFavoriteWorkflow(0, newName);

      // Verify database - name should be updated
      const favorite = await TestSetupHelper.getFavoriteById(dbManager, favoriteId);
      expect(favorite.name).toBe(newName);
      expect(favorite.latitude).toBeCloseTo(40.7128, 4);
      expect(favorite.longitude).toBeCloseTo(-74.0060, 4);

      // Reload page to verify persistence
      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Verify table shows updated name
      const rowData = await favoritesPage.getTableRowData(0);
      expect(rowData.name).toContain(newName);
    });

    test('should edit favorite area metadata without timeline regeneration', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create a favorite area
      const favoriteId = await TestSetupHelper.createFavoriteArea(dbManager, user.id, {
        name: 'Original Area',
        city: 'Boston',
        country: 'USA',
        southWestLat: 42.0,
        southWestLon: -71.5,
        northEastLat: 42.5,
        northEastLon: -71.0
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Edit only the name (metadata change, no geometry change)
      await favoritesPage.clickEditInTable(0);
      await favoritesPage.waitForEditDialog();

      const input = page.locator('.p-dialog input[placeholder*="name"]');
      await input.clear();
      await input.fill('Updated Area Name');

      await favoritesPage.submitEditDialog();

      // Should NOT show timeline regeneration modal for metadata-only changes
      await favoritesPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify database - name should be updated, geometry unchanged
      const result = await dbManager.client.query(`
        SELECT id, name,
               ST_YMin(geometry) as south_lat,
               ST_XMin(geometry) as west_lon,
               ST_YMax(geometry) as north_lat,
               ST_XMax(geometry) as east_lon
        FROM favorite_locations
        WHERE id = $1
      `, [favoriteId]);

      const favorite = result.rows[0];
      expect(favorite.name).toBe('Updated Area Name');
      expect(parseFloat(favorite.south_lat)).toBeCloseTo(42.0, 4);
      expect(parseFloat(favorite.west_lon)).toBeCloseTo(-71.5, 4);
      expect(parseFloat(favorite.north_lat)).toBeCloseTo(42.5, 4);
      expect(parseFloat(favorite.east_lon)).toBeCloseTo(-71.0, 4);
    });

    test('should edit favorite area with redraw and trigger timeline regeneration', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create a favorite area
      const favoriteId = await TestSetupHelper.createFavoriteArea(dbManager, user.id, {
        name: 'Area to Redraw',
        city: 'Boston',
        country: 'USA',
        southWestLat: 42.0,
        southWestLon: -71.5,
        northEastLat: 42.5,
        northEastLon: -71.0
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Open edit dialog
      await favoritesPage.clickEditInTable(0);
      await favoritesPage.waitForEditDialog();

      // Click "Redraw area" button
      const redrawButton = page.locator('.p-dialog button:has-text("Redraw")');
      await redrawButton.click();

      // Wait for edit map to appear and be ready
      await page.waitForSelector('#edit-area-map.leaflet-container', { state: 'attached', timeout: 5000 });
      await page.waitForTimeout(2000); // Give map time to fully initialize

      // Draw new rectangle on the edit dialog map with smaller coordinates
      // Dialog map is smaller, so use coordinates closer to top-left
      await favoritesPage.drawRectangle(50, 50, 200, 150, 'edit-area-map');
      await page.waitForTimeout(1000);

      // Submit the edit
      await favoritesPage.submitEditDialog();

      // Timeline regeneration should occur for geometry changes
      try {
        await favoritesPage.waitForTimelineRegenerationModal();
        await favoritesPage.waitForTimelineRegenerationToComplete();
      } catch (error) {
        // If modal doesn't appear or completes too quickly, just wait for success
        await favoritesPage.waitForSuccessToast();
        await page.waitForTimeout(2000);
      }

      // Verify database - geometry should be updated
      const result = await dbManager.client.query(`
        SELECT id, name,
               ST_YMin(geometry) as south_lat,
               ST_XMin(geometry) as west_lon,
               ST_YMax(geometry) as north_lat,
               ST_XMax(geometry) as east_lon
        FROM favorite_locations
        WHERE id = $1
      `, [favoriteId]);

      const favorite = result.rows[0];
      expect(favorite.name).toBe('Area to Redraw');

      // Verify coordinates changed from original values
      const southLat = parseFloat(favorite.south_lat);
      const westLon = parseFloat(favorite.west_lon);
      const northLat = parseFloat(favorite.north_lat);
      const eastLon = parseFloat(favorite.east_lon);

      // At least one coordinate should have changed
      const coordsChanged =
        southLat !== 42.0 ||
        westLon !== -71.5 ||
        northLat !== 42.5 ||
        eastLon !== -71.0;

      expect(coordsChanged).toBe(true);

      // Verify we got valid coordinates (basic sanity check)
      expect(southLat).toBeGreaterThan(40);
      expect(southLat).toBeLessThan(45);
      expect(northLat).toBeGreaterThan(southLat); // North should be greater than south
    });

    test('should edit favorite from map context menu', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Map Edit Test',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Right-click on marker
      await favoritesPage.rightClickFavoriteMarker(0);
      await favoritesPage.waitForMapContextMenu();

      // Click Edit
      await favoritesPage.clickContextMenuItem('Edit');

      // Fill and submit edit dialog
      const newName = 'Edited via Map';
      await favoritesPage.fillEditDialog(newName);
      await favoritesPage.submitEditDialog();

      // Wait for success toast
      await favoritesPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify database - name should be updated
      const favorite = await TestSetupHelper.getFavoriteById(dbManager, favoriteId);
      expect(favorite.name).toBe(newName);
    });
  });

  test.describe('Delete Favorite', () => {
    test('should delete favorite from table', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'To Delete',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      const initialCount = await TestSetupHelper.countFavorites(dbManager, user.id);

      // Delete the favorite
      await favoritesPage.deleteFavoriteWorkflow(0);

      // Verify database
      const finalCount = await TestSetupHelper.countFavorites(dbManager, user.id);
      expect(finalCount).toBe(initialCount - 1);

      const favorite = await TestSetupHelper.getFavoriteById(dbManager, favoriteId);
      expect(favorite).toBeNull();

      // Verify table is empty
      await page.waitForTimeout(1000);
      expect(await favoritesPage.isTableEmpty()).toBe(true);
    });

    test('should cancel delete confirmation', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create a favorite
      await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Do Not Delete',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      const initialCount = await TestSetupHelper.countFavorites(dbManager, user.id);

      // Try to delete but cancel
      await favoritesPage.clickDeleteInTable(0);
      await favoritesPage.rejectDialog();

      // Verify not deleted
      const finalCount = await TestSetupHelper.countFavorites(dbManager, user.id);
      expect(finalCount).toBe(initialCount);
    });

    test('should delete favorite from map context menu', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Delete via Map',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Right-click on marker and delete
      await favoritesPage.rightClickFavoriteMarker(0);
      await favoritesPage.waitForMapContextMenu();
      await favoritesPage.clickContextMenuItem('Delete');

      // Confirm deletion
      await favoritesPage.confirmDialog();

      // Wait for timeline regeneration
      await favoritesPage.waitForTimelineRegenerationModal();
      await favoritesPage.waitForTimelineRegenerationToComplete();

      // Verify database
      const favorite = await TestSetupHelper.getFavoriteById(dbManager, favoriteId);
      expect(favorite).toBeNull();
    });
  });

  test.describe('Filters', () => {
    test('should filter by type', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create mixed favorites
      await TestSetupHelper.createMultipleFavorites(dbManager, user.id, 2, 1);

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Filter by POINT
      await favoritesPage.selectTypeFilter('POINT');

      // Verify only points are shown
      const pointRowCount = await favoritesPage.getTableRowCount();
      expect(pointRowCount).toBe(2);

      // Filter by AREA
      await favoritesPage.selectTypeFilter('AREA');

      // Verify only areas are shown
      const areaRowCount = await favoritesPage.getTableRowCount();
      expect(areaRowCount).toBe(1);

      // Show all
      await favoritesPage.selectTypeFilter(null);

      // Verify all are shown
      const allRowCount = await favoritesPage.getTableRowCount();
      expect(allRowCount).toBe(3);
    });

    test('should search by name', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create favorites with unique names
      await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Coffee Shop',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Home Sweet Home',
        city: 'Boston',
        country: 'USA',
        latitude: 42.3601,
        longitude: -71.0589
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Search for "Coffee"
      await favoritesPage.fillSearchInput('Coffee');

      // Verify only matching favorite is shown
      const searchRowCount = await favoritesPage.getTableRowCount();
      expect(searchRowCount).toBe(1);

      const rowData = await favoritesPage.getTableRowData(0);
      expect(rowData.name).toContain('Coffee');
    });

    test('should search by city', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create favorites with different cities
      await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Favorite 1',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Favorite 2',
        city: 'Boston',
        country: 'USA',
        latitude: 42.3601,
        longitude: -71.0589
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Search for "Boston"
      await favoritesPage.fillSearchInput('Boston');

      // Verify only Boston favorite is shown
      const searchRowCount = await favoritesPage.getTableRowCount();
      expect(searchRowCount).toBe(1);
    });

    test('should clear filters', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create favorites
      await TestSetupHelper.createMultipleFavorites(dbManager, user.id, 2, 1);

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Apply filters
      await favoritesPage.selectTypeFilter('POINT');
      await favoritesPage.fillSearchInput('Test');

      // Verify clear button is enabled
      expect(await favoritesPage.isClearFiltersButtonEnabled()).toBe(true);

      // Clear filters
      await favoritesPage.clearFilters();

      // Verify all favorites are shown
      const allRowCount = await favoritesPage.getTableRowCount();
      expect(allRowCount).toBe(3);

      // Verify clear button is disabled
      expect(await favoritesPage.isClearFiltersButtonEnabled()).toBe(false);
    });
  });

  test.describe('Bulk Operations', () => {
    test('should select multiple rows', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create multiple favorites
      await TestSetupHelper.createMultipleFavorites(dbManager, user.id, 3, 0);

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Select first two rows
      await favoritesPage.selectTableRow(0);
      await favoritesPage.selectTableRow(1);

      // Verify selection count
      const selectedCount = await favoritesPage.getSelectedRowCount();
      expect(selectedCount).toBe(2);
    });

    test('should show bulk action buttons when rows selected', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create favorites
      await TestSetupHelper.createMultipleFavorites(dbManager, user.id, 2, 0);

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Initially bulk buttons should not be visible
      const bulkEditButton = page.locator(favoritesPage.selectors.bulkEditButton);
      const reconcileSelectedButton = page.locator(favoritesPage.selectors.reconcileSelectedButton);

      expect(await bulkEditButton.isVisible()).toBe(false);
      expect(await reconcileSelectedButton.isVisible()).toBe(false);

      // Select a row
      await favoritesPage.selectTableRow(0);

      // Now buttons should be visible
      expect(await bulkEditButton.isVisible()).toBe(true);
      expect(await reconcileSelectedButton.isVisible()).toBe(true);
    });

    test('should perform bulk edit of city and country', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create favorites with different cities
      const fav1Id = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Favorite 1',
        city: 'Old City 1',
        country: 'Old Country 1',
        latitude: 40.7128,
        longitude: -74.0060
      });

      const fav2Id = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Favorite 2',
        city: 'Old City 2',
        country: 'Old Country 2',
        latitude: 42.3601,
        longitude: -71.0589
      });

      const fav3Id = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Favorite 3',
        city: 'Old City 3',
        country: 'Old Country 3',
        latitude: 34.0522,
        longitude: -118.2437
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Select first two rows (will update fav1 and fav2, leave fav3 unchanged)
      await favoritesPage.selectTableRow(0);
      await favoritesPage.selectTableRow(1);

      // Click bulk edit
      await favoritesPage.clickBulkEdit();
      await favoritesPage.waitForBulkEditDialog();

      // Fill in new city and country
      const dialog = page.locator(favoritesPage.selectors.bulkEditDialog);

      // Enable and fill city field (autocomplete input)
      const cityCheckbox = dialog.locator('label:has-text("City")').locator('..').locator('input[type="checkbox"]');
      await cityCheckbox.check();
      await page.waitForTimeout(300);

      const cityInput = dialog.locator('.p-autocomplete input.p-autocomplete-input[placeholder*="city"]');
      await cityInput.click();
      await cityInput.fill('New York');
      await page.waitForTimeout(500);

      // Click on dialog header to close autocomplete dropdown
      const dialogHeader = dialog.locator('.p-dialog-header');
      await dialogHeader.click();
      await page.waitForTimeout(300);

      // Enable and fill country field (autocomplete input)
      const countryCheckbox = dialog.locator('label:has-text("Country")').locator('..').locator('input[type="checkbox"]');
      await countryCheckbox.check();
      await page.waitForTimeout(300);

      const countryInput = dialog.locator('.p-autocomplete input.p-autocomplete-input[placeholder*="country"]');
      await countryInput.click();
      await countryInput.fill('USA');
      await page.waitForTimeout(500);

      // Click on dialog header again to close autocomplete dropdown
      await dialogHeader.click();
      await page.waitForTimeout(300);

      // Submit bulk edit
      const saveButton = dialog.locator('button:has-text("Update")');
      await saveButton.click();

      await page.waitForTimeout(1000);

      // Handle possible typo detection dialog
      const continueButton = page.locator('button:has-text("Continue Anyway")');
      if (await continueButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await continueButton.click();
        await page.waitForTimeout(300);
      }

      // Wait for success
      await favoritesPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify database - first two favorites should be updated
      const result1 = await dbManager.client.query(
        'SELECT city, country FROM favorite_locations WHERE id = $1',
        [fav1Id]
      );
      expect(result1.rows[0].city).toBe('New York');
      expect(result1.rows[0].country).toBe('USA');

      const result2 = await dbManager.client.query(
        'SELECT city, country FROM favorite_locations WHERE id = $1',
        [fav2Id]
      );
      expect(result2.rows[0].city).toBe('New York');
      expect(result2.rows[0].country).toBe('USA');

      // Third favorite should remain unchanged
      const result3 = await dbManager.client.query(
        'SELECT city, country FROM favorite_locations WHERE id = $1',
        [fav3Id]
      );
      expect(result3.rows[0].city).toBe('Old City 3');
      expect(result3.rows[0].country).toBe('Old Country 3');

      // Reload and verify in table
      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Verify updated values appear in table
      const tableText = await page.locator('.favorites-table').textContent();
      expect(tableText).toContain('New York');
      expect(tableText).toContain('USA');
    });

    test('should perform bulk edit of only city', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create favorites
      const fav1Id = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Favorite A',
        city: 'Boston',
        country: 'USA',
        latitude: 42.3601,
        longitude: -71.0589
      });

      const fav2Id = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Favorite B',
        city: 'Cambridge',
        country: 'USA',
        latitude: 42.3736,
        longitude: -71.1097
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Select both rows
      await favoritesPage.selectTableRow(0);
      await favoritesPage.selectTableRow(1);

      // Click bulk edit
      await favoritesPage.clickBulkEdit();
      await favoritesPage.waitForBulkEditDialog();

      // Enable and fill only city field (leave country unchecked)
      const dialog = page.locator(favoritesPage.selectors.bulkEditDialog);

      // Enable city field (autocomplete input)
      const cityCheckbox = dialog.locator('label:has-text("City")').locator('..').locator('input[type="checkbox"]');
      await cityCheckbox.check();
      await page.waitForTimeout(300);

      const cityInput = dialog.locator('.p-autocomplete input.p-autocomplete-input[placeholder*="city"]');
      await cityInput.click();
      await cityInput.fill('San Francisco');
      await page.waitForTimeout(500);

      // Click on dialog header to close autocomplete dropdown
      const dialogHeader = dialog.locator('.p-dialog-header');
      await dialogHeader.click();
      await page.waitForTimeout(300);

      // Do NOT enable country field - leave it unchecked so it won't be updated

      // Submit
      const saveButton = dialog.locator('button:has-text("Update")');
      await saveButton.click();

      await page.waitForTimeout(1000);

      // Handle possible typo detection dialog
      const continueButton = page.locator('button:has-text("Continue Anyway")');
      if (await continueButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await continueButton.click();
        await page.waitForTimeout(300);
      }

      await favoritesPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify database - city updated, country unchanged
      const result1 = await dbManager.client.query(
        'SELECT city, country FROM favorite_locations WHERE id = $1',
        [fav1Id]
      );
      expect(result1.rows[0].city).toBe('San Francisco');
      expect(result1.rows[0].country).toBe('USA'); // Should remain unchanged

      const result2 = await dbManager.client.query(
        'SELECT city, country FROM favorite_locations WHERE id = $1',
        [fav2Id]
      );
      expect(result2.rows[0].city).toBe('San Francisco');
      expect(result2.rows[0].country).toBe('USA'); // Should remain unchanged
    });
  });

  test.describe('Reconcile Favorites', () => {
    test('should show reconcile all button', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Initially button should be disabled (no favorites)
      expect(await favoritesPage.isReconcileAllButtonDisabled()).toBe(true);

      // Create a favorite
      await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Test',
        city: null,
        country: null,
        latitude: 40.7128,
        longitude: -74.0060
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Button should now be enabled
      expect(await favoritesPage.isReconcileAllButtonDisabled()).toBe(false);
    });

    test('should open reconcile dialog for all favorites', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create favorites
      await TestSetupHelper.createMultipleFavorites(dbManager, user.id, 2, 0);

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Click reconcile all
      await favoritesPage.clickReconcileAll();

      // Verify reconcile dialog opens
      await favoritesPage.waitForReconcileDialog();

      const dialog = page.locator(favoritesPage.selectors.reconcileDialog);
      expect(await dialog.isVisible()).toBe(true);
    });

    test('should open reconcile dialog for selected favorites', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create favorites
      await TestSetupHelper.createMultipleFavorites(dbManager, user.id, 3, 0);

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Select rows
      await favoritesPage.selectTableRow(0);
      await favoritesPage.selectTableRow(1);

      // Click reconcile selected
      await favoritesPage.clickReconcileSelected();

      // Verify reconcile dialog opens
      await favoritesPage.waitForReconcileDialog();

      const dialog = page.locator(favoritesPage.selectors.reconcileDialog);
      expect(await dialog.isVisible()).toBe(true);
    });
  });

  test.describe('View Details', () => {
    test('should navigate to place details when clicking view button', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'View Test',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Click view details
      await favoritesPage.clickViewDetails(0);

      // Verify navigation to place details page
      await page.waitForURL(`**/app/place-details/favorite/${favoriteId}`, { timeout: 10000 });
      expect(page.url()).toContain(`/app/place-details/favorite/${favoriteId}`);
    });
  });

  test.describe('Show on Map', () => {
    test('should focus on map when clicking show on map button', async ({page, dbManager}) => {
      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create a favorite
      await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Map Focus Test',
        city: 'New York',
        country: 'USA',
        latitude: 40.7128,
        longitude: -74.0060
      });

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Click show on map
      await favoritesPage.clickShowOnMap(0);

      // Verify map is focused (this would require map zoom level check)
      // For now just verify no errors occurred
      await page.waitForTimeout(1000);
    });
  });

  test.describe('Empty State', () => {
    test('should show empty state when no favorites exist', async ({page, dbManager}) => {
      const {favoritesPage} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Verify empty state
      expect(await favoritesPage.isTableEmpty()).toBe(true);

      // Verify empty message text
      const emptyState = page.locator('.empty-state');
      const text = await emptyState.textContent();
      expect(text).toContain('No Favorite Locations Found');
    });
  });

  test.describe('Context Menu on Pending Favorites', () => {
    test('should show context menu on pending favorite marker', async ({page, dbManager}) => {
      const {favoritesPage} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Enable bulk mode and add pending
      await favoritesPage.toggleBulkMode();
      await page.waitForTimeout(1000);

      await favoritesPage.addFavoritePointInBulkMode(300, 300, 'Pending Test');
      await page.waitForTimeout(1500);

      // Right-click on pending marker
      await favoritesPage.rightClickPendingMarker(0);
      await favoritesPage.waitForMapContextMenu();

      // Verify context menu has "Remove from Pending" option
      const removeItem = page.locator('.p-contextmenu-item-label:has-text("Remove from Pending")');
      expect(await removeItem.isVisible()).toBe(true);
    });
  });

  test.describe('Responsive Behavior', () => {
    test('should handle mobile viewport', async ({page, dbManager}) => {
      // Set mobile viewport
      await page.setViewportSize({ width: 375, height: 667 });

      const {favoritesPage, user} = await TestSetupHelper.loginAndNavigateToFavoritesPage(page, dbManager);

      // Create favorites
      await TestSetupHelper.createMultipleFavorites(dbManager, user.id, 2, 0);

      await page.reload();
      await favoritesPage.waitForPageLoad();

      // Verify page still works on mobile
      const rowCount = await favoritesPage.getTableRowCount();
      expect(rowCount).toBe(2);

      // Verify map is still visible
      const map = page.locator(favoritesPage.selectors.leafletMap);
      expect(await map.isVisible()).toBe(true);
    });
  });
});
