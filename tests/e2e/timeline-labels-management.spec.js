import { test, expect } from '../fixtures/isolated-fixture.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';

test.describe('Timeline Labels Management Page', () => {
  test('creates timeline label from UI and persists row in DB', async ({ page, isolatedUsers, dbManager }) => {
    const { timelineLabelsPage, user } = await TestSetupHelper.loginAndNavigateToTimelineLabelsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const initialCount = await TestSetupHelper.countTimelineLabels(dbManager, user.id);
    const labelName = 'Vacation in Spain';

    await timelineLabelsPage.createLabel({ name: labelName });

    await expect
      .poll(async () => TestSetupHelper.countTimelineLabels(dbManager, user.id), { timeout: 10000 })
      .toBe(initialCount + 1);

    const created = (await TestSetupHelper.getTimelineLabelsByUser(dbManager, user.id)).find((tag) => tag.name === labelName);
    expect(created).toBeTruthy();
    expect(created.source).toBe('manual');
  });

  test('edits linked label and syncs linked trip fields', async ({ page, isolatedUsers, dbManager }) => {
    const { timelineLabelsPage, user } = await TestSetupHelper.loginAndNavigateToTimelineLabelsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const originalStart = new Date('2025-01-05T00:00:00.000Z');
    const originalEnd = new Date('2025-01-08T00:00:00.000Z');

    const timelineLabelId = await TestSetupHelper.createTimelineLabel(dbManager, user.id, {
      name: 'Linked Spain',
      startTime: originalStart,
      endTime: originalEnd,
      source: 'manual'
    });

    await dbManager.client.query('UPDATE timeline_labels SET color = $1 WHERE id = $2', ['#112233', timelineLabelId]);

    const tripId = await TestSetupHelper.createTrip(dbManager, user.id, {
      name: 'Linked Spain',
      startTime: originalStart,
      endTime: originalEnd,
      status: 'COMPLETED',
      color: '#112233',
      timelineLabelId
    });

    await page.reload();
    await timelineLabelsPage.waitForPageLoad();

    await timelineLabelsPage.editLabel('Linked Spain', {
      newName: 'Linked Spain Updated',
      startIndex: 9,
      endIndex: 13,
      randomizeColor: true
    });

    const updatedTag = await TestSetupHelper.getTimelineLabelById(dbManager, timelineLabelId);
    const updatedTrip = await TestSetupHelper.getTripById(dbManager, tripId);

    expect(updatedTag.name).toBe('Linked Spain Updated');
    expect(updatedTag.color).not.toBe('#112233');
    expect(new Date(updatedTag.start_time).getTime()).not.toBe(originalStart.getTime());
    expect(new Date(updatedTag.end_time).getTime()).not.toBe(originalEnd.getTime());

    expect(updatedTrip.name).toBe(updatedTag.name);
    expect(new Date(updatedTrip.start_time).getTime()).toBe(new Date(updatedTag.start_time).getTime());
    expect(new Date(updatedTrip.end_time).getTime()).toBe(new Date(updatedTag.end_time).getTime());
    expect(updatedTrip.color).toBe(updatedTag.color);
  });

  test('deletes standalone label from labels page', async ({ page, isolatedUsers, dbManager }) => {
    const { timelineLabelsPage, user } = await TestSetupHelper.loginAndNavigateToTimelineLabelsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const labelId = await TestSetupHelper.createTimelineLabel(dbManager, user.id, {
      name: 'Standalone Label',
      startTime: new Date('2025-01-01T00:00:00.000Z'),
      endTime: new Date('2025-01-03T00:00:00.000Z'),
      source: 'manual'
    });

    await page.reload();
    await timelineLabelsPage.waitForPageLoad();

    await timelineLabelsPage.deleteLabel('Standalone Label', 'standalone');

    await expect
      .poll(async () => TestSetupHelper.getTimelineLabelById(dbManager, labelId), { timeout: 10000 })
      .toBeNull();
  });

  test('creates trip plan from completed label', async ({ page, isolatedUsers, dbManager }) => {
    const { timelineLabelsPage, user } = await TestSetupHelper.loginAndNavigateToTimelineLabelsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const timelineLabelId = await TestSetupHelper.createTimelineLabel(dbManager, user.id, {
      name: 'Completed Label',
      startTime: new Date('2025-01-01T00:00:00.000Z'),
      endTime: new Date('2025-01-04T00:00:00.000Z'),
      source: 'manual'
    });

    await page.reload();
    await timelineLabelsPage.waitForPageLoad();

    await timelineLabelsPage.createTripPlanFromLabel('Completed Label');

    await expect
      .poll(async () => TestSetupHelper.getTripByTimelineLabelId(dbManager, timelineLabelId), { timeout: 10000 })
      .not.toBeNull();
    const linkedTrip = await TestSetupHelper.getTripByTimelineLabelId(dbManager, timelineLabelId);
    expect(linkedTrip.name).toBe('Completed Label');
  });

  test('unlinks label from trip while preserving both entities', async ({ page, isolatedUsers, dbManager }) => {
    const { timelineLabelsPage, user } = await TestSetupHelper.loginAndNavigateToTimelineLabelsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const { timelineLabelId, tripId } = await TestSetupHelper.createLinkedLabelAndTrip(dbManager, user.id, {
      name: 'Linked For Unlink',
      tripStatus: 'COMPLETED'
    });

    await page.reload();
    await timelineLabelsPage.waitForPageLoad();

    await timelineLabelsPage.unlinkLabelFromTrip('Linked For Unlink');

    await expect
      .poll(async () => (await TestSetupHelper.getTripById(dbManager, tripId))?.timeline_label_id, { timeout: 10000 })
      .toBeNull();

    const tag = await TestSetupHelper.getTimelineLabelById(dbManager, timelineLabelId);
    const trip = await TestSetupHelper.getTripById(dbManager, tripId);

    expect(tag).toBeTruthy();
    expect(trip).toBeTruthy();
    expect(trip.timeline_label_id).toBeNull();
  });

  test('deletes linked label with unlink_only mode (trip preserved)', async ({ page, isolatedUsers, dbManager }) => {
    const { timelineLabelsPage, user } = await TestSetupHelper.loginAndNavigateToTimelineLabelsPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const { timelineLabelId, tripId } = await TestSetupHelper.createLinkedLabelAndTrip(dbManager, user.id, {
      name: 'Linked Delete Label',
      tripStatus: 'COMPLETED'
    });

    await page.reload();
    await timelineLabelsPage.waitForPageLoad();

    await timelineLabelsPage.deleteLabel('Linked Delete Label', 'unlink_only');

    await expect
      .poll(async () => TestSetupHelper.getTimelineLabelById(dbManager, timelineLabelId), { timeout: 10000 })
      .toBeNull();

    const trip = await TestSetupHelper.getTripById(dbManager, tripId);
    expect(trip).toBeTruthy();
    expect(trip.timeline_label_id).toBeNull();
  });
});
