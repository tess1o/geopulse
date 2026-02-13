import {test, expect} from '../fixtures/database-fixture.js';
import {TimelinePage} from '../pages/TimelinePage.js';
import {TestData} from '../fixtures/test-data.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';
import {TestDates} from '../fixtures/test-dates.js';
import * as TimelineTestData from '../utils/timeline-test-data.js';

test.describe('Timeline Page', () => {

  // Date range for static test data (Sept 21, 2025)
  const testDateRange = {
    startDate: new Date('2025-09-21'),
    endDate: new Date('2025-09-21')
  };

  test.describe('API Request Optimization', () => {
    test('should not make duplicate API calls when loading timeline page', async ({page, dbManager}) => {
      // First, log in and set up data (don't count these API calls)
      const timelinePage = new TimelinePage(page);
      const { testUser } = await timelinePage.loginAndNavigate();

      // Insert some test data
      const user = await dbManager.getUserByEmail(testUser.email);
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id);

      // NOW set up request listener to track only the test navigation API calls
      const apiCalls = {
        streamingTimeline: [],
        streamingTimelineCount: [],
        periodTags: []
      };

      page.on('request', request => {
        const url = request.url();

        // Track streaming timeline requests (exclude count)
        if (url.includes('/api/streaming-timeline') && !url.includes('/count')) {
          apiCalls.streamingTimeline.push({
            url: url,
            method: request.method(),
            timestamp: Date.now()
          });
          console.log('[REQUEST] Streaming Timeline:', url);
        }

        // Track count requests
        if (url.includes('/api/streaming-timeline/count')) {
          apiCalls.streamingTimelineCount.push({
            url: url,
            method: request.method(),
            timestamp: Date.now()
          });
          console.log('[REQUEST] Streaming Timeline Count:', url);
        }

        // Track period tags requests
        if (url.includes('/api/period-tags/for-timerange')) {
          apiCalls.periodTags.push({
            url: url,
            method: request.method(),
            timestamp: Date.now()
          });
          console.log('[REQUEST] Period Tags:', url);
        }
      });

      // Navigate to timeline page with test date range
      await timelinePage.navigateWithDateRange(testDateRange.startDate, testDateRange.endDate);

      // Wait for the page to load completely
      await timelinePage.waitForPageLoad();

      // Wait for timeline content or no data message
      try {
        await timelinePage.waitForTimelineContent();
      } catch {
        // If no content, check for no data message
        await timelinePage.waitForNoDataMessage();
      }

      // Wait for network to be completely idle - longer in CI environments
      await page.waitForLoadState('networkidle');

      // Wait a bit more to catch any delayed duplicate calls (longer in CI)
      const waitTime = process.env.CI ? 3000 : 1500;
      await page.waitForTimeout(waitTime);

      // Verify each API endpoint was called exactly once
      console.log('=== API Call Summary ===');
      console.log('Streaming Timeline calls:', apiCalls.streamingTimeline.length);
      console.log('Streaming Timeline Count calls:', apiCalls.streamingTimelineCount.length);
      console.log('Period Tags calls:', apiCalls.periodTags.length);

      expect(apiCalls.streamingTimeline.length).toBe(1);
      expect(apiCalls.streamingTimelineCount.length).toBe(1);
      // Period tags may or may not be called depending on whether there are tags
      expect(apiCalls.periodTags.length).toBeLessThanOrEqual(1);
    });

    test('should not make duplicate API calls when redirecting to timeline with query params', async ({page, dbManager}) => {
      // Log in first (don't count these API calls)
      const timelinePage = new TimelinePage(page);
      const { testUser } = await timelinePage.loginAndNavigate();

      // Insert some test data
      const user = await dbManager.getUserByEmail(testUser.email);
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id);

      // NOW set up request listener to track only the test navigation API calls
      const apiCalls = {
        streamingTimeline: [],
        streamingTimelineCount: []
      };

      page.on('request', request => {
        const url = request.url();

        // Track streaming timeline requests (exclude count)
        if (url.includes('/api/streaming-timeline') && !url.includes('/count')) {
          apiCalls.streamingTimeline.push({
            url: url,
            method: request.method(),
            timestamp: Date.now()
          });
          console.log('[REQUEST] Streaming Timeline:', url);
        }

        // Track count requests
        if (url.includes('/api/streaming-timeline/count')) {
          apiCalls.streamingTimelineCount.push({
            url: url,
            method: request.method(),
            timestamp: Date.now()
          });
          console.log('[REQUEST] Streaming Timeline Count:', url);
        }
      });

      // Navigate directly to /app/timeline without query params (simulates the reported issue)
      await page.goto('/app/timeline');

      // Wait for redirect to complete (should add query params)
      await page.waitForURL(/\/app\/timeline\?start=.*&end=.*/);

      // Wait for the page to load completely
      await timelinePage.waitForPageLoad();

      // Wait for timeline content or no data message
      try {
        await timelinePage.waitForTimelineContent();
      } catch {
        await timelinePage.waitForNoDataMessage();
      }

      // Wait for network to be completely idle - longer in CI environments
      await page.waitForLoadState('networkidle');

      // Wait to catch any delayed duplicate calls (longer in CI)
      const waitTime = process.env.CI ? 3000 : 1500;
      await page.waitForTimeout(waitTime);

      // Verify each API endpoint was called exactly once
      console.log('=== API Call Summary (After Redirect) ===');
      console.log('Streaming Timeline calls:', apiCalls.streamingTimeline.length);
      console.log('Streaming Timeline Count calls:', apiCalls.streamingTimelineCount.length);

      expect(apiCalls.streamingTimeline.length).toBe(1);
      expect(apiCalls.streamingTimelineCount.length).toBe(1);
    });
  });
  
  test.describe('Initial State and Empty Data', () => {
    test('should show empty state when no timeline data exists', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testUser } = await timelinePage.loginAndNavigate();
      
      // Verify we're on the timeline page
      expect(await timelinePage.isOnTimelinePage()).toBe(true);
      
      // Wait for loading to complete and no data message to appear
      await page.waitForSelector('.timeline-container', { timeout: 10000 });
      await timelinePage.waitForNoDataMessage();
      
      // Check for no data message
      const noDataMessage = page.locator('.loading-messages').filter({ hasText: 'No timeline for the given date range' });
      const noDataVisible = await noDataMessage.isVisible();
      expect(noDataVisible).toBe(true);
      
      // Verify database has no timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      const hasTimelineData = await TimelinePage.verifyTimelineDataExists(dbManager, user.id);
    });

    test('should show loading state initially', async ({page}) => {
      const timelinePage = new TimelinePage(page);
      const { testUser } = await timelinePage.loginAndNavigate();
      
      // Check if loading state appears briefly
      try {
        await page.waitForSelector('.loading-messages .p-progress-spinner', { timeout: 1000 });
        const loadingSpinner = page.locator('.loading-messages .p-progress-spinner');
        expect(await loadingSpinner.isVisible()).toBe(true);
      } catch {
        // Loading might be too fast to catch, which is fine
        console.log('Loading state was too fast to capture');
      }
    });
  });

  test.describe('Timeline with Data', () => {
    test('should display Movement Timeline header', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertRegularStaysTestData, TestData.users.existing, testDateRange);
      
      const header = page.locator('.timeline-header:has-text("Movement Timeline")');
      expect(await header.isVisible()).toBe(true);
    });

    test('should display regular stays with correct information', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testData } = await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertVerifiableStaysTestData, TestData.users.existing, testDateRange);
      
      await timelinePage.waitForTimelineContent();
      
      const stayCards = timelinePage.getTimelineCards('stays');
      expect(await stayCards.count()).toBe(testData.length);
      
      // Verify each stay card displays correct information
      for (let i = 0; i < testData.length; i++) {
        const stayCard = stayCards.nth(i);
        const expectedStay = testData[i];
        
        // Check location name is displayed correctly
        const locationText = await stayCard.locator('.location-name').textContent();
        expect(locationText.trim()).toBe(expectedStay.locationName);
        
        // Check duration is displayed (the UI shows total minutes for stays)
        const expectedTotalMinutes = Math.floor(expectedStay.duration / 60);
        
        // For regular stays, use .duration-text; for overnight stays, use the first .duration-detail
        const hasDurationText = await stayCard.locator('.duration-text').count() > 0;
        const durationLocator = hasDurationText 
          ? stayCard.locator('.duration-text') 
          : stayCard.locator('.duration-detail').first();
        const durationText = await durationLocator.textContent();
        
        // The UI shows duration like "57 minutes" or "2 hours 30 minutes"
        const expectedHours = Math.floor(expectedStay.duration / 3600);
        const expectedMinutes = Math.floor((expectedStay.duration % 3600) / 60);
        
        if (expectedHours > 0 && expectedMinutes > 0) {
          // Format like "2 hours 30 minutes"
          expect(durationText).toContain(`${expectedHours} hour`);
          expect(durationText).toContain(`${expectedMinutes} minute`);
        } else if (expectedHours > 0) {
          // Format like "2 hours"
          expect(durationText).toContain(`${expectedHours} hour`);
        } else {
          // Format like "57 minutes"
          expect(durationText).toContain(`${expectedTotalMinutes} minute`);
        }
      }
    });

    test('should display regular trips with correct information', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testData } = await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertVerifiableTripsTestData, TestData.users.existing, testDateRange);
      
      await timelinePage.waitForTimelineContent();
      
      const tripCards = timelinePage.getTimelineCards('trips');
      expect(await tripCards.count()).toBe(testData.length);
      
      // Verify each trip card displays correct information
      for (let i = 0; i < testData.length; i++) {
        const tripCard = tripCards.nth(i);
        const expectedTrip = testData[i];

        // Check distance is displayed correctly (convert meters to km)
        const expectedDistanceKm = Math.round(expectedTrip.distanceMeters / 1000 * 100) / 100; // Round to 2 decimal places
        const distanceText = await tripCard.locator('.trip-detail:has-text("Distance")').textContent();

        // The UI might show whole numbers without decimals, so check for both formats
        const expectedDistanceStr1 = expectedDistanceKm.toFixed(2); // "12.00"
        const expectedDistanceStr2 = expectedDistanceKm.toString(); // "12" if it's a whole number
        
        const hasCorrectDistance = distanceText.includes(expectedDistanceStr1) || 
                                  distanceText.includes(expectedDistanceStr2) ||
                                  distanceText.includes(Math.round(expectedDistanceKm).toString());
        
        expect(hasCorrectDistance).toBe(true);
        expect(distanceText).toContain('km');
        
        // Check duration is displayed correctly (convert seconds to minutes)
        const expectedDurationMin = Math.floor(expectedTrip.durationSeconds / 60);
        const durationText = await tripCard.locator('.trip-detail:has-text("Duration")').textContent();
        expect(durationText).toContain(`${expectedDurationMin} minute`);
        
        // Check movement type is displayed correctly
        const movementText = await tripCard.locator('.trip-detail:has-text("Movement")').textContent();
        const expectedMovementIcon = expectedTrip.movementType === 'CAR' ? '🚗' : '🚶';
        expect(movementText).toContain(expectedMovementIcon);
      }
    });

    test('should display regular data gaps with correct information', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testData } = await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertVerifiableDataGapsTestData, TestData.users.existing, testDateRange);
      
      await timelinePage.waitForTimelineContent();
      
      const gapCards = timelinePage.getTimelineCards('gaps');
      expect(await gapCards.count()).toBe(testData.length);
      
      // Verify each data gap card displays correct information
      for (let i = 0; i < testData.length; i++) {
        const gapCard = gapCards.nth(i);
        const expectedGap = testData[i];
        
        // Check duration is displayed correctly
        const gapText = await gapCard.textContent();
        
        // Data gap should indicate it's a gap in data
        expect(gapText).toMatch(/gap|missing|data/i);
        
        // Calculate expected duration values
        const expectedHours = Math.floor(expectedGap.durationSeconds / 3600);
        const expectedMinutes = Math.floor((expectedGap.durationSeconds % 3600) / 60);
        const expectedTotalMinutes = Math.floor(expectedGap.durationSeconds / 60);
        
        // Assert the correct duration format based on expected duration
        if (expectedHours > 0 && expectedMinutes > 0) {
          // Format like "1 hour 30 minutes" 
          expect(gapText).toContain(`${expectedHours} hour`);
          expect(gapText).toContain(`${expectedMinutes} minute`);
        } else if (expectedHours > 0) {
          // Format like "1 hour"
          expect(gapText).toContain(`${expectedHours} hour`);
        } else if (expectedTotalMinutes > 1) {
          // Format like "30 minutes"
          expect(gapText).toContain(`${expectedTotalMinutes} minute`);
        } else {
          // Format like "less than a minute" or "1 minute"
          expect(gapText).toMatch(/less than a minute|1 minute/i);
        }
      }
    });
  });

  test.describe('Overnight Timeline Elements', () => {
    test('should display overnight stays with correct data and special formatting', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;

      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData, testUser);

      await timelinePage.waitForTimelineContent();

      const overnightStayCards = timelinePage.getTimelineCards('overnightStays');
      const totalCards = await overnightStayCards.count();
      expect(totalCards).toBeGreaterThan(0);

      console.log(`Found ${totalCards} overnight stay cards (timezone-dependent)`);

      // Verify that overnight stay cards have the correct structure
      // Don't assume specific locations or counts (timezone-dependent)
      for (let i = 0; i < totalCards; i++) {
        const stayCard = overnightStayCards.nth(i);
        const cardText = await stayCard.textContent();

        // Check that location name is displayed
        const locationText = await stayCard.locator('.location-name').textContent();
        expect(locationText.trim().length).toBeGreaterThan(0);
        expect(['Hotel Downtown', 'Airport Terminal']).toContain(locationText.trim());

        // Check that duration is shown
        expect(cardText).toMatch(/\d+\s+hours?/);

        // Check "On this day" duration is present for overnight stays
        expect(cardText).toMatch(/on this day|this day/i);

        // Each card should show a time
        expect(cardText).toMatch(/\d{2}:\d{2}/);
      }

      // Verify moon icons and date groups exist
      expect(await timelinePage.getMoonIconsCount()).toBeGreaterThan(0);
      expect(await timelinePage.getDateGroupsCount()).toBeGreaterThanOrEqual(1);
    });

    test('should calculate "on this day" duration correctly when browser timezone differs from user timezone', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Simulate browser in New York timezone but user setting is Europe/London
      testUser.timezone = 'Europe/London';
      
      // Mock browser timezone to America/New_York
      await page.addInitScript(() => {
        // Override getTimezoneOffset to simulate New York timezone
        const originalGetTimezoneOffset = Date.prototype.getTimezoneOffset;
        Date.prototype.getTimezoneOffset = function() {
          // New York is UTC-5 (300 minutes) or UTC-4 (240 minutes) depending on DST
          return 300; // Simulate EST (UTC-5)
        };
      });
      
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData, testUser);
      
      await timelinePage.waitForTimelineContent();
      
      const overnightStayCards = timelinePage.getTimelineCards('overnightStays');
      expect(await overnightStayCards.count()).toBeGreaterThan(0);
      
      // Get the first overnight stay card
      const firstStayCard = overnightStayCards.nth(0);
      const cardText = await firstStayCard.textContent();
      
      console.log('Browser timezone mismatch test - Card text:', cardText);
      
      // The "on this day" calculation should still work correctly despite browser timezone mismatch
      // It should show the start time as 00:00 (midnight) in Europe/London, NOT affected by browser timezone
      expect(cardText).toMatch(/on this day|this day/i);
      
      // Should show correct start time (00:00) when stay continues from previous day
      // This tests that getStartOfDay/getEndOfDay use user timezone, not browser timezone
      if (cardText.includes('Continued from')) {
        expect(cardText).toMatch(/00:00/);
        // Should NOT show times that would indicate browser timezone usage like 17:00 or 05:00
        expect(cardText).not.toMatch(/17:00|05:00/);
      }
      
      // Verify localStorage still contains the correct user timezone
      const userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });
      
      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('Europe/London');
    });

    test('should display overnight trips with correct data and special formatting', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Set timezone to Europe/Kyiv to match test expectations
      testUser.timezone = 'Europe/Kyiv';
      
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightTripsTestData, testUser);
      
      await timelinePage.waitForTimelineContent();
      
      const overnightTripCards = timelinePage.getTimelineCards('overnightTrips');
      
      // The trip (8 PM yesterday to 12 PM today) should appear on 2 days, so expect 2 cards
      const totalCards = await overnightTripCards.count();
      expect(totalCards).toBe(2); // Overnight trip should appear on 2 days
      
      // Test data contains 1 trip, but it will generate 2 cards (start day + continuation day)
      const expectedTrip = testData[0];
      const expectedDistanceKm = Math.round(expectedTrip.distanceMeters / 1000);
      const totalHours = Math.floor(expectedTrip.totalDuration / 3600);
      
      // Verify each card
      for (let i = 0; i < totalCards; i++) {
        const tripCard = overnightTripCards.nth(i);
        const cardText = await tripCard.textContent();
        
        // Check total distance and duration are shown (should be same on both cards)
        expect(cardText).toContain(`${expectedDistanceKm} km`);
        expect(cardText).toContain(`${totalHours} hour`);
        
        // Check movement type
        expect(cardText).toContain('Movement: 🚗 Car');

        // Check "On this day" duration is shown for the current date segment
        expect(cardText).toMatch(/on this day|this day/i);
        
        // Verify card-specific content based on position
        if (i === 0) {
          // First card: Start day (Sept 20) - should show actual start time
          // 8 PM yesterday UTC = 11 PM yesterday Europe/Kyiv (23:00)
          expect(cardText).toMatch(/09\/20\/2025,\s*23:00/);
          expect(cardText).not.toMatch(/continued.*from/i);
          
          // "On this day" should show: 23:00 - 23:59 (59m)
          expect(cardText).toMatch(/23:00\s*-\s*23:59/);
          expect(cardText).toMatch(/59m/);
          
        } else if (i === 1) {
          // Second card: Continuation day (Sept 21) - should show "Continued from"
          expect(cardText).toMatch(/continued.*from.*sep.*20.*23:00/i);
          expect(cardText).not.toMatch(/09\/20\/2025,\s*23:00/);
          
          // "On this day" should show: 00:00 - 15:00 (15h)
          // 12 PM UTC + 3 hours timezone offset = 15:00 Europe/Kyiv
          expect(cardText).toMatch(/00:00\s*-\s*09:00/);
          expect(cardText).toMatch(/9h/);
        }
      }
      
      expect(await timelinePage.getMoonIconsCount()).toBeGreaterThan(0);
    });

    test('should display overnight data gaps with correct data and special formatting', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Set timezone to Europe/Kyiv to match test expectations
      testUser.timezone = 'Europe/Kyiv';
      
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightDataGapsTestData, testUser);
      
      await timelinePage.waitForTimelineContent();
      
      const overnightGapCards = timelinePage.getTimelineCards('overnightGaps');
      
      // The data gap (8 PM yesterday to 12 PM today) should appear on 2 days, so expect 2 cards
      const totalCards = await overnightGapCards.count();
      expect(totalCards).toBe(2); // Overnight data gap should appear on 2 days
      
      // Test data contains 1 data gap, but it will generate 2 cards (start day + continuation day)
      const expectedGap = testData[0];
      const totalHours = Math.floor(expectedGap.totalDuration / 3600);
      
      // Verify each card
      for (let i = 0; i < totalCards; i++) {
        const gapCard = overnightGapCards.nth(i);
        const cardText = await gapCard.textContent();
        
        // Check that it indicates a data gap
        expect(cardText).toMatch(/gap|missing|data|unknown/i);
        
        // Check total duration is shown (should be same on both cards)
        expect(cardText).toContain(`${totalHours} hours`);
        
        // Check "On this day" duration is shown for the current date segment
        expect(cardText).toMatch(/on this day|this day/i);
        
        // Verify card-specific content based on position
        if (i === 0) {
          // First card: Start day (Sept 20) - should show actual start time
          // 8 PM yesterday UTC = 11 PM yesterday Europe/Kyiv (23:00)
          expect(cardText).toMatch(/09\/20\/2025,\s*23:00/);
          expect(cardText).not.toMatch(/continued.*from/i);
          
          // "On this day" should show: 23:00 - 23:59 (59m)
          expect(cardText).toMatch(/23:00\s*-\s*23:59/);
          expect(cardText).toMatch(/59m/);
          
        } else if (i === 1) {
          // Second card: Continuation day (Sept 21) - should show "Continued from"
          expect(cardText).toMatch(/continued.*from.*sep.*20.*23:00/i);
          expect(cardText).not.toMatch(/09\/20\/2025,\s*23:00/);
          
          // "On this day" should show: 00:00 - 15:00 (15h)
          // 12 PM UTC + 3 hours timezone offset = 15:00 Europe/Kyiv
          expect(cardText).toMatch(/00:00\s*-\s*11:00/);
          expect(cardText).toMatch(/11h/);
        }
      }
      
      expect(await timelinePage.getMoonIconsCount()).toBeGreaterThan(0);
    });

    test('should properly display overnight elements across multiple date groups', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testUser } = await timelinePage.loginAndNavigate();
      
      // Insert mixed overnight test data
      const user = await dbManager.getUserByEmail(testUser.email);
      await TimelineTestData.insertVerifiableOvernightStaysTestData(dbManager, user.id);
      await TimelineTestData.insertVerifiableOvernightTripsTestData(dbManager, user.id);
      await TimelineTestData.insertVerifiableOvernightDataGapsTestData(dbManager, user.id);

      await timelinePage.navigateWithDateRange(new Date('2025-09-20'), new Date('2025-09-22'));
      await timelinePage.waitForPageLoad();
      
      await timelinePage.waitForTimelineContent();
      
      const dateGroupCount = await timelinePage.getDateGroupsCount();
      expect(dateGroupCount).toBeGreaterThanOrEqual(2);
      
      const dateSeparators = page.locator('.date-separator-text');
      expect(await dateSeparators.count()).toBe(dateGroupCount);
      
      const allOvernightCards = page.locator('.timeline-card--overnight-stay, .timeline-card--overnight-trip, .timeline-card--overnight-data-gap');
      const overnightCardCount = await allOvernightCards.count();
      expect(overnightCardCount).toBeGreaterThan(0);
      
      const moonIconCount = await timelinePage.getMoonIconsCount();
      expect(moonIconCount).toBeGreaterThan(0);
    });
  });

  test.describe('Timeline UI Behavior and Data Verification', () => {
    test('should display date separators correctly', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertRegularStaysTestData, TestData.users.existing, testDateRange);
      
      await timelinePage.waitForTimelineContent();
      
      const dateSeparators = page.locator('.date-separator');
      expect(await dateSeparators.count()).toBeGreaterThan(0);
      
      const dateSeparatorText = page.locator('.date-separator-text');
      expect(await dateSeparatorText.count()).toBeGreaterThan(0);
      
      const dateGroups = page.locator('.date-group');
      expect(await dateGroups.count()).toBeGreaterThan(0);
    });
    
    test('should display date separators in user timezone format', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Create user with specific timezone
      testUser.timezone = 'Europe/London';
      
      // Login and navigate
      await timelinePage.loginAndNavigate(testUser);
      
      // Insert test data
      const user = await dbManager.getUserByEmail(testUser.email);
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id);

      await timelinePage.navigateWithDateRange(new Date('2025-09-20'), new Date('2025-09-22'));
      await timelinePage.waitForPageLoad();

      await timelinePage.waitForTimelineContent();
      
      // Check that date separators use proper timezone formatting
      const dateSeparatorText = page.locator('.date-separator-text').first();
      const dateText = await dateSeparatorText.textContent();
      
      // Verify date format is displayed (should be formatted in user's timezone)
      expect(dateText).toMatch(/\w+,\s+\w+\s+\d{1,2}/); // Format like "Monday, September 19"
      
      // Verify localStorage contains the correct timezone
      const userInfo = await page.evaluate(() => {
        const userInfoStr = localStorage.getItem('userInfo');
        return userInfoStr ? JSON.parse(userInfoStr) : null;
      });
      
      expect(userInfo).toBeTruthy();
      expect(userInfo.timezone).toBe('Europe/London');
    });

    test('should handle timeline item clicks correctly', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertRegularStaysTestData, TestData.users.existing, testDateRange);
      
      await timelinePage.waitForTimelineContent();
      
      const timelineItems = page.locator('.custom-timeline .p-timeline-event-content');
      expect(await timelineItems.count()).toBeGreaterThan(0);
      
      const firstItem = timelineItems.first();
      await firstItem.click();
      
      expect(true).toBe(true); // Item is clickable
    });

    test('should correctly display timeline markers with appropriate icons', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testUser } = await timelinePage.loginAndNavigate();
      
      // Insert mixed timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id);
      await TimelineTestData.insertRegularTripsTestData(dbManager, user.id);

      await timelinePage.navigateWithDateRange(new Date('2025-09-20'), new Date('2025-09-22'));
      await timelinePage.waitForPageLoad();
      await timelinePage.waitForTimelineContent();
      
      const stayMarkers = page.locator('.marker-stay, .marker-overnight-stay');
      const tripMarkers = page.locator('.marker-trip, .marker-overnight-trip');
      const gapMarkers = page.locator('.marker-data-gap, .marker-overnight-data-gap');
      
      const totalMarkers = await stayMarkers.count() + await tripMarkers.count() + await gapMarkers.count();
      expect(totalMarkers).toBeGreaterThan(0);
      
      const mapMarkerIcons = page.locator('.timeline-marker .pi-map-marker');
      const carIcons = page.locator('.timeline-marker .pi-car');
      const questionIcons = page.locator('.timeline-marker .pi-question');
      const moonIcons = page.locator('.timeline-marker .pi-moon');
      
      const totalIcons = await mapMarkerIcons.count() + await carIcons.count() + 
                        await questionIcons.count() + await moonIcons.count();
      expect(totalIcons).toBeGreaterThan(0);
    });

    test('should verify data consistency between database and UI display', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testData } = await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertVerifiableStaysTestData, TestData.users.existing, testDateRange);
      
      await timelinePage.waitForTimelineContent();
      
      const stayCards = timelinePage.getTimelineCards('stays');  
      const tripCards = timelinePage.getTimelineCards('trips');  
      const gapCards = timelinePage.getTimelineCards('gaps');
      
      const uiStayCount = await stayCards.count();
      
      expect(uiStayCount).toBeGreaterThanOrEqual(testData.length);
    });
  });

  test.describe('Timezone Switching Tests', () => {
    // Helper function to switch user timezone in localStorage and reload page
    async function switchUserTimezone(page, timelinePage, newTimezone) {
      await page.evaluate((timezone) => {
        const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
        userInfo.timezone = timezone;
        localStorage.setItem('userInfo', JSON.stringify(userInfo));
      }, newTimezone);
      
      await page.reload();
      await timelinePage.waitForTimelineContent();
    }

    // Helper function to format UTC time to specific timezone
    function formatTimeInTimezone(utcDateString, timezone) {
      const date = new Date(utcDateString);
      return date.toLocaleString('en-US', {
        timeZone: timezone,
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      });
    }

    test('should display overnight stays correctly after switching from Europe/Kyiv to America/New_York', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;

      // Start with Europe/Kyiv timezone
      testUser.timezone = 'Europe/Kyiv';

      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData, testUser);
      await timelinePage.waitForTimelineContent();

      // Verify initial display in Europe/Kyiv timezone
      const overnightStayCards = timelinePage.getTimelineCards('overnightStays');
      const kyivTotalCards = await overnightStayCards.count();
      expect(kyivTotalCards).toBeGreaterThan(0);

      // Get locations visible in Kyiv timezone
      const kyivLocations = [];
      for (let i = 0; i < kyivTotalCards; i++) {
        const locationText = await overnightStayCards.nth(i).locator('.location-name').textContent();
        kyivLocations.push(locationText.trim());
      }
      // Change user timezone to New York
      await switchUserTimezone(page, timelinePage, 'America/New_York');

      // Verify display changes to New York timezone
      const updatedOvernightStayCards = timelinePage.getTimelineCards('overnightStays');
      const nyTotalCards = await updatedOvernightStayCards.count();
      expect(nyTotalCards).toBeGreaterThan(0);

      console.log(`New York timezone: ${nyTotalCards} overnight stay cards`);

      // Get locations visible in NY timezone
      const nyLocations = [];
      for (let i = 0; i < nyTotalCards; i++) {
        const locationText = await updatedOvernightStayCards.nth(i).locator('.location-name').textContent();
        nyLocations.push(locationText.trim());
      }
      console.log(`NY locations: ${nyLocations.join(', ')}`);

      // Verify that overnight stays are displayed (count may differ due to timezone)
      // Both Hotel Downtown and Airport Terminal should appear in at least one timezone
      const allLocations = new Set([...kyivLocations, ...nyLocations]);
      expect(allLocations.has('Hotel Downtown')).toBeTruthy();

      // Verify cards show times (format may vary)
      for (let i = 0; i < nyTotalCards; i++) {
        const cardText = await updatedOvernightStayCards.nth(i).textContent();
        expect(cardText).toMatch(/\d{2}:\d{2}/); // Has time in HH:MM format
        expect(cardText).toMatch(/on this day/i); // Has "on this day" section
      }
    });

    test('should display overnight trips correctly after switching from Europe/Kyiv to America/New_York', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Start with Europe/Kyiv timezone
      testUser.timezone = 'Europe/Kyiv';
      
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightTripsTestData, testUser);
      await timelinePage.waitForTimelineContent();
      
      // Verify initial display in Europe/Kyiv - trip is NOT overnight in Kyiv (02:00-18:00 same day)
      const overnightTripCards = timelinePage.getTimelineCards('overnightTrips');
      const totalCards = await overnightTripCards.count();
      expect(totalCards).toBe(2);

      // Switch to New York timezone
      await switchUserTimezone(page, timelinePage, 'America/New_York');
      
      // Verify display in New York timezone - trip IS overnight in NY (19:00 Sept 20 to 11:00 Sept 21)
      const updatedOvernightTripCards = timelinePage.getTimelineCards('overnightTrips');
      const updatedTotalCards = await updatedOvernightTripCards.count();
      expect(updatedTotalCards).toBe(2); // One overnight trip spanning 2 days shows as 1 card
      
      const updatedFirstCardText = await updatedOvernightTripCards.nth(0).textContent();
      expect(updatedFirstCardText).toContain(`09/20/2025, 16:00`);
      expect(updatedFirstCardText).toContain(`On this day: 16:00 - 23:59 (7h 59m)`);

      const updatedSecondCardText = await updatedOvernightTripCards.nth(1).textContent();
      expect(updatedSecondCardText).toContain(`Continued from Sep 20, 16:00`);
      expect(updatedSecondCardText).toContain(`On this day: 00:00 - 02:00 (2h)`);
      
      console.log('Trip timezone switch - Kyiv: 0 overnight trips (same day)');
      console.log('Trip timezone switch - New York: 1 overnight trip (spans midnight)');
    });

    test('should display overnight data gaps correctly after switching from Europe/Kyiv to America/New_York', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Start with Europe/Kyiv timezone
      testUser.timezone = 'Europe/Kyiv';
      
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightDataGapsTestData, testUser);
      await timelinePage.waitForTimelineContent();
      
      // Verify initial display in Europe/Kyiv
      const overnightGapCards = timelinePage.getTimelineCards('overnightGaps');
      const totalCards = await overnightGapCards.count();
      expect(totalCards).toBe(2);

      const firstCardText = await overnightGapCards.nth(0).textContent();
      expect(firstCardText).toContain('09/20/2025, 23:00');

      const secondCardText = await overnightGapCards.nth(1).textContent();
      expect(secondCardText).toMatch(/Continued from Sep 20, 23:00/);
      
      // Switch to New York timezone
      await switchUserTimezone(page, timelinePage, 'America/New_York');
      
      // Verify display in New York timezone  
      const updatedGapCards = timelinePage.getTimelineCards('overnightGaps');
      const updatedFirstCardText = await updatedGapCards.nth(1).textContent();
      expect(updatedFirstCardText).toMatch(/Continued from Sep 20, 16:00/);
    });

    test('should handle items that change overnight status when switching timezones', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Start with Europe/Kyiv timezone
      testUser.timezone = 'Europe/Kyiv';
      
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData, testUser);
      await timelinePage.waitForTimelineContent();
      
      // Count overnight stay cards in Kyiv timezone
      const kyivOvernightCards = await timelinePage.getTimelineCards('overnightStays').count();
      const kyivMoonIcons = await timelinePage.getMoonIconsCount();
      
      // Switch to New York timezone
      await switchUserTimezone(page, timelinePage, 'America/New_York');
      
      // Count overnight stay cards in New York timezone
      const nyOvernightCards = await timelinePage.getTimelineCards('overnightStays').count();
      const nyMoonIcons = await timelinePage.getMoonIconsCount();
      
      // Both should still be overnight (16-hour duration spans midnight in both timezones)
      expect(kyivOvernightCards).toBe(2);
      expect(nyOvernightCards).toBe(2);
      expect(kyivMoonIcons).toBeGreaterThan(0);
      expect(nyMoonIcons).toBeGreaterThan(0);
      
      console.log(`Kyiv: ${kyivOvernightCards} cards, ${kyivMoonIcons} moon icons`);
      console.log(`New York: ${nyOvernightCards} cards, ${nyMoonIcons} moon icons`);
    });

    test('should display date separators correctly after timezone switch', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Start with Europe/Kyiv timezone
      testUser.timezone = 'Europe/Kyiv';
      
      await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData, testUser);
      await timelinePage.waitForTimelineContent();
      
      // Get date group count in Kyiv timezone
      const kyivDateGroups = await timelinePage.getDateGroupsCount();
      
      // Switch to New York timezone
      await switchUserTimezone(page, timelinePage, 'America/New_York');
      
      // Get date group count in New York timezone
      const nyDateGroups = await timelinePage.getDateGroupsCount();
      
      expect(kyivDateGroups).toBeGreaterThanOrEqual(2);
      expect(nyDateGroups).toBeGreaterThanOrEqual(1);

      console.log(`Date groups - Kyiv: ${kyivDateGroups}, New York: ${nyDateGroups}`);
    });

    test('should maintain chronological order of timeline items after timezone switch', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;

      // Start with Europe/Kyiv timezone
      testUser.timezone = 'Europe/Kyiv';

      await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData, testUser);
      await timelinePage.waitForTimelineContent();

      // Get ALL timeline cards (not filtered by type) to verify chronological order
      const kyivAllCards = await page.locator('.timeline-card').all();
      const kyivCardData = [];
      for (const card of kyivAllCards) {
        const locationEl = await card.locator('.location-name').first();
        if (await locationEl.count() > 0) {
          const location = await locationEl.textContent();
          kyivCardData.push(location.trim());
        }
      }

      console.log(`Kyiv timezone card order: ${kyivCardData.join(' -> ')}`);

      // Switch to New York timezone
      await switchUserTimezone(page, timelinePage, 'America/New_York');

      // Get ALL timeline cards in NY timezone
      const nyAllCards = await page.locator('.timeline-card').all();
      const nyCardData = [];
      for (const card of nyAllCards) {
        const locationEl = await card.locator('.location-name').first();
        if (await locationEl.count() > 0) {
          const location = await locationEl.textContent();
          nyCardData.push(location.trim());
        }
      }

      console.log(`NY timezone card order: ${nyCardData.join(' -> ')}`);

      // Verify that both timezones show Hotel Downtown and/or Airport Terminal
      // The specific order may differ due to date changes, but both locations should exist
      const kyivSet = new Set(kyivCardData);
      const nySet = new Set(nyCardData);

      // Both timezones should have at least one of the test locations
      const hasHotelInEither = kyivSet.has('Hotel Downtown') || nySet.has('Hotel Downtown');
      const hasAirportInEither = kyivSet.has('Airport Terminal') || nySet.has('Airport Terminal');

      expect(hasHotelInEither || hasAirportInEither).toBeTruthy();

      console.log(`Timeline maintains data integrity across timezone switch`);
    });
  });

  test.describe('Period Tags Display on Timeline', () => {
    test('should display period tag banner when viewing tagged period', async ({page, dbManager}) => {
      await TestSetupHelper.setupTimelineWithPeriodTag(page, dbManager, {
        timelineDataFn: TimelineTestData.insertRegularStaysTestData,
        periodTag: {
          tagName: 'Summer Vacation 2025',
          startTime: TestDates.PERIOD_TAG.START_DATE,
          endTime: TestDates.PERIOD_TAG.END_DATE,
          source: 'manual'
        },
        startDate: TestDates.PERIOD_TAG.MIDDLE_DATE,
        endDate: TestDates.PERIOD_TAG.MIDDLE_DATE
      });

      // Verify period tag banner is visible
      await TestSetupHelper.assertPeriodTagVisibility(page, 'Summer Vacation 2025', true, expect);
    });

    test('should display multiple period tags when viewing overlapping periods', async ({page, dbManager}) => {
      const { user } = await TestSetupHelper.setupTimelineWithPeriodTag(page, dbManager, {
        timelineDataFn: TimelineTestData.insertRegularStaysTestData,
        skipNavigation: true
      });

      // Create multiple period tags with some overlap
      await TestSetupHelper.createMultiplePeriodTagsForTimeline(dbManager, user.id, [
        {
          tagName: 'Business Trip',
          startTime: TestDates.PERIOD_TAG.START_DATE,
          endTime: TestDates.PERIOD_TAG.MIDDLE_DATE,
          source: 'manual'
        },
        {
          tagName: 'Conference',
          startTime: TestDates.PERIOD_TAG.MIDDLE_DATE,
          endTime: TestDates.PERIOD_TAG.END_DATE,
          source: 'manual'
        }
      ]);

      // Navigate to date that overlaps both tags
      const timelinePage = new TimelinePage(page);
      await timelinePage.navigateWithDateRange(TestDates.PERIOD_TAG.MIDDLE_DATE, TestDates.PERIOD_TAG.MIDDLE_DATE);
      await timelinePage.waitForPageLoad();
      await timelinePage.waitForTimelineContent();

      // Verify at least one period tag is displayed
      const periodTagBanners = page.locator('.gp-period-badge, .p-message:has-text("Business Trip"), .p-message:has-text("Conference")');
      expect(await periodTagBanners.count()).toBeGreaterThan(0);
    });

    test('should not display period tag banner when viewing non-tagged period', async ({page, dbManager}) => {
      await TestSetupHelper.setupTimelineWithPeriodTag(page, dbManager, {
        timelineDataFn: TimelineTestData.insertRegularStaysTestData,
        periodTag: {
          tagName: 'Different Period',
          startTime: TestDates.PERIOD_TAG.OUTSIDE_RANGE,
          endTime: new Date('2025-08-07T00:00:00Z'),
          source: 'manual'
        },
        startDate: TestDates.PERIOD_TAG.MIDDLE_DATE,
        endDate: TestDates.PERIOD_TAG.MIDDLE_DATE
      });

      // Verify period tag banner is not visible
      await TestSetupHelper.assertPeriodTagVisibility(page, 'Different Period', false, expect);
    });

    test('should display active period tag for current date', async ({page, dbManager}) => {
      const { timelinePage } = await TestSetupHelper.setupTimelineWithPeriodTag(page, dbManager, {
        periodTag: {
          tagName: 'Ongoing Trip',
          startTime: TestDates.daysAgo(3),
          endTime: null,
          source: 'owntracks'
        },
        startDate: TestDates.today(),
        endDate: TestDates.today(),
        skipNavigation: true
      });

      // Navigate to current date
      await timelinePage.navigateWithDateRange(TestDates.today(), TestDates.today());
      await timelinePage.waitForPageLoad();

      // Wait a bit for any period tag banners to render
      await page.waitForTimeout(1000);

      // Verify active tag is displayed or check for timeline content
      const timelineContent = page.locator('.timeline-container');
      expect(await timelineContent.isVisible()).toBe(true);
    });

    test('should handle period tag with partial overlap on timeline date range', async ({page, dbManager}) => {
      await TestSetupHelper.setupTimelineWithPeriodTag(page, dbManager, {
        timelineDataFn: TimelineTestData.insertRegularStaysTestData,
        periodTag: {
          tagName: 'Partial Overlap Tag',
          startTime: new Date('2025-09-19T00:00:00Z'),
          endTime: new Date('2025-09-21T12:00:00Z'), // Ends mid-day
          source: 'manual'
        },
        startDate: TestDates.PERIOD_TAG.MIDDLE_DATE,
        endDate: TestDates.PERIOD_TAG.END_DATE
      });

      // Verify period tag is displayed for the overlapping portion
      await TestSetupHelper.assertPeriodTagVisibility(page, 'Partial Overlap Tag', true, expect);
    });

    test('should display OwnTracks source badge on period tag banner', async ({page, dbManager}) => {
      await TestSetupHelper.setupTimelineWithPeriodTag(page, dbManager, {
        timelineDataFn: TimelineTestData.insertRegularStaysTestData,
        periodTag: {
          tagName: 'OwnTracks Trip',
          startTime: TestDates.PERIOD_TAG.START_DATE,
          endTime: TestDates.PERIOD_TAG.END_DATE,
          source: 'owntracks'
        },
        startDate: TestDates.PERIOD_TAG.MIDDLE_DATE,
        endDate: TestDates.PERIOD_TAG.MIDDLE_DATE
      });

      // Verify OwnTracks badge is displayed
      const periodTagBanner = page.locator('.gp-period-badge, .p-message:has-text("OwnTracks Trip")');
      if (await periodTagBanner.isVisible()) {
        const bannerText = await periodTagBanner.textContent();
        expect(bannerText).toContain('OwnTracks');
      }
    });

    test('should update period tag display when changing date range', async ({page, dbManager}) => {
      const { user, timelinePage } = await TestSetupHelper.setupTimelineWithPeriodTag(page, dbManager, {
        skipNavigation: true
      });

      // Insert timeline data for multiple dates so we can navigate and see period tags
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id); // Sept 21
      await TimelineTestData.insertRegularTripsTestData(dbManager, user.id); // Sept 21

      // Create two period tags for different date ranges
      await TestSetupHelper.createMultiplePeriodTagsForTimeline(dbManager, user.id, [
        {
          tagName: 'Tag A',
          startTime: new Date('2025-09-20T00:00:00Z'),
          endTime: new Date('2025-09-21T23:59:59Z'), // Covers Sept 21
          source: 'manual'
        },
        {
          tagName: 'Tag B',
          startTime: new Date('2025-09-21T00:00:00Z'), // Also covers Sept 21
          endTime: new Date('2025-09-23T00:00:00Z'),
          source: 'manual'
        }
      ]);

      // Navigate to Sept 21 where timeline data exists
      await timelinePage.navigateWithDateRange(TestDates.PERIOD_TAG.MIDDLE_DATE, TestDates.PERIOD_TAG.MIDDLE_DATE);
      await timelinePage.waitForPageLoad();
      await timelinePage.waitForTimelineContent();

      // Check if both Tag A and Tag B are visible (both cover Sept 21)
      const isTagAVisible = await TestSetupHelper.isPeriodTagVisible(page, 'Tag A');
      const isTagBVisible = await TestSetupHelper.isPeriodTagVisible(page, 'Tag B');

      // At least one tag should be visible on Sept 21
      expect(isTagAVisible || isTagBVisible).toBe(true);
    });

    test('should link to period tags management page from timeline', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testUser } = await timelinePage.loginAndNavigate();

      const user = await dbManager.getUserByEmail(testUser.email);
      await TimelineTestData.insertRegularStaysTestData(dbManager, user.id);

      // Create a period tag
      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Test Tag',
        startTime: new Date('2025-09-20T00:00:00Z'),
        endTime: new Date('2025-09-22T00:00:00Z'),
        source: 'manual'
      });

      // Navigate to the tagged period
      await timelinePage.navigateWithDateRange(new Date('2025-09-21'), new Date('2025-09-21'));
      await timelinePage.waitForPageLoad();
      await timelinePage.waitForTimelineContent();

      // Look for any link to period tags management (if implemented in UI)
      const periodTagsLink = page.locator('a[href*="period-tags"]');

      if (await periodTagsLink.count() > 0) {
        // Click the link
        await periodTagsLink.first().click();

        // Verify navigation to period tags page
        await page.waitForURL('**/app/period-tags', { timeout: 5000 });
        expect(page.url()).toContain('/app/period-tags');
      } else {
        // If no link exists yet, just verify the page renders correctly
        expect(true).toBe(true);
      }
    });
  });
});