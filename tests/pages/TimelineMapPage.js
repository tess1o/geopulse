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
    
    // Wait for map loading to complete first
    await this.waitForMapLoadingToComplete();
    
    // Wait for map tiles to start loading (check for tile layer)
    // Use state: 'attached' instead of visible since tiles might be loading
    await this.page.waitForSelector('.leaflet-tile-pane', { 
      state: 'attached',
      timeout: 10000 
    });
    
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
      // Try multiple approaches to get the map instance
      const mapContainer = document.querySelector('.leaflet-container');
      
      // Approach 1: Direct _leaflet_map property
      if (mapContainer && mapContainer._leaflet_map) {
        const center = mapContainer._leaflet_map.getCenter();
        return { lat: center.lat, lng: center.lng };
      }
      
      // Approach 2: Try to find map through Vue component
      if (window.Vue && mapContainer) {
        const vueComponent = mapContainer.__vue__;
        if (vueComponent && vueComponent.map) {
          const center = vueComponent.map.getCenter();
          return { lat: center.lat, lng: center.lng };
        }
      }
      
      // Approach 3: Check for global map instances
      if (window.L && window.L.map) {
        const center = window.L.map.getCenter();
        return { lat: center.lat, lng: center.lng };
      }
      
      // Approach 4: Look for any Leaflet map instances in global scope
      if (typeof window !== 'undefined') {
        for (const key in window) {
          if (window[key] && typeof window[key].getCenter === 'function') {
            try {
              const center = window[key].getCenter();
              return { lat: center.lat, lng: center.lng };
            } catch (e) {
              // Continue if this wasn't a valid map instance
            }
          }
        }
      }
      
      console.warn('Could not find Leaflet map instance for center coordinates');
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
    
    // First ensure the map container is visible and has size
    await mapContainer.waitFor({ state: 'visible' });
    
    // Try multiple approaches to trigger context menu
    try {
      // Approach 1: Regular right-click with position
      const boundingBox = await mapContainer.boundingBox();
      if (boundingBox) {
        const safeX = Math.min(x, boundingBox.width - 10);
        const safeY = Math.min(y, boundingBox.height - 10);
        
        await mapContainer.click({ 
          position: { x: safeX, y: safeY }, 
          button: 'right',
          force: true
        });
      } else {
        // Fallback: click on the center
        await mapContainer.click({ 
          button: 'right',
          force: true
        });
      }
      
      // Wait for context menu to appear
      await this.page.waitForTimeout(1000);
      
    } catch (error) {
      console.log('Right-click approach 1 failed, trying alternative...');
      
      // Approach 2: Use dispatchEvent for contextmenu
      await this.page.evaluate((coords) => {
        const container = document.querySelector('.leaflet-container');
        if (container) {
          const event = new MouseEvent('contextmenu', {
            bubbles: true,
            cancelable: true,
            clientX: coords.x,
            clientY: coords.y,
            button: 2
          });
          container.dispatchEvent(event);
        }
      }, { x, y });
      
      await this.page.waitForTimeout(1000);
    }
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
      friends: '.control-button[title*="Friends"]',
      favorites: '.control-button[title*="Favorites"]', 
      timeline: '.control-button[title*="Timeline"]',
      path: '.control-button[title*="Path"]',
      immich: '.control-button[title*="Photos"]'
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
      friends: '.control-button[title*="Friends"]',
      favorites: '.control-button[title*="Favorites"]',
      timeline: '.control-button[title*="Timeline"]', 
      path: '.control-button[title*="Path"]',
      immich: '.control-button[title*="Photos"]'
    };
    
    const selector = layerControls[layerType];
    if (!selector) {
      throw new Error(`Unknown layer type: ${layerType}`);
    }
    
    const button = this.page.locator(selector);
    // Check if button has 'active' class instead of aria-pressed
    const className = await button.getAttribute('class');
    return className && className.includes('active');
  }

  /**
   * Click the "Zoom to Data" button
   */
  async clickZoomToData() {
    await this.page.click('.control-button[title="Zoom to Data"]');
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
    return this.page.locator('.timeline-marker');
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
    // Favorite markers have a star icon inside them
    return this.page.locator('.leaflet-marker-pane .fas.fa-star').locator('..');
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
   * Get favorite area polygons on the map
   */
  getFavoritePolygons() {
    // Favorite areas are rendered as SVG polygons or paths in the leaflet overlay pane
    return this.page.locator('.leaflet-overlay-pane svg polygon, .leaflet-overlay-pane svg path[fill]');
  }

  /**
   * Count favorite area polygons
   */
  async countFavoritePolygons() {
    const polygons = this.getFavoritePolygons();
    return await polygons.count();
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
    try {
      // Increase timeout and wait for the element to be visible, not just present
      await this.page.waitForSelector('.p-contextmenu', { 
        state: 'visible',
        timeout: 10000 
      });
    } catch (error) {
      // Debug: Check what elements are present
      const allMenus = await this.page.locator('[class*="menu"]').count();
      const allContexts = await this.page.locator('[class*="context"]').count();
      console.log(`Debug: Found ${allMenus} menu elements, ${allContexts} context elements`);
      
      // Check for alternative context menu selectors
      const altSelectors = [
        '.context-menu',
        '.contextmenu',
        '.p-menu',
        '[role="menu"]',
        '.leaflet-contextmenu'
      ];
      
      for (const selector of altSelectors) {
        const count = await this.page.locator(selector).count();
        if (count > 0) {
          console.log(`Debug: Found alternative selector: ${selector} (${count} elements)`);
        }
      }
      
      throw error;
    }
  }

  /**
   * Click a context menu item
   */
  async clickContextMenuItem(text) {
    const menuItem = this.page.locator('.p-contextmenu-item-label', { hasText: text });
    await menuItem.click();
  }

  /**
   * Check if context menu is visible
   */
  async isContextMenuVisible() {
    const contextMenu = this.page.locator('.p-contextmenu');
    return await contextMenu.isVisible();
  }

  // ===========================================
  // DIALOGS AND MODALS
  // ===========================================

  /**
   * Wait for Add Favorite dialog to appear
   */
  async waitForAddFavoriteDialog() {
    // Wait for dialog to appear and ensure it has the correct title
    await this.page.waitForSelector('.p-dialog', { timeout: 5000 });
    await this.page.waitForSelector('.p-dialog-title:text("Add To Favorites")', { timeout: 2000 });
  }

  /**
   * Fill and submit the Add Favorite dialog
   */
  async submitAddFavoriteDialog(favoriteName) {
    await this.waitForAddFavoriteDialog();
    
    // Use the actual input selector from the HTML
    const nameInput = this.page.locator('.p-dialog .p-inputtext[placeholder="Location name"]');
    await nameInput.fill(favoriteName);
    
    // Click the Save button using getByText for reliability
    const submitButton = this.page.locator('.p-dialog').getByRole('button', { name: 'Save' });
    await submitButton.click();
  }

  /**
   * Wait for Add Area Favorite dialog to appear
   */
  async waitForAddAreaFavoriteDialog() {
    // Wait for dialog to appear and ensure it has the correct title
    await this.page.waitForSelector('.p-dialog', { timeout: 5000 });
    await this.page.waitForSelector('.p-dialog-title:text("Add Area To Favorites")', { timeout: 2000 });
  }

  /**
   * Fill and submit the Add Area Favorite dialog
   */
  async submitAddAreaFavoriteDialog(areaName) {
    await this.waitForAddAreaFavoriteDialog();
    
    // Use the actual input selector from the HTML
    const nameInput = this.page.locator('.p-dialog .p-inputtext[placeholder="Location name"]');
    await nameInput.fill(areaName);
    
    // Click the Save button
    const submitButton = this.page.locator('.p-dialog').getByRole('button', { name: 'Save' });
    await submitButton.click();
  }

  /**
   * Close Add Favorite dialog
   */
  async closeAddFavoriteDialog() {
    // Click the Cancel button
    const cancelButton = this.page.locator('.p-dialog').getByRole('button', { name: 'Cancel' });
    await cancelButton.click();
  }

  /**
   * Wait for Edit Favorite dialog to appear
   */
  async waitForEditFavoriteDialog() {
    // Wait for dialog to appear and ensure it has the correct title
    await this.page.waitForSelector('.p-dialog', { timeout: 5000 });
    await this.page.waitForSelector('.p-dialog-title:text("Edit Favorite")', { timeout: 2000 });
  }

  /**
   * Fill and submit the Edit Favorite dialog
   */
  async submitEditFavoriteDialog(newName) {
    await this.waitForEditFavoriteDialog();
    
    const nameInput = this.page.locator('.p-dialog .p-inputtext[placeholder="Enter location name"]');
    await nameInput.clear();
    await nameInput.fill(newName);
    
    const submitButton = this.page.locator('.p-dialog').getByRole('button', { name: 'Save' });
    await submitButton.click();
  }

  /**
   * Wait for Timeline Regeneration modal to appear
   */
  async waitForTimelineRegenerationModal() {
    await this.page.waitForSelector('.timeline-regeneration-modal', { timeout: 5000 });
    await this.page.waitForSelector('.p-dialog-title:text("Timeline Regeneration")', { timeout: 2000 });
  }

  /**
   * Wait for Timeline Regeneration modal to disappear
   */
  async waitForTimelineRegenerationModalToClose() {
    await this.page.waitForSelector('.timeline-regeneration-modal', { 
      state: 'hidden', 
      timeout: 15000 // Regeneration can take time
    });
  }

  /**
   * Wait for Photo Viewer dialog
   */
  async waitForPhotoViewer() {
    await this.page.waitForSelector('.p-dialog', { timeout: 5000 });
    // Photo viewer has title like "Photos (1/2)"
    await this.page.waitForSelector('.p-dialog-title:text-matches("Photos \\\\(\\\\d+/\\\\d+\\\\)")', { timeout: 2000 });
  }

  /**
   * Close Photo Viewer dialog
   */
  async closePhotoViewer() {
    // Try the Close button in photo actions first
    const closeButton = this.page.locator('.photo-actions .p-button:has(.p-button-label:text("Close"))');
    if (await closeButton.count() > 0) {
      await closeButton.click();
    } else {
      // Fallback to X button in header
      const headerCloseButton = this.page.locator('.p-dialog .p-dialog-close-button');
      await headerCloseButton.click();
    }
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
    const markerSelectors = {
      timeline: '.timeline-marker',
      favorite: '.leaflet-marker-pane .fas.fa-star',
      'current-location': '.leaflet-marker-icon[data-marker-type="current-location"]'
    };
    
    const selector = markerSelectors[markerType];
    if (!selector) {
      throw new Error(`Unknown marker type: ${markerType}`);
    }
    
    const markers = this.page.locator(selector);
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
    await this.waitForSuccessToast('Favorite point added');
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
    
    // Fill and submit area dialog (use area-specific method)
    await this.submitAddAreaFavoriteDialog(areaName);
    
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
    await this.waitForSuccessToast('updated successfully');
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
    await this.page.waitForSelector('.p-confirmdialog', { timeout: 5000 });
    await this.page.waitForSelector('.p-dialog-title:text("Delete Favorite")', { timeout: 2000 });
    
    // Click the "Yes" button to confirm deletion
    await this.page.click('.p-confirmdialog-accept-button');
    
    // Wait for timeline regeneration to complete
    await this.waitForTimelineRegenerationModal();
    await this.waitForTimelineRegenerationModalToClose();
    
    // Wait for success notification
    await this.waitForSuccessToast('deleted');
    await this.page.waitForTimeout(1000)
  }
}