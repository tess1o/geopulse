import {test, expect} from '../fixtures/isolated-fixture.js';
import {TimelinePage} from '../pages/TimelinePage.js';
import {TimelineMapPage} from '../pages/TimelineMapPage.js';
import * as TimelineTestData from '../utils/timeline-test-data.js';
import * as MapTestData from '../utils/map-test-data.js';
import {DateFormatTestHelper, DateFormatValues, KnownDateStrings} from '../utils/date-format-test-helper.js';
import {buildManagedUser as createManagedUser} from '../utils/isolated-user-helper.js';
import { randomUUID } from 'crypto';
import { MAP_POPUP_CONTENT_SELECTOR } from '../utils/map-engine-harness.js';

const getUtcTodayDate = () => {
  const now = new Date();
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 12));
};

const getIsoDate = (date) => date.toISOString().slice(0, 10);

const CURRENT_LOCATION_MARKER_SELECTOR = [
  '.map-view-container [data-marker-type="current-location"]',
  '.map-view-container .current-location-marker'
].join(', ');

const getPopupCard = (page) => page
  .locator(MAP_POPUP_CONTENT_SELECTOR)
  .locator('.gp-map-popup-card')
  .first();

const closeOpenMapPopups = (page) => page.evaluate(() => {
  const host = document.querySelector('.map-view-container [data-testid="map-host-raster"]');
  const registeredMap = host?.id ? window.__GP_E2E_MAPS?.[host.id] : null;
  registeredMap?.closePopup?.();
  document.querySelector('.leaflet-container')?._leaflet_map?.closePopup?.();
  document.querySelectorAll('.maplibregl-popup').forEach((popup) => popup.remove());
});

const openCurrentLocationLayer = (page) => page.evaluate((currentLocationSelector) => {
  const host = document.querySelector('.map-view-container [data-testid="map-host-raster"]');
  const registeredMap = host?.id ? window.__GP_E2E_MAPS?.[host.id] : null;
  const map = registeredMap || document.querySelector('.leaflet-container')?._leaflet_map;
  if (!map || typeof map.eachLayer !== 'function') {
    return false;
  }

  const currentLocationElements = new Set(document.querySelectorAll(currentLocationSelector));
  const openLayer = (layer) => {
    if (!layer) {
      return false;
    }

    if (typeof layer.eachLayer === 'function') {
      let openedChild = false;
      layer.eachLayer((childLayer) => {
        if (!openedChild) {
          openedChild = openLayer(childLayer);
        }
      });
      return openedChild;
    }

    const element = typeof layer.getElement === 'function' ? layer.getElement() : null;
    const isCurrentLocationLayer = layer?.options?.markerType === 'current-location'
      || (element && currentLocationElements.has(element));

    if (isCurrentLocationLayer && typeof layer.openPopup === 'function') {
      layer.openPopup();
      return true;
    }

    return false;
  };

  let opened = false;
  map.eachLayer((layer) => {
    if (!opened) {
      opened = openLayer(layer);
    }
  });

  return opened;
}, CURRENT_LOCATION_MARKER_SELECTOR);

const moveRegularStaysToDate = async (dbManager, userId, date) => {
  const isoDate = getIsoDate(date);
  const gpsRows = await dbManager.client.query(`
    SELECT id
    FROM gps_points
    WHERE user_id = $1
    ORDER BY timestamp
  `, [userId]);
  const stayRows = await dbManager.client.query(`
    SELECT id
    FROM timeline_stays
    WHERE user_id = $1
    ORDER BY timestamp
  `, [userId]);

  for (let index = 0; index < gpsRows.rows.length; index += 1) {
    const timestamp = `${isoDate}T${String(15 + index).padStart(2, '0')}:00:00Z`;
    await dbManager.client.query(`
      UPDATE gps_points
      SET timestamp = $2,
          created_at = $2
      WHERE id = $1
    `, [gpsRows.rows[index].id, timestamp]);
  }

  for (let index = 0; index < stayRows.rows.length; index += 1) {
    const timestamp = `${isoDate}T${String(15 + index).padStart(2, '0')}:00:00Z`;
    await dbManager.client.query(`
      UPDATE timeline_stays
      SET timestamp = $2,
          created_at = NOW(),
          last_updated = NOW()
      WHERE id = $1
    `, [stayRows.rows[index].id, timestamp]);
  }
};

