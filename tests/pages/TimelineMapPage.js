import { expect } from '@playwright/test';

export class TimelineMapPage {
  constructor(page) {
    this.page = page;
  }

  // ===========================================
  // MAP INITIALIZATION AND BASIC OPERATIONS
  // ===========================================

  /**
   * Wait for the map to be fully initialized and ready for interaction
   */
  async waitForMapReady() {
    // Wait for map container to be visible
    await this.page.waitForSelector('.map-view-container', { timeout: 10000 });
    
    // Wait for Leaflet map to be initialized (check for leaflet-container class)
    await this.page.waitForSelector('.leaflet-container', { timeout: 10000 });
    
    // Wait for map tiles to start loading (check for tile layer)
    await this.page.waitForSelector('.leaflet-tile-pane', { timeout: 10000 });
    
    // Give additional time for tiles to load and map to settle
    await this.page.waitForTimeout(2000);
    
    // Verify map is interactive (zoom controls present)
    await this.page.waitForSelector('.leaflet-control-zoom', { timeout: 5000 });
  }

  /**
   * Get the map's current center coordinates
   */
  async getMapCenter() {
    return await this.page.evaluate(() => {
      const mapContainer = document.querySelector('.leaflet-container');
      if (mapContainer && mapContainer._leaflet_map) {
        const center = mapContainer._leaflet_map.getCenter();
        return { lat: center.lat, lng: center.lng };
      }
      return null;
    });
  }

  /**
   * Get the map's current zoom level
   */
  async getMapZoom() {
    return await this.page.evaluate(() => {
      const mapContainer = document.querySelector('.leaflet-container');
      if (mapContainer && mapContainer._leaflet_map) {
        return mapContainer._leaflet_map.getZoom();
      }
      return null;
    });
  }

  /**
   * Click on the map at specific coordinates (relative to map container)
   */
  async clickOnMap(x, y) {
    const mapContainer = this.page.locator('.leaflet-container');
    await mapContainer.click({ position: { x, y } });
  }

  /**
   * Right-click on the map at specific coordinates
   */
  async rightClickOnMap(x, y) {
    const mapContainer = this.page.locator('.leaflet-container');
    await mapContainer.click({ 
      position: { x, y }, 
      button: 'right' 
    });
  }

  // ===========================================
  // MAP CONTROLS
  // ===========================================

  /**
   * Get all map control buttons
   */
  getMapControls() {
    return this.page.locator('.map-controls');
  }

  /**
   * Toggle a specific layer control
   */
  async toggleLayerControl(layerType) {
    const layerControls = {
      friends: '[data-testid="toggle-friends"]',
      favorites: '[data-testid="toggle-favorites"]', 
      timeline: '[data-testid="toggle-timeline"]',
      path: '[data-testid="toggle-path"]',
      immich: '[data-testid="toggle-immich"]'
    };
    
    const selector = layerControls[layerType];
    if (!selector) {
      throw new Error(`Unknown layer type: ${layerType}`);
    }
    
    await this.page.click(selector);
  }

  /**
   * Check if a layer control is active/pressed
   */
  async isLayerActive(layerType) {
    const layerControls = {
      friends: '[data-testid="toggle-friends"]',
      favorites: '[data-testid="toggle-favorites"]',
      timeline: '[data-testid="toggle-timeline"]', 
      path: '[data-testid="toggle-path"]',
      immich: '[data-testid="toggle-immich"]'
    };
    
    const selector = layerControls[layerType];
    if (!selector) {
      throw new Error(`Unknown layer type: ${layerType}`);
    }
    
    const button = this.page.locator(selector);
    const isPressed = await button.getAttribute('aria-pressed');
    return isPressed === 'true';
  }

  /**
   * Click the "Zoom to Data" button
   */
  async clickZoomToData() {
    await this.page.click('[data-testid="zoom-to-data"]');
    // Wait for zoom animation to complete
    await this.page.waitForTimeout(1000);
  }

  // ===========================================
  // LAYER ELEMENTS (MARKERS, PATHS, ETC.)
  // ===========================================

