import { expect } from '@playwright/test';

export class FavoritesManagementPage {
  constructor(page) {
    this.page = page;

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
      leafletMap: '.leaflet-container',
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
      addDialog: '.p-dialog:has(.p-dialog-title:text("Add Point to Favorites")), .p-dialog:has(.p-dialog-title:text("Add Area to Favorites"))',
      editDialog: '.p-dialog:has(.p-dialog-title:text("Edit Favorite Location"))',
      bulkEditDialog: '.p-dialog:has(.p-dialog-title:text("Bulk Edit"))',
      bulkSaveDialog: '.p-dialog:has(.p-dialog-title:text("Confirm Bulk Save"))',
      reconcileDialog: '.p-dialog:has(.p-dialog-title:has-text("Reconcile"))',
      confirmDialog: '.p-confirmdialog',
      timelineRegenerationModal: '.p-dialog:has(.p-dialog-title:text-matches("Timeline"))',

      // Dialog inputs
      favoriteNameInput: '.p-dialog input[placeholder*="name"]',
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

    // Click the navigation link
    const link = this.page.locator(`nav a:has-text("${linkText}")`);
    await link.click();
  }

  // ===========================================
  // MAP OPERATIONS
  // ===========================================

  async waitForMapReady() {
    await this.page.waitForSelector(this.selectors.leafletMap, { timeout: 10000 });
    await this.page.waitForSelector('.leaflet-tile-pane', {
      state: 'attached',
      timeout: 10000
    });
    await this.page.waitForTimeout(1500);
  }

  async rightClickOnMap(x, y) {
    const mapContainer = this.page.locator(this.selectors.leafletMap);
    await mapContainer.waitFor({ state: 'visible' });

    const boundingBox = await mapContainer.boundingBox();
    if (boundingBox) {
      const safeX = Math.min(x, boundingBox.width - 10);
      const safeY = Math.min(y, boundingBox.height - 10);

      await mapContainer.click({
        position: { x: safeX, y: safeY },
        button: 'right',
        force: true
      });
    }

    await this.page.waitForTimeout(1000);
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
  }

  async drawRectangle(startX, startY, endX, endY, mapId = null) {
    // If mapId is specified, use it; otherwise use the main map
    const mapSelector = mapId ? `#${mapId}` : '#favorites-map';
    const mapContainer = this.page.locator(mapSelector);

    // Get the bounding box of the map
    const boundingBox = await mapContainer.boundingBox();
    if (!boundingBox) {
      throw new Error('Map container not found or not visible');
    }

    // Calculate absolute coordinates
    const startAbsX = boundingBox.x + startX;
    const startAbsY = boundingBox.y + startY;
    const endAbsX = boundingBox.x + endX;
    const endAbsY = boundingBox.y + endY;

    // Simulate click-and-drag: move to start, hold down, drag to end, release
    await this.page.mouse.move(startAbsX, startAbsY);
    await this.page.waitForTimeout(100); // Small delay before pressing

    await this.page.mouse.down(); // Press and HOLD left button
    await this.page.waitForTimeout(200); // Hold for a moment to ensure mousedown registers

    // Drag to end position while holding button
    // Use intermediate steps for more realistic dragging
    const steps = 10;
    for (let i = 1; i <= steps; i++) {
      const intermediateX = startAbsX + ((endAbsX - startAbsX) * i / steps);
      const intermediateY = startAbsY + ((endAbsY - startAbsY) * i / steps);
      await this.page.mouse.move(intermediateX, intermediateY);
      await this.page.waitForTimeout(20); // Small delay between moves
    }

    await this.page.waitForTimeout(100); // Pause at end position
    await this.page.mouse.up(); // Release button

    await this.page.waitForTimeout(500); // Wait for rectangle to be created
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

  async waitForAddDialog() {
    await this.page.waitForSelector(this.selectors.addDialog, { timeout: 5000 });
  }

  async fillAddDialog(name) {
    await this.waitForAddDialog();
    await this.page.fill(this.selectors.favoriteNameInput, name);
  }

  async submitAddDialog() {
    await this.page.click(this.selectors.dialogSaveButton);
  }

  async closeAddDialog() {
    await this.page.click(this.selectors.dialogCancelButton);
  }

  async waitForEditDialog() {
    await this.page.waitForSelector(this.selectors.editDialog, { timeout: 5000 });
  }

  async fillEditDialog(newName) {
    await this.waitForEditDialog();
    const input = this.page.locator(this.selectors.favoriteNameInput);
    await input.clear();
    await input.fill(newName);
  }

  async submitEditDialog() {
    await this.page.click(this.selectors.dialogSaveButton);
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
    await this.clickDeleteInTable(index);
    await this.confirmDialog();
    await this.waitForTimelineRegenerationModal();
    await this.waitForTimelineRegenerationToComplete();
    await this.waitForSuccessToast();
  }

  async bulkSaveWorkflow() {
    await this.clickSavePending();
    await this.waitForBulkSaveDialog();
    await this.confirmBulkSave();
    await this.waitForTimelineRegenerationModal();
    await this.waitForTimelineRegenerationToComplete();
    await this.waitForSuccessToast();
  }
}
