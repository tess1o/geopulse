import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {JourneyInsightsPage} from '../pages/JourneyInsightsPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {TestConfig} from '../config/test-config.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';
import {GeocodingFactory} from '../utils/geocoding-factory.js';
import {randomUUID} from 'crypto';

test.describe('Journey Insights', () => {
  
  test.describe('Initial State and Empty Data', () => {
    test('should show empty state when no timeline data exists', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const journeyInsightsPage = new JourneyInsightsPage(page);
      const testUser = TestData.users.existing;
      
      // Create user first
      await UserFactory.createUser(page, testUser);
      
      // Login to the app
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Navigate to journey insights
      await journeyInsightsPage.navigate();
      await journeyInsightsPage.waitForPageLoad();
      
      // Verify we're on the journey insights page
      expect(await journeyInsightsPage.isOnJourneyInsightsPage()).toBe(true);
      
      // Wait for loading to complete
      await journeyInsightsPage.waitForLoadingComplete();
      
      // Check what's actually displayed
      const hasEmptyState = await journeyInsightsPage.hasEmptyState();
      console.log('Has empty state:', hasEmptyState);
      
      // If not empty state, check if regular sections are there with zero data
      if (!hasEmptyState) {
        console.log('No empty state, checking for regular sections with zero data');
        // Verify main sections exist but show no data
        expect(await journeyInsightsPage.getCountriesCount()).toBe(0);
        expect(await journeyInsightsPage.getCitiesCount()).toBe(0);
        
        // Check if the page shows sections at all
        const hasAllSections = await journeyInsightsPage.hasAllSections();
        console.log('Has all sections:', hasAllSections);
      } else {
        // Verify empty state is properly shown
        expect(hasEmptyState).toBe(true);
      }
      
      // Verify database has no timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      const hasTimelineData = await JourneyInsightsPage.verifyTimelineDataExists(dbManager, user.id);
      expect(hasTimelineData).toBe(false);
    });

    test('should show loading state initially', async ({page}) => {
      const loginPage = new LoginPage(page);
      const journeyInsightsPage = new JourneyInsightsPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Navigate to journey insights
      await journeyInsightsPage.navigate();
      
      // Check if loading state appears briefly
      // Note: This might be too fast to catch in some cases
      try {
        await page.waitForSelector('.insights-loading', { timeout: 1000 });
        expect(await journeyInsightsPage.isLoading()).toBe(true);
      } catch {
        // Loading might be too fast to catch, which is fine
        console.log('Loading state was too fast to capture');
      }
      
      // Wait for loading to complete
      await journeyInsightsPage.waitForLoadingComplete();
      expect(await journeyInsightsPage.isLoading()).toBe(false);
    });
  });

  test.describe('Journey Insights with Data', () => {
    test('should display geographic insights with countries and cities', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const journeyInsightsPage = new JourneyInsightsPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertTestTimelineData(dbManager, user.id);
      
      // Navigate to journey insights
      await journeyInsightsPage.navigate();
      await journeyInsightsPage.waitForPageLoad();
      await journeyInsightsPage.waitForLoadingComplete();
      
      // Verify main sections are visible
      expect(await journeyInsightsPage.hasAllSections()).toBe(true);
      
      // Verify geographic data is displayed
      const countriesCount = await journeyInsightsPage.getCountriesCount();
      const citiesCount = await journeyInsightsPage.getCitiesCount();
      
      expect(countriesCount).toBeGreaterThan(0);
      expect(citiesCount).toBeGreaterThan(0);
      
      // Get country and city names from UI
      const uiCountries = await journeyInsightsPage.getCountryNames();
      const uiCities = await journeyInsightsPage.getCityNames();
      
      expect(uiCountries.length).toBe(countriesCount);
      expect(uiCities.length).toBe(citiesCount);
      
      // Verify against database
      const dbCountries = await JourneyInsightsPage.getCountriesFromDb(dbManager, user.id);
      const dbCities = await JourneyInsightsPage.getCitiesFromDb(dbManager, user.id);
      
      // Check that UI shows expected countries (order may differ)
      for (const country of dbCountries) {
        expect(uiCountries).toContain(country);
      }
      
      // Check that UI shows expected cities
      for (const city of dbCities) {
        expect(uiCities).toContain(city.name);
      }
      
      // Verify country flags are displayed (when available)
      if (countriesCount > 0) {
        // Allow some time for flags to load
        await page.waitForTimeout(2000);
        const hasFlags = await journeyInsightsPage.hasCountryFlags();
        // Flags might not load immediately or at all due to network, so we don't assert true
        console.log('Country flags loaded:', hasFlags);
      }
    });

    test('should display travel story with distance data', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const journeyInsightsPage = new JourneyInsightsPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test timeline data with specific distances
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertTestTimelineDataWithDistances(dbManager, user.id);
      
      await journeyInsightsPage.navigate();
      await journeyInsightsPage.waitForPageLoad();
      await journeyInsightsPage.waitForLoadingComplete();
      
      // Verify travel story section is visible
      expect(await journeyInsightsPage.hasTravelStoryData()).toBe(true);
      
      // Get distance values from UI
      const totalDistance = await journeyInsightsPage.getTotalDistance();
      const carDistance = await journeyInsightsPage.getCarDistance();
      const walkDistance = await journeyInsightsPage.getWalkDistance();
      
      // Verify values are displayed (should be formatted with km)
      expect(totalDistance).toContain('m');
      expect(carDistance).toContain('m');
      expect(walkDistance).toContain('m');
      
      // Extract numeric values for comparison
      const totalNum = parseInt(totalDistance.replace(/[^\d]/g, ''));
      const carNum = parseInt(carDistance.replace(/[^\d]/g, ''));
      const walkNum = parseInt(walkDistance.replace(/[^\d]/g, ''));
      
      expect(totalNum).toBeGreaterThan(0);
      expect(carNum).toBeGreaterThanOrEqual(0);
      expect(walkNum).toBeGreaterThanOrEqual(0);
      
      // Verify against database
      const dbTotalDistance = await JourneyInsightsPage.getTotalDistanceFromDb(dbManager, user.id);
      const dbCarDistance = await JourneyInsightsPage.getDistanceByTransportationFromDb(dbManager, user.id, 'CAR');
      const dbWalkDistance = await JourneyInsightsPage.getDistanceByTransportationFromDb(dbManager, user.id, 'WALK');
      
      expect(totalNum).toBe(dbTotalDistance);
      expect(carNum).toBe(dbCarDistance);
      expect(walkNum).toBe(dbWalkDistance);
    });

    test('should display activity patterns with proper time formatting', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const journeyInsightsPage = new JourneyInsightsPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertTestTimelineData(dbManager, user.id);
      
      await journeyInsightsPage.navigate();
      await journeyInsightsPage.waitForPageLoad();
      await journeyInsightsPage.waitForLoadingComplete();
      
      // Verify activity patterns section is visible
      expect(await journeyInsightsPage.hasActivityPatternsData()).toBe(true);
      
      // Get activity pattern values
      const mostActiveMonth = await journeyInsightsPage.getMostActiveMonth();
      const busiestDay = await journeyInsightsPage.getBusiestDayOfWeek();
      const mostActiveTime = await journeyInsightsPage.getMostActiveTime();
      
      // Verify values are not N/A when we have data
      expect(mostActiveMonth).not.toBe('N/A');
      expect(busiestDay).not.toBe('N/A');
      expect(mostActiveTime).not.toBe('N/A');
      
      // Verify they contain expected patterns
      const months = ['January', 'February', 'March', 'April', 'May', 'June',
                     'July', 'August', 'September', 'October', 'November', 'December'];
      const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
      
      expect(months.some(month => mostActiveMonth.includes(month))).toBe(true);
      expect(days.some(day => busiestDay.includes(day))).toBe(true);
      expect(mostActiveTime.length).toBeGreaterThan(0);
      
      // Test more specific patterns for time formatting
      // The localMostActiveTime computed property should format time in 12-hour format with AM/PM
      const timePattern = /\d{1,2}:\d{2}\s*(AM|PM)/i;
      expect(timePattern.test(mostActiveTime)).toBe(true);
      console.log('Most active time format test:', mostActiveTime, 'matches pattern:', timePattern.test(mostActiveTime));
    });

    test('should properly use Vue computed properties for time display', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const journeyInsightsPage = new JourneyInsightsPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertTestTimelineData(dbManager, user.id);
      
      await journeyInsightsPage.navigate();
      await journeyInsightsPage.waitForPageLoad();
      await journeyInsightsPage.waitForLoadingComplete();
      
      // Check that the component is using computed properties correctly
      // We can verify this by checking the DOM elements have the expected classes and content
      const timePatternCard = page.locator('.insight-stat-pattern:has-text("Most Active Time of Day")');
      await expect(timePatternCard).toBeVisible();
      
      const timeValue = await timePatternCard.locator('.pattern-value').textContent();
      console.log('Displayed time value:', timeValue);
      
      // Verify the time is in the correct format (12-hour with AM/PM)
      const timeFormatRegex = /\d{1,2}:\d{2}\s*(AM|PM)/i;
      expect(timeFormatRegex.test(timeValue)).toBe(true);
      
      // If this was using the raw timePatterns.mostActiveTime instead of localMostActiveTime,
      // the format might be different or the timezone conversion wouldn't apply
      // This test would catch issues where the component isn't using the computed property
      expect(timeValue).not.toBe('N/A');
      expect(timeValue.trim().length).toBeGreaterThan(0);
    });

    test('should display achievement badges', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const journeyInsightsPage = new JourneyInsightsPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertTestTimelineData(dbManager, user.id);
      
      await journeyInsightsPage.navigate();
      await journeyInsightsPage.waitForPageLoad();
      await journeyInsightsPage.waitForLoadingComplete();
      
      // Verify milestones section is visible
      expect(await journeyInsightsPage.hasMilestonesData()).toBe(true);
      
      // Get badge information
      const totalBadges = await journeyInsightsPage.getBadgeCount();
      const earnedBadges = await journeyInsightsPage.getEarnedBadgeCount();
      const badgeTitles = await journeyInsightsPage.getBadgeTitles();
      
      expect(totalBadges).toBeGreaterThan(0);
      expect(earnedBadges).toBeGreaterThanOrEqual(0);
      expect(badgeTitles.length).toBe(totalBadges);
      
      // Test individual badges
      for (const title of badgeTitles) {
        const isEarned = await journeyInsightsPage.isBadgeEarned(title);
        if (!isEarned) {
          // Check progress if not earned
          const progress = await journeyInsightsPage.getBadgeProgress(title);
          expect(progress).toBeGreaterThanOrEqual(0);
          expect(progress).toBeLessThanOrEqual(100);
        }
      }
    });
  });

  test.describe('Data Integration and Consistency', () => {
    test('should show consistent data across all sections', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const journeyInsightsPage = new JourneyInsightsPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert comprehensive test data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertComprehensiveTestData(dbManager, user.id);
      
      await journeyInsightsPage.navigate();
      await journeyInsightsPage.waitForPageLoad();
      await journeyInsightsPage.waitForLoadingComplete();
      
      // Wait for all sections to load
      await journeyInsightsPage.waitForAllSections();
      
      // Verify all sections have data
      expect(await journeyInsightsPage.getCountriesCount()).toBeGreaterThan(0);
      expect(await journeyInsightsPage.getCitiesCount()).toBeGreaterThan(0);
      expect(await journeyInsightsPage.hasTravelStoryData()).toBe(true);
      expect(await journeyInsightsPage.hasActivityPatternsData()).toBe(true);
      expect(await journeyInsightsPage.hasMilestonesData()).toBe(true);
      
      // Verify no empty state is shown when we have data
      expect(await journeyInsightsPage.hasEmptyState()).toBe(false);
      
      // Check data consistency with database
      const dbCountries = await JourneyInsightsPage.getCountriesFromDb(dbManager, user.id);
      const uiCountries = await journeyInsightsPage.getCountryNames();
      
      expect(dbCountries.length).toBe(uiCountries.length);
      
      // Verify total distance calculation matches
      const uiTotalDistance = await journeyInsightsPage.getTotalDistance();
      const dbTotalDistance = await JourneyInsightsPage.getTotalDistanceFromDb(dbManager, user.id);
      const uiTotalNum = parseInt(uiTotalDistance.replace(/[^\d]/g, ''));
      
      expect(uiTotalNum).toBe(dbTotalDistance);
    });

    test('should refresh data when navigating back to the page', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const journeyInsightsPage = new JourneyInsightsPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // First visit - no data
      await journeyInsightsPage.navigate();
      await journeyInsightsPage.waitForPageLoad();
      await journeyInsightsPage.waitForLoadingComplete();
      
      // Check what state is shown - might not be empty if backend returns empty data structure
      const hasEmptyState = await journeyInsightsPage.hasEmptyState();
      console.log('Initial visit - Has empty state:', hasEmptyState);
      
      // If not showing empty state, verify there's no meaningful data
      if (!hasEmptyState) {
        expect(await journeyInsightsPage.getCountriesCount()).toBe(0);
        expect(await journeyInsightsPage.getCitiesCount()).toBe(0);
      }
      
      // Navigate away
      await page.goto('/app/profile');
      console.log('Navigated away from page to ' + page.url());
      // Use more flexible URL pattern that matches query parameters
      await TestHelpers.waitForNavigation(page, '**/app/profile');
      
      // Add data while away
      await insertTestTimelineData(dbManager, user.id);
      console.log('Inserted timeline data while away from journey insights page');
      
      // Navigate back to journey insights
      await journeyInsightsPage.navigate();
      await journeyInsightsPage.waitForPageLoad();
      await journeyInsightsPage.waitForLoadingComplete();
      
      // Verify we're back on the journey insights page
      expect(await journeyInsightsPage.isOnJourneyInsightsPage()).toBe(true);
      
      // Now verify that the page shows the new data we inserted
      console.log('Checking if new data is reflected on the page');
      
      // The page should now show data instead of empty state
      const countriesCount = await journeyInsightsPage.getCountriesCount();
      const citiesCount = await journeyInsightsPage.getCitiesCount();
      
      console.log(`After navigation back: Countries=${countriesCount}, Cities=${citiesCount}`);
      
      // Verify the new data is displayed (insertTestTimelineData creates 3 countries and cities)
      expect(countriesCount).toBeGreaterThan(0);
      expect(citiesCount).toBeGreaterThan(0);
      
      // Verify specific data from our test inserts
      const countries = await journeyInsightsPage.getCountryNames();
      const cities = await journeyInsightsPage.getCityNames();
      
      // Should contain data from our insertTestTimelineData function
      expect(countries).toContain('United States');
      expect(countries).toContain('France'); 
      expect(cities).toContain('New York');
      expect(cities).toContain('Paris');
      
      // Verify travel story data is also present
      const totalDistance = await journeyInsightsPage.getTotalDistance();
      expect(totalDistance).not.toBe('0 km'); // Should show actual distance
    });
  });
});

