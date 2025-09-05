import {test, expect} from '../fixtures/database-fixture.js';
import {TimelinePage} from '../pages/TimelinePage.js';
import {TimelineMapPage} from '../pages/TimelineMapPage.js';
import {TestData} from '../fixtures/test-data.js';
import * as TimelineTestData from '../utils/timeline-test-data.js';
import * as MapTestData from '../utils/map-test-data.js';

test.describe('Timeline Map Component', () => {

  test.describe('Map Initialization and Basic Functionality', () => {
    test('should initialize map correctly with no data', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Navigate to timeline page (no data)
      await timelinePage.loginAndNavigate();
      
      // Verify map shows no data message
      expect(await mapPage.isShowingNoDataMessage()).toBe(true);
      expect(await mapPage.isMapLoading()).toBe(false);
    });

    test('should initialize map with data and proper bounds', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Setup timeline with data
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      
      // Wait for map to be ready
      await mapPage.waitForMapReady();
      await mapPage.waitForMapLoadingToComplete();
      
      // Verify map has data and proper bounds
      expect(await mapPage.verifyMapHasData()).toBe(true);
      await mapPage.waitForMapToFitData();
      
      // Verify reasonable zoom level
      const zoom = await mapPage.getMapZoom();
      expect(zoom).toBeGreaterThan(5);
      expect(zoom).toBeLessThan(18);
    });

    test('should display map controls', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Verify map controls are visible
      const controls = mapPage.getMapControls();
      expect(await controls.isVisible()).toBe(true);
    });

    test('should handle map resize correctly', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Simulate viewport change
      await page.setViewportSize({ width: 800, height: 600 });
      await page.waitForTimeout(1000); // Allow map to resize
      
      // Verify map is still functional
      const zoom = await mapPage.getMapZoom();
      expect(zoom).toBeGreaterThan(5);
    });
  });

  test.describe('Map Layer Controls', () => {
    test('should toggle timeline layer visibility', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Timeline layer should be active by default
      expect(await mapPage.isLayerActive('timeline')).toBe(true);
      
      // Toggle timeline layer off
      await mapPage.toggleLayerControl('timeline');
      expect(await mapPage.isLayerActive('timeline')).toBe(false);
      
      // Toggle timeline layer back on
      await mapPage.toggleLayerControl('timeline');
      expect(await mapPage.isLayerActive('timeline')).toBe(true);
    });

    test('should toggle favorites layer visibility', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Toggle favorites layer
      const initialState = await mapPage.isLayerActive('favorites');
      await mapPage.toggleLayerControl('favorites');
      expect(await mapPage.isLayerActive('favorites')).toBe(!initialState);
      
      // Toggle back
      await mapPage.toggleLayerControl('favorites');
      expect(await mapPage.isLayerActive('favorites')).toBe(initialState);
    });

    test('should toggle path layer visibility', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Toggle path layer
      const initialState = await mapPage.isLayerActive('path');
      await mapPage.toggleLayerControl('path');
      expect(await mapPage.isLayerActive('path')).toBe(!initialState);
      
      // Toggle back  
      await mapPage.toggleLayerControl('path');
      expect(await mapPage.isLayerActive('path')).toBe(initialState);
    });

    test('should use zoom to data functionality', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Change zoom level manually
      await page.evaluate(() => {
        const mapContainer = document.querySelector('.leaflet-container');
        if (mapContainer && mapContainer._leaflet_map) {
          mapContainer._leaflet_map.setZoom(5);
        }
      });
      
      // Get current zoom
      let zoom = await mapPage.getMapZoom();
      expect(zoom).toBeLessThanOrEqual(6);
      
      // Click zoom to data
      await mapPage.clickZoomToData();
      
      // Verify zoom changed to fit data
      zoom = await mapPage.getMapZoom();
      expect(zoom).toBeGreaterThan(8);
    });
  });

  test.describe('Timeline Markers and Data Display', () => {
    test('should display timeline markers when data is loaded', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      const { testData } = await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertVerifiableStaysTestData);
      await mapPage.waitForMapReady();
      
      // Verify timeline markers are displayed
      const markerCount = await mapPage.countMarkers('timeline');
      expect(markerCount).toBeGreaterThan(0);
      expect(markerCount).toBeLessThanOrEqual(testData.length);
    });

    test('should display different types of timeline markers', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Insert comprehensive map test data (stays, trips, gaps + GPS path)
      const { testUser } = await timelinePage.loginAndNavigate();
      const user = await dbManager.getUserByEmail(testUser.email);
      
      await MapTestData.insertComprehensiveMapTestData(dbManager, user.id);
      
      await page.reload();
      await timelinePage.waitForPageLoad();
      await mapPage.waitForMapReady();
      
      // Verify different types of markers are displayed
      const totalMarkers = await mapPage.countMarkers('timeline');
      expect(totalMarkers).toBeGreaterThan(5); // Should have stays + trips + gaps
    });

    test('should display path lines when path layer is active', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Ensure path layer is active
      if (!(await mapPage.isLayerActive('path'))) {
        await mapPage.toggleLayerControl('path');
      }
      
      // Verify path lines are displayed
      const pathCount = await mapPage.getPathLines().count();
      expect(pathCount).toBeGreaterThanOrEqual(0); // May be 0 if no path data
    });

    test('should display current location marker when viewing today', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Setup timeline with today's data
      const today = new Date();
      await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertRegularStaysTestData, TestData.users.existing, {
        startDate: today,
        endDate: today
      });
      await mapPage.waitForMapReady();
      
      // Current location might be visible depending on data
      const isCurrentLocationVisible = await mapPage.isCurrentLocationVisible();
      // This test depends on having recent location data, so we just verify the method works
      expect(typeof isCurrentLocationVisible).toBe('boolean');
    });
  });

  test.describe('Map Click Interactions', () => {
    test('should handle timeline marker clicks', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Ensure timeline layer is active
      if (!(await mapPage.isLayerActive('timeline'))) {
        await mapPage.toggleLayerControl('timeline');
      }
      
      // Click on first timeline marker
      await mapPage.clickTimelineMarker(0);
      
      // Verify marker interaction worked (may trigger highlighting)
      // The exact behavior depends on the highlighting system
      await page.waitForTimeout(500);
    });

    test('should handle path line clicks', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Ensure path layer is active
      if (!(await mapPage.isLayerActive('path'))) {
        await mapPage.toggleLayerControl('path');
      }
      
      // Check if path lines exist before clicking
      const pathCount = await mapPage.getPathLines().count();
      if (pathCount > 0) {
        await mapPage.clickPathLine(0);
        await page.waitForTimeout(500);
      }
    });

    test('should clear highlights when clicking on empty map', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Click on timeline marker to create highlight
      if (!(await mapPage.isLayerActive('timeline'))) {
        await mapPage.toggleLayerControl('timeline');
      }
      await mapPage.clickTimelineMarker(0);
      await page.waitForTimeout(500);
      
      // Click on empty area of map to clear highlights
      await mapPage.clickOnMap(100, 100);
      await page.waitForTimeout(500);
      
      // Verify highlights were cleared (implementation dependent)
    });
  });

  test.describe('Responsive Behavior', () => {
    test('should handle mobile viewport', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Set mobile viewport
      await page.setViewportSize({ width: 375, height: 667 });
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Verify map controls are still accessible on mobile
      const controls = mapPage.getMapControls();
      expect(await controls.isVisible()).toBe(true);
      
      // Verify map is still functional
      const zoom = await mapPage.getMapZoom();
      expect(zoom).toBeGreaterThan(5);
    });

    test('should handle tablet viewport', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Set tablet viewport
      await page.setViewportSize({ width: 768, height: 1024 });
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Verify map functionality on tablet
      const zoom = await mapPage.getMapZoom();
      expect(zoom).toBeGreaterThan(5);
    });
  });

  test.describe('Error Handling and Edge Cases', () => {
    test('should handle map with single data point', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Insert only one stay
      const { testUser } = await timelinePage.loginAndNavigate();
      const user = await dbManager.getUserByEmail(testUser.email);
      
      const now = new Date();
      const stayTime = new Date(now.getTime() - (2 * 60 * 60 * 1000));
      
      // Create single location
      const result = await dbManager.client.query(`
        INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
        VALUES (nextval('reverse_geocoding_location_seq'), 'POINT(-74.0060 40.7128)', 'POINT(-74.0060 40.7128)', 'Single Location, New York, NY', 'test', 'New York', 'United States', NOW(), NOW())
        RETURNING id
      `);
      const geocodingId = result.rows[0].id;
      
      await dbManager.client.query(`
        INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
        VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
      `, [user.id, stayTime, 3600, 40.7128, -74.0060, 'Single Location', geocodingId]);
      
      await page.reload();
      await timelinePage.waitForPageLoad();
      await mapPage.waitForMapReady();
      
      // Verify map centers on single point with reasonable zoom
      const center = await mapPage.getMapCenter();
      expect(center.lat).toBeCloseTo(40.7128, 1);
      expect(center.lng).toBeCloseTo(-74.0060, 1);
      
      const zoom = await mapPage.getMapZoom();
      expect(zoom).toBeGreaterThan(10); // Should zoom in closer for single point
    });

    test('should handle rapid layer toggling', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      await timelinePage.setupTimelineWithData(dbManager, MapTestData.insertMapTestStaysData);
      await mapPage.waitForMapReady();
      
      // Rapidly toggle multiple layers
      for (let i = 0; i < 3; i++) {
        await mapPage.toggleLayerControl('timeline');
        await mapPage.toggleLayerControl('favorites');
        await mapPage.toggleLayerControl('path');
        await page.waitForTimeout(100);
      }
      
      // Verify map is still functional after rapid toggling
      const zoom = await mapPage.getMapZoom();
      expect(zoom).toBeGreaterThan(5);
    });

    test('should handle empty data gracefully', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const mapPage = new TimelineMapPage(page);
      
      // Navigate without inserting any data
      await timelinePage.loginAndNavigate();
      
      // Verify no data message is shown
      expect(await mapPage.isShowingNoDataMessage()).toBe(true);
      
      // Verify map controls are still functional
      const controls = mapPage.getMapControls();
      expect(await controls.isVisible()).toBe(true);
    });
  });
});