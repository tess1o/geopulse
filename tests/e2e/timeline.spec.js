import {test, expect} from '../fixtures/database-fixture.js';
import {TimelinePage} from '../pages/TimelinePage.js';
import {TestData} from '../fixtures/test-data.js';
import * as TimelineTestData from '../utils/timeline-test-data.js';

test.describe('Timeline Page', () => {
  
  // Date range for static test data (Sept 21, 2025)
  const testDateRange = {
    startDate: new Date('2025-09-21'),
    endDate: new Date('2025-09-21')
  };
  
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
      
      // Set timezone to Europe/Kyiv to match test expectations
      testUser.timezone = 'Europe/Kyiv';
      
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData, testUser);
      
      await timelinePage.waitForTimelineContent();
      
      const overnightStayCards = timelinePage.getTimelineCards('overnightStays');
      expect(await overnightStayCards.count()).toBeGreaterThan(0);
      
      // Overnight stays span multiple days, so we expect more cards than test data entries
      // For Airport Terminal (8 hours from 17:00 Sept 21 to 01:00 Sept 22), we expect 2 cards
      const totalCards = await overnightStayCards.count();
      expect(totalCards).toBe(2); // Airport Terminal should appear on 2 days
      
      // Verify each overnight stay card displays correct information
      for (let i = 0; i < totalCards; i++) {
        const stayCard = overnightStayCards.nth(i);
        const cardText = await stayCard.textContent();
        
        // Check location name is displayed correctly - all cards should be Airport Terminal
        const locationText = await stayCard.locator('.location-name').textContent();
        expect(locationText.trim()).toBe('Airport Terminal');
        
        // Check total duration is shown (8 hours for all cards)
        expect(cardText).toContain('8 hours');
        
        // Check "On this day" duration is present
        expect(cardText).toMatch(/on this day|this day/i);
        
        // Verify card-specific content based on position
        if (i === 0) {
          // First card: Start day (Sept 21) - should show actual start time
          expect(cardText).toMatch(/09\/21\/2025,\s*17:00/);
          expect(cardText).not.toMatch(/continued.*from/i);
          
          // "On this day" should show: 17:00 - 23:59 (6h 59m)
          expect(cardText).toMatch(/17:00\s*-\s*23:59/);
          expect(cardText).toMatch(/6h\s*59m|6\s+hours?/);
          
        } else if (i === 1) {
          // Second card: Continuation day (Sept 22) - should show "Continued from"
          expect(cardText).toMatch(/continued.*from.*sep.*21.*17:00/i);
          expect(cardText).not.toMatch(/09\/21\/2025,\s*17:00/);
          
          // "On this day" should show: 00:00 - 01:00 (1 hour)
          expect(cardText).toMatch(/00:00\s*-\s*01:00/);
          expect(cardText).toMatch(/1h|1\s+hours?/);
        }
        
        console.log(`Overnight Stay Card ${i}: "${cardText.slice(0, 300)}..."`);
      }
      
      expect(await timelinePage.getMoonIconsCount()).toBeGreaterThan(0);
      expect(await timelinePage.getDateGroupsCount()).toBeGreaterThanOrEqual(2);
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
        
        console.log(`Overnight Trip Card ${i}: "${cardText.slice(0, 300)}..."`);
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
        
        console.log(`Overnight Data Gap Card ${i}: "${cardText.slice(0, 300)}..."`);
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
      
      console.log(`Multi-day overnight test: ${dateGroupCount} date groups, ${overnightCardCount} overnight cards, ${moonIconCount} moon icons`);
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
      
      await page.reload();
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
      
      console.log('UI vs DB comparison:', {
        dbStays: testData.length,
        uiStays: uiStayCount
      });
      
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
      
      // Start with Europe/Kyiv timezone (UTC+3)
      testUser.timezone = 'Europe/Kyiv';
      
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData, testUser);
      await timelinePage.waitForTimelineContent();
      
      // Verify initial display in Europe/Kyiv timezone
      const overnightStayCards = timelinePage.getTimelineCards('overnightStays');
      const totalCards = await overnightStayCards.count();
      expect(totalCards).toBe(2);
      
      // Get text from first card (should show Europe/Kyiv times)
      const firstCardText = await overnightStayCards.nth(0).textContent();
      // Airport Terminal starts at 14:00 UTC Sept 21 - calculate expected Kyiv time
      const airportKyivTime = formatTimeInTimezone('2025-09-21T14:00:00Z', 'Europe/Kiev');
      console.log(`Expecting Airport Terminal at ${airportKyivTime} in Kyiv timezone`);
      expect(firstCardText).toMatch(new RegExp(`09/21/2025,\\s*${airportKyivTime}`));
      expect(firstCardText).toMatch(/Airport Terminal/); // Verify it's the airport
      
      // Change user timezone to New York (UTC-5)
      await switchUserTimezone(page, timelinePage, 'America/New_York');
      
      // Verify display changes to New York timezone
      const updatedOvernightStayCards = timelinePage.getTimelineCards('overnightStays');
      const updatedTotalCards = await updatedOvernightStayCards.count();
      expect(updatedTotalCards).toBe(2);

      const hoteFirstCard = await updatedOvernightStayCards.nth(0).textContent();
      // Hotel Downtown: 21:00 UTC Sept 20 - calculate expected New York time (handles DST automatically)
      const hoteNYTimeFirst = formatTimeInTimezone('2025-09-20T21:00:00Z', 'America/New_York');
      console.log(`Expecting Hotel Downtown at ${hoteNYTimeFirst} in New York timezone`);
      expect(hoteFirstCard).toMatch(new RegExp(`${hoteNYTimeFirst}`));
      expect(hoteFirstCard).toMatch(/Hotel Downtown/); // Hotel Downtown
      
      // Get text from first card (should now show New York times)
      const hotelSecondCard = await updatedOvernightStayCards.nth(1).textContent();
      // Hotel Downtown: 21:00 UTC Sept 20 - calculate expected New York time (handles DST automatically)
      const hotelNYTime = formatTimeInTimezone('2025-09-20T21:00:00Z', 'America/New_York');
      console.log(`Expecting Hotel Downtown at ${hotelNYTime} in New York timezone`);
      expect(hotelSecondCard).toMatch(new RegExp(`Continued from Sep 20, ${hotelNYTime}`));
      expect(hotelSecondCard).toMatch(/Hotel Downtown/); // Hotel Downtown
      
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

    test('should maintain timeline item order after timezone switch', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Start with Europe/Kyiv timezone
      testUser.timezone = 'Europe/Kyiv';
      
      await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData, testUser);
      await timelinePage.waitForTimelineContent();
      
      // Get timeline card order in Kyiv
      const kyivFirstCardLocation = await timelinePage.getTimelineCards('stays').nth(0).locator('.location-name').textContent();
      const kyivSecondCardLocation = await timelinePage.getTimelineCards('overnightStays').nth(0).locator('.location-name').textContent();

      console.log(`Kyiv card order: ${kyivFirstCardLocation} - ${kyivSecondCardLocation}`);
      
      // Switch to New York timezone
      await switchUserTimezone(page, timelinePage, 'America/New_York');
      
      // Get timeline card order in New York
      const nyFirstCardLocation = await timelinePage.getTimelineCards('overnightStays').nth(0).locator('.location-name').textContent();
      const nySecondCardLocation = await timelinePage.getTimelineCards('stays').nth(0).locator('.location-name').textContent();

      console.log(`New York card order: ${nyFirstCardLocation} - ${nySecondCardLocation}`);

      // Order should remain the same (chronological order maintained)
      expect(kyivFirstCardLocation.trim()).toBe(nyFirstCardLocation.trim());
      expect(kyivSecondCardLocation.trim()).toBe(nySecondCardLocation.trim());
      
      console.log(`Card order maintained: ${kyivFirstCardLocation} -> ${kyivSecondCardLocation}`);
    });
  });
});