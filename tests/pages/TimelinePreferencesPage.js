import { expect } from '@playwright/test';

export class TimelinePreferencesPage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      pageTitle: 'h1.gp-page-title:has-text("Timeline Preferences")',
      settingsNav: '.settings-nav',
      unsavedWarning: '.unsaved-warning',
      confirmDialog: '.p-confirmdialog:visible',
      regenerationModal: '.timeline-regeneration-modal',
      successToast: '.p-toast-message-success',
      infoToast: '.p-toast-message-info',
      jobsPageTitle: 'h1.gp-page-title:has-text("Timeline Generation Jobs")',
      jobDetailsTitle: 'h1.gp-page-title:has-text("Timeline Generation Progress")',
      historyJobCard: '.history-job-card',
      noActiveJobCard: '.no-job-card',
      jobStatusTitle: '.status-title',
      jobIdLabel: '.job-id',
    };
  }

  async navigate(tab = null) {
    await this.page.goto(tab ? `/app/timeline/preferences?tab=${tab}` : '/app/timeline/preferences');
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 10000 });
    // Inputs render only once the effective preferences have loaded.
    await this.page.locator('[data-setting-id] .number-input input').first()
      .waitFor({ state: 'visible', timeout: 10000 });
  }

  // ---------------------------------------------------------------------------
  // Settings
  // ---------------------------------------------------------------------------

  numberInput(settingId) {
    return this.page.locator(`[data-setting-id="${settingId}"] .number-input input`).first();
  }

  async selectTab(label) {
    await this.page.locator(this.selectors.settingsNav).getByRole('button', { name: label }).click();
  }

  async getNumberValue(settingId) {
    const raw = await this.numberInput(settingId).inputValue();
    return Number(raw.replace(/[^\d.,-]/g, '').replace(',', '.'));
  }

  async setNumberValue(settingId, value) {
    const input = this.numberInput(settingId);
    await input.click();
    await input.fill(String(value));
    // PrimeVue InputNumber commits the parsed value on blur.
    await input.press('Tab');
  }

  saveButton() {
    return this.page.locator('.gp-page-actions').getByRole('button', { name: 'Save Changes' });
  }

  regenerateButton() {
    return this.page.locator('.gp-page-actions').getByRole('button', { name: 'Regenerate Timeline' });
  }

  unsavedWarning() {
    return this.page.locator(this.selectors.unsavedWarning);
  }

  async clickSave() {
    await this.saveButton().click();
  }

  async clickDiscard() {
    await this.unsavedWarning().getByRole('button', { name: 'Discard' }).click();
  }

  async openMoreMenuItem(label) {
    await this.page.locator('.gp-page-actions').getByRole('button', { name: 'More' }).click();
    await this.page.locator('.p-menu:visible').getByRole('menuitem', { name: label }).click();
  }

  // ---------------------------------------------------------------------------
  // Dialogs
  // ---------------------------------------------------------------------------

  async expectConfirmDialog(header) {
    const dialog = this.page.locator(this.selectors.confirmDialog);
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await expect(dialog.locator('.p-dialog-title')).toHaveText(header);
    return dialog;
  }

  async confirmDialog(header, acceptLabel) {
    const dialog = await this.expectConfirmDialog(header);
    await dialog.getByRole('button', { name: acceptLabel, exact: true }).click();
    await expect(dialog).toBeHidden({ timeout: 5000 });
  }

  async waitForRegenerationToFinish(timeout = 60000) {
    await this.page.waitForSelector(this.selectors.regenerationModal, { state: 'visible', timeout: 10000 });
    await this.page.waitForSelector(this.selectors.regenerationModal, { state: 'hidden', timeout });
  }

  async waitForToast(selector, text, timeout = 10000) {
    const toast = this.page.locator(selector).filter({ hasText: text }).first();
    await expect(toast).toBeVisible({ timeout });
  }

  /**
   * Leave the page through an in-app router link, so the unsaved-changes guard runs.
   */
  async clickProfileInUserMenu() {
    await this.page.locator('.gp-user-menu-trigger:visible').first().click();
    await this.page.locator('a.gp-user-menu-item[href="/app/profile"]:visible').click();
  }

  // ---------------------------------------------------------------------------
  // Timeline jobs pages
  // ---------------------------------------------------------------------------

  async navigateToJobsList() {
    await this.page.goto('/app/timeline/jobs');
    await this.page.waitForSelector(this.selectors.jobsPageTitle, { timeout: 10000 });
  }

  historyJobCards() {
    return this.page.locator(this.selectors.historyJobCard);
  }

  // ---------------------------------------------------------------------------
  // Database helpers
  // ---------------------------------------------------------------------------

  static async getStoredPreferences(dbManager, userId) {
    const result = await dbManager.client.query(
      'SELECT timeline_preferences FROM users WHERE id = $1',
      [userId]
    );
    return result.rows[0]?.timeline_preferences ?? null;
  }

  static async setStoredPreferences(dbManager, userId, preferences) {
    await dbManager.client.query(
      'UPDATE users SET timeline_preferences = $2::jsonb WHERE id = $1',
      [userId, preferences === null ? null : JSON.stringify(preferences)]
    );
  }

  static async getStayCount(dbManager, userId) {
    const result = await dbManager.client.query(
      'SELECT COUNT(*)::int AS count FROM timeline_stays WHERE user_id = $1',
      [userId]
    );
    return result.rows[0].count;
  }
}
