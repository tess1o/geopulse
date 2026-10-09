import { randomUUID } from 'crypto';
import { test, expect } from '../fixtures/isolated-fixture.js';
import { TimelinePage } from '../pages/TimelinePage.js';
import { UserProfilePage } from '../pages/UserProfilePage.js';
import { PlaceDetailsPage } from '../pages/PlaceDetailsPage.js';
import { SharedTimelinePage } from '../pages/SharedTimelinePage.js';
import { TimeDigestPage } from '../pages/TimeDigestPage.js';
import { TripWorkspacePage } from '../pages/TripWorkspacePage.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { MapEngineHarness } from '../utils/map-engine-harness.js';
import { ShareLinkFactory } from '../utils/share-link-factory.js';
import { DateFactory } from '../utils/date-factory.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';
import {
  ImmichFactory,
  IMMICH_E2E_SERVER_URL,
  IMMICH_E2E_UNREACHABLE_URL,
  KYIV_COFFEE_PLACE,
  PHOTO_DAYS
} from '../utils/immich-factory.js';

// These tests need the fake Immich server: the geopulse-demo-integrations-e2e service in
// tests/docker-compose.e2e.yml. See utils/immich-factory.js for how it places photos.

// Noon UTC, so TimelinePage.navigateWithDateRange (local date parts) lands on the same days everywhere.
const TIMELINE_RANGE = {
  startDate: new Date(`${PHOTO_DAYS.first}T12:00:00Z`),
  endDate: new Date(`${PHOTO_DAYS.last}T12:00:00Z`)
};

// ImmichLatestPhotosSection shows this many photos before "Load more".
const LATEST_PHOTOS_PAGE_SIZE = 20;

const TIMELINE_MAP = '.map-view-container';
// Raster photo markers (DOM) and the vector photo source/layer ids (MapLibre).
const PHOTO_MARKER = '.gp-photo-map-marker';
const VECTOR_PHOTO_LAYER_TOKEN = 'gp-immich-photos';

const createPhotoUser = async (page, dbManager, isolatedUsers, seed = (userId) => ImmichFactory.insertPhotoStays(dbManager, userId)) => {
  const testUser = createManagedUser(isolatedUsers);
  const { user } = await TestSetupHelper.createAndLoginUser(page, dbManager, testUser);

  // Stays first: the fake server builds the library on the first request it gets for the user.
  const seeded = await seed(user.id);
  await ImmichFactory.configure(dbManager, user.id);
  const photos = await ImmichFactory.expectSeededPhotos(page);

  return { user, testUser, seeded, photos };
};

const openTimelineRange = async (page) => {
  const timelinePage = new TimelinePage(page);
  await timelinePage.navigateWithDateRange(TIMELINE_RANGE.startDate, TIMELINE_RANGE.endDate);
  await timelinePage.waitForPageLoad();
  await timelinePage.waitForTimelineContent();
  await expect(page.locator('.timeline-card--stay').first()).toBeVisible({ timeout: 15000 });
  await new MapEngineHarness(page).waitForMapReady({ rootSelector: TIMELINE_MAP, settleMs: 500 });
  return timelinePage;
};

const photosControl = (page, rootSelector = TIMELINE_MAP) => (
  page.locator(`${rootSelector} .control-button[title="Show Photos"], ${rootSelector} .control-button[title="Hide Photos"]`).first()
);

const showPhotosOnMap = async (page, rootSelector = TIMELINE_MAP) => {
  const button = page.locator(`${rootSelector} .control-button[title="Show Photos"]`);
  await expect(button).toBeEnabled({ timeout: 15000 });
  await button.click();
  await expect(page.locator(`${rootSelector} .control-button[title="Hide Photos"]`)).toHaveClass(/active/, { timeout: 10000 });
};

