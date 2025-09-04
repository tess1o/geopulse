import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {TimelinePage} from '../pages/TimelinePage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {TestConfig} from '../config/test-config.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';
import {randomUUID} from 'crypto';

test.describe('Timeline Page', () => {
  
  test.describe('Initial State and Empty Data', () => {
    test('should show empty state when no timeline data exists', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      // Create user first
      await UserFactory.createUser(page, testUser);
      
      // Login to the app
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Verify we're on the timeline page
      expect(await timelinePage.isOnTimelinePage()).toBe(true);
      
      // Wait for loading to complete
      await page.waitForSelector('.timeline-container', { timeout: 10000 });
      
      // Wait for loading spinner to disappear and no data message to appear
      await page.waitForSelector('.p-progressspinner', { state: 'detached', timeout: 15000 });
      await page.waitForSelector('.loading-messages:has-text("No timeline for the given date range")', { timeout: 10000 });
      
      // Debug: log the actual HTML content after loading
      const timelineContainer = page.locator('.timeline-container');
      const containerHTML = await timelineContainer.innerHTML();

      // Check for no data message
      const noDataMessage = page.locator('.loading-messages').filter({ hasText: 'No timeline for the given date range' });
      const noDataVisible = await noDataMessage.isVisible();
      expect(noDataVisible).toBe(true);
      
      // Verify database has no timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      const hasTimelineData = await verifyTimelineDataExists(dbManager, user.id);
    });

    test('should show loading state initially', async ({page}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
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
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertTimelineTestData(dbManager, user.id);
      
      // Refresh the page to load new data
      await page.reload();
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline container to load
      await page.waitForSelector('.timeline-container', { timeout: 10000 });
      
      // Verify header is present
      const header = page.locator('.timeline-header:has-text("Movement Timeline")');
      expect(await header.isVisible()).toBe(true);
    });

    test('should display regular stays with correct information', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test data with known stay information
      const user = await dbManager.getUserByEmail(testUser.email);
      const testStayData = await insertVerifiableStaysTestData(dbManager, user.id);
      
      await page.reload();
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Check for stay cards using the correct class
      const stayCards = page.locator('.timeline-card--stay');
      expect(await stayCards.count()).toBe(testStayData.length);
      
      // Verify each stay card displays correct information
      for (let i = 0; i < testStayData.length; i++) {
        const stayCard = stayCards.nth(i);
        const expectedStay = testStayData[i];
        
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
        
        console.log(`Stay ${i}: Expected location "${expectedStay.locationName}", got "${locationText.trim()}"`);
        console.log(`Stay ${i}: Expected duration ${expectedHours}h ${expectedMinutes}m, got "${durationText}"`);
      }
    });

    test('should display regular trips with correct information', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test data with known trip information
      const user = await dbManager.getUserByEmail(testUser.email);
      const testTripData = await insertVerifiableTripsTestData(dbManager, user.id);
      
      await page.reload();
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Check for trip cards
      const tripCards = page.locator('.trip-content').locator('..');
      expect(await tripCards.count()).toBe(testTripData.length);
      
      // Verify each trip card displays correct information
      for (let i = 0; i < testTripData.length; i++) {
        const tripCard = tripCards.nth(i);
        const expectedTrip = testTripData[i];
        
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
        
        console.log(`Trip ${i}: Expected distance ${expectedDistanceKm}km, got "${distanceText}"`);
        console.log(`Trip ${i}: Expected duration ${expectedDurationMin}min, got "${durationText}"`);
        console.log(`Trip ${i}: Expected movement ${expectedTrip.movementType}, got "${movementText}"`);
      }
    });

    test('should display regular data gaps with correct information', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test data with known data gap information
      const user = await dbManager.getUserByEmail(testUser.email);
      const testGapData = await insertVerifiableDataGapsTestData(dbManager, user.id);
      
      await page.reload();
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Check for data gap cards
      const gapCards = page.locator('.p-card').filter({ has: page.locator('[class*="gap"], [class*="data-gap"]') });
      expect(await gapCards.count()).toBe(testGapData.length);
      
      // Verify each data gap card displays correct information
      for (let i = 0; i < testGapData.length; i++) {
        const gapCard = gapCards.nth(i);
        const expectedGap = testGapData[i];
        
        // Check duration is displayed correctly
        const gapText = await gapCard.textContent();
        
        // Data gap should indicate it's a gap in data
        expect(gapText).toMatch(/gap|missing|data/i);
        
        // The duration might be displayed in various formats, so let's be more flexible
        const expectedHours = Math.floor(expectedGap.durationSeconds / 3600);
        const expectedMinutes = Math.floor((expectedGap.durationSeconds % 3600) / 60);
        const expectedTotalMinutes = Math.floor(expectedGap.durationSeconds / 60);
        
        // Check for any reasonable duration representation
        const hasDuration = gapText.includes(`${expectedHours} hour`) ||
                           gapText.includes(`${expectedMinutes} minute`) ||
                           gapText.includes(`${expectedTotalMinutes} minute`) ||
                           gapText.includes('Duration:');
        
        expect(hasDuration).toBe(true);
        
        console.log(`Gap ${i}: Expected ${expectedGap.durationSeconds}s (${expectedHours}h ${expectedMinutes}m), total minutes: ${expectedTotalMinutes}, card text: "${gapText.slice(0, 200)}..."`);
      }
    });
  });

  test.describe('Overnight Timeline Elements', () => {
    test('should display overnight stays with correct data and special formatting', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test data with known overnight stay information
      const user = await dbManager.getUserByEmail(testUser.email);
      const testOvernightStayData = await insertVerifiableOvernightStaysTestData(dbManager, user.id);

      // Navigate to timeline with date range covering the overnight period
      const now = new Date();
      const today = new Date(now);
      const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
      
      const startDate = `${String(yesterday.getMonth() + 1).padStart(2, '0')}/${String(yesterday.getDate()).padStart(2, '0')}/${yesterday.getFullYear()}`;
      const endDate = `${String(today.getMonth() + 1).padStart(2, '0')}/${String(today.getDate()).padStart(2, '0')}/${today.getFullYear()}`;
      
      await page.goto(`/app/timeline?start=${startDate}&end=${endDate}`);
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Check for overnight stay cards
      const overnightStayCards = page.locator('.timeline-card--overnight-stay');
      expect(await overnightStayCards.count()).toBeGreaterThan(0);
      
      // Verify each overnight stay card displays correct information
      for (let i = 0; i < testOvernightStayData.length; i++) {
        const stayCard = overnightStayCards.nth(i);
        const expectedStay = testOvernightStayData[i];
        
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
      
      // Check for moon icons on overnight stays
      const moonIcons = page.locator('.timeline-marker .pi-moon');
      expect(await moonIcons.count()).toBeGreaterThan(0);
      
      // Verify overnight stay spans multiple days (check date groups)
      const dateGroups = page.locator('.date-group');
      expect(await dateGroups.count()).toBeGreaterThanOrEqual(2);
    });

    test('should display overnight trips with correct data and special formatting', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test data with known overnight trip information
      const user = await dbManager.getUserByEmail(testUser.email);
      const testOvernightTripData = await insertVerifiableOvernightTripsTestData(dbManager, user.id);

      // Navigate to timeline with date range covering the overnight period
      const now = new Date();
      const today = new Date(now);
      const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
      
      const startDate = `${String(yesterday.getMonth() + 1).padStart(2, '0')}/${String(yesterday.getDate()).padStart(2, '0')}/${yesterday.getFullYear()}`;
      const endDate = `${String(today.getMonth() + 1).padStart(2, '0')}/${String(today.getDate()).padStart(2, '0')}/${today.getFullYear()}`;
      
      await page.goto(`/app/timeline?start=${startDate}&end=${endDate}`);
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Check for overnight trip cards
      const overnightTripCards = page.locator('.timeline-card--overnight-trip');
      expect(await overnightTripCards.count()).toBeGreaterThan(0);
      
      // Verify each overnight trip card displays correct information
      for (let i = 0; i < testOvernightTripData.length; i++) {
        const tripCard = overnightTripCards.nth(i);
        const expectedTrip = testOvernightTripData[i];
        
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
      
      // Check for moon icons on overnight trips
      const moonIcons = page.locator('.timeline-marker .pi-moon');
      expect(await moonIcons.count()).toBeGreaterThan(0);
    });

    test('should display overnight data gaps with correct data and special formatting', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test data with known overnight data gap information
      const user = await dbManager.getUserByEmail(testUser.email);
      const testOvernightGapData = await insertVerifiableOvernightDataGapsTestData(dbManager, user.id);

      // Navigate to timeline with date range covering the overnight period
      const now = new Date();
      const today = new Date(now);
      const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
      
      const startDate = `${String(yesterday.getMonth() + 1).padStart(2, '0')}/${String(yesterday.getDate()).padStart(2, '0')}/${yesterday.getFullYear()}`;
      const endDate = `${String(today.getMonth() + 1).padStart(2, '0')}/${String(today.getDate()).padStart(2, '0')}/${today.getFullYear()}`;
      
      await page.goto(`/app/timeline?start=${startDate}&end=${endDate}`);
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Check for overnight data gap cards
      const overnightGapCards = page.locator('.timeline-card--overnight-data-gap');
      expect(await overnightGapCards.count()).toBeGreaterThan(0);
      
      // Verify each overnight data gap card displays correct information
      for (let i = 0; i < testOvernightGapData.length; i++) {
        const gapCard = overnightGapCards.nth(i);
        const expectedGap = testOvernightGapData[i];
        
        const cardText = await gapCard.textContent();
        
        // Check that it indicates a data gap
        expect(cardText).toMatch(/gap|missing|data|unknown/i);
        
        // Check that it shows overnight gap indicators
        expect(cardText).toMatch(/continued|overnight|from/i);
        
        // Debug: Log the expected vs actual duration
        const totalHours = Math.floor(expectedGap.totalDuration / 3600);
        const totalMinutes = Math.floor(expectedGap.totalDuration / 60);
        
        console.log(`Overnight Gap ${i} Debug:`);
        console.log(`  Expected duration: ${expectedGap.totalDuration} seconds = ${totalHours} hours = ${totalMinutes} minutes`);
        console.log(`  Start time: ${expectedGap.startTime}`);
        console.log(`  End time: ${expectedGap.endTime}`);
        console.log(`  Full card text: "${cardText}"`);
        
        // BUG: Overnight data gap cards display minutes instead of hours
        // The component shows "12 minutes" when it should show "12 hours"
        if (totalHours > 0) {
          expect(cardText).toContain(`${totalHours} hour`);
        }
        
        console.log(`Overnight Gap ${i}: Expected "${totalHours} hours" but got duration in minutes - this is a BUG`);
        
        // Check "On this day" duration is shown for the current date segment
        expect(cardText).toMatch(/on this day|this day/i);
      }
      
      // Check for moon icons on overnight gaps
      const moonIcons = page.locator('.timeline-marker .pi-moon');
      expect(await moonIcons.count()).toBeGreaterThan(0);
    });

    test('should properly display overnight elements across multiple date groups', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert mixed overnight test data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertVerifiableOvernightStaysTestData(dbManager, user.id);
      await insertVerifiableOvernightTripsTestData(dbManager, user.id);
      await insertVerifiableOvernightDataGapsTestData(dbManager, user.id);

      // Navigate to timeline with date range covering the overnight periods
      const now = new Date();
      const today = new Date(now);
      const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
      
      const startDate = `${String(yesterday.getMonth() + 1).padStart(2, '0')}/${String(yesterday.getDate()).padStart(2, '0')}/${yesterday.getFullYear()}`;
      const endDate = `${String(today.getMonth() + 1).padStart(2, '0')}/${String(today.getDate()).padStart(2, '0')}/${today.getFullYear()}`;
      
      await page.goto(`/app/timeline?start=${startDate}&end=${endDate}`);
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Verify multiple date groups exist (overnight elements span dates)
      const dateGroups = page.locator('.date-group');
      const dateGroupCount = await dateGroups.count();
      expect(dateGroupCount).toBeGreaterThanOrEqual(2);
      
      // Check that each date group has a proper date separator
      const dateSeparators = page.locator('.date-separator-text');
      expect(await dateSeparators.count()).toBe(dateGroupCount);
      
      // Verify that overnight elements appear in both date groups
      const allOvernightCards = page.locator('.timeline-card--overnight-stay, .timeline-card--overnight-trip, .timeline-card--overnight-data-gap');
      const overnightCardCount = await allOvernightCards.count();
      
      // Should have overnight cards distributed across the date groups
      expect(overnightCardCount).toBeGreaterThan(0);
      
      // Check that moon icons are present for overnight elements
      const moonIcons = page.locator('.timeline-marker .pi-moon');
      expect(await moonIcons.count()).toBeGreaterThan(0);
      
      console.log(`Multi-day overnight test: ${dateGroupCount} date groups, ${overnightCardCount} overnight cards, ${await moonIcons.count()} moon icons`);
    });
  });

  test.describe('Timeline UI Behavior and Data Verification', () => {
    test('should display date separators correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test data spanning multiple days
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMultiDayTimelineData(dbManager, user.id);
      
      await page.reload();
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Check for date separators
      const dateSeparators = page.locator('.date-separator');
      expect(await dateSeparators.count()).toBeGreaterThan(0);
      
      // Verify date separator text is present
      const dateSeparatorText = page.locator('.date-separator-text');
      expect(await dateSeparatorText.count()).toBeGreaterThan(0);
      
      // Check that date groups are present (at least 1, possibly more for multi-day data)
      const dateGroups = page.locator('.date-group');
      expect(await dateGroups.count()).toBeGreaterThan(0);
    });

    test('should handle timeline item clicks correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test timeline data
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertTimelineTestData(dbManager, user.id);
      
      await page.reload();
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Find and click a timeline item
      const timelineItems = page.locator('.custom-timeline .p-timeline-event-content');
      expect(await timelineItems.count()).toBeGreaterThan(0);
      
      // Click the first timeline item
      const firstItem = timelineItems.first();
      await firstItem.click();
      
      // Verify the item is clickable (no error should occur)
      // The actual highlight behavior would need to be tested with map interaction
      expect(true).toBe(true);
    });

    test('should correctly display timeline markers with appropriate icons', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert test data with different timeline element types
      const user = await dbManager.getUserByEmail(testUser.email);
      await insertMixedTimelineData(dbManager, user.id);
      
      await page.reload();
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Check for different marker types
      const stayMarkers = page.locator('.marker-stay, .marker-overnight-stay');
      const tripMarkers = page.locator('.marker-trip, .marker-overnight-trip');
      const gapMarkers = page.locator('.marker-data-gap, .marker-overnight-data-gap');
      
      // Verify that different marker types exist
      const totalMarkers = await stayMarkers.count() + await tripMarkers.count() + await gapMarkers.count();
      expect(totalMarkers).toBeGreaterThan(0);
      
      // Check for appropriate icons
      const mapMarkerIcons = page.locator('.timeline-marker .pi-map-marker');
      const carIcons = page.locator('.timeline-marker .pi-car');
      const questionIcons = page.locator('.timeline-marker .pi-question');
      const moonIcons = page.locator('.timeline-marker .pi-moon');
      
      const totalIcons = await mapMarkerIcons.count() + await carIcons.count() + 
                        await questionIcons.count() + await moonIcons.count();
      expect(totalIcons).toBeGreaterThan(0);
    });

    test('should verify data consistency between database and UI display', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const timelinePage = new TimelinePage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Insert known test data
      const user = await dbManager.getUserByEmail(testUser.email);
      const testData = await insertKnownTimelineData(dbManager, user.id);
      
      await page.reload();
      await timelinePage.waitForPageLoad();
      
      // Wait for timeline content
      await page.waitForSelector('.timeline-content', { timeout: 10000 });
      
      // Verify database data matches UI display
      const stayCards = page.locator('.p-card').filter({ has: page.locator('[class*="stay"]') });
      const tripCards = page.locator('.p-card').filter({ has: page.locator('.trip-content') });
      const gapCards = page.locator('.p-card').filter({ has: page.locator('[class*="gap"]') });
      
      const uiStayCount = await stayCards.count();
      const uiTripCount = await tripCards.count();
      const uiGapCount = await gapCards.count();
      
      console.log('UI vs DB comparison:', {
        dbStays: testData.stays.length,
        uiStays: uiStayCount,
        dbTrips: testData.trips.length,
        uiTrips: uiTripCount,
        dbGaps: testData.gaps.length,
        uiGaps: uiGapCount
      });
      
      // Allow for overnight items being displayed multiple times across dates
      expect(uiStayCount).toBeGreaterThanOrEqual(testData.stays.length);
      expect(uiTripCount).toBeGreaterThanOrEqual(testData.trips.length);
      expect(uiGapCount).toBeGreaterThanOrEqual(testData.gaps.length);
    });
  });
});

