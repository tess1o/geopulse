import { expect } from '@playwright/test';

export class SharedLocationPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Page elements
      logo: '.logo',
      appTitle: '.app-title',
      themeToggle: '.large-theme-toggle',

      // Loading state
      loadingState: '.loading-state',
      loadingSpinner: '.p-progress-spinner',
      loadingMessage: 'text="Loading shared location..."',

      // Error state
      errorState: '.error-state',
      errorCard: '.error-card',
      errorIcon: '.error-icon',
      errorMessage: '.error-message',
      errorTitle: '.error-message h3',
      errorText: '.error-message p',
      retryButton: 'button:has-text("Try Again")',

      // Password required state
      passwordState: '.password-state',
      passwordCard: '.password-card',
      passwordIcon: '.password-icon',
      passwordForm: '.password-form',
      passwordTitle: '.password-form h3',
      passwordDescription: '.password-form p',
      passwordInput: '.password-input input',
      accessButton: 'button:has-text("Access")',

      // Location display
      locationDisplay: '.location-display',
      locationInfoCard: '.location-info-card',
      shareTitle: '.share-title',
      shareDescription: '.share-description',

      // Info grid
      infoGrid: '.info-grid',
      infoItem: '.info-item',
      infoLabel: '.info-label',
      infoValue: '.info-value',

      // Specific info items
      sharedByInfo: '.info-item:has(.info-label:has-text("Shared by"))',
      lastSeenInfo: '.info-item:has(.info-label:has-text("Last seen"))',
      expiresInfo: '.info-item:has(.info-label:has-text("Expires"))',
      scopeInfo: '.info-item:has(.info-label:has-text("Scope"))',

      // Map
      mapCard: '.map-card',
      mapHeader: '.map-header',
      mapTitle: '.map-title',
      refreshButton: 'button:has(.pi-refresh)',
      mapContainer: '.map-container',
      leafletMap: '.leaflet-container',

      // Map markers and layers
      locationMarker: '.leaflet-marker-icon',
      pathLayer: '.leaflet-overlay-pane path',

      // No data state
      noDataState: '.no-data-state',
      noDataCard: '.no-data-card',
      noDataIcon: '.no-data-icon',
      noDataTitle: '.no-data-content h3',
      noDataMessage: '.no-data-content p',

      // Footer
      footer: '.shared-footer',
      footerText: '.footer-text',
      getAppButton: 'button:has-text("Get GeoPulse")'
    };
  }

  /**
   * Navigation Methods
   */
  async navigateToSharedLink(linkId) {
    await this.page.goto(`/shared/${linkId}`);
  }

  async isOnSharedLocationPage() {
    try {
      const url = this.page.url();
      return url.includes('/shared/');
    } catch {
      return false;
    }
  }

  async waitForPageLoad() {
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(500);
  }

  /**
   * State Check Methods
   */
  async isLoading() {
    return await this.page.locator(this.selectors.loadingState).isVisible();
  }

  async isErrorShown() {
    return await this.page.locator(this.selectors.errorState).isVisible();
  }

  async isPasswordRequired() {
    return await this.page.locator(this.selectors.passwordState).isVisible();
  }

  async isLocationDisplayed() {
    return await this.page.locator(this.selectors.locationDisplay).isVisible();
  }

  async isNoDataShown() {
    return await this.page.locator(this.selectors.noDataState).isVisible();
  }

  /**
   * Error State Methods
   */
  async getErrorMessage() {
    const errorText = await this.page.locator(this.selectors.errorText).textContent();
    return errorText.trim();
  }

  async clickRetry() {
    await this.page.locator(this.selectors.retryButton).click();
  }

  /**
   * Password Methods
   */
  async enterPassword(password) {
    await this.page.locator(this.selectors.passwordInput).fill(password);
  }

  async clickAccessButton() {
    await this.page.locator(this.selectors.accessButton).click();
  }

  async submitPassword(password) {
    await this.enterPassword(password);
    await this.clickAccessButton();
  }

  async waitForPasswordPrompt() {
    await this.page.waitForSelector(this.selectors.passwordState, { state: 'visible', timeout: 5000 });
  }

  /**
   * Location Info Methods
   */
  async getShareTitle() {
    return await this.page.locator(this.selectors.shareTitle).textContent();
  }

  async getShareDescription() {
    const desc = await this.page.locator(this.selectors.shareDescription).textContent();
    return desc ? desc.trim() : null;
  }

  async getInfoValue(label) {
    const infoItem = this.page.locator(`.info-item:has(.info-label:has-text("${label}"))`);
    const value = await infoItem.locator(this.selectors.infoValue).textContent();
    return value.trim();
  }

  async getSharedBy() {
    return await this.getInfoValue('Shared by');
  }

  async getLastSeen() {
    return await this.getInfoValue('Last seen');
  }

  async getExpires() {
    return await this.getInfoValue('Expires');
  }

  async getScope() {
    return await this.getInfoValue('Scope');
  }

  async isExpirationWarning() {
    const expiresValue = this.page.locator(this.selectors.expiresInfo).locator(this.selectors.infoValue);
    const classes = await expiresValue.getAttribute('class');
    return classes.includes('expiring-soon') || classes.includes('expired');
  }

  /**
   * Map Methods
   */
  async isMapDisplayed() {
    return await this.page.locator(this.selectors.leafletMap).isVisible();
  }

  async waitForMapReady() {
    await this.page.waitForSelector(this.selectors.leafletMap, { state: 'visible', timeout: 10000 });
    // Wait for map tiles to load
    await this.page.waitForTimeout(1000);
  }

  async getMapTitle() {
    return await this.page.locator(this.selectors.mapTitle).textContent();
  }

  async hasLocationMarker() {
    // Wait for SVG circle markers to be rendered (they appear as path elements in leaflet-overlay-pane)
    try {
      await this.page.waitForFunction(
        () => {
          // Check for SVG circle markers (rendered as path elements)
          const paths = document.querySelectorAll('.leaflet-overlay-pane path.leaflet-interactive[fill="#9c27b0"]');
          if (paths.length > 0) return true;

          // Fallback: check for any markers in marker pane
          const markerPane = document.querySelector('.leaflet-marker-pane');
          return markerPane && markerPane.children.length > 0;
        },
        { timeout: 10000 }
      );
      return true;
    } catch (error) {
      console.log('No markers found after waiting 10 seconds');
      return false;
    }
  }

  async hasPathLayer() {
    // Path layer (history) is a polyline, not a circle marker
    // Circle markers have 'd' attribute like "M549,272a12,12 0 1,0 24,0 a12,12 0 1,0 -24,0"
    // Polylines have 'd' attribute like "M100,200L150,300L200,400" (L for line-to commands)
    const hasPolyline = await this.page.evaluate(() => {
      const paths = document.querySelectorAll('.leaflet-overlay-pane path.leaflet-interactive');
      for (const path of paths) {
        const d = path.getAttribute('d');
        // Polylines contain 'L' (line-to) commands, circles use 'a' (arc) commands
        if (d && d.includes('L') && !d.includes('a12,12')) {
          return true;
        }
      }
      return false;
    });
    return hasPolyline;
  }

  async getMarkerCount() {
    return await this.page.locator(this.selectors.locationMarker).count();
  }

  async clickRefreshButton() {
    await this.page.locator(this.selectors.refreshButton).click();
  }

  async waitForRefresh() {
    // Wait for refresh to complete
    await this.page.waitForTimeout(1000);
  }

  /**
   * Wait Methods
   */
  async waitForLocationToLoad() {
    await this.page.waitForSelector(this.selectors.locationDisplay, { state: 'visible', timeout: 10000 });
  }

  async waitForError() {
    await this.page.waitForSelector(this.selectors.errorState, { state: 'visible', timeout: 5000 });
  }

  /**
   * Database Helper Methods
   */
  static async insertGpsPoint(dbManager, pointData) {
    const gpsQuery = `
      INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
    `;

    const gpsValues = [
      pointData.device_id || 'test-device',
      pointData.user_id,
      `POINT(${pointData.longitude} ${pointData.latitude})`, // PostGIS POINT format: lon, lat
      pointData.timestamp,
      pointData.accuracy || 10.0,
      pointData.battery || 100,
      pointData.velocity || 0.0,
      pointData.altitude || 20.0,
      pointData.source_type || 'OWNTRACKS',
      pointData.created_at || pointData.timestamp
    ];

    await dbManager.client.query(gpsQuery, gpsValues);
  }

  static async insertMultipleGpsPoints(dbManager, points) {
    for (const point of points) {
      await this.insertGpsPoint(dbManager, point);
    }
  }

  static async createGpsPointsForUser(dbManager, userId, count = 5) {
    const now = new Date();
    const points = [];

    for (let i = 0; i < count; i++) {
      const timestamp = new Date(now.getTime() - (count - i) * 60000); // 1 minute apart
      const point = {
        user_id: userId,
        device_id: 'test-device',
        latitude: 51.5074 + (Math.random() - 0.5) * 0.01, // London area
        longitude: -0.1278 + (Math.random() - 0.5) * 0.01,
        timestamp: timestamp.toISOString(),
        accuracy: 10.0,
        battery: 100 - i,
        velocity: i === count - 1 ? 0.0 : 5.0, // Last point is stationary
        altitude: 20.0,
        source_type: 'OWNTRACKS',
        created_at: timestamp.toISOString()
      };
      points.push(point);
    }

    await this.insertMultipleGpsPoints(dbManager, points);
    return points;
  }

  static async getViewCount(dbManager, linkId) {
    const result = await dbManager.client.query(
      'SELECT view_count FROM shared_link WHERE id = $1',
      [linkId]
    );
    return result.rows[0] ? parseInt(result.rows[0].view_count) : 0;
  }

  static async updateLinkViewCount(dbManager, linkId, viewCount) {
    await dbManager.client.query(
      'UPDATE shared_link SET view_count = $1 WHERE id = $2',
      [viewCount, linkId]
    );
  }
}