const hidePhotosOnMap = async (page, rootSelector = TIMELINE_MAP) => {
  const button = page.locator(`${rootSelector} .control-button[title="Hide Photos"]`);
  await expect(button).toBeEnabled({ timeout: 15000 });
  await button.click();
  await expect(page.locator(`${rootSelector} .control-button[title="Show Photos"]`)).not.toHaveClass(/active/, { timeout: 10000 });
};

const isVectorMap = async (page, rootSelector = '') => (
  (await page.locator(`${rootSelector} [data-testid="map-host-vector"]`.trim()).count()) > 0
);

// Raster photo markers are DOM markers. Vector photos are a MapLibre GeoJSON source with one feature
// per photo group; a group that overlaps a stay is drawn inside a combined marker but stays in the source.
const countPhotoMarkers = async (page, rootSelector = '') => {
  if (await isVectorMap(page, rootSelector)) {
    const { count } = await new MapEngineHarness(page).countVectorSourceFeatures({
      sourceIncludes: [VECTOR_PHOTO_LAYER_TOKEN],
      rootSelector: rootSelector || null
    });
    return count;
  }
  return page.locator(`${rootSelector} ${PHOTO_MARKER}`.trim()).count();
};

const expectPhotoMarkers = async (page, rootSelector = '') => {
  await expect.poll(() => countPhotoMarkers(page, rootSelector), { timeout: 20000 }).toBeGreaterThan(0);
};

const expectNoPhotoMarkers = async (page, rootSelector = '') => {
  await expect.poll(() => countPhotoMarkers(page, rootSelector), { timeout: 10000 }).toBe(0);
};

// Photo markers sit in a marker cluster on top of the stay marker and path at the same spot, so a
// pointer click lands on a cluster (which only zooms) or on whatever overlaps it. Fire Leaflet's
// click on a photo marker instead: the same handler a real click runs, which opens the viewer.
const openPhotoViewerFromRasterMap = async (page, rootSelector) => {
  await expect.poll(() => page.evaluate((root) => {
    const host = document.querySelector(`${root} [data-testid="map-host-raster"]`);
    const map = host ? window.__GP_E2E_MAPS?.[host.id] : null;
    if (!map) {
      return 'map not registered';
    }

    // Photo markers carry options.photoCount; they live in a cluster group, or on the map without one.
    const isPhotoMarker = (layer) => Number(layer?.options?.photoCount) > 0;
    let photoMarker = null;
    map.eachLayer((layer) => {
      if (photoMarker) {
        return;
      }
      if (isPhotoMarker(layer)) {
        photoMarker = layer;
      } else if (typeof layer.zoomToShowLayer === 'function') {
        photoMarker = layer.getLayers().find(isPhotoMarker) || null;
      }
    });
    if (!photoMarker) {
      return 'no photo marker';
    }

    photoMarker.fire('click');
    return 'clicked';
  }, rootSelector), { timeout: 20000 }).toBe('clicked');
};

// Vector photos are native MapLibre layers, so click where one is drawn. A photo group that overlaps
// a stay is drawn inside a combined marker instead, whose popup lists the photos. Clusters only zoom
// in, so keep going until the viewer opens.
const openPhotoViewerFromVectorMap = async (page, rootSelector, viewer) => {
  const harness = new MapEngineHarness(page);

  await expect.poll(async () => {
    if (await viewer.isVisible().catch(() => false)) {
      return true;
    }

    const photoRow = page.locator('.gp-cross-type-popup .timeline-stack-select.stack-item--photo').first();
    const comboWithPhotos = page.locator(`${rootSelector} .gp-cross-type-marker`)
      .filter({ has: page.locator('.pi-camera') })
      .first();

    if (await photoRow.isVisible().catch(() => false)) {
      await photoRow.click();
    } else if (await comboWithPhotos.count()) {
      // Other DOM markers can overlap the combined marker; its own click listener opens the popup.
      await comboWithPhotos.dispatchEvent('click');
    } else {
      await harness.clickVectorRenderedFeature({ layerIncludes: [VECTOR_PHOTO_LAYER_TOKEN], rootSelector });
    }

    await page.waitForTimeout(800);
    return viewer.isVisible().catch(() => false);
  }, { timeout: 30000 }).toBe(true);
};

