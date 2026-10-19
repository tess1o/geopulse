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
const dateGroups = (page) => page.locator('.date-group');
const dateGroup = (page, label) => dateGroups(page).filter({
  has: page.locator('.date-separator-text', { hasText: label }),
});

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

    test('location mode dates a stay abroad by its local day, not the profile day', async ({ page, isolatedUsers, dbManager }) => {
      await createUserAndLogin(page, dbManager, isolatedUsers, {
        timezone: 'America/New_York',
        timeDisplayMode: 'location',
        seed: async (userId) => {
          // New York: Sep 21 16:00 -> Sep 22 02:00 (overnight there). Tokyo: Sep 22 05:00 -> 15:00 (one local day).
          await insertStay(dbManager, userId, Places.TOKYO_HOTEL, {
            timestamp: '2025-09-21T20:00:00Z',
            durationSeconds: 10 * 3600,
          });
        },
      });

      await page.goto(SEP_21_22);

      const stay = stayCard(page, 'Shinjuku Hotel');
      await expect(stay.locator('.timeline-timestamp')).toContainText('09/22/2025 05:00 GMT+9');
      await expect(page.locator('.timeline-card--overnight-stay')).toHaveCount(0);

      // Only the Tokyo date has items
      await expect(dateGroups(page)).toHaveCount(1);
      await expect(dateGroup(page, 'September 22, 2025').locator('.timeline-card--stay')).toHaveCount(1);
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

  test.describe('Local day boundaries', () => {
    // In local time mode a day ends at midnight wherever the user is. A trip or data gap is dated by the local clock
    // at each end; one that spans two local days is split at midnight on the departure clock (on the arrival clock
    // when the departure midnight is not inside it). Profile timezone mode keeps profile-timezone days.
    //
    // All data is in September 2025: Paris is CEST (UTC+2, "GMT+2"), Los Angeles PDT (UTC-7), New York EDT (UTC-4),
    // Tokyo UTC+9 ("GMT+9") and Honolulu HST (UTC-10).

    const day = (date) => `/app/timeline?start=${date}&end=${date}`;
    const days = (start, end) => `/app/timeline?start=${start}&end=${end}`;
    const SEP_21_LABEL = 'September 21, 2025';
    const SEP_22_LABEL = 'September 22, 2025';

    const cardsNamed = (scope, name) => scope.locator('.timeline-card').filter({ hasText: name });
    const overnightStay = (scope, name) => scope.locator('.timeline-card--overnight-stay').filter({ hasText: name });
    const overnightTrips = (scope) => scope.locator('.timeline-card--overnight-trip');
    const overnightGaps = (scope) => scope.locator('.timeline-card--overnight-data-gap');
    const startDayCard = (cards) => cards.filter({ hasNotText: 'Continued from' });
    const continuationCard = (cards) => cards.filter({ hasText: 'Continued from' });

    /** Waits until one of the tracked requests asked for exactly this from/to range. */
    const expectRangeRequested = async (requests, from, to) => {
      await expect.poll(
        () => requests.map((url) => `${url.searchParams.get('from')} -> ${url.searchParams.get('to')}`),
        { timeout: 15000 }
      ).toContain(`${from} -> ${to}`);
    };

    const trackRangeRequests = (page) => ({
      timeline: trackApiRequests(page, /\/timeline$/),
      path: trackApiRequests(page, /\/gps\/points\/path$/),
      notes: trackApiRequests(page, /\/notes\/search$/),
    });

    /** Paris 08:00-13:00, fly 11.5 h, Los Angeles 15:30 until 08:00 the next morning, Santa Monica at 16:00. */
    const seedEuropeToUs = (dbManager) => async (userId) => {
      await insertStay(dbManager, userId, Places.PARIS_HOTEL, {
        timestamp: '2025-09-21T06:00:00Z', // Paris 08:00
        durationSeconds: 5 * 3600,
      });
      await insertTrip(dbManager, userId, Places.PARIS_HOTEL, Places.LA_HOTEL, {
        timestamp: '2025-09-21T11:00:00Z', // Paris 13:00 -> Los Angeles 15:30
        durationSeconds: 690 * 60,
        distanceMeters: 9100000,
        movementType: 'FLIGHT',
      });
      await insertStay(dbManager, userId, Places.LA_HOTEL, {
        timestamp: '2025-09-21T22:30:00Z', // Los Angeles Sep 21 15:30 (Paris Sep 22 00:30) -> Sep 22 08:00
        durationSeconds: 990 * 60,
      });
      await insertStay(dbManager, userId, Places.LA_PIER, {
        timestamp: '2025-09-22T23:00:00Z', // Los Angeles Sep 22 16:00 (Paris Sep 23 01:00)
        durationSeconds: 2 * 3600,
      });
    };

    /** Los Angeles 09:00-16:00, fly at 17:00 and land in Paris at 12:30 the next day, Left Bank Cafe after midnight. */
    const seedUsToEurope = (dbManager) => async (userId) => {
      await insertStay(dbManager, userId, Places.LA_HOTEL, {
        timestamp: '2025-09-21T16:00:00Z', // Los Angeles 09:00
        durationSeconds: 7 * 3600,
      });
      await insertTrip(dbManager, userId, Places.LA_HOTEL, Places.PARIS_HOTEL, {
        timestamp: '2025-09-22T00:00:00Z', // Los Angeles Sep 21 17:00 -> Paris Sep 22 12:30
        durationSeconds: 630 * 60,
        distanceMeters: 9100000,
        movementType: 'FLIGHT',
      });
      await insertStay(dbManager, userId, Places.PARIS_HOTEL, {
        timestamp: '2025-09-22T10:30:00Z', // Paris 12:30 -> 22:30
        durationSeconds: 10 * 3600,
      });
      await insertStay(dbManager, userId, Places.PARIS_CAFE, {
        timestamp: '2025-09-22T22:30:00Z', // Paris Sep 23 00:30 (Los Angeles Sep 22 15:30)
        durationSeconds: 90 * 60,
      });
    };

    test.describe('Europe to the US (westbound)', () => {
      test('the travel day lasts from midnight in Paris until midnight in California', async ({ page, isolatedUsers, dbManager }) => {
        const requests = trackRangeRequests(page);
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'Europe/Paris',
          timeDisplayMode: 'location',
          seed: seedEuropeToUs(dbManager),
        });

        await page.goto(day('2025-09-21'));

        await expect(stayCard(page, 'Hotel Louvre').locator('.timeline-timestamp')).toContainText('09/21/2025 08:00 GMT+2');
        await expect(tripCards(page)).toHaveCount(1);
        await expect(tripCards(page).locator('.timeline-timestamp')).toContainText('09/21/2025 13:00 GMT+2');
        await expect(overnightTrips(page)).toHaveCount(0);

        // The evening in California belongs to Sep 21 although it is already Sep 22 in Paris
        const laHotel = overnightStay(page, 'Downtown LA Hotel');
        await expect(laHotel.locator('.timeline-timestamp')).toContainText('09/21/2025, 15:30 PDT');
        await expect(laHotel).toContainText('On this day: 15:30 - 23:59');
        await expect(cardsNamed(page, 'Santa Monica Pier')).toHaveCount(0);

        // The timeline is fetched 26 h wider than the Paris day; everything else follows the 33-hour local day
        await expectRangeRequested(requests.timeline, '2025-09-19T20:00:00.000Z', '2025-09-22T23:59:59.999Z');
        await expectRangeRequested(requests.path, '2025-09-20T22:00:00.000Z', '2025-09-22T06:59:59.999Z');
        await expectRangeRequested(requests.notes, '2025-09-20T22:00:00.000Z', '2025-09-22T06:59:59.999Z');
      });

      test('the next day starts at midnight in California', async ({ page, isolatedUsers, dbManager }) => {
        const requests = trackRangeRequests(page);
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'Europe/Paris',
          timeDisplayMode: 'location',
          seed: seedEuropeToUs(dbManager),
        });

        await page.goto(day('2025-09-22'));

        const laHotel = overnightStay(page, 'Downtown LA Hotel');
        await expect(laHotel.locator('.timeline-timestamp')).toContainText('Continued from Sep 21, 15:30 PDT');
        await expect(laHotel).toContainText('On this day: 00:00 - 08:00');

        // 16:00 in Los Angeles is Sep 23 in Paris, but still Sep 22 locally
        await expect(stayCard(page, 'Santa Monica Pier').locator('.timeline-timestamp')).toContainText('09/22/2025 16:00 PDT');
        await expect(cardsNamed(page, 'Hotel Louvre')).toHaveCount(0);
        await expect(page.locator('.timeline-card--trip, .timeline-card--overnight-trip')).toHaveCount(0);

        await expectRangeRequested(requests.path, '2025-09-22T07:00:00.000Z', '2025-09-23T06:59:59.999Z');
      });

      test('a range of days groups every item under its local date', async ({ page, isolatedUsers, dbManager }) => {
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'Europe/Paris',
          timeDisplayMode: 'location',
          seed: seedEuropeToUs(dbManager),
        });

        await page.goto(days('2025-09-21', '2025-09-22'));

        await expect(dateGroups(page)).toHaveCount(2);

        const sep21 = dateGroup(page, SEP_21_LABEL);
        await expect(cardsNamed(sep21, 'Hotel Louvre')).toHaveCount(1);
        await expect(sep21.locator('.timeline-card--trip')).toHaveCount(1);
        await expect(startDayCard(overnightStay(sep21, 'Downtown LA Hotel'))).toHaveCount(1);
        await expect(cardsNamed(sep21, 'Santa Monica Pier')).toHaveCount(0);

        const sep22 = dateGroup(page, SEP_22_LABEL);
        await expect(continuationCard(overnightStay(sep22, 'Downtown LA Hotel'))).toHaveCount(1);
        await expect(cardsNamed(sep22, 'Santa Monica Pier')).toHaveCount(1);
        await expect(cardsNamed(sep22, 'Hotel Louvre')).toHaveCount(0);
      });

      test('without data during the flight the data gap stays on the travel day', async ({ page, isolatedUsers, dbManager }) => {
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'Europe/Paris',
          timeDisplayMode: 'location',
          seed: async (userId) => {
            await insertStay(dbManager, userId, Places.PARIS_HOTEL, {
              timestamp: '2025-09-21T06:00:00Z', // Paris 08:00 -> 13:00
              durationSeconds: 5 * 3600,
            });
            await insertDataGap(dbManager, userId, {
              startTime: '2025-09-21T11:00:00Z', // Paris 13:00
              endTime: '2025-09-21T19:00:00Z', // New York 15:00
            });
            await insertStay(dbManager, userId, Places.NEW_YORK_OFFICE, {
              timestamp: '2025-09-21T19:00:00Z', // New York 15:00 -> 23:00 (Paris Sep 22 05:00)
              durationSeconds: 8 * 3600,
            });
          },
        });

        await page.goto(days('2025-09-21', '2025-09-22'));

        await expect(stayCard(page, 'Midtown Office').locator('.timeline-timestamp')).toContainText('09/21/2025 15:00 EDT');
        const gap = dataGapCards(page);
        await expect(gap).toHaveCount(1);
        await expect(gap.locator('.timeline-timestamp')).toContainText('09/21/2025 13:00 GMT+2');
        await expect(gap.locator('.detail-value').first()).toHaveText('09/21/2025 15:00 EDT');

        await expect(overnightGaps(page)).toHaveCount(0);
        await expect(page.locator('.timeline-card--overnight-stay')).toHaveCount(0);
        await expect(dateGroups(page)).toHaveCount(1);
        await expect(dateGroup(page, SEP_21_LABEL)).toHaveCount(1);
      });

      test('profile mode keeps Paris days and requests exactly the selected day', async ({ page, isolatedUsers, dbManager }) => {
        const requests = trackRangeRequests(page);
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'Europe/Paris',
          seed: seedEuropeToUs(dbManager),
        });

        await page.goto(day('2025-09-21'));

        await expect(stayCard(page, 'Hotel Louvre').locator('.timeline-timestamp')).toContainText('09/21/2025 08:00');
        // The flight lands at 00:30 on Sep 22 Paris time, so it crosses midnight (in local time mode it does not)
        await expect(tripCards(page)).toHaveCount(0);
        const flight = overnightTrips(page);
        await expect(flight.locator('.timeline-timestamp')).toContainText('09/21/2025, 13:00');
        await expect(flight).toContainText('On this day: 13:00 - 23:59');
        // The California evening starts at 00:30 on Sep 22 in Paris
        await expect(cardsNamed(page, 'Downtown LA Hotel')).toHaveCount(0);
        for (const timestamp of await allTimestamps(page).all()) {
          await expect(timestamp).not.toContainText(/GMT|PDT/);
        }

        await expectRangeRequested(requests.timeline, '2025-09-20T22:00:00.000Z', '2025-09-21T21:59:59.999Z');
        await expectRangeRequested(requests.path, '2025-09-20T22:00:00.000Z', '2025-09-21T21:59:59.999Z');
        for (const url of requests.timeline) {
          expect(url.searchParams.has('includeLocationTimezones')).toBe(false);
        }
      });
    });

    test.describe('The US to Europe (eastbound)', () => {
      test('an overnight flight is split at midnight on the departure clock', async ({ page, isolatedUsers, dbManager }) => {
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'America/Los_Angeles',
          timeDisplayMode: 'location',
          seed: seedUsToEurope(dbManager),
        });

        await page.goto(days('2025-09-21', '2025-09-22'));

        await expect(overnightTrips(page)).toHaveCount(2);
        await expect(dateGroups(page)).toHaveCount(2);

        const departure = overnightTrips(dateGroup(page, SEP_21_LABEL));
        await expect(departure.locator('.timeline-timestamp')).toContainText('09/21/2025, 17:00 PDT');
        await expect(departure).toContainText('On this day: 17:00 - 23:59');

        // Sep 22 begins at midnight in Los Angeles (09:00 in Paris), while still in the air
        const sep22 = dateGroup(page, SEP_22_LABEL);
        const arrival = overnightTrips(sep22);
        await expect(arrival.locator('.timeline-timestamp')).toContainText('Continued from Sep 21, 17:00 PDT');
        await expect(arrival).toContainText('On this day: 00:00 - 12:30 (3h 30m)');
        await expect(stayCard(sep22, 'Hotel Louvre').locator('.timeline-timestamp')).toContainText('09/22/2025 12:30 GMT+2');

        // After midnight in Paris it is Sep 23 there, though still Sep 22 in Los Angeles
        await expect(cardsNamed(page, 'Left Bank Cafe')).toHaveCount(0);
      });

      test('the arrival day starts mid-flight and ends at midnight in Paris', async ({ page, isolatedUsers, dbManager }) => {
        const requests = trackRangeRequests(page);
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'America/Los_Angeles',
          timeDisplayMode: 'location',
          seed: seedUsToEurope(dbManager),
        });

        await page.goto(day('2025-09-22'));

        await expect(stayCard(page, 'Hotel Louvre')).toHaveCount(1);
        await expect(cardsNamed(page, 'Left Bank Cafe')).toHaveCount(0);
        await expect(cardsNamed(page, 'Downtown LA Hotel')).toHaveCount(0);

        // A 15-hour day: 00:00 in Los Angeles to 24:00 in Paris
        await expectRangeRequested(requests.timeline, '2025-09-21T05:00:00.000Z', '2025-09-24T08:59:59.999Z');
        await expectRangeRequested(requests.path, '2025-09-22T07:00:00.000Z', '2025-09-22T21:59:59.999Z');
        await expectRangeRequested(requests.notes, '2025-09-22T07:00:00.000Z', '2025-09-22T21:59:59.999Z');

        await page.goto(day('2025-09-23'));

        await expect(stayCard(page, 'Left Bank Cafe').locator('.timeline-timestamp')).toContainText('09/23/2025 00:30 GMT+2');
        await expect(cardsNamed(page, 'Hotel Louvre')).toHaveCount(0);
      });

      test('profile mode keeps Los Angeles days', async ({ page, isolatedUsers, dbManager }) => {
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'America/Los_Angeles',
          seed: seedUsToEurope(dbManager),
        });

        await page.goto(day('2025-09-22'));

        const arrival = overnightTrips(page);
        await expect(arrival.locator('.timeline-timestamp')).toContainText('Continued from Sep 21, 17:00');
        await expect(arrival).toContainText('On this day: 00:00 - 03:30');
        await expect(stayCard(page, 'Left Bank Cafe').locator('.timeline-timestamp')).toContainText('09/22/2025 15:30');
      });

      test('without data during the flight the data gap is split at midnight in New York', async ({ page, isolatedUsers, dbManager }) => {
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'America/New_York',
          timeDisplayMode: 'location',
          seed: async (userId) => {
            await insertStay(dbManager, userId, Places.NEW_YORK_OFFICE, {
              timestamp: '2025-09-21T14:00:00Z', // New York 10:00 -> 18:00
              durationSeconds: 8 * 3600,
            });
            await insertDataGap(dbManager, userId, {
              startTime: '2025-09-21T22:00:00Z', // New York 18:00
              endTime: '2025-09-22T08:00:00Z', // Paris Sep 22 10:00
            });
            await insertStay(dbManager, userId, Places.PARIS_HOTEL, {
              timestamp: '2025-09-22T08:00:00Z', // Paris 10:00 -> 14:00
              durationSeconds: 4 * 3600,
            });
          },
        });

        await page.goto(days('2025-09-21', '2025-09-22'));

        await expect(overnightGaps(page)).toHaveCount(2);

        const departure = overnightGaps(dateGroup(page, SEP_21_LABEL));
        await expect(departure.locator('.timeline-timestamp')).toContainText('09/21/2025, 18:00 EDT');
        await expect(departure).toContainText('On this day: 18:00 - 23:59');

        // Midnight in New York is 06:00 in Paris; the gap ends at 10:00 Paris time, 4 hours later
        const sep22 = dateGroup(page, SEP_22_LABEL);
        await expect(overnightGaps(sep22)).toContainText('On this day: 00:00 - 10:00 (4h)');
        await expect(stayCard(sep22, 'Hotel Louvre').locator('.timeline-timestamp')).toContainText('09/22/2025 10:00 GMT+2');
      });
    });

    test.describe('Across the date line', () => {
      test('Tokyo to Honolulu: landing on an earlier calendar date keeps the trip on the departure day', async ({ page, isolatedUsers, dbManager }) => {
        const requests = trackRangeRequests(page);
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'Asia/Tokyo',
          timeDisplayMode: 'location',
          seed: async (userId) => {
            await insertStay(dbManager, userId, Places.TOKYO_HOTEL, {
              timestamp: '2025-09-21T00:00:00Z', // Tokyo Sep 21 09:00
              durationSeconds: 3600,
            });
            await insertTrip(dbManager, userId, Places.TOKYO_HOTEL, Places.HONOLULU_HOTEL, {
              timestamp: '2025-09-21T01:00:00Z', // Tokyo Sep 21 10:00 -> Honolulu Sep 20 22:00
              durationSeconds: 7 * 3600,
              distanceMeters: 6200000,
              movementType: 'FLIGHT',
            });
            await insertStay(dbManager, userId, Places.HONOLULU_HOTEL, {
              timestamp: '2025-09-21T08:00:00Z', // Honolulu Sep 20 22:00 -> Sep 21 08:00
              durationSeconds: 10 * 3600,
            });
            await insertStay(dbManager, userId, Places.HONOLULU_LOOKOUT, {
              timestamp: '2025-09-21T19:00:00Z', // Honolulu Sep 21 09:00 (Tokyo Sep 22 04:00)
              durationSeconds: 3 * 3600,
            });
          },
        });

        await page.goto(days('2025-09-20', '2025-09-22'));

        // Dates never go backwards: everything after take-off stays on Sep 21
        await expect(dateGroups(page)).toHaveCount(1);
        const sep21 = dateGroup(page, SEP_21_LABEL);
        await expect(sep21.locator('.timeline-card--stay')).toHaveCount(3);
        await expect(sep21.locator('.timeline-card--trip')).toHaveCount(1);
        await expect(page.locator('.timeline-card--overnight-stay, .timeline-card--overnight-trip')).toHaveCount(0);

        await expect(stayCard(sep21, 'Shinjuku Hotel').locator('.timeline-timestamp')).toContainText('09/21/2025 09:00 GMT+9');
        await expect(stayCard(sep21, 'Waikiki Hotel').locator('.timeline-timestamp')).toContainText('09/20/2025 22:00 HST');
        await expect(stayCard(sep21, 'Diamond Head Lookout').locator('.timeline-timestamp')).toContainText('09/21/2025 09:00 HST');

        await page.goto(day('2025-09-21'));

        // From midnight in Tokyo to midnight in Honolulu: a 43-hour day
        await expect(stayCard(page, 'Diamond Head Lookout')).toHaveCount(1);
        await expectRangeRequested(requests.path, '2025-09-20T15:00:00.000Z', '2025-09-22T09:59:59.999Z');
      });

      test('Honolulu to Tokyo: a flight that skips a date without passing midnight stays on its departure day', async ({ page, isolatedUsers, dbManager }) => {
        const requests = trackRangeRequests(page);
        await createUserAndLogin(page, dbManager, isolatedUsers, {
          timezone: 'Pacific/Honolulu',
          timeDisplayMode: 'location',
          seed: async (userId) => {
            await insertStay(dbManager, userId, Places.HONOLULU_HOTEL, {
              timestamp: '2025-09-20T20:00:00Z', // Honolulu Sep 20 10:00 -> 14:00
              durationSeconds: 4 * 3600,
            });
            // Take-off at 14:00 HST is already Sep 21 09:00 in Tokyo; landing at 17:00 JST is Sep 20 22:00 in Honolulu
            await insertTrip(dbManager, userId, Places.HONOLULU_HOTEL, Places.TOKYO_HOTEL, {
              timestamp: '2025-09-21T00:00:00Z',
              durationSeconds: 8 * 3600,
              distanceMeters: 6200000,
              movementType: 'FLIGHT',
            });
            await insertStay(dbManager, userId, Places.TOKYO_HOTEL, {
              timestamp: '2025-09-21T08:00:00Z', // Tokyo Sep 21 17:00
              durationSeconds: 3 * 3600,
            });
          },
        });

        await page.goto(days('2025-09-20', '2025-09-21'));

        await expect(overnightTrips(page)).toHaveCount(0);
        const sep20 = dateGroup(page, 'September 20, 2025');
        await expect(stayCard(sep20, 'Waikiki Hotel').locator('.timeline-timestamp')).toContainText('09/20/2025 10:00 HST');
        await expect(sep20.locator('.timeline-card--trip .timeline-timestamp')).toContainText('09/20/2025 14:00 HST');

        const sep21 = dateGroup(page, SEP_21_LABEL);
        await expect(sep21.locator('.timeline-card')).toHaveCount(1);
        await expect(stayCard(sep21, 'Shinjuku Hotel').locator('.timeline-timestamp')).toContainText('09/21/2025 17:00 GMT+9');

        // Sep 21 starts at landing, not at the earlier midnight in Tokyo, so the flight is not on its map
        await page.goto(day('2025-09-21'));
        await expect(stayCard(page, 'Shinjuku Hotel')).toHaveCount(1);
        await expectRangeRequested(requests.path, '2025-09-21T08:00:00.000Z', '2025-09-21T14:59:59.999Z');
      });
    });
  });
});