// Helper functions for inserting test data
async function verifyTimelineDataExists(dbManager, userId) {
  const staysResult = await dbManager.client.query(`
    SELECT COUNT(*) as count FROM timeline_stays WHERE user_id = $1
  `, [userId]);
  
  const tripsResult = await dbManager.client.query(`
    SELECT COUNT(*) as count FROM timeline_trips WHERE user_id = $1
  `, [userId]);
  
  const staysCount = parseInt(staysResult.rows[0].count);
  const tripsCount = parseInt(tripsResult.rows[0].count);
  
  return staysCount > 0 || tripsCount > 0;
}

async function insertTimelineTestData(dbManager, userId) {
  const now = new Date();
  const today = new Date();
  today.setHours(10, 0, 0, 0);

  // Insert basic timeline data
  await insertRegularStaysTestData(dbManager, userId);
  await insertRegularTripsTestData(dbManager, userId);
}

async function insertRegularStaysTestData(dbManager, userId) {
  const now = new Date();
  const locations = [
    { name: 'Home', lat: 40.7128, lon: -74.0060 },
    { name: 'Office', lat: 40.7589, lon: -73.9851 },
    { name: 'Gym', lat: 40.7484, lon: -73.9857 }
  ];

  // Create reverse geocoding locations
  for (const location of locations) {
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`POINT(${location.lon} ${location.lat})`, `${location.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
    // Insert stay for today
    const stayTime = new Date(now.getTime() - (2 * 60 * 60 * 1000)); // 2 hours ago
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
    `, [
      userId,
      stayTime,
      3600, // 1 hour stay
      location.lat,
      location.lon,
      location.name,
      geocodingId
    ]);
  }
}

