import { expect } from '@playwright/test';
import { MapEngineHarness } from '../utils/map-engine-harness.js';

export class FavoritesManagementPage {
  constructor(page) {
    this.page = page;
    this.mapHarness = new MapEngineHarness(page);

    this.selectors = {
      // Page container
      pageContainer: '.page-container',
      pageTitle: 'h1:has-text("Favorite Locations Management")',

      // Header actions
      bulkModeButton: 'button:has-text("Bulk Mode")',
      savePendingButton: 'button.save-pending-button',
      reconcileAllButton: 'button:has-text("Reconcile All"):not(.bulk-action-button)',
      bulkEditButton: 'button.bulk-action-button:has-text("Bulk Edit")',
      reconcileSelectedButton: 'button.bulk-action-button:has-text("Reconcile Selected")',

      // Map
      mapContainer: '.map-container',
      mapHost: '#favorites-map[data-testid="map-host-raster"], #favorites-map[data-testid="map-host-vector"]',
      leafletMap: '#favorites-map[data-testid="map-host-raster"], #favorites-map[data-testid="map-host-vector"]',
      favoriteMarker: '.favorite-marker-icon',
      favoriteAreaMarker: '.favorite-area-icon',
      pendingPointMarker: '.pending-marker-icon',
      pendingAreaMarker: '.pending-area-icon',

      // Pending favorites panel
      pendingPanel: '.pending-panel',
      pendingItem: '.pending-item',
      clearPendingButton: '.pending-panel button:has-text("Clear All")',
      savePendingPanelButton: '.pending-panel button:has-text("Save All")',
      removePendingButton: 'button.pi-trash',

      // Filters
      typeSelect: '.filter-controls .type-select',
      searchInput: '.filter-controls .search-input',
      clearFiltersButton: 'button:has-text("Clear Filters")',

      // Table
      favoritesTable: '.favorites-table',
      tableRow: '.p-datatable-tbody tr',
      tableCheckbox: '.selection-col input[type="checkbox"]',
      viewButton: 'button.view-button',
      editButton: 'button.edit-button',
      deleteButton: 'button.delete-button',
      mapButton: 'button.map-button',

      // Dialogs
      addDialog: '.p-dialog',
      editDialog: '.p-dialog:has(.p-dialog-title:text("Edit Favorite Location"))',
      bulkEditDialog: '.p-dialog:has(.p-dialog-title:text("Bulk Edit"))',
      bulkSaveDialog: '.p-dialog:has(.p-dialog-title:text("Confirm Bulk Save"))',
      reconcileDialog: '.p-dialog:has(.p-dialog-title:has-text("Reconcile"))',
      confirmDialog: '.p-confirmdialog',
      timelineRegenerationModal: '.p-dialog:has(.p-dialog-title:text-matches("Timeline"))',

      // Dialog inputs
      editFavoriteNameInput: '#name',
      dialogSaveButton: '.p-dialog button:has-text("Save")',
      dialogCancelButton: '.p-dialog button:has-text("Cancel")',
      dialogConfirmButton: '.p-confirmdialog-accept-button',
      dialogRejectButton: '.p-confirmdialog-reject-button',

      // Context menus
      contextMenu: '.p-contextmenu',
      contextMenuItem: '.p-contextmenu-item-label',

      // Toast notifications
      successToast: '.p-toast-message-success',
      infoToast: '.p-toast-message-info',
      errorToast: '.p-toast-message-error',
      toastDetail: '.p-toast-detail',
    };
  }

  // ===========================================
  // NAVIGATION
  // ===========================================

