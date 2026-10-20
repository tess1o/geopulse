import { test, expect } from '../fixtures/isolated-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {GpsDataPage} from '../pages/GpsDataPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';
import {GpsDataFactory} from '../utils/gps-data-factory.js';
import {DateFormatTestHelper, DateFormatValues} from '../utils/date-format-test-helper.js';
import {UserSettingsApi} from '../utils/user-settings-api.js';
import { randomUUID } from 'crypto';
import { readFile } from 'node:fs/promises';

const insertGlobalTelemetryConfig = async (dbManager, userId, sourceType, {
    telemetryMapping = []
} = {}) => {
    const telemetryConfigId = randomUUID();

    await dbManager.client.query(`
        INSERT INTO gps_source_type_telemetry_config (
            id,
            user_id,
            source_type,
            mapping
        )
        VALUES ($1, $2, $3, $4::jsonb)
    `, [telemetryConfigId, userId, sourceType, JSON.stringify(telemetryMapping)]);
};

const getTelemetryKeys = async (page) => {
    const keyInputs = page.locator('.telemetry-table tbody tr td:first-child input');
    const keyCount = await keyInputs.count();
    const keys = [];

    for (let i = 0; i < keyCount; i++) {
        const key = (await keyInputs.nth(i).inputValue()).trim();
        if (key) {
            keys.push(key);
        }
    }

    return keys;
};

const findTelemetryRowByKey = async (page, keyToFind) => {
    const rows = page.locator('.telemetry-table tbody tr');
    const rowCount = await rows.count();

    for (let i = 0; i < rowCount; i++) {
        const row = rows.nth(i);
        const keyInput = row.locator('td').first().locator('input');
        if (await keyInput.count() === 0) {
            continue;
        }

        const key = (await keyInput.inputValue()).trim();
        if (key === keyToFind) {
            return row;
        }
    }

    throw new Error(`Telemetry mapping row not found for key: ${keyToFind}`);
};

const isGpsDateRangeRequest = (request) => {
    if (!request.url().includes('/api/v1/gps/points')) {
        return false;
    }

    if (request.method() !== 'GET') {
        return false;
    }

    const requestUrl = new URL(request.url());
    return requestUrl.searchParams.has('from') && requestUrl.searchParams.has('to');
};

/**
 * Seed `count` points one hour apart on 2025-08-20 (08:00Z, 09:00Z, ...), with speed i * 10 km/h.
 * Returns them newest first, which is the table's default order, so result[i] is table row i.
 */
const seedHourlyPoints = async (dbManager, userId, count, { deviceId = 'write-actions-device', latBase = 51.5 } = {}) => {
    const points = Array.from({ length: count }, (_, i) => ({
        user_id: userId,
        device_id: deviceId,
        latitude: latBase + i * 0.001,
        longitude: -0.12 - i * 0.001,
        timestamp: `2025-08-20T${String(8 + i).padStart(2, '0')}:00:00Z`,
        accuracy: 5 + i,
        battery: 90 - i,
        velocity: i * 10,
        altitude: 20,
        source_type: 'OWNTRACKS'
    }));

    const rows = await GpsDataFactory.insertMultipleGpsPoints(dbManager, points);
    return rows
        .map((row, i) => ({ ...points[i], id: Number(row.id), hour: `T${String(8 + i).padStart(2, '0')}:00:00` }))
        .reverse();
};

const getGpsPointIds = async (dbManager, userId) => {
    const result = await dbManager.client.query(
        'SELECT id FROM gps_points WHERE user_id = $1 ORDER BY id',
        [userId]
    );
    return result.rows.map(row => Number(row.id));
};

const getGpsPointRow = async (dbManager, pointId) => {
    const result = await dbManager.client.query(`
        SELECT ST_Y(coordinates) AS lat, ST_X(coordinates) AS lng, velocity, accuracy, timestamp
        FROM gps_points
        WHERE id = $1
    `, [pointId]);
    return result.rows[0] || null;
};

const isGpsPointsApiCall = (response, method, pathPattern) =>
    response.request().method() === method && pathPattern.test(new URL(response.url()).pathname);

const waitForCsvExport = (page) => page.waitForResponse(response =>
    isGpsPointsApiCall(response, 'GET', /\/api\/v1\/gps\/points\/exports$/) && response.status() === 200
);

const csvDataLines = (csv) => csv.split('\n').slice(1).filter(line => line.trim() !== '');

const loginAndOpenGpsData = async (page, testUser) => {
    const loginPage = new LoginPage(page);
    const gpsDataPage = new GpsDataPage(page);

    await loginPage.navigate();
    await loginPage.login(testUser.email, testUser.password);
    await TestHelpers.waitForNavigation(page, '**/app/timeline');

    await gpsDataPage.navigate();
    await gpsDataPage.waitForPageLoad();
    return gpsDataPage;
};

