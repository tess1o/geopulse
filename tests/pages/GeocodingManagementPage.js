import { expect } from '@playwright/test';

export class GeocodingManagementPage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      // Page container
      pageContainer: '.page-container',
      pageTitle: 'h1:has-text("Reverse Geocoding Management")',

      // Header actions
      reconcileAllButton: 'button:has-text("Reconcile All"):not(.bulk-action-button)',
      bulkEditButton: 'button.bulk-action-button:has-text("Bulk Edit")',
      reconcileSelectedButton: 'button.bulk-action-button:has-text("Reconcile Selected")',

      // Filters
      providerSelect: '.filter-controls .provider-select',
      searchInput: '.filter-controls .search-input',
      clearFiltersButton: 'button:has-text("Clear Filters")',

      // Table
      geocodingTable: '.geocoding-table',
      tableRow: '.p-datatable-tbody tr',
      tableCheckbox: '.selection-col input[type="checkbox"]',
      viewButton: 'button.view-button',
      editButton: 'button.edit-button',
      reconcileButton: 'button.reconcile-button',

      // Dialogs
      editDialog: '.p-dialog:has(.p-dialog-title:text("Edit Geocoding Result"))',
      bulkEditDialog: '.p-dialog:has(.p-dialog-title:text("Bulk Edit"))',
      reconcileDialog: '.p-dialog:has(.p-dialog-title:has-text("Reconcile"))',
      confirmDialog: '.p-confirmdialog',

      // Dialog inputs
      displayNameInput: '.p-dialog input#displayName',
      cityInput: '.p-dialog input#city',
      countryInput: '.p-dialog input#country',
      dialogSaveButton: '.p-dialog button:has-text("Save Changes"), .p-dialog button:has-text("Save")',
      dialogUpdateButton: '.p-dialog button:has-text("Update")',
      dialogCancelButton: '.p-dialog button:has-text("Cancel")',
      dialogConfirmButton: '.p-confirmdialog-accept-button',
      dialogRejectButton: '.p-confirmdialog-reject-button',

      // Toast notifications
      successToast: '.p-toast-message-success',
      infoToast: '.p-toast-message-info',
      errorToast: '.p-toast-message-error',
      warnToast: '.p-toast-message-warn',
      toastDetail: '.p-toast-detail',
    };
  }

  // ===========================================
  // NAVIGATION
  // ===========================================

  async navigate() {
    await this.page.goto('/app/geocoding-management');
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageContainer, { timeout: 10000 });
    await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 5000 });
    await this.page.waitForTimeout(1000); // Wait for table to load
  }

  async isOnGeocodingPage() {
    try {
      await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  // ===========================================
  // FILTERS
  // ===========================================

  async selectProviderFilter(provider) {
    // provider: 'Nominatim', 'GoogleMaps', 'Mapbox', 'Photon', or null for 'All Providers'
    await this.page.click(this.selectors.providerSelect);
    await this.page.waitForTimeout(300);

    const optionText = provider || 'All Providers';
    await this.page.click(`.p-select-option:has-text("${optionText}")`);
    await this.page.waitForTimeout(800); // Wait for table to reload
  }

  async fillSearchInput(text) {
    await this.page.fill(this.selectors.searchInput, text);
    await this.page.waitForTimeout(800); // Wait for debounce + table reload
  }

  async clearFilters() {
    await this.page.click(this.selectors.clearFiltersButton);
    await this.page.waitForTimeout(800); // Wait for table to reload
  }

  async isClearFiltersButtonEnabled() {
    const button = this.page.locator(this.selectors.clearFiltersButton);
    return await button.isEnabled();
  }

  async hasActiveFilters() {
    return await this.isClearFiltersButtonEnabled();
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

    const nameCell = row.locator('.name-cell');
    const name = await nameCell.textContent();

    // Try to get city and country (might not be visible on mobile)
    let city = null;
    let country = null;

    const cityCell = row.locator('td').nth(2); // Assuming column order
    if (await cityCell.count() > 0) {
      const cityText = await cityCell.textContent();
      city = cityText.trim() !== '-' ? cityText.trim() : null;
    }

    const countryCell = row.locator('td').nth(3);
    if (await countryCell.count() > 0) {
      const countryText = await countryCell.textContent();
      country = countryText.trim() !== '-' ? countryText.trim() : null;
    }

    return { name: name.trim(), city, country };
  }

  async selectTableRow(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const checkbox = row.locator(this.selectors.tableCheckbox);
    await checkbox.click();
    await this.page.waitForTimeout(300);
  }

  async getSelectedRowCount() {
    const selectedCheckboxes = this.page.locator('.selection-col input[type="checkbox"]:checked');
    return await selectedCheckboxes.count();
  }

  async clickViewDetails(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const viewButton = row.locator(this.selectors.viewButton);
    await viewButton.click();
  }

  async clickEditInTable(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const editButton = row.locator(this.selectors.editButton);
    await editButton.click();
  }

  async clickReconcileInTable(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const reconcileButton = row.locator(this.selectors.reconcileButton);
    await reconcileButton.click();
  }

  async isTableEmpty() {
    const emptyState = this.page.locator('.empty-state');
    return await emptyState.isVisible();
  }

  async waitForTableLoad() {
    // Wait for table to finish loading
    await this.page.waitForTimeout(1000);

    // Check if loading indicator is gone
    const loadingIndicator = this.page.locator('.p-datatable-loading-icon');
    if (await loadingIndicator.isVisible().catch(() => false)) {
      await loadingIndicator.waitFor({ state: 'hidden', timeout: 5000 });
    }
  }

  // ===========================================
  // PAGINATION AND SORTING
  // ===========================================

  async goToNextPage() {
    const nextButton = this.page.locator('.p-paginator-next');
    await nextButton.click();
    await this.waitForTableLoad();
  }

  async goToPreviousPage() {
    const prevButton = this.page.locator('.p-paginator-prev');
    await prevButton.click();
    await this.waitForTableLoad();
  }

  async getCurrentPage() {
    const pageInfo = this.page.locator('.p-paginator-current');
    const text = await pageInfo.textContent();
    // Extract page number from text like "Showing 1 to 50 of 100"
    const match = text.match(/Showing (\d+) to (\d+) of (\d+)/);
    if (match) {
      return {
        start: parseInt(match[1]),
        end: parseInt(match[2]),
        total: parseInt(match[3])
      };
    }
    return null;
  }

  async sortByColumn(columnName) {
    // columnName: 'Display Name', 'City', 'Country', 'Provider', 'Last Used'
    const header = this.page.locator(`th:has-text("${columnName}")`);
    await header.click();
    await this.waitForTableLoad();
  }

  // ===========================================
  // BULK OPERATIONS
  // ===========================================

  async clickBulkEdit() {
    await this.page.click(this.selectors.bulkEditButton);
  }

  async clickReconcileSelected() {
    await this.page.click(this.selectors.reconcileSelectedButton);
  }

  async clickReconcileAll() {
    await this.page.click(this.selectors.reconcileAllButton);
  }

  async isReconcileAllButtonDisabled() {
    const button = this.page.locator(this.selectors.reconcileAllButton);
    return await button.isDisabled();
  }

  async areBulkActionButtonsVisible() {
    const bulkEditButton = this.page.locator(this.selectors.bulkEditButton);
    const reconcileSelectedButton = this.page.locator(this.selectors.reconcileSelectedButton);

    const bulkEditVisible = await bulkEditButton.isVisible().catch(() => false);
    const reconcileSelectedVisible = await reconcileSelectedButton.isVisible().catch(() => false);

    return bulkEditVisible && reconcileSelectedVisible;
  }

  // ===========================================
  // DIALOGS
  // ===========================================

  async waitForEditDialog() {
    await this.page.waitForSelector(this.selectors.editDialog, { timeout: 5000 });
  }

  async fillEditDialog(displayName, city = null, country = null) {
    await this.waitForEditDialog();

    if (displayName !== null) {
      const displayNameInput = this.page.locator(this.selectors.displayNameInput);
      await displayNameInput.clear();
      await displayNameInput.fill(displayName);
    }

    if (city !== null) {
      const cityInput = this.page.locator(this.selectors.cityInput);
      await cityInput.clear();
      await cityInput.fill(city);
      await this.page.waitForTimeout(300);
    }

    if (country !== null) {
      const countryInput = this.page.locator(this.selectors.countryInput);
      await countryInput.clear();
      await countryInput.fill(country);
      await this.page.waitForTimeout(300);
    }
  }

  async submitEditDialog() {
    await this.page.click(this.selectors.dialogSaveButton);
  }

  async closeEditDialog() {
    await this.page.click(this.selectors.dialogCancelButton);
  }

  async waitForBulkEditDialog() {
    await this.page.waitForSelector(this.selectors.bulkEditDialog, { timeout: 5000 });
  }

  async submitBulkEdit() {
    const dialog = this.page.locator(this.selectors.bulkEditDialog);
    const updateButton = dialog.locator('button:has-text("Update")');
    await updateButton.click();
  }

  async waitForReconcileDialog() {
    await this.page.waitForSelector(this.selectors.reconcileDialog, { timeout: 5000 });
  }

  async selectReconcileProviders(providers) {
    await this.waitForReconcileDialog();

    for (const provider of providers) {
      const checkbox = this.page.locator(`.p-checkbox-label:has-text("${provider}")`).locator('..');
      await checkbox.click();
      await this.page.waitForTimeout(200);
    }
  }

  async submitReconcile() {
    const dialog = this.page.locator(this.selectors.reconcileDialog);
    const button = dialog.locator('button:has-text("Start Reconciliation")');
    await button.click();
  }

  async waitForReconcileToComplete() {
    // Wait for reconcile dialog to close (indicates completion)
    await this.page.waitForSelector(this.selectors.reconcileDialog, {
      state: 'hidden',
      timeout: 60000 // Reconciliation can take a while
    });
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

  async waitForInfoToast() {
    await this.page.waitForSelector(this.selectors.infoToast, { timeout: 5000 });
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

  async editGeocodingResultWorkflow(index, displayName, city = null, country = null) {
    await this.clickEditInTable(index);
    await this.fillEditDialog(displayName, city, country);
    await this.submitEditDialog();
    await this.waitForSuccessToast();
    await this.page.waitForTimeout(1000);
  }

  async viewDetailsWorkflow(index, expectedResultId) {
    await this.clickViewDetails(index);
    // Wait for navigation to place details page
    await this.page.waitForURL(`**/app/place-details/geocoding/${expectedResultId}`, { timeout: 10000 });
  }
}