  async navigate() {
    await this.page.goto('/app/favorites-management');
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageContainer, { timeout: 10000 });
    await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 5000 });
    await this.waitForMapReady();
  }

  async isOnFavoritesPage() {
    try {
      await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  async openNavigationMenu() {
    // Click the hamburger menu button
    const menuButton = this.page.locator('button:has(.pi-bars)');
    await menuButton.click();
    await this.page.waitForTimeout(500);
  }

  async clickNavigationLink(linkText) {
    // Open menu first
    await this.openNavigationMenu();

    // Match the label text exactly to avoid collisions like "Timeline" vs "Timeline Preferences".
    const escapedLinkText = linkText.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const exactLabelPattern = new RegExp(`^\\s*${escapedLinkText}\\s*$`);
    const link = this.page
      .locator('nav:visible a.gp-nav-item-link')
      .filter({
        has: this.page.locator('.gp-nav-item-label', { hasText: exactLabelPattern })
      });

    await expect(link).toHaveCount(1);
    await link.click();
  }

  // ===========================================
  // MAP OPERATIONS
  // ===========================================

  async waitForMapReady() {
    await this.mapHarness.waitForMapReady({
      mapId: 'favorites-map',
      timeout: 15000,
      settleMs: 1200
    });
  }

  async rightClickOnMap(x, y) {
    await this.mapHarness.rightClickOnMap(x, y, { mapId: 'favorites-map' });
    await this.page.waitForTimeout(700);
  }

  async waitForMapContextMenu() {
    await this.page.waitForSelector(this.selectors.contextMenu, {
      state: 'visible',
      timeout: 10000
    });
  }

  async clickContextMenuItem(text) {
    const menuItem = this.page.locator(this.selectors.contextMenuItem, { hasText: text });
    await menuItem.click();
    await this.page.waitForSelector(this.selectors.contextMenu, {
      state: 'hidden',
      timeout: 5000
    }).catch(() => {});
  }

  async drawRectangle(startX, startY, endX, endY, mapId = null) {
    const resolvedMapId = mapId || 'favorites-map';
    await this.waitForDrawingMode(resolvedMapId, { required: false });
    await this.page.waitForTimeout(120);
    await this.mapHarness.drawRectangle(startX, startY, endX, endY, {
      mapId: resolvedMapId
    });
  }

  async waitForDrawingMode(mapId = 'favorites-map', options = {}) {
    const mapHost = this.mapHarness.getMapHostLocator({ mapId });
    const timeout = options.timeout ?? 3500;
    const required = options.required ?? false;

    try {
      await expect.poll(async () => {
        return mapHost.evaluate((element) => {
          const nodes = [element, ...element.querySelectorAll('*')];
          return nodes.some((node) => {
            const cursor = window.getComputedStyle(node).cursor || '';
            return cursor.includes('crosshair') || cursor.includes('cell');
          });
        }).catch(() => false);
      }, { timeout }).toBe(true);

      return true;
    } catch (error) {
      if (required) {
        throw error;
      }
      return false;
    }
  }

  async cancelDrawing() {
    await this.page.keyboard.press('Escape');
    await this.page.waitForTimeout(500);
  }

  async countFavoriteMarkers() {
    const markers = this.page.locator(this.selectors.favoriteMarker);
    return await markers.count();
  }

  async countPendingMarkers() {
    const markers = this.page.locator(this.selectors.pendingPointMarker);
    return await markers.count();
  }

  async countFavoriteAreas() {
    // Count both the rectangles and the center markers for favorite areas
    const areaIcons = this.page.locator(this.selectors.favoriteAreaMarker);
    return await areaIcons.count();
  }

  async rightClickFavoriteMarker(index = 0) {
    const marker = this.page.locator(this.selectors.favoriteMarker).nth(index);
    await marker.waitFor({ state: 'visible', timeout: 5000 });
    await marker.click({ button: 'right', force: true });
    await this.page.waitForTimeout(1000);
  }

  async rightClickPendingMarker(index = 0) {
    const marker = this.page.locator(this.selectors.pendingPointMarker).nth(index);
    await marker.waitFor({ state: 'visible', timeout: 5000 });
    await marker.click({ button: 'right', force: true });
    await this.page.waitForTimeout(1000);
  }

  // ===========================================
  // BULK MODE
  // ===========================================

  async toggleBulkMode() {
    await this.page.click(this.selectors.bulkModeButton);
    await this.page.waitForTimeout(500);
  }

  async isBulkModeEnabled() {
    const button = this.page.locator(this.selectors.bulkModeButton);
    const text = await button.textContent();
    return text.includes('ON');
  }

  async clickSavePending() {
    await this.page.click(this.selectors.savePendingButton);
  }

  async getPendingCount() {
    const button = this.page.locator(this.selectors.savePendingButton);
    const text = await button.textContent();
    const match = text.match(/Save (\d+) Pending/);
    return match ? parseInt(match[1]) : 0;
  }

  async isSavePendingButtonVisible() {
    const button = this.page.locator(this.selectors.savePendingButton);
    return await button.isVisible();
  }

  // ===========================================
  // PENDING FAVORITES PANEL
  // ===========================================

  async isPendingPanelVisible() {
    const panel = this.page.locator(this.selectors.pendingPanel);
    return await panel.isVisible();
  }

  async getPendingItemCount() {
    const items = this.page.locator(this.selectors.pendingItem);
    return await items.count();
  }

  async getPendingItemName(index) {
    const item = this.page.locator(this.selectors.pendingItem).nth(index);
    const nameEl = item.locator('.item-name');
    return await nameEl.textContent();
  }

  async removePendingItem(index) {
    const item = this.page.locator(this.selectors.pendingItem).nth(index);
    const removeButton = item.locator('button:has(.pi-trash)');
    await removeButton.click();
    await this.page.waitForTimeout(500);
  }

  async clickClearPending() {
    await this.page.click(this.selectors.clearPendingButton);
  }

  async clickSavePendingFromPanel() {
    await this.page.click(this.selectors.savePendingPanelButton);
  }

  // ===========================================
  // FILTERS
  // ===========================================

  async selectTypeFilter(type) {
    // type: 'POINT', 'AREA', or null for 'All Types'
    await this.page.click(this.selectors.typeSelect);
    await this.page.waitForTimeout(300);

    const optionText = type === 'POINT' ? 'Point' : type === 'AREA' ? 'Area' : 'All Types';
    await this.page.click(`.p-select-option:has-text("${optionText}")`);
    await this.page.waitForTimeout(500);
  }

  async fillSearchInput(text) {
    await this.page.fill(this.selectors.searchInput, text);
    await this.page.waitForTimeout(600); // Wait for debounce
  }

  async clearFilters() {
    await this.page.click(this.selectors.clearFiltersButton);
    await this.page.waitForTimeout(500);
  }

  async isClearFiltersButtonEnabled() {
    const button = this.page.locator(this.selectors.clearFiltersButton);
    return await button.isEnabled();
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

    // Try to get type (might not be visible on mobile)
    let type = null;
    const typeTag = row.locator('.type-tag');
    if (await typeTag.count() > 0) {
      type = await typeTag.textContent();
    }

    return { name: name.trim(), type };
  }

  async selectTableRow(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const checkbox = row.locator(this.selectors.tableCheckbox);
    await checkbox.click();
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

  async clickDeleteInTable(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const deleteButton = row.locator(this.selectors.deleteButton);
    await deleteButton.click();
  }

  async clickShowOnMap(index) {
    const row = this.page.locator(this.selectors.tableRow).nth(index);
    const mapButton = row.locator(this.selectors.mapButton);
    await mapButton.click();
    await this.page.waitForTimeout(1000);
  }

  async isTableEmpty() {
    const emptyState = this.page.locator('.empty-state');
    return await emptyState.isVisible();
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

  // ===========================================
  // DIALOGS
  // ===========================================

  getAddDialog() {
    return this.page
      .getByRole('dialog')
      .filter({ hasText: /Add (Point|Area) to Favorites/ })
      .last();
  }

  async waitForAddDialog() {
    await this.getAddDialog().waitFor({ state: 'visible', timeout: 10000 });
  }

  async fillAddDialog(name) {
    await this.waitForAddDialog();
    const dialog = this.getAddDialog();
    const input = dialog.locator('input').first();
    await expect(input).toBeVisible({ timeout: 10000 });
    await expect(input).toBeEditable({ timeout: 10000 });
    await input.fill(name);
  }

  async submitAddDialog() {
    const dialog = this.getAddDialog();
    await dialog.locator('button:has-text("Save")').click();
    await dialog.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
  }

  async closeAddDialog() {
    const dialog = this.getAddDialog();
    await dialog.locator('button:has-text("Cancel")').click();
    await dialog.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
  }

  async waitForEditDialog() {
    await this.page.waitForSelector(this.selectors.editDialog, {
      state: 'visible',
      timeout: 10000
    });
  }

  async fillEditDialog(newName) {
    await this.waitForEditDialog();
    const dialog = this.page.locator(this.selectors.editDialog).first();
    const input = dialog.locator(this.selectors.editFavoriteNameInput).first();
    await input.fill(newName);
  }

  async submitEditDialog() {
    const dialog = this.page.locator(this.selectors.editDialog).first();
    await dialog.locator('button:has-text("Save")').click();
  }

  async waitForBulkEditDialog() {
    await this.page.waitForSelector(this.selectors.bulkEditDialog, { timeout: 5000 });
  }

  async fillBulkEditField(fieldName, value) {
    await this.waitForBulkEditDialog();

    // Find the field by label and fill it
    const dialog = this.page.locator(this.selectors.bulkEditDialog);
    const field = dialog.locator(`input[placeholder*="${fieldName}"]`);
    await field.fill(value);
  }

  async submitBulkEdit() {
    const dialog = this.page.locator(this.selectors.bulkEditDialog);
    const saveButton = dialog.locator('button:has-text("Save")');
    await saveButton.click();
  }

  async waitForBulkSaveDialog() {
    await this.page.waitForSelector(this.selectors.bulkSaveDialog, { timeout: 5000 });
  }

  async getBulkSaveDialogCounts() {
    await this.waitForBulkSaveDialog();
    const dialog = this.page.locator(this.selectors.bulkSaveDialog);
    const text = await dialog.textContent();

    const pointsMatch = text.match(/(\d+) point/);
    const areasMatch = text.match(/(\d+) area/);

    return {
      points: pointsMatch ? parseInt(pointsMatch[1]) : 0,
      areas: areasMatch ? parseInt(areasMatch[1]) : 0
    };
  }

  async confirmBulkSave() {
    const dialog = this.page.locator(this.selectors.bulkSaveDialog);
    const confirmButton = dialog.locator('button:has-text("Confirm")');
    await confirmButton.click();
  }

  async cancelBulkSave() {
    const dialog = this.page.locator(this.selectors.bulkSaveDialog);
    const cancelButton = dialog.locator('button:has-text("Cancel")');
    await cancelButton.click();
  }

  async waitForReconcileDialog() {
    await this.page.waitForSelector(this.selectors.reconcileDialog, { timeout: 5000 });
  }

  async selectReconcileProviders(providers) {
    await this.waitForReconcileDialog();

    for (const provider of providers) {
      const checkbox = this.page.locator(`.p-checkbox-label:has-text("${provider}")`).locator('..');
      await checkbox.click();
    }
  }

  async submitReconcile() {
    const dialog = this.page.locator(this.selectors.reconcileDialog);
    const button = dialog.locator('button:has-text("Start Reconciliation")');
    await button.click();
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

  async waitForTimelineRegenerationModal() {
    await this.page.waitForSelector(this.selectors.timelineRegenerationModal, { timeout: 5000 });
  }

  async waitForTimelineRegenerationToComplete() {
    await this.page.waitForSelector(this.selectors.timelineRegenerationModal, {
      state: 'hidden',
      timeout: 30000
    });
  }

  async waitForTimelineRegenerationCycle(options = {}) {
    const appearanceTimeout = options.appearanceTimeout ?? 7000;
    const completionTimeout = options.completionTimeout ?? 45000;
    const optional = options.optional ?? true;

    try {
      await this.page.waitForSelector(this.selectors.timelineRegenerationModal, { timeout: appearanceTimeout });
    } catch (error) {
      if (optional) {
        return false;
      }
      throw error;
    }

    await this.page.waitForSelector(this.selectors.timelineRegenerationModal, {
      state: 'hidden',
      timeout: completionTimeout
    });

    return true;
  }

  // ===========================================
  // TOAST NOTIFICATIONS
  // ===========================================

  async waitForToastBySelector(selector, expectedText = null, timeout = 15000) {
    const visibleSelector = `${selector}:visible`;

    if (!expectedText) {
      await expect.poll(
        () => this.page.locator(visibleSelector).count(),
        { timeout }
      ).toBeGreaterThan(0);
      return;
    }

    await expect.poll(async () => {
      const texts = await this.page.locator(visibleSelector).allInnerTexts().catch(() => []);
      return texts.join(' | ');
    }, { timeout }).toContain(expectedText);
  }

  async waitForSuccessToast(expectedText = null, options = {}) {
    const timeout = options.timeout ?? 20000;
    const allowInfoFallback = options.allowInfoFallback ?? true;
    const required = options.required ?? true;

    try {
      await this.waitForToastBySelector(this.selectors.successToast, expectedText, timeout);
      return 'success';
    } catch (error) {
      if (!required) {
        return null;
      }

      if (!allowInfoFallback) {
        throw error;
      }

      await this.waitForToastBySelector(this.selectors.infoToast, expectedText, timeout);
      return 'info';
    }
  }

  async waitForInfoToast() {
    await this.waitForToastBySelector(this.selectors.infoToast, null, 10000);
  }

  async waitForErrorToast() {
    await this.waitForToastBySelector(this.selectors.errorToast, null, 10000);
  }

  async getToastMessage() {
    const toast = this.page.locator(this.selectors.toastDetail).first();
    return await toast.textContent();
  }

  // ===========================================
  // WORKFLOW HELPERS
  // ===========================================

  async addFavoritePointWorkflow(x, y, name) {
    await this.rightClickOnMap(x, y);
    await this.waitForMapContextMenu();
    await this.clickContextMenuItem('Add to Favorites');
    await this.fillAddDialog(name);
    await this.submitAddDialog();
  }

  async addFavoriteAreaWorkflow(startX, startY, endX, endY, name) {
    await this.rightClickOnMap(startX, startY);
    await this.waitForMapContextMenu();
    await this.clickContextMenuItem('Add an area to Favorites');
    await this.drawRectangle(startX, startY, endX, endY);
    await this.fillAddDialog(name);
    await this.submitAddDialog();
  }

  async addFavoritePointInBulkMode(x, y, name) {
    // Ensure bulk mode is on
    if (!await this.isBulkModeEnabled()) {
      await this.toggleBulkMode();
    }

    await this.rightClickOnMap(x, y);
    await this.waitForMapContextMenu();
    await this.clickContextMenuItem('Add to Favorites');
    await this.fillAddDialog(name);
    await this.submitAddDialog();

    // Should add to pending, not trigger timeline regeneration
    await this.page.waitForTimeout(1000);
  }

  async editFavoriteWorkflow(index, newName) {
    await this.clickEditInTable(index);
    await this.fillEditDialog(newName);
    await this.submitEditDialog();

    // Wait for success toast (timeline regeneration happens in background)
    await this.waitForSuccessToast();

    // Wait a bit for database update to complete
    await this.page.waitForTimeout(2000);
  }

  async deleteFavoriteWorkflow(index) {
    const initialRowCount = await this.getTableRowCount();
    await this.clickDeleteInTable(index);
    await this.confirmDialog();
    await this.waitForTimelineRegenerationCycle({ optional: true });
    await this.waitForSuccessToast(null, { required: false, timeout: 20000 });

    await expect.poll(async () => {
      const currentCount = await this.getTableRowCount();
      if (currentCount < initialRowCount) {
        return true;
      }

      return await this.isTableEmpty().catch(() => false);
    }, { timeout: 20000 }).toBe(true);
  }

  async bulkSaveWorkflow() {
    await this.clickSavePending();
    await this.waitForBulkSaveDialog();
    await this.confirmBulkSave();
    await this.waitForTimelineRegenerationCycle({ optional: true });
    await this.waitForSuccessToast(null, { required: false, timeout: 20000 });
  }
}
