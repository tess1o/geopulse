import { test, expect } from '../fixtures/isolated-fixture.js';
import { LoginPage } from '../pages/LoginPage.js';
import { GpsDataPage } from '../pages/GpsDataPage.js';
import { TestHelpers } from '../utils/test-helpers.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { GpsDataFactory } from '../utils/gps-data-factory.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';
import {
  LocationTimezonePlaces as Places,
  seedLocationTimezoneCities,
  setTimeDisplayMode,
  insertStay,
  insertTrip,
  insertDataGap,
} from '../utils/location-timezone-test-data.js';

// "Timeline time zone" preference: times of stays, trips, data gaps and GPS points are shown either in the profile
// timezone (default) or in the local timezone of each place, resolved from the nearest GeoNames city.
//
// All data is on Sept 21, 2025: New York is EDT (UTC-4), Tokyo is UTC+9 (Intl short name "GMT+9"),
// Kyiv and Helsinki are both UTC+3 ("GMT+3").

const SEP_21 = '/app/timeline?start=2025-09-21&end=2025-09-21';
const SEP_21_22 = '/app/timeline?start=2025-09-21&end=2025-09-22';

/**
 * Records the API requests whose path matches, so tests can check the query parameters that were sent.
 * Only XHR/fetch count: page navigations such as /app/timeline would otherwise match /timeline$ too.
 */
const trackApiRequests = (page, pathPattern) => {
  const urls = [];
  page.on('request', (request) => {
    if (!['xhr', 'fetch'].includes(request.resourceType())) {
      return;
    }
    const url = new URL(request.url());
    if (pathPattern.test(url.pathname)) {
      urls.push(url);
    }
  });
  return urls;
};

/**
 * Creates a user, applies the time display mode and inserts data before the first login, so the app starts in that
 * mode (and loads the home country for "location" mode) exactly as it would for a returning user.
 */
const createUserAndLogin = async (page, dbManager, isolatedUsers, { timezone, timeDisplayMode, seed }) => {
  const testUser = await isolatedUsers.create(page, { timezone });
  const user = await dbManager.getUserByEmail(testUser.email);

  if (timeDisplayMode) {
    await setTimeDisplayMode(dbManager, user.id, timeDisplayMode);
  }
  if (seed) {
    await seed(user.id);
  }

  const loginPage = new LoginPage(page);
  await loginPage.navigate();
  await loginPage.login(testUser.email, testUser.password);
  await TestHelpers.waitForNavigation(page, '**/app/timeline', 30000);

  return { testUser, user };
};

/** A day that starts in Tokyo and ends in New York, with a data gap between the two. */
const seedTokyoThenNewYork = (dbManager) => async (userId) => {
  await insertStay(dbManager, userId, Places.TOKYO_HOTEL, {
    timestamp: '2025-09-21T12:00:00Z', // Tokyo 21:00, New York 08:00
    durationSeconds: 3600,
  });
  await insertTrip(dbManager, userId, Places.TOKYO_HOTEL, Places.TOKYO_HANEDA, {
    timestamp: '2025-09-21T13:00:00Z', // Tokyo 22:00, New York 09:00
    durationSeconds: 1800,
    distanceMeters: 18000,
  });
  await insertDataGap(dbManager, userId, {
    startTime: '2025-09-21T13:30:00Z', // after the trip into Haneda: Tokyo 22:30
    endTime: '2025-09-21T16:00:00Z', // before the New York stay: New York 12:00
  });
  await insertStay(dbManager, userId, Places.NEW_YORK_OFFICE, {
    timestamp: '2025-09-21T16:00:00Z', // New York 12:00
    durationSeconds: 7200,
  });
};

const stayCard = (page, name) => page.locator('.timeline-card--stay').filter({ hasText: name });
const tripCards = (page) => page.locator('.timeline-card--trip');
const dataGapCards = (page) => page.locator('.timeline-card--data-gap');
const allTimestamps = (page) => page.locator('.timeline-timestamp');

