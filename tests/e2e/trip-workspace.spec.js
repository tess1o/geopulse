import { test, expect } from '../fixtures/isolated-fixture.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';
import { TripWorkspacePage } from '../pages/TripWorkspacePage.js';

const days = (n) => n * 24 * 60 * 60 * 1000;

const createAndLoginRasterUser = (page, dbManager, userData) =>
  TestSetupHelper.createAndLoginUser(page, dbManager, userData, { mapMode: 'RASTER' });

const stubPlanSuggestion = async (page, title = 'Stubbed plan suggestion') => {
  await page.route('**/api/v1/trip-planning/suggestions*', async (route) => {
    const url = new URL(route.request().url());
    const lat = Number(url.searchParams.get('latitude') || url.searchParams.get('lat') || 51.5007);
    const lon = Number(url.searchParams.get('longitude') || url.searchParams.get('lon') || -0.1246);

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 'success',
        data: {
          title,
          latitude: lat,
          longitude: lon,
          sourceType: 'coordinates',
          favoriteId: null,
          favoriteType: null,
        },
      }),
    });
  });
};

test.describe('Trip Workspace Page', () => {
  test('UPCOMING trip: plan-only flow with map right-click add/edit/delete and planning UI', async ({ page, isolatedUsers, dbManager }) => {
    const testUser = createManagedUser(isolatedUsers);
    const { user } = await createAndLoginRasterUser(page, dbManager, testUser);

    const now = Date.now();
    const tripId = await TestSetupHelper.createTrip(dbManager, user.id, {
      name: 'Future London',
      startTime: new Date(now + days(4)),
      endTime: new Date(now + days(7)),
      status: 'UPCOMING',
      color: '#2563EB'
    });

    await stubPlanSuggestion(page, 'London suggestion');

    await page.goto(`/app/trips/${tripId}`);
    const tripWorkspacePage = new TripWorkspacePage(page);
    await tripWorkspacePage.waitForPageLoad();

    // Same layout in every trip state: summary bar + map + stops rail, no tabs.
    expect(await tripWorkspacePage.isSummaryBarVisible()).toBe(true);
    expect(await tripWorkspacePage.getStopsHeading()).toBe('Stops (0)');
    // A future trip has no timeline/path data, so there is nothing to switch to.
    expect(await tripWorkspacePage.isLensSwitchVisible()).toBe(false);

    await tripWorkspacePage.addPlanItemFromMap({ title: 'Tower Bridge' });

    const createdItem = await TestSetupHelper.getTripPlanItemByTitle(dbManager, tripId, 'Tower Bridge');
    expect(createdItem).toBeTruthy();
    await expect(tripWorkspacePage.rowByTitle('Tower Bridge')).toBeVisible({ timeout: 10000 });
    expect(await tripWorkspacePage.getStopsHeading()).toBe('Stops (1)');

    await tripWorkspacePage.editPlannedItem('Tower Bridge', 'Tower Bridge Updated');

    const editedItem = await TestSetupHelper.getTripPlanItemById(dbManager, createdItem.id);
    expect(editedItem.title).toBe('Tower Bridge Updated');

    await tripWorkspacePage.deletePlannedItem('Tower Bridge Updated');

    await expect
      .poll(async () => TestSetupHelper.getTripPlanItemById(dbManager, createdItem.id), { timeout: 10000 })
      .toBeNull();
    expect(await tripWorkspacePage.getStopsHeading()).toBe('Stops (0)');
  });

  test('ACTIVE trip: stops rail, map planning, and mark-visited action', async ({ page, isolatedUsers, dbManager }) => {
    const testUser = createManagedUser(isolatedUsers);
    const { user } = await createAndLoginRasterUser(page, dbManager, testUser);

    const now = Date.now();
    const tripId = await TestSetupHelper.createTrip(dbManager, user.id, {
      name: 'Active Spain',
      startTime: new Date(now - days(1)),
      endTime: new Date(now + days(2)),
      status: 'ACTIVE',
      color: '#16A34A'
    });

    const seededItemId = await TestSetupHelper.createTripPlanItem(dbManager, tripId, {
      title: 'Sagrada Familia',
      latitude: 41.4036,
      longitude: 2.1744,
      plannedDay: new Date(now).toISOString().slice(0, 10),
      priority: 'MUST',
      orderIndex: 0,
      isVisited: false
    });

    await stubPlanSuggestion(page, 'Barcelona suggestion');

    await page.goto(`/app/trips/${tripId}`);
    const tripWorkspacePage = new TripWorkspacePage(page);
    await tripWorkspacePage.waitForPageLoad();

    expect(await tripWorkspacePage.isSummaryBarVisible()).toBe(true);
    expect(await tripWorkspacePage.getStopsHeading()).toBe('Stops (1)');
    expect(await tripWorkspacePage.getVisitStatus('Sagrada Familia')).toBe('planned');

    await tripWorkspacePage.addPlanItemFromMap({ title: 'Park Guell' });
    const addedFromMap = await TestSetupHelper.getTripPlanItemByTitle(dbManager, tripId, 'Park Guell');
    expect(addedFromMap).toBeTruthy();
    await expect(tripWorkspacePage.rowByTitle('Park Guell')).toBeVisible({ timeout: 10000 });
    expect(await tripWorkspacePage.getStopsHeading()).toBe('Stops (2)');

    await tripWorkspacePage.markVisited('Sagrada Familia');
    await expect.poll(async () => tripWorkspacePage.getVisitStatus('Sagrada Familia'), { timeout: 10000 }).toBe('visited');

    const visitedItem = await TestSetupHelper.getTripPlanItemById(dbManager, seededItemId);
    expect(visitedItem.is_visited).toBe(true);
    expect(visitedItem.manual_override_state).toBe('CONFIRMED');

    // The other stop is untouched.
    expect(await tripWorkspacePage.getVisitStatus('Park Guell')).toBe('planned');
  });

  test('COMPLETED trip: auto-matched visit shown in the rail and the Actual lens', async ({ page, isolatedUsers, dbManager }) => {
    const testUser = createManagedUser(isolatedUsers);
    const { user } = await createAndLoginRasterUser(page, dbManager, testUser);

    const now = Date.now();
    const tripStart = new Date(now - days(10));
    const tripEnd = new Date(now - days(7));

    const tripId = await TestSetupHelper.createTrip(dbManager, user.id, {
      name: 'Completed Paris',
      startTime: tripStart,
      endTime: tripEnd,
      status: 'COMPLETED',
      color: '#F59E0B'
    });

    const stayTimestamp = new Date(now - days(9));
    const stayId = await TestSetupHelper.createTimelineStayAt(dbManager, user.id, {
      timestamp: stayTimestamp,
      durationSeconds: 1800,
      latitude: 48.8584,
      longitude: 2.2945,
      locationName: 'Eiffel Tower Stay'
    });

    const matchedItemId = await TestSetupHelper.createTripPlanItem(dbManager, tripId, {
      title: 'Eiffel Tower',
      latitude: 48.8584,
      longitude: 2.2945,
      plannedDay: new Date(now - days(9)).toISOString().slice(0, 10),
      priority: 'MUST',
      orderIndex: 0,
      isVisited: true,
      visitConfidence: 0.96,
      visitSource: 'AUTO',
      visitedAt: stayTimestamp
    });

    await TestSetupHelper.createTripVisitMatch(dbManager, {
      tripId,
      planItemId: matchedItemId,
      stayId,
      distanceMeters: 12,
      dwellSeconds: 1800,
      confidence: 0.96,
      decision: 'AUTO_MATCHED'
    });

    await page.goto(`/app/trips/${tripId}`);
    const tripWorkspacePage = new TripWorkspacePage(page);
    await tripWorkspacePage.waitForPageLoad();

    // The rail opens on the Plan lens; the stay makes the Actual lens available.
    await expect(tripWorkspacePage.lensSwitch()).toBeVisible({ timeout: 15000 });

    expect(await tripWorkspacePage.getStopsHeading()).toBe('Stops (1)');
    await expect.poll(async () => tripWorkspacePage.getVisitStatus('Eiffel Tower'), { timeout: 10000 }).toBe('visited');
    expect(await tripWorkspacePage.getStopSummaryText('Eiffel Tower')).toContain('96%');

    await tripWorkspacePage.openLens('Timeline');
    await expect(page.locator('.trip-rail .trip-actual-lens')).toContainText('Eiffel Tower Stay', { timeout: 15000 });

    await tripWorkspacePage.openLens('Plan');
    await expect(tripWorkspacePage.rowByTitle('Eiffel Tower')).toBeVisible({ timeout: 10000 });

    const item = await TestSetupHelper.getTripPlanItemById(dbManager, matchedItemId);
    expect(item.manual_override_state).toBeNull();
    const persistedMatch = await dbManager.client.query(
      'SELECT decision FROM trip_place_visit_match WHERE trip_id = $1 AND plan_item_id = $2 ORDER BY id DESC LIMIT 1',
      [tripId, matchedItemId]
    );
    expect(persistedMatch.rows[0]?.decision).toBe('AUTO_MATCHED');
  });
});
