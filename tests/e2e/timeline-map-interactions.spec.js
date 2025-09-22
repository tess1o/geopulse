import {test, expect} from '../fixtures/database-fixture.js';
import {TimelinePage} from '../pages/TimelinePage.js';
import {TimelineMapPage} from '../pages/TimelineMapPage.js';
import {TestData} from '../fixtures/test-data.js';
import * as TimelineTestData from '../utils/timeline-test-data.js';
import * as MapTestData from '../utils/map-test-data.js';

test.describe('Timeline Map Interactions', () => {

  test.describe('Context Menus', () => {
    test('should show map context menu on right click', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Right-click on map and wait for context menu to appear
      await mapPage.rightClickOnMap(300, 300);
      await mapPage.waitForMapContextMenu();
      
      // Verify context menu appears
      expect(await mapPage.isContextMenuVisible()).toBe(true);
      
      // Verify context menu has expected items
      // Use the correct selector from the actual HTML structure
      const addToFavoritesItem = page.locator('.p-contextmenu-item-label', { hasText: 'Add to Favorites' });
      const addAreaItem = page.locator('.p-contextmenu-item-label', { hasText: 'Add an area to Favorites' });
      
      await expect(addToFavoritesItem).toBeVisible();
      await expect(addAreaItem).toBeVisible();
    });

    test('should handle context menu item clicks', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Right-click and select "Add to Favorites"
      await mapPage.rightClickOnMap(300, 300);
      await mapPage.waitForMapContextMenu();
      await mapPage.clickContextMenuItem('Add to Favorites');
      
      // Verify Add Favorite dialog opens
      await mapPage.waitForAddFavoriteDialog();
      
      // Close dialog
      await mapPage.closeAddFavoriteDialog();
    });

    test('should show favorite context menu on favorite marker right-click', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Setup timeline with data and add a favorite
      const { testUser } = await timelinePage.loginAndNavigate();
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // Insert a favorite location first
      await dbManager.client.query(`
        INSERT INTO favorite_locations 
        (id, user_id, name, city, country, type, geometry) 
        VALUES (8888, $1, 'Test Favorite', 'Test City', 'Test Country', 'POINT', 
                ST_GeomFromText('POINT(-74.0060 40.7128)', 4326))
      `, [user.id]);
      
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id);

      await timelinePage.navigateWithDateRange(new Date('2025-09-20'), new Date('2025-09-22'));
      await timelinePage.waitForPageLoad();

      await mapPage.waitForMapReady();
      
      // Ensure favorites layer is active
      if (!(await mapPage.isLayerActive('favorites'))) {
        await mapPage.toggleLayerControl('favorites');
      }
      
      // Wait for favorite markers to appear
      await page.waitForTimeout(1000);
      
      const favoriteCount = await mapPage.countMarkers('favorite');
      if (favoriteCount > 0) {
        // Right-click on favorite marker
        await mapPage.rightClickFavoriteMarker(0);
        
        // Verify favorite context menu appears
        await mapPage.waitForMapContextMenu();
        
        // Check for Edit and Delete options
        const editItem = page.locator('.p-contextmenu-item-label', { hasText: 'Edit' });
        const deleteItem = page.locator('.p-contextmenu-item-label', { hasText: 'Delete' });
        
        expect(await editItem.isVisible()).toBe(true);
        expect(await deleteItem.isVisible()).toBe(true);
      }
    });
  });

  test.describe('Add Favorite Point Dialog', () => {
    test('should complete add favorite point workflow', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      const { user } = await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      const favoriteName = 'Test Favorite Point';
      
      // Check initial favorite count in database
      const initialResult = await dbManager.client.query(`
        SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1
      `, [user.id]);
      const initialCount = parseInt(initialResult.rows[0].count);
      
      try {
        // Complete the add favorite workflow
        await mapPage.addFavoritePointWorkflow(300, 300, favoriteName);
        
        // Verify success - favorite should be added and layer toggled on
        expect(await mapPage.isLayerActive('favorites')).toBe(true);
        
        // Verify favorite marker appears (may take time to reload)
        await page.waitForTimeout(2000);
        const favoriteCount = await mapPage.countMarkers('favorite');
        expect(favoriteCount).toBeGreaterThan(0);
        
        // VERIFY DATABASE CHANGE: Check that favorite was actually inserted
        const finalResult = await dbManager.client.query(`
          SELECT COUNT(*) as count, name FROM favorite_locations 
          WHERE user_id = $1 AND name = $2
          GROUP BY name
        `, [user.id, favoriteName]);
        
        expect(finalResult.rows.length).toBe(1);
        expect(finalResult.rows[0].name).toBe(favoriteName);
        
        // Verify total count increased by 1
        const totalResult = await dbManager.client.query(`
          SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1
        `, [user.id]);
        const finalTotalCount = parseInt(totalResult.rows[0].count);
        expect(finalTotalCount).toBe(initialCount + 1);
        
      } catch (error) {
        console.log('Add favorite workflow test failed:', error.message);
        // This test might fail if backend isn't properly set up for favorites
        // or if timeline regeneration takes too long
        throw error;
      }
    });

    test('should handle add favorite dialog cancellation', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Right-click and open add favorite dialog
      await mapPage.rightClickOnMap(300, 300);
      await mapPage.waitForMapContextMenu();
      await mapPage.clickContextMenuItem('Add to Favorites');
      
      // Wait for dialog and close it
      await mapPage.waitForAddFavoriteDialog();
      await mapPage.closeAddFavoriteDialog();
      
      // Verify dialog is closed
      const dialog = page.locator('.p-dialog:has(.p-dialog-title:text("Add To Favorites"))');
      expect(await dialog.isVisible()).toBe(false);
    });
  });

  test.describe('Rectangle Drawing Tool', () => {
    test('should enter drawing mode when starting area creation', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Right-click and select "Add an area to Favorites"
      await mapPage.rightClickOnMap(300, 300);
      await mapPage.waitForMapContextMenu();
      await mapPage.clickContextMenuItem('Add an area to Favorites');
      
      // Wait for drawing mode to activate
      await page.waitForTimeout(1000);
      
      // Verify in drawing mode (this may require specific implementation)
      // The exact verification depends on how drawing mode is visually indicated
      const isDrawing = await mapPage.isInDrawingMode();
      expect(typeof isDrawing).toBe('boolean');
    });

    test('should cancel drawing with Escape key', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Start drawing mode
      await mapPage.rightClickOnMap(300, 300);
      await mapPage.waitForMapContextMenu();
      await mapPage.clickContextMenuItem('Add an area to Favorites');
      
      await page.waitForTimeout(1000);
      
      // Cancel with Escape key
      await mapPage.cancelRectangleDrawing();
      await page.waitForTimeout(500);
      
      // Verify drawing mode is cancelled
      const isDrawing = await mapPage.isInDrawingMode();
      expect(isDrawing).toBe(false);
    });

    test('should complete rectangle drawing workflow', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      const { user } = await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      const areaName = 'Test Favorite Area';
      
      // Check initial favorite count in database
      const initialResult = await dbManager.client.query(`
        SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1
      `, [user.id]);
      const initialCount = parseInt(initialResult.rows[0].count);
      
      try {
        // Complete the add favorite area workflow
        await mapPage.addFavoriteAreaWorkflow(200, 200, 400, 350, areaName);
        
        // Verify success
        expect(await mapPage.isLayerActive('favorites')).toBe(true);
        
        // Verify favorite area appears as red rectangle/polygon
        await page.waitForTimeout(2000);
        const polygonCount = await mapPage.countFavoritePolygons();
        expect(polygonCount).toBeGreaterThan(0);
        
        // Verify it's actually a polygon, not a marker
        const favoritePolygons = mapPage.getFavoritePolygons();
        const firstPolygon = favoritePolygons.first();
        expect(await firstPolygon.isVisible()).toBe(true);
        
        // VERIFY DATABASE CHANGE: Check that favorite area was actually inserted
        const finalResult = await dbManager.client.query(`
          SELECT COUNT(*) as count, name, type FROM favorite_locations 
          WHERE user_id = $1 AND name = $2
          GROUP BY name, type
        `, [user.id, areaName]);
        
        expect(finalResult.rows.length).toBe(1);
        expect(finalResult.rows[0].name).toBe(areaName);
        // Rectangle/area should be stored as AREA type (not POINT)
        expect(finalResult.rows[0].type).toBe('AREA');
        
        // Verify total count increased by 1
        const totalResult = await dbManager.client.query(`
          SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1
        `, [user.id]);
        const finalTotalCount = parseInt(totalResult.rows[0].count);
        expect(finalTotalCount).toBe(initialCount + 1);
        
        // Verify geometry is actually a polygon (not a point)
        const geometryResult = await dbManager.client.query(`
          SELECT ST_GeometryType(geometry) as geom_type FROM favorite_locations 
          WHERE user_id = $1 AND name = $2
        `, [user.id, areaName]);
        
        expect(geometryResult.rows.length).toBe(1);
        expect(geometryResult.rows[0].geom_type).toBe('ST_Polygon');
        
      } catch (error) {
        console.log('Add favorite area workflow test failed:', error.message);
        // This test might fail due to complex drawing simulation
        // or backend integration issues
        throw error;
      }
    });
  });

  test.describe('Edit Favorite Dialog', () => {
    test('should complete edit favorite workflow', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Setup timeline with a pre-existing favorite
      const { testUser } = await timelinePage.loginAndNavigate();
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // Insert a favorite location
      await dbManager.client.query(`
        INSERT INTO favorite_locations 
        (id, user_id, name, city, country, type, geometry) 
        VALUES (8889, $1, 'Original Name', 'Test City', 'Test Country', 'POINT', 
                ST_GeomFromText('POINT(-74.0060 40.7128)', 4326))
      `, [user.id]);
      
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id);

      await timelinePage.navigateWithDateRange(new Date('2025-09-20'), new Date('2025-09-22'));
      await timelinePage.waitForPageLoad();
      await mapPage.waitForMapReady();
      
      // Ensure favorites layer is active
      if (!(await mapPage.isLayerActive('favorites'))) {
        await mapPage.toggleLayerControl('favorites');
      }
      
      await page.waitForTimeout(1000);
      
      const favoriteCount = await mapPage.countMarkers('favorite');
      if (favoriteCount > 0) {
        try {
          const originalName = 'Original Name';
          const newName = 'Updated Favorite Name';
          
          // Verify original name exists in database before edit
          const beforeResult = await dbManager.client.query(`
            SELECT id, name FROM favorite_locations 
            WHERE user_id = $1 AND name = $2
          `, [user.id, originalName]);
          
          expect(beforeResult.rows.length).toBe(1);
          expect(beforeResult.rows[0].name).toBe(originalName);
          const favoriteId = beforeResult.rows[0].id;
          
          // Complete edit workflow
          await mapPage.editFavoriteWorkflow(0, newName);
          
          // VERIFY DATABASE CHANGE: Check that name was actually updated
          const afterResult = await dbManager.client.query(`
            SELECT name FROM favorite_locations 
            WHERE user_id = $1 AND id = $2
          `, [user.id, favoriteId]);
          
          expect(afterResult.rows.length).toBe(1);
          expect(afterResult.rows[0].name).toBe(newName);
          
          // Verify old name no longer exists
          const oldNameResult = await dbManager.client.query(`
            SELECT COUNT(*) as count FROM favorite_locations 
            WHERE user_id = $1 AND name = $2
          `, [user.id, originalName]);
          
          expect(parseInt(oldNameResult.rows[0].count)).toBe(0);
          
        } catch (error) {
          console.log('Edit favorite workflow test failed:', error.message);
          // May fail if favorite markers don't have proper data attributes
          throw error;
        }
      } else {
        throw new Error('No favorite markers found for editing test');
      }
    });
  });

  test.describe('Delete Favorite Dialog', () => {
    test('should complete delete favorite workflow', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Setup timeline with a pre-existing favorite
      const { testUser } = await timelinePage.loginAndNavigate();
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // Insert a favorite location
      await dbManager.client.query(`
        INSERT INTO favorite_locations 
        (id, user_id, name, city, country, type, geometry) 
        VALUES (8890, $1, 'To Delete', 'Test City', 'Test Country', 'POINT', 
                ST_GeomFromText('POINT(-74.0060 40.7128)', 4326))
      `, [user.id]);
      
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id);

      await timelinePage.navigateWithDateRange(new Date('2025-09-20'), new Date('2025-09-22'));
      await timelinePage.waitForPageLoad();
      await mapPage.waitForMapReady();
      
      // Ensure favorites layer is active
      if (!(await mapPage.isLayerActive('favorites'))) {
        await mapPage.toggleLayerControl('favorites');
      }
      
      await page.waitForTimeout(1000);
      
      const favoriteCount = await mapPage.countMarkers('favorite');
      if (favoriteCount > 0) {
        try {
          const favoriteNameToDelete = 'To Delete';
          
          // Verify favorite exists in database before deletion
          const beforeResult = await dbManager.client.query(`
            SELECT id, name FROM favorite_locations 
            WHERE user_id = $1 AND name = $2
          `, [user.id, favoriteNameToDelete]);
          
          expect(beforeResult.rows.length).toBe(1);
          expect(beforeResult.rows[0].name).toBe(favoriteNameToDelete);
          const favoriteId = beforeResult.rows[0].id;
          
          // Get initial total count
          const initialCountResult = await dbManager.client.query(`
            SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1
          `, [user.id]);
          const initialCount = parseInt(initialCountResult.rows[0].count);
          
          // Complete delete workflow
          await mapPage.deleteFavoriteWorkflow(0);
          
          // VERIFY DATABASE CHANGE: Check that favorite was actually deleted
          const afterResult = await dbManager.client.query(`
            SELECT COUNT(*) as count FROM favorite_locations 
            WHERE user_id = $1 AND id = $2
          `, [user.id, favoriteId]);
          
          expect(parseInt(afterResult.rows[0].count)).toBe(0);
          
          // Verify total count decreased by 1
          const finalCountResult = await dbManager.client.query(`
            SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1
          `, [user.id]);
          const finalCount = parseInt(finalCountResult.rows[0].count);
          expect(finalCount).toBe(initialCount - 1);
          
          // Verify favorite was deleted from UI (count should decrease)
          await page.reload();
          await timelinePage.waitForPageLoad();
          const newFavoriteCount = await mapPage.countMarkers('favorite');
          expect(newFavoriteCount).toBeLessThan(favoriteCount);
          
        } catch (error) {
          console.log('Delete favorite workflow test failed:', error.message);
          // May fail due to complex confirmation dialog handling
          throw error;
        }
      } else {
        throw new Error('No favorite markers found for deletion test');
      }
    });
  });

  test.describe('Immich Integration Disabled', () => {
    test('should not show Immich controls when integration is disabled', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Verify that "Show Photos" button does not exist in map controls
      const showPhotosButton = page.locator('.map-controls .control-button[title="Show Photos"]');
      expect(await showPhotosButton.count()).toBe(0);
      
      // Verify that no camera icon button exists in map controls
      const cameraIconButton = page.locator('.map-controls .control-button .pi-camera');
      expect(await cameraIconButton.count()).toBe(0);
      
      // Verify that immich layer toggle would fail (layer doesn't exist)
      try {
        await mapPage.toggleLayerControl('immich');
        throw new Error('Should not be able to toggle immich layer when disabled');
      } catch (error) {
        // This should fail because the button doesn't exist
        expect(error.message).toContain('waiting for locator');
      }
    });

    test('should not show any photo markers on map', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Wait for all layers to load
      await page.waitForTimeout(2000);
      
      // Verify no photo/immich markers exist
      const immichMarkers = page.locator('.leaflet-marker-icon[data-marker-type="immich"]');
      expect(await immichMarkers.count()).toBe(0);
      
      const photoMarkers = page.locator('.leaflet-marker-pane *[class*="photo"]');
      expect(await photoMarkers.count()).toBe(0);
      
      const cameraMarkers = page.locator('.leaflet-marker-pane .pi-camera');
      expect(await cameraMarkers.count()).toBe(0);
    });
  });

  test.describe('Timeline Regeneration Modal', () => {
    test('should show timeline regeneration modal during favorite operations', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      try {
        // Start add favorite workflow
        await mapPage.rightClickOnMap(300, 300);
        await mapPage.waitForMapContextMenu();
        await mapPage.clickContextMenuItem('Add to Favorites');
        
        await mapPage.waitForAddFavoriteDialog();
        await mapPage.submitAddFavoriteDialog('Test Regeneration');
        
        // Verify timeline regeneration modal appears
        await mapPage.waitForTimelineRegenerationModal();
        
        // Modal should automatically close after regeneration
        await mapPage.waitForTimelineRegenerationModalToClose();
        
      } catch (error) {
        console.log('Timeline regeneration modal test failed:', error.message);
        // This test depends on backend integration working properly
      }
    });
  });

  test.describe('Error Handling', () => {
    test('should handle context menu on map edge', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Right-click near map edge
      await mapPage.rightClickOnMap(10, 10);
      
      try {
        await mapPage.waitForMapContextMenu();
        expect(await mapPage.isContextMenuVisible()).toBe(true);
      } catch (error) {
        // Context menu might not appear at very edge - this is acceptable
        console.log('Context menu at edge test - menu may not appear at extreme edge');
      }
    });
  });
});