import { expect } from '@playwright/test';

/**
 * Page object for the admin user management pages:
 * /app/admin/users, /app/admin/users/:id and /app/admin/audit-logs.
 */
export class AdminUsersPage {
  constructor(page) {
    this.page = page;
  }

  get selectors() {
    return {
      // Users list
      searchInput: 'input[placeholder="Search users..."]',
      desktopTableRows: '.desktop-only tbody tr',

      // User details
      detailsTitle: 'h1.gp-page-title:has-text("User Details")',
      userHeader: '.user-header-card',
      userHeaderName: '.user-header-info h2',
      userHeaderEmail: '.user-header-info .user-email',
      userBadges: '.user-header-info .user-badges .p-tag',
      card: '.card',
      cardTitle: '.card-title h3',
      infoItem: '.info-item',
      actionsCard: '.actions-card',
      apiTokensCard: '.api-tokens-card',
      apiTokenRows: '.api-tokens-card tbody tr',
      dialog: '.p-dialog',
      toast: '.p-toast-message',

      // Audit logs
      auditActionTypeSelect: '#actionType',
      auditTargetTypeSelect: '#targetType',
      auditAdminEmailInput: '#adminEmail',
      auditTableRows: '.desktop-only tbody tr',
    };
  }

  // ---------------------------------------------------------------------------
  // Users list
  // ---------------------------------------------------------------------------

  async navigateToUsers() {
    const usersLoaded = this.waitForUsersResponse();
    await this.page.goto('/app/admin/users');
    await usersLoaded;
    await expect(this.page.locator('h1.gp-page-title')).toHaveText('User Management');
  }

