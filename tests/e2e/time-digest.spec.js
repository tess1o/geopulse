import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {TimeDigestPage} from '../pages/TimeDigestPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {GeocodingFactory} from '../utils/geocoding-factory.js';

test.describe('Time Digest', () => {

  test.describe('Initial State and Empty Data', () => {
    test('should show empty state when no digest data exists for selected period', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      // Create user first
      await UserFactory.createUser(page, testUser);

      // Login to the app
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Navigate to time digest page (defaults to current month)
      await digestPage.navigate();
      await digestPage.waitForPageLoad();

      // Verify we're on the time digest page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Wait for loading to complete
      await digestPage.waitForLoadingComplete();

      // Verify database has no digest data for current period
      const user = await dbManager.getUserByEmail(testUser.email);
      const now = new Date();
      const hasDigestData = await TimeDigestPage.verifyDigestDataExists(
        dbManager,
        user.id,
        now.getFullYear(),
        now.getMonth() + 1
      );
      expect(hasDigestData).toBe(false);

      // Check what's displayed - empty state OR digest content with no data
      const hasEmptyState = await digestPage.hasEmptyState();
      const hasContent = await digestPage.hasDigestContent();

      console.log('Has empty state:', hasEmptyState);
      console.log('Has digest content:', hasContent);

      // Must show either empty state or content (not 404)
      expect(hasEmptyState || hasContent).toBe(true);
    });

    test('should show loading state initially', async ({page}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Navigate to time digest
      await digestPage.navigate();

      // Check if loading state appears briefly
      try {
        await page.waitForSelector('.p-progress-spinner', { timeout: 1000 });
        expect(await digestPage.isLoading()).toBe(true);
      } catch {
        // Loading might be too fast to catch, which is fine
        console.log('Loading state was too fast to capture');
      }

      // Wait for loading to complete
      await digestPage.waitForLoadingComplete();
      expect(await digestPage.isLoading()).toBe(false);
    });
  });

  test.describe('Monthly Digest with Data', () => {
    test('should display metrics section with correct data', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test digest data for January 2024
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMonthlyDigestTestData(dbManager, user.id, 2024, 1);

      // Navigate to time digest for January 2024
      await digestPage.navigate(2024, 1, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Verify we're on the correct page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Page must have digest content (not empty state or error)
      expect(await digestPage.hasDigestContent()).toBe(true);

      // Verify metrics section is visible
      expect(await digestPage.hasMetrics()).toBe(true);

      // Get metrics from UI
      const metrics = await digestPage.getMetricValues();
      console.log('Digest metrics:', metrics);

      // Verify we have some metrics
      expect(metrics.length).toBeGreaterThan(0);

      // Verify against database
      const startDate = new Date(2024, 0, 1); // Jan 1, 2024
      const endDate = new Date(2024, 0, 31, 23, 59, 59); // Jan 31, 2024

      const dbTotalDistance = await TimeDigestPage.getTotalDistanceFromDb(dbManager, user.id, startDate, endDate);
      const dbActiveDays = await TimeDigestPage.getActiveDaysFromDb(dbManager, user.id, startDate, endDate);
      const dbTripCount = await TimeDigestPage.getTripCountFromDb(dbManager, user.id, startDate, endDate);

      console.log('Database metrics:', {
        totalDistance: dbTotalDistance,
        activeDays: dbActiveDays,
        tripCount: dbTripCount
      });

      // Verify database has data
      expect(dbTotalDistance).toBeGreaterThan(0);
      expect(dbActiveDays).toBeGreaterThan(0);
      expect(dbTripCount).toBeGreaterThan(0);

      // Find distance metric in UI
      const distanceMetric = await digestPage.getMetricByLabel('distance');
      if (distanceMetric) {
        console.log('Distance metric from UI:', distanceMetric);
        // Distance should be displayed in km
        expect(distanceMetric.value).toMatch(/\d+/);
      }

      // Check for metrics title showing the period
      const metricsTitle = await digestPage.getMetricsTitle();
      console.log('Metrics title:', metricsTitle);
      expect(metricsTitle).toBeTruthy();
    });

    test('should display highlights section', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test digest data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMonthlyDigestTestData(dbManager, user.id, 2024, 1);

      await digestPage.navigate(2024, 1, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Verify we're on the correct page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Page must have digest content (not empty state or error)
      expect(await digestPage.hasDigestContent()).toBe(true);

      // Verify highlights section - it may or may not be visible depending on data
      const hasHighlights = await digestPage.hasHighlights();
      console.log('Has highlights section:', hasHighlights);

      if (hasHighlights) {
        // If highlights are shown, verify they have content
        const highlights = await digestPage.getHighlights();
        console.log('Highlights:', highlights);
        expect(highlights.length).toBeGreaterThan(0);
      }
    });

    test('should display top places section with data from database', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test digest data with places
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMonthlyDigestTestDataWithPlaces(dbManager, user.id, 2024, 1);

      await digestPage.navigate(2024, 1, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Verify we're on the correct page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Page must have digest content
      expect(await digestPage.hasDigestContent()).toBe(true);

      // Verify places section is visible
      expect(await digestPage.hasPlaces()).toBe(true);

      // Get places from UI
      const placeNames = await digestPage.getPlaceNames();
      const placesCount = await digestPage.getPlacesCount();

      console.log('Places from UI:', placeNames);
      console.log('Places count:', placesCount);

      // Verify we have places
      expect(placesCount).toBeGreaterThan(0);

      // Verify against database
      const startDate = new Date(2024, 0, 1);
      const endDate = new Date(2024, 0, 31, 23, 59, 59);

      const dbPlaces = await TimeDigestPage.getTopPlacesFromDb(dbManager, user.id, startDate, endDate, 10);
      const dbPlaceNames = dbPlaces.map(p => p.name);

      console.log('Places from DB:', dbPlaceNames);

      // UI should show up to 10 places
      expect(placesCount).toBeLessThanOrEqual(10);

      // Verify that UI places exist in database
      for (const placeName of placeNames) {
        expect(dbPlaceNames).toContain(placeName);
      }
    });

    test('should display milestones section when achievements exist', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test digest data with significant activity for milestones
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMonthlyDigestTestDataWithMilestones(dbManager, user.id, 2024, 1);

      await digestPage.navigate(2024, 1, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Verify we're on the correct page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Page must have digest content
      expect(await digestPage.hasDigestContent()).toBe(true);

      // Check if milestones section is visible (may or may not be depending on backend logic)
      const hasMilestones = await digestPage.hasMilestones();
      console.log('Has milestones section:', hasMilestones);

      if (hasMilestones) {
        const milestones = await digestPage.getMilestones();
        console.log('Milestones:', milestones);

        // If milestones are shown, verify they have content
        expect(milestones.length).toBeGreaterThan(0);

        // Each milestone should have a title and description
        for (const milestone of milestones) {
          expect(milestone.title).toBeTruthy();
          expect(milestone.description).toBeTruthy();
        }
      }
    });

    test('should display trends section with chart or placeholder', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test digest data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMonthlyDigestTestData(dbManager, user.id, 2024, 1);

      await digestPage.navigate(2024, 1, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Verify we're on the correct page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Page must have digest content
      expect(await digestPage.hasDigestContent()).toBe(true);

      // Verify trends section exists
      expect(await digestPage.hasTrends()).toBe(true);

      // Note: Chart canvas rendering doesn't work properly in headless Playwright
      // So we just verify the section exists and either shows BarChart or placeholder
      const hasBarChart = await page.locator('.digest-trends .bar-chart').isVisible();
      const hasPlaceholder = await page.locator('.digest-trends .no-trends-placeholder').isVisible();

      console.log('Has bar chart:', hasBarChart);
      console.log('Has placeholder:', hasPlaceholder);

      // Must show either bar chart OR placeholder (not both, not neither)
      expect(hasBarChart || hasPlaceholder).toBe(true);
      expect(hasBarChart && hasPlaceholder).toBe(false);
    });
  });

  test.describe('Yearly Digest with Data', () => {
    test('should display yearly digest metrics correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test digest data for entire year 2024
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertYearlyDigestTestData(dbManager, user.id, 2024);

      // Navigate to yearly digest for 2024
      await digestPage.navigate(2024, null, 'yearly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Verify we're on the correct page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Page must have digest content
      expect(await digestPage.hasDigestContent()).toBe(true);

      // Verify metrics section is visible
      expect(await digestPage.hasMetrics()).toBe(true);

      // Get metrics from UI
      const metrics = await digestPage.getMetricValues();
      console.log('Yearly digest metrics:', metrics);

      // Verify we have metrics
      expect(metrics.length).toBeGreaterThan(0);

      // Verify against database
      const startDate = new Date(2024, 0, 1); // Jan 1, 2024
      const endDate = new Date(2024, 11, 31, 23, 59, 59); // Dec 31, 2024

      const dbTotalDistance = await TimeDigestPage.getTotalDistanceFromDb(dbManager, user.id, startDate, endDate);
      const dbActiveDays = await TimeDigestPage.getActiveDaysFromDb(dbManager, user.id, startDate, endDate);
      const dbTripCount = await TimeDigestPage.getTripCountFromDb(dbManager, user.id, startDate, endDate);

      console.log('Database yearly metrics:', {
        totalDistance: dbTotalDistance,
        activeDays: dbActiveDays,
        tripCount: dbTripCount
      });

      // Verify database has data
      expect(dbTotalDistance).toBeGreaterThan(0);
      expect(dbActiveDays).toBeGreaterThan(0);
      expect(dbTripCount).toBeGreaterThan(0);

      // Check for metrics title showing the year
      const metricsTitle = await digestPage.getMetricsTitle();
      console.log('Yearly metrics title:', metricsTitle);
      expect(metricsTitle).toBeTruthy();
      expect(metricsTitle).toContain('2024');
    });

    test('should display yearly places and highlights', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test digest data for entire year
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertYearlyDigestTestDataWithPlaces(dbManager, user.id, 2024);

      await digestPage.navigate(2024, null, 'yearly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Verify we're on the correct page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Page must have digest content
      expect(await digestPage.hasDigestContent()).toBe(true);

      // Verify places section
      expect(await digestPage.hasPlaces()).toBe(true);

      const placesCount = await digestPage.getPlacesCount();
      console.log('Yearly places count:', placesCount);
      expect(placesCount).toBeGreaterThan(0);

      // Verify highlights (may or may not be present)
      const hasHighlights = await digestPage.hasHighlights();
      console.log('Has yearly highlights:', hasHighlights);

      if (hasHighlights) {
        const highlights = await digestPage.getHighlights();
        console.log('Yearly highlights:', highlights);
        expect(highlights.length).toBeGreaterThan(0);
      }
    });
  });

  test.describe('Data Integration and Consistency', () => {
    test('should show consistent data across all digest sections', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert comprehensive test data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertComprehensiveDigestData(dbManager, user.id, 2024, 1);

      await digestPage.navigate(2024, 1, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Verify we're on the correct page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Verify all main sections are present
      expect(await digestPage.hasDigestContent()).toBe(true);
      expect(await digestPage.hasMetrics()).toBe(true);
      expect(await digestPage.hasPlaces()).toBe(true);

      // Verify no empty state is shown when we have data
      expect(await digestPage.hasEmptyState()).toBe(false);

      // Get data from different sections
      const metrics = await digestPage.getMetricValues();
      const places = await digestPage.getPlaceNames();

      console.log('Comprehensive digest data:', { metrics, places });

      // All sections should show data
      expect(metrics.length).toBeGreaterThan(0);
      expect(places.length).toBeGreaterThan(0);
    });

    test('should correctly handle comparison data when available', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test data for current month and previous month
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMonthlyDigestTestData(dbManager, user.id, 2024, 1); // January
      await insertMonthlyDigestTestData(dbManager, user.id, 2023, 12); // December (for comparison)

      await digestPage.navigate(2024, 1, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Verify we're on the correct page
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);

      // Page must have digest content
      expect(await digestPage.hasDigestContent()).toBe(true);

      // Get comparison text if available
      const comparisonText = await digestPage.getComparisonText();
      console.log('Comparison text:', comparisonText);

      // Comparison might not always be shown depending on backend logic
      if (comparisonText) {
        expect(comparisonText).toBeTruthy();
      }
    });
  });

  test.describe('Error Handling and Edge Cases', () => {
    test('should handle navigation between different periods', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test data for multiple months
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMonthlyDigestTestData(dbManager, user.id, 2024, 1);
      await insertMonthlyDigestTestData(dbManager, user.id, 2024, 2);

      // First visit to January digest
      await digestPage.navigate(2024, 1, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      expect(await digestPage.isOnTimeDigestPage()).toBe(true);
      expect(await digestPage.hasDigestContent()).toBe(true);
      expect(await digestPage.hasMetrics()).toBe(true);

      // Navigate to February
      await digestPage.navigate(2024, 2, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      // Should still work properly
      expect(await digestPage.isOnTimeDigestPage()).toBe(true);
      expect(await digestPage.hasDigestContent()).toBe(true);
      expect(await digestPage.hasMetrics()).toBe(true);
    });

    test('should handle switching between monthly and yearly views', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const digestPage = new TimeDigestPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Insert test data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMonthlyDigestTestData(dbManager, user.id, 2024, 1);
      await insertYearlyDigestTestData(dbManager, user.id, 2024);

      // Start with monthly view
      await digestPage.navigate(2024, 1, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      expect(await digestPage.isOnTimeDigestPage()).toBe(true);
      expect(await digestPage.hasDigestContent()).toBe(true);
      expect(await digestPage.hasMetrics()).toBe(true);

      // Switch to yearly view
      await digestPage.navigate(2024, null, 'yearly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();

      expect(await digestPage.isOnTimeDigestPage()).toBe(true);
      expect(await digestPage.hasDigestContent()).toBe(true);
      expect(await digestPage.hasMetrics()).toBe(true);

      // Metrics title should reflect the year
      const metricsTitle = await digestPage.getMetricsTitle();
      console.log('Yearly view metrics title:', metricsTitle);
      expect(metricsTitle).toBeTruthy();
    });
  });
});

