import { expect } from '@playwright/test';

export class PlaceDetailsPage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      // Page container
      pageContainer: '.gp-page-container',
      pageTitle: '.gp-page-title',
      pageSubtitle: '.gp-page-subtitle',
      breadcrumbNav: '.breadcrumb-nav',
      backButton: 'button:has-text("Back")',

      // Loading & Error states
      loadingContainer: '.loading-container',
      loadingSpinner: '.p-progress-spinner',
      errorContainer: '.error-container',
      errorTitle: '.error-title',
      errorMessage: '.error-message',
      retryButton: 'button:has-text("Try Again")',

      // Place Header
      placeHeader: '.place-header',
      locationName: '.location-name',
      locationInfo: '.location-info',
      editButton: '.place-header button:has-text("Edit")',
      createFavoriteButton: '.place-header button:has-text("Create Favorite")',

      // Related Favorite Notice
      relatedFavoriteNotice: '.related-favorite-notice',
      noticeTitle: '.notice-title',
      noticeMessage: '.notice-message',
      favoriteInfo: '.favorite-info',
      favoriteName: '.favorite-name',
      favoriteDistance: '.favorite-distance',
      favoriteStats: '.favorite-stats',
      viewFavoriteButton: '.view-favorite-button',

      // Statistics Card
      statsCard: '.gp-card:has(.gp-card-title:text("Place Statistics"))',
      statsGrid: '.stats-grid',
      statSection: '.stat-section',
      sectionTitle: '.section-title',
      statItems: '.stat-items',
      statItemFull: '.stat-item-full',
      statValue: '.stat-value',
      statLabel: '.stat-label',

      // Map
      mapContainer: '.place-map-container',
      mapCard: '.gp-card:has(.gp-card-title:text("Location"))',
      leafletMap: '.leaflet-container',

      // Visits Table
      visitsTable: '.visits-data-table',
      visitsCard: '.gp-card:has(.table-title:text("All Visits"))',
      visitsTableRow: '.p-datatable-tbody tr',
      exportButton: 'button.export-button, button:has-text("Export CSV")',
      tableHeader: '.p-datatable-thead th',
      tableHeaderTitle: '.table-header',
      tableTitle: '.table-title',
      noDataState: '.no-data-state',
      noDataTitle: '.no-data-title',
      noDataMessage: '.no-data-message',

      // Dialogs
      editFavoriteDialog: '.p-dialog:has(.p-dialog-title:text("Edit Favorite Location"))',
      geocodingEditDialog: '.p-dialog:has(.p-dialog-title:text("Edit Geocoding Result"))',
      createFavoriteDialog: '.p-dialog:has(.p-dialog-title:text("Create Favorite Location"))',
      timelineRegenerationModal: '.p-dialog:has(.p-dialog-title:text-matches("Timeline|Regenerating"))',

      // Dialog inputs
      dialogInput: '.p-dialog input',
      favoriteNameInput: '#favorite-name',
      dialogSaveButton: '.p-dialog button:has-text("Save"), .p-dialog button:has-text("Update")',
      dialogCancelButton: '.p-dialog button:has-text("Cancel")',
      createFavoriteSubmitButton: '.p-dialog button:has-text("Create Favorite")',

      // Timeline Regeneration
      jobProgressBar: '.job-progress',
      jobStatusText: '.job-status-text',

      // Toast notifications
      successToast: '.p-toast-message-success',
      errorToast: '.p-toast-message-error',
      toastDetail: '.p-toast-detail',

      // Pagination (in visits table)
      paginationFirst: '.p-paginator-first',
      paginationPrev: '.p-paginator-prev',
      paginationNext: '.p-paginator-next',
      paginationLast: '.p-paginator-last',
      paginationPages: '.p-paginator-pages .p-paginator-page',
      paginationCurrent: '.p-paginator-current',
    };
  }

  // ===========================================
  // NAVIGATION
  // ===========================================

  async navigateToFavorite(favoriteId) {
    await this.page.goto(`/app/place-details/favorite/${favoriteId}`);
  }

  async navigateToGeocoding(geocodingId) {
    await this.page.goto(`/app/place-details/geocoding/${geocodingId}`);
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageContainer, { timeout: 10000 });

    // Wait for either content or error state
    const hasContent = await Promise.race([
      this.page.waitForSelector(this.selectors.placeHeader, { timeout: 10000 }).then(() => true),
      this.page.waitForSelector(this.selectors.errorContainer, { timeout: 10000 }).then(() => false)
    ]);

    // If page loaded successfully (not error), wait for map to be ready
    if (hasContent) {
      try {
        await this.waitForMapReady();
      } catch (error) {
        // Map might not be present in error states or related favorite cases
        // Don't fail the page load if map isn't there
      }
    }
  }

  async goBack() {
    await this.page.locator(this.selectors.backButton).click();
  }

  // ===========================================
  // PAGE STATE CHECKS
  // ===========================================

  async isLoading() {
    return await this.page.locator(this.selectors.loadingContainer).isVisible();
  }

  async hasError() {
    return await this.page.locator(this.selectors.errorContainer).isVisible();
  }

  async getErrorMessage() {
    const errorMsg = await this.page.locator(this.selectors.errorMessage);
    return await errorMsg.textContent();
  }

  async getPageTitle() {
    const title = await this.page.locator(this.selectors.pageTitle);
    return await title.textContent();
  }

  async hasRelatedFavoriteNotice() {
    return await this.page.locator(this.selectors.relatedFavoriteNotice).isVisible();
  }

  async hasStatisticsCard() {
    return await this.page.locator(this.selectors.statsCard).isVisible();
  }

  async hasVisitsTable() {
    // Check if the visits card exists (it shows even when empty with "No Visits Found")
    return await this.page.locator(this.selectors.visitsCard).isVisible();
  }

  async hasVisitsData() {
    // Check if the table has actual data (not just the empty state)
    return await this.page.locator(this.selectors.visitsTable).isVisible();
  }

  async isVisitsTableEmpty() {
    // Check if showing "No Visits Found" message
    const noDataState = this.page.locator(this.selectors.noDataState);
    return await noDataState.isVisible();
  }

  async getNoVisitsMessage() {
    const message = await this.page.locator(this.selectors.noDataMessage);
    if (await message.isVisible()) {
      return await message.textContent();
    }
    return null;
  }

  async hasMap() {
    return await this.page.locator(this.selectors.mapContainer).isVisible();
  }

  async waitForMapReady() {
    // Wait for map container
    await this.page.waitForSelector(this.selectors.mapContainer, { timeout: 10000 });

    // Wait for leaflet to initialize
    await this.page.waitForSelector(this.selectors.leafletMap, { timeout: 10000 });

    // Wait for tiles to start loading
    await this.page.waitForSelector('.leaflet-tile-pane', {
      state: 'attached',
      timeout: 10000
    });

    // Give map time to render
    await this.page.waitForTimeout(1500);
  }

  // ===========================================
  // RELATED FAVORITE NOTICE
  // ===========================================

  async getRelatedFavoriteTitle() {
    const title = await this.page.locator(this.selectors.noticeTitle);
    return await title.textContent();
  }

  async getRelatedFavoriteMessage() {
    const message = await this.page.locator(this.selectors.noticeMessage);
    return await message.textContent();
  }

  async getRelatedFavoriteName() {
    const name = await this.page.locator(this.selectors.favoriteName);
    return await name.textContent();
  }

  async getRelatedFavoriteDistance() {
    const distance = await this.page.locator(this.selectors.favoriteDistance);
    if (await distance.isVisible()) {
      return await distance.textContent();
    }
    return null;
  }

  async clickViewRelatedFavorite() {
    await this.page.locator(this.selectors.viewFavoriteButton).click();
    await this.page.waitForTimeout(500);
  }

  // ===========================================
  // STATISTICS CARD
  // ===========================================

  async getStatistics() {
    const stats = {};
    const statItems = await this.page.locator(this.selectors.statItemFull).all();

    for (const item of statItems) {
      const labelElement = await item.locator(this.selectors.statLabel);
      const valueElement = await item.locator(this.selectors.statValue);

      // Get the text content (label includes icon, so get just the span text)
      const labelSpan = await labelElement.locator('span').textContent();
      const value = await valueElement.textContent();

      stats[labelSpan.trim()] = value.trim();
    }

    return stats;
  }

  async getStatValue(label) {
    // Find stat item that has a label span containing the text
    const statItems = await this.page.locator(this.selectors.statItemFull).all();

    for (const item of statItems) {
      const labelSpan = await item.locator(`${this.selectors.statLabel} span`);
      const labelText = await labelSpan.textContent();

      if (labelText.trim() === label) {
        const value = await item.locator(this.selectors.statValue);
        return await value.textContent();
      }
    }

    return null;
  }

  // ===========================================
  // VISITS TABLE
  // ===========================================

  async getVisitsTableRowCount() {
    const rows = await this.page.locator(this.selectors.visitsTableRow).all();
    return rows.length;
  }

  async getVisitRowData(index) {
    const row = this.page.locator(this.selectors.visitsTableRow).nth(index);
    const cells = await row.locator('td').allTextContents();
    return cells;
  }

  async clickExportButton() {
    await this.page.locator(this.selectors.exportButton).click();
  }

  async sortByColumn(columnName) {
    const header = this.page.locator(this.selectors.tableHeader, { hasText: columnName });
    await header.click();
    await this.page.waitForTimeout(1000);
  }

  async getPaginationInfo() {
    const current = await this.page.locator(this.selectors.paginationCurrent);
    if (await current.isVisible()) {
      return await current.textContent();
    }
    return null;
  }

  async goToNextPage() {
    await this.page.locator(this.selectors.paginationNext).click();
    await this.page.waitForTimeout(1000);
  }

  async goToPreviousPage() {
    await this.page.locator(this.selectors.paginationPrev).click();
    await this.page.waitForTimeout(1000);
  }

  // ===========================================
  // EDIT FUNCTIONALITY
  // ===========================================

  async clickEditButton() {
    await this.page.locator(this.selectors.editButton).click();
    await this.page.waitForTimeout(500);
  }

  async waitForEditFavoriteDialog() {
    await this.page.waitForSelector(this.selectors.editFavoriteDialog, {
      state: 'visible',
      timeout: 5000
    });
  }

  async waitForGeocodingEditDialog() {
    await this.page.waitForSelector(this.selectors.geocodingEditDialog, {
      state: 'visible',
      timeout: 5000
    });
  }

  async fillEditDialog(name, city = null, country = null) {
    // Fill name - handle both Edit Favorite (#name) and Edit Geocoding (#displayName)
    const nameInput = this.page.locator('.p-dialog input#name, .p-dialog input#displayName').first();
    await nameInput.clear();
    await nameInput.fill(name);

    // Fill city if provided
    if (city !== null) {
      const cityInput = this.page.locator('.p-dialog input#city');
      await cityInput.clear();
      await cityInput.fill(city);
    }

    // Fill country if provided
    if (country !== null) {
      const countryInput = this.page.locator('.p-dialog input#country');
      await countryInput.clear();
      await countryInput.fill(country);
    }
  }

  async submitEditDialog() {
    await this.page.locator(this.selectors.dialogSaveButton).click();
  }

  async cancelEditDialog() {
    await this.page.locator(this.selectors.dialogCancelButton).click();
  }

  async editWorkflow(newName, newCity = null, newCountry = null) {
    await this.clickEditButton();

    // Wait for appropriate dialog based on current URL
    const url = this.page.url();
    if (url.includes('/favorite/')) {
      await this.waitForEditFavoriteDialog();
    } else if (url.includes('/geocoding/')) {
      await this.waitForGeocodingEditDialog();
    }

    await this.fillEditDialog(newName, newCity, newCountry);
    await this.submitEditDialog();
    await this.page.waitForTimeout(500);
  }

  // ===========================================
  // CREATE FAVORITE (for geocoding)
  // ===========================================

  async clickCreateFavoriteButton() {
    await this.page.locator(this.selectors.createFavoriteButton).click();
    await this.page.waitForTimeout(500);
  }

  async waitForCreateFavoriteDialog() {
    await this.page.waitForSelector(this.selectors.createFavoriteDialog, {
      state: 'visible',
      timeout: 5000
    });
  }

  async fillCreateFavoriteDialog(name) {
    const nameInput = this.page.locator(this.selectors.favoriteNameInput);
    await nameInput.clear();
    await nameInput.fill(name);
  }

  async submitCreateFavorite() {
    await this.page.locator(this.selectors.createFavoriteSubmitButton).click();
  }

  async cancelCreateFavorite() {
    await this.page.locator(this.selectors.dialogCancelButton).click();
  }

  async createFavoriteWorkflow(name) {
    await this.clickCreateFavoriteButton();
    await this.waitForCreateFavoriteDialog();
    await this.fillCreateFavoriteDialog(name);
    await this.submitCreateFavorite();
    await this.page.waitForTimeout(500);
  }

  // ===========================================
  // TIMELINE REGENERATION MODAL
  // ===========================================

  async waitForTimelineRegenerationModal() {
    await this.page.waitForSelector(this.selectors.timelineRegenerationModal, {
      state: 'visible',
      timeout: 5000
    });
  }

  async waitForTimelineRegenerationToComplete(timeout = 30000) {
    // Wait for modal to appear
    try {
      await this.waitForTimelineRegenerationModal();
    } catch (error) {
      // Modal might not appear if operation is too fast
      return;
    }

    // Wait for modal to disappear (job complete)
    await this.page.waitForSelector(this.selectors.timelineRegenerationModal, {
      state: 'hidden',
      timeout
    });
  }

  // ===========================================
  // TOAST NOTIFICATIONS
  // ===========================================

  async waitForSuccessToast() {
    await this.page.waitForSelector(this.selectors.successToast, {
      state: 'visible',
      timeout: 10000
    });
  }

  async waitForErrorToast() {
    await this.page.waitForSelector(this.selectors.errorToast, {
      state: 'visible',
      timeout: 10000
    });
  }

  async getToastMessage() {
    const toast = await this.page.locator(this.selectors.toastDetail);
    return await toast.textContent();
  }

  // ===========================================
  // HELPER METHODS
  // ===========================================

  async clickRetryButton() {
    await this.page.locator(this.selectors.retryButton).click();
  }

  async getCoordinatesFromPage() {
    // Extract coordinates from the page (e.g., from coordinates info)
    const coordsText = await this.page.locator('.coordinates-info').textContent();
    const matches = coordsText.match(/([-\d.]+),\s*([-\d.]+)/);
    if (matches) {
      return {
        latitude: parseFloat(matches[1]),
        longitude: parseFloat(matches[2])
      };
    }
    return null;
  }
}
