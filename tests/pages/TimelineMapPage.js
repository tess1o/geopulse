import { expect } from '@playwright/test';
import { MAP_POPUP_CONTENT_SELECTOR, MapEngineHarness } from '../utils/map-engine-harness.js';

export class TimelineMapPage {
  constructor(page) {
    this.page = page;
    this.mapHarness = new MapEngineHarness(page);
    this.selectors = {
      mapHost: '[data-testid="map-host-raster"], [data-testid="map-host-vector"]',
      popupContent: MAP_POPUP_CONTENT_SELECTOR,
      favoriteMarker: [
        '.map-view-container .custom-marker.favorite-marker',
        '.map-view-container .custom-marker.favorite-location-marker',
        '.map-view-container .favorite-marker-icon',
        '.map-view-container .leaflet-marker-pane .leaflet-marker-icon.favorite-location-marker',
        '.map-view-container .leaflet-interactive[role="button"]:has(i.fas.fa-star)',
        '.map-view-container .maplibregl-marker:has(i.fas.fa-star)'
      ].join(', '),
    };
  }

  // ===========================================
  // MAP INITIALIZATION AND BASIC OPERATIONS
  // ===========================================

  /**
   * Wait for the map to be fully initialized and ready for interaction
   */
  async waitForMapReady() {
    await this.page.waitForSelector('.map-view-container', { timeout: 10000 });
    await this.mapHarness.waitForMapReady({
      rootSelector: '.map-view-container',
      timeout: 15000,
      settleMs: 1200
    });
  }

  /**
   * Get the map's current center coordinates
   */
  async getMapCenter() {
    return await this.page.evaluate(() => {
      const host = document.querySelector('[data-testid="map-host-vector"], [data-testid="map-host-raster"]');
      const mapId = host?.id;
      const registry = window.__GP_E2E_MAPS || {};
      const registeredMap = mapId ? registry[mapId] : null;

      if (registeredMap && typeof registeredMap.getCenter === 'function') {
        const center = registeredMap.getCenter();
        return { lat: center.lat, lng: center.lng };
      }

      const leafletContainer = document.querySelector('.leaflet-container');
      if (leafletContainer && leafletContainer._leaflet_map) {
        const center = leafletContainer._leaflet_map.getCenter();
        return { lat: center.lat, lng: center.lng };
      }

      return null;
    });
  }

  /**
   * Click on the map at specific coordinates (relative to map container)
   */
  async clickOnMap(x, y) {
    const mapHost = this.page.locator(this.selectors.mapHost).first();
    await mapHost.click({ position: { x, y }, force: true });
  }

