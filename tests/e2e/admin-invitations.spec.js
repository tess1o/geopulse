import { randomUUID } from 'crypto';
import { test, expect } from '../fixtures/isolated-fixture.js';
import { LoginPage } from '../pages/LoginPage.js';
import { TestHelpers } from '../utils/test-helpers.js';

test.describe('Admin Invitations', () => {
  test('should keep USED invitation and show "Deleted user" after deleting the invited user', async ({ page, isolatedUsers, dbManager }) => {
    const loginPage = new LoginPage(page);

    const adminUser = await isolatedUsers.create(page);
    const invitedUser = await isolatedUsers.create(page);

    await dbManager.client.query(
      "UPDATE users SET role = 'ADMIN' WHERE email = $1",
      [adminUser.email]
    );

    const adminDbUser = await dbManager.getUserByEmail(adminUser.email);
    const invitedDbUser = await dbManager.getUserByEmail(invitedUser.email);

    expect(adminDbUser).toBeTruthy();
    expect(invitedDbUser).toBeTruthy();

    const invitationId = randomUUID();
    const invitationToken = `e2e-invite-${randomUUID().replace(/-/g, '')}`;

    await dbManager.client.query(
      `INSERT INTO user_invitations
        (id, token, created_by, created_at, expires_at, used, used_at, used_by, revoked, revoked_at)
       VALUES
        ($1, $2, $3, NOW(), NOW() + interval '7 days', true, NOW(), $4, false, NULL)`,
      [invitationId, invitationToken, adminDbUser.id, invitedDbUser.id]
    );

    await loginPage.navigate();
    await loginPage.login(adminUser.email, adminUser.password);
    await TestHelpers.waitForNavigation(page, '**/app/timeline');

    const deleteResponse = await page.request.delete(`/api/admin/users/${invitedDbUser.id}`);
    expect(deleteResponse.ok()).toBe(true);

    const deletedUserResult = await dbManager.client.query(
      'SELECT id FROM users WHERE id = $1',
      [invitedDbUser.id]
    );
    expect(deletedUserResult.rowCount).toBe(0);

    const invitationResult = await dbManager.client.query(
      'SELECT used, used_by FROM user_invitations WHERE id = $1',
      [invitationId]
    );
    expect(invitationResult.rowCount).toBe(1);
    expect(invitationResult.rows[0].used).toBe(true);
    expect(invitationResult.rows[0].used_by).toBeNull();

    await page.goto('/app/admin/invitations');
    await page.waitForLoadState('networkidle');

    const tokenPreview = `${invitationToken.substring(0, 12)}...`;
    const invitationRow = page.locator('tbody tr').filter({ hasText: tokenPreview }).first();

    await expect(invitationRow).toBeVisible();
    await expect(invitationRow).toContainText('USED');
    await expect(invitationRow).toContainText('Deleted user');
  });
});
