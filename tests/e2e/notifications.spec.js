import { test, expect } from '../fixtures/isolated-fixture.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';

async function insertGeofenceRule(dbManager, ownerUserId, name) {
  const result = await dbManager.client.query(`
    INSERT INTO geofence_rules (
      owner_user_id,
      name,
      north_east_lat,
      north_east_lon,
      south_west_lat,
      south_west_lon,
      monitor_enter,
      monitor_leave,
      cooldown_seconds,
      status,
      created_at,
      updated_at
    )
    VALUES (
      $1::uuid,
      $2::text,
      50.0,
      30.0,
      49.0,
      29.0,
      true,
      true,
      120,
      'ACTIVE',
      NOW(),
      NOW()
    )
    RETURNING id
  `, [ownerUserId, name]);

  return Number(result.rows[0].id);
}

async function insertGeofenceEvent(dbManager, {
  ownerUserId,
  subjectUserId,
  ruleId,
  eventType,
  title,
  message,
  deliveryStatus = 'SKIPPED',
  occurredAt = new Date().toISOString()
}) {
  const result = await dbManager.client.query(`
    INSERT INTO geofence_events (
      owner_user_id,
      subject_user_id,
      rule_id,
      event_type,
      occurred_at,
      title,
      message,
      subject_display_name,
      delivery_status,
      created_at,
      seen_at
    )
    SELECT
      $1::uuid,
      $2::uuid,
      $3::bigint,
      $4::text,
      $5::timestamptz,
      $6::text,
      $7::text,
      COALESCE(NULLIF(TRIM(u.full_name), ''), u.email, 'Unknown subject'),
      $8::text,
      NOW(),
      NULL
    FROM users u
    WHERE u.id = $2::uuid
    RETURNING id
  `, [ownerUserId, subjectUserId, Number(ruleId), eventType, occurredAt, title, message, deliveryStatus]);

  return Number(result.rows[0].id);
}

async function insertGeofenceNotification(dbManager, {
  ownerUserId,
  subjectUserId,
  ruleId,
  geofenceEventId,
  eventType,
  title,
  message,
  deliveryStatus = 'SKIPPED',
  occurredAt = new Date().toISOString()
}) {
  const result = await dbManager.client.query(`
    INSERT INTO user_notifications (
      owner_user_id,
      source,
      type,
      title,
      message,
      occurred_at,
      seen_at,
      delivery_status,
      object_ref,
      metadata,
      dedupe_key,
      created_at
    )
    SELECT
      $1::uuid,
      'GEOFENCE',
      CASE WHEN $4::text = 'ENTER' THEN 'GEOFENCE_ENTER' ELSE 'GEOFENCE_LEAVE' END,
      $5::text,
      $6::text,
      $7::timestamptz,
      NULL,
      $8::text,
      $9::text,
      jsonb_build_object(
        'ruleId', $3::bigint,
        'ruleName', gr.name,
        'subjectUserId', $2::uuid,
        'subjectDisplayName', COALESCE(NULLIF(TRIM(u.full_name), ''), u.email),
        'eventCode', $4::text,
        'eventVerb', CASE WHEN $4::text = 'ENTER' THEN 'entered' ELSE 'left' END
      ),
      CONCAT('geofence-event:', $9::text),
      NOW()
    FROM geofence_rules gr
    JOIN users u ON u.id = $2::uuid
    WHERE gr.id = $3::bigint
    RETURNING id
  `, [
    ownerUserId,
    subjectUserId,
    Number(ruleId),
    eventType,
    title,
    message,
    occurredAt,
    deliveryStatus,
    String(geofenceEventId)
  ]);

  return Number(result.rows[0].id);
}

async function insertImportNotification(dbManager, {
  ownerUserId,
  title,
  message,
  occurredAt = new Date().toISOString()
}) {
  const result = await dbManager.client.query(`
    INSERT INTO user_notifications (
      owner_user_id,
      source,
      type,
      title,
      message,
      occurred_at,
      seen_at,
      delivery_status,
      object_ref,
      metadata,
      dedupe_key,
      created_at
    )
    VALUES (
      $1::uuid,
      'IMPORT',
      'IMPORT_COMPLETED',
      $2::text,
      $3::text,
      $4::timestamptz,
      NULL,
      'SKIPPED',
      $5::text,
      jsonb_build_object('scope', 'e2e'),
      $6::text,
      NOW()
    )
    RETURNING id
  `, [
    ownerUserId,
    title,
    message,
    occurredAt,
    `import-ref-${Date.now()}`,
    `import-e2e-${Date.now()}-${Math.floor(Math.random() * 100000)}`
  ]);

  return Number(result.rows[0].id);
}

async function getGeofenceEventSeenAt(dbManager, geofenceEventId) {
  const result = await dbManager.client.query(`
    SELECT seen_at
    FROM geofence_events
    WHERE id = $1::bigint
  `, [Number(geofenceEventId)]);

  return result.rows[0]?.seen_at || null;
}

async function getNotificationSeenAtById(dbManager, notificationId) {
  const result = await dbManager.client.query(`
    SELECT seen_at
    FROM user_notifications
    WHERE id = $1::bigint
  `, [Number(notificationId)]);

  return result.rows[0]?.seen_at || null;
}