  /**
   * Get all timeline markers on the map
   */
  getTimelineMarkers() {
    // Timeline markers should have specific classes or data attributes
    return this.page.locator('.leaflet-marker-icon[data-marker-type="timeline"]');
  }

  /**
   * Get a specific timeline marker by index
   */
  getTimelineMarker(index) {
    return this.getTimelineMarkers().nth(index);
  }

  /**
   * Click on a timeline marker
   */
  async clickTimelineMarker(index = 0) {
    const marker = this.getTimelineMarker(index);
    await marker.click();
  }

  /**
   * Get all favorite markers on the map
   */
  getFavoriteMarkers() {
    return this.page.locator('.leaflet-marker-icon[data-marker-type="favorite"]');
  }

  /**
   * Right-click on a favorite marker
   */
  async rightClickFavoriteMarker(index = 0) {
    const marker = this.getFavoriteMarkers().nth(index);
    await marker.click({ button: 'right' });
  }

  /**
   * Get path lines on the map
   */
  getPathLines() {
    return this.page.locator('.leaflet-interactive[data-layer-type="path"]');
  }

  /**
   * Click on a path line
   */
  async clickPathLine(index = 0) {
    const pathLine = this.getPathLines().nth(index);
    await pathLine.click();
  }

  /**
   * Get current location marker
   */
  getCurrentLocationMarker() {
    return this.page.locator('.leaflet-marker-icon[data-marker-type="current-location"]');
  }

  /**
   * Check if current location marker is visible
   */
  async isCurrentLocationVisible() {
    const marker = this.getCurrentLocationMarker();
    return await marker.isVisible();
  }

  // ===========================================
  // CONTEXT MENUS
  // ===========================================

  /**
   * Wait for map context menu to appear
   */
  async waitForMapContextMenu() {
    await this.page.waitForSelector('.p-contextmenu', { timeout: 5000 });
  }

  /**
   * Click a context menu item
   */
  async clickContextMenuItem(text) {
    const menuItem = this.page.locator('.p-menuitem-text', { hasText: text });
    await menuItem.click();
  }

  /**
   * Check if context menu is visible
   */
  async isContextMenuVisible() {
    return await this.page.locator('.p-contextmenu').isVisible();
  }

  // ===========================================
  // DIALOGS AND MODALS
  // ===========================================

  /**
   * Wait for Add Favorite dialog to appear
   */
  async waitForAddFavoriteDialog() {
    await this.page.waitForSelector('[data-testid="add-favorite-dialog"]', { timeout: 5000 });
  }

  /**
   * Fill and submit the Add Favorite dialog
   */
  async submitAddFavoriteDialog(favoriteName) {
    await this.waitForAddFavoriteDialog();
    
    const nameInput = this.page.locator('[data-testid="favorite-name-input"]');
    await nameInput.fill(favoriteName);
    
    const submitButton = this.page.locator('[data-testid="add-favorite-submit"]');
    await submitButton.click();
  }

  /**
   * Close Add Favorite dialog
   */
  async closeAddFavoriteDialog() {
    const closeButton = this.page.locator('[data-testid="add-favorite-close"]');
    await closeButton.click();
  }

  /**
   * Wait for Edit Favorite dialog to appear
   */
  async waitForEditFavoriteDialog() {
    await this.page.waitForSelector('[data-testid="edit-favorite-dialog"]', { timeout: 5000 });
  }

  /**
   * Fill and submit the Edit Favorite dialog
   */
  async submitEditFavoriteDialog(newName) {
    await this.waitForEditFavoriteDialog();
    
    const nameInput = this.page.locator('[data-testid="edit-favorite-name-input"]');
    await nameInput.clear();
    await nameInput.fill(newName);
    
    const submitButton = this.page.locator('[data-testid="edit-favorite-submit"]');
    await submitButton.click();
  }

  /**
   * Wait for Timeline Regeneration modal to appear
   */
  async waitForTimelineRegenerationModal() {
    await this.page.waitForSelector('[data-testid="timeline-regeneration-modal"]', { timeout: 5000 });
  }