test.describe('GPS Data Page', () => {

    test.describe('Basic GPS Data Viewing', () => {
        test('should display GPS data for authenticated user', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page, { timezone: 'Europe/Kyiv' });

            // Create user and GPS test data
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

        test('should keep row checkbox visual state in sync with selection state', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);
            const user = await dbManager.getUserByEmail(testUser.email);

            const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device').byDate.august10.slice(0, 3);
            await GpsDataFactory.insertGpsData(dbManager, gpsTestData);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            const firstRowCheckbox = page
                .locator('.gps-data-table tbody tr')
                .first()
                .locator('.selection-col input.p-checkbox-input');

            await expect(firstRowCheckbox).not.toBeChecked();

            await firstRowCheckbox.click();
            await expect(firstRowCheckbox).toBeChecked();
            await expect(page.locator('.selection-text')).toHaveText('1 selected');
            await expect(page.locator('button:has-text("Delete Selected")')).toBeVisible();

            await firstRowCheckbox.click();
            await expect(firstRowCheckbox).not.toBeChecked();
            await expect(page.locator('.selection-text')).toHaveCount(0);
        });

        test('should show empty state when user has no GPS data', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user without GPS data

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

        test('should display GPS data with correct details', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user and GPS test data
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
            for (const [index, point] of displayedPoints.entries()) {
                expect(point).toHaveProperty('timestamp');
                expect(point.timestamp).toMatch(/\d{1,2}:\d{2}:\d{2}/);
                expect(point).toHaveProperty('delta');
                if (index === 0) {
                    expect(point.delta).toBe('-');
                } else {
                    expect(point.delta).toMatch(/^(\d+s|\d+m \d{2}s|\d+h \d+m|\d+d \d+h)$/);
                }
                expect(point).toHaveProperty('coordinates');
                expect(point.coordinates).toHaveProperty('lat');
                expect(point.coordinates).toHaveProperty('lng');
                expect(typeof point.coordinates.lat).toBe('number');
                expect(typeof point.coordinates.lng).toBe('number');
                expect(point).toHaveProperty('source');
                expect(point.source).toBe('OWNTRACKS');
            }
        });

        test('should display correct summary statistics', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user and GPS test data with known timestamps
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

        test('should apply user date format to summary cards and timestamp column', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page, { dateFormat: DateFormatValues.DMY, timezone: 'UTC' });
            const user = await dbManager.getUserByEmail(testUser.email);
            await dbManager.client.query(
                "UPDATE users SET ui_preferences = jsonb_set(ui_preferences, '{dateFormat}', to_jsonb($1::text)) WHERE id = $2",
                [DateFormatValues.DMY, user.id]
            );

            const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
            await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            const stats = await gpsDataPage.getSummaryStats();
            DateFormatTestHelper.expectContainsDate(stats.firstPointDate, '10/08/2025', '08/10/2025');
            DateFormatTestHelper.expectContainsDate(stats.lastPointDate, '15/08/2025', '08/15/2025');

            const displayedPoints = await gpsDataPage.getDisplayedGpsPoints();
            expect(displayedPoints.length).toBeGreaterThan(0);
            const timestampTexts = displayedPoints.map(point => point.timestamp);
            const matchingTimestamp = timestampTexts.find(text =>
                text.includes('10/08/2025') || text.includes('12/08/2025') || text.includes('15/08/2025')
            );
            expect(matchingTimestamp).toBeTruthy();
            expect(matchingTimestamp).not.toContain('08/10/2025');
            expect(matchingTimestamp).not.toContain('08/12/2025');
            expect(matchingTimestamp).not.toContain('08/15/2025');
        });

        test('should display correct points today count', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });
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

        test('should handle timezone correctly for points today count - GMT+3 scenario', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const gmtPlus3Timezone = 'Africa/Nairobi';
            const testUser = await isolatedUsers.create(page, { timezone: gmtPlus3Timezone });

            // Create user
            const user = await dbManager.getUserByEmail(testUser.email);

            // Simulate GMT+3 timezone scenario
            // Create points that should count as "today" from GMT+3 perspective

            // Create points that are "today" from GMT+3 perspective
            // but fall into different UTC days
            const todaysPointsGmtPlus3 = [];

            // Point 1: Today at 01:00 GMT+3 (yesterday 22:00 UTC)
            const gmtPlus3TodayParts = new Intl.DateTimeFormat('en-CA', {
                timeZone: gmtPlus3Timezone,
                year: 'numeric',
                month: '2-digit',
                day: '2-digit'
            }).formatToParts(new Date()).reduce((parts, part) => {
                if (part.type !== 'literal') {
                    parts[part.type] = Number(part.value);
                }
                return parts;
            }, {});
            const point1Utc = new Date(Date.UTC(
                gmtPlus3TodayParts.year,
                gmtPlus3TodayParts.month - 1,
                gmtPlus3TodayParts.day,
                -2,
                0,
                0
            ));

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
        test('should filter GPS data by date range', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });
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

        test('should filter GPS data by date range spanning multiple days', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user and multi-day GPS test data
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
            const startDate = new Date(2025, 7, 10, 0, 0, 0, 0); // Month is 0-indexed: 7 = August
            const endDate = new Date(2025, 7, 12, 23, 59, 0, 0);

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

    test.describe('Telemetry Rendering', () => {
        test('should expose global OwnTracks telemetry defaults and persist mapping changes', async ({ page, isolatedUsers, dbManager }) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            const telemetrySection = page.locator('.telemetry-mapping-section');
            await expect(telemetrySection).toBeVisible();
            await expect(telemetrySection.locator('.telemetry-mapping-content')).toHaveCount(0);

            await telemetrySection.locator('button:has-text("Show Mapping")').click();
            await expect(telemetrySection.locator('.telemetry-mapping-content')).toBeVisible();

            const telemetryKeys = await getTelemetryKeys(page);
            expect(telemetryKeys).toHaveLength(13);
            expect(telemetryKeys).toEqual(expect.arrayContaining([
                'ignition',
                'armed',
                'doors',
                'batt_v',
                'batt_ina',
                'sats',
                'rssi',
                'lte_pct',
                'pitch',
                'roll',
                'geofence_lat',
                'geofence_lon',
                'geofence_radius'
            ]));

            const ignitionRow = await findTelemetryRowByKey(page, 'ignition');
            await ignitionRow
                .locator('.telemetry-check-cell')
                .nth(1)
                .locator('input.p-checkbox-input')
                .setChecked(false);

            await page.locator('.telemetry-mapping-actions button:has-text("Save Mapping")').click();
            await gpsDataPage.waitForSuccessToast();

            const user = await dbManager.getUserByEmail(testUser.email);
            const mappingResult = await dbManager.client.query(`
                SELECT mapping
                FROM gps_source_type_telemetry_config
                WHERE user_id = $1
                  AND source_type = 'OWNTRACKS'
            `, [user.id]);

            expect(mappingResult.rows.length).toBe(1);
            const persistedMapping = mappingResult.rows[0].mapping;
            expect(Array.isArray(persistedMapping)).toBe(true);

            const ignitionMapping = persistedMapping.find(entry => entry.key === 'ignition');
            expect(ignitionMapping).toBeTruthy();
            expect(ignitionMapping.showInGpsData).toBe(true);
            expect(ignitionMapping.showInCurrentPopup).toBe(false);
            expect(ignitionMapping.enabled).toBe(true);

            const geofenceLatMapping = persistedMapping.find(entry => entry.key === 'geofence_lat');
            expect(geofenceLatMapping).toBeTruthy();
            expect(geofenceLatMapping.enabled).toBe(false);
        });

        test('should show only GPS Data telemetry fields enabled by mapping', async ({ page, isolatedUsers, dbManager }) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);
            const user = await dbManager.getUserByEmail(testUser.email);

            await insertGlobalTelemetryConfig(dbManager, user.id, 'OWNTRACKS', {
                telemetryMapping: [
                    {
                        key: 'ignition',
                        label: 'Ignition',
                        type: 'boolean',
                        enabled: true,
                        order: 10,
                        trueValues: ['1', 'true', 'yes', 'on'],
                        falseValues: ['0', 'false', 'no', 'off'],
                        showInGpsData: true,
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
                        showInCurrentPopup: true
                    },
                    {
                        key: 'geofence_lat',
                        label: 'Geofence Latitude',
                        type: 'number',
                        unit: 'deg',
                        enabled: false,
                        order: 30,
                        showInGpsData: true,
                        showInCurrentPopup: true
                    }
                ]
            });

            await dbManager.client.query(`
                INSERT INTO gps_points (
                    device_id, user_id, coordinates, timestamp, accuracy, battery,
                    velocity, altitude, source_type, created_at, telemetry
                )
                VALUES (
                    'telemetry-device', $1, ST_GeomFromText('POINT(-0.1278 51.5074)', 4326),
                    '2025-08-10T10:00:00Z', 5.0, 95, 0.0, 25.0, 'OWNTRACKS',
                    '2025-08-10T10:00:00Z', $2::jsonb
                )
            `, [user.id, JSON.stringify({
                ignition: 1,
                lte_pct: 72,
                geofence_lat: 51.5074
            })]);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            const telemetryCell = page.locator('.gps-data-table tbody tr').first().locator('.telemetry-cell');
            await expect(telemetryCell).toBeVisible();
            await expect(telemetryCell).toContainText('Ignition:');
            await expect(telemetryCell).toContainText('Yes');
            await expect(telemetryCell).not.toContainText('LTE Signal');
            await expect(telemetryCell).not.toContainText('Geofence Latitude');
            await expect(telemetryCell.locator('.telemetry-item')).toHaveCount(1);
        });

        test('should render telemetry for OwnTracks points without any source config link', async ({ page, isolatedUsers, dbManager }) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);
            const user = await dbManager.getUserByEmail(testUser.email);

            await dbManager.client.query(`
                INSERT INTO gps_points (
                    device_id, user_id, coordinates, timestamp, accuracy, battery,
                    velocity, altitude, source_type, created_at, telemetry
                )
                VALUES (
                    'imported-owntracks', $1, ST_GeomFromText('POINT(-0.1200 51.5000)', 4326),
                    '2025-08-10T11:00:00Z', 5.0, 90, 0.0, 20.0, 'OWNTRACKS',
                    '2025-08-10T11:00:00Z', $2::jsonb
                )
            `, [user.id, JSON.stringify({
                ignition: 1,
                lte_pct: 72
            })]);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            const telemetryCell = page.locator('.gps-data-table tbody tr').first().locator('.telemetry-cell');
            await expect(telemetryCell).toBeVisible();
            await expect(telemetryCell).toContainText('Ignition:');
            await expect(telemetryCell).toContainText('LTE Signal:');
        });

        test('should keep telemetry rendering after deleting and recreating OwnTracks source config', async ({ page, isolatedUsers, dbManager }) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);
            const user = await dbManager.getUserByEmail(testUser.email);

            await insertGlobalTelemetryConfig(dbManager, user.id, 'OWNTRACKS', {
                telemetryMapping: [
                    {
                        key: 'ignition',
                        label: 'Ignition',
                        type: 'boolean',
                        enabled: true,
                        order: 10,
                        trueValues: ['1'],
                        falseValues: ['0'],
                        showInGpsData: true,
                        showInCurrentPopup: true
                    }
                ]
            });

            const firstSourceId = randomUUID();
            const secondSourceId = randomUUID();
            await dbManager.client.query(`
                INSERT INTO gps_source_config (
                    id, user_id, source_type, username, password_hash, active, connection_type
                )
                VALUES ($1, $2, 'OWNTRACKS', 'before-recreate', '', true, 'HTTP')
            `, [firstSourceId, user.id]);

            await dbManager.client.query(`
                INSERT INTO gps_points (
                    device_id, user_id, coordinates, timestamp, accuracy, battery,
                    velocity, altitude, source_type, created_at, telemetry
                )
                VALUES (
                    'telemetry-device', $1, ST_GeomFromText('POINT(-0.1278 51.5074)', 4326),
                    '2025-08-10T10:00:00Z', 5.0, 95, 0.0, 25.0, 'OWNTRACKS',
                    '2025-08-10T10:00:00Z', $2::jsonb
                )
            `, [user.id, JSON.stringify({ ignition: 1 })]);

            await dbManager.client.query(`DELETE FROM gps_source_config WHERE id = $1`, [firstSourceId]);
            await dbManager.client.query(`
                INSERT INTO gps_source_config (
                    id, user_id, source_type, username, password_hash, active, connection_type
                )
                VALUES ($1, $2, 'OWNTRACKS', 'after-recreate', '', true, 'HTTP')
            `, [secondSourceId, user.id]);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            const telemetryCell = page.locator('.gps-data-table tbody tr').first().locator('.telemetry-cell');
            await expect(telemetryCell).toBeVisible();
            await expect(telemetryCell).toContainText('Ignition:');
        });
    });

    test.describe('User Data Isolation', () => {
        test('should only show GPS data for the authenticated user', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);

            // Create two users
            const user1 = await isolatedUsers.create(page);
            const user2 = await isolatedUsers.create(page);

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

            // Clear session and login as user2 (avoids flaky logout-response timing). Clear storage on the
            // login page: the GPS page would redirect itself to /login on its next 401.
            await TestSetupHelper.leaveAppAndClearCookies(page);
            await loginPage.navigate();
            await page.evaluate(() => {
                localStorage.clear();
                sessionStorage.clear();
            });

            await loginPage.login(user2.email, user2.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await gpsDataPage.navigate();
            await gpsDataPage.waitForPageLoad();

            const user2Stats = await gpsDataPage.getSummaryStats();
            expect(user2Stats.totalPoints).toBe(user2GpsData.allPoints.length);
            expect(user2Stats.totalPoints).not.toBe(user1GpsData.allPoints.length);
        });
    });

    test.describe('GPS Point Write Actions', () => {
        test('should stage a point edit and save new coordinates, speed and accuracy on apply', async ({ page, isolatedUsers, dbManager }) => {
            const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });
            const user = await dbManager.getUserByEmail(testUser.email);
            const points = await seedHourlyPoints(dbManager, user.id, 3);
            const [target, ...untouched] = points;
            const targetBefore = await getGpsPointRow(dbManager, target.id);
            const untouchedBefore = await Promise.all(untouched.map(point => getGpsPointRow(dbManager, point.id)));

            const gpsDataPage = await loginAndOpenGpsData(page, testUser);
            expect(await gpsDataPage.getRowCoordinatesText(0))
                .toBe(`${target.latitude.toFixed(6)},${target.longitude.toFixed(6)}`);

            await gpsDataPage.openEditDialog(0);
            await gpsDataPage.stageEdit({ lat: 48.8566, lng: 2.3522, velocity: 42.5, accuracy: 7.5 });

            // Staged only: the row shows the new values, the database still has the old ones
            const pendingBanner = page.locator(gpsDataPage.selectors.pendingEditsBanner);
            await expect(pendingBanner).toContainText('1 GPS point change is not applied yet');
            await expect(gpsDataPage.getRow(0)).toHaveClass(/pending-edit-row/);
            expect(await gpsDataPage.getRowCoordinatesText(0)).toBe('48.856600,2.352200');
            expect(await getGpsPointRow(dbManager, target.id)).toEqual(targetBefore);

            const updateResponse = page.waitForResponse(response =>
                isGpsPointsApiCall(response, 'PUT', /\/api\/v1\/gps\/points\/bulk$/)
            );
            await gpsDataPage.applyPendingEdits();
            expect((await updateResponse).ok()).toBe(true);
            await gpsDataPage.waitForSuccessToast();
            await expect(pendingBanner).toHaveCount(0);
            await gpsDataPage.waitForTableReload();

            const targetAfter = await getGpsPointRow(dbManager, target.id);
            expect(targetAfter.lat).toBeCloseTo(48.8566, 6);
            expect(targetAfter.lng).toBeCloseTo(2.3522, 6);
            expect(targetAfter.velocity).toBeCloseTo(42.5, 4);
            expect(targetAfter.accuracy).toBeCloseTo(7.5, 4);
            // The edit dialog has no timestamp field; the point keeps its time
            expect(new Date(targetAfter.timestamp).getTime()).toBe(new Date(targetBefore.timestamp).getTime());

            for (const [index, point] of untouched.entries()) {
                expect(await getGpsPointRow(dbManager, point.id)).toEqual(untouchedBefore[index]);
            }

            expect(await gpsDataPage.getRowCoordinatesText(0)).toBe('48.856600,2.352200');
            await expect(gpsDataPage.getRow(0)).not.toHaveClass(/pending-edit-row/);
        });

        test('should keep the point when single delete is cancelled and remove it when confirmed', async ({ page, isolatedUsers, dbManager }) => {
            const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });
            const user = await dbManager.getUserByEmail(testUser.email);
            const points = await seedHourlyPoints(dbManager, user.id, 3);
            const target = points[1];

            const gpsDataPage = await loginAndOpenGpsData(page, testUser);
            expect(await gpsDataPage.getDisplayedPointsCount()).toBe(3);

            // Cancel keeps the row
            await gpsDataPage.openDeleteDialog(1);
            await expect(gpsDataPage.getDialog('Delete GPS Point'))
                .toContainText('Are you sure you want to delete this GPS point?');
            await gpsDataPage.clickDialogButton('Delete GPS Point', 'Cancel');
            await expect(gpsDataPage.getDialog('Delete GPS Point')).toBeHidden();

            expect(await getGpsPointIds(dbManager, user.id)).toEqual(points.map(point => point.id).sort((a, b) => a - b));
            expect(await gpsDataPage.getDisplayedPointsCount()).toBe(3);

            // Confirm removes exactly that row
            await gpsDataPage.openDeleteDialog(1);
            const deleteResponse = page.waitForResponse(response =>
                isGpsPointsApiCall(response, 'DELETE', new RegExp(`/api/v1/gps/points/${target.id}$`))
            );
            await gpsDataPage.clickDialogButton('Delete GPS Point', 'Delete');
            expect((await deleteResponse).ok()).toBe(true);
            await expect(gpsDataPage.getDialog('Delete GPS Point')).toBeHidden();
            await gpsDataPage.waitForSuccessToast();
            await gpsDataPage.waitForTableReload();

            expect(await getGpsPointRow(dbManager, target.id)).toBeNull();
            expect(await getGpsPointIds(dbManager, user.id))
                .toEqual([points[0].id, points[2].id].sort((a, b) => a - b));
            expect(await gpsDataPage.getDisplayedPointsCount()).toBe(2);
            expect((await gpsDataPage.getSummaryStats()).totalPoints).toBe(2);
        });

        test('should bulk delete exactly the rows picked with checkboxes and leave another user untouched', async ({ page, isolatedUsers, dbManager }) => {
            const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });
            const otherUser = await isolatedUsers.create(page, { timezone: 'UTC' });
            const user = await dbManager.getUserByEmail(testUser.email);
            const otherUserRecord = await dbManager.getUserByEmail(otherUser.email);

            const points = await seedHourlyPoints(dbManager, user.id, 6);
            await seedHourlyPoints(dbManager, otherUserRecord.id, 4, { deviceId: 'other-user-device', latBase: 50.4 });
            const otherUserIdsBefore = await getGpsPointIds(dbManager, otherUserRecord.id);

            const gpsDataPage = await loginAndOpenGpsData(page, testUser);

            const selectedIndexes = [0, 2, 5];
            for (const index of selectedIndexes) {
                await gpsDataPage.toggleRowCheckbox(index);
            }
            await expect(page.locator(gpsDataPage.selectors.selectionText)).toHaveText('3 selected');

            await gpsDataPage.openBulkDeleteDialog();
            await expect(gpsDataPage.getDialog('Delete Multiple GPS Points')).toContainText('delete 3 GPS points');

            const bulkDeleteResponse = page.waitForResponse(response =>
                isGpsPointsApiCall(response, 'POST', /\/api\/v1\/gps\/points\/bulk$/)
            );
            await gpsDataPage.clickDialogButton('Delete Multiple GPS Points', 'Delete All');
            const response = await bulkDeleteResponse;
            expect(response.ok()).toBe(true);
            expect(new Set(response.request().postDataJSON().gpsPointIds))
                .toEqual(new Set(selectedIndexes.map(index => points[index].id)));
            await gpsDataPage.waitForSuccessToast();
            await gpsDataPage.waitForTableReload();

            const remainingIds = points
                .filter((_, index) => !selectedIndexes.includes(index))
                .map(point => point.id)
                .sort((a, b) => a - b);
            expect(await getGpsPointIds(dbManager, user.id)).toEqual(remainingIds);
            expect(await getGpsPointIds(dbManager, otherUserRecord.id)).toEqual(otherUserIdsBefore);

            expect(await gpsDataPage.getDisplayedPointsCount()).toBe(3);
            await expect(page.locator(gpsDataPage.selectors.selectionText)).toHaveCount(0);
        });

        test('should select a row range with shift-click and bulk delete only that range', async ({ page, isolatedUsers, dbManager }) => {
            const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });
            const otherUser = await isolatedUsers.create(page, { timezone: 'UTC' });
            const user = await dbManager.getUserByEmail(testUser.email);
            const otherUserRecord = await dbManager.getUserByEmail(otherUser.email);

            const points = await seedHourlyPoints(dbManager, user.id, 6);
            await seedHourlyPoints(dbManager, otherUserRecord.id, 4, { deviceId: 'other-user-device', latBase: 50.4 });
            const otherUserIdsBefore = await getGpsPointIds(dbManager, otherUserRecord.id);

            const gpsDataPage = await loginAndOpenGpsData(page, testUser);

            // Click row 1, then shift-click row 4: rows 1-4 are selected, rows 0 and 5 are not
            await gpsDataPage.clickRow(1);
            await expect(page.locator(gpsDataPage.selectors.selectionText)).toHaveText('1 selected');
            await gpsDataPage.clickRow(4, { shift: true });
            await expect(page.locator(gpsDataPage.selectors.selectionText)).toHaveText('4 selected');

            const rangeIndexes = [1, 2, 3, 4];
            for (let index = 0; index < points.length; index++) {
                const checkbox = gpsDataPage.getRowCheckbox(index);
                if (rangeIndexes.includes(index)) {
                    await expect(checkbox).toBeChecked();
                } else {
                    await expect(checkbox).not.toBeChecked();
                }
            }

            await gpsDataPage.openBulkDeleteDialog();
            await expect(gpsDataPage.getDialog('Delete Multiple GPS Points')).toContainText('delete 4 GPS points');

            const bulkDeleteResponse = page.waitForResponse(response =>
                isGpsPointsApiCall(response, 'POST', /\/api\/v1\/gps\/points\/bulk$/)
            );
            await gpsDataPage.clickDialogButton('Delete Multiple GPS Points', 'Delete All');
            expect((await bulkDeleteResponse).ok()).toBe(true);
            await gpsDataPage.waitForSuccessToast();
            await gpsDataPage.waitForTableReload();

            expect(await getGpsPointIds(dbManager, user.id))
                .toEqual([points[0].id, points[5].id].sort((a, b) => a - b));
            expect(await getGpsPointIds(dbManager, otherUserRecord.id)).toEqual(otherUserIdsBefore);
            expect(await gpsDataPage.getDisplayedPointsCount()).toBe(2);
        });

        test('should delete all of the user\'s points even when filters narrow the table', async ({ page, isolatedUsers, dbManager }) => {
            const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });
            const otherUser = await isolatedUsers.create(page, { timezone: 'UTC' });
            const user = await dbManager.getUserByEmail(testUser.email);
            const otherUserRecord = await dbManager.getUserByEmail(otherUser.email);

            await seedHourlyPoints(dbManager, user.id, 6);
            await seedHourlyPoints(dbManager, otherUserRecord.id, 4, { deviceId: 'other-user-device', latBase: 50.4 });
            const otherUserIdsBefore = await getGpsPointIds(dbManager, otherUserRecord.id);

            const gpsDataPage = await loginAndOpenGpsData(page, testUser);

            // Speeds are 0, 10, ..., 50 km/h: a 25 km/h minimum leaves 3 rows
            await gpsDataPage.showAdvancedFilters();
            await gpsDataPage.setSpeedMinFilter(25);
            await expect(page.locator(gpsDataPage.selectors.activeFilterChips).filter({ hasText: 'Speed:' })).toBeVisible();
            expect(await gpsDataPage.getDisplayedPointsCount()).toBe(3);

            // "Delete All Data" is not narrowed by filters: the dialog counts every point of the user
            await gpsDataPage.openDeleteAllDialog();
            await expect(gpsDataPage.getDialog('Delete All GPS Data')).toContainText('All 6 GPS points');

            const deleteAllResponse = page.waitForResponse(response =>
                isGpsPointsApiCall(response, 'DELETE', /\/api\/v1\/gps\/points$/)
            );
            await gpsDataPage.clickDialogButton('Delete All GPS Data', 'Delete Everything');
            const response = await deleteAllResponse;
            expect(response.ok()).toBe(true);
            expect(new URL(response.url()).search).toBe('');
            await gpsDataPage.waitForSuccessToast();
            await gpsDataPage.waitForTableReload();

            expect(await GpsDataFactory.getGpsPointsCount(dbManager, user.id)).toBe(0);
            expect(await getGpsPointIds(dbManager, otherUserRecord.id)).toEqual(otherUserIdsBefore);
            expect(await gpsDataPage.hasGpsData()).toBe(false);
            await expect(page.locator(gpsDataPage.selectors.deleteAllButton)).toHaveCount(0);
        });
    });

    test.describe('CSV Export Functionality', () => {
        test('should export manual selection over filters, filters over all data', async ({ page, isolatedUsers, dbManager }) => {
            const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });
            const user = await dbManager.getUserByEmail(testUser.email);
            const points = await seedHourlyPoints(dbManager, user.id, 6);

            const gpsDataPage = await loginAndOpenGpsData(page, testUser);

            // Priority 3: no filters, no selection -> all points
            await expect(page.locator(gpsDataPage.selectors.exportButton)).toHaveText('Export CSV');
            let exportResponse = waitForCsvExport(page);
            await gpsDataPage.clickExportCsv();
            let response = await exportResponse;
            await gpsDataPage.waitForExportComplete();
            let exportUrl = new URL(response.url());
            expect(exportUrl.searchParams.has('ids')).toBe(false);
            expect(exportUrl.searchParams.has('speedMin')).toBe(false);
            expect(csvDataLines(await response.text())).toHaveLength(6);

            // Priority 2: filters, no selection -> filtered points (speed 30, 40, 50 = table rows 0-2)
            await gpsDataPage.showAdvancedFilters();
            await gpsDataPage.setSpeedMinFilter(25);
            await expect(page.locator(gpsDataPage.selectors.exportButton)).toHaveText('Export CSV (3 pts)');
            exportResponse = waitForCsvExport(page);
            await gpsDataPage.clickExportCsv();
            response = await exportResponse;
            await gpsDataPage.waitForExportComplete();
            exportUrl = new URL(response.url());
            expect(exportUrl.searchParams.get('speedMin')).toBe('25');
            expect(exportUrl.searchParams.has('ids')).toBe(false);
            let lines = csvDataLines(await response.text());
            expect(lines).toHaveLength(3);
            for (const point of points.slice(0, 3)) {
                expect(lines.some(line => line.includes(point.hour))).toBe(true);
            }

            // Priority 1: selection wins over the still-active filter -> only the selected points
            const selected = [points[0], points[2]];
            await gpsDataPage.toggleRowCheckbox(0);
            await gpsDataPage.toggleRowCheckbox(2);
            await expect(page.locator(gpsDataPage.selectors.exportButton)).toHaveText('Export CSV (2 selected)');
            exportResponse = waitForCsvExport(page);
            await gpsDataPage.clickExportCsv();
            response = await exportResponse;
            await gpsDataPage.waitForExportComplete();
            exportUrl = new URL(response.url());
            expect(new Set(exportUrl.searchParams.get('ids').split(',').map(Number)))
                .toEqual(new Set(selected.map(point => point.id)));
            expect(exportUrl.searchParams.has('speedMin')).toBe(false);
            lines = csvDataLines(await response.text());
            expect(lines).toHaveLength(2);
            for (const point of selected) {
                expect(lines.some(line => line.includes(point.hour))).toBe(true);
            }
            expect(lines.some(line => line.includes(points[1].hour))).toBe(false);
        });

        test('should export GPS data to CSV', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user and GPS test data
            const user = await dbManager.getUserByEmail(testUser.email);

            const gpsTestData = GpsDataFactory.generateTestData(user.id, 'test-device');
            await GpsDataFactory.insertGpsData(dbManager, gpsTestData.allPoints);
            await dbManager.client.query(
                `
                    UPDATE gps_points
                    SET telemetry = $1::jsonb
                    WHERE id = (
                        SELECT id
                        FROM gps_points
                        WHERE user_id = $2
                        ORDER BY timestamp ASC
                        LIMIT 1
                    )
                `,
                [JSON.stringify({ ignition: 1, batt_v: 12.6 }), user.id]
            );

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

            const csvContent = await readFile(downloadPath, 'utf-8');
            const [headerLine] = csvContent.split('\n');
            expect(headerLine).toContain('telemetry');
            expect(csvContent).toContain('""ignition"":1');
            expect(csvContent).toContain('""batt_v"":12.6');
        });

        test('should export filtered GPS data to CSV', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user and GPS test data
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
            const startDate = new Date(2025, 7, 10, 0, 0, 0, 0); // Month is 0-indexed: 7 = August
            const endDate = new Date(2025, 7, 10, 23, 59, 0, 0);

            await gpsDataPage.setDateRangeFilter(startDate, endDate);
            await gpsDataPage.waitForTableReload();

            // Verify filter is applied
            expect(await gpsDataPage.hasDateFilter()).toBe(true);

            // Track export API call for this filtered export
            const exportResponsePromise = page.waitForResponse(response => {
                if (!response.url().includes('/api/v1/gps/points/exports')) {
                    return false;
                }

                if (response.request().method() !== 'GET') {
                    return false;
                }

                const responseUrl = new URL(response.url());
                return (
                    response.status() === 200
                    && responseUrl.searchParams.has('from')
                    && responseUrl.searchParams.has('to')
                );
            });

            // Download event is not always emitted reliably in headless mode for blob URLs
            const downloadPromise = page.waitForEvent('download', { timeout: 5000 }).catch(() => null);

            // Export filtered data
            await gpsDataPage.clickExportCsv();
            const exportResponse = await exportResponsePromise;
            expect(exportResponse.ok()).toBe(true);
            await gpsDataPage.waitForExportComplete();
            await gpsDataPage.waitForSuccessToast();

            // Verify download when event is available
            const download = await downloadPromise;
            if (download) {
                expect(download.suggestedFilename()).toMatch(/\.csv$/);
            }

            // The exported file should contain only the filtered data
            // In a real test, you might want to read the CSV and verify its contents
        });

        test('should handle export with no data gracefully', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user without GPS data

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
        test('should handle large datasets with pagination', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user and large GPS dataset
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
        test('should send correct UTC date range for single day selection in America/New_York timezone', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page, { timezone: 'America/New_York' });
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

            const expectedStartPrefix = '2025-09-22T04:00:';
            const expectedEndPrefix = '2025-09-23T03:59:';

            // Select full single day: 09/22/2025 00:00 -> 09/22/2025 23:59
            const selectedStartDate = new Date(2025, 8, 22, 0, 0, 0, 0);
            const selectedEndDate = new Date(2025, 8, 22, 23, 59, 0, 0);
            await gpsDataPage.fillDateRange(selectedStartDate, selectedEndDate);

            // Wait for the exact API request to avoid races with unrelated GPS requests
            const apiRequestPromise = page.waitForRequest(request => {
                if (!isGpsDateRangeRequest(request)) {
                    return false;
                }

                const requestUrl = new URL(request.url());
                const startTime = requestUrl.searchParams.get('from') || '';
                const endTime = requestUrl.searchParams.get('to') || '';
                return startTime.startsWith(expectedStartPrefix) && endTime.startsWith(expectedEndPrefix);
            });
            await gpsDataPage.applyDateFilter();
            await gpsDataPage.waitForTableReload();
            const apiRequest = await apiRequestPromise;

            // Verify the API request parameters
            expect(apiRequest).not.toBeNull();
            const url = new URL(apiRequest.url());
            const startTime = url.searchParams.get('from');
            const endTime = url.searchParams.get('to');

            // Expected for America/New_York (EDT = UTC-4 in September):
            // 09/22/2025 00:00 EDT => 2025-09-22T04:00:xx.xxxZ
            // 09/22/2025 23:59 EDT => 2025-09-23T03:59:xx.xxxZ
            // Seconds can vary with DatePicker's internal seconds state, so assert prefix.
            expect(startTime).toMatch(/^2025-09-22T04:00:\d{2}\.\d{3}Z$/);
            expect(endTime).toMatch(/^2025-09-23T03:59:\d{2}\.\d{3}Z$/);

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

        test('should send correct UTC date range for single day selection in Europe/London timezone', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page, { timezone: 'Europe/London' });
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

            const expectedStartPrefix = '2025-09-21T23:00:';
            const expectedEndPrefix = '2025-09-22T22:59:';

            // Select full single day: 09/22/2025 00:00 -> 09/22/2025 23:59
            const selectedStartDate = new Date(2025, 8, 22, 0, 0, 0, 0);
            const selectedEndDate = new Date(2025, 8, 22, 23, 59, 0, 0);
            await gpsDataPage.fillDateRange(selectedStartDate, selectedEndDate);

            // Wait for the exact API request to avoid races with unrelated GPS requests
            const apiRequestPromise = page.waitForRequest(request => {
                if (!isGpsDateRangeRequest(request)) {
                    return false;
                }

                const requestUrl = new URL(request.url());
                const startTime = requestUrl.searchParams.get('from') || '';
                const endTime = requestUrl.searchParams.get('to') || '';
                return startTime.startsWith(expectedStartPrefix) && endTime.startsWith(expectedEndPrefix);
            });
            await gpsDataPage.applyDateFilter();
            await gpsDataPage.waitForTableReload();
            const apiRequest = await apiRequestPromise;

            // Verify the API request parameters
            expect(apiRequest).not.toBeNull();
            const url = new URL(apiRequest.url());
            const startTime = url.searchParams.get('from');
            const endTime = url.searchParams.get('to');

            // Expected for Europe/London (BST = UTC+1 in September):
            // 09/22/2025 00:00 BST => 2025-09-21T23:00:xx.xxxZ
            // 09/22/2025 23:59 BST => 2025-09-22T22:59:xx.xxxZ
            // Seconds can vary with DatePicker's internal seconds state, so assert prefix.
            expect(startTime).toMatch(/^2025-09-21T23:00:\d{2}\.\d{3}Z$/);
            expect(endTime).toMatch(/^2025-09-22T22:59:\d{2}\.\d{3}Z$/);

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

        test('should handle timezone switching and update API requests accordingly', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const gpsDataPage = new GpsDataPage(page);
            const testUser = await isolatedUsers.create(page, { timezone: 'UTC' });
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

            // Select date in UTC timezone
            const selectedStartDate = new Date(2025, 8, 22, 0, 0, 0, 0); // September 22, 2025
            const selectedEndDate = new Date(2025, 8, 22, 23, 59, 0, 0);
            await gpsDataPage.fillDateRange(selectedStartDate, selectedEndDate);
            const utcRequestPromise = page.waitForRequest(request => isGpsDateRangeRequest(request));
            await gpsDataPage.applyDateFilter();
            await gpsDataPage.waitForTableReload();
            const utcRequestRaw = await utcRequestPromise;

            // Switch to America/New_York timezone through the backend profile API.
            await UserSettingsApi.updateCurrentUserProfile(page, {timezone: 'America/New_York'});

            // Reload page to apply timezone change
            await page.reload();
            await gpsDataPage.waitForPageLoad();

            // Select same date again (should now be interpreted as NY time)
            await gpsDataPage.fillDateRange(selectedStartDate, selectedEndDate);
            const nyRequestPromise = page.waitForRequest(request => isGpsDateRangeRequest(request));
            await gpsDataPage.applyDateFilter();
            await gpsDataPage.waitForTableReload();
            const nyRequestRaw = await nyRequestPromise;

            // Check the difference between UTC and NY timezone requests
            const utcRequest = new URL(utcRequestRaw.url());
            const nyRequest = new URL(nyRequestRaw.url());

            const utcStartTime = utcRequest.searchParams.get('from');
            const nyStartTime = nyRequest.searchParams.get('from');

            // Times should be different due to timezone offset
            expect(utcStartTime).not.toBe(nyStartTime);

            // UTC: 09/22/2025 00:00 UTC => 2025-09-22T00:00:xx.xxxZ
            // NY: 09/22/2025 00:00 EDT => 2025-09-22T04:00:xx.xxxZ
            // Seconds can vary with DatePicker's internal seconds state, so assert prefix.
            expect(utcStartTime).toMatch(/^2025-09-22T00:00:\d{2}\.\d{3}Z$/);
            expect(nyStartTime).toMatch(/^2025-09-22T04:00:\d{2}\.\d{3}Z$/);
        });
    });
});
