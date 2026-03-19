import { test, expect } from '../fixtures/isolated-fixture.js';
import { TestSetupHelper } from '../utils/test-setup-helper.js';
import { UserFactory } from '../utils/user-factory.js';
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js';

async function getGeofenceRuleByName(dbManager, ownerUserId, ruleName) {
  const result = await dbManager.client.query(`
    SELECT id, owner_user_id, subject_user_id, name
    FROM geofence_rules
    WHERE owner_user_id = $1 AND name = $2
    ORDER BY id DESC
    LIMIT 1
  `, [ownerUserId, ruleName]);
  return result.rows[0] || null;
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
  const normalizedRuleId = Number(ruleId);
  if (!Number.isFinite(normalizedRuleId)) {
    throw new Error(`insertGeofenceEvent requires numeric ruleId, got: ${ruleId}`);
  }

  const result = await dbManager.client.query(`
    INSERT INTO geofence_events (
      owner_user_id,
      subject_user_id,
      rule_id,
      event_type,
      occurred_at,
      title,
      message,
      delivery_status,
      created_at,
      seen_at
    )
    VALUES ($1::uuid, $2::uuid, $3::bigint, $4::text, $5::timestamptz, $6::text, $7::text, $8::text, NOW(), NULL)
    RETURNING id
  `, [ownerUserId, subjectUserId, normalizedRuleId, eventType, occurredAt, title, message, deliveryStatus]);

  const geofenceEventId = Number(result.rows[0].id);

  await dbManager.client.query(`
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
      CASE WHEN $4 = 'ENTER' THEN 'GEOFENCE_ENTER' ELSE 'GEOFENCE_LEAVE' END,
      $6::text,
      $7::text,
      $5::timestamptz,
      NULL,
      $8::text,
      $9::text,
      jsonb_build_object(
        'ruleId', $3::bigint,
        'ruleName', gr.name,
        'subjectUserId', $2::uuid,
        'subjectDisplayName', COALESCE(NULLIF(TRIM(u.full_name), ''), u.email),
        'eventCode', $4::text,
        'eventVerb', CASE WHEN $4 = 'ENTER' THEN 'entered' ELSE 'left' END
      ),
      CONCAT('geofence-event:', $9),
      NOW()
    FROM geofence_rules gr
    JOIN users u ON u.id = $2::uuid
    WHERE gr.id = $3::bigint
  `, [ownerUserId, subjectUserId, normalizedRuleId, eventType, occurredAt, title, message, deliveryStatus, geofenceEventId]);

  return geofenceEventId;
}

