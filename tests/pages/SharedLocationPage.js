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
      expiresInfo: '.info-item:has(.info-label:has-text("Expires"))',

      // Map
      refreshButton: 'button:has(.pi-refresh)',
      leafletMap: '.leaflet-container',


      // No data state
      noDataState: '.no-data-state',
      noDataIcon: '.no-data-icon',
      noDataMessage: '.no-data-content p',

      // Footer
      footer: '.shared-footer',
    };
  }

  /**
   * Navigation Methods
   */
  async navigateToSharedLink(linkId) {
    await this.page.goto(`/shared/${linkId}`);
  }

  async waitForPageLoad() {
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(500);
  }

  /**
   * State Check Methods
   */

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

  async getInfoValue(label) {
    const infoItem = this.page.locator(`.info-item:has(.info-label:has-text("${label}"))`);
    const value = await infoItem.locator(this.selectors.infoValue).textContent();
    return value.trim();
  }

  async getSharedBy() {
    return await this.getInfoValue('Shared by');
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

  /**
   * Wait Methods
   */
  async waitForLocationToLoad() {
    await this.page.waitForSelector(this.selectors.locationDisplay, { state: 'visible', timeout: 10000 });
  }

  async waitForError() {
    await this.page.waitForSelector(this.selectors.errorState, { state: 'visible', timeout: 5000 });
  }
}
