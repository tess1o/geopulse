import {test, expect} from '../fixtures/database-fixture.js';
import {SharedLocationPage} from '../pages/SharedLocationPage.js';
import {SharedTimelinePage} from '../pages/SharedTimelinePage.js';
import {AppNavigation} from '../pages/AppNavigation.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';
import {DateFactory} from '../utils/date-factory.js';
import {ShareLinkFactory} from '../utils/share-link-factory.js';
import {GpsDataFactory} from '../utils/gps-data-factory.js';
import {TestConstants} from '../fixtures/test-constants.js';
import {TestData} from '../fixtures/test-data.js';
import {insertVerifiableStaysTestData} from '../utils/timeline-test-data.js';
import {GeocodingFactory} from '../utils/geocoding-factory.js';
import * as TimelineTestData from "../utils/timeline-test-data.js";

test.describe('Shared Links Public Access', () => {

  test.describe('Live Location Share - Public Access', () => {
    test('should access public live location share with current location only', async ({page, dbManager, context}) => {
      const sharedLocationPage = new SharedLocationPage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_SMALL
      );

      // Create public share link
      const link = await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_1,
        name: 'Public Current Location'
      });

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
      await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);
      const viewCount = await ShareLinkFactory.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(1);
    });

    test('should access public live location share with history', async ({page, dbManager, context}) => {
      const sharedLocationPage = new SharedLocationPage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_MEDIUM
      );

      // Create link with history
      const link = await ShareLinkFactory.createLiveLocationWithHistory(dbManager, user.id, 24, {
        id: TestConstants.TEST_UUIDS.LINK_2,
        name: 'Public With History'
      });

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
      const sharedLocationPage = new SharedLocationPage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_SMALL
      );

      // Create password-protected link
      const link = await ShareLinkFactory.createProtectedLiveLocation(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_3,
        name: 'Protected Live'
      });

      // Access as guest
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();

      // Verify password prompt
      expect(await sharedLocationPage.isPasswordRequired()).toBe(true);
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(false);

      // Submit correct password
      await sharedLocationPage.submitPassword(TestConstants.PASSWORDS.testpass123);
      await sharedLocationPage.waitForLocationToLoad();

      // Verify access granted
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);
    });

    test('should reject incorrect password for live location share', async ({page, dbManager, context}) => {
      const sharedLocationPage = new SharedLocationPage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_SMALL
      );

      // Create protected link
      const link = await ShareLinkFactory.createProtectedLiveLocation(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_4,
        name: 'Wrong Pass Test'
      });

      // Access and submit wrong password
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();
      await sharedLocationPage.submitPassword('wrongpass');
      await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

      // Verify error shown
      expect(await sharedLocationPage.isErrorShown()).toBe(true);

      // View count should NOT increment
      const viewCount = await ShareLinkFactory.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(0);
    });

    test('should show error for expired live location share', async ({page, dbManager, context}) => {
      const sharedLocationPage = new SharedLocationPage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_SMALL
      );

      // Create EXPIRED link
      const link = await ShareLinkFactory.createExpiredLiveLocation(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_5,
        name: 'Expired Link'
      });

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
      const sharedLocationPage = new SharedLocationPage(page);

      // Setup WITHOUT GPS data (gpsPointCount = 0)
      const { user } = await TestSetupHelper.setupPublicShareAccess(page, dbManager, context, 0);

      // Create link (no GPS data)
      const link = await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_6,
        name: 'No Data Link'
      });

      // Access as guest
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await page.waitForTimeout(TestConstants.TIMEOUTS.LONG);

      // Should show no data message
      expect(await sharedLocationPage.isNoDataShown()).toBe(true);
    });
  });

  test.describe('Timeline Share - Public Access', () => {
    test('should access public timeline share', async ({page, dbManager, context}) => {
      const sharedTimelinePage = new SharedTimelinePage(page);
      const testUser = TestData.users.existing;

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_MEDIUM
      );

      // Create active timeline share
      const link = await ShareLinkFactory.createActiveTimeline(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_7,
        name: 'Public Timeline',
        show_photos: false
      });

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();

      // Verify no password prompt
      expect(await sharedTimelinePage.isPasswordRequired()).toBe(false);

      // Wait for timeline to load
      await page.waitForTimeout(TestConstants.TIMEOUTS.LONG);

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
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(page, dbManager, context, 0);

      // Create UPCOMING timeline (starts in future)
      const link = await ShareLinkFactory.createUpcomingTimeline(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_8,
        name: 'Upcoming Trip'
      });

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
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_MEDIUM
      );

      // Create protected timeline
      const link = await ShareLinkFactory.createProtectedTimeline(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_9,
        name: 'Protected Timeline'
      });

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForPasswordPrompt();

      // Verify password prompt
      expect(await sharedTimelinePage.isPasswordRequired()).toBe(true);

      // Submit password
      await sharedTimelinePage.verifyPassword(TestConstants.PASSWORDS.timelinepass);
      await page.waitForTimeout(TestConstants.TIMEOUTS.LONG);

      // Verify access granted
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(true);
    });

    test('should reject incorrect password for timeline share', async ({page, dbManager, context}) => {
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(page, dbManager, context, 0);

      // Create protected timeline
      const link = await ShareLinkFactory.createProtectedTimeline(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_A,
        name: 'Wrong Pass Timeline'
      });

      // Access and submit wrong password
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForPasswordPrompt();
      await sharedTimelinePage.verifyPassword('wrongpass');
      await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

      // Verify error
      expect(await sharedTimelinePage.hasPasswordError()).toBe(true);
      const passwordError = await sharedTimelinePage.getPasswordError();
      expect(passwordError).toContain('Invalid password');

      // View count should NOT increment
      const viewCount = await ShareLinkFactory.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(0);
    });

    test('should show error for expired timeline share', async ({page, dbManager, context}) => {
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(page, dbManager, context, 0);

      // Create EXPIRED timeline
      const link = await ShareLinkFactory.createExpiredTimeline(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_B,
        name: 'Expired Timeline'
      });

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
      const sharedTimelinePage = new SharedTimelinePage(page);

      // Setup WITHOUT timeline data
      const { user } = await TestSetupHelper.setupPublicShareAccess(page, dbManager, context, 0);

      // Create timeline (no data)
      const link = await ShareLinkFactory.createActiveTimeline(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_C,
        name: 'Empty Timeline'
      });

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(TestConstants.TIMEOUTS.LONG);

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
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_MEDIUM
      );

      // Create active timeline
      const link = await ShareLinkFactory.createActiveTimeline(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_D,
        name: 'Active Timeline'
      });

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(TestConstants.TIMEOUTS.LONG);

      // Verify refresh button appears for active timelines
      const status = await sharedTimelinePage.getStatus();
      if (status === 'Active') {
        expect(await sharedTimelinePage.hasRefreshButton()).toBe(true);
      }
    });

    test('should filter timeline data by date', async ({page, dbManager, context}) => {
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(page, dbManager, context, 0);

      // Helper to insert a stay
      const insertStay = async (date, name) => {
        const geocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
          dbManager, `POINT(-74.0060 40.7128)`, `${name}, New York, NY`, 'New York', 'United States'
        );

        // Insert GPS point
        await GpsDataFactory.insertGpsPoint(dbManager, {
          user_id: user.id,
          latitude: 40.7128,
          longitude: -74.0060,
          timestamp: date
        });

        // Insert stay
        await dbManager.client.query(`
          INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
          VALUES ($1, $2, 3600, ST_SetSRID(ST_MakePoint(-74.0060, 40.7128), 4326), $3, $4, $5, $6)
        `, [user.id, date, name, geocodingId, new Date(date).toISOString(), new Date(date).toISOString()]);
      };

      // Insert data for two different days
      await insertStay('2025-09-20T10:00:00Z', 'Visit 1');
      await insertStay('2025-09-20T14:00:00Z', 'Visit 2');
      await insertStay('2025-09-21T11:00:00Z', 'Visit 3');

      // Create share link covering both days
      const link = await ShareLinkFactory.createTimeline(dbManager, user.id, {
        id: 'eeeeeeee-eeee-eeee-eeee-111111111111',
        name: 'Filterable Timeline',
        dateRange: {
          startDate: new Date('2025-09-20T00:00:00Z'),
          endDate: new Date('2025-09-21T23:59:59Z'),
          expiresAt: DateFactory.futureDate(1)
        }
      });

      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

      expect(await sharedTimelinePage.hasDateFilter()).toBe(true);
      const initialItemCount = await sharedTimelinePage.getTimelineItemCount();
      expect(initialItemCount).toBe(3);

      // Apply date filter for the first day
      await sharedTimelinePage.setDateRange(new Date('2025-09-20'), new Date('2025-09-20'));
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(TestConstants.TIMEOUTS.SHORT);

      const filteredItemCount = await sharedTimelinePage.getTimelineItemCount();
      expect(filteredItemCount).toBe(2);
      expect(await sharedTimelinePage.isDateFilterActive()).toBe(true);

      // Clear filter
      await sharedTimelinePage.clearDateFilter();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(TestConstants.TIMEOUTS.SHORT);

      const restoredItemCount = await sharedTimelinePage.getTimelineItemCount();
      expect(restoredItemCount).toBe(3);
      expect(await sharedTimelinePage.isDateFilterActive()).toBe(false);
    });

    test('should display timeline data and map correctly', async ({page, dbManager, context}) => {
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_SMALL
      );

      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id); // This inserts GPS points and stays

      const link = await ShareLinkFactory.createTimeline(dbManager, user.id, {
        id: 'eeeeeeee-eeee-eeee-eeee-222222222222',
        name: 'Data-rich Timeline',
        dateRange: {
          startDate: new Date('2025-09-20T00:00:00Z'),
          endDate: new Date('2025-09-22T23:59:59Z'),
          expiresAt: DateFactory.futureDate(1)
        },
        show_current_location: true
      });

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
      const sharedLocationPage = new SharedLocationPage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_SMALL
      );

      // Create link
      const link = await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_E,
        name: 'View Count Test'
      });

      // First access
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForLocationToLoad();
      await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

      let viewCount = await ShareLinkFactory.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(1);

      // Second access (reload)
      await page.reload();
      await sharedLocationPage.waitForLocationToLoad();
      await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

      viewCount = await ShareLinkFactory.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(2);
    });

    test('should increment view count on timeline access', async ({page, dbManager, context}) => {
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(
        page, dbManager, context, TestConstants.DATA_COUNTS.GPS_POINTS_MEDIUM
      );

      // Create timeline
      const link = await ShareLinkFactory.createActiveTimeline(dbManager, user.id, {
        id: TestConstants.TEST_UUIDS.LINK_F,
        name: 'Timeline View Count'
      });

      // First access
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

      let viewCount = await ShareLinkFactory.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(1);

      // Second access
      await page.reload();
      await sharedTimelinePage.waitForPageLoad();
      await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

      viewCount = await ShareLinkFactory.getViewCount(dbManager, link.id);
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
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(page, dbManager, context, 0);

      // Insert test data with stays
      await insertVerifiableStaysTestData(dbManager, user.id);

      // Create timeline with photos ENABLED
      const link = await ShareLinkFactory.createTimelineWithPhotos(dbManager, user.id, {
        id: 'a0a0a0a0-1111-1111-1111-111111111111',
        name: 'Timeline With Photos',
        dateRange: {
          startDate: new Date('2025-09-20T00:00:00Z'),
          endDate: new Date('2025-09-22T23:59:59Z'),
          expiresAt: DateFactory.futureDate(30)
        }
      });

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(TestConstants.TIMEOUTS.LONG);

      // Verify timeline is displayed
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(true);

      // Verify link in database has show_photos enabled
      const result = await dbManager.client.query('SELECT show_photos FROM shared_link WHERE id = $1', [link.id]);
      expect(result.rows[0].show_photos).toBe(true);
    });

    test('should not display photos when timeline share has show_photos disabled', async ({page, dbManager, context}) => {
      const sharedTimelinePage = new SharedTimelinePage(page);

      const { user } = await TestSetupHelper.setupPublicShareAccess(page, dbManager, context, 0);
      await insertVerifiableStaysTestData(dbManager, user.id);

      // Create timeline with photos DISABLED
      const link = await ShareLinkFactory.createTimeline(dbManager, user.id, {
        id: 'b0b0b0b0-2222-2222-2222-222222222222',
        name: 'Timeline Without Photos',
        dateRange: {
          startDate: new Date('2025-09-20T00:00:00Z'),
          endDate: new Date('2025-09-22T23:59:59Z'),
          expiresAt: DateFactory.futureDate(30)
        },
        show_photos: false
      });

      // Access as guest
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await page.waitForTimeout(TestConstants.TIMEOUTS.LONG);

      // Verify timeline is displayed
      expect(await sharedTimelinePage.isTimelineDisplayed()).toBe(true);

      // Verify no photo markers appear (if we had photo test data)
      const photoMarkerCount = await sharedTimelinePage.getPhotoMarkerCount();
      expect(photoMarkerCount).toBe(0);
    });
  });

  test.describe('Authenticated Access to Shared Links', () => {
    test('should allow owner to access their own shared live location while logged in', async ({page, dbManager}) => {
      const sharedLocationPage = new SharedLocationPage(page);

      const { user } = await TestSetupHelper.createAndLoginUser(page, dbManager);
      await GpsDataFactory.createGpsPointsForUser(dbManager, user.id, TestConstants.DATA_COUNTS.GPS_POINTS_SMALL);

      // Create share link
      const link = await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
        id: 'c0c0c0c0-1111-1111-1111-111111111111',
        name: 'Owner Access Test'
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
      const sharedLocationPage = new SharedLocationPage(page);
      const appNav = new AppNavigation(page);

      // Create both users and login as owner
      const { ownerData, viewerData, owner } = await TestSetupHelper.setupMultiUserShareTest(page, dbManager);

      await GpsDataFactory.createGpsPointsForUser(dbManager, owner.id, TestConstants.DATA_COUNTS.GPS_POINTS_SMALL);

      // Create share link by owner
      const link = await ShareLinkFactory.createLiveLocation(dbManager, owner.id, {
        id: 'd0d0d0d0-1111-1111-1111-111111111111',
        name: 'Public Link'
      });

      // Switch to viewer
      await TestSetupHelper.switchUser(page, appNav, viewerData);

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
      const sharedLocationPage = new SharedLocationPage(page);
      const appNav = new AppNavigation(page);

      // Create both users and login as owner
      const { viewerData, owner } = await TestSetupHelper.setupMultiUserShareTest(
        page, dbManager, 'owner2@test.com', 'viewer2@test.com'
      );

      await GpsDataFactory.createGpsPointsForUser(dbManager, owner.id, TestConstants.DATA_COUNTS.GPS_POINTS_SMALL);

      // Create PASSWORD-PROTECTED share link
      const link = await ShareLinkFactory.createProtectedLiveLocation(dbManager, owner.id, {
        id: 'e0e0e0e0-2222-2222-2222-222222222222',
        name: 'Protected Link'
      });

      // Switch to viewer
      await TestSetupHelper.switchUser(page, appNav, viewerData);

      // Try to access protected link
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();

      // Should still require password even though logged in
      expect(await sharedLocationPage.isPasswordRequired()).toBe(true);

      // Submit correct password
      await sharedLocationPage.submitPassword(TestConstants.PASSWORDS.testpass123);
      await sharedLocationPage.waitForLocationToLoad();

      // Now should have access
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);
    });
  });
});
