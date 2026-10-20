import { randomUUID } from 'crypto';
import { test, expect } from '../fixtures/isolated-fixture.js';
import { LoginPage } from '../pages/LoginPage.js';
import { AdminUsersPage } from '../pages/AdminUsersPage.js';
import { TestHelpers } from '../utils/test-helpers.js';

// Other workers create users and admin audit entries concurrently, so every assertion here is scoped to
// the isolated users created by the test (unique emails/ids) and the admin that performed the action.

const loginAs = async (page, user) => {
  const loginPage = new LoginPage(page);
  await loginPage.navigate();
  await loginPage.login(user.email, user.password);
  await TestHelpers.waitForNavigation(page, '**/app/timeline');
};

// Leave the app before dropping the session cookies so in-flight requests don't redirect mid-navigation.
const logout = async (page) => {
  await page.goto('about:blank');
  await page.context().clearCookies();
};

/**
 * Create an isolated user, promote it to ADMIN (before login so the session carries the role) and sign in.
 */
const loginAsNewAdmin = async (page, isolatedUsers, dbManager) => {
  const admin = await isolatedUsers.create(page);
  await dbManager.client.query("UPDATE users SET role = 'ADMIN' WHERE email = $1", [admin.email]);
  const adminDbUser = await dbManager.getUserByEmail(admin.email);
  expect(adminDbUser.role).toBe('ADMIN');

  await loginAs(page, admin);
  return { admin, adminId: adminDbUser.id };
};

const expectLoginRejected = async (page, user, password) => {
  const loginPage = new LoginPage(page);
  await loginPage.navigate();
  await loginPage.login(user.email, password);
  await expect(page.locator(loginPage.getErrorSelector())).toBeVisible();
  expect(new URL(page.url()).pathname).toBe('/login');
};

const auditActionsFor = async (dbManager, adminId, targetId) => {
  const result = await dbManager.client.query(
    `SELECT action_type, target_type, details
       FROM audit_log
      WHERE admin_user_id = $1 AND target_id = $2
      ORDER BY id`,
    [adminId, String(targetId)]
  );
  return result.rows;
};

/**
 * Filter the audit log page down to one action type for USER targets and to this admin's email, then expect
 * a row for the target user.
 */
const expectAuditLogRow = async (adminUsersPage, { adminEmail, targetId, actionType, actionTypeLabel }) => {
  await adminUsersPage.navigateToAuditLogs();
  await adminUsersPage.filterAuditLogs({
    targetType: 'USER',
    targetTypeLabel: 'User',
    actionType,
    actionTypeLabel,
  });
  await adminUsersPage.filterAuditLogsByAdminEmail(adminEmail);

  const row = adminUsersPage.auditRowForTarget(targetId);
  await expect(row.first()).toBeVisible();
  await expect(row.first()).toContainText(adminEmail);
  await expect(row.first()).toContainText(actionTypeLabel);

  // Every visible row belongs to this admin and this action type.
  const rows = adminUsersPage.auditRows();
  const rowCount = await rows.count();
  for (let i = 0; i < rowCount; i += 1) {
    await expect(rows.nth(i)).toContainText(adminEmail);
    await expect(rows.nth(i)).toContainText(actionTypeLabel);
  }
};