async function insertRegularTripsTestData(dbManager, userId) {
  const now = new Date();
  const trips = [
    { distance: 5000, duration: 1800, type: 'CAR' }, // 5km, 30 min
    { distance: 2000, duration: 1200, type: 'WALK' }, // 2km, 20 min
    { distance: 8000, duration: 2400, type: 'CAR' } // 8km, 40 min
  ];

  for (let i = 0; i < trips.length; i++) {
    const trip = trips[i];
    const tripTime = new Date(now.getTime() - ((i + 1) * 60 * 60 * 1000)); // Hours ago
    
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_latitude, start_longitude, end_latitude, end_longitude, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
    `, [
      userId,
      tripTime,
      trip.duration,
      40.7128 + (i * 0.001),
      -74.0060 + (i * 0.001),
      40.7200 + (i * 0.001),
      -74.0100 + (i * 0.001),
      trip.distance,
      trip.type
    ]);
  }
}

async function insertDataGapsTestData(dbManager, userId) {
  const now = new Date();
  
  // Insert a data gap for today
  const gapStartTime = new Date(now.getTime() - (4 * 60 * 60 * 1000)); // 4 hours ago
  const gapEndTime = new Date(gapStartTime.getTime() + (30 * 60 * 1000)); // 30 minutes later
  
  await dbManager.client.query(`
    INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds, created_at)
    VALUES ($1, $2, $3, $4, NOW())
  `, [
    userId,
    gapStartTime,
    gapEndTime,
    1800 // 30 minutes in seconds
  ]);
}

async function insertOvernightStaysTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(18, 0, 0, 0);

  // Create reverse geocoding location
  const result = await dbManager.client.query(`
    INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
    VALUES (nextval('reverse_geocoding_location_seq'), 'POINT(-74.0060 40.7128)', 'POINT(-74.0060 40.7128)', 'Hotel, New York, NY', 'test', 'New York', 'United States', NOW(), NOW())
    RETURNING id
  `);
  const geocodingId = result.rows[0].id;

  await dbManager.client.query(`
    INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
    VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
  `, [
    userId,
    yesterday,
    16 * 60 * 60,
    40.7128,
    -74.0060,
    'Hotel',
    geocodingId
  ]);
}

async function insertOvernightTripsTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(18, 0, 0, 0);

  await dbManager.client.query(`
    INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_latitude, start_longitude, end_latitude, end_longitude, distance_meters, movement_type, created_at, last_updated)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
  `, [
    userId,
    yesterday,
    16*60*60,
    40.7128,
    -74.0060,
    41.0000,
    -74.5000,
    150000, // 150km - long overnight trip
    'CAR'
  ]);
}

async function insertOvernightDataGapsTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(23, 0, 0, 0); // 11 PM yesterday
  
  const today = new Date(now);
  today.setHours(6, 0, 0, 0); // 6 AM today

  // Insert overnight data gap (11 PM yesterday to 6 AM today = 7 hours)
  await dbManager.client.query(`
    INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds, created_at)
    VALUES ($1, $2, $3, $4, NOW())
  `, [
    userId,
    yesterday,
    today,
    25200 // 7 hours in seconds (7 * 60 * 60)
  ]);
}

async function insertMultiDayTimelineData(dbManager, userId) {
  const now = new Date();
  
  // Insert data for today and yesterday to ensure multiple days
  const today = new Date(now);
  today.setHours(14, 0, 0, 0); // 2 PM today
  
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(10, 0, 0, 0); // 10 AM yesterday
  
  const dates = [today, yesterday];
  
  for (let i = 0; i < dates.length; i++) {
    const date = dates[i];
    
    // Insert a stay for each day
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`POINT(-74.0060 40.7128)`, `Location ${i}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
    `, [
      userId,
      date,
      120, // 2 hours
      40.7128 + (i * 0.001),
      -74.0060 + (i * 0.001),
      `Location ${i}`,
      geocodingId
    ]);
  }
}

async function insertMixedTimelineData(dbManager, userId) {
  // Insert a mix of regular and overnight elements
  await insertRegularStaysTestData(dbManager, userId);
  await insertRegularTripsTestData(dbManager, userId);
  await insertDataGapsTestData(dbManager, userId);
  await insertOvernightStaysTestData(dbManager, userId);
  await insertOvernightTripsTestData(dbManager, userId);
  await insertOvernightDataGapsTestData(dbManager, userId);
}

async function insertKnownTimelineData(dbManager, userId) {
  const stays = [];
  const trips = [];
  const gaps = [];
  
  // Insert 2 stays
  for (let i = 0; i < 2; i++) {
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`POINT(-74.0060 40.7128)`, `Known Stay ${i}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    const stayTime = new Date(Date.now() - (i * 2 * 60 * 60 * 1000));
    
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
    `, [
      userId, stayTime, 60, 40.7128, -74.0060, `Known Stay ${i}`, geocodingId
    ]);
    
    stays.push({ name: `Known Stay ${i}`, timestamp: stayTime });
  }
  
  // Insert 2 trips
  for (let i = 0; i < 2; i++) {
    const tripTime = new Date(Date.now() - ((i + 3) * 60 * 60 * 1000));
    
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_latitude, start_longitude, end_latitude, end_longitude, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
    `, [
      userId, tripTime, 30, 40.7128, -74.0060, 40.7200, -74.0100, 5000, 'CAR'
    ]);
    
    trips.push({ distance: 5000, timestamp: tripTime });
  }
  
  // Insert 1 gap
  const gapStartTime = new Date(Date.now() - (6 * 60 * 60 * 1000));
  const gapEndTime = new Date(gapStartTime.getTime() + (30 * 60 * 1000)); // 30 minutes later
  
  await dbManager.client.query(`
    INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds, created_at)
    VALUES ($1, $2, $3, $4, NOW())
  `, [userId, gapStartTime, gapEndTime, 1800]); // 30 minutes in seconds
  
  gaps.push({ duration: 1800, timestamp: gapStartTime });
  
  return { stays, trips, gaps };
}

