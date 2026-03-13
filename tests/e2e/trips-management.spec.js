import { test, expect } from '../fixtures/isolated-fixture.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';

const days = (n) => n * 24 * 60 * 60 * 1000;

test.describe('Trip Plans Management Page', () => {
  test('creates trip plan from UI and auto-creates linked timeline label', async ({ page, isolatedUsers, dbManager }) => {
    const { tripsPage, user } = await TestSetupHelper.loginAndNavigateToTripsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    await tripsPage.createTripPlan({ name: 'Spain Planner', notes: 'UI create flow' });

    const trip = await TestSetupHelper.getTripByName(dbManager, user.id, 'Spain Planner');
    expect(trip).toBeTruthy();
    expect(trip.period_tag_id).toBeTruthy();

    const linkedTag = await TestSetupHelper.getPeriodTagById(dbManager, trip.period_tag_id);
    expect(linkedTag).toBeTruthy();
    expect(linkedTag.source).toBe('trip');
  });

  test('edits trip and synchronizes linked label fields', async ({ page, isolatedUsers, dbManager }) => {
    const { tripsPage, user } = await TestSetupHelper.loginAndNavigateToTripsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const now = Date.now();
    const { periodTagId, tripId } = await TestSetupHelper.createLinkedLabelAndTrip(dbManager, user.id, {
      name: 'Sync Trip',
      startTime: new Date(now - days(8)),
      endTime: new Date(now - days(6)),
      color: '#334455',
      periodTagSource: 'trip',
      tripStatus: 'COMPLETED'
    });

    await page.reload();
    await tripsPage.waitForPageLoad();

    await tripsPage.editTripPlan('Sync Trip', {
      newName: 'Sync Trip Updated',
      startIndex: 9,
      endIndex: 14,
      randomizeColor: true
    });

    const updatedTrip = await TestSetupHelper.getTripById(dbManager, tripId);
    const updatedTag = await TestSetupHelper.getPeriodTagById(dbManager, periodTagId);

    expect(updatedTrip.name).toBe('Sync Trip Updated');
    expect(updatedTag.tag_name).toBe(updatedTrip.name);
    expect(updatedTag.color).toBe(updatedTrip.color);
    expect(new Date(updatedTag.start_time).getTime()).toBe(new Date(updatedTrip.start_time).getTime());
    expect(new Date(updatedTag.end_time).getTime()).toBe(new Date(updatedTrip.end_time).getTime());
  });

  test('supports search and status filters (ALL, UPCOMING, ACTIVE, COMPLETED)', async ({ page, isolatedUsers, dbManager }) => {
    const { tripsPage, user } = await TestSetupHelper.loginAndNavigateToTripsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const now = Date.now();
    await TestSetupHelper.createTrip(dbManager, user.id, {
      name: 'Upcoming London',
      startTime: new Date(now + days(5)),
      endTime: new Date(now + days(7)),
      status: 'UPCOMING'
    });
    await TestSetupHelper.createTrip(dbManager, user.id, {
      name: 'Active Spain',
      startTime: new Date(now - days(1)),
      endTime: new Date(now + days(1)),
      status: 'ACTIVE'
    });
    await TestSetupHelper.createTrip(dbManager, user.id, {
      name: 'Completed Paris',
      startTime: new Date(now - days(12)),
      endTime: new Date(now - days(8)),
      status: 'COMPLETED'
    });

    await page.reload();
    await tripsPage.waitForPageLoad();

    await tripsPage.setSearchTerm('Active Spain');
    expect(await tripsPage.getVisibleTripNames()).toEqual(['Active Spain']);

    await tripsPage.setSearchTerm('');
    await tripsPage.setStatusFilter('Upcoming');
    expect(await tripsPage.getVisibleTripNames()).toEqual(['Upcoming London']);

    await tripsPage.setStatusFilter('Active');
    expect(await tripsPage.getVisibleTripNames()).toEqual(['Active Spain']);

    await tripsPage.setStatusFilter('Completed');
    expect(await tripsPage.getVisibleTripNames()).toEqual(['Completed Paris']);

    await tripsPage.setStatusFilter('All statuses');
    const allNames = await tripsPage.getVisibleTripNames();
    expect(allNames).toContain('Upcoming London');
    expect(allNames).toContain('Active Spain');
    expect(allNames).toContain('Completed Paris');
  });

  test('creates trip plan from timeline label via dialog', async ({ page, isolatedUsers, dbManager }) => {
    const { tripsPage, user } = await TestSetupHelper.loginAndNavigateToTripsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const periodTagId = await TestSetupHelper.createPeriodTag(dbManager, user.id, {
      tagName: 'Label For Trip Creation',
      startTime: new Date('2025-03-10T00:00:00.000Z'),
      endTime: new Date('2025-03-14T00:00:00.000Z'),
      source: 'manual'
    });

    await page.reload();
    await tripsPage.waitForPageLoad();

    await tripsPage.createTripFromTimelineLabel('Label For Trip Creation');

    await expect
      .poll(async () => TestSetupHelper.getTripByPeriodTagId(dbManager, periodTagId), { timeout: 10000 })
      .not.toBeNull();
    const trip = await TestSetupHelper.getTripByPeriodTagId(dbManager, periodTagId);
    expect(trip.name).toBe('Label For Trip Creation');
  });

  test('unlinks trip from timeline label using actions column', async ({ page, isolatedUsers, dbManager }) => {
    const { tripsPage, user } = await TestSetupHelper.loginAndNavigateToTripsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const { periodTagId, tripId } = await TestSetupHelper.createLinkedLabelAndTrip(dbManager, user.id, {
      name: 'Trip To Unlink',
      periodTagSource: 'trip',
      tripStatus: 'COMPLETED'
    });

    await page.reload();
    await tripsPage.waitForPageLoad();

    await tripsPage.unlinkTripFromLabel('Trip To Unlink');

    await expect
      .poll(async () => (await TestSetupHelper.getTripById(dbManager, tripId))?.period_tag_id, { timeout: 10000 })
      .toBeNull();

    const trip = await TestSetupHelper.getTripById(dbManager, tripId);
    const tag = await TestSetupHelper.getPeriodTagById(dbManager, periodTagId);

    expect(trip.period_tag_id).toBeNull();
    expect(tag).toBeTruthy();
  });

  test('deletes linked trip in unlink_only and delete_both modes', async ({ page, isolatedUsers, dbManager }) => {
    const { tripsPage, user } = await TestSetupHelper.loginAndNavigateToTripsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const first = await TestSetupHelper.createLinkedLabelAndTrip(dbManager, user.id, {
      name: 'Delete Trip Unlink Only',
      periodTagSource: 'trip',
      tripStatus: 'COMPLETED'
    });

    const second = await TestSetupHelper.createLinkedLabelAndTrip(dbManager, user.id, {
      name: 'Delete Trip And Label',
      periodTagSource: 'trip',
      tripStatus: 'COMPLETED'
    });

    await page.reload();
    await tripsPage.waitForPageLoad();

    await tripsPage.deleteTrip('Delete Trip Unlink Only', 'unlink_only');

    await expect
      .poll(async () => TestSetupHelper.getTripById(dbManager, first.tripId), { timeout: 10000 })
      .toBeNull();

    const firstTag = await TestSetupHelper.getPeriodTagById(dbManager, first.periodTagId);
    expect(firstTag).toBeTruthy();

    await tripsPage.deleteTrip('Delete Trip And Label', 'delete_both');

    await expect
      .poll(async () => TestSetupHelper.getTripById(dbManager, second.tripId), { timeout: 10000 })
      .toBeNull();

    await expect
      .poll(async () => TestSetupHelper.getPeriodTagById(dbManager, second.periodTagId), { timeout: 10000 })
      .toBeNull();
  });
});
