import { expect } from '@playwright/test';

export class PeriodTagsManagementPage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      // Page container
      pageContainer: '.page-container',
      pageTitle: 'h1:has-text("Period Tags")',

      // Header actions
      createButton: 'button:has-text("Create Period Tag")',

      // Active tag banner
      activeTagBanner: '.active-tag-banner',
      activeTagMessage: '.p-message:has-text("Active Tag")',

      // Filters
      searchInput: '.search-input',
      sourceSelect: '.source-select',
      bulkDeleteButton: 'button:has-text("Delete")',

      // Table
      dataTable: '.desktop-table',
      tableRow: '.p-datatable-tbody tr',
      tableCheckbox: '.p-checkbox-box',
      viewTimelineButton: 'button[aria-label*="View timeline"]',
      editButton: 'button[aria-label*="Edit"]',
      deleteButton: 'button[aria-label*="Delete"]',

      // Mobile cards
      mobileCards: '.mobile-cards',
      periodTagCard: '.period-tag-card',
      cardCheckbox: '.tag-checkbox',

      // Dialogs
      createDialog: '.p-dialog:has(.p-dialog-title:text("Create Period Tag"))',
      editDialog: '.p-dialog:has(.p-dialog-title:text("Edit Period Tag"))',
      confirmDialog: '.p-confirmdialog',

      // Dialog inputs
      tagNameInput: '.p-dialog input#tagName, .p-dialog input[placeholder*="e.g.,"]',
      dateRangePicker: '.p-dialog input[placeholder*="Select start and end dates"]',
      startDatePicker: '.p-dialog .p-datepicker-input',
      endDatePicker: '.p-dialog .p-datepicker-input',
      dialogCreateButton: '.p-dialog button:has-text("Create")',
      dialogEditButton: '.p-dialog button:has-text("Update")',
      dialogCancelButton: '.p-dialog button:has-text("Cancel")',
      dialogConfirmButton: '.p-confirmdialog-accept-button',
      dialogRejectButton: '.p-confirmdialog-reject-button',

      // Toast notifications
      successToast: '.p-toast-message-success',
      errorToast: '.p-toast-message-error',
      warnToast: '.p-toast-message-warn',
      toastDetail: '.p-toast-detail',
    };
  }

  // ===========================================
  // NAVIGATION
  // ===========================================

  async navigate() {
    await this.page.goto('/app/period-tags');
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageContainer, { timeout: 10000 });
    await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 5000 });
  }

  async isOnPeriodTagsPage() {
    try {
      await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  // ===========================================
  // ACTIVE TAG BANNER
  // ===========================================

  async isActiveTagBannerVisible() {
    const banner = this.page.locator(this.selectors.activeTagMessage);
    try {
      await banner.waitFor({ state: 'visible', timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  async getActiveTagName() {
    // Wait for the banner to be visible first
    await this.page.locator(this.selectors.activeTagMessage).waitFor({ state: 'visible', timeout: 5000 });

    const banner = this.page.locator(this.selectors.activeTagBanner);
    const text = await banner.textContent();

    // Extract tag name - it's between "Active Tag:" and the next tag/badge
    // Format: "Active Tag: <tag name> OwnTracks Since ..."
    const match = text.match(/Active Tag:\s*([^O]+)/); // Match until "OwnTracks" or other badge
    if (match) {
      return match[1].trim();
    }

    // Fallback: try to get the span directly
    const tagNameSpan = this.page.locator('.active-tag-banner span').first();
    return await tagNameSpan.textContent();
  }

  // ===========================================
  // FILTERS
  // ===========================================

  async fillSearchInput(text) {
    await this.page.fill(this.selectors.searchInput, text);
    await this.page.waitForTimeout(500); // Wait for filter to apply
  }

  async selectSourceFilter(source) {
    // source: 'manual', 'owntracks', or null for 'All Sources'
    await this.page.click(this.selectors.sourceSelect);
    await this.page.waitForTimeout(300);

    const optionText = source === 'manual' ? 'Manual' :
                      source === 'owntracks' ? 'OwnTracks' : 'All Sources';
    await this.page.click(`.p-select-option:has-text("${optionText}")`);
    await this.page.waitForTimeout(500);
  }

  async clearSourceFilter() {
    // Click the clear button (X) on the select
    const clearButton = this.page.locator(this.selectors.sourceSelect).locator('.p-select-clear-icon');
    if (await clearButton.isVisible()) {
      await clearButton.click();
      await this.page.waitForTimeout(500);
    }
  }

  // ===========================================
  // TABLE OPERATIONS
  // ===========================================

  async getTableRowCount() {
    const rows = this.page.locator(this.selectors.tableRow);
    return await rows.count();
  }

  async getTableRowData(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);

    // Get tag name
    const nameCell = row.locator('td').nth(1); // Second column (after checkbox)
    const name = await nameCell.textContent();

    // Get source badge
    const sourceBadge = row.locator('.p-tag');
    const source = await sourceBadge.textContent();

    // Check if active
    const activeTag = row.locator('.p-tag:has-text("Active")');
    const isActive = await activeTag.count() > 0;

    return {
      name: name.trim(),
      source: source.trim(),
      isActive
    };
  }

  async selectTableRow(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    // Click on the checkbox wrapper div (PrimeVue checkbox)
    const checkbox = row.locator('.p-checkbox').first();
    await checkbox.click();
    await this.page.waitForTimeout(300);
  }

  async getSelectedRowCount() {
    const selectedCheckboxes = this.page.locator('.p-checkbox-checked');
    // Subtract 1 if select-all checkbox is checked
    const count = await selectedCheckboxes.count();
    return count > 0 ? count : 0;
  }

  async clickViewTimeline(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const viewButton = row.locator('button.p-button-text').nth(0); // First icon button
    await viewButton.click();
  }

  async clickEditInTable(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const editButton = row.locator('button.p-button-text').nth(1); // Second icon button
    await editButton.click();
  }

  async clickDeleteInTable(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const deleteButton = row.locator('button.p-button-text').nth(2); // Third icon button
    await deleteButton.click();
  }

  async isEditButtonDisabled(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const editButton = row.locator('button.p-button-text').nth(1);
    return await editButton.isDisabled();
  }

  async isDeleteButtonDisabled(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const deleteButton = row.locator('button.p-button-text').nth(2);
    return await deleteButton.isDisabled();
  }

  async isTableEmpty() {
    // Check if the empty state is visible (either desktop table or mobile cards)
    const desktopEmpty = this.page.locator('.p-datatable-empty-message');
    const mobileEmpty = this.page.locator('.mobile-cards .empty-state');

    const desktopVisible = await desktopEmpty.isVisible().catch(() => false);
    const mobileVisible = await mobileEmpty.isVisible().catch(() => false);

    return desktopVisible || mobileVisible;
  }

  // ===========================================
  // MOBILE CARD OPERATIONS
  // ===========================================

  async getMobileCardCount() {
    const cards = this.page.locator(this.selectors.periodTagCard);
    return await cards.count();
  }

  async getMobileCardData(index) {
    const card = this.page.locator(this.selectors.periodTagCard).nth(index);

    const titleEl = card.locator('.card-title');
    const name = await titleEl.textContent();

    const badges = card.locator('.p-tag');
    const badgeCount = await badges.count();
    let source = '';
    let isActive = false;

    for (let i = 0; i < badgeCount; i++) {
      const text = await badges.nth(i).textContent();
      if (text.includes('Active')) {
        isActive = true;
      } else if (text.includes('Manual') || text.includes('OwnTracks')) {
        source = text.trim();
      }
    }

    return { name: name.trim(), source, isActive };
  }

  async clickEditInMobileCard(index) {
    const card = this.page.locator(this.selectors.periodTagCard).nth(index);
    const editButton = card.locator('button:has-text("Edit")');
    await editButton.click();
  }

  async clickDeleteInMobileCard(index) {
    const card = this.page.locator(this.selectors.periodTagCard).nth(index);
    const deleteButton = card.locator('button:has(.pi-trash)');
    await deleteButton.click();
  }

  // ===========================================
  // BULK OPERATIONS
  // ===========================================

  async isBulkDeleteButtonVisible() {
    const button = this.page.locator(this.selectors.bulkDeleteButton);
    return await button.isVisible();
  }

  async getBulkDeleteCount() {
    const button = this.page.locator(this.selectors.bulkDeleteButton);
    const text = await button.textContent();
    const match = text.match(/Delete \((\d+)\)/);
    return match ? parseInt(match[1]) : 0;
  }

  async clickBulkDelete() {
    await this.page.click(this.selectors.bulkDeleteButton);
  }

  // ===========================================
  // DIALOGS
  // ===========================================

  async clickCreateButton() {
    await this.page.click(this.selectors.createButton);
  }

  async waitForCreateDialog() {
    await this.page.waitForSelector(this.selectors.createDialog, { timeout: 5000 });
  }

  async waitForEditDialog() {
    await this.page.waitForSelector(this.selectors.editDialog, { timeout: 5000 });
  }

  async fillCreateDialog(tagName, startDate = null, endDate = null) {
    await this.waitForCreateDialog();

    // Fill tag name
    const nameInput = this.page.locator(this.selectors.tagNameInput).first();
    await nameInput.fill(tagName);

    // Fill date range if both dates provided
    if (startDate && endDate) {
      await this.fillDateRange(startDate, endDate);
    }
  }

  async fillDateRange(startDate, endDate) {
    // Click on the date range picker to open calendar
    const dateRangePicker = this.page.locator(this.selectors.dateRangePicker);
    await dateRangePicker.click();
    await this.page.waitForTimeout(500);

    // The calendar should now be open - we need to click dates
    // Format: "M d, yy" means the calendar shows month/day/year
    const startDay = startDate.getDate();
    const endDay = endDate.getDate();

    // Click start date in calendar
    const startDateButton = this.page.locator(`.p-datepicker-calendar td span:has-text("${startDay}")`).first();
    await startDateButton.click();
    await this.page.waitForTimeout(300);

    // Click end date in calendar
    const endDateButton = this.page.locator(`.p-datepicker-calendar td span:has-text("${endDay}")`).first();
    await endDateButton.click();
    await this.page.waitForTimeout(300);

    // Click on dialog header to close the datepicker
    const dialogHeader = this.page.locator('.p-dialog-header');
    await dialogHeader.click();
    await this.page.waitForTimeout(300);
  }

  async fillEditDialog(tagName, startDate = null, endDate = null) {
    await this.waitForEditDialog();

    // Clear and fill tag name
    const nameInput = this.page.locator(this.selectors.tagNameInput).first();
    await nameInput.clear();
    await nameInput.fill(tagName);

    // Fill date range if both dates provided
    if (startDate && endDate) {
      await this.fillDateRange(startDate, endDate);
    }
  }

  async submitCreateDialog() {
    await this.page.click(this.selectors.dialogCreateButton);
  }

  async submitEditDialog() {
    await this.page.click(this.selectors.dialogEditButton);
  }

  async cancelDialog() {
    await this.page.click(this.selectors.dialogCancelButton);
  }

  async waitForConfirmDialog() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { timeout: 5000 });
  }

  async confirmDialog() {
    await this.waitForConfirmDialog();
    await this.page.click(this.selectors.dialogConfirmButton);
  }

  async rejectDialog() {
    await this.waitForConfirmDialog();
    await this.page.click(this.selectors.dialogRejectButton);
  }

  // ===========================================
  // TOAST NOTIFICATIONS
  // ===========================================

  async waitForSuccessToast() {
    await this.page.waitForSelector(this.selectors.successToast, { timeout: 5000 });
  }

  async waitForErrorToast() {
    await this.page.waitForSelector(this.selectors.errorToast, { timeout: 5000 });
  }

  async waitForWarnToast() {
    await this.page.waitForSelector(this.selectors.warnToast, { timeout: 5000 });
  }

  async getToastMessage() {
    const toast = this.page.locator(this.selectors.toastDetail).first();
    return await toast.textContent();
  }

  // ===========================================
  // WORKFLOW HELPERS
  // ===========================================

  async createPeriodTagWorkflow(tagName, startDate = null, endDate = null) {
    await this.clickCreateButton();
    await this.fillCreateDialog(tagName, startDate, endDate);
    await this.submitCreateDialog();
    await this.waitForSuccessToast();
    await this.page.waitForTimeout(1000);
  }

  async editPeriodTagWorkflow(index, tagName, startDate = null, endDate = null) {
    await this.clickEditInTable(index);
    await this.fillEditDialog(tagName, startDate, endDate);
    await this.submitEditDialog();
    await this.waitForSuccessToast();
    await this.page.waitForTimeout(1000);
  }

  async deletePeriodTagWorkflow(index) {
    await this.clickDeleteInTable(index);
    await this.confirmDialog();
    await this.waitForSuccessToast();
    await this.page.waitForTimeout(1000);
  }

  async bulkDeleteWorkflow(indices) {
    // Select rows
    for (const index of indices) {
      await this.selectTableRow(index);
    }

    // Click bulk delete
    await this.clickBulkDelete();
    await this.confirmDialog();
    await this.waitForSuccessToast();
    await this.page.waitForTimeout(1000);
  }

  // ===========================================
  // UTILITIES
  // ===========================================

  formatDateForInput(date) {
    // Format: MM/DD/YYYY for PrimeVue DatePicker
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const year = date.getFullYear();
    return `${month}/${day}/${year}`;
  }
}