test.describe('Location time display', () => {
  test.beforeAll(async ({ dbManager }) => {
    await seedLocationTimezoneCities(dbManager);
  });

  test.describe('Timeline cards', () => {
    test('profile mode shows every item in the profile timezone and does not request location timezones', async ({ page, isolatedUsers, dbManager }) => {
      const timelineRequests = trackApiRequests(page, /\/timeline$/);
      await createUserAndLogin(page, dbManager, isolatedUsers, {
        timezone: 'America/New_York',
        seed: seedTokyoThenNewYork(dbManager),
      });

      await page.goto(SEP_21);

      const tokyoStay = stayCard(page, 'Shinjuku Hotel');
      await expect(tokyoStay.locator('.timeline-timestamp')).toContainText('09/21/2025 08:00');
      await expect(tripCards(page).first().locator('.timeline-timestamp')).toContainText('09/21/2025 09:00');
      await expect(dataGapCards(page).first().locator('.timeline-timestamp')).toContainText('09/21/2025 09:30');
      await expect(dataGapCards(page).first().locator('.detail-value').first()).toHaveText('09/21/2025 12:00');
      await expect(stayCard(page, 'Midtown Office').locator('.timeline-timestamp')).toContainText('09/21/2025 12:00');

      // No zone labels and no "where does this zone come from" hint in profile mode
      for (const timestamp of await allTimestamps(page).all()) {
        await expect(timestamp).not.toContainText(/GMT|EDT/);
        await expect(timestamp).not.toHaveAttribute('title');
      }

      expect(timelineRequests.length).toBeGreaterThan(0);
      for (const url of timelineRequests) {
        expect(url.searchParams.has('includeLocationTimezones')).toBe(false);
      }
    });

    test('location mode shows each stay, trip and data gap in local time and labels every item of a mixed day', async ({ page, isolatedUsers, dbManager }) => {
      const timelineRequests = trackApiRequests(page, /\/timeline$/);
      await createUserAndLogin(page, dbManager, isolatedUsers, {
        timezone: 'America/New_York',
        timeDisplayMode: 'location',
        seed: seedTokyoThenNewYork(dbManager),
      });

      await page.goto(SEP_21);

      const tokyoStayTimestamp = stayCard(page, 'Shinjuku Hotel').locator('.timeline-timestamp');
      await expect(tokyoStayTimestamp).toContainText('09/21/2025 21:00 GMT+9');
      await expect(tokyoStayTimestamp).toHaveAttribute(
        'title',
        /^Local time in Asia\/Tokyo, from the nearest city Tokyo \(JP, [\d.]+ km away\)$/
      );

      // Trip start time comes from its origin
      await expect(tripCards(page).first().locator('.timeline-timestamp')).toContainText('09/21/2025 22:00 GMT+9');

      // A data gap starts in the zone of the item before it and ends in the zone of the item after it
      const gap = dataGapCards(page).first();
      await expect(gap.locator('.timeline-timestamp')).toContainText('09/21/2025 22:30 GMT+9');
      await expect(gap.locator('.detail-value').first()).toHaveText('09/21/2025 12:00 EDT');

      // Home items are labelled too because the day mixes zones
      const newYorkStayTimestamp = stayCard(page, 'Midtown Office').locator('.timeline-timestamp');
      await expect(newYorkStayTimestamp).toContainText('09/21/2025 12:00 EDT');
      await expect(newYorkStayTimestamp).toHaveAttribute('title', /^Local time in America\/New_York, from the nearest city New York \(US,/);

      expect(timelineRequests.length).toBeGreaterThan(0);
      for (const url of timelineRequests) {
        expect(url.searchParams.get('includeLocationTimezones')).toBe('true');
      }
    });

    test('location mode adds no zone labels on a day spent in the profile timezone', async ({ page, isolatedUsers, dbManager }) => {
      await createUserAndLogin(page, dbManager, isolatedUsers, {
        timezone: 'America/New_York',
        timeDisplayMode: 'location',
        seed: async (userId) => {
          await insertStay(dbManager, userId, Places.NEW_YORK_OFFICE, {
            timestamp: '2025-09-21T16:00:00Z',
            durationSeconds: 7200,
          });
        },
      });

      await page.goto(SEP_21);

      const timestamp = stayCard(page, 'Midtown Office').locator('.timeline-timestamp');
      await expect(timestamp).toContainText('09/21/2025 12:00');
      await expect(timestamp).not.toContainText(/GMT|EDT/);
      // The hint is still there, so users can see where the zone came from
      await expect(timestamp).toHaveAttribute('title', /^Local time in America\/New_York/);
    });

    test('location mode labels a place abroad even when it has the same UTC offset as the profile timezone', async ({ page, isolatedUsers, dbManager }) => {
      await createUserAndLogin(page, dbManager, isolatedUsers, {
        timezone: 'Europe/Kyiv',
        timeDisplayMode: 'location',
        seed: async (userId) => {
          await insertStay(dbManager, userId, Places.HELSINKI_CAFE, {
            timestamp: '2025-09-21T09:00:00Z', // 12:00 in both Kyiv and Helsinki
            durationSeconds: 7200,
          });
        },
      });

      await page.goto(SEP_21);

      // Finland is not the profile country (UA), so the time is labelled although the clock reading is the same
      const timestamp = stayCard(page, 'Esplanadi Cafe').locator('.timeline-timestamp');
      await expect(timestamp).toContainText('09/21/2025 12:00 GMT+3');
      await expect(timestamp).toHaveAttribute('title', /^Local time in Europe\/Helsinki, from the nearest city Helsinki \(FI,/);
    });

    test('location mode falls back to the profile timezone when no city is close enough', async ({ page, isolatedUsers, dbManager }) => {
      await createUserAndLogin(page, dbManager, isolatedUsers, {
        timezone: 'America/New_York',
        timeDisplayMode: 'location',
        seed: async (userId) => {
          await insertStay(dbManager, userId, Places.MID_ATLANTIC, {
            timestamp: '2025-09-21T16:00:00Z',
            durationSeconds: 7200,
          });
        },
      });

      await page.goto(SEP_21);

      const timestamp = page.locator('.timeline-card--stay').first().locator('.timeline-timestamp');
      await expect(timestamp).toContainText('09/21/2025 12:00');
      await expect(timestamp).not.toContainText(/GMT|EDT/);
      await expect(timestamp).toHaveAttribute('title', /^Local timezone unknown: .* Showing your profile timezone\.$/);
    });

    test('location mode shows an overnight stay abroad in local time while days stay grouped by the profile timezone', async ({ page, isolatedUsers, dbManager }) => {
      await createUserAndLogin(page, dbManager, isolatedUsers, {
        timezone: 'America/New_York',
        timeDisplayMode: 'location',
        seed: async (userId) => {
          // New York: Sep 21 16:00 -> Sep 22 02:00. Tokyo: Sep 22 05:00 -> 15:00.
          await insertStay(dbManager, userId, Places.TOKYO_HOTEL, {
            timestamp: '2025-09-21T20:00:00Z',
            durationSeconds: 10 * 3600,
          });
        },
      });

      await page.goto(SEP_21_22);

      const overnightCards = page.locator('.timeline-card--overnight-stay');
      await expect(overnightCards).toHaveCount(2);

      // Start day (Sep 21 in New York) shows the Tokyo date and time of arrival
      const startDayCard = overnightCards.filter({ hasNotText: 'Continued from' });
      await expect(startDayCard.locator('.timeline-timestamp')).toContainText('09/22/2025, 05:00 GMT+9');
      await expect(startDayCard).toContainText('On this day: 16:00 - 23:59');

      const continuationCard = overnightCards.filter({ hasText: 'Continued from' });
      await expect(continuationCard.locator('.timeline-timestamp')).toContainText('Continued from Sep 22, 05:00 GMT+9');
      await expect(continuationCard).toContainText('On this day: 00:00 - 02:00');
    });
  });

  test.describe('Profile setting', () => {
    test('switching to local time is saved and applied to the timeline', async ({ page, isolatedUsers, dbManager }) => {
      const statusResponse = page.waitForResponse((response) => response.url().includes('/location-timezones/status'));
      const { profilePage, user, testUser } = await TestSetupHelper.loginAndNavigateToUserProfilePage(
        page,
        dbManager,
        createManagedUser(isolatedUsers, { timezone: 'America/New_York' })
      );

      const status = await (await statusResponse).json();
      expect(status.enabled).toBe(true);
      expect(status.available).toBe(true);

      await expect(page.locator(profilePage.selectors.profile.timeDisplayModeDropdown)).toBeVisible();
      expect(await profilePage.getSelectedTimeDisplayMode()).toBe('Profile timezone');

      await profilePage.selectTimeDisplayMode('Local time at each place');
      expect(await profilePage.getSelectedTimeDisplayMode()).toBe('Local time at each place');
      // GeoNames cities are loaded, so no "unavailable" warning
      await expect(page.getByText('Local time is unavailable')).toHaveCount(0);

      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();

      expect(await profilePage.getTimeDisplayModeFromLocalStorage()).toBe('location');
      const dbUser = await dbManager.getUserByEmail(testUser.email);
      expect(dbUser.ui_preferences?.timeDisplayMode).toBe('location');

      await page.reload();
      await profilePage.waitForPageLoad();
      expect(await profilePage.getSelectedTimeDisplayMode()).toBe('Local time at each place');

      await insertStay(dbManager, user.id, Places.TOKYO_HOTEL, {
        timestamp: '2025-09-21T12:00:00Z',
        durationSeconds: 3600,
      });
      await page.goto(SEP_21);

      await expect(stayCard(page, 'Shinjuku Hotel').locator('.timeline-timestamp')).toContainText('09/21/2025 21:00 GMT+9');
    });
  });

  test.describe('GPS data table', () => {
    test('location mode shows GPS point times in the local time of each point', async ({ page, isolatedUsers, dbManager }) => {
      const gpsRequests = trackApiRequests(page, /\/gps\/points$/);
      await createUserAndLogin(page, dbManager, isolatedUsers, {
        timezone: 'America/New_York',
        timeDisplayMode: 'location',
        seed: async (userId) => {
          await GpsDataFactory.insertGpsPoint(dbManager, {
            user_id: userId,
            latitude: Places.TOKYO_HOTEL.lat,
            longitude: Places.TOKYO_HOTEL.lon,
            timestamp: '2025-09-21T12:00:00Z',
          });
          await GpsDataFactory.insertGpsPoint(dbManager, {
            user_id: userId,
            latitude: Places.NEW_YORK_OFFICE.lat,
            longitude: Places.NEW_YORK_OFFICE.lon,
            timestamp: '2025-09-21T16:00:00Z',
          });
        },
      });

      const gpsDataPage = new GpsDataPage(page);
      await gpsDataPage.navigate();
      await gpsDataPage.waitForPageLoad();

      const rows = page.locator(gpsDataPage.selectors.tableRows);

      const tokyoRow = rows.filter({ hasText: Places.TOKYO_HOTEL.lon.toFixed(6) });
      await expect(tokyoRow.locator('.timestamp-date')).toHaveText('09/21/2025');
      await expect(tokyoRow.locator('.timestamp-time')).toHaveText('21:00:00 GMT+9');
      await expect(tokyoRow.locator('.timestamp-cell')).toHaveAttribute('title', /^Local time in Asia\/Tokyo/);

      // A point in the profile timezone keeps its plain time
      const newYorkRow = rows.filter({ hasText: Places.NEW_YORK_OFFICE.lon.toFixed(6) });
      await expect(newYorkRow.locator('.timestamp-time')).toHaveText('12:00:00');

      expect(gpsRequests.length).toBeGreaterThan(0);
      for (const url of gpsRequests) {
        expect(url.searchParams.get('includeLocationTimezones')).toBe('true');
      }
    });
  });
});