  waitForUsersResponse(predicate = () => true) {
    return this.page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/admin/users'
        && response.request().method() === 'GET'
        && predicate(url);
    });
  }

  /**
   * Type into the users search box and wait for the debounced search request to complete.
   */
  async searchUsers(query) {
    const searchLoaded = this.waitForUsersResponse(url => url.searchParams.get('search') === query);
    await this.page.fill(this.selectors.searchInput, query);
    const response = await searchLoaded;
    expect(response.ok()).toBe(true);
  }

  userRows() {
    return this.page.locator(this.selectors.desktopTableRows);
  }

  userRow(email) {
    return this.userRows().filter({ hasText: email });
  }

  async openUserFromList(email) {
    await this.userRow(email).getByRole('link', { name: email, exact: true }).click();
    await this.waitForUserDetailsLoaded();
  }

  // ---------------------------------------------------------------------------
  // User details
  // ---------------------------------------------------------------------------

  async navigateToUserDetails(userId) {
    await this.page.goto(`/app/admin/users/${userId}`);
    await this.waitForUserDetailsLoaded();
  }

  async waitForUserDetailsLoaded() {
    await this.page.waitForURL(url => /^\/app\/admin\/users\/[0-9a-f-]{36}$/.test(url.pathname));
    await expect(this.page.locator(this.selectors.detailsTitle)).toBeVisible();
    await expect(this.page.locator(this.selectors.userHeader)).toBeVisible();
    await expect(this.page.locator(`${this.selectors.apiTokensCard} .p-datatable-mask`)).toHaveCount(0);
  }

  headerName() {
    return this.page.locator(this.selectors.userHeaderName);
  }

  headerEmail() {
    return this.page.locator(this.selectors.userHeaderEmail);
  }

  headerBadges() {
    return this.page.locator(this.selectors.userBadges);
  }

  card(title) {
    return this.page.locator(this.selectors.card).filter({
      has: this.page.locator(this.selectors.cardTitle, { hasText: title }),
    });
  }

  /**
   * Value cell of an info item (e.g. "Authentication", "GPS Points") inside the given card.
   */
  infoValue(cardTitle, label) {
    return this.card(cardTitle)
      .locator(this.selectors.infoItem)
      .filter({ has: this.page.locator('label', { hasText: label }) })
      .locator('.info-value');
  }

  actionButton(label) {
    return this.page.locator(this.selectors.actionsCard).getByRole('button', { name: label, exact: true });
  }

  toast(text) {
    // On admin pages AppLayout renders the only ungrouped <Toast />; .first() just guards against a repeated message still on screen.
    return this.page.locator(this.selectors.toast).filter({ hasText: text }).first();
  }

  dialog(header) {
    return this.page.locator(this.selectors.dialog).filter({
      has: this.page.locator('.p-dialog-title', { hasText: header }),
    });
  }

  waitForAdminUserRequest(userId, method, suffix = '') {
    return this.page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === `/api/v1/admin/users/${userId}${suffix}`
        && response.request().method() === method;
    });
  }

  async disableUser(userId) {
    const updated = this.waitForAdminUserRequest(userId, 'PUT', '/status');
    await this.actionButton('Disable User').click();
    expect((await updated).ok()).toBe(true);
    await expect(this.toast('User disabled')).toBeVisible();
  }

  async enableUser(userId) {
    const updated = this.waitForAdminUserRequest(userId, 'PUT', '/status');
    await this.actionButton('Enable User').click();
    expect((await updated).ok()).toBe(true);
    await expect(this.toast('User enabled')).toBeVisible();
  }

  async promoteToAdmin(userId) {
    const updated = this.waitForAdminUserRequest(userId, 'PUT', '/role');
    await this.actionButton('Promote to Admin').click();
    expect((await updated).ok()).toBe(true);
    await expect(this.actionButton('Demote to User')).toBeVisible();
  }

  async demoteToUser(userId) {
    const updated = this.waitForAdminUserRequest(userId, 'PUT', '/role');
    await this.actionButton('Demote to User').click();
    expect((await updated).ok()).toBe(true);
    await expect(this.actionButton('Promote to Admin')).toBeVisible();
  }

  /**
   * Reset the user's password and return the temporary password shown in the dialog.
   */
  async resetPassword(userId) {
    const reset = this.waitForAdminUserRequest(userId, 'POST', '/password-resets');
    await this.actionButton('Reset Password').click();
    expect((await reset).ok()).toBe(true);

    const passwordDialog = this.dialog('Password Reset');
    await expect(passwordDialog).toBeVisible();
    const tempPasswordInput = passwordDialog.locator('input[readonly]');
    await expect(tempPasswordInput).not.toHaveValue('');
    const tempPassword = await tempPasswordInput.inputValue();

    // The header X is also labelled "Close"; use the footer button.
    await passwordDialog.locator('.p-dialog-footer').getByRole('button', { name: 'Close', exact: true }).click();
    await expect(passwordDialog).toBeHidden();
    return tempPassword;
  }

  async deleteUser(userId) {
    await this.actionButton('Delete User').click();
    const confirmDialog = this.dialog('Confirm Delete');
    await expect(confirmDialog).toBeVisible();

    const deleted = this.waitForAdminUserRequest(userId, 'DELETE');
    await confirmDialog.getByRole('button', { name: 'Delete', exact: true }).click();
    expect((await deleted).ok()).toBe(true);
    await this.page.waitForURL(url => url.pathname === '/app/admin/users');
  }

  apiTokenRow(tokenName) {
    return this.page.locator(this.selectors.apiTokenRows).filter({ hasText: tokenName });
  }

  async revokeApiToken(tokenId, tokenName) {
    await this.apiTokenRow(tokenName).locator('button:has(.pi-ban)').click();
    const revokeDialog = this.dialog('Revoke API Token');
    await expect(revokeDialog).toBeVisible();

    const revoked = this.page.waitForResponse(response =>
      new URL(response.url()).pathname === `/api/v1/admin/api-tokens/${tokenId}`
      && response.request().method() === 'DELETE'
    );
    await revokeDialog.getByRole('button', { name: 'Revoke', exact: true }).click();
    expect((await revoked).ok()).toBe(true);
    await expect(revokeDialog).toBeHidden();
  }

  // ---------------------------------------------------------------------------
  // Audit logs
  // ---------------------------------------------------------------------------

  async navigateToAuditLogs() {
    const logsLoaded = this.waitForAuditLogsResponse();
    await this.page.goto('/app/admin/audit-logs');
    await logsLoaded;
    await expect(this.page.locator('h1.gp-page-title')).toHaveText('Audit Logs');
  }

  waitForAuditLogsResponse(predicate = () => true) {
    return this.page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/admin/audit-logs'
        && response.request().method() === 'GET'
        && predicate(url);
    });
  }

  async selectOption(selectSelector, optionLabel) {
    await this.page.locator(selectSelector).click();
    await this.page.getByRole('option', { name: optionLabel, exact: true }).click();
  }

  /**
   * Filter by action type (option label, e.g. "User Disabled") and target type (e.g. "User").
   * Both are server-side filters, so each selection triggers a reload.
   */
  async filterAuditLogs({ actionTypeLabel, actionType, targetTypeLabel, targetType }) {
    if (targetTypeLabel) {
      const loaded = this.waitForAuditLogsResponse(url => url.searchParams.get('targetType') === targetType);
      await this.selectOption(this.selectors.auditTargetTypeSelect, targetTypeLabel);
      await loaded;
    }
    if (actionTypeLabel) {
      const loaded = this.waitForAuditLogsResponse(url => url.searchParams.get('actionType') === actionType);
      await this.selectOption(this.selectors.auditActionTypeSelect, actionTypeLabel);
      await loaded;
    }
  }

  /**
   * The admin email filter is applied client-side to the loaded page of results.
   */
  async filterAuditLogsByAdminEmail(email) {
    // The debounced input reloads the page of results before filtering it.
    const reloaded = this.waitForAuditLogsResponse();
    await this.page.fill(this.selectors.auditAdminEmailInput, email);
    await reloaded;
    await expect(this.page.locator('.desktop-only .p-datatable-mask')).toHaveCount(0);
  }

  auditRows() {
    return this.page.locator(this.selectors.auditTableRows);
  }

  auditRowForTarget(targetId) {
    return this.auditRows().filter({ hasText: targetId });
  }
}