  /**
   * Right-click on the map at specific coordinates
   */
  async rightClickOnMap(x, y) {
    await this.mapHarness.rightClickOnMap(x, y, {
      rootSelector: '.map-view-container'
    });
    await this.page.waitForTimeout(700);
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

  getPopupContent() {
    return this.page.locator(this.selectors.popupContent).first();
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
    return this.page.locator('.custom-marker.timeline-marker, .leaflet-marker-pane .timeline-marker');
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
    await marker.waitFor({ state: 'visible', timeout: 10000 });
    await marker.click();
  }

  /**
   * Get all favorite markers on the map
   */
  getFavoriteMarkers() {
    return this.page.locator(this.selectors.favoriteMarker);
  }

  /**
   * Right-click on a favorite marker
   */
  async rightClickFavoriteMarker(index = 0) {
    const mode = await this.mapHarness.getActiveMode({ rootSelector: '.map-view-container' });

    if (mode === 'VECTOR') {
      const vectorMarker = this.page.locator(
        '.map-view-container .maplibregl-marker.custom-marker.favorite-marker, ' +
        '.map-view-container .maplibregl-marker.custom-marker.favorite-location-marker, ' +
        '.map-view-container .maplibregl-marker .custom-marker.favorite-marker, ' +
        '.map-view-container .maplibregl-marker .custom-marker.favorite-location-marker'
      ).nth(index);

      await vectorMarker.waitFor({ state: 'visible', timeout: 10000 });
      await vectorMarker.scrollIntoViewIfNeeded();
      await this.page.waitForTimeout(100);

      await this.tryTriggerContextMenuOnMarker(vectorMarker);
      if (await this.isContextMenuVisible()) {
        return;
      }
      throw new Error(`Could not open vector favorite context menu for marker index ${index}`);
    }

    const marker = this.page.locator(
      '.map-view-container .custom-marker.favorite-marker, ' +
      '.map-view-container .custom-marker.favorite-location-marker, ' +
      '.map-view-container .leaflet-interactive[role="button"]:has(i.fas.fa-star), ' +
      '.map-view-container .leaflet-marker-pane .leaflet-marker-icon.favorite-location-marker'
    ).nth(index);
    await marker.waitFor({ state: 'visible', timeout: 10000 });
    await marker.scrollIntoViewIfNeeded();
    await this.page.waitForTimeout(100);

    await this.tryTriggerContextMenuOnMarker(marker);
    if (await this.isContextMenuVisible()) {
      return;
    }

    // Last-resort fallback: trigger Leaflet marker contextmenu directly from layer object for raster.
    const firedFromLayer = await this.page.evaluate((targetIndex) => {
      const host = document.querySelector('.map-view-container [data-testid="map-host-raster"]');
      const mapId = host?.id || null;
      const registry = window.__GP_E2E_MAPS || {};
      const map = mapId ? registry[mapId] : null;
      if (!map || typeof map.eachLayer !== 'function') {
        return false;
      }

      const favoriteLayers = [];
      map.eachLayer((layer) => {
        if (!layer || typeof layer.fire !== 'function' || typeof layer.getLatLng !== 'function') {
          return;
        }
        if (layer?.options?.favorite || layer?.options?.favoriteIndex !== undefined) {
          favoriteLayers.push(layer);
        }
      });

      const layer = favoriteLayers[targetIndex] || favoriteLayers[0];
      if (!layer) {
        return false;
      }

      const latlng = layer.getLatLng?.();
      layer.fire('contextmenu', {
        latlng,
        originalEvent: {
          preventDefault() {},
          stopPropagation() {},
          stopImmediatePropagation() {}
        }
      });
      return true;
    }, index);

    if (!firedFromLayer) {
      throw new Error(`Could not open favorite context menu for marker index ${index}`);
    }
  }

  async tryTriggerContextMenuOnMarker(markerLocator) {
    try {
      await markerLocator.click({ button: 'right', force: true, timeout: 3000 });
      await this.page.waitForTimeout(250);
      if (await this.isContextMenuVisible()) {
        return true;
      }
    } catch {
      // Fall through to alternative strategies.
    }

    const markerBox = await markerLocator.boundingBox();
    if (markerBox) {
      await this.page.mouse.click(
        markerBox.x + (markerBox.width / 2),
        markerBox.y + (markerBox.height / 2),
        { button: 'right' }
      );
      await this.page.waitForTimeout(250);
      if (await this.isContextMenuVisible()) {
        return true;
      }
    }

    const handle = await markerLocator.elementHandle();
    if (handle) {
      await handle.evaluate((element) => {
        const target = element instanceof HTMLElement ? element : null;
        if (!target) {
          return;
        }

        const icon = target.querySelector('i.fas.fa-star, i.pi.pi-map-marker');
        const eventTarget = (icon instanceof HTMLElement ? icon : target);
        const rect = eventTarget.getBoundingClientRect();
        const clientX = rect.left + (rect.width / 2);
        const clientY = rect.top + (rect.height / 2);

        eventTarget.dispatchEvent(new PointerEvent('pointerdown', {
          bubbles: true,
          cancelable: true,
          button: 2,
          buttons: 2,
          pointerType: 'mouse',
          clientX,
          clientY
        }));

        eventTarget.dispatchEvent(new MouseEvent('contextmenu', {
          bubbles: true,
          cancelable: true,
          button: 2,
          buttons: 2,
          clientX,
          clientY
        }));

        eventTarget.dispatchEvent(new PointerEvent('pointerup', {
          bubbles: true,
          cancelable: true,
          button: 2,
          buttons: 0,
          pointerType: 'mouse',
          clientX,
          clientY
        }));
      });
      await this.page.waitForTimeout(250);
      return await this.isContextMenuVisible();
    }

    return false;
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
    const mode = await this.mapHarness.getActiveMode({ rootSelector: '.map-view-container' });
    if (mode === 'VECTOR') {
      const rendered = await this.mapHarness.countVectorRenderedFeatures({
        rootSelector: '.map-view-container',
        layerIncludes: ['gp-favorites', 'areas']
      });
      if (rendered.count > 0) {
        return rendered.count;
      }

      const summary = await this.mapHarness.countVectorSourceFeatures({
        rootSelector: '.map-view-container',
        sourceIncludes: ['gp-favorites', 'areas-source']
      });
      return summary.count;
    }

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
    return this.page.locator('.leaflet-marker-icon[data-marker-type="current-location"], .maplibre-shared-location-dot, .maplibre-avatar-icon-container');
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
  async waitForTimelineRegenerationModal(options = {}) {
    const timeout = options.timeout ?? 7000;
    const required = options.required ?? true;

    try {
      await this.page.waitForSelector('.timeline-regeneration-modal', { timeout });
      await this.page.waitForSelector('.p-dialog-title:text("Timeline Regeneration")', { timeout: 3000 });
      return true;
    } catch (error) {
      if (!required) {
        return false;
      }
      throw error;
    }
  }

  /**
   * Wait for Timeline Regeneration modal to disappear
   */
  async waitForTimelineRegenerationModalToClose(options = {}) {
    const timeout = options.timeout ?? 30000;
    await this.page.waitForSelector('.timeline-regeneration-modal', {
      state: 'hidden',
      timeout
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
    await this.mapHarness.drawRectangle(startX, startY, endX, endY, {
      rootSelector: '.map-view-container'
    });
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
    return await this.page.evaluate(() => {
      const mapHost = document.querySelector('[data-testid="map-host-raster"], [data-testid="map-host-vector"]');
      if (!mapHost) {
        return false;
      }

      const hostCursor = getComputedStyle(mapHost).cursor;
      if (hostCursor === 'crosshair') {
        return true;
      }

      const canvas = mapHost.querySelector('canvas');
      const canvasCursor = canvas ? getComputedStyle(canvas).cursor : null;
      return canvasCursor === 'crosshair';
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
      timeline: '.custom-marker.timeline-marker, .leaflet-marker-pane .timeline-marker',
      favorite: this.selectors.favoriteMarker,
      'current-location': '.leaflet-marker-icon[data-marker-type="current-location"], .maplibre-shared-location-dot, .maplibre-avatar-icon-container'
    };
    
    const selector = markerSelectors[markerType];
    if (!selector) {
      throw new Error(`Unknown marker type: ${markerType}`);
    }

    if (markerType === 'favorite') {
      const mode = await this.mapHarness.getActiveMode({ rootSelector: '.map-view-container' });
      if (mode === 'RASTER') {
        const layerCount = await this.page.evaluate(() => {
          const host = document.querySelector('.map-view-container [data-testid="map-host-raster"]');
          const mapId = host?.id || null;
          const registry = window.__GP_E2E_MAPS || {};
          const map = mapId ? registry[mapId] : null;

          if (!map || typeof map.eachLayer !== 'function') {
            return 0;
          }

          let count = 0;
          map.eachLayer((layer) => {
            if (!layer || typeof layer.getLatLng !== 'function') {
              return;
            }

            if (layer?.options?.favorite || layer?.options?.favoriteIndex !== undefined) {
              count += 1;
            }
          });

          return count;
        });

        if (layerCount > 0) {
          return layerCount;
        }
      }
    }
    
    const markers = this.page.locator(selector);
    return await markers.count();
  }

  async focusMapOnCoordinates(latitude, longitude, zoom = 12) {
    await this.page.evaluate(({ latitude: lat, longitude: lng, zoomLevel }) => {
      const host = document.querySelector('[data-testid="map-host-vector"], [data-testid="map-host-raster"]');
      const mapId = host?.id || null;
      const registry = window.__GP_E2E_MAPS || {};
      const map = mapId ? registry[mapId] : null;

      if (map && typeof map.setView === 'function') {
        map.setView([lat, lng], zoomLevel, { animate: false });
        return;
      }

      if (map && typeof map.jumpTo === 'function') {
        map.jumpTo({ center: [lng, lat], zoom: zoomLevel });
        return;
      }

      const leafletContainer = document.querySelector('.leaflet-container');
      const leafletMap = leafletContainer?._leaflet_map;
      if (leafletMap && typeof leafletMap.setView === 'function') {
        leafletMap.setView([lat, lng], zoomLevel, { animate: false });
      }
    }, { latitude, longitude, zoomLevel: zoom });

    await this.page.waitForTimeout(800);
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
  async waitForSuccessToast(expectedText = null, options = {}) {
    const timeout = options.timeout ?? 15000;
    const allowInfoFallback = options.allowInfoFallback ?? true;
    const required = options.required ?? true;

    const visibleSelector = allowInfoFallback
      ? '.p-toast-message-success:visible, .p-toast-message-info:visible'
      : '.p-toast-message-success:visible';

    try {
      await expect.poll(() => this.page.locator(visibleSelector).count(), { timeout }).toBeGreaterThan(0);
    } catch (error) {
      if (!required) {
        return null;
      }
      throw error;
    }

    if (!expectedText) {
      return true;
    }

    await expect.poll(async () => {
      const texts = await this.page.locator(visibleSelector).allInnerTexts().catch(() => []);
      return texts.join(' | ');
    }, { timeout }).toContain(expectedText);

    return true;
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
    const modalVisible = await this.waitForTimelineRegenerationModal({ required: false, timeout: 7000 });
    if (modalVisible) {
      await this.waitForTimelineRegenerationModalToClose({ timeout: 40000 });
    }
    
    // Wait for success notification
    await this.waitForSuccessToast(null, { required: false, timeout: 12000 });
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
    const modalVisible = await this.waitForTimelineRegenerationModal({ required: false, timeout: 7000 });
    if (modalVisible) {
      await this.waitForTimelineRegenerationModalToClose({ timeout: 40000 });
    }
    
    // Wait for success notification
    await this.waitForSuccessToast(null, { required: false, timeout: 12000 });
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
    await this.waitForSuccessToast(null, { required: false, timeout: 12000 });
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
    const modalVisible = await this.waitForTimelineRegenerationModal({ required: false, timeout: 7000 });
    if (modalVisible) {
      await this.waitForTimelineRegenerationModalToClose({ timeout: 40000 });
    }
    
    // Wait for success notification
    await this.waitForSuccessToast(null, { required: false, timeout: 12000 });
    await this.page.waitForTimeout(600);
  }
}
