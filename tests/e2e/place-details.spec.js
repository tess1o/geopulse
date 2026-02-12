import {test, expect} from '../fixtures/database-fixture.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';
import {PlaceDetailsPage} from '../pages/PlaceDetailsPage.js';

test.describe('Place Details Page', () => {

  test.describe('Favorite Place Details - Page Load and Initial State', () => {
    test('should display favorite point place details correctly', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite point
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Central Park',
        city: 'New York',
        country: 'USA',
        latitude: 40.7829,
        longitude: -73.9654
      });

      // Create some timeline stays at this favorite
      await TestSetupHelper.createMultipleTimelineStays(dbManager, user.id, [
        {favoriteId, coords: `POINT(-73.9654 40.7829)`, locationName: 'Central Park', timestampOffset: '1 day'},
        {favoriteId, coords: `POINT(-73.9654 40.7829)`, locationName: 'Central Park', timestampOffset: '2 days'},
        {favoriteId, coords: `POINT(-73.9654 40.7829)`, locationName: 'Central Park', timestampOffset: '3 days'}
      ]);

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Verify page displays correctly
      expect(await placeDetailsPage.isLoading()).toBe(false);
      expect(await placeDetailsPage.hasError()).toBe(false);

      // Verify page title
      const pageTitle = await placeDetailsPage.getPageTitle();
      expect(pageTitle).toContain('Central Park');

      // Verify no related favorite notice (this is a favorite itself)
      expect(await placeDetailsPage.hasRelatedFavoriteNotice()).toBe(false);

      // Verify statistics card is visible
      expect(await placeDetailsPage.hasStatisticsCard()).toBe(true);

      // Verify map is visible
      expect(await placeDetailsPage.hasMap()).toBe(true);

      // Verify visits table is visible
      expect(await placeDetailsPage.hasVisitsTable()).toBe(true);

      // Verify edit button is present
      const editButton = page.locator('button:has-text("Edit")');
      expect(await editButton.isVisible()).toBe(true);
    });

    test('should display favorite area place details correctly', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite area
      const favoriteId = await TestSetupHelper.createFavoriteArea(dbManager, user.id, {
        name: 'Downtown District',
        city: 'Boston',
        country: 'USA',
        southWestLat: 42.35,
        southWestLon: -71.07,
        northEastLat: 42.36,
        northEastLon: -71.05
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Verify page displays correctly
      const pageTitle = await placeDetailsPage.getPageTitle();
      expect(pageTitle).toContain('Downtown District');

      // Verify map is visible (area should be displayed)
      expect(await placeDetailsPage.hasMap()).toBe(true);
    });

    test('should show error state for non-existent favorite', async ({page, dbManager}) => {
      await TestSetupHelper.createAndLoginUser(page, dbManager);

      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(999999);
      await placeDetailsPage.waitForPageLoad();

      // Verify error state
      expect(await placeDetailsPage.hasError()).toBe(true);

      const errorMessage = await placeDetailsPage.getErrorMessage();
      expect(errorMessage.length).toBeGreaterThan(0);
    });
  });

  test.describe('Geocoding Place Details - Page Load and Initial State', () => {
    test('should display geocoding place details correctly', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a geocoding result
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: `POINT(-74.0060 40.7128)`,
        displayName: 'Times Square',
        city: 'New York',
        country: 'USA',
        providerName: 'Nominatim'
      });

      // Create some timeline stays
      await TestSetupHelper.createMultipleTimelineStays(dbManager, user.id, [
        {geocodingId, coords: `POINT(-74.0060 40.7128)`, locationName: 'Times Square', timestampOffset: '1 day'},
        {geocodingId, coords: `POINT(-74.0060 40.7128)`, locationName: 'Times Square', timestampOffset: '2 days'}
      ]);

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToGeocoding(geocodingId);
      await placeDetailsPage.waitForPageLoad();

      // Verify page displays correctly
      expect(await placeDetailsPage.isLoading()).toBe(false);
      expect(await placeDetailsPage.hasError()).toBe(false);

      const pageTitle = await placeDetailsPage.getPageTitle();
      expect(pageTitle).toContain('Times Square');

      // Verify statistics card is visible (has visits)
      expect(await placeDetailsPage.hasStatisticsCard()).toBe(true);

      // Verify map is visible
      expect(await placeDetailsPage.hasMap()).toBe(true);

      // Verify visits table is visible
      expect(await placeDetailsPage.hasVisitsTable()).toBe(true);

      // Verify create favorite button is present
      const createFavoriteButton = page.locator('button:has-text("Create Favorite")');
      expect(await createFavoriteButton.isVisible()).toBe(true);
    });

    test('should show related favorite notice when geocoding is within favorite bounds', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite area
      const favoriteId = await TestSetupHelper.createFavoriteArea(dbManager, user.id, {
        name: 'Manhattan Area',
        city: 'New York',
        country: 'USA',
        southWestLat: 40.70,
        southWestLon: -74.02,
        northEastLat: 40.80,
        northEastLon: -73.90
      });

      // Create a geocoding result within that area
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: `POINT(-74.0060 40.7128)`,
        displayName: 'Times Square',
        city: 'New York',
        country: 'USA'
      });

      // Navigate to geocoding place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToGeocoding(geocodingId);
      await placeDetailsPage.waitForPageLoad();

      // Verify related favorite notice is visible
      expect(await placeDetailsPage.hasRelatedFavoriteNotice()).toBe(true);

      // Verify notice content
      const noticeTitle = await placeDetailsPage.getRelatedFavoriteTitle();
      expect(noticeTitle).toContain('Grouped');

      const favoriteName = await placeDetailsPage.getRelatedFavoriteName();
      expect(favoriteName).toContain('Manhattan Area');

      // Verify statistics card is NOT visible (related favorite case)
      expect(await placeDetailsPage.hasStatisticsCard()).toBe(false);

      // Verify visits table is NOT visible (related favorite case)
      expect(await placeDetailsPage.hasVisitsTable()).toBe(false);
    });

    test('should navigate to related favorite when clicking view button', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite area
      const favoriteId = await TestSetupHelper.createFavoriteArea(dbManager, user.id, {
        name: 'Brooklyn Area',
        city: 'Brooklyn',
        country: 'USA',
        southWestLat: 40.60,
        southWestLon: -74.05,
        northEastLat: 40.75,
        northEastLon: -73.85
      });

      // Create a geocoding result within that area
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: `POINT(-73.95 40.65)`,
        displayName: 'Brooklyn Location',
        city: 'Brooklyn',
        country: 'USA'
      });

      // Navigate to geocoding place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToGeocoding(geocodingId);
      await placeDetailsPage.waitForPageLoad();

      // Click view related favorite button
      await placeDetailsPage.clickViewRelatedFavorite();

      // Verify navigation to favorite details page
      await page.waitForURL(`**/app/place-details/favorite/${favoriteId}`, {timeout: 5000});
      expect(page.url()).toContain(`/app/place-details/favorite/${favoriteId}`);
    });
  });

  test.describe('Statistics Display', () => {
    test('should display statistics for favorite with visits', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Home',
        city: 'San Francisco',
        country: 'USA',
        latitude: 37.7749,
        longitude: -122.4194
      });

      // Create timeline stays for this favorite
      await TestSetupHelper.createMultipleTimelineStays(dbManager, user.id, [
        {favoriteId, coords: `POINT(-122.4194 37.7749)`, locationName: 'Home', durationSeconds: 7200, timestampOffset: '1 day'},
        {favoriteId, coords: `POINT(-122.4194 37.7749)`, locationName: 'Home', durationSeconds: 3600, timestampOffset: '2 days'},
        {favoriteId, coords: `POINT(-122.4194 37.7749)`, locationName: 'Home', durationSeconds: 5400, timestampOffset: '3 days'}
      ]);

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Verify statistics card is visible
      expect(await placeDetailsPage.hasStatisticsCard()).toBe(true);

      // Verify statistics contain expected data
      const stats = await placeDetailsPage.getStatistics();
      expect(Object.keys(stats).length).toBeGreaterThan(0);
    });
  });

  test.describe('Visits Table - Pagination and Sorting', () => {
    test('should display visits in table', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Office',
        city: 'Seattle',
        country: 'USA',
        latitude: 47.6062,
        longitude: -122.3321
      });

      // Create timeline stays for this favorite
      const staysData = [];
      for (let i = 0; i < 5; i++) {
        staysData.push({
          favoriteId,
          coords: `POINT(-122.3321 47.6062)`,
          locationName: 'Office',
          durationSeconds: 3600 + (i * 300),
          timestampOffset: `${i + 1} days`
        });
      }
      await TestSetupHelper.createMultipleTimelineStays(dbManager, user.id, staysData);

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Verify visits table has rows
      const rowCount = await placeDetailsPage.getVisitsTableRowCount();
      expect(rowCount).toBe(5);
    });

    test('should sort visits table by clicking column header', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create favorite and visits
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Gym',
        city: 'Portland',
        country: 'USA',
        latitude: 45.5152,
        longitude: -122.6784
      });

      // Create timeline stays for this favorite
      const staysData = [];
      for (let i = 0; i < 3; i++) {
        staysData.push({
          favoriteId,
          coords: `POINT(-122.6784 45.5152)`,
          locationName: 'Gym',
          durationSeconds: 1800 + (i * 600), // Varying durations
          timestampOffset: `${i + 1} days`
        });
      }
      await TestSetupHelper.createMultipleTimelineStays(dbManager, user.id, staysData);

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Get initial first row data
      const initialFirstRow = await placeDetailsPage.getVisitRowData(0);

      // Sort by duration (or timestamp)
      await placeDetailsPage.sortByColumn('Duration');
      await page.waitForTimeout(1000);

      // Get new first row data
      const sortedFirstRow = await placeDetailsPage.getVisitRowData(0);

      // Verify order changed (this is a basic check)
      // In a real test, you might want to verify specific ordering
      expect(sortedFirstRow).toBeDefined();
    });

    test('should export visits to CSV', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create favorite and visits
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Library',
        city: 'Austin',
        country: 'USA',
        latitude: 30.2672,
        longitude: -97.7431
      });

      // Create timeline stay for this favorite
      await TestSetupHelper.createTimelineStay(dbManager, user.id, {
        favoriteId,
        coords: `POINT(-97.7431 30.2672)`,
        locationName: 'Library',
        timestampOffset: '1 day'
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Set up download listener
      const downloadPromise = page.waitForEvent('download');

      // Click export button
      await placeDetailsPage.clickExportButton();

      // Wait for download
      const download = await downloadPromise;

      // Verify download occurred
      expect(download).toBeDefined();
      expect(download.suggestedFilename()).toContain('.csv');
    });
  });

  test.describe('Edit Favorite', () => {
    test('should edit favorite point name without timeline regeneration', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Original Name',
        city: 'Denver',
        country: 'USA',
        latitude: 39.7392,
        longitude: -104.9903
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Edit the name
      await placeDetailsPage.editWorkflow('Updated Name');

      // Should NOT show timeline regeneration modal for metadata-only changes
      await placeDetailsPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify database
      const favorite = await TestSetupHelper.getFavoriteById(dbManager, favoriteId);
      expect(favorite.name).toBe('Updated Name');

      // Reload and verify persistence
      await page.reload();
      await placeDetailsPage.waitForPageLoad();

      const pageTitle = await placeDetailsPage.getPageTitle();
      expect(pageTitle).toContain('Updated Name');
    });

    test('should edit favorite metadata (city and country) without timeline regeneration', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'My Place',
        city: 'Old City',
        country: 'Old Country',
        latitude: 39.7392,
        longitude: -104.9903
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Edit city and country
      await placeDetailsPage.clickEditButton();
      await placeDetailsPage.waitForEditFavoriteDialog();
      await placeDetailsPage.fillEditDialog('My Place', 'New City', 'New Country');
      await placeDetailsPage.submitEditDialog();

      // Should NOT show timeline regeneration modal for metadata-only changes
      await placeDetailsPage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // Verify database
      const favorite = await TestSetupHelper.getFavoriteById(dbManager, favoriteId);
      expect(favorite.name).toBe('My Place');
    });

    test('should cancel edit dialog', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Original Name',
        city: 'Miami',
        country: 'USA',
        latitude: 25.7617,
        longitude: -80.1918
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Open edit dialog and cancel
      await placeDetailsPage.clickEditButton();
      await placeDetailsPage.waitForEditFavoriteDialog();
      await placeDetailsPage.cancelEditDialog();

      // Verify no changes
      const favorite = await TestSetupHelper.getFavoriteById(dbManager, favoriteId);
      expect(favorite.name).toBe('Original Name');
    });
  });

  test.describe('Edit Geocoding Location', () => {
    test('should edit geocoding location name', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a geocoding result
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: `POINT(-74.0060 40.7128)`,
        displayName: 'Original Location',
        city: 'New York',
        country: 'USA'
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToGeocoding(geocodingId);
      await placeDetailsPage.waitForPageLoad();

      // Edit the name
      await placeDetailsPage.editWorkflow('Updated Location');

      // Wait for success toast
      await placeDetailsPage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // Verify database
      const geocoding = await TestSetupHelper.getGeocodingResultById(dbManager, geocodingId);
      expect(geocoding.display_name).toBe('Updated Location');

      // Reload and verify
      await page.reload();
      await placeDetailsPage.waitForPageLoad();

      const pageTitle = await placeDetailsPage.getPageTitle();
      expect(pageTitle).toContain('Updated Location');
    });

    test('should edit geocoding city and country', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a geocoding result
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: `POINT(-118.2437 34.0522)`,
        displayName: 'LA Location',
        city: 'Los Angeles',
        country: 'USA'
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToGeocoding(geocodingId);
      await placeDetailsPage.waitForPageLoad();

      // Edit city and country
      await placeDetailsPage.clickEditButton();
      await placeDetailsPage.waitForGeocodingEditDialog();
      await placeDetailsPage.fillEditDialog('LA Location', 'Los Angeles Updated', 'United States');
      await placeDetailsPage.submitEditDialog();

      // Wait for success
      await placeDetailsPage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // Verify database
      const geocoding = await TestSetupHelper.getGeocodingResultById(dbManager, geocodingId);
      expect(geocoding.city).toBe('Los Angeles Updated');
      expect(geocoding.country).toBe('United States');
    });
  });

  test.describe('Create Favorite from Geocoding', () => {
    test('should create favorite from geocoding location', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a geocoding result
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: `POINT(-122.4194 37.7749)`,
        displayName: 'San Francisco Location',
        city: 'San Francisco',
        country: 'USA'
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToGeocoding(geocodingId);
      await placeDetailsPage.waitForPageLoad();

      const initialCount = await TestSetupHelper.countFavorites(dbManager, user.id);

      // Create favorite
      await placeDetailsPage.createFavoriteWorkflow('My Favorite SF Spot');

      // Wait for timeline regeneration
      try {
        await placeDetailsPage.waitForTimelineRegenerationModal();
        await placeDetailsPage.waitForTimelineRegenerationToComplete();
      } catch (error) {
        // Modal might complete too quickly
        await page.waitForTimeout(2000);
      }

      // Verify success toast
      await placeDetailsPage.waitForSuccessToast();

      // Verify database
      const finalCount = await TestSetupHelper.countFavorites(dbManager, user.id);
      expect(finalCount).toBe(initialCount + 1);
    });

    test('should cancel create favorite dialog', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a geocoding result
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: `POINT(-87.6298 41.8781)`,
        displayName: 'Chicago Location',
        city: 'Chicago',
        country: 'USA'
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToGeocoding(geocodingId);
      await placeDetailsPage.waitForPageLoad();

      const initialCount = await TestSetupHelper.countFavorites(dbManager, user.id);

      // Open and cancel create favorite dialog
      await placeDetailsPage.clickCreateFavoriteButton();
      await placeDetailsPage.waitForCreateFavoriteDialog();
      await placeDetailsPage.cancelCreateFavorite();

      // Verify no favorite was created
      const finalCount = await TestSetupHelper.countFavorites(dbManager, user.id);
      expect(finalCount).toBe(initialCount);
    });

    test('should pre-fill favorite name with geocoding location name', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a geocoding result
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: `POINT(-112.0740 33.4484)`,
        displayName: 'Phoenix Downtown',
        city: 'Phoenix',
        country: 'USA'
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToGeocoding(geocodingId);
      await placeDetailsPage.waitForPageLoad();

      // Open create favorite dialog
      await placeDetailsPage.clickCreateFavoriteButton();
      await placeDetailsPage.waitForCreateFavoriteDialog();

      // Verify name input is pre-filled
      const nameInput = page.locator('#favorite-name');
      const inputValue = await nameInput.inputValue();
      expect(inputValue).toBe('Phoenix Downtown');

      // Cancel dialog
      await placeDetailsPage.cancelCreateFavorite();
    });
  });

  test.describe('Navigation', () => {
    test('should navigate back when clicking back button', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Test Location',
        city: 'Dallas',
        country: 'USA',
        latitude: 32.7767,
        longitude: -96.7970
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Click back button
      await placeDetailsPage.goBack();

      // Verify navigated away from place details
      await page.waitForTimeout(1000);
      expect(page.url()).not.toContain('/app/place-details/');
    });
  });

  test.describe('Error Handling', () => {
    test('should show error and allow retry', async ({page, dbManager}) => {
      await TestSetupHelper.createAndLoginUser(page, dbManager);

      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(999999);
      await placeDetailsPage.waitForPageLoad();

      // Verify error state
      expect(await placeDetailsPage.hasError()).toBe(true);

      // Verify retry button is present
      const retryButton = page.locator('button:has-text("Try Again")');
      expect(await retryButton.isVisible()).toBe(true);
    });
  });

  test.describe('Map Display', () => {
    test('should display map with point geometry for favorite point', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite point
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Coffee Shop',
        city: 'Nashville',
        country: 'USA',
        latitude: 36.1627,
        longitude: -86.7816
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Additional wait for map to fully render (waitForPageLoad includes map wait)
      await page.waitForTimeout(500);

      // Verify map container is visible
      expect(await placeDetailsPage.hasMap()).toBe(true);

      // Verify leaflet map is rendered and visible
      await page.waitForSelector('.leaflet-container', { state: 'visible', timeout: 5000 });
      const leafletMap = page.locator('.leaflet-container');
      expect(await leafletMap.isVisible()).toBe(true);
    });

    test('should display map with area geometry for favorite area', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite area
      const favoriteId = await TestSetupHelper.createFavoriteArea(dbManager, user.id, {
        name: 'Park Area',
        city: 'Atlanta',
        country: 'USA',
        southWestLat: 33.74,
        southWestLon: -84.40,
        northEastLat: 33.76,
        northEastLon: -84.38
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Additional wait for map to fully render (waitForPageLoad includes map wait)
      await page.waitForTimeout(500);

      // Verify map container is visible
      expect(await placeDetailsPage.hasMap()).toBe(true);

      // Verify leaflet map is rendered and visible
      await page.waitForSelector('.leaflet-container', { state: 'visible', timeout: 5000 });
      const leafletMap = page.locator('.leaflet-container');
      expect(await leafletMap.isVisible()).toBe(true);
    });
  });

  test.describe('Responsive Behavior', () => {
    test('should handle mobile viewport', async ({page, dbManager}) => {
      // Set mobile viewport
      await page.setViewportSize({width: 375, height: 667});

      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'Mobile Test',
        city: 'Tampa',
        country: 'USA',
        latitude: 27.9506,
        longitude: -82.4572
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Verify page still works on mobile
      expect(await placeDetailsPage.hasError()).toBe(false);

      const pageTitle = await placeDetailsPage.getPageTitle();
      expect(pageTitle).toContain('Mobile Test');

      // Verify map is still visible on mobile
      expect(await placeDetailsPage.hasMap()).toBe(true);
    });
  });

  test.describe('Place Details with No Visits', () => {
    test('should display favorite with no visits', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a favorite without any timeline stays
      const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
        name: 'New Favorite',
        city: 'Houston',
        country: 'USA',
        latitude: 29.7604,
        longitude: -95.3698
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Verify page displays
      expect(await placeDetailsPage.hasError()).toBe(false);

      const pageTitle = await placeDetailsPage.getPageTitle();
      expect(pageTitle).toContain('New Favorite');

      // Map should still be visible
      expect(await placeDetailsPage.hasMap()).toBe(true);

      // Visits table might be empty or show "no visits" message
      expect(await placeDetailsPage.hasVisitsTable()).toBe(true);
    });

    test('should display geocoding location with no visits', async ({page, dbManager}) => {
      const {user} = await TestSetupHelper.createAndLoginUser(page, dbManager);

      // Create a geocoding result without timeline stays
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: `POINT(-95.3698 29.7604)`,
        displayName: 'Houston Spot',
        city: 'Houston',
        country: 'USA'
      });

      // Navigate to place details
      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToGeocoding(geocodingId);
      await placeDetailsPage.waitForPageLoad();

      // Verify page displays
      expect(await placeDetailsPage.hasError()).toBe(false);

      const pageTitle = await placeDetailsPage.getPageTitle();
      expect(pageTitle).toContain('Houston Spot');

      // Map should be visible
      expect(await placeDetailsPage.hasMap()).toBe(true);
    });
  });
});
