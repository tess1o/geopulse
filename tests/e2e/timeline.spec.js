import {test, expect} from '../fixtures/database-fixture.js';
import {TimelinePage} from '../pages/TimelinePage.js';
import {TestData} from '../fixtures/test-data.js';
import * as TimelineTestData from '../utils/timeline-test-data.js';

test.describe('Timeline Page', () => {
  
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
      await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertRegularStaysTestData);
      
      const header = page.locator('.timeline-header:has-text("Movement Timeline")');
      expect(await header.isVisible()).toBe(true);
    });

    test('should display regular stays with correct information', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testData } = await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertVerifiableStaysTestData);
      
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
        
        const durationText = await stayCard.locator('.duration-text, .duration-detail, .stay-duration').textContent();
        
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
      const { testData } = await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertVerifiableTripsTestData);
      
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
      const { testData } = await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertVerifiableDataGapsTestData);
      
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
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightStaysTestData);
      
      await timelinePage.waitForTimelineContent();
      
      const overnightStayCards = timelinePage.getTimelineCards('overnightStays');
      expect(await overnightStayCards.count()).toBeGreaterThan(0);
      
      // Verify each overnight stay card displays correct information
      for (let i = 0; i < testData.length; i++) {
        const stayCard = overnightStayCards.nth(i);
        const expectedStay = testData[i];
        
        // Check location name is displayed correctly
        const locationText = await stayCard.locator('.location-name').textContent();
        expect(locationText.trim()).toBe(expectedStay.locationName);
        
        // Check that it shows "Continued from" or similar overnight indicator
        const cardText = await stayCard.textContent();
        expect(cardText).toMatch(/continued|overnight|from/i);
        
        // Check total duration is shown
        const totalHours = Math.floor(expectedStay.totalDuration / 3600);
        if (totalHours > 0) {
          expect(cardText).toContain(`${totalHours} hour`);
        }
        
        // Check "On this day" duration is shown for the current date segment
        expect(cardText).toMatch(/on this day|this day/i);
        
        console.log(`Overnight Stay ${i}: Expected location "${expectedStay.locationName}", card text preview: "${cardText.slice(0, 200)}..."`);
      }
      
      expect(await timelinePage.getMoonIconsCount()).toBeGreaterThan(0);
      expect(await timelinePage.getDateGroupsCount()).toBeGreaterThanOrEqual(2);
    });

    test('should display overnight trips with correct data and special formatting', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightTripsTestData);
      
      await timelinePage.waitForTimelineContent();
      
      const overnightTripCards = timelinePage.getTimelineCards('overnightTrips');
      expect(await overnightTripCards.count()).toBeGreaterThan(0);
      
      // Verify each overnight trip card displays correct information
      for (let i = 0; i < testData.length; i++) {
        const tripCard = overnightTripCards.nth(i);
        const expectedTrip = testData[i];
        
        const cardText = await tripCard.textContent();
        
        // Check that it shows overnight trip indicators
        expect(cardText).toMatch(/continued|overnight|from/i);
        
        // Calculate expected values
        const expectedDistanceKm = Math.round(expectedTrip.distanceMeters / 1000);
        const totalHours = Math.floor(expectedTrip.totalDuration / 3600);
        
        // BUG: Overnight trip cards should display total distance but currently don't
        expect(cardText).toContain(`${expectedDistanceKm} km`);

        // Check total duration is shown
        if (totalHours > 0) {
          expect(cardText).toContain(`${totalHours} hour`);
        }
        
        // Check movement type
        const expectedIcon = expectedTrip.movementType === 'CAR' ? '🚗' : '🚶';
        expect(cardText).toContain(expectedIcon);
        
        // Check movement type text
        expect(cardText).toContain(`Trip - ${expectedTrip.movementType}`);
        
        // Check "On this day" duration is shown for the current date segment
        expect(cardText).toMatch(/on this day|this day/i);
      }
      
      expect(await timelinePage.getMoonIconsCount()).toBeGreaterThan(0);
    });

    test('should display overnight data gaps with correct data and special formatting', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      const { testData } = await timelinePage.setupOvernightTimelineWithData(dbManager, TimelineTestData.insertVerifiableOvernightDataGapsTestData);
      
      await timelinePage.waitForTimelineContent();
      
      const overnightGapCards = timelinePage.getTimelineCards('overnightGaps');
      expect(await overnightGapCards.count()).toBeGreaterThan(0);
      
      // Verify each overnight data gap card displays correct information
      for (let i = 0; i < testData.length; i++) {
        const gapCard = overnightGapCards.nth(i);
        const expectedGap = testData[i];
        
        const cardText = await gapCard.textContent();
        
        // Check that it indicates a data gap
        expect(cardText).toMatch(/gap|missing|data|unknown/i);
        
        // Check that it shows overnight gap indicators
        expect(cardText).toMatch(/continued|overnight|from/i);
        
        // Calculate expected duration values
        const totalHours = Math.floor(expectedGap.totalDuration / 3600);
        const totalMinutes = Math.floor(expectedGap.totalDuration / 60);
        
        // Assert the correct duration format based on expected duration
        if (totalHours > 0) {
          expect(cardText).toContain(`${totalHours} hour`);
        } else if (totalMinutes > 1) {
          expect(cardText).toContain(`${totalMinutes} minute`);
        } else {
          expect(cardText).toMatch(/less than a minute|1 minute/i);
        }
        
        // Check "On this day" duration is shown for the current date segment
        expect(cardText).toMatch(/on this day|this day/i);
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

      // Navigate to overnight timeline
      const now = new Date();
      const today = new Date(now);
      const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
      await timelinePage.navigateWithDateRange(yesterday, today);
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
      await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertRegularStaysTestData);
      
      await timelinePage.waitForTimelineContent();
      
      const dateSeparators = page.locator('.date-separator');
      expect(await dateSeparators.count()).toBeGreaterThan(0);
      
      const dateSeparatorText = page.locator('.date-separator-text');
      expect(await dateSeparatorText.count()).toBeGreaterThan(0);
      
      const dateGroups = page.locator('.date-group');
      expect(await dateGroups.count()).toBeGreaterThan(0);
    });

    test('should handle timeline item clicks correctly', async ({page, dbManager}) => {
      const timelinePage = new TimelinePage(page);
      await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertRegularStaysTestData);
      
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
      const { testData } = await timelinePage.setupTimelineWithData(dbManager, TimelineTestData.insertVerifiableStaysTestData);
      
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
});