test.describe('Geofences UI', () => {
  test('should load Geofences page and show seeded default templates', async ({ page, isolatedUsers, dbManager }) => {
    const { geofencesPage } = await TestSetupHelper.loginAndNavigateToGeofencesPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    expect(await geofencesPage.isOnGeofencesPage()).toBe(true);

    await geofencesPage.switchToTab('Templates');
    expect(await geofencesPage.templateRowExists('In-App Enter (Default)')).toBe(true);
    expect(await geofencesPage.templateRowExists('In-App Leave (Default)')).toBe(true);
  });

  test('should support rules CRUD and only allow live-sharing friends as subjects', async ({ page, isolatedUsers, dbManager }) => {
    const ownerData = createManagedUser(isolatedUsers, { fullName: 'Geo Owner' });
    const friendVisibleData = createManagedUser(isolatedUsers, { fullName: 'Live Friend' });
    const friendHiddenData = createManagedUser(isolatedUsers, { fullName: 'NoLive Friend' });

    const { geofencesPage, user: owner } = await TestSetupHelper.loginAndNavigateToGeofencesPage(page, dbManager, ownerData);

    await UserFactory.createUser(page, friendVisibleData);
    await UserFactory.createUser(page, friendHiddenData);
    const friendVisible = await dbManager.getUserByEmail(friendVisibleData.email);
    const friendHidden = await dbManager.getUserByEmail(friendHiddenData.email);

    await TestSetupHelper.setupFriendship(dbManager, owner.id, friendVisible.id, {
      userToFriend: { shareLive: false, shareTimeline: false },
      friendToUser: { shareLive: true, shareTimeline: false }
    });
    await TestSetupHelper.setupFriendship(dbManager, owner.id, friendHidden.id, {
      userToFriend: { shareLive: false, shareTimeline: false },
      friendToUser: { shareLive: false, shareTimeline: false }
    });

    await page.reload();
    await geofencesPage.waitForPageLoad();

    await geofencesPage.openSelectForField('Subject');
    const subjectOptions = (await geofencesPage.getOpenSelectOptionLabels()).map(option => option.trim());
    expect(subjectOptions.some(option => option.includes(friendVisibleData.fullName))).toBe(true);
    expect(subjectOptions.some(option => option.includes(friendHiddenData.fullName))).toBe(false);
    await geofencesPage.closeOpenSelect();

    const ruleName = 'Friend Home Rule';
    const updatedRuleName = 'Office Perimeter Rule';
    await geofencesPage.fillRuleName(ruleName);
    await geofencesPage.setRuleSubject(friendVisibleData.fullName);
    await geofencesPage.clickStartRectangleDraw();
    await geofencesPage.drawRectangle(120, 80, 360, 220);
    await geofencesPage.waitForAreaSelected();
    await geofencesPage.saveRule();
    await geofencesPage.waitForSuccessToast('Rule created');
    await geofencesPage.waitForRuleRowState(ruleName, true, 15000);

    await geofencesPage.editRule(ruleName);
    await geofencesPage.fillRuleName(updatedRuleName);
    await geofencesPage.setRuleCooldown(300);
    await geofencesPage.saveRule();
    await geofencesPage.waitForSuccessToast('Rule updated');
    await geofencesPage.waitForRuleRowState(updatedRuleName, true, 15000);
    await geofencesPage.waitForRuleRowState(ruleName, false, 15000);

    await geofencesPage.deleteRule(updatedRuleName);
    await geofencesPage.acceptConfirmDialog();
    await geofencesPage.waitForSuccessToast('Rule deleted');
    await geofencesPage.waitForRuleRowState(updatedRuleName, false, 15000);
  });

  test('should support templates CRUD and reflect default enter template changes in Rules tab', async ({ page, isolatedUsers, dbManager }) => {
    const { geofencesPage } = await TestSetupHelper.loginAndNavigateToGeofencesPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    await geofencesPage.switchToTab('Templates');

    const templateName = 'Urgent Enter Template';
    const updatedTemplateName = 'Primary Arrival Template';
    await geofencesPage.fillTemplateName(templateName);
    await geofencesPage.fillTemplateDestination('discord://token@channel');
    await geofencesPage.fillTemplateTitle('{{subjectName}} {{eventVerb}} {{geofenceName}}');
    await geofencesPage.fillTemplateBody('At {{timestamp}} ({{lat}}, {{lon}})');
    await geofencesPage.toggleTemplateDefaultEnter();
    await page.waitForSelector('.p-confirmdialog', { state: 'visible', timeout: 5000 });
    await geofencesPage.acceptConfirmDialog();
    await geofencesPage.saveTemplate();
    await geofencesPage.waitForSuccessToast('Template created');
    await geofencesPage.waitForTemplateRowState(templateName, true, 15000);

    await geofencesPage.editTemplate(templateName);
    await geofencesPage.fillTemplateName(updatedTemplateName);
    await geofencesPage.fillTemplateBody('{{subjectName}} {{eventVerb}} {{geofenceName}} @ {{timestamp}}');
    await geofencesPage.saveTemplate();
    await geofencesPage.waitForSuccessToast('Template updated');
    await geofencesPage.waitForTemplateRowState(updatedTemplateName, true, 15000);

    await geofencesPage.switchToTab('Rules');
    await geofencesPage.openSelectForField('Enter Template');
    const enterTemplateOptions = (await geofencesPage.getOpenSelectOptionLabels()).map(option => option.trim());
    expect(enterTemplateOptions.some(option => option.includes(`Use default ENTER template (${updatedTemplateName})`))).toBe(true);
    await geofencesPage.closeOpenSelect();

    await geofencesPage.switchToTab('Templates');
    await geofencesPage.deleteTemplate(updatedTemplateName);
    await geofencesPage.acceptConfirmDialog();
    await geofencesPage.waitForSuccessToast('Template deleted');
    await geofencesPage.waitForTemplateRowState(updatedTemplateName, false, 15000);

    await geofencesPage.switchToTab('Rules');
    await geofencesPage.openSelectForField('Enter Template');
    const optionsAfterDelete = (await geofencesPage.getOpenSelectOptionLabels()).map(option => option.trim());
    expect(optionsAfterDelete.some(option => option.includes(updatedTemplateName))).toBe(false);
    await geofencesPage.closeOpenSelect();
  });

  test('should show unread badge, toasts and events for simulated ENTER/LEAVE geofence events', async ({ page, isolatedUsers, dbManager }) => {
    await page.addInitScript(() => {
      try {
        Object.defineProperty(document, 'hidden', {
          configurable: true,
          get: () => false
        });
      } catch {}
      try {
        document.hasFocus = () => true;
      } catch {}
    });

    const { geofencesPage, user } = await TestSetupHelper.loginAndNavigateToGeofencesPage(
      page,
      dbManager,
      createManagedUser(isolatedUsers)
    );

    const ruleName = 'Notification Rule';
    await geofencesPage.fillRuleName(ruleName);
    await geofencesPage.setRuleSubject('(Me)');
    await geofencesPage.clickStartRectangleDraw();
    await geofencesPage.drawRectangle(120, 80, 360, 220);
    await geofencesPage.waitForAreaSelected();
    await geofencesPage.saveRule();
    await geofencesPage.waitForSuccessToast('Rule created');

    const rule = await getGeofenceRuleByName(dbManager, user.id, ruleName);
    expect(rule).toBeTruthy();

    await insertGeofenceEvent(dbManager, {
      ownerUserId: user.id,
      subjectUserId: user.id,
      ruleId: rule.id,
      eventType: 'ENTER',
      title: 'E2E ENTER notification',
      message: 'Entered home geofence.'
    });

    await page.bringToFront();
    await geofencesPage.waitForBellBadgeCount(1, 25000);
    await geofencesPage.waitForNotificationToast('E2E ENTER notification', 25000);

    await insertGeofenceEvent(dbManager, {
      ownerUserId: user.id,
      subjectUserId: user.id,
      ruleId: rule.id,
      eventType: 'LEAVE',
      title: 'E2E LEAVE notification',
      message: 'Left home geofence.'
    });

    await page.bringToFront();
    await geofencesPage.waitForBellBadgeCount(2, 25000);
    await geofencesPage.waitForNotificationToast('E2E LEAVE notification', 25000);

    await geofencesPage.openNotificationBell();
    await expect(page.locator('.gp-notification-item-title', { hasText: 'E2E ENTER notification' })).toBeVisible();
    await expect(page.locator('.gp-notification-item-title', { hasText: 'E2E LEAVE notification' })).toBeVisible();
    await geofencesPage.closeNotificationBell();

    await geofencesPage.switchToTab('Events');
    await geofencesPage.waitForEventRowState('E2E ENTER notification', true, 15000);
    await geofencesPage.waitForEventRowState('E2E LEAVE notification', true, 15000);

    await geofencesPage.markAllEventsSeen();
    await geofencesPage.expectBellBadgeHidden();
  });
});
