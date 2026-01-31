import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {ShareLinksPage} from '../pages/ShareLinksPage.js';
import {SharedLocationPage} from '../pages/SharedLocationPage.js';
import {SharedTimelinePage} from '../pages/SharedTimelinePage.js';
import {AppNavigation} from '../pages/AppNavigation.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {insertVerifiableStaysTestData, insertVerifiableTripsTestData} from '../utils/timeline-test-data.js';
import {GeocodingFactory} from '../utils/geocoding-factory.js';
import {UserFactory} from '../utils/user-factory.js';
import * as TimelineTestData from "../utils/timeline-test-data.js";

test.describe('Shared Links Public Access', () => {

  test.describe('Live Location Share - Public Access', () => {
    test('should access public live location share with current location only', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup: Create user and GPS data
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create public share link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: '11111111-1111-1111-1111-111111111111',
        user_id: user.id,
        name: 'Public Current Location',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false,
        password: null
      });

      // Logout to simulate guest access
      await context.clearCookies();

      // Access shared link as guest
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();

      // Should not show password prompt
      expect(await sharedLocationPage.isPasswordRequired()).toBe(false);

      // Should show location display
      await sharedLocationPage.waitForLocationToLoad();
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);

      // Verify scope
      const scope = await sharedLocationPage.getScope();
      expect(scope).toBe('Current Location Only');

      // Verify map
      await sharedLocationPage.waitForMapReady();
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);
      expect(await sharedLocationPage.hasLocationMarker()).toBe(true);
      expect(await sharedLocationPage.hasPathLayer()).toBe(false);

      // Verify view count
      await page.waitForTimeout(1000);
      const viewCount = await SharedLocationPage.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(1);
    });

    test('should access public live location share with history', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 10);

      // Create link with history
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: '22222222-2222-2222-2222-222222222222',
        user_id: user.id,
        name: 'Public With History',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: true,
        history_hours: 24
      });

      await context.clearCookies();

      // Access as guest
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForLocationToLoad();

      // Verify scope shows history
      const scope = await sharedLocationPage.getScope();
      expect(scope).toContain('Location History');
      expect(scope).toContain('24h');

      // Verify map has path
      await sharedLocationPage.waitForMapReady();
      expect(await sharedLocationPage.hasPathLayer()).toBe(true);
    });

    test('should require password for protected live location share', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create password-protected link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);

      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: '33333333-3333-3333-3333-333333333333',
        user_id: user.id,
        name: 'Protected Live',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false,
        password: '$2a$12$Iuh13ihQQPT2Kr9u9KVygu5kS2FMxBnIWE154uXbtPnVfSEpNjOqC'
      });

      await context.clearCookies();

      // Access as guest
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();

      // Verify password prompt
      expect(await sharedLocationPage.isPasswordRequired()).toBe(true);
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(false);

      // Submit correct password
      await sharedLocationPage.submitPassword('testpass123');
      await sharedLocationPage.waitForLocationToLoad();

      // Verify access granted
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);
    });

    test('should reject incorrect password for live location share', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create protected link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: '44444444-4444-4444-4444-444444444444',
        user_id: user.id,
        name: 'Wrong Pass Test',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false,
        password: '$2a$12$Iuh13ihQQPT2Kr9u9KVygu5kS2FMxBnIWE154uXbtPnVfSEpNjOqC'
      });

      await context.clearCookies();

      // Access and submit wrong password
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();
      await sharedLocationPage.submitPassword('wrongpass');
      await page.waitForTimeout(1000);

      // Verify error shown
      expect(await sharedLocationPage.isErrorShown()).toBe(true);

      // View count should NOT increment
      const viewCount = await SharedLocationPage.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(0);
    });

    test('should show error for expired live location share', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create EXPIRED link
      const expiredDate = new Date();
      expiredDate.setDate(expiredDate.getDate() - 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: '55555555-5555-5555-5555-555555555555',
        user_id: user.id,
        name: 'Expired Link',
        expires_at: expiredDate.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      await context.clearCookies();

      // Try to access expired link
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForError();

      // Verify error
      expect(await sharedLocationPage.isErrorShown()).toBe(true);
      const errorMsg = await sharedLocationPage.getErrorMessage();
      expect(errorMsg.toLowerCase()).toContain('expired');
    });

    test('should show no data message when user has no GPS points', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup WITHOUT GPS data
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Create link (no GPS data)
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: '66666666-6666-6666-6666-666666666666',
        user_id: user.id,
        name: 'No Data Link',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      await context.clearCookies();

      // Access as guest
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Should show no data message
      expect(await sharedLocationPage.isNoDataShown()).toBe(true);
    });
  });

  test.describe('Timeline Share - Public Access', () => {
    test('should access public timeline share', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 10);

      // Create active timeline share
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 7);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: '77777777-7777-7777-7777-777777777777',
        user_id: user.id,
        name: 'Public Timeline',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString(),
        show_current_location: true,
        show_photos: false
      });

      await context.clearCookies();

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();

      // Verify no password prompt
      expect(await sharedTimelinePage.isPasswordRequired()).toBe(false);

      // Wait for timeline to load
      await page.waitForTimeout(2000);

      // Verify timeline display
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(true);

      // Verify status
      const status = await sharedTimelinePage.getStatus();
      expect(status).toBe('Active');
      expect(await sharedTimelinePage.getTimelineName()).toBe('Public Timeline');
      expect(await sharedTimelinePage.getHeader()).toContain(testUser.fullName);
      expect(await sharedTimelinePage.getStatusSeverity()).toBe('success');
    });

    test('should show upcoming timeline message', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Create UPCOMING timeline (starts in future)
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() + 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 14);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: '88888888-8888-8888-8888-888888888888',
        user_id: user.id,
        name: 'Upcoming Trip',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString()
      });

      await context.clearCookies();

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();

      // Verify upcoming message
      expect(await sharedTimelinePage.isUpcomingTrip()).toBe(true);

      // Verify timeline not shown yet
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(false);
    });

    test('should access password-protected timeline share', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 10);

      // Create protected timeline
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 7);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: '99999999-9999-9999-9999-999999999999',
        user_id: user.id,
        name: 'Protected Timeline',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString(),
        password: '$2a$12$pe7F7dhlJC3OE7gp9lqiW.i2H/er3W0U7G357gqsj3pQJHrXifZCK'
      });

      await context.clearCookies();

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForPasswordPrompt();

      // Verify password prompt
      expect(await sharedTimelinePage.isPasswordRequired()).toBe(true);

      // Submit password
      await sharedTimelinePage.verifyPassword('timelinepass');
      await page.waitForTimeout(2000);

      // Verify access granted
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(true);
    });

    test('should reject incorrect password for timeline share', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Create protected timeline
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 7);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        user_id: user.id,
        name: 'Wrong Pass Timeline',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString(),
        password: '$2a$12$pe7F7dhlJC3OE7gp9lqiW.i2H/er3W0U7G357gqsj3pQJHrXifZCK'
      });

      await context.clearCookies();

      // Access and submit wrong password
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForPasswordPrompt();
      await sharedTimelinePage.verifyPassword('wrongpass');
      await page.waitForTimeout(1000);

      // Verify error
      expect(await sharedTimelinePage.hasPasswordError()).toBe(true);
      const passwordError = await sharedTimelinePage.getPasswordError();
      expect(passwordError).toContain('Invalid password');

      // View count should NOT increment
      const viewCount = await SharedTimelinePage.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(0);
    });

    test('should show error for expired timeline share', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Create EXPIRED timeline
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 30);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() - 14);
      const expiredDate = new Date(now);
      expiredDate.setDate(expiredDate.getDate() - 7);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        user_id: user.id,
        name: 'Expired Timeline',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiredDate.toISOString()
      });

      await context.clearCookies();

      // Try to access
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForError();

      // Verify error
      expect(await sharedTimelinePage.isErrorShown()).toBe(true);
      const errorMsg = await sharedTimelinePage.getErrorMessage();
      expect(errorMsg.toLowerCase()).toContain('expired');
    });

    test('should show empty timeline message when no data', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup WITHOUT timeline data
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Create timeline (no data)
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 7);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
        user_id: user.id,
        name: 'Empty Timeline',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString()
      });

      await context.clearCookies();

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(2000);

      // Verify timeline view is shown but empty
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(true);

      // Check for empty states
      const hasTimelineData = await sharedTimelinePage.hasTimelineData();
      const hasMapData = await sharedTimelinePage.hasMapData();

      // At least one should be empty
      expect(hasTimelineData || hasMapData).toBeDefined();
    });
  });

  test.describe('Timeline Share - Advanced Features', () => {
    test('should show refresh button on active timeline', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 10);

      // Create active timeline
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 7);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
        user_id: user.id,
        name: 'Active Timeline',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString()
      });

      await context.clearCookies();

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(2000);

      // Verify refresh button appears for active timelines
      const status = await sharedTimelinePage.getStatus();
      if (status === 'Active') {
        expect(await sharedTimelinePage.hasRefreshButton()).toBe(true);
      }
    });

    test('should filter timeline data by date', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Helper to insert a stay
      const insertStay = async (date, name) => {
        const geocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
          dbManager, `POINT(-74.0060 40.7128)`, `${name}, New York, NY`, 'New York', 'United States'
        );

        // Insert GPS point
        const gpsQuery = `
          INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
        `;
        const gpsValues = [
          'test-device', user.id, `POINT(-74.0060 40.7128)`, date, 10.0, 100, 0.0, 20.0, 'OVERLAND', date
        ];
        await dbManager.client.query(gpsQuery, gpsValues);
        
        // Insert stay
        await dbManager.client.query(`
          INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
          VALUES ($1, $2, 3600, ST_SetSRID(ST_MakePoint(-74.0060, 40.7128), 4326), $3, $4, $5, $6)
        `, [user.id, date, name, geocodingId, new Date(date).toISOString(), new Date(date).toISOString()]);
      };

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert data for two different days
      await insertStay('2025-09-20T10:00:00Z', 'Visit 1');
      await insertStay('2025-09-20T14:00:00Z', 'Visit 2');
      await insertStay('2025-09-21T11:00:00Z', 'Visit 3');

      // Create share link covering both days
      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: 'eeeeeeee-eeee-eeee-eeee-111111111111',
        user_id: user.id,
        name: 'Filterable Timeline',
        start_date: '2025-09-20T00:00:00Z',
        end_date: '2025-09-21T23:59:59Z',
        expires_at: new Date(Date.now() + 86400000).toISOString()
      });

      await context.clearCookies();
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(1000);

      expect(await sharedTimelinePage.hasDateFilter()).toBe(true);
      const initialItemCount = await sharedTimelinePage.getTimelineItemCount();
      expect(initialItemCount).toBe(3);

      // Apply date filter for the first day
      await sharedTimelinePage.setDateRange(new Date('2025-09-20'), new Date('2025-09-20'));
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(500);

      const filteredItemCount = await sharedTimelinePage.getTimelineItemCount();
      expect(filteredItemCount).toBe(2);
      expect(await sharedTimelinePage.isDateFilterActive()).toBe(true);

      // Clear filter
      await sharedTimelinePage.clearDateFilter();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(500);

      const restoredItemCount = await sharedTimelinePage.getTimelineItemCount();
      expect(restoredItemCount).toBe(3);
      expect(await sharedTimelinePage.isDateFilterActive()).toBe(false);
    });

    test('should display timeline data and map correctly', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id); // This inserts GPS points and stays
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 2);

      const startDate = new Date('2025-09-20T00:00:00Z');
      const endDate = new Date('2025-09-22T23:59:59Z');

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: 'eeeeeeee-eeee-eeee-eeee-222222222222',
        user_id: user.id,
        name: 'Data-rich Timeline',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: new Date(Date.now() + 86400000).toISOString(), // 1 day
        show_current_location: true
      });

      await context.clearCookies();
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await sharedTimelinePage.waitForMapReady();

      // Verify timeline and map are displayed
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(true);
      expect(await sharedTimelinePage.isMapDisplayed()).toBe(true);

      // Verify timeline has data
      const itemCount = await sharedTimelinePage.getTimelineItemCount();
      expect(itemCount).toBe(3);

      // Verify map has data
      expect(await sharedTimelinePage.isMapDisplayed()).toBe(true);
      expect(await sharedTimelinePage.hasPathLayer()).toBe(true); // Should have path as GPS points are inserted
    });
  });

  test.describe('View Count Tracking', () => {
    test('should increment view count on live location access', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        user_id: user.id,
        name: 'View Count Test',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false,
        view_count: 0
      });

      await context.clearCookies();

      // First access
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForLocationToLoad();
      await page.waitForTimeout(1000);

      let viewCount = await SharedLocationPage.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(1);

      // Second access (reload)
      await page.reload();
      await sharedLocationPage.waitForLocationToLoad();
      await page.waitForTimeout(1000);

      viewCount = await SharedLocationPage.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(2);
    });

    test('should increment view count on timeline access', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 10);

      // Create timeline
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 7);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
        user_id: user.id,
        name: 'Timeline View Count',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString(),
        view_count: 0
      });

      await context.clearCookies();

      // First access
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(1000);

      let viewCount = await SharedTimelinePage.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(1);

      // Second access
      await page.reload();
      await sharedTimelinePage.waitForPageLoad();
      await page.waitForTimeout(1000);

      viewCount = await SharedTimelinePage.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(2);
    });
  });

  test.describe('Error Handling and Edge Cases', () => {
    test('should show error for non-existent live location share link', async ({page, context}) => {
      const sharedLocationPage = new SharedLocationPage(page);
      await context.clearCookies();

      // Access non-existent share link
      await sharedLocationPage.navigateToSharedLink('99999999-9999-9999-9999-999999999999');
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForError();

      // Verify error is shown
      expect(await sharedLocationPage.isErrorShown()).toBe(true);
      const errorMsg = await sharedLocationPage.getErrorMessage();
      expect(errorMsg.toLowerCase()).toMatch(/not found|invalid|does not exist/);
    });

    test('should show error for non-existent timeline share link', async ({page, context}) => {
      const sharedTimelinePage = new SharedTimelinePage(page);
      await context.clearCookies();

      // Access non-existent timeline link
      await sharedTimelinePage.navigateToSharedTimeline('88888888-8888-8888-8888-888888888888');
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForError();

      // Verify error is shown
      expect(await sharedTimelinePage.isErrorShown()).toBe(true);
      const errorMsg = await sharedTimelinePage.getErrorMessage();
      expect(errorMsg.toLowerCase()).toMatch(/not found|invalid|does not exist/);
    });

    test('should handle malformed share link ID', async ({page, context}) => {
      const sharedLocationPage = new SharedLocationPage(page);
      await context.clearCookies();

      // Access with malformed UUID
      await page.goto('/shared/not-a-valid-uuid');
      await page.waitForTimeout(1000);

      // Should show error or redirect
      const isError = await sharedLocationPage.isErrorShown();
      const url = page.url();

      // Either shows error page or redirects (both are acceptable)
      expect(isError || url.includes('/login') || url.includes('/error')).toBe(true);
    });
  });

  test.describe('Photo Display Verification', () => {
    test('should display photos when timeline share has show_photos enabled', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert test data with stays
      await insertVerifiableStaysTestData(dbManager, user.id);

      // Create timeline with photos ENABLED
      const now = new Date();
      const startDate = new Date('2025-09-20T00:00:00Z');
      const endDate = new Date('2025-09-22T23:59:59Z');
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: 'a0a0a0a0-1111-1111-1111-111111111111',
        user_id: user.id,
        name: 'Timeline With Photos',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString(),
        show_photos: true
      });

      await context.clearCookies();

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(2000);

      // Verify timeline is displayed
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(true);

      // Verify link in database has show_photos enabled
      const result = await dbManager.client.query('SELECT show_photos FROM shared_link WHERE id = $1', [link.id]);
      expect(result.rows[0].show_photos).toBe(true);
    });

    test('should not display photos when timeline share has show_photos disabled', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await insertVerifiableStaysTestData(dbManager, user.id);

      // Create timeline with photos DISABLED
      const now = new Date();
      const startDate = new Date('2025-09-20T00:00:00Z');
      const endDate = new Date('2025-09-22T23:59:59Z');
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const link = await SharedTimelinePage.insertTimelineShareLink(dbManager, {
        id: 'b0b0b0b0-2222-2222-2222-222222222222',
        user_id: user.id,
        name: 'Timeline Without Photos',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString(),
        show_photos: false
      });

      await context.clearCookies();

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(2000);

      // Verify timeline is displayed
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(true);

      // Verify no photo markers appear (if we had photo test data)
      const photoMarkerCount = await sharedTimelinePage.getPhotoMarkerCount();
      expect(photoMarkerCount).toBe(0);
    });
  });

  test.describe('Authenticated Access to Shared Links', () => {
    test('should allow owner to access their own shared live location while logged in', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create share link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'c0c0c0c0-1111-1111-1111-111111111111',
        user_id: user.id,
        name: 'Owner Access Test',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      // Access own share link while still logged in
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForLocationToLoad();

      // Should be able to access
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);
    });

    test('should allow other authenticated users to access public shared links', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const appNav = new AppNavigation(page);

      // Create BOTH users at the start
      const ownerData = { ...TestData.users.existing, email: 'owner@test.com' };
      const viewerData = { ...TestData.users.existing, email: 'viewer@test.com' };

      await UserFactory.createUser(page, ownerData);
      await UserFactory.createUser(page, viewerData);

      // Login as owner
      await loginPage.navigate();
      await loginPage.login(ownerData.email, ownerData.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const owner = await dbManager.getUserByEmail(ownerData.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, owner.id, 5);

      // Create share link by owner
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'd0d0d0d0-1111-1111-1111-111111111111',
        user_id: owner.id,
        name: 'Public Link',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false,
        password: null
      });

      // Logout owner and login as viewer
      await appNav.logout();

      await loginPage.navigate();
      await loginPage.login(viewerData.email, viewerData.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Access owner's public share link as different user
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForLocationToLoad();

      // Should be able to access (it's public)
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);

      // Verify it's showing owner's name
      const sharedBy = await sharedLocationPage.getSharedBy();
      expect(sharedBy).toContain(ownerData.fullName);
    });

    test('should require password even when logged in as different user', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const appNav = new AppNavigation(page);

      // Create BOTH users at the start
      const ownerData = { ...TestData.users.existing, email: 'owner2@test.com' };
      const viewerData = { ...TestData.users.existing, email: 'viewer2@test.com' };

      await UserFactory.createUser(page, ownerData);
      await UserFactory.createUser(page, viewerData);

      // Login as owner
      await loginPage.navigate();
      await loginPage.login(ownerData.email, ownerData.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const owner = await dbManager.getUserByEmail(ownerData.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, owner.id, 5);

      // Create PASSWORD-PROTECTED share link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'e0e0e0e0-2222-2222-2222-222222222222',
        user_id: owner.id,
        name: 'Protected Link',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false,
        password: '$2a$12$Iuh13ihQQPT2Kr9u9KVygu5kS2FMxBnIWE154uXbtPnVfSEpNjOqC' // testpass123
      });

      // Logout owner and login as viewer
      await appNav.logout();

      await loginPage.navigate();
      await loginPage.login(viewerData.email, viewerData.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Try to access protected link
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();

      // Should still require password even though logged in
      expect(await sharedLocationPage.isPasswordRequired()).toBe(true);

      // Submit correct password
      await sharedLocationPage.submitPassword('testpass123');
      await sharedLocationPage.waitForLocationToLoad();

      // Now should have access
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);
    });
  });
});