  /**
   * Wait for Timeline Regeneration modal to disappear
   */
  async waitForTimelineRegenerationModalToClose() {
    await this.page.waitForSelector('[data-testid="timeline-regeneration-modal"]', { 
      state: 'hidden', 
      timeout: 15000 // Regeneration can take time
    });
  }

  /**
   * Wait for Photo Viewer dialog
   */
  async waitForPhotoViewer() {
    await this.page.waitForSelector('[data-testid="photo-viewer-dialog"]', { timeout: 5000 });
  }

  /**
   * Close Photo Viewer dialog
   */
  async closePhotoViewer() {
    const closeButton = this.page.locator('[data-testid="photo-viewer-close"]');
    await closeButton.click();
  }

  // ===========================================
  // RECTANGLE DRAWING TOOL
  // ===========================================

  /**
   * Simulate drawing a rectangle on the map
   */
  async drawRectangle(startX, startY, endX, endY) {
    const mapContainer = this.page.locator('.leaflet-container');
    
    // Start drawing by mouse down at start position
    await mapContainer.hover({ position: { x: startX, y: startY } });
    await this.page.mouse.down();
    
    // Drag to end position
    await mapContainer.hover({ position: { x: endX, y: endY } });
    
    // Complete drawing by mouse up
    await this.page.mouse.up();
    
    // Wait for drawing to complete and dialog to appear
    await this.page.waitForTimeout(500);
  }

  /**
   * Cancel rectangle drawing using Escape key
   */
  async cancelRectangleDrawing() {
    await this.page.keyboard.press('Escape');
  }

  /**
   * Check if currently in drawing mode
   */
  async isInDrawingMode() {
    // Check for drawing cursor or drawing-related classes
    return await this.page.evaluate(() => {
      const mapContainer = document.querySelector('.leaflet-container');
      return mapContainer ? mapContainer.style.cursor === 'crosshair' : false;
    });
  }

  // ===========================================
  // DATA VERIFICATION METHODS
  // ===========================================

  /**
   * Count the number of visible markers of a specific type
   */
  async countMarkers(markerType) {
    const markers = this.page.locator(`.leaflet-marker-icon[data-marker-type="${markerType}"]`);
    return await markers.count();
  }

  /**
   * Verify that map has loaded with data
   */
  async verifyMapHasData() {
    await this.waitForMapReady();
    
    // Check if any markers or paths are visible
    const timelineMarkers = await this.countMarkers('timeline');
    const favoriteMarkers = await this.countMarkers('favorite');
    const pathLines = await this.getPathLines().count();
    
    return timelineMarkers > 0 || favoriteMarkers > 0 || pathLines > 0;
  }

  /**
   * Verify that a marker is highlighted
   */
  async verifyMarkerIsHighlighted(markerIndex = 0) {
    const marker = this.getTimelineMarker(markerIndex);
    const className = await marker.getAttribute('class');
    return className && className.includes('highlighted');
  }

  /**
   * Wait for map bounds to fit data
   */
  async waitForMapToFitData() {
    // Wait for auto-fit bounds to complete
    await this.page.waitForTimeout(1000);
    
    // Verify map has reasonable zoom and center
    const zoom = await this.getMapZoom();
    expect(zoom).toBeGreaterThan(5); // Should zoom in to show data
    expect(zoom).toBeLessThan(20); // But not too close
  }

  // ===========================================
  // ERROR HANDLING AND STATES
  // ===========================================

  /**
   * Check if map is showing no data message
   */
  async isShowingNoDataMessage() {
    const noDataMessage = this.page.locator('.loading-messages').filter({ 
      hasText: 'No data to show on the map' 
    });
    return await noDataMessage.isVisible();
  }

  /**
   * Check if map is in loading state
   */
  async isMapLoading() {
    const loadingSpinner = this.page.locator('.loading-messages .p-progress-spinner');
    return await loadingSpinner.isVisible();
  }