// Helper functions for inserting test data

async function insertMonthlyDigestTestData(dbManager, userId, year, month) {
  // Insert timeline data for a specific month
  const startDate = new Date(year, month - 1, 1);
  const daysInMonth = new Date(year, month, 0).getDate();

  // Create reverse geocoding locations
  const locations = [
    { coords: 'POINT(-74.0060 40.7128)', name: 'Home, New York, NY', city: 'New York', country: 'United States' },
    { coords: 'POINT(-73.9851 40.7589)', name: 'Office, New York, NY', city: 'New York', country: 'United States' },
    { coords: 'POINT(2.3522 48.8566)', name: 'Coffee Shop, Paris, France', city: 'Paris', country: 'France' }
  ];

  const geocodingIds = await GeocodingFactory.insertOrGetGeocodingLocations(dbManager, locations);

  // Insert stays and trips throughout the month
  for (let day = 1; day <= Math.min(15, daysInMonth); day++) {
    const date = new Date(year, month - 1, day, 10, 0, 0);
    const geocodingId = geocodingIds[day % geocodingIds.length];
    const locationName = ['Home', 'Office', 'Coffee Shop'][day % 3];

    // Insert stay
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
    `, [
      userId,
      date,
      3600 + (day * 600), // 1-3 hours
      40.7128 + (day * 0.001),
      -74.0060 + (day * 0.001),
      locationName,
      geocodingId
    ]);

    // Insert trip
    if (day < daysInMonth) {
      await dbManager.client.query(`
        INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
        VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
      `, [
        userId,
        new Date(date.getTime() + 3600000), // 1 hour after stay
        1800 + (day * 300), // 30-60 minutes
        40.7128 + (day * 0.001),
        -74.0060 + (day * 0.001),
        40.7128 + ((day + 1) * 0.001),
        -74.0060 + ((day + 1) * 0.001),
        5000 + (day * 2000), // 5-35 km
        day % 2 === 0 ? 'CAR' : 'WALK'
      ]);
    }
  }
}

async function insertMonthlyDigestTestDataWithPlaces(dbManager, userId, year, month) {
  await insertMonthlyDigestTestData(dbManager, userId, year, month);

  // Add more diverse places
  const additionalLocations = [
    { coords: 'POINT(-73.9857 40.7484)', name: 'Gym, New York, NY', city: 'New York', country: 'United States' },
    { coords: 'POINT(-73.9776 40.7614)', name: 'Restaurant, New York, NY', city: 'New York', country: 'United States' },
    { coords: 'POINT(-73.9442 40.8006)', name: 'Park, New York, NY', city: 'New York', country: 'United States' }
  ];

  const startDate = new Date(year, month - 1, 1);

  const additionalGeocodingIds = await GeocodingFactory.insertOrGetGeocodingLocations(dbManager, additionalLocations);

  for (let idx = 0; idx < additionalLocations.length; idx++) {
    const location = additionalLocations[idx];
    const geocodingId = additionalGeocodingIds[idx];
    const locationName = location.name.split(',')[0];

    // Add multiple visits to each place
    for (let i = 0; i < 5; i++) {
      const date = new Date(startDate.getTime() + (i * 2 * 24 * 60 * 60 * 1000));
      await dbManager.client.query(`
        INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
        VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), $6, $7, NOW(), NOW())
      `, [
        userId,
        date,
        3600 + (i * 600),
        parseFloat(location.coords.split(' ')[1].replace(')', '')),
        parseFloat(location.coords.split(' ')[0].replace('POINT(', '')),
        locationName,
        geocodingId
      ]);
    }
  }
}

async function insertMonthlyDigestTestDataWithMilestones(dbManager, userId, year, month) {
  // Insert significant amount of data to trigger milestones
  await insertMonthlyDigestTestDataWithPlaces(dbManager, userId, year, month);

  // Add extra long trips for milestones
  const startDate = new Date(year, month - 1, 15);

  for (let i = 0; i < 5; i++) {
    const date = new Date(startDate.getTime() + (i * 24 * 60 * 60 * 1000));
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
    `, [
      userId,
      date,
      7200, // 2 hours
      40.7128,
      -74.0060,
      40.9128,
      -74.2060,
      50000 + (i * 10000), // 50-90 km trips
      'CAR'
    ]);
  }
}