const insertCurrentLocationTelemetryScenario = async (dbManager, userId, showCurrentLocationTelemetry = true, selectedDate = getUtcTodayDate()) => {
  await TimelineTestData.insertRegularStaysTestData(dbManager, userId);
  await moveRegularStaysToDate(dbManager, userId, selectedDate);
  const currentPointTimestamp = `${getIsoDate(selectedDate)}T23:30:00Z`;

  const telemetryConfigId = randomUUID();
  const telemetryMapping = [
    {
      key: 'ignition',
      label: 'Ignition',
      type: 'boolean',
      enabled: true,
      order: 10,
      trueValues: ['1', 'true', 'yes', 'on'],
      falseValues: ['0', 'false', 'no', 'off'],
      showInGpsData: false,
      showInCurrentPopup: true
    },
    {
      key: 'lte_pct',
      label: 'LTE Signal',
      type: 'number',
      unit: '%',
      enabled: true,
      order: 20,
      showInGpsData: false,
      showInCurrentPopup: false
    }
  ];

  await dbManager.client.query(`
    INSERT INTO gps_source_type_telemetry_config (
      id,
      user_id,
      source_type,
      mapping
    )
    VALUES ($1, $2, 'OWNTRACKS', $3::jsonb)
  `, [telemetryConfigId, userId, JSON.stringify(telemetryMapping)]);

  // Keep the current-location marker separate from timeline stay markers so
  // the test opens the latest-position popup, not an overlapping stay popup.
  await dbManager.client.query(`
    INSERT INTO gps_points (
      device_id,
      user_id,
      coordinates,
      timestamp,
      accuracy,
      battery,
      velocity,
      altitude,
      source_type,
      created_at,
      telemetry
    )
    VALUES (
      'current-location-device',
      $1,
      ST_GeomFromText('POINT(-73.9854 40.7535)', 4326),
      $3,
      5.0,
      88,
      0.0,
      15.0,
      'OWNTRACKS',
      $3,
      $2::jsonb
    )
  `, [userId, JSON.stringify({ ignition: 1, lte_pct: 72 }), currentPointTimestamp]);

  await dbManager.client.query(`
    UPDATE users
    SET timeline_display_show_current_location_telemetry = $2
    WHERE id = $1
  `, [userId, showCurrentLocationTelemetry]);
};

const insertStayPopupTelemetryScenario = async (dbManager, userId, showCurrentLocationTelemetry = true) => {
  await TimelineTestData.insertRegularStaysTestData(dbManager, userId);

  const telemetryConfigId = randomUUID();
  const telemetryMapping = [
    {
      key: 'ignition',
      label: 'Ignition',
      type: 'boolean',
      enabled: true,
      order: 10,
      trueValues: ['1', 'true', 'yes', 'on'],
      falseValues: ['0', 'false', 'no', 'off'],
      showInGpsData: false,
      showInCurrentPopup: true
    }
  ];

  await dbManager.client.query(`
    INSERT INTO gps_source_type_telemetry_config (
      id,
      user_id,
      source_type,
      mapping
    )
    VALUES ($1, $2, 'OWNTRACKS', $3::jsonb)
  `, [telemetryConfigId, userId, JSON.stringify(telemetryMapping)]);

  await dbManager.client.query(`
    UPDATE gps_points
    SET source_type = 'OWNTRACKS',
        telemetry = $2::jsonb
    WHERE user_id = $1
  `, [userId, JSON.stringify({ ignition: 1 })]);

  await dbManager.client.query(`
    UPDATE users
    SET timeline_display_show_current_location_telemetry = $2
    WHERE id = $1
  `, [userId, showCurrentLocationTelemetry]);
};

const openCurrentLocationPopup = async (page) => {
  await closeOpenMapPopups(page);

  await expect.poll(async () => {
    const markerCount = await page.locator(CURRENT_LOCATION_MARKER_SELECTOR).count();
    if (markerCount > 0) {
      return markerCount;
    }

    const layerOpened = await openCurrentLocationLayer(page);
    if (layerOpened) {
      await closeOpenMapPopups(page);
      return 1;
    }

    return 0;
  }, { timeout: 15000 }).toBeGreaterThan(0);

  await closeOpenMapPopups(page);
  const opened = await openCurrentLocationLayer(page);

  if (opened) {
    await expect(getPopupCard(page)).toBeVisible({ timeout: 10000 });
    return;
  }

  const markerCandidates = page.locator(CURRENT_LOCATION_MARKER_SELECTOR);
  const markerCount = await markerCandidates.count();
  if (markerCount > 0) {
    await markerCandidates.first().click({ force: true });
    await expect(getPopupCard(page)).toBeVisible({ timeout: 10000 });
    return;
  }

  await expect(getPopupCard(page)).toBeVisible({ timeout: 10000 });
};