const openPhotoViewerFromMap = async (page, rootSelector) => {
  const viewer = page.locator('.photo-viewer-dialog:visible').last();

  if (await isVectorMap(page, rootSelector)) {
    await openPhotoViewerFromVectorMap(page, rootSelector, viewer);
  } else {
    await openPhotoViewerFromRasterMap(page, rootSelector);
  }

  return viewer;
};

// The viewer loads the preview through the GeoPulse proxy into a blob URL; naturalWidth proves it decoded.
const expectViewerShowsPhoto = async (viewer, photos) => {
  await expect(viewer).toBeVisible({ timeout: 10000 });
  const image = viewer.locator('img.main-photo');
  await expect(image).toBeVisible({ timeout: 15000 });
  await expect.poll(
    () => image.evaluate((img) => (img.complete ? img.naturalWidth : 0)),
    { timeout: 15000 }
  ).toBeGreaterThan(0);

  const fileNames = photos.map((photo) => photo.originalFileName);
  expect(fileNames).toContain(await image.getAttribute('alt'));
};

const closePhotoViewer = async (page) => {
  const viewer = page.locator('.photo-viewer-dialog:visible').last();
  await viewer.getByRole('button', { name: 'Close photo viewer' }).click();
  await expect(page.locator('.photo-viewer-dialog:visible')).toHaveCount(0, { timeout: 10000 });
};

// Card triggers say "Open photo" or "Open N photos"; add them up.
const countPhotosOnCards = (page, cardSelector = '.timeline-card') => (
  page.locator(`${cardSelector} .photo-trigger`).evaluateAll((triggers) => triggers.reduce((sum, trigger) => {
    const label = trigger.getAttribute('aria-label') || '';
    const match = label.match(/Open (\d+) photos/);
    return sum + (match ? Number(match[1]) : 1);
  }, 0))
);

const latestPhotosCard = (page, title) => (
  page.locator('.immich-photos-card').filter({ has: page.locator('.section-title', { hasText: title }) }).first()
);

const expectLatestPhotoTiles = async (card, expectedCount = null) => {
  await expect(card).toBeVisible({ timeout: 20000 });
  const tiles = card.locator('.immich-photo-tile');
  if (expectedCount === null) {
    await expect.poll(() => tiles.count(), { timeout: 20000 }).toBeGreaterThan(0);
  } else {
    await expect(tiles).toHaveCount(expectedCount, { timeout: 20000 });
  }
  return tiles;
};