  /**
   * Wait for map loading to complete
   */
  async waitForMapLoadingToComplete() {
    await this.page.waitForSelector('.loading-messages .p-progress-spinner', { 
      state: 'hidden', 
      timeout: 10000 
    });
  }

  // ===========================================
  // TOAST NOTIFICATIONS
  // ===========================================

  /**
   * Wait for a success toast notification
   */
  async waitForSuccessToast(expectedText = null) {
    await this.page.waitForSelector('.p-toast-message-success', { timeout: 5000 });
    
    if (expectedText) {
      const toastText = await this.page.locator('.p-toast-message-success .p-toast-detail').textContent();
      expect(toastText).toContain(expectedText);
    }
  }

  /**
   * Wait for an error toast notification
   */
  async waitForErrorToast(expectedText = null) {
    await this.page.waitForSelector('.p-toast-message-error', { timeout: 5000 });
    
    if (expectedText) {
      const toastText = await this.page.locator('.p-toast-message-error .p-toast-detail').textContent();
      expect(toastText).toContain(expectedText);
    }
  }

  // ===========================================
  // COMPLEX WORKFLOW HELPERS
  // ===========================================

  /**
   * Complete workflow: Add favorite point via context menu
   */
  async addFavoritePointWorkflow(x, y, favoriteName) {
    // Right-click on map
    await this.rightClickOnMap(x, y);
    
    // Wait for context menu and click "Add to Favorites"
    await this.waitForMapContextMenu();
    await this.clickContextMenuItem('Add to Favorites');
    
    // Fill and submit dialog
    await this.submitAddFavoriteDialog(favoriteName);
    
    // Wait for timeline regeneration to complete
    await this.waitForTimelineRegenerationModal();
    await this.waitForTimelineRegenerationModalToClose();
    
    // Wait for success notification
    await this.waitForSuccessToast('favorite');
  }

  /**
   * Complete workflow: Add favorite area via drawing
   */
  async addFavoriteAreaWorkflow(startX, startY, endX, endY, areaName) {
    // Right-click on map to open context menu
    await this.rightClickOnMap(startX, startY);
    
    // Click "Add an area to Favorites"
    await this.waitForMapContextMenu();
    await this.clickContextMenuItem('Add an area to Favorites');
    
    // Draw rectangle
    await this.drawRectangle(startX, startY, endX, endY);
    
    // Fill and submit area dialog
    await this.submitAddFavoriteDialog(areaName);
    
    // Wait for timeline regeneration to complete
    await this.waitForTimelineRegenerationModal();
    await this.waitForTimelineRegenerationModalToClose();
    
    // Wait for success notification
    await this.waitForSuccessToast('area');
  }

  /**
   * Complete workflow: Edit favorite via context menu
   */
  async editFavoriteWorkflow(markerIndex, newName) {
    // Right-click on favorite marker
    await this.rightClickFavoriteMarker(markerIndex);
    
    // Wait for context menu and click "Edit"
    await this.waitForMapContextMenu();
    await this.clickContextMenuItem('Edit');
    
    // Fill and submit edit dialog
    await this.submitEditFavoriteDialog(newName);
    
    // Wait for success notification
    await this.waitForSuccessToast('renamed');
  }

  /**
   * Complete workflow: Delete favorite via context menu
   */
  async deleteFavoriteWorkflow(markerIndex) {
    // Right-click on favorite marker
    await this.rightClickFavoriteMarker(markerIndex);
    
    // Wait for context menu and click "Delete"
    await this.waitForMapContextMenu();
    await this.clickContextMenuItem('Delete');
    
    // Confirm deletion in confirmation dialog
    await this.page.waitForSelector('.p-confirm-dialog', { timeout: 5000 });
    await this.page.click('.p-confirm-dialog-accept');
    
    // Wait for timeline regeneration to complete
    await this.waitForTimelineRegenerationModal();
    await this.waitForTimelineRegenerationModalToClose();
    
    // Wait for success notification
    await this.waitForSuccessToast('deleted');
  }
}