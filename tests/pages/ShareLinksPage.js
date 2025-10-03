import { expect } from '@playwright/test';

export class ShareLinksPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Page elements
      pageTitle: 'h1:has-text("Share Links")',
      pageDescription: 'text="Create and manage shareable links to your location data"',

      // Header actions
      createLinkButton: 'button:has-text("Create New Link")',
      createFirstLinkButton: 'button:has-text("Create Your First Link")',

      // Loading state
      loadingSpinner: '.p-progress-spinner',
      loadingMessage: 'text="Loading share links..."',

      // Error state
      errorMessage: '.p-message-error',

      // Empty state
      emptyState: '.empty-state',
      emptyIcon: '.empty-icon',
      emptyStateTitle: '.empty-state h3:has-text("No share links yet")',

      // Links sections
      activeLinksSection: '.links-section:has(.section-title:has-text("Active Links"))',
      expiredLinksSection: '.links-section:has(.section-title:has-text("Expired Links"))',
      sectionTitle: '.section-title',

      // Link cards
      linkCard: '.link-card',
      activeLinkCard: '.link-card.active',
      expiredLinkCard: '.link-card.expired',
      linkTitle: '.link-title',
      linkMeta: '.link-meta',
      linkDate: '.link-date',
      linkExpires: '.link-expires',

      // Link status
      linkStatus: '.link-status',
      activeTag: '.p-tag:has-text("Active")',
      expiredTag: '.p-tag:has-text("Expired")',

      // Link details
      linkUrlSection: '.link-url-section',
      shareUrlInput: '.share-url-input',
      copyButton: 'button:has(.pi-copy)',

      // Link settings
      linkSettings: '.link-settings',
      settingItem: '.setting-item',
      settingLabel: '.setting-label',
      settingValue: '.setting-value',

      // Link actions
      editButton: 'button:has-text("Edit")',
      deleteButton: 'button:has-text("Delete")',

      // Create/Edit Dialog
      shareDialog: '.share-link-dialog',
      dialogHeader: '.p-dialog-header',
      createDialogHeader: '.p-dialog-header:has-text("Create Share Link")',
      editDialogHeader: '.p-dialog-header:has-text("Edit Share Link")',

      // Form fields
      nameInput: '#name',
      currentOnlyRadio: '#current-only',
      withHistoryRadio: '#with-history',
      expiresAtCalendar: '#expires_at',
      hasPasswordCheckbox: '#has_password',
      passwordInput: '#password',

      // Form actions
      cancelButton: 'button:has-text("Cancel")',
      submitButton: '.submit-btn',
      createSubmitButton: 'button:has-text("Create Link")',
      updateSubmitButton: 'button:has-text("Update Link")',

      // Delete Confirmation Dialog
      deleteDialog: '.delete-dialog',
      deleteDialogHeader: '.p-dialog-header:has-text("Confirm Delete")',
      warningIcon: '.warning-icon',
      deleteMessage: '.delete-message',
      confirmDeleteButton: '.delete-dialog button:has-text("Delete")',
      cancelDeleteButton: '.delete-dialog button:has-text("Cancel")',

      // Toast notifications
      toast: '.p-toast',
      toastMessage: '.p-toast-message',
      toastSummary: '.p-toast-summary',
      toastDetail: '.p-toast-detail',
      successToast: '.p-toast-message-success',
      errorToast: '.p-toast-message-error'
    };
  }

  /**
   * Navigation Methods
   */
  async navigate() {
    await this.page.goto('/app/share-links');
  }

  async isOnShareLinksPage() {
    try {
      const url = this.page.url();
      return url.includes('/app/share-links');
    } catch {
      return false;
    }
  }

  async waitForPageLoad() {
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(500);
  }

  async waitForLoadingComplete() {
    try {
      await this.page.waitForSelector(this.selectors.loadingSpinner, { state: 'hidden', timeout: 10000 });
    } catch {
      // Loading spinner might not appear for fast operations
    }
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Empty State Methods
   */
  async hasEmptyState() {
    return await this.page.locator(this.selectors.emptyState).isVisible();
  }

  async clickCreateFirstLink() {
    await this.page.locator(this.selectors.createFirstLinkButton).click();
  }

  /**
   * Link List Methods
   */
  async getActiveLinkCards() {
    return this.page.locator(this.selectors.activeLinkCard);
  }

  async getExpiredLinkCards() {
    return this.page.locator(this.selectors.expiredLinkCard);
  }

  async getActiveLinksCount() {
    return await this.page.locator(this.selectors.activeLinkCard).count();
  }

  async getExpiredLinksCount() {
    return await this.page.locator(this.selectors.expiredLinkCard).count();
  }

  async hasActiveLinksSection() {
    return await this.page.locator(this.selectors.activeLinksSection).isVisible();
  }

  async hasExpiredLinksSection() {
    return await this.page.locator(this.selectors.expiredLinksSection).isVisible();
  }

  async getLinkCardByName(name) {
    return this.page.locator(`.link-card:has(.link-title:has-text("${name}"))`);
  }

  async isLinkActive(name) {
    const card = await this.getLinkCardByName(name);
    return await card.locator(this.selectors.activeTag).isVisible();
  }

  async isLinkExpired(name) {
    const card = await this.getLinkCardByName(name);
    return await card.locator(this.selectors.expiredTag).isVisible();
  }

  /**
   * Link Details Methods
   */
  async getLinkUrl(linkName) {
    const card = await this.getLinkCardByName(linkName);
    const input = card.locator(this.selectors.shareUrlInput);
    return await input.inputValue();
  }

  async getLinkSetting(linkName, settingLabel) {
    const card = await this.getLinkCardByName(linkName);
    // Use a more flexible selector that matches partial text
    const settingItem = card.locator('.setting-item').filter({
      has: this.page.locator('.setting-label').filter({ hasText: settingLabel })
    });
    const value = await settingItem.locator(this.selectors.settingValue).textContent();
    return value.trim();
  }

  async isPasswordProtected(linkName) {
    const value = await this.getLinkSetting(linkName, 'Password Protected');
    return value === 'Yes';
  }

  async getViewCount(linkName) {
    const value = await this.getLinkSetting(linkName, 'View Count');
    return parseInt(value);
  }

  async showsHistory(linkName) {
    const value = await this.getLinkSetting(linkName, 'Show History');
    return value === 'Yes';
  }

  /**
   * Create Link Methods
   */
  async clickCreateNewLink() {
    await this.page.locator(this.selectors.createLinkButton).click();
  }

  async isCreateDialogVisible() {
    return await this.page.locator(this.selectors.createDialogHeader).isVisible();
  }

  async isEditDialogVisible() {
    return await this.page.locator(this.selectors.editDialogHeader).isVisible();
  }

  async fillLinkForm({ name, showHistory, expiresAt, hasPassword, password }) {
    if (name !== undefined) {
      await this.page.locator(this.selectors.nameInput).fill(name);
    }

    if (showHistory !== undefined) {
      if (showHistory) {
        await this.page.locator(this.selectors.withHistoryRadio).click();
      } else {
        await this.page.locator(this.selectors.currentOnlyRadio).click();
      }
    }

    if (expiresAt !== undefined) {
      // PrimeVue Calendar component - need to find the input inside the span wrapper
      const calendarInput = this.page.locator(`${this.selectors.expiresAtCalendar} input`);

      // Click to focus
      await calendarInput.click();

      // Clear existing value
      await calendarInput.press('Control+A');
      await calendarInput.press('Backspace');

      // Format date as MM/DD/YY HH:mm
      const month = String(expiresAt.getMonth() + 1).padStart(2, '0');
      const day = String(expiresAt.getDate()).padStart(2, '0');
      const year = String(expiresAt.getFullYear()).slice(-2);
      const hours = String(expiresAt.getHours()).padStart(2, '0');
      const minutes = String(expiresAt.getMinutes()).padStart(2, '0');

      // Type the date
      await calendarInput.fill(`${month}/${day}/${year} ${hours}:${minutes}`);

      // Press Tab to move to next field (this will close the calendar overlay without closing the dialog)
      await calendarInput.press('Tab');

      // Wait for calendar overlay to close
      await this.page.waitForTimeout(300);
    }

    if (hasPassword !== undefined) {
      // PrimeVue Checkbox - check if there's a wrapper or use direct input
      const checkboxWrapper = this.page.locator(this.selectors.hasPasswordCheckbox);
      const checkboxInput = checkboxWrapper.locator('input[type="checkbox"]');

      const inputExists = await checkboxInput.count();
      const checkbox = inputExists > 0 ? checkboxInput : checkboxWrapper;

      const isChecked = await checkbox.isChecked();
      if (hasPassword !== isChecked) {
        // Click the wrapper (the visible checkbox element) not the hidden input
        await checkboxWrapper.click();
      }
    }

    if (password !== undefined && hasPassword) {
      // PrimeVue Password component might also have a wrapper
      // Try to find input inside the password component
      const passwordField = this.page.locator(`${this.selectors.passwordInput} input`).first();
      const passwordFieldExists = await passwordField.count();

      if (passwordFieldExists > 0) {
        await passwordField.fill(password);
      } else {
        // Fallback to direct input if no wrapper exists
        await this.page.locator(this.selectors.passwordInput).fill(password);
      }
    }
  }

  async submitCreateForm() {
    await this.page.locator(this.selectors.createSubmitButton).click();
  }

  async submitUpdateForm() {
    await this.page.locator(this.selectors.updateSubmitButton).click();
  }

  async cancelDialog() {
    await this.page.locator(this.selectors.cancelButton).first().click();
  }

  async createShareLink(linkData) {
    await this.clickCreateNewLink();
    await this.waitForDialogToOpen();
    await this.fillLinkForm(linkData);
    await this.submitCreateForm();
  }

  async waitForDialogToOpen() {
    await this.page.waitForSelector(this.selectors.shareDialog, { state: 'visible', timeout: 5000 });
  }

  async waitForDialogToClose() {
    await this.page.waitForSelector(this.selectors.shareDialog, { state: 'hidden', timeout: 5000 });
  }

  /**
   * Edit Link Methods
   */
  async clickEditLink(linkName) {
    const card = await this.getLinkCardByName(linkName);
    await card.locator(this.selectors.editButton).click();
  }

  async editShareLink(linkName, updatedData) {
    await this.clickEditLink(linkName);
    await this.waitForDialogToOpen();
    await this.fillLinkForm(updatedData);
    await this.submitUpdateForm();
  }

  /**
   * Delete Link Methods
   */
  async clickDeleteLink(linkName) {
    const card = await this.getLinkCardByName(linkName);
    await card.locator(this.selectors.deleteButton).click();
  }

  async isDeleteDialogVisible() {
    return await this.page.locator(this.selectors.deleteDialog).isVisible();
  }

  async confirmDelete() {
    await this.page.locator(this.selectors.confirmDeleteButton).click();
  }

  async cancelDelete() {
    await this.page.locator(this.selectors.cancelDeleteButton).click();
  }

  async deleteShareLink(linkName) {
    await this.clickDeleteLink(linkName);
    await this.waitForDeleteDialogToOpen();
    await this.confirmDelete();
  }

  async waitForDeleteDialogToOpen() {
    await this.page.waitForSelector(this.selectors.deleteDialog, { state: 'visible', timeout: 5000 });
  }

  async waitForDeleteDialogToClose() {
    await this.page.waitForSelector(this.selectors.deleteDialog, { state: 'hidden', timeout: 5000 });
  }

  /**
   * Copy to Clipboard Methods
   */
  async clickCopyButton(linkName) {
    const card = await this.getLinkCardByName(linkName);
    await card.locator(this.selectors.copyButton).click();
  }

  /**
   * Toast Notification Methods
   */
  async waitForSuccessToast(expectedText = null) {
    await this.page.waitForSelector(this.selectors.successToast, { state: 'visible', timeout: 5000 });
    if (expectedText) {
      const detail = await this.page.locator(this.selectors.toastDetail).textContent();
      expect(detail.toLowerCase()).toContain(expectedText.toLowerCase());
    }
  }

  async waitForErrorToast() {
    await this.page.waitForSelector(this.selectors.errorToast, { state: 'visible', timeout: 5000 });
  }

  async waitForToastToDisappear() {
    await this.page.waitForSelector(this.selectors.toast, { state: 'hidden', timeout: 10000 });
  }

  /**
   * Wait for link to appear in UI after database insertion
   */
  async waitForLinkToAppear(linkName, timeout = 5000) {
    await this.page.waitForSelector(`.link-card:has(.link-title:has-text("${linkName}"))`, {
      state: 'visible',
      timeout: timeout
    });
  }

  /**
   * Wait for UI to update after database changes
   */
  async waitForUIUpdate() {
    await this.page.waitForTimeout(1000);
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Database Helper Methods
   */
  static async getShareLinkById(dbManager, linkId) {
    const result = await dbManager.client.query(
      'SELECT * FROM shared_link WHERE id = $1',
      [linkId]
    );
    return result.rows[0] || null;
  }

  static async getShareLinksByUserId(dbManager, userId) {
    const result = await dbManager.client.query(
      'SELECT * FROM shared_link WHERE user_id = $1 ORDER BY created_at DESC',
      [userId]
    );
    return result.rows;
  }

  static async countShareLinks(dbManager, userId) {
    const result = await dbManager.client.query(
      'SELECT COUNT(*) as count FROM shared_link WHERE user_id = $1',
      [userId]
    );
    return parseInt(result.rows[0].count);
  }

  static async insertShareLink(dbManager, linkData) {
    const id = linkData.id || '00000000-0000-0000-0000-000000000001';
    const result = await dbManager.client.query(`
      INSERT INTO shared_link (id, name, expires_at, password, show_history, user_id, created_at, view_count)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
      RETURNING *
    `, [
      id,
      linkData.name || 'Test Link',
      linkData.expires_at,
      linkData.password || null,
      linkData.show_history || false,
      linkData.user_id,
      linkData.created_at || new Date().toISOString(),
      linkData.view_count || 0
    ]);
    return result.rows[0];
  }

  static async deleteAllShareLinks(dbManager, userId) {
    await dbManager.client.query(
      'DELETE FROM shared_link WHERE user_id = $1',
      [userId]
    );
  }
}