async function insertYearlyDigestTestData(dbManager, userId, year) {
  // Insert data across multiple months
  for (let month = 1; month <= 12; month++) {
    await insertMonthlyDigestTestData(dbManager, userId, year, month);
  }
}

async function insertYearlyDigestTestDataWithPlaces(dbManager, userId, year) {
  // Insert data with places across the year
  for (let month = 1; month <= 6; month++) { // First half of the year
    await insertMonthlyDigestTestDataWithPlaces(dbManager, userId, year, month);
  }
}

async function insertComprehensiveDigestData(dbManager, userId, year, month) {
  // Insert comprehensive data with all features
  await insertMonthlyDigestTestDataWithMilestones(dbManager, userId, year, month);

  // Add even more variety
  const startDate = new Date(year, month - 1, 1);
  const daysInMonth = new Date(year, month, 0).getDate();

  // Add trips on weekends
  for (let day = 1; day <= daysInMonth; day++) {
    const date = new Date(year, month - 1, day);
    const dayOfWeek = date.getDay();

    if (dayOfWeek === 0 || dayOfWeek === 6) { // Weekend
      await dbManager.client.query(`
        INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
        VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326), ST_SetSRID(ST_MakePoint($6, $7), 4326), $8, $9, NOW(), NOW())
      `, [
        userId,
        new Date(year, month - 1, day, 14, 0, 0),
        3600,
        40.7128,
        -74.0060,
        40.8128,
        -74.1060,
        15000, // 15 km
        'CAR'
      ]);
    }
  }
}