test.describe('Immich integration', () => {
  test.describe('Connected Apps settings', () => {
    test.skip(({ mapMode }) => mapMode === 'VECTOR', 'No map involved; covered by the raster project.');

    test('tests the connection, rejects a wrong API key and saves working settings', async ({ page, isolatedUsers, dbManager }) => {
      const testUser = createManagedUser(isolatedUsers);
      const { user } = await TestSetupHelper.createAndLoginUser(page, dbManager, testUser);
      await ImmichFactory.insertPhotoStays(dbManager, user.id);

      // The fake server only knows keys stored in users.immich_preferences, so register the key
      // without a server URL and with the integration off: the UI still starts from scratch.
      const apiKey = ImmichFactory.apiKeyFor(user.id);
      await ImmichFactory.configure(dbManager, user.id, { serverUrl: null, enabled: false });

      const profilePage = new UserProfilePage(page);
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      await profilePage.switchToImmichTab();
      expect(await profilePage.isImmichIntegrationEnabled()).toBe(false);

      await profilePage.toggleImmichIntegration();
      await profilePage.fillImmichForm(IMMICH_E2E_SERVER_URL, 'not-a-known-immich-key');
      await profilePage.testImmichConnection();

      const message = profilePage.getImmichConnectionMessage();
      await expect(message).toHaveClass(/p-message-error/, { timeout: 15000 });
      await expect(message).toContainText('Immich rejected the API key');

      await profilePage.fillImmichForm(null, apiKey);
      await profilePage.testImmichConnection();
      await expect(message).toHaveClass(/p-message-success/, { timeout: 15000 });
      await expect(message).toContainText('Successfully connected to Immich server.');
      const connectionText = await message.textContent();

      await profilePage.saveImmichSettings();
      await profilePage.waitForSuccessToast();

      await expect.poll(() => ImmichFactory.getPreferences(dbManager, user.id), { timeout: 10000 }).toEqual({
        serverUrl: IMMICH_E2E_SERVER_URL,
        apiKey,
        enabled: true
      });

      // The connection test counts the whole library, which is exactly the seeded photos.
      const photos = await ImmichFactory.expectSeededPhotos(page);
      expect(connectionText).toContain(`${photos.length} ${photos.length === 1 ? 'asset' : 'assets'} available`);

      // The saved settings are the ones the timeline uses.
      await openTimelineRange(page);
      await expect.poll(() => countPhotosOnCards(page), { timeout: 20000 }).toBe(photos.length);
    });
  });

  test.describe('Photo proxy', () => {
    test.skip(({ mapMode }) => mapMode === 'VECTOR', 'No map involved; covered by the raster project.');

    test('serves thumbnails, previews and downloads through GeoPulse', async ({ page, isolatedUsers, dbManager }) => {
      const { photos } = await createPhotoUser(page, dbManager, isolatedUsers);
      const [photo] = photos;

      expect(photo.thumbnailUrl).toBe(`/api/v1/integrations/immich/photos/${photo.id}/thumbnail`);
      expect(photo.latitude).toBeCloseTo(KYIV_COFFEE_PLACE.latitude, 3);
      expect(photo.longitude).toBeCloseTo(KYIV_COFFEE_PLACE.longitude, 3);

      const sizes = {};
      for (const variant of ['thumbnail', 'preview', 'download']) {
        const response = await ImmichFactory.fetchPhotoBytes(page, photo.id, variant);
        expect(response.status(), `${variant} status`).toBe(200);
        expect(response.headers()['content-type']).toContain('image/jpeg');
        const body = await response.body();
        expect([body[0], body[1]], `${variant} is a JPEG`).toEqual([0xff, 0xd8]);
        sizes[variant] = body.length;
      }

      // The fake server keeps a 480 px thumbnail and a 1280 px preview of every photo.
      expect(sizes.preview).toBeGreaterThan(sizes.thumbnail);

      const missing = await ImmichFactory.fetchPhotoBytes(page, randomUUID(), 'thumbnail');
      expect(missing.ok()).toBe(false);
    });
  });

  test.describe('Timeline', () => {
    test('shows photo markers on the map and opens one in the photo viewer', async ({ page, isolatedUsers, dbManager }) => {
      const { photos } = await createPhotoUser(page, dbManager, isolatedUsers);
      await openTimelineRange(page);

      // Photos are off on the timeline map until the control is used.
      await expect(photosControl(page)).toBeVisible({ timeout: 15000 });
      await expectNoPhotoMarkers(page, TIMELINE_MAP);

      await showPhotosOnMap(page);
      await expectPhotoMarkers(page, TIMELINE_MAP);

      const viewer = await openPhotoViewerFromMap(page, TIMELINE_MAP);
      await expectViewerShowsPhoto(viewer, photos);
      await closePhotoViewer(page);
    });

    test('the Photos control hides and shows the photo markers', async ({ page, isolatedUsers, dbManager }) => {
      await createPhotoUser(page, dbManager, isolatedUsers);
      await openTimelineRange(page);

      await showPhotosOnMap(page);
      await expectPhotoMarkers(page, TIMELINE_MAP);

      await hidePhotosOnMap(page);
      await expectNoPhotoMarkers(page, TIMELINE_MAP);

      await showPhotosOnMap(page);
      await expectPhotoMarkers(page, TIMELINE_MAP);
    });

    test('shows every photo on the stay card it was taken during and opens the gallery', async ({ page, isolatedUsers, dbManager }) => {
      const { photos } = await createPhotoUser(page, dbManager, isolatedUsers);
      await openTimelineRange(page);

      await expect(page.locator('.timeline-card--stay')).toHaveCount(PHOTO_DAYS.count, { timeout: 15000 });
      await expect.poll(() => countPhotosOnCards(page, '.timeline-card--stay'), { timeout: 20000 }).toBe(photos.length);

      const cardWithPhotos = page.locator('.timeline-card--stay').filter({ has: page.locator('.photo-trigger') }).first();
      await cardWithPhotos.locator('.photo-trigger').click();

      const viewer = page.locator('.photo-viewer-dialog:visible').last();
      await expectViewerShowsPhoto(viewer, photos);
      await closePhotoViewer(page);
    });

    test('still loads when Immich is unreachable and reports the photo error', async ({ page, isolatedUsers, dbManager }) => {
      const testUser = createManagedUser(isolatedUsers);
      const { user } = await TestSetupHelper.createAndLoginUser(page, dbManager, testUser);
      await ImmichFactory.insertPhotoStays(dbManager, user.id);
      await ImmichFactory.configure(dbManager, user.id, { serverUrl: IMMICH_E2E_UNREACHABLE_URL });

      const search = await ImmichFactory.requestPhotoSearch(page, {
        from: `${PHOTO_DAYS.first}T00:00:00Z`,
        to: `${PHOTO_DAYS.last}T23:59:59Z`
      });
      expect(search.ok()).toBe(false);

      await openTimelineRange(page);
      await expect(page.locator('.timeline-card--stay')).toHaveCount(PHOTO_DAYS.count, { timeout: 15000 });
      await expect(page.locator('.timeline-card .photo-trigger')).toHaveCount(0);

      // Immich is configured, so the control is offered; turning it on reports the failure.
      await page.locator(`${TIMELINE_MAP} .control-button[title="Show Photos"]`).click({ timeout: 15000 });
      await expect(
        page.locator('.p-toast-message-error').filter({ hasText: 'Failed to Load Photos' }).first()
      ).toBeVisible({ timeout: 15000 });
      await expectNoPhotoMarkers(page, TIMELINE_MAP);
    });
  });

  test.describe('Places and locations', () => {
    test('place details show the photos taken at a favorite and their map markers', async ({ page, isolatedUsers, dbManager }) => {
      let favoriteId = null;
      const { photos } = await createPhotoUser(page, dbManager, isolatedUsers, async (userId) => {
        favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, userId, KYIV_COFFEE_PLACE);
        return ImmichFactory.insertPhotoStays(dbManager, userId, { favoriteId });
      });

      const placeDetailsPage = new PlaceDetailsPage(page);
      await placeDetailsPage.navigateToFavorite(favoriteId);
      await placeDetailsPage.waitForPageLoad();

      // Every photo is within 30 m of the favorite and inside its visit window.
      const card = latestPhotosCard(page, `Latest photos near ${KYIV_COFFEE_PLACE.name}`);
      const tiles = await expectLatestPhotoTiles(card, Math.min(photos.length, LATEST_PHOTOS_PAGE_SIZE));
      await expect(card.locator('.immich-photos-count')).toContainText(String(Math.min(photos.length, LATEST_PHOTOS_PAGE_SIZE)));

      await expectPhotoMarkers(page);

      await tiles.first().click();
      await expectViewerShowsPhoto(page.locator('.photo-viewer-dialog:visible').last(), photos);
      await closePhotoViewer(page);
    });

    test('city details show the latest photos in the city and their map markers', async ({ page, isolatedUsers, dbManager }) => {
      const { photos } = await createPhotoUser(page, dbManager, isolatedUsers, async (userId) => {
        const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, userId, {
          coords: `POINT(${KYIV_COFFEE_PLACE.longitude} ${KYIV_COFFEE_PLACE.latitude})`,
          displayName: KYIV_COFFEE_PLACE.name,
          city: KYIV_COFFEE_PLACE.city,
          country: KYIV_COFFEE_PLACE.country
        });
        return ImmichFactory.insertPhotoStays(dbManager, userId, { geocodingId });
      });

      await page.goto(`/app/location-analytics/city/${encodeURIComponent(KYIV_COFFEE_PLACE.city)}`);
      await page.waitForLoadState('networkidle');

      const card = latestPhotosCard(page, `Latest photos in ${KYIV_COFFEE_PLACE.city}`);
      const tiles = await expectLatestPhotoTiles(card);
      expect(await tiles.count()).toBeLessThanOrEqual(photos.length);

      await expectPhotoMarkers(page);

      await tiles.first().click();
      await expectViewerShowsPhoto(page.locator('.photo-viewer-dialog:visible').last(), photos);
      await closePhotoViewer(page);
    });

    test('country details show the photo locations map and the latest photos', async ({ page, isolatedUsers, dbManager }) => {
      const { photos } = await createPhotoUser(page, dbManager, isolatedUsers, async (userId) => {
        const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, userId, {
          coords: `POINT(${KYIV_COFFEE_PLACE.longitude} ${KYIV_COFFEE_PLACE.latitude})`,
          displayName: KYIV_COFFEE_PLACE.name,
          city: KYIV_COFFEE_PLACE.city,
          country: KYIV_COFFEE_PLACE.country
        });
        return ImmichFactory.insertPhotoStays(dbManager, userId, { geocodingId });
      });

      await page.goto(`/app/location-analytics/country/${encodeURIComponent(KYIV_COFFEE_PLACE.country)}`);
      await page.waitForLoadState('networkidle');

      const mapCard = page.locator('.immich-photos-map-card');
      await expect(mapCard).toBeVisible({ timeout: 20000 });
      await expect(mapCard).toContainText(`Photo locations in ${KYIV_COFFEE_PLACE.country}`);
      await expectPhotoMarkers(page, '.immich-photos-map-card');

      const card = latestPhotosCard(page, `Latest photos in ${KYIV_COFFEE_PLACE.country}`);
      const tiles = await expectLatestPhotoTiles(card);
      expect(await tiles.count()).toBeLessThanOrEqual(photos.length);

      const viewer = await openPhotoViewerFromMap(page, '.immich-photos-map-card');
      await expectViewerShowsPhoto(viewer, photos);
      await closePhotoViewer(page);
    });
  });

  test.describe('Shared timeline', () => {
    const createPhotoShare = async (page, dbManager, isolatedUsers, context, { showPhotos }) => {
      const { user, photos } = await createPhotoUser(page, dbManager, isolatedUsers);
      const link = await ShareLinkFactory.createTimeline(dbManager, user.id, {
        id: randomUUID(),
        name: showPhotos ? 'Coffee week with photos' : 'Coffee week without photos',
        show_photos: showPhotos,
        dateRange: {
          startDate: new Date(`${PHOTO_DAYS.first}T00:00:00Z`),
          endDate: new Date(`${PHOTO_DAYS.last}T23:59:59Z`),
          expiresAt: DateFactory.futureDate(30)
        }
      });

      // Open it as a guest.
      await context.clearCookies();
      const sharedTimelinePage = new SharedTimelinePage(page);
      await sharedTimelinePage.navigateToSharedTimeline(link.id);
      await sharedTimelinePage.waitForPageLoad();
      await sharedTimelinePage.waitForLoadingToFinish();
      await expect(page.locator('.timeline-card--stay')).toHaveCount(PHOTO_DAYS.count, { timeout: 20000 });

      return { photos };
    };

    test('a guest sees the owner\'s photos on the map and the cards when photos are shared', async ({ page, isolatedUsers, dbManager, context }) => {
      const { photos } = await createPhotoShare(page, dbManager, isolatedUsers, context, { showPhotos: true });

      await expectPhotoMarkers(page);
      await expect.poll(() => countPhotosOnCards(page, '.timeline-card--stay'), { timeout: 20000 }).toBe(photos.length);

      const cardWithPhotos = page.locator('.timeline-card--stay').filter({ has: page.locator('.photo-trigger') }).first();
      await cardWithPhotos.locator('.photo-trigger').click();
      await expectViewerShowsPhoto(page.locator('.photo-viewer-dialog:visible').last(), photos);
      await closePhotoViewer(page);
    });

    test('a guest sees no photos when the share does not include them', async ({ page, isolatedUsers, dbManager, context }) => {
      await createPhotoShare(page, dbManager, isolatedUsers, context, { showPhotos: false });

      await page.waitForLoadState('networkidle');
      await expect(page.locator(`.control-button[title*="Photos"]`)).toHaveCount(0);
      await expectNoPhotoMarkers(page);
      await expect(page.locator('.timeline-card .photo-trigger')).toHaveCount(0);
    });
  });

  test.describe('Rewind', () => {
    test.skip(({ mapMode }) => mapMode === 'VECTOR', 'No map involved; covered by the raster project.');

    test('shows photo memories from the period', async ({ page, isolatedUsers, dbManager }) => {
      const { photos } = await createPhotoUser(page, dbManager, isolatedUsers);

      const digestPage = new TimeDigestPage(page);
      const [year, month] = PHOTO_DAYS.first.split('-').map(Number);
      await digestPage.navigate(year, month, 'monthly');
      await digestPage.waitForPageLoad();
      await digestPage.waitForLoadingComplete();
      expect(await digestPage.hasDigestContent()).toBe(true);

      const memories = page.locator('.immich-photos-card--rewind');
      await expect(memories).toBeVisible({ timeout: 20000 });
      await expect(memories).toContainText('Memories along the way');

      const tiles = memories.locator('.rewind-justified-tile');
      await expect.poll(() => tiles.count(), { timeout: 20000 }).toBeGreaterThan(0);
      expect(await tiles.count()).toBeLessThanOrEqual(photos.length);

      await tiles.first().click();
      await expectViewerShowsPhoto(page.locator('.photo-viewer-dialog:visible').last(), photos);
      await closePhotoViewer(page);
    });
  });

  test.describe('Trip workspace', () => {
    test('a completed trip shows its photos in the timeline lens and on the map', async ({ page, isolatedUsers, dbManager }) => {
      let tripId = null;
      const { photos } = await createPhotoUser(page, dbManager, isolatedUsers, async (userId) => {
        const stays = await ImmichFactory.insertPhotoStays(dbManager, userId);
        tripId = await TestSetupHelper.createTrip(dbManager, userId, {
          name: 'Kyiv coffee trip',
          startTime: new Date(`${PHOTO_DAYS.first}T00:00:00Z`),
          endTime: new Date(`${PHOTO_DAYS.last}T23:59:59Z`),
          status: 'COMPLETED'
        });
        return stays;
      });

      await page.goto(`/app/trips/${tripId}`);
      const tripWorkspacePage = new TripWorkspacePage(page);
      await tripWorkspacePage.waitForPageLoad();
      await expect(tripWorkspacePage.lensSwitch()).toBeVisible({ timeout: 15000 });
      await tripWorkspacePage.openLens('Timeline');

      const card = page.locator('.trip-actual-lens .immich-photos-card')
        .filter({ has: page.locator('.section-title', { hasText: 'Trip Photos' }) });
      const tiles = await expectLatestPhotoTiles(card, Math.min(photos.length, LATEST_PHOTOS_PAGE_SIZE));

      // The trip map shows photos by default.
      await expectPhotoMarkers(page);

      await tiles.first().click();
      await expectViewerShowsPhoto(page.locator('.photo-viewer-dialog:visible').last(), photos);
      await closePhotoViewer(page);
    });
  });
});
