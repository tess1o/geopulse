import { test, expect } from '../fixtures/isolated-fixture.js';
import { TimelinePage } from '../pages/TimelinePage.js';
import { PlaceDetailsPage } from '../pages/PlaceDetailsPage.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { MapEngineHarness } from '../utils/map-engine-harness.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';

test.use({ mapMode: 'VECTOR' });

const TIMELINE_DAY = {
  startDate: new Date('2025-09-21'),
  endDate: new Date('2025-09-21')
};

const NEXT_DAY = {
  startDate: new Date('2025-09-22'),
  endDate: new Date('2025-09-22')
};

const markdownSnippet = (value) => String(value || '')
  .replace(/[#*_`>\[\]()]/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()
  .slice(0, 500);

const setupVectorTimelineUser = async (page, dbManager, isolatedUsers, userOverrides = {}) => {
  const timelinePage = new TimelinePage(page);
  const testUser = createManagedUser(isolatedUsers, userOverrides);
  const { user } = await TestSetupHelper.createAndLoginUser(page, dbManager, testUser, { mapMode: 'VECTOR' });
  return { timelinePage, testUser, user };
};

const insertStay = async (dbManager, userId, {
  timestamp = '2025-09-21T09:00:00Z',
  durationSeconds = 3600,
  latitude = 40.7128,
  longitude = -74.0060,
  locationName = 'Timeline Notes Stay',
  favoriteId = null,
  geocodingId = null,
  locationSource = favoriteId ? 'FAVORITE' : 'GEOCODING'
} = {}) => {
  const result = await dbManager.client.query(`
    INSERT INTO timeline_stays (
      user_id,
      favorite_id,
      geocoding_id,
      timestamp,
      stay_duration,
      location,
      location_name,
      location_source,
      created_at,
      last_updated
    )
    VALUES (
      $1, $2, $3, $4, $5,
      ST_SetSRID(ST_MakePoint($6, $7), 4326),
      $8, $9, NOW(), NOW()
    )
    RETURNING id
  `, [
    userId,
    favoriteId,
    geocodingId,
    timestamp,
    durationSeconds,
    longitude,
    latitude,
    locationName,
    locationSource
  ]);

  return result.rows[0].id;
};

const insertTrip = async (dbManager, userId, {
  timestamp = '2025-09-21T11:00:00Z',
  durationSeconds = 1800,
  startLatitude = 40.7128,
  startLongitude = -74.0060,
  endLatitude = 40.7308,
  endLongitude = -73.9973,
  distanceMeters = 2400,
  movementType = 'WALK'
} = {}) => {
  const result = await dbManager.client.query(`
    INSERT INTO timeline_trips (
      user_id,
      timestamp,
      trip_duration,
      start_point,
      end_point,
      distance_meters,
      movement_type,
      created_at,
      last_updated
    )
    VALUES (
      $1, $2, $3,
      ST_SetSRID(ST_MakePoint($4, $5), 4326),
      ST_SetSRID(ST_MakePoint($6, $7), 4326),
      $8, $9, NOW(), NOW()
    )
    RETURNING id
  `, [
    userId,
    timestamp,
    durationSeconds,
    startLongitude,
    startLatitude,
    endLongitude,
    endLatitude,
    distanceMeters,
    movementType
  ]);

  return result.rows[0].id;
};

const insertGpsPoint = async (dbManager, userId, {
  deviceId = 'timeline-notes-map-device',
  timestamp,
  latitude,
  longitude,
  velocity = 0
}) => {
  await dbManager.client.query(`
    INSERT INTO gps_points (
      device_id,
      user_id,
      coordinates,
      timestamp,
      accuracy,
      battery,
      velocity,
      altitude,
      source_type,
      created_at
    )
    VALUES (
      $1, $2,
      ST_SetSRID(ST_MakePoint($3, $4), 4326),
      $5, 8.0, 90, $6, 20.0, 'OVERLAND', $5
    )
  `, [
    deviceId,
    userId,
    longitude,
    latitude,
    timestamp,
    velocity
  ]);
};

const insertGeoPulseNote = async (dbManager, userId, {
  title,
  contentMarkdown,
  eventTime,
  latitude = null,
  longitude = null,
  locationSource = latitude !== null && longitude !== null ? 'EXPLICIT' : 'NONE',
  anchorType = 'TIMESTAMP',
  stayId = null,
  tripId = null
}) => {
  const result = await dbManager.client.query(`
    INSERT INTO timeline_notes (
      user_id,
      title,
      content_markdown,
      snippet,
      event_time,
      location,
      location_source,
      anchor_type,
      stay_id,
      trip_id,
      created_at,
      updated_at
    )
    VALUES (
      $1, $2, $3, $4, $5,
      CASE
        WHEN $6::double precision IS NULL OR $7::double precision IS NULL
          THEN NULL
        ELSE ST_SetSRID(ST_MakePoint($7, $6), 4326)
      END,
      $8, $9, $10, $11, NOW(), NOW()
    )
    RETURNING id
  `, [
    userId,
    title,
    contentMarkdown,
    markdownSnippet(contentMarkdown),
    eventTime,
    latitude,
    longitude,
    locationSource,
    anchorType,
    stayId,
    tripId
  ]);

  return result.rows[0].id;
};

const findGeoPulseNoteByTitle = async (dbManager, userId, title) => {
  const result = await dbManager.client.query(`
    SELECT
      id,
      title,
      content_markdown,
      snippet,
      anchor_type,
      stay_id,
      trip_id,
      location_source,
      ST_Y(location) AS latitude,
      ST_X(location) AS longitude,
      deleted_at
    FROM timeline_notes
    WHERE user_id = $1 AND title = $2
    ORDER BY id DESC
    LIMIT 1
  `, [userId, title]);

  return result.rows[0] || null;
};

const findGeoPulseNoteById = async (dbManager, noteId) => {
  const result = await dbManager.client.query(`
    SELECT id, title, content_markdown, deleted_at
    FROM timeline_notes
    WHERE id = $1
  `, [noteId]);

  return result.rows[0] || null;
};

const getTimelineCardByText = (page, cardSelector, text) => (
  page.locator(cardSelector).filter({ hasText: text }).first()
);

const openCardContextMenu = async (page, card) => {
  await expect(card).toBeVisible({ timeout: 15000 });
  await card.click({ button: 'right', force: true });
  await expect(page.locator('.p-contextmenu:visible')).toBeVisible({ timeout: 5000 });
};

const clickContextMenuItem = async (page, label) => {
  await page.locator('.p-contextmenu:visible .p-contextmenu-item-label', { hasText: label }).first().click();
};

const getDialogByTitle = (page, title) => (
  page.locator('.p-dialog:visible').filter({ has: page.locator('.p-dialog-title', { hasText: title }) }).last()
);

const fillAndSaveNoteDialog = async (page, { title, content, submitLabel = /Save|Update/ }) => {
  const dialog = page.locator('.note-editor-dialog:visible').last();
  await expect(dialog).toBeVisible({ timeout: 10000 });

  if (title !== undefined) {
    await dialog.locator('#note-title').fill(title);
  }
  if (content !== undefined) {
    await dialog.locator('#note-content').fill(content);
  }

  await dialog.getByRole('button', { name: submitLabel }).click();
};

const waitForSuccessToast = async (page, text) => {
  await expect(
    page.locator('.p-toast-message-success').filter({ hasText: text }).last()
  ).toBeVisible({ timeout: 10000 });
};

const openNotesViewerFromCard = async (page, card) => {
  const trigger = card.locator('.note-trigger').first();
  await expect(trigger).toBeVisible({ timeout: 15000 });
  await trigger.click();
  const viewer = page.locator('.notes-viewer-dialog:visible').last();
  await expect(viewer).toBeVisible({ timeout: 10000 });
  return viewer;
};

const closeNotesViewer = async (viewer) => {
  await viewer.locator('.p-dialog-footer').getByRole('button', { name: 'Close' }).click();
  await expect(viewer).toBeHidden({ timeout: 10000 });
};

const openFirstNoteMarker = async (page, rootSelector) => {
  const marker = page.locator(`${rootSelector} .gp-note-marker-wrapper`).first();
  await expect(marker).toBeVisible({ timeout: 15000 });
  await marker.dispatchEvent('click');

  const viewer = page.locator('.notes-viewer-dialog:visible').last();
  await expect(viewer).toBeVisible({ timeout: 10000 });
  return viewer;
};

const editFirstVisibleNote = async (page, { title, content }) => {
  const viewer = page.locator('.notes-viewer-dialog:visible').last();
  await viewer.getByRole('button', { name: 'Edit note' }).first().click();

  await expect(getDialogByTitle(page, 'Edit Note')).toBeVisible({ timeout: 10000 });
  await fillAndSaveNoteDialog(page, { title, content, submitLabel: 'Update' });
  await waitForSuccessToast(page, 'Note updated');
};

const deleteFirstVisibleNote = async (page) => {
  const viewer = page.locator('.notes-viewer-dialog:visible').last();
  await viewer.getByRole('button', { name: 'Delete note' }).first().click();

  const confirm = viewer.locator('.note-delete-confirm').first();
  await expect(confirm).toBeVisible({ timeout: 5000 });
  await confirm.getByRole('button', { name: 'Delete' }).click();
  await waitForSuccessToast(page, 'Note deleted');
};

const waitForVectorMap = async (page, rootSelector) => {
  const mapHarness = new MapEngineHarness(page);
  const mode = await mapHarness.waitForMapReady({ rootSelector, timeout: 20000, settleMs: 800 });
  expect(mode).toBe('VECTOR');
};

test.describe('Timeline GeoPulse notes', () => {
  test('creates, edits, and deletes a GeoPulse note from the timeline UI', async ({ page, isolatedUsers, dbManager }) => {
    const { timelinePage, user } = await setupVectorTimelineUser(page, dbManager, isolatedUsers);

    await insertStay(dbManager, user.id, {
      timestamp: '2025-09-21T09:00:00Z',
      latitude: 40.7484,
      longitude: -73.9857,
      locationName: 'Notes Test Stay'
    });

    await timelinePage.navigateWithDateRange(TIMELINE_DAY.startDate, TIMELINE_DAY.endDate);
    await timelinePage.waitForPageLoad();
    await timelinePage.waitForTimelineContent();

    const stayCard = getTimelineCardByText(page, '.timeline-card--stay', 'Notes Test Stay');
    await openCardContextMenu(page, stayCard);
    await clickContextMenuItem(page, 'Add note...');

    await expect(getDialogByTitle(page, 'Add Note')).toBeVisible({ timeout: 10000 });
    await fillAndSaveNoteDialog(page, {
      title: 'Timeline UI note',
      content: 'Created from the timeline card UI.'
    });
    await waitForSuccessToast(page, 'Note saved');

    await expect.poll(
      () => findGeoPulseNoteByTitle(dbManager, user.id, 'Timeline UI note'),
      { timeout: 10000 }
    ).not.toBeNull();
    const createdNote = await findGeoPulseNoteByTitle(dbManager, user.id, 'Timeline UI note');

    expect(createdNote.content_markdown).toBe('Created from the timeline card UI.');
    expect(createdNote.anchor_type).toBe('STAY');
    expect(createdNote.stay_id).toBeTruthy();
    expect(Number(createdNote.latitude)).toBeCloseTo(40.7484, 5);
    expect(Number(createdNote.longitude)).toBeCloseTo(-73.9857, 5);
    expect(createdNote.location_source).toBe('EXPLICIT');

    await expect(stayCard.locator('.note-trigger')).toBeVisible({ timeout: 15000 });

    let viewer = await openNotesViewerFromCard(page, stayCard);
    await expect(viewer).toContainText('Timeline UI note');
    await expect(viewer).toContainText('Created from the timeline card UI.');
    await expect(viewer).toContainText('GeoPulse');
    await expect(viewer).toContainText('Geotagged');

    await editFirstVisibleNote(page, {
      title: 'Timeline UI note edited',
      content: 'Updated from the timeline note viewer.'
    });

    viewer = page.locator('.notes-viewer-dialog:visible').last();
    await expect(viewer).toContainText('Timeline UI note edited', { timeout: 15000 });
    await expect(viewer).toContainText('Updated from the timeline note viewer.');

    await expect.poll(
      () => findGeoPulseNoteByTitle(dbManager, user.id, 'Timeline UI note edited'),
      { timeout: 10000 }
    ).not.toBeNull();
    const editedNote = await findGeoPulseNoteByTitle(dbManager, user.id, 'Timeline UI note edited');
    expect(editedNote.content_markdown).toBe('Updated from the timeline note viewer.');

    await deleteFirstVisibleNote(page);

    await expect(stayCard.locator('.note-trigger')).toHaveCount(0, { timeout: 15000 });
    await expect.poll(async () => {
      const note = await findGeoPulseNoteById(dbManager, editedNote.id);
      return note?.deleted_at !== null;
    }, { timeout: 10000 }).toBe(true);
  });

  test('lists only matching GeoPulse notes on timeline cards and the vector map', async ({ page, isolatedUsers, dbManager }) => {
    const otherUser = await isolatedUsers.create(page);
    const { timelinePage, user } = await setupVectorTimelineUser(page, dbManager, isolatedUsers);
    const otherDbUser = await dbManager.getUserByEmail(otherUser.email);

    const stayId = await insertStay(dbManager, user.id, {
      timestamp: '2025-09-21T09:00:00Z',
      latitude: 40.7128,
      longitude: -74.0060,
      locationName: 'Filtered Stay'
    });
    const tripId = await insertTrip(dbManager, user.id, {
      timestamp: '2025-09-21T11:00:00Z',
      startLatitude: 40.7128,
      startLongitude: -74.0060,
      endLatitude: 40.7308,
      endLongitude: -73.9973
    });
    await insertStay(dbManager, user.id, {
      timestamp: '2025-09-22T09:00:00Z',
      latitude: 40.7600,
      longitude: -73.9800,
      locationName: 'Next Day Stay'
    });
    await insertGpsPoint(dbManager, user.id, {
      timestamp: '2025-09-21T09:00:00Z',
      latitude: 40.7128,
      longitude: -74.0060
    });
    await insertGpsPoint(dbManager, user.id, {
      timestamp: '2025-09-21T11:00:00Z',
      latitude: 40.7218,
      longitude: -74.0016,
      velocity: 1.5
    });
    await insertGpsPoint(dbManager, user.id, {
      timestamp: '2025-09-21T11:30:00Z',
      latitude: 40.7308,
      longitude: -73.9973,
      velocity: 1.5
    });

    await insertGeoPulseNote(dbManager, user.id, {
      title: 'Visible stay note',
      contentMarkdown: 'This note belongs to the selected stay.',
      eventTime: '2025-09-21T09:20:00Z',
      latitude: 40.7128,
      longitude: -74.0060,
      locationSource: 'DERIVED_STAY',
      anchorType: 'STAY',
      stayId
    });
    await insertGeoPulseNote(dbManager, user.id, {
      title: 'Visible trip note',
      contentMarkdown: 'This note belongs to the selected trip.',
      eventTime: '2025-09-21T11:10:00Z',
      latitude: 40.7218,
      longitude: -74.0016,
      locationSource: 'DERIVED_TRIP_INTERPOLATED',
      anchorType: 'TRIP',
      tripId
    });
    await insertGeoPulseNote(dbManager, user.id, {
      title: 'Out of range note',
      contentMarkdown: 'This note is outside the selected timeline day.',
      eventTime: '2025-09-23T09:20:00Z',
      latitude: 40.7600,
      longitude: -73.9800
    });
    await insertGeoPulseNote(dbManager, otherDbUser.id, {
      title: 'Other user note',
      contentMarkdown: 'This note belongs to another user.',
      eventTime: '2025-09-21T09:20:00Z',
      latitude: 40.7128,
      longitude: -74.0060
    });

    await timelinePage.navigateWithDateRange(TIMELINE_DAY.startDate, TIMELINE_DAY.endDate);
    await timelinePage.waitForPageLoad();
    await timelinePage.waitForTimelineContent();

    const stayCard = getTimelineCardByText(page, '.timeline-card--stay', 'Filtered Stay');
    const tripCard = page.locator('.timeline-card--trip').first();

    await expect(stayCard.locator('.note-trigger')).toBeVisible({ timeout: 15000 });
    await expect(tripCard.locator('.note-trigger')).toBeVisible({ timeout: 15000 });

    let viewer = await openNotesViewerFromCard(page, stayCard);
    await expect(viewer).toContainText('Visible stay note');
    await expect(viewer).not.toContainText('Out of range note');
    await expect(viewer).not.toContainText('Other user note');
    await closeNotesViewer(viewer);

    viewer = await openNotesViewerFromCard(page, tripCard);
    await expect(viewer).toContainText('Visible trip note');
    await expect(viewer).not.toContainText('Out of range note');
    await expect(viewer).not.toContainText('Other user note');
    await closeNotesViewer(viewer);

    await waitForVectorMap(page, '.map-view-container');
    await page.locator('.map-controls .control-button[title="Show Notes"]').first().click();
    await expect(page.locator('.map-view-container .gp-note-marker-wrapper')).toHaveCount(2, { timeout: 15000 });
    viewer = await openFirstNoteMarker(page, '.map-view-container');
    await expect(viewer).toContainText(/Visible (stay|trip) note/);
    await closeNotesViewer(viewer);

    await timelinePage.navigateWithDateRange(NEXT_DAY.startDate, NEXT_DAY.endDate);
    await timelinePage.waitForPageLoad();
    await timelinePage.waitForTimelineContent();
    await expect(page.locator('.timeline-card--stay .note-trigger, .timeline-card--trip .note-trigger')).toHaveCount(0, { timeout: 15000 });
  });
});

test.describe('Place details GeoPulse notes', () => {
  test('lists, edits, deletes, and maps nearby notes through Place Details UI', async ({ page, isolatedUsers, dbManager }) => {
    const otherUser = await isolatedUsers.create(page);
    const { user } = await setupVectorTimelineUser(page, dbManager, isolatedUsers);
    const otherDbUser = await dbManager.getUserByEmail(otherUser.email);

    const favoriteId = await TestSetupHelper.createFavoritePoint(dbManager, user.id, {
      name: 'Notes Favorite',
      city: 'New York',
      country: 'USA',
      latitude: 40.7484,
      longitude: -73.9857
    });

    await insertStay(dbManager, user.id, {
      favoriteId,
      timestamp: '2025-09-21T09:00:00Z',
      durationSeconds: 3600,
      latitude: 40.7484,
      longitude: -73.9857,
      locationName: 'Notes Favorite'
    });
    await insertStay(dbManager, user.id, {
      favoriteId,
      timestamp: '2025-09-21T13:00:00Z',
      durationSeconds: 3600,
      latitude: 40.7484,
      longitude: -73.9857,
      locationName: 'Notes Favorite'
    });

    const editableNoteId = await insertGeoPulseNote(dbManager, user.id, {
      title: 'Place nearby note',
      contentMarkdown: 'This note is close enough to the favorite.',
      eventTime: '2025-09-21T10:00:00Z',
      latitude: 40.74845,
      longitude: -73.98565
    });
    await insertGeoPulseNote(dbManager, user.id, {
      title: 'Second nearby note',
      contentMarkdown: 'This note should also appear near the place.',
      eventTime: '2025-09-21T12:00:00Z',
      latitude: 40.7485,
      longitude: -73.9856
    });
    await insertGeoPulseNote(dbManager, user.id, {
      title: 'Far away note',
      contentMarkdown: 'This note is outside the place radius.',
      eventTime: '2025-09-21T11:00:00Z',
      latitude: 40.7589,
      longitude: -73.9851
    });
    await insertGeoPulseNote(dbManager, user.id, {
      title: 'Place window miss',
      contentMarkdown: 'This note is outside the visit search window.',
      eventTime: '2025-09-23T11:00:00Z',
      latitude: 40.74845,
      longitude: -73.98565
    });
    await insertGeoPulseNote(dbManager, otherDbUser.id, {
      title: 'Other user nearby note',
      contentMarkdown: 'This nearby note belongs to a different user.',
      eventTime: '2025-09-21T10:00:00Z',
      latitude: 40.74845,
      longitude: -73.98565
    });

    const placeDetailsPage = new PlaceDetailsPage(page);
    await placeDetailsPage.navigateToFavorite(favoriteId);
    await expect(page.locator('.place-header')).toBeVisible({ timeout: 15000 });
    await waitForVectorMap(page, '.place-map-container');

    const notesCard = page.locator('.place-notes-card').first();
    await expect(notesCard).toBeVisible({ timeout: 15000 });
    await expect(notesCard.locator('.place-note-item')).toHaveCount(2, { timeout: 15000 });
    await expect(notesCard).toContainText('Place nearby note');
    await expect(notesCard).toContainText('Second nearby note');
    await expect(notesCard).not.toContainText('Far away note');
    await expect(notesCard).not.toContainText('Place window miss');
    await expect(notesCard).not.toContainText('Other user nearby note');

    await expect(page.locator('.place-map-container .gp-note-marker-wrapper')).toHaveCount(2, { timeout: 15000 });
    let viewer = await openFirstNoteMarker(page, '.place-map-container');
    await expect(viewer).toContainText(/nearby note/i);
    await closeNotesViewer(viewer);

    await notesCard.getByRole('button', { name: /View all/i }).click();
    viewer = page.locator('.notes-viewer-dialog:visible').last();
    await expect(viewer).toBeVisible({ timeout: 10000 });
    await expect(viewer).toContainText('Place nearby note');
    await expect(viewer).toContainText('Second nearby note');

    const editableCard = viewer.locator('.note-card').filter({ hasText: 'Place nearby note' }).first();
    await editableCard.getByRole('button', { name: 'Edit note' }).click();
    await fillAndSaveNoteDialog(page, {
      title: 'Place nearby note edited',
      content: 'Edited from the Place Details notes viewer.',
      submitLabel: 'Update'
    });
    await waitForSuccessToast(page, 'Note updated');

    await expect(notesCard).toContainText('Place nearby note edited', { timeout: 15000 });
    await expect(notesCard).toContainText('Edited from the Place Details notes viewer.');
    await expect.poll(async () => {
      const note = await findGeoPulseNoteById(dbManager, editableNoteId);
      return note?.title;
    }, { timeout: 10000 }).toBe('Place nearby note edited');

    viewer = page.locator('.notes-viewer-dialog:visible').last();
    await expect(viewer).toContainText('Place nearby note edited', { timeout: 15000 });
    const editedCard = viewer.locator('.note-card').filter({ hasText: 'Place nearby note edited' }).first();
    await editedCard.getByRole('button', { name: 'Delete note' }).click();
    const confirm = editedCard.locator('.note-delete-confirm').first();
    await expect(confirm).toBeVisible({ timeout: 5000 });
    await confirm.getByRole('button', { name: 'Delete' }).click();
    await waitForSuccessToast(page, 'Note deleted');

    await expect(notesCard.locator('.place-note-item')).toHaveCount(1, { timeout: 15000 });
    await expect(notesCard).not.toContainText('Place nearby note edited');
    await expect(notesCard).toContainText('Second nearby note');
    await expect(page.locator('.place-map-container .gp-note-marker-wrapper')).toHaveCount(1, { timeout: 15000 });
    await expect.poll(async () => {
      const note = await findGeoPulseNoteById(dbManager, editableNoteId);
      return note?.deleted_at !== null;
    }, { timeout: 10000 }).toBe(true);
  });
});