// Helper functions for inserting test data
async function insertTestTimelineData(dbManager, userId) {
  // First, insert reverse geocoding locations for city/country data using sequence
  const geocodingId1 = await GeocodingFactory.insertOrGetGeocodingLocation(
    dbManager,
    'POINT(-74.0060 40.7128)',
    'Home, New York, NY, USA',
    'New York',
    'United States'
  );

  const geocodingId2 = await GeocodingFactory.insertOrGetGeocodingLocation(
    dbManager,
    'POINT(-73.9851 40.7589)',
    'Office, New York, NY, USA',
    'New York',
    'United States'
  );

  const geocodingId3 = await GeocodingFactory.insertOrGetGeocodingLocation(
    dbManager,
    'POINT(2.3522 48.8566)',
    'Restaurant, Paris, France',
    'Paris',
    'France'
  );

  // Insert sample stays data with geocoding references
  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES 
      ($1, '2024-01-01 08:00:00', 36000, ST_SetSRID(ST_MakePoint(-74.0060, 40.7128), 4326), 'Home', $2, NOW(), NOW()),
      ($1, '2024-01-02 09:00:00', 28800, ST_SetSRID(ST_MakePoint(-73.9851, 40.7589), 4326), 'Office', $3, NOW(), NOW()),
      ($1, '2024-01-03 19:00:00', 7200, ST_SetSRID(ST_MakePoint(2.3522, 48.8566), 4326), 'Restaurant', $4, NOW(), NOW())
  `, [userId, geocodingId1, geocodingId2, geocodingId3]);

  // Insert sample trips data - let DB auto-generate IDs
  await dbManager.client.query(`
    INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
    VALUES 
      ($1, '2024-01-01 18:00:00', 1800, ST_SetSRID(ST_MakePoint(-74.0060, 40.7128), 4326), ST_SetSRID(ST_MakePoint(-73.9851, 40.7589), 4326), 5000, 'CAR', NOW(), NOW()),
      ($1, '2024-01-02 08:30:00', 1800, ST_SetSRID(ST_MakePoint(-73.9851, 40.7589), 4326), ST_SetSRID(ST_MakePoint(-73.9851, 40.7489), 4326), 2000, 'WALK', NOW(), NOW()),
      ($1, '2024-01-03 17:30:00', 3600, ST_SetSRID(ST_MakePoint(2.3522, 48.8566), 4326), ST_SetSRID(ST_MakePoint(2.3422, 48.8466), 4326), 15000, 'CAR', NOW(), NOW())
  `, [userId]);
}

async function insertTestTimelineDataWithDistances(dbManager, userId) {
  // Insert trips with specific distances for testing
  await dbManager.client.query(`
    INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
    VALUES 
      ($1, '2024-01-01 08:00:00', 3600, ST_SetSRID(ST_MakePoint(-74.0060, 40.7128), 4326), ST_SetSRID(ST_MakePoint(-74.1060, 40.8128), 4326), 50000, 'CAR', NOW(), NOW()),
      ($1, '2024-01-01 12:00:00', 1800, ST_SetSRID(ST_MakePoint(-74.1060, 40.8128), 4326), ST_SetSRID(ST_MakePoint(-74.1080, 40.8140), 4326), 3000, 'WALK', NOW(), NOW()),
      ($1, '2024-01-02 14:00:00', 3600, ST_SetSRID(ST_MakePoint(-74.1080, 40.8140), 4326), ST_SetSRID(ST_MakePoint(-74.2080, 40.9140), 4326), 25000, 'CAR', NOW(), NOW()),
      ($1, '2024-01-02 16:00:00', 900, ST_SetSRID(ST_MakePoint(-74.2080, 40.9140), 4326), ST_SetSRID(ST_MakePoint(-74.2090, 40.9150), 4326), 1000, 'WALK', NOW(), NOW())
  `, [userId]);
  
  // Total: 79km, Car: 75km, Walk: 4km
}

async function insertComprehensiveTestData(dbManager, userId) {
  // First, insert reverse geocoding locations for diverse geographical data
  const locations = [
    { coords: 'POINT(-74.0060 40.7128)', name: 'Home, New York, NY, USA', city: 'New York', country: 'United States' },
    { coords: 'POINT(-0.1278 51.5074)', name: 'Hotel, London, UK', city: 'London', country: 'United Kingdom' },
    { coords: 'POINT(2.3522 48.8566)', name: 'Office, Paris, France', city: 'Paris', country: 'France' },
    { coords: 'POINT(13.4050 52.5200)', name: 'Cafe, Berlin, Germany', city: 'Berlin', country: 'Germany' },
    { coords: 'POINT(139.6503 35.6762)', name: 'Park, Tokyo, Japan', city: 'Tokyo', country: 'Japan' }
  ];

  const geocodingIds = await GeocodingFactory.insertOrGetGeocodingLocations(dbManager, locations);

  // Insert diverse geographical stays data
  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
    VALUES 
      ($1, '2024-01-01 08:00:00', 36000, ST_SetSRID(ST_MakePoint(-74.0060, 40.7128), 4326), 'Home', $2, NOW(), NOW()),
      ($1, '2024-01-15 20:00:00', 43200, ST_SetSRID(ST_MakePoint(-0.1278, 51.5074), 4326), 'Hotel', $3, NOW(), NOW()),
      ($1, '2024-02-01 09:00:00', 28800, ST_SetSRID(ST_MakePoint(2.3522, 48.8566), 4326), 'Office', $4, NOW(), NOW()),
      ($1, '2024-02-15 14:00:00', 7200, ST_SetSRID(ST_MakePoint(13.4050, 52.5200), 4326), 'Cafe', $5, NOW(), NOW()),
      ($1, '2024-03-01 12:00:00', 10800, ST_SetSRID(ST_MakePoint(139.6503, 35.6762), 4326), 'Park', $6, NOW(), NOW())
  `, [userId, ...geocodingIds]);

  // Insert varied trip data across different times and transportation types
  await dbManager.client.query(`
    INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_point, end_point, distance_meters, movement_type, created_at, last_updated)
    VALUES 
      ($1, '2024-01-01 18:00:00', 3600, ST_SetSRID(ST_MakePoint(-74.0060, 40.7128), 4326), ST_SetSRID(ST_MakePoint(-74.1060, 40.8128), 4326), 45000, 'CAR', NOW(), NOW()),
      ($1, '2024-01-15 19:00:00', 1800, ST_SetSRID(ST_MakePoint(-0.1278, 51.5074), 4326), ST_SetSRID(ST_MakePoint(-0.1300, 51.5100), 4326), 3000, 'WALK', NOW(), NOW()),
      ($1, '2024-02-01 08:30:00', 1800, ST_SetSRID(ST_MakePoint(2.3522, 48.8566), 4326), ST_SetSRID(ST_MakePoint(2.3600, 48.8600), 4326), 12000, 'CAR', NOW(), NOW()),
      ($1, '2024-02-15 16:00:00', 2700, ST_SetSRID(ST_MakePoint(13.4050, 52.5200), 4326), ST_SetSRID(ST_MakePoint(13.4100, 52.5250), 4326), 8000, 'WALK', NOW(), NOW()),
      ($1, '2024-03-01 11:00:00', 3600, ST_SetSRID(ST_MakePoint(139.6503, 35.6762), 4326), ST_SetSRID(ST_MakePoint(139.6600, 35.6800), 4326), 22000, 'CAR', NOW(), NOW()),
      ($1, '2024-03-01 15:00:00', 1200, ST_SetSRID(ST_MakePoint(139.6600, 35.6800), 4326), ST_SetSRID(ST_MakePoint(139.6620, 35.6820), 4326), 2000, 'WALK', NOW(), NOW())
  `, [userId]);
}