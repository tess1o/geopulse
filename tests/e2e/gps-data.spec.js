import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {GpsDataPage} from '../pages/GpsDataPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {GpsDataFactory} from '../utils/gps-data-factory.js';
import {AppNavigation} from "../pages/AppNavigation.js";

test.describe('GPS Data Page', () => {
  
  test.describe('Basic GPS Data Viewing', () => {
    test('should display GPS data for authenticated user', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user and GPS test data
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
      await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);
      
      // Login and navigate to GPS data page
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Verify we're on the GPS data page
      expect(await gpsDataPage.isOnGpsDataPage()).toBe(true);
      
      // Verify summary statistics are displayed
      const stats = await gpsDataPage.getSummaryStats();
      expect(stats.totalPoints).toBeGreaterThan(0);
      expect(stats.totalPoints).toBe(gpsTestData.allPoints.length);
      
      // Verify GPS data is displayed in table
      expect(await gpsDataPage.hasGpsData()).toBe(true);
      
      const displayedCount = await gpsDataPage.getDisplayedPointsCount();
      expect(displayedCount).toBeGreaterThan(0);
      expect(displayedCount).toBeLessThanOrEqual(50); // Default page size
      
      // Verify export button is enabled
      expect(await gpsDataPage.isExportButtonEnabled()).toBe(true);
    });

    test('should show empty state when user has no GPS data', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user without GPS data
      await UserFactory.createUser(page, testUser);
      
      // Login and navigate to GPS data page
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Verify empty state
      expect(await gpsDataPage.hasGpsData()).toBe(false);
      
      const stats = await gpsDataPage.getSummaryStats();
      expect(stats.totalPoints).toBe(0);
      
      // Verify export button is disabled
      expect(await gpsDataPage.isExportButtonEnabled()).toBe(false);
    });

    test('should display GPS data with correct details', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user and GPS test data
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // Create a smaller dataset for detailed verification
      const singleDayData = GpsDataFactory.generateTestData(user.id, 'test-device').byDate.august10.slice(0, 5);
      await GpsDataFactory.insertGpsData(dbManager, singleDayData);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Get displayed GPS points
      const displayedPoints = await gpsDataPage.getDisplayedGpsPoints();
      expect(displayedPoints.length).toBe(5);
      
      // Verify data structure and content
      for (const point of displayedPoints) {
        expect(point).toHaveProperty('timestamp');
        expect(point).toHaveProperty('coordinates');
        expect(point.coordinates).toHaveProperty('lat');
        expect(point.coordinates).toHaveProperty('lng');
        expect(typeof point.coordinates.lat).toBe('number');
        expect(typeof point.coordinates.lng).toBe('number');
        expect(point).toHaveProperty('source');
        expect(point.source).toBe('OWNTRACKS');
      }
    });

    test('should display correct summary statistics', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user and GPS test data with known timestamps
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // Generate test data with predictable dates
      const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
      await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Get summary statistics
      const stats = await gpsDataPage.getSummaryStats();
      
      // Verify total points count
      expect(stats.totalPoints).toBe(gpsTestData.allPoints.length);
      expect(stats.totalPoints).toBeGreaterThan(0);
      
      // Verify points today (should be 0 since test data is from 2025)
      expect(stats.pointsToday).toBe(0);
      
      // Verify first point date exists and is formatted correctly
      expect(stats.firstPointDate).toBeTruthy();
      expect(typeof stats.firstPointDate).toBe('string');
      expect(stats.firstPointDate).not.toBe('-');
      // Should contain "2025" since our test data is from 2025, and "08" or "8" for August
      expect(stats.firstPointDate).toContain('2025');
      expect(stats.firstPointDate).toMatch(/0?8/); // Matches "8" or "08" for August
      
      // Verify last point date exists and is formatted correctly
      expect(stats.lastPointDate).toBeTruthy();
      expect(typeof stats.lastPointDate).toBe('string');
      expect(stats.lastPointDate).not.toBe('-');
      expect(stats.lastPointDate).toContain('2025');
      expect(stats.lastPointDate).toMatch(/0?8/); // Matches "8" or "08" for August
      
      // Verify that first and last dates are different (we have multi-day data)
      expect(stats.firstPointDate).not.toBe(stats.lastPointDate);
      
      // Database verification - check that stats match actual data
      const dbTotalCount = await GpsDataFactory.getGpsPointsCount(dbManager, user.id);
      expect(stats.totalPoints).toBe(dbTotalCount);
      
      // Verify the actual first and last points from database
      const firstPointQuery = await dbManager.client.query(
        'SELECT timestamp FROM gps_points WHERE user_id = $1 ORDER BY timestamp ASC LIMIT 1',
        [user.id]
      );
      const lastPointQuery = await dbManager.client.query(
        'SELECT timestamp FROM gps_points WHERE user_id = $1 ORDER BY timestamp DESC LIMIT 1',
        [user.id]
      );
      
      expect(firstPointQuery.rows).toHaveLength(1);
      expect(lastPointQuery.rows).toHaveLength(1);
      
      // The UI should show dates that correspond to our database dates
      const dbFirstDate = new Date(firstPointQuery.rows[0].timestamp);
      const dbLastDate = new Date(lastPointQuery.rows[0].timestamp);
      
      // Both should be from August 2025
      expect(dbFirstDate.getFullYear()).toBe(2025);
      expect(dbFirstDate.getMonth()).toBe(7); // August (0-indexed)
      expect(dbLastDate.getFullYear()).toBe(2025);
      expect(dbLastDate.getMonth()).toBe(7); // August (0-indexed)
    });

    test('should display correct points today count', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // Create some GPS data for today
      const today = new Date();
      const todaysPoints = [];
      
      for (let i = 0; i < 5; i++) {
        const timestamp = new Date(today);
        timestamp.setHours(9 + i, i * 10, 0, 0); // Different times throughout the day
        
        todaysPoints.push({
          id: 99000 + i,
          device_id: 'today-device',
          user_id: user.id,
          coordinates: `POINT (-0.1278 51.5074)`, // London coordinates
          timestamp: timestamp.toISOString().replace('T', ' ').replace('Z', ''),
          accuracy: 5.0,
          battery: 90 - i,
          velocity: 2.5,
          altitude: 20.0,
          source_type: 'OWNTRACKS',
          created_at: timestamp.toISOString().replace('T', ' ').replace('Z', '')
        });
      }
      
      // Also add some historical data (should not count toward "today")
      const historicalData = GpsDataFactory.generateTestData(user.id, 'historical-device', 80000);
      
      // Insert both today's and historical data
      await GpsDataFactory.insertGpsData(dbManager, todaysPoints);
      await GpsDataFactory.insertGpsData(dbManager, historicalData.allPoints);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Get summary statistics
      const stats = await gpsDataPage.getSummaryStats();
      
      // Verify total points (today's + historical)
      const expectedTotal = todaysPoints.length + historicalData.allPoints.length;
      expect(stats.totalPoints).toBe(expectedTotal);
      
      // Verify points today (should be exactly 5)
      expect(stats.pointsToday).toBe(5);
      
      // Database verification - check today's data
      const todayStart = new Date(today);
      todayStart.setHours(0, 0, 0, 0);
      const todayEnd = new Date(today);
      todayEnd.setHours(23, 59, 59, 999);
      
      const todayDbCount = await GpsDataFactory.getGpsPointsCount(
        dbManager, 
        user.id,
        todayStart.toISOString().replace('T', ' ').replace('Z', ''),
        todayEnd.toISOString().replace('T', ' ').replace('Z', '')
      );
      
      expect(stats.pointsToday).toBe(todayDbCount);
      expect(todayDbCount).toBe(5);
    });

    test('should handle timezone correctly for points today count - GMT+3 scenario', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // Simulate GMT+3 timezone scenario
      // Create points that should count as "today" from GMT+3 perspective

      // Set browser timezone to GMT+3 (Europe/Kyiv)
      await page.addInitScript(() => {
        // Override Intl.DateTimeFormat to simulate GMT+3 timezone
        Object.defineProperty(Intl.DateTimeFormat.prototype, 'resolvedOptions', {
          value: function() {
            return {
              ...Object.getPrototypeOf(this).resolvedOptions.call(this),
              timeZone: 'Europe/Kyiv'
            };
          }
        });
        
        // Override Date.prototype.getTimezoneOffset to return GMT+3 offset
        Object.defineProperty(Date.prototype, 'getTimezoneOffset', {
          value: function() { return -180; } // GMT+3 = -180 minutes
        });
      });
      
      // Scenario: It's 02:00 AM in GMT+3 (which is 23:00 UTC previous day)
      const now = new Date();
      const gmtPlus3Offset = -3 * 60; // GMT+3 in minutes
      
      // Create points that are "today" from GMT+3 perspective
      // but fall into different UTC days
      const todaysPointsGmtPlus3 = [];
      
      // Point 1: Today at 01:00 GMT+3 (yesterday 22:00 UTC)
      const point1Time = new Date(now);
      point1Time.setHours(1, 0, 0, 0); // 01:00 local time
      const point1Utc = new Date(point1Time.getTime() - (gmtPlus3Offset * 60 * 1000)); // Convert to UTC
      
      todaysPointsGmtPlus3.push({
        id: 88000,
        device_id: 'tz-test-device',
        user_id: user.id,
        coordinates: `POINT (-0.1278 51.5074)`,
        timestamp: point1Utc.toISOString().replace('T', ' ').replace('Z', ''),
        accuracy: 5.0,
        battery: 85,
        velocity: 1.5,
        altitude: 20.0,
        source_type: 'OWNTRACKS',
        created_at: point1Utc.toISOString().replace('T', ' ').replace('Z', '')
      });
      
      // Insert the test data
      await GpsDataFactory.insertGpsData(dbManager, todaysPointsGmtPlus3);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Get summary statistics
      const stats = await gpsDataPage.getSummaryStats();

      expect(stats.totalPoints).toBe(1);
      
      expect(stats.pointsToday).toBe(1,
        'Expected 1 point for "today" from GMT+3 user perspective. ' +
        'If this fails, it demonstrates the timezone bug where backend uses UTC instead of user timezone.');
    });
  });

  test.describe('Date Range Filtering', () => {
    test('should filter GPS data by date range', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user and multi-day GPS test data
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
      await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Verify initial state (all data)
      const initialStats = await gpsDataPage.getSummaryStats();
      expect(initialStats.totalPoints).toBe(gpsTestData.allPoints.length);
      
      // Apply date filter for August 10, 2025 only
      // Use explicit constructor to avoid timezone issues
      const startDate = new Date(2025, 7, 10); // Month is 0-indexed: 7 = August
      const endDate = new Date(2025, 7, 10);
      
      await gpsDataPage.setDateRangeFilter(startDate, endDate);
      await gpsDataPage.waitForTableReload();
      
      // Verify filter is applied
      expect(await gpsDataPage.hasDateFilter()).toBe(true);
      const filterText = await gpsDataPage.getDateFilterText();
      expect(filterText).toContain('Aug 10');
      
      // Verify filtered table data count (summary stats remain unfiltered)
      const august10Count = gpsTestData.byDate.august10.length;
      const filteredTableCount = await gpsDataPage.getDisplayedPointsCount();
      
      // Debug info if the test fails - helps identify frontend timezone bug
      if (filteredTableCount === 0) {
        console.log('DEBUG: Date filter not working - this indicates a frontend timezone bug');
        console.log('Expected august10Count:', august10Count);
        console.log('Has GPS data:', await gpsDataPage.hasGpsData());
        console.log('Filter text:', filterText);
        console.log('KNOWN ISSUE: Frontend sends wrong date to backend (timezone offset bug)');
      }
      
      expect(filteredTableCount).toBeGreaterThan(0);
      expect(filteredTableCount).toBe(Math.min(august10Count, 50)); // Limited by page size
      
      // Summary stats should remain unchanged (they show totals, not filtered data)
      const filteredStats = await gpsDataPage.getSummaryStats();
      expect(filteredStats.totalPoints).toBe(gpsTestData.allPoints.length);
      
      // Verify database query returns correct count
      const dbCount = await GpsDataFactory.getGpsPointsCount(
        dbManager, 
        user.id, 
        '2025-08-10 00:00:00', 
        '2025-08-10 23:59:59'
      );
      expect(dbCount).toBe(august10Count);
      
      // Clear filter
      await gpsDataPage.clearDateFilter();
      await gpsDataPage.waitForTableReload();
      
      // Verify filter is cleared
      expect(await gpsDataPage.hasDateFilter()).toBe(false);
      
      // Verify all data is shown again
      const finalStats = await gpsDataPage.getSummaryStats();
      expect(finalStats.totalPoints).toBe(gpsTestData.allPoints.length);
    });

    test('should filter GPS data by date range spanning multiple days', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user and multi-day GPS test data
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
      await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Apply date filter for August 10-12, 2025 (should include Aug 10 and Aug 12)
      // Use explicit constructor to avoid timezone issues
      const startDate = new Date(2025, 7, 10); // Month is 0-indexed: 7 = August
      const endDate = new Date(2025, 7, 12);
      
      await gpsDataPage.setDateRangeFilter(startDate, endDate);
      await gpsDataPage.waitForTableReload();
      
      // Verify filtered table data count (summary stats remain unfiltered)
      const expectedCount = gpsTestData.byDate.august10.length + gpsTestData.byDate.august12.length;
      const filteredTableCount = await gpsDataPage.getDisplayedPointsCount();
      expect(filteredTableCount).toBe(Math.min(expectedCount, 50)); // Limited by page size
      
      // Summary stats should remain unchanged (they show totals, not filtered data)
      const filteredStats = await gpsDataPage.getSummaryStats();
      expect(filteredStats.totalPoints).toBe(gpsTestData.allPoints.length);
      
      // Verify database query returns correct count
      const dbCount = await GpsDataFactory.getGpsPointsCount(
        dbManager, 
        user.id, 
        '2025-08-10 00:00:00', 
        '2025-08-12 23:59:59'
      );
      expect(dbCount).toBe(expectedCount);
    });
  });

  test.describe('User Data Isolation', () => {
    test('should only show GPS data for the authenticated user', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      
      // Create two users
      const user1 = TestData.users.existing;
      const user2 = TestData.users.another;
      
      await UserFactory.createUser(page, user1);
      await UserFactory.createUser(page, user2);
      
      const user1Record = await dbManager.getUserByEmail(user1.email);
      const user2Record = await dbManager.getUserByEmail(user2.email);
      
      // Create GPS data for both users (different amounts to test isolation)
      const user1GpsData = GpsDataFactory.generateTestData(user1Record.id, 'user1-device', 90000);
      
      // For user2, create only August 12 and 15 data (subset for isolation testing)
      const user2GpsData = {
        allPoints: [
          ...GpsDataFactory.generateAugust12Data(user2Record.id, 'user2-device', 91000),
          ...GpsDataFactory.generateAugust15Data(user2Record.id, 'user2-device', 91100)
        ],
        byDate: {
          august12: GpsDataFactory.generateAugust12Data(user2Record.id, 'user2-device', 91000),
          august15: GpsDataFactory.generateAugust15Data(user2Record.id, 'user2-device', 91100)
        }
      };
      
      await GpsDataFactory.insertGpsData(dbManager, user1GpsData.allPoints);
      await GpsDataFactory.insertGpsData(dbManager, user2GpsData.allPoints);
      
      // Verify both users have data in database
      const user1DbCount = await GpsDataFactory.getGpsPointsCount(dbManager, user1Record.id);
      const user2DbCount = await GpsDataFactory.getGpsPointsCount(dbManager, user2Record.id);
      
      expect(user1DbCount).toBe(user1GpsData.allPoints.length);
      expect(user2DbCount).toBe(user2GpsData.allPoints.length);
      
      // Login as user1 and verify they only see their data
      await loginPage.navigate();
      await loginPage.login(user1.email, user1.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      const user1Stats = await gpsDataPage.getSummaryStats();
      expect(user1Stats.totalPoints).toBe(user1GpsData.allPoints.length);
      expect(user1Stats.totalPoints).not.toBe(user2GpsData.allPoints.length);

      // Logout and login as user2
      const appNavigation = new AppNavigation(page);
      await appNavigation.logout();

      await loginPage.navigate();
      await loginPage.login(user2.email, user2.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      const user2Stats = await gpsDataPage.getSummaryStats();
      expect(user2Stats.totalPoints).toBe(user2GpsData.allPoints.length);
      expect(user2Stats.totalPoints).not.toBe(user1GpsData.allPoints.length);
    });
  });

  test.describe('CSV Export Functionality', () => {
    test('should export GPS data to CSV', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user and GPS test data
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
      await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Verify export button is enabled
      expect(await gpsDataPage.isExportButtonEnabled()).toBe(true);
      
      // Set up download listener
      const downloadPromise = page.waitForEvent('download');
      
      // Click export button
      await gpsDataPage.clickExportCsv();
      
      // Verify loading state
      expect(await gpsDataPage.isExportButtonLoading()).toBe(true);
      
      // Wait for export to complete
      await gpsDataPage.waitForExportComplete();
      
      // Wait for success toast
      await gpsDataPage.waitForSuccessToast();
      const toastMessage = await gpsDataPage.getToastMessage();
      expect(toastMessage).toContain('export');
      
      // Verify download occurred
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/\.csv$/);
      
      // Verify file content (optional - save to temp location and check)
      const downloadPath = await download.path();
      expect(downloadPath).toBeTruthy();
    });

    test('should export filtered GPS data to CSV', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user and GPS test data
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
      await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Apply date filter
      // Use explicit constructor to avoid timezone issues
      const startDate = new Date(2025, 7, 10); // Month is 0-indexed: 7 = August
      const endDate = new Date(2025, 7, 10);
      
      await gpsDataPage.setDateRangeFilter(startDate, endDate);
      await gpsDataPage.waitForTableReload();
      
      // Verify filter is applied
      expect(await gpsDataPage.hasDateFilter()).toBe(true);
      
      // Set up download listener
      const downloadPromise = page.waitForEvent('download');
      
      // Export filtered data
      await gpsDataPage.clickExportCsv();
      await gpsDataPage.waitForExportComplete();
      await gpsDataPage.waitForSuccessToast();
      
      // Verify download
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/\.csv$/);
      
      // The exported file should contain only the filtered data
      // In a real test, you might want to read the CSV and verify its contents
    });

    test('should handle export with no data gracefully', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user without GPS data
      await UserFactory.createUser(page, testUser);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Verify export button is disabled when no data
      expect(await gpsDataPage.isExportButtonEnabled()).toBe(false);
      
      // Verify empty state
      expect(await gpsDataPage.hasGpsData()).toBe(false);
    });
  });

  test.describe('Pagination and Performance', () => {
    test('should handle large datasets with pagination', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const gpsDataPage = new GpsDataPage(page);
      const testUser = TestData.users.existing;
      
      // Create user and large GPS dataset
      await UserFactory.createUser(page, testUser);
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // Generate multiple days of data for pagination testing
      const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
      await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);
      
      // Login and navigate
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();
      
      // Verify initial page shows data (up to 50 items)
      const initialCount = await gpsDataPage.getDisplayedPointsCount();
      expect(initialCount).toBeGreaterThan(0);
      expect(initialCount).toBeLessThanOrEqual(50);
      
      // Check if pagination is available (if we have more than 50 points)
      if (gpsTestData.allPoints.length > 50) {
        // Verify paginator is present and has multiple pages
        const paginator = page.locator('.p-paginator');
        expect(await paginator.isVisible()).toBe(true);
        
        // Check for multiple page buttons (indicates pagination is working)
        const pageButtons = page.locator('.p-paginator-page');
        const pageButtonCount = await pageButtons.count();
        expect(pageButtonCount).toBeGreaterThan(1);
        
        // Verify next button is enabled (not disabled)
        const nextButton = page.locator('.p-paginator-next');
        expect(await nextButton.isDisabled()).toBe(false);
        
        // Navigate to next page if available
        await gpsDataPage.goToNextPage();
        await gpsDataPage.waitForTableReload();
        
        // Verify we're on a different page
        const secondPageCount = await gpsDataPage.getDisplayedPointsCount();
        expect(secondPageCount).toBeGreaterThan(0);
      }
      
      // Verify total count in stats matches database
      const stats = await gpsDataPage.getSummaryStats();
      expect(stats.totalPoints).toBe(gpsTestData.allPoints.length);
    });
  });
});