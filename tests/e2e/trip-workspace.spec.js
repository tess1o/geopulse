import { test, expect } from '../fixtures/isolated-fixture.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';
import { TripWorkspacePage } from '../pages/TripWorkspacePage.js';

const days = (n) => n * 24 * 60 * 60 * 1000;

const stubPlanSuggestion = async (page, title = 'Stubbed plan suggestion') => {
  await page.route('**/api/trips/plan-suggestion*', async (route) => {
    const url = new URL(route.request().url());
    const lat = Number(url.searchParams.get('lat') || 51.5007);
    const lon = Number(url.searchParams.get('lon') || -0.1246);

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
    const { user } = await TestSetupHelper.createAndLoginUser(page, dbManager, testUser);

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

    expect(await tripWorkspacePage.isOverviewTabVisible()).toBe(false);
    expect(await tripWorkspacePage.isPlanTabVisible()).toBe(true);

    await tripWorkspacePage.isPlanningCalloutVisible('Future trip planning mode');
    expect(await tripWorkspacePage.getComparisonCardTitle()).toBe('Planned Stops');
    expect(await tripWorkspacePage.isMatchedStayColumnVisible()).toBe(false);

    await tripWorkspacePage.addPlanItemFromMap({ title: 'Tower Bridge' });

    const createdItem = await TestSetupHelper.getTripPlanItemByTitle(dbManager, tripId, 'Tower Bridge');
    expect(createdItem).toBeTruthy();

    await tripWorkspacePage.editPlannedItem('Tower Bridge', 'Tower Bridge Updated');

    const editedItem = await TestSetupHelper.getTripPlanItemById(dbManager, createdItem.id);
    expect(editedItem.title).toBe('Tower Bridge Updated');

    await tripWorkspacePage.deletePlannedItem('Tower Bridge Updated');

    await expect
      .poll(async () => TestSetupHelper.getTripPlanItemById(dbManager, createdItem.id), { timeout: 10000 })
      .toBeNull();
  });

  test('ACTIVE trip: overview+plan tabs, map planning, and visit action transitions', async ({ page, isolatedUsers, dbManager }) => {
    const testUser = createManagedUser(isolatedUsers);
    const { user } = await TestSetupHelper.createAndLoginUser(page, dbManager, testUser);

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

    expect(await tripWorkspacePage.isOverviewTabVisible()).toBe(true);
    expect(await tripWorkspacePage.isPlanTabVisible()).toBe(true);

    await tripWorkspacePage.openPlanTab();
    await tripWorkspacePage.isPlanningCalloutVisible('Active trip planning mode');

    await tripWorkspacePage.addPlanItemFromMap({ title: 'Park Guell' });
    const addedFromMap = await TestSetupHelper.getTripPlanItemByTitle(dbManager, tripId, 'Park Guell');
    expect(addedFromMap).toBeTruthy();

    await tripWorkspacePage.applyVisitAction('Sagrada Familia', 'markVisited');
    await expect.poll(async () => tripWorkspacePage.getStatusText('Sagrada Familia'), { timeout: 10000 }).toContain('Visited');

    await tripWorkspacePage.applyVisitAction('Sagrada Familia', 'markNotVisited');
    await expect.poll(async () => tripWorkspacePage.getStatusText('Sagrada Familia'), { timeout: 10000 }).toContain('Missed');

    await tripWorkspacePage.applyVisitAction('Sagrada Familia', 'reset');
    await expect.poll(async () => tripWorkspacePage.getStatusText('Sagrada Familia'), { timeout: 10000 }).toContain('Planned');

    const resetItem = await TestSetupHelper.getTripPlanItemById(dbManager, seededItemId);
    expect(resetItem.manual_override_state).toBeNull();
    expect(resetItem.is_visited).toBe(false);
  });

  test('COMPLETED trip: plan-vs-actual with matched stay evidence and manual override reset', async ({ page, isolatedUsers, dbManager }) => {
    const testUser = createManagedUser(isolatedUsers);
    const { user } = await TestSetupHelper.createAndLoginUser(page, dbManager, testUser);

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

    await tripWorkspacePage.openPlanTab();

    expect(await tripWorkspacePage.getComparisonCardTitle()).toBe('Plan vs Actual');
    expect(await tripWorkspacePage.isMatchedStayColumnVisible()).toBe(true);

    await tripWorkspacePage.expectMatchedStayEvidence('Eiffel Tower', 'Eiffel Tower Stay', 'High');

    await tripWorkspacePage.applyVisitAction('Eiffel Tower', 'markNotVisited');
    await expect.poll(async () => tripWorkspacePage.getStatusText('Eiffel Tower'), { timeout: 10000 }).toContain('Missed');

    await tripWorkspacePage.applyVisitAction('Eiffel Tower', 'reset');
    await expect.poll(async () => tripWorkspacePage.getStatusText('Eiffel Tower'), { timeout: 10000 }).toContain('Visited');

    const resetItem = await TestSetupHelper.getTripPlanItemById(dbManager, matchedItemId);
    expect(resetItem.manual_override_state).toBeNull();
    const persistedMatch = await dbManager.client.query(
      'SELECT decision FROM trip_place_visit_match WHERE trip_id = $1 AND plan_item_id = $2 ORDER BY id DESC LIMIT 1',
      [tripId, matchedItemId]
    );
    expect(persistedMatch.rows[0]?.decision).toBe('AUTO_MATCHED');
  });
});