test.describe('Admin Users', () => {
  test('should redirect a non-admin user away from admin pages', async ({ page, isolatedUsers }) => {
    const regularUser = await isolatedUsers.create(page);
    await loginAs(page, regularUser);

    for (const adminPath of ['/app/admin', '/app/admin/users', '/app/admin/audit-logs']) {
      await page.goto(adminPath, { waitUntil: 'commit' });
      await TestHelpers.waitForNavigation(page, url => url.pathname === '/app/timeline');
    }

    // The backend enforces the role as well.
    const usersResponse = await page.request.get('/api/v1/admin/users');
    expect(usersResponse.status()).toBe(403);
  });

  test('should find a user by email and show their details and API tokens', async ({ page, isolatedUsers, dbManager }) => {
    const adminUsersPage = new AdminUsersPage(page);
    const targetUser = await isolatedUsers.create(page);
    const targetDbUser = await dbManager.getUserByEmail(targetUser.email);

    const tokenId = randomUUID();
    const tokenName = `e2e-admin-token-${tokenId.slice(0, 8)}`;
    await dbManager.client.query(
      `INSERT INTO user_api_tokens (id, user_id, name, token_hash, token_prefix, token_suffix, created_at)
       VALUES ($1, $2, $3, $4, $5, $6, NOW())`,
      [
        tokenId,
        targetDbUser.id,
        tokenName,
        `${randomUUID()}${randomUUID()}`.replace(/-/g, ''),
        'gp_e2eadm',
        tokenId.slice(-4),
      ]
    );

    const { adminId } = await loginAsNewAdmin(page, isolatedUsers, dbManager);

    await adminUsersPage.navigateToUsers();
    await adminUsersPage.searchUsers(targetUser.email);

    const row = adminUsersPage.userRow(targetUser.email);
    await expect(adminUsersPage.userRows()).toHaveCount(1);
    await expect(row).toContainText(targetUser.fullName);
    await expect(row).toContainText('USER');
    await expect(row).toContainText('Active');

    await adminUsersPage.openUserFromList(targetUser.email);
    expect(new URL(page.url()).pathname).toBe(`/app/admin/users/${targetDbUser.id}`);

    // Header
    await expect(adminUsersPage.headerName()).toHaveText(targetUser.fullName);
    await expect(adminUsersPage.headerEmail()).toHaveText(targetUser.email);
    await expect(adminUsersPage.headerBadges()).toHaveText(['USER', 'Active']);

    // Info and stats cards
    await expect(adminUsersPage.infoValue('User Information', 'Authentication')).toHaveText('Password');
    await expect(adminUsersPage.infoValue('User Information', 'Timezone')).toHaveText(targetDbUser.timezone);
    await expect(adminUsersPage.infoValue('Activity & Statistics', 'GPS Points')).toHaveText('0');
    await expect(adminUsersPage.infoValue('Activity & Statistics', 'Last GPS Point')).toHaveText('Never');

    // API tokens card
    await expect(adminUsersPage.card('API Tokens')).toBeVisible();
    const tokenRow = adminUsersPage.apiTokenRow(tokenName);
    await expect(tokenRow).toHaveCount(1);
    await expect(tokenRow).toContainText(`gp_e2eadm...${tokenId.slice(-4)}`);
    await expect(tokenRow).toContainText('Active');

    await adminUsersPage.revokeApiToken(tokenId, tokenName);
    await expect(tokenRow).toContainText('Revoked');
    await expect(tokenRow.locator('button:has(.pi-ban)')).toBeDisabled();

    const tokenResult = await dbManager.client.query(
      'SELECT revoked_at, revoked_by FROM user_api_tokens WHERE id = $1',
      [tokenId]
    );
    expect(tokenResult.rows[0].revoked_at).not.toBeNull();
    expect(tokenResult.rows[0].revoked_by).toBe(adminId);
  });

  test('should disable and re-enable a user, blocking sign-in while disabled', async ({ page, isolatedUsers, dbManager }) => {
    const adminUsersPage = new AdminUsersPage(page);
    const targetUser = await isolatedUsers.create(page);
    const targetDbUser = await dbManager.getUserByEmail(targetUser.email);
    const { admin, adminId } = await loginAsNewAdmin(page, isolatedUsers, dbManager);

    await adminUsersPage.navigateToUserDetails(targetDbUser.id);
    await adminUsersPage.disableUser(targetDbUser.id);
    await expect(adminUsersPage.headerBadges()).toHaveText(['USER', 'Disabled']);
    await expect(adminUsersPage.actionButton('Enable User')).toBeVisible();

    let dbUser = await dbManager.getUserByEmail(targetUser.email);
    expect(dbUser.is_active).toBe(false);

    // A disabled user cannot sign in.
    await logout(page);
    await expectLoginRejected(page, targetUser, targetUser.password);

    await loginAs(page, admin);
    await expectAuditLogRow(adminUsersPage, {
      adminEmail: admin.email,
      targetId: targetDbUser.id,
      actionType: 'USER_DISABLED',
      actionTypeLabel: 'User Disabled',
    });

    await adminUsersPage.navigateToUserDetails(targetDbUser.id);
    await expect(adminUsersPage.headerBadges()).toHaveText(['USER', 'Disabled']);
    await adminUsersPage.enableUser(targetDbUser.id);
    await expect(adminUsersPage.headerBadges()).toHaveText(['USER', 'Active']);

    dbUser = await dbManager.getUserByEmail(targetUser.email);
    expect(dbUser.is_active).toBe(true);

    await expectAuditLogRow(adminUsersPage, {
      adminEmail: admin.email,
      targetId: targetDbUser.id,
      actionType: 'USER_ENABLED',
      actionTypeLabel: 'User Enabled',
    });

    const auditRows = await auditActionsFor(dbManager, adminId, targetDbUser.id);
    expect(auditRows.map(r => r.action_type)).toEqual(['USER_DISABLED', 'USER_ENABLED']);
    expect(auditRows.every(r => r.target_type === 'USER')).toBe(true);

    // Re-enabled user can sign in again.
    await logout(page);
    await loginAs(page, targetUser);
  });

  test('should change a user role and reset their password', async ({ page, isolatedUsers, dbManager }) => {
    const adminUsersPage = new AdminUsersPage(page);
    const targetUser = await isolatedUsers.create(page);
    const targetDbUser = await dbManager.getUserByEmail(targetUser.email);
    const { admin, adminId } = await loginAsNewAdmin(page, isolatedUsers, dbManager);

    await adminUsersPage.navigateToUserDetails(targetDbUser.id);

    await adminUsersPage.promoteToAdmin(targetDbUser.id);
    await expect(adminUsersPage.toast('User role changed to ADMIN')).toBeVisible();
    await expect(adminUsersPage.headerBadges().first()).toHaveText('ADMIN');
    expect((await dbManager.getUserByEmail(targetUser.email)).role).toBe('ADMIN');

    await adminUsersPage.demoteToUser(targetDbUser.id);
    await expect(adminUsersPage.toast('User role changed to USER')).toBeVisible();
    await expect(adminUsersPage.headerBadges().first()).toHaveText('USER');
    expect((await dbManager.getUserByEmail(targetUser.email)).role).toBe('USER');

    const tempPassword = await adminUsersPage.resetPassword(targetDbUser.id);
    expect(tempPassword).not.toBe(targetUser.password);
    expect((await dbManager.getUserByEmail(targetUser.email)).password_hash).not.toBe(targetDbUser.password_hash);

    const auditRows = await auditActionsFor(dbManager, adminId, targetDbUser.id);
    expect(auditRows.map(r => r.action_type)).toEqual([
      'USER_ROLE_CHANGED',
      'USER_ROLE_CHANGED',
      'USER_PASSWORD_RESET',
    ]);
    expect(auditRows.every(r => r.target_type === 'USER')).toBe(true);
    expect(JSON.stringify(auditRows[0].details)).toContain('ADMIN');

    await expectAuditLogRow(adminUsersPage, {
      adminEmail: admin.email,
      targetId: targetDbUser.id,
      actionType: 'USER_ROLE_CHANGED',
      actionTypeLabel: 'User Role Changed',
    });
    await expect(adminUsersPage.auditRowForTarget(targetDbUser.id)).toHaveCount(2);

    await expectAuditLogRow(adminUsersPage, {
      adminEmail: admin.email,
      targetId: targetDbUser.id,
      actionType: 'USER_PASSWORD_RESET',
      actionTypeLabel: 'User Password Reset',
    });

    // The old password stops working; the temporary one signs in.
    await logout(page);
    await expectLoginRejected(page, targetUser, targetUser.password);
    await logout(page);
    await loginAs(page, { email: targetUser.email, password: tempPassword });
  });

  test('should delete a user from the details page', async ({ page, isolatedUsers, dbManager }) => {
    const adminUsersPage = new AdminUsersPage(page);
    const targetUser = await isolatedUsers.create(page);
    const targetDbUser = await dbManager.getUserByEmail(targetUser.email);
    const { admin, adminId } = await loginAsNewAdmin(page, isolatedUsers, dbManager);

    await adminUsersPage.navigateToUserDetails(targetDbUser.id);
    await adminUsersPage.deleteUser(targetDbUser.id);

    const deletedResult = await dbManager.client.query('SELECT id FROM users WHERE id = $1', [targetDbUser.id]);
    expect(deletedResult.rowCount).toBe(0);

    // The deleted user no longer appears in the list.
    await adminUsersPage.searchUsers(targetUser.email);
    await expect(adminUsersPage.userRow(targetUser.email)).toHaveCount(0);

    const auditRows = await auditActionsFor(dbManager, adminId, targetDbUser.id);
    expect(auditRows.map(r => r.action_type)).toEqual(['USER_DELETED']);
    expect(JSON.stringify(auditRows[0].details)).toContain(targetUser.email);

    await expectAuditLogRow(adminUsersPage, {
      adminEmail: admin.email,
      targetId: targetDbUser.id,
      actionType: 'USER_DELETED',
      actionTypeLabel: 'User Deleted',
    });
  });

  test('should not allow an admin to disable or delete their own account', async ({ page, isolatedUsers, dbManager }) => {
    const adminUsersPage = new AdminUsersPage(page);
    const { adminId } = await loginAsNewAdmin(page, isolatedUsers, dbManager);

    await adminUsersPage.navigateToUserDetails(adminId);
    await expect(adminUsersPage.actionButton('Disable User')).toBeDisabled();
    await expect(adminUsersPage.actionButton('Delete User')).toBeDisabled();
    await expect(page.locator('.warning-message')).toContainText('You cannot disable or delete your own account.');
  });
});