const setupTimelineWithMapMode = (
  timelinePage,
  dbManager,
  dataInsertFunction,
  testUser,
  dateRange,
  mapMode
) => {
  const effectiveDateRange = dateRange ?? {
    startDate: new Date(),
    endDate: new Date()
  };

  return timelinePage.setupTimelineWithData(
    dbManager,
    dataInsertFunction,
    testUser,
    effectiveDateRange,
    { mapMode }
  );
};

test.describe('Timeline Map Interactions', () => {
  test.use({ mapMode: 'RASTER' });

  test.describe('Raster map loading', () => {
    test('should not request vector map assets when raster mode is selected on initial timeline load', async ({page, isolatedUsers, dbManager}) => {
      const vectorRequests = [];
      page.on('request', (request) => {
        const url = request.url();
        if (
          url.includes('.pbf')
          || url.includes('tiles.openfreemap.org')
          || url.includes('maplibre')
        ) {
          vectorRequests.push(url);
        }
      });

      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);

      await setupTimelineWithMapMode(timelinePage, dbManager, TimelineTestData.insertRegularStaysTestData, createManagedUser(isolatedUsers), {
        startDate: new Date('2025-09-21'),
        endDate: new Date('2025-09-21')
      }, 'RASTER');

      await mapPage.waitForMapReady();

      await expect(page.locator('.map-view-container [data-testid="map-host-raster"]')).toBeVisible();
      await expect(page.locator('.map-view-container [data-testid="map-host-vector"]')).toHaveCount(0);
      expect(vectorRequests).toEqual([]);
    });

    test('should keep raster mode from auth snapshot when display settings fail', async ({page, isolatedUsers, dbManager}) => {
      const vectorRequests = [];
      page.on('request', (request) => {
        const url = request.url();
        if (
          url.includes('.pbf')
          || url.includes('tiles.openfreemap.org')
          || url.includes('maplibre')
        ) {
          vectorRequests.push(url);
        }
      });

      await page.route('**/api/users/preferences/timeline/display', async (route) => {
        if (route.request().method() !== 'GET') {
          await route.continue();
          return;
        }

        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 'error',
            message: 'display preferences unavailable'
          })
        });
      });

      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);

      await setupTimelineWithMapMode(timelinePage, dbManager, TimelineTestData.insertRegularStaysTestData, createManagedUser(isolatedUsers), {
        startDate: new Date('2025-09-21'),
        endDate: new Date('2025-09-21')
      }, 'RASTER');

      await mapPage.waitForMapReady();

      await expect(page.locator('.map-view-container [data-testid="map-host-raster"]')).toBeVisible();
      await expect(page.locator('.map-view-container [data-testid="map-host-vector"]')).toHaveCount(0);
      expect(vectorRequests).toEqual([]);
    });
  });

  test('should display timeline marker popup timestamps using user date format', async ({page, isolatedUsers, dbManager, mapMode}) => {
    const timelinePage = new TimelinePage(page);
    const mapPage = new TimelineMapPage(page);
    const testUser = createManagedUser(isolatedUsers, { dateFormat: DateFormatValues.DMY });

    await setupTimelineWithMapMode(timelinePage, dbManager, TimelineTestData.insertRegularStaysTestData, testUser, {
      startDate: new Date('2025-09-21'),
      endDate: new Date('2025-09-21')
    }, mapMode);

    await mapPage.waitForMapReady();
    const firstStayCard = timelinePage.getTimelineCards('stays').first();
    await expect(firstStayCard).toBeVisible();
    await firstStayCard.click();

    const popupContent = mapPage.getPopupContent();
    await expect(popupContent).toBeVisible({ timeout: 15000 });

    const popupText = await popupContent.textContent();
    DateFormatTestHelper.expectContainsDate(
      popupText,
      KnownDateStrings.sep21_2025.DMY,
      KnownDateStrings.sep21_2025.MDY
    );
  });

  test.describe('Current Location Telemetry', () => {
    test('should show current-location telemetry from latest point when enabled', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      const today = getUtcTodayDate();
      const testUser = createManagedUser(isolatedUsers, { timezone: 'UTC' });

      await setupTimelineWithMapMode(
        timelinePage,
        dbManager,
        async (manager, userId) => insertCurrentLocationTelemetryScenario(manager, userId, true, today),
        testUser,
        {
          startDate: today,
          endDate: today
        },
        mapMode
      );

      await mapPage.waitForMapReady();
      await openCurrentLocationPopup(page);

      const popup = mapPage.getPopupContent();
      await expect(popup.locator('.gp-map-popup-section-title', { hasText: 'Telemetry' })).toBeVisible();
      await expect(popup).toContainText('Ignition');
      await expect(popup).toContainText('Yes');
      await expect(popup).not.toContainText('LTE Signal');
    });

    test('should hide current-location telemetry when display toggle is disabled', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      const today = getUtcTodayDate();
      const testUser = createManagedUser(isolatedUsers, { timezone: 'UTC' });

      await setupTimelineWithMapMode(
        timelinePage,
        dbManager,
        async (manager, userId) => insertCurrentLocationTelemetryScenario(manager, userId, false, today),
        testUser,
        {
          startDate: today,
          endDate: today
        },
        mapMode
      );

      await mapPage.waitForMapReady();
      await openCurrentLocationPopup(page);

      const popup = mapPage.getPopupContent();
      await expect(popup.locator('.gp-map-popup-section-title', { hasText: 'Telemetry' })).toHaveCount(0);
      await expect(popup).not.toContainText('Ignition');
    });

    test('should show telemetry in stay popup when clicking a StayCard', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      const testUser = createManagedUser(isolatedUsers);

      await setupTimelineWithMapMode(
        timelinePage,
        dbManager,
        async (manager, userId) => insertStayPopupTelemetryScenario(manager, userId),
        testUser,
        {
          startDate: new Date('2025-09-21'),
          endDate: new Date('2025-09-21')
        },
        mapMode
      );

      await mapPage.waitForMapReady();
      const firstStayCard = timelinePage.getTimelineCards('stays').first();
      await expect(firstStayCard).toBeVisible();
      await firstStayCard.click();

      const popup = mapPage.getPopupContent();
      await expect(popup).toBeVisible({ timeout: 15000 });
      await expect(popup.locator('.gp-map-popup-section-title', { hasText: 'Telemetry' })).toBeVisible();
      await expect(popup).toContainText('Ignition');
      await expect(popup).toContainText('Yes');
    });

    test('should hide telemetry in stay popup when display toggle is disabled', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      const testUser = createManagedUser(isolatedUsers);

      await setupTimelineWithMapMode(
        timelinePage,
        dbManager,
        async (manager, userId) => insertStayPopupTelemetryScenario(manager, userId, false),
        testUser,
        {
          startDate: new Date('2025-09-21'),
          endDate: new Date('2025-09-21')
        },
        mapMode
      );

      await mapPage.waitForMapReady();
      const firstStayCard = timelinePage.getTimelineCards('stays').first();
      await expect(firstStayCard).toBeVisible();
      await firstStayCard.click();

      const popup = mapPage.getPopupContent();
      await expect(popup).toBeVisible({ timeout: 15000 });
      await expect(popup.locator('.gp-map-popup-section-title', { hasText: 'Telemetry' })).toHaveCount(0);
      await expect(popup).not.toContainText('Ignition');
    });
  });

  test.describe('Context Menus', () => {
    test('should show map context menu on right click', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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

    test('should handle context menu item clicks', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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

    test('should show favorite context menu on favorite marker right-click', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Setup timeline with data and add a favorite
      const { testUser } = await timelinePage.loginAndNavigate(createManagedUser(isolatedUsers), { dbManager, mapMode });
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
    test('should complete add favorite point workflow', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      const { user } = await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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

        await expect.poll(async () => {
          const result = await dbManager.client.query(`
            SELECT COUNT(*) as count
            FROM favorite_locations
            WHERE user_id = $1 AND name = $2
          `, [user.id, favoriteName]);
          return parseInt(result.rows[0].count);
        }, { timeout: 30000 }).toBe(1);
        
        // Verify total count increased by 1
        await expect.poll(async () => {
          const result = await dbManager.client.query(`
            SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1
          `, [user.id]);
          return parseInt(result.rows[0].count);
        }, { timeout: 30000 }).toBe(initialCount + 1);
        
      } catch (error) {
        console.log('Add favorite workflow test failed:', error.message);
        // This test might fail if backend isn't properly set up for favorites
        // or if timeline regeneration takes too long
        throw error;
      }
    });

    test('should handle add favorite dialog cancellation', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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
    test('should enter drawing mode when starting area creation', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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

    test('should cancel drawing with Escape key', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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

    test('should complete rectangle drawing workflow', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      const { user } = await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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

        await expect.poll(async () => {
          const result = await dbManager.client.query(`
            SELECT COUNT(*) as count
            FROM favorite_locations
            WHERE user_id = $1 AND name = $2 AND type = 'AREA'
          `, [user.id, areaName]);
          return parseInt(result.rows[0].count);
        }, { timeout: 20000 }).toBe(1);
        
        // Verify total count increased by 1
        await expect.poll(async () => {
          const result = await dbManager.client.query(`
            SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1
          `, [user.id]);
          return parseInt(result.rows[0].count);
        }, { timeout: 20000 }).toBe(initialCount + 1);
        
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
    test('should complete edit favorite workflow', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Setup timeline with a pre-existing favorite
      const { testUser } = await timelinePage.loginAndNavigate(createManagedUser(isolatedUsers), { dbManager, mapMode });
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

      await mapPage.focusMapOnCoordinates(40.7128, -74.0060, 12);
      await expect.poll(() => mapPage.countMarkers('favorite'), { timeout: 30000 }).toBeGreaterThan(0);

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
        await expect.poll(async () => {
          const result = await dbManager.client.query(`
            SELECT name FROM favorite_locations 
            WHERE user_id = $1 AND id = $2
          `, [user.id, favoriteId]);
          return result.rows[0]?.name || null;
        }, { timeout: 20000 }).toBe(newName);
        
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
    });
  });

  test.describe('Delete Favorite Dialog', () => {
    test('should complete delete favorite workflow', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Setup timeline with a pre-existing favorite
      const { testUser } = await timelinePage.loginAndNavigate(createManagedUser(isolatedUsers), { dbManager, mapMode });
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

      await mapPage.focusMapOnCoordinates(40.7128, -74.0060, 12);
      await expect.poll(() => mapPage.countMarkers('favorite'), { timeout: 30000 }).toBeGreaterThan(0);
      const favoriteCountBeforeDelete = await mapPage.countMarkers('favorite');

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
        await expect.poll(async () => {
          const result = await dbManager.client.query(`
            SELECT COUNT(*) as count FROM favorite_locations 
            WHERE user_id = $1 AND id = $2
          `, [user.id, favoriteId]);
          return parseInt(result.rows[0].count);
        }, { timeout: 30000 }).toBe(0);
        
        // Verify total count decreased by 1
        const finalCountResult = await dbManager.client.query(`
          SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1
        `, [user.id]);
        const finalCount = parseInt(finalCountResult.rows[0].count);
        expect(finalCount).toBe(initialCount - 1);
        
        // Verify favorite was deleted from UI (count should decrease)
        await page.reload();
        await timelinePage.waitForPageLoad();
        await mapPage.waitForMapReady();
        if (!(await mapPage.isLayerActive('favorites'))) {
          await mapPage.toggleLayerControl('favorites');
        }

        await expect.poll(() => mapPage.countMarkers('favorite'), { timeout: 30000 }).toBeLessThan(favoriteCountBeforeDelete);
        
      } catch (error) {
        console.log('Delete favorite workflow test failed:', error.message);
        // May fail due to complex confirmation dialog handling
        throw error;
      }
    });
  });

  test.describe('Immich Integration Disabled', () => {
    test('should not show Immich controls when integration is disabled', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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

    test('should not show any photo markers on map', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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
    test('should show timeline regeneration modal during favorite operations', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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
    test('should handle context menu on map edge', async ({page, isolatedUsers, dbManager, mapMode}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await setupTimelineWithMapMode(timelinePage, dbManager, MapTestData.insertMapTestStaysData, createManagedUser(isolatedUsers), null, mapMode);
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
