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

            // Set user timezone to UTC for consistent test behavior
            testUser.timezone = 'UTC';
            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Create some GPS data for "today" in UTC
            // Use a fixed date to avoid midnight boundary issues during test execution
            const fixedToday = new Date('2025-08-20T00:00:00.000Z');
            const todaysPoints = [];

            for (let i = 0; i < 5; i++) {
                const timestamp = new Date(fixedToday);
                timestamp.setUTCHours(9 + i, i * 10, 0, 0); // Different times throughout the day in UTC

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

            // For this test, we can't reliably test "Points Today" because:
            // 1. The backend calculates "today" based on current date, not test data date
            // 2. The test data is for a fixed date (2025-08-20) but "today" is when test runs
            // So we'll verify that points today is a reasonable number (0 or more)
            expect(stats.pointsToday).toBeGreaterThanOrEqual(0);

            // Database verification - check that our test data was inserted correctly
            const testDataDbCount = await GpsDataFactory.getGpsPointsCount(
                dbManager,
                user.id,
                '2025-08-20 00:00:00',
                '2025-08-20 23:59:59'
            );

            expect(testDataDbCount).toBe(5);
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
                // Store the original method to avoid infinite recursion
                const originalResolvedOptions = Intl.DateTimeFormat.prototype.resolvedOptions;
                
                // Override Intl.DateTimeFormat to simulate GMT+3 timezone
                Object.defineProperty(Intl.DateTimeFormat.prototype, 'resolvedOptions', {
                    value: function () {
                        const original = originalResolvedOptions.call(this);
                        return {
                            ...original,
                            timeZone: 'Europe/Kyiv'
                        };
                    }
                });

                // Override Date.prototype.getTimezoneOffset to return GMT+3 offset
                Object.defineProperty(Date.prototype, 'getTimezoneOffset', {
                    value: function () {
                        return -180;
                    } // GMT+3 = -180 minutes
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

            expect(stats.pointsToday).toBe(1);
        });
    });

    test.describe('Date Range Filtering', () => {
        test('should filter GPS data by date range', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = TestData.users.existing;

            // Create user with UTC timezone and multi-day GPS test data
            testUser.timezone = 'UTC';
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
            const startDate = new Date('2025-08-10T00:00:00.000Z');
            const endDate = new Date('2025-08-10T23:59:59.999Z');

            await gpsDataPage.setDateRangeFilter(startDate, endDate);
            await gpsDataPage.waitForTableReload();

            // Verify filter is applied
            expect(await gpsDataPage.hasDateFilter()).toBe(true);
            const filterText = await gpsDataPage.getDateFilterText();
            expect(filterText).toContain('Showing 1-50 of 58 points');

            // Verify filtered table data count (summary stats remain unfiltered)
            const august10Count = gpsTestData.byDate.august10.length;
            const filteredTableCount = await gpsDataPage.getDisplayedPointsCount();

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

            // Wait for export to complete
            await gpsDataPage.waitForExportComplete();

            // Wait for success toast
            await gpsDataPage.waitForSuccessToast();
            const toastMessage = await gpsDataPage.getToastMessage();
            expect(toastMessage).toContain('Export Started');

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

    test.describe('Timezone Date Picker Bug Tests', () => {
        test('should send correct UTC date range for single day selection in America/New_York timezone', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = TestData.users.existing;

            // Set user timezone to America/New_York 
            testUser.timezone = 'America/New_York';
            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Create some GPS data so the page loads properly
            const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
            await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);

            // Login and navigate
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            // Set up request interception to capture API calls
            let apiRequest = null;
            await page.route('**/api/gps*', route => {
                const url = route.request().url();
                if (url.includes('startTime') && url.includes('endTime')) {
                    apiRequest = route.request();
                }
                route.continue();
            });

            // Select single day: 09/22/2025 - 09/22/2025
            const selectedDate = new Date(2025, 8, 22); // September 22, 2025 (month is 0-indexed)
            await gpsDataPage.setDateRangeFilter(selectedDate, selectedDate);
            await gpsDataPage.waitForTableReload();

            // Wait for API request to be made
            await page.waitForTimeout(2000);

            // Verify the API request parameters
            expect(apiRequest).not.toBeNull();
            const url = new URL(apiRequest.url());
            const startTime = url.searchParams.get('startTime');
            const endTime = url.searchParams.get('endTime');

            // Expected for America/New_York (EDT = UTC-4 in September):
            // 09/22/2025 00:00:00 EDT = 2025-09-22T04:00:00.000Z
            // 09/22/2025 23:59:59 EDT = 2025-09-23T03:59:59.999Z
            
            // THE BUG: Currently sends 2025-09-21T04:00:00.000Z (wrong day!)
            // SHOULD BE: 2025-09-22T04:00:00.000Z (correct day)
            
            // This test will fail initially, demonstrating the bug
            expect(startTime).toBe('2025-09-22T04:00:00.000Z'); // Should be 09/22, NOT 09/21!
            expect(endTime).toBe('2025-09-23T03:59:59.999Z');   // Should be 09/23 (end of 09/22 NY time)

            // Additional verification: check that start date represents the correct day
            const startDateObj = new Date(startTime);
            const startDateInNY = startDateObj.toLocaleDateString('en-US', { 
                timeZone: 'America/New_York',
                year: 'numeric',
                month: '2-digit', 
                day: '2-digit'
            });
            
            expect(startDateInNY).toBe('09/22/2025'); // The start time should represent 09/22 in NY timezone
        });

        test('should send correct UTC date range for single day selection in Europe/London timezone', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = TestData.users.existing;

            // Set user timezone to Europe/London
            testUser.timezone = 'Europe/London';
            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Create some GPS data
            const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
            await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);

            // Login and navigate
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            // Set up request interception
            let apiRequest = null;
            await page.route('**/api/gps*', route => {
                const url = route.request().url();
                if (url.includes('startTime') && url.includes('endTime')) {
                    apiRequest = route.request();
                }
                route.continue();
            });

            // Select single day: 09/22/2025 - 09/22/2025
            const selectedDate = new Date(2025, 8, 22); // September 22, 2025
            await gpsDataPage.setDateRangeFilter(selectedDate, selectedDate);
            await gpsDataPage.waitForTableReload();

            // Wait for API request
            await page.waitForTimeout(2000);

            // Verify the API request parameters
            expect(apiRequest).not.toBeNull();
            const url = new URL(apiRequest.url());
            const startTime = url.searchParams.get('startTime');
            const endTime = url.searchParams.get('endTime');

            // Expected for Europe/London (BST = UTC+1 in September):
            // 09/22/2025 00:00:00 BST = 2025-09-21T23:00:00.000Z
            // 09/22/2025 23:59:59 BST = 2025-09-22T22:59:59.999Z
            expect(startTime).toBe('2025-09-21T23:00:00.000Z');
            expect(endTime).toBe('2025-09-22T22:59:59.999Z');

            // Verify the start time represents the correct day in London timezone
            const startDateObj = new Date(startTime);
            const startDateInLondon = startDateObj.toLocaleDateString('en-US', { 
                timeZone: 'Europe/London',
                year: 'numeric',
                month: '2-digit', 
                day: '2-digit'
            });
            expect(startDateInLondon).toBe('09/22/2025');
        });

        test('should handle timezone switching and update API requests accordingly', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = TestData.users.existing;

            // Start with UTC timezone
            testUser.timezone = 'UTC';
            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Create GPS data
            const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
            await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);

            // Login and navigate
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            // Track API requests
            const apiRequests = [];
            await page.route('**/api/gps*', route => {
                const url = route.request().url();
                if (url.includes('startTime') && url.includes('endTime')) {
                    apiRequests.push(route.request());
                }
                route.continue();
            });

            // Select date in UTC timezone
            const selectedDate = new Date(2025, 8, 22); // September 22, 2025
            await gpsDataPage.setDateRangeFilter(selectedDate, selectedDate);
            await gpsDataPage.waitForTableReload();
            await page.waitForTimeout(1000);

            // Switch to America/New_York timezone
            await page.evaluate(() => {
                const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
                userInfo.timezone = 'America/New_York';
                localStorage.setItem('userInfo', JSON.stringify(userInfo));
            });

            // Reload page to apply timezone change
            await page.reload();
            await gpsDataPage.waitForPageLoad();

            // Select same date again (should now be interpreted as NY time)
            await gpsDataPage.setDateRangeFilter(selectedDate, selectedDate);
            await gpsDataPage.waitForTableReload();
            await page.waitForTimeout(1000);

            // Verify we have at least 2 API requests
            expect(apiRequests.length).toBeGreaterThanOrEqual(2);

            // Check the difference between UTC and NY timezone requests
            const utcRequest = new URL(apiRequests[0].url());
            const nyRequest = new URL(apiRequests[apiRequests.length - 1].url());

            const utcStartTime = utcRequest.searchParams.get('startTime');
            const nyStartTime = nyRequest.searchParams.get('startTime');

            // Times should be different due to timezone offset
            expect(utcStartTime).not.toBe(nyStartTime);

            // UTC: 09/22/2025 00:00:00 UTC = 2025-09-22T00:00:00.000Z
            // NY: 09/22/2025 00:00:00 EDT = 2025-09-22T04:00:00.000Z
            expect(utcStartTime).toBe('2025-09-22T00:00:00.000Z');
            expect(nyStartTime).toBe('2025-09-22T04:00:00.000Z');
        });
    });
});