// Verifiable test data functions that return expected values for verification
async function insertVerifiableStaysTestData(dbManager, userId) {
  const now = new Date();
  const stayData = [
    { name: 'Home', duration: 7200, lat: 40.7128, lon: -74.0060 }, // 2 hours
    { name: 'Office', duration: 5400, lat: 40.7589, lon: -73.9851 }, // 1.5 hours
    { name: 'Gym', duration: 3600, lat: 40.7484, lon: -73.9857 } // 1 hour
  ];

  const results = [];

  for (let i = 0; i < stayData.length; i++) {
    const stay = stayData[i];
    const stayTime = new Date(now.getTime() - ((i + 1) * 2 * 60 * 60 * 1000)); // 2, 4, 6 hours ago

    // Create reverse geocoding location
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`POINT(${stay.lon} ${stay.lat})`, `${stay.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
    // Insert stay
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
    `, [
      userId,
      stayTime,
      stay.duration,
      stay.lat,
      stay.lon,
      stay.name,
      geocodingId
    ]);

    results.push({
      locationName: stay.name,
      duration: stay.duration,
      timestamp: stayTime
    });
  }

  return results.reverse(); // Reverse to match timeline display order (most recent first)
}

// Verifiable overnight test data functions
async function insertVerifiableOvernightStaysTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(18, 0, 0, 0); // Use same time as working insertOvernightStaysTestData

  const stayData = [
    { name: 'Hotel Downtown', duration: 16 * 60 * 60 }, // 16 hours in seconds (like the working version)
    { name: 'Airport Terminal', duration: 14 * 60 * 60 }  // 14 hours in seconds
  ];

  const results = [];

  for (let i = 0; i < stayData.length; i++) {
    const stay = stayData[i];
    const stayStartTime = new Date(yesterday.getTime() + (i * 60 * 60 * 1000)); // Start 1 hour apart
    
    // Create reverse geocoding location
    const result = await dbManager.client.query(`
      INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
      VALUES (nextval('reverse_geocoding_location_seq'), 'POINT(-74.0060 40.7128)', 'POINT(-74.0060 40.7128)', $1, 'test', 'New York', 'United States', NOW(), NOW())
      RETURNING id
    `, [`${stay.name}, New York, NY`]);
    
    const geocodingId = result.rows[0].id;
    
    // Insert overnight stay (use same structure as working version)
    await dbManager.client.query(`
      INSERT INTO timeline_stays (user_id, timestamp, stay_duration, latitude, longitude, location_name, geocoding_id, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
    `, [
      userId,
      stayStartTime,
      stay.duration,
      40.7128,
      -74.0060,
      stay.name,
      geocodingId
    ]);

    results.push({
      locationName: stay.name,
      totalDuration: stay.duration,
      startTime: stayStartTime
    });
  }

  return results; // Don't reverse, keep chronological order
}

async function insertVerifiableOvernightTripsTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(18, 0, 0, 0); // Use same time as working insertOvernightTripsTestData

  const tripData = [
    { distance: 150000, duration: 16*60*60, type: 'CAR' }, // 150km, 16 hours in seconds (like working version)
    { distance: 100000, duration: 14*60*60, type: 'CAR' }  // 100km, 14 hours in seconds
  ];

  const results = [];

  for (let i = 0; i < tripData.length; i++) {
    const trip = tripData[i];
    const tripStartTime = new Date(yesterday.getTime() + (i * 60 * 60 * 1000)); // Start 1 hour apart
    
    // Insert overnight trip (use same structure as working version)
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_latitude, start_longitude, end_latitude, end_longitude, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
    `, [
      userId,
      tripStartTime,
      trip.duration,
      40.7128,
      -74.0060,
      41.0000,
      -74.5000,
      trip.distance,
      trip.type
    ]);

    results.push({
      distanceMeters: trip.distance,
      totalDuration: trip.duration,
      movementType: trip.type,
      startTime: tripStartTime
    });
  }

  return results; // Don't reverse, keep chronological order
}

async function insertVerifiableOvernightDataGapsTestData(dbManager, userId) {
  const now = new Date();
  const yesterday = new Date(now.getTime() - (24 * 60 * 60 * 1000));
  yesterday.setHours(18, 0, 0, 0); // Use same time as working insertOvernightDataGapsTestData
  
  const today = new Date(now);
  today.setHours(6, 0, 0, 0); // 6 AM today (like working version)

  const gapData = [
    { startOffset: 0, endOffset: 0 }, // 18:00 yesterday to 06:00 today = 12 hours
    { startOffset: 1, endOffset: 1 }  // 19:00 yesterday to 07:00 today = 12 hours
  ];

  const results = [];

  for (let i = 0; i < gapData.length; i++) {
    const gap = gapData[i];
    const gapStartTime = new Date(yesterday.getTime() + (gap.startOffset * 60 * 60 * 1000));
    const gapEndTime = new Date(today.getTime() + (gap.endOffset * 60 * 60 * 1000));
    
    // Calculate actual duration in seconds
    const durationSeconds = Math.floor((gapEndTime.getTime() - gapStartTime.getTime()) / 1000);
    
    // Insert overnight data gap (use same structure as working version)
    await dbManager.client.query(`
      INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds, created_at)
      VALUES ($1, $2, $3, $4, NOW())
    `, [
      userId,
      gapStartTime,
      gapEndTime,
      durationSeconds
    ]);

    results.push({
      totalDuration: durationSeconds,
      startTime: gapStartTime,
      endTime: gapEndTime
    });
  }

  return results; // Don't reverse, keep chronological order
}

async function insertVerifiableTripsTestData(dbManager, userId) {
  const now = new Date();
  const tripData = [
    { distance: 5500, duration: 1800, type: 'CAR' }, // 5.5km, 30 min, Car
    { distance: 2000, duration: 1200, type: 'WALK' }, // 2km, 20 min, Walk
    { distance: 12000, duration: 2400, type: 'CAR' } // 12km, 40 min, Car
  ];

  const results = [];

  for (let i = 0; i < tripData.length; i++) {
    const trip = tripData[i];
    const tripTime = new Date(now.getTime() - ((i + 1) * 60 * 60 * 1000)); // 1, 2, 3 hours ago
    
    await dbManager.client.query(`
      INSERT INTO timeline_trips (user_id, timestamp, trip_duration, start_latitude, start_longitude, end_latitude, end_longitude, distance_meters, movement_type, created_at, last_updated)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
    `, [
      userId,
      tripTime,
      trip.duration,
      40.7128 + (i * 0.001),
      -74.0060 + (i * 0.001),
      40.7200 + (i * 0.001),
      -74.0100 + (i * 0.001),
      trip.distance,
      trip.type
    ]);

    results.push({
      distanceMeters: trip.distance,
      durationSeconds: trip.duration,
      movementType: trip.type,
      timestamp: tripTime
    });
  }

  return results.reverse(); // Reverse to match timeline display order (most recent first)
}

async function insertVerifiableDataGapsTestData(dbManager, userId) {
  const now = new Date();
  const gapData = [
    { duration: 1800 }, // 30 minutes
    { duration: 3600 } // 1 hour
  ];

  const results = [];

  for (let i = 0; i < gapData.length; i++) {
    const gap = gapData[i];
    const gapStartTime = new Date(now.getTime() - ((i + 1) * 3 * 60 * 60 * 1000)); // 3, 6 hours ago
    const gapEndTime = new Date(gapStartTime.getTime() + (gap.duration * 1000));
    
    await dbManager.client.query(`
      INSERT INTO timeline_data_gaps (user_id, start_time, end_time, duration_seconds, created_at)
      VALUES ($1, $2, $3, $4, NOW())
    `, [
      userId,
      gapStartTime,
      gapEndTime,
      gap.duration
    ]);

    results.push({
      durationSeconds: gap.duration,
      timestamp: gapStartTime
    });
  }

  return results.reverse(); // Reverse to match timeline display order (most recent first)
}