test.describe('Notification Bell', () => {
  test('should mark single geofence notification seen from bell and sync geofence event', async ({ page, isolatedUsers, dbManager }) => {
    const { geofencesPage, user } = await TestSetupHelper.loginAndNavigateToGeofencesPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers, { fullName: 'Bell Owner Single' })
    );

    const ruleId = await insertGeofenceRule(dbManager, user.id, 'Bell Rule Single');
    const geofenceEventId = await insertGeofenceEvent(dbManager, {
      ownerUserId: user.id,
      subjectUserId: user.id,
      ruleId,
      eventType: 'ENTER',
      title: 'Bell ENTER single',
      message: 'Bell single geofence notification.'
    });
    const notificationId = await insertGeofenceNotification(dbManager, {
      ownerUserId: user.id,
      subjectUserId: user.id,
      ruleId,
      geofenceEventId,
      eventType: 'ENTER',
      title: 'Bell ENTER single',
      message: 'Bell single geofence notification.'
    });

    await page.bringToFront();
    await geofencesPage.waitForBellBadgeCount(1, 25000);
    await geofencesPage.openNotificationBell();
    await geofencesPage.waitForNotificationItemVisible('Bell ENTER single', 10000);

    await geofencesPage.markNotificationSeenByTitle('Bell ENTER single');
    await geofencesPage.expectBellBadgeHidden(10000);
    await geofencesPage.waitForNotificationItemHidden('Bell ENTER single', 10000);

    const eventSeenAt = await getGeofenceEventSeenAt(dbManager, geofenceEventId);
    const notificationSeenAt = await getNotificationSeenAtById(dbManager, notificationId);

    expect(eventSeenAt).not.toBeNull();
    expect(notificationSeenAt).not.toBeNull();
  });

  test('should mark all seen from bell and sync geofence events while covering import notifications', async ({ page, isolatedUsers, dbManager }) => {
    const { geofencesPage, user } = await TestSetupHelper.loginAndNavigateToGeofencesPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers, { fullName: 'Bell Owner All' })
    );

    const ruleId = await insertGeofenceRule(dbManager, user.id, 'Bell Rule All');
    const geofenceEventIdOne = await insertGeofenceEvent(dbManager, {
      ownerUserId: user.id,
      subjectUserId: user.id,
      ruleId,
      eventType: 'ENTER',
      title: 'Bell ENTER all',
      message: 'Bell ENTER message.'
    });
    const geofenceEventIdTwo = await insertGeofenceEvent(dbManager, {
      ownerUserId: user.id,
      subjectUserId: user.id,
      ruleId,
      eventType: 'LEAVE',
      title: 'Bell LEAVE all',
      message: 'Bell LEAVE message.'
    });

    const geofenceNotificationOne = await insertGeofenceNotification(dbManager, {
      ownerUserId: user.id,
      subjectUserId: user.id,
      ruleId,
      geofenceEventId: geofenceEventIdOne,
      eventType: 'ENTER',
      title: 'Bell ENTER all',
      message: 'Bell ENTER message.'
    });
    const geofenceNotificationTwo = await insertGeofenceNotification(dbManager, {
      ownerUserId: user.id,
      subjectUserId: user.id,
      ruleId,
      geofenceEventId: geofenceEventIdTwo,
      eventType: 'LEAVE',
      title: 'Bell LEAVE all',
      message: 'Bell LEAVE message.'
    });
    const importNotificationId = await insertImportNotification(dbManager, {
      ownerUserId: user.id,
      title: 'Bell IMPORT all',
      message: 'Import notification message.'
    });

    await page.bringToFront();
    await geofencesPage.waitForBellBadgeCount(3, 25000);
    await geofencesPage.openNotificationBell();
    await geofencesPage.markAllNotificationsSeen();
    await geofencesPage.expectBellBadgeHidden(10000);

    expect(await getGeofenceEventSeenAt(dbManager, geofenceEventIdOne)).not.toBeNull();
    expect(await getGeofenceEventSeenAt(dbManager, geofenceEventIdTwo)).not.toBeNull();
    expect(await getNotificationSeenAtById(dbManager, geofenceNotificationOne)).not.toBeNull();
    expect(await getNotificationSeenAtById(dbManager, geofenceNotificationTwo)).not.toBeNull();
    expect(await getNotificationSeenAtById(dbManager, importNotificationId)).not.toBeNull();

    await geofencesPage.switchToTab('Events');
    await geofencesPage.expectEventsUnreadCleared();
  });

  test('should mark single import notification seen from bell', async ({ page, isolatedUsers, dbManager }) => {
    const { geofencesPage, user } = await TestSetupHelper.loginAndNavigateToGeofencesPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers, { fullName: 'Bell Owner Import' })
    );

    const importNotificationId = await insertImportNotification(dbManager, {
      ownerUserId: user.id,
      title: 'Bell IMPORT single',
      message: 'Import-only notification.'
    });

    await page.bringToFront();
    await geofencesPage.waitForBellBadgeCount(1, 25000);
    await geofencesPage.openNotificationBell();
    await geofencesPage.waitForNotificationItemVisible('Bell IMPORT single', 10000);

    await geofencesPage.markNotificationSeenByTitle('Bell IMPORT single');
    await geofencesPage.expectBellBadgeHidden(10000);
    await geofencesPage.waitForNotificationItemHidden('Bell IMPORT single', 10000);

    expect(await getNotificationSeenAtById(dbManager, importNotificationId)).not.toBeNull();
  });
});
