import { expect } from '@playwright/test';

export class SharedTimelinePage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Header elements
      header: '.shared-header',
      headerAuthenticated: '.header-authenticated',
      headerMinimal: '.header-minimal',
      brand: '.brand',
      timelineName: '.timeline-name',
      statusTag: '.status-tag',
      themeToggle: 'button:has(.dark-mode-switcher)',

      // Header rows
      headerRow1: '.header-row-1',
      headerRow2: '.header-row-2',
      headerRow3: '.header-row-3',

      // Meta information
      metaCompact: '.meta-compact',
      metaSeparator: '.meta-separator',

      // Date filter
      dateRangePicker: '.header-datepicker',
      clearFilterButton: 'button[aria-label="Clear date filter"]',
      refreshButton: 'button[aria-label="Refresh data"]',

      // Loading state
      loadingState: '.state-container:has(.p-progress-spinner)',
      loadingSpinner: '.p-progress-spinner',
      loadingMessage: 'text="Loading shared timeline..."',

      // Password state
      passwordCard: '.password-card',
      passwordInput: '#password input',
      passwordError: '.p-error',
      accessButton: 'button:has-text("Access Timeline")',

      // Upcoming timeline state
      upcomingCard: '.info-card',
      upcomingIcon: '.pi-calendar',
      upcomingMessage: 'text="This trip hasn\'t started yet."',
      tripBeginsInfo: '.detail-item:has(.pi-calendar-plus)',

      // Error state
      errorCard: '.error-card',
      errorIcon: '.pi-exclamation-circle',
      errorTitle: 'h2:has-text("Unable to Load Timeline")',
      errorMessage: '.error-card p',

      // Timeline view
      timelineView: '.timeline-view',
      timelineContainer: '.timeline-container',

      // Empty states
      emptyTimelineState: '.empty-timeline-state',
      emptyMapState: '.empty-map-state',
      noDataIcon: '.pi-map-marker',

      // Timeline map
      timelineMap: '.timeline-map',
      leafletMap: '.leaflet-container',
      mapMarkers: '.leaflet-marker-pane',
      mapPaths: '.leaflet-overlay-pane path',
      currentLocationMarker: '.current-location-marker',
      photoMarkers: '.photo-marker',

      // Timeline sidebar
      timelineSidebar: '.timeline-sidebar',
      timelineItems: '.timeline-item',
      stayCard: '.timeline-item.stay',
      tripCard: '.timeline-item.trip',

      // Cards
      card: '.p-card',
      cardHeader: '.p-card-header',
      cardContent: '.p-card-content',

      // Buttons
      button: 'button',

      // Status indicators
      statusSuccess: '.p-tag-success',
      statusInfo: '.p-tag-info',
      statusSecondary: '.p-tag-secondary',
    };
  }

  /**
   * Navigation Methods
   */
  async navigateToSharedTimeline(linkId) {
    await this.page.goto(`/shared-timeline/${linkId}`);
  }

  async isOnSharedTimelinePage() {
    try {
      const url = this.page.url();
      return url.includes('/shared-timeline/');
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

  async isPasswordRequired() {
    return await this.page.locator(this.selectors.passwordCard).isVisible();
  }

  async isErrorShown() {
    return await this.page.locator(this.selectors.errorCard).isVisible();
  }

  async isUpcomingTrip() {
    return await this.page.locator(this.selectors.upcomingCard).isVisible();
  }

  async isTimelineDisplayed() {
    return await this.page.locator(this.selectors.timelineView).isVisible();
  }

  async hasTimelineData() {
    const itemCount = await this.page.locator(this.selectors.timelineItems).count();
    return itemCount > 0;
  }

  async hasMapData() {
    const mapVisible = await this.page.locator(this.selectors.leafletMap).isVisible();
    if (!mapVisible) return false;

    // Check for paths or markers
    const pathCount = await this.page.locator(this.selectors.mapPaths).count();
    return pathCount > 0;
  }

  /**
   * Header Methods
   */
  async getTimelineName() {
    return await this.page.locator(this.selectors.timelineName).textContent();
  }

  async getStatus() {
    const statusTag = this.page.locator(this.selectors.statusTag);
    return await statusTag.textContent();
  }

  async getStatusSeverity() {
    const statusTag = this.page.locator(this.selectors.statusTag);
    const classes = await statusTag.getAttribute('class');

    if (classes.includes('p-tag-success')) return 'success';
    if (classes.includes('p-tag-info')) return 'info';
    if (classes.includes('p-tag-secondary')) return 'secondary';
    return 'unknown';
  }

  async getSharedBy() {
    const metaText = await this.page.locator('.header-row-2').textContent();
    // Extract username from "👤 UserName • 🕒 Expires..."
    const match = metaText.match(/👤\s*(.+?)\s*•/);
    return match ? match[1].trim() : null;
  }

  async getExpirationText() {
    const metaText = await this.page.locator('.header-row-2').textContent();
    // Extract expiration from "... • 🕒 Expires Feb 6, 2026"
    const match = metaText.match(/Expires\s+(.+)/);
    return match ? match[1].trim() : null;
  }

  /**
   * Password Methods
   */
  async enterPassword(password) {
    await this.page.locator(this.selectors.passwordInput).fill(password);
  }

  async submitPassword() {
    await this.page.locator(this.selectors.accessButton).click();
  }

  async verifyPassword(password) {
    await this.enterPassword(password);
    await this.submitPassword();
  }

  async hasPasswordError() {
    return await this.page.locator(this.selectors.passwordError).isVisible();
  }

  async getPasswordError() {
    return await this.page.locator(this.selectors.passwordError).textContent();
  }

  async waitForPasswordPrompt() {
    await this.page.waitForSelector(this.selectors.passwordCard, { state: 'visible', timeout: 5000 });
  }

  /**
   * Error Methods
   */
  async getErrorMessage() {
    return await this.page.locator(this.selectors.errorMessage).textContent();
  }

  async waitForError() {
    await this.page.waitForSelector(this.selectors.errorCard, { state: 'visible', timeout: 5000 });
  }

  /**
   * Upcoming Trip Methods
   */
  async getTripBeginDate() {
    const beginInfo = this.page.locator(this.selectors.tripBeginsInfo);
    const text = await beginInfo.textContent();
    // Extract date from "Trip begins: Feb 1, 2026"
    const match = text.match(/Trip begins:\s*(.+)/);
    return match ? match[1].trim() : null;
  }

  /**
   * Date Filter Methods
   */
  async hasDateFilter() {
    return await this.page.locator(this.selectors.dateRangePicker).isVisible();
  }

  async setDateRange(startDate, endDate) {
    const picker = this.page.locator(this.selectors.dateRangePicker);
    await picker.click();

    // Wait for calendar to open
    await this.page.waitForSelector('.p-datepicker', { state: 'visible', timeout: 2000 });

    // Select start date
    await this.page.locator(`.p-datepicker-calendar td:has-text("${startDate.getDate()}")`).first().click();

    // Select end date
    await this.page.locator(`.p-datepicker-calendar td:has-text("${endDate.getDate()}")`).last().click();

    // Wait for filter to apply
    await this.page.waitForTimeout(500);
  }

  async clearDateFilter() {
    const clearButton = this.page.locator(this.selectors.clearFilterButton);
    if (await clearButton.isVisible()) {
      await clearButton.click();
      await this.page.waitForTimeout(500);
    }
  }

  async isDateFilterActive() {
    return await this.page.locator(this.selectors.clearFilterButton).isVisible();
  }

  /**
   * Refresh Methods
   */
  async clickRefresh() {
    await this.page.locator(this.selectors.refreshButton).click();
    await this.page.waitForTimeout(500);
  }

  async hasRefreshButton() {
    return await this.page.locator(this.selectors.refreshButton).isVisible();
  }

  /**
   * Map Methods
   */
  async isMapDisplayed() {
    return await this.page.locator(this.selectors.leafletMap).isVisible();
  }

  async waitForMapReady() {
    await this.page.waitForSelector(this.selectors.leafletMap, { state: 'visible', timeout: 10000 });
    await this.page.waitForTimeout(1000);
  }

  async hasCurrentLocationMarker() {
    // Current location marker is a special marker with a pulsing effect
    const markerExists = await this.page.evaluate(() => {
      const markers = document.querySelectorAll('.leaflet-marker-pane .leaflet-marker-icon');
      for (const marker of markers) {
        if (marker.classList.contains('current-location-marker') ||
            marker.querySelector('.pulsing-marker')) {
          return true;
        }
      }
      return false;
    });
    return markerExists;
  }

  async hasPhotoMarkers() {
    const photoMarkerCount = await this.page.locator(this.selectors.photoMarkers).count();
    return photoMarkerCount > 0;
  }

  async getPhotoMarkerCount() {
    return await this.page.locator(this.selectors.photoMarkers).count();
  }

  async hasPathLayer() {
    // Check for polyline paths (not circle markers)
    const hasPath = await this.page.evaluate(() => {
      const paths = document.querySelectorAll('.leaflet-overlay-pane path.leaflet-interactive');
      for (const path of paths) {
        const d = path.getAttribute('d');
        if (d && d.includes('L') && !d.includes('a12,12')) {
          return true;
        }
      }
      return false;
    });
    return hasPath;
  }

  async clickTimelineMarker(index = 0) {
    const markers = this.page.locator('.leaflet-marker-icon');
    await markers.nth(index).click();
  }

  /**
   * Timeline Sidebar Methods
   */
  async getTimelineItemCount() {
    return await this.page.locator(this.selectors.timelineItems).count();
  }

  async getStayCount() {
    return await this.page.locator(this.selectors.stayCard).count();
  }

  async getTripCount() {
    return await this.page.locator(this.selectors.tripCard).count();
  }

  async clickTimelineItem(index = 0) {
    const items = this.page.locator(this.selectors.timelineItems);
    await items.nth(index).click();
  }

  async getTimelineItemTitle(index = 0) {
    const items = this.page.locator(this.selectors.timelineItems);
    const item = items.nth(index);
    return await item.locator('.timeline-item-title, h3').textContent();
  }

  /**
   * Empty State Methods
   */
  async isEmptyTimelineShown() {
    return await this.page.locator(this.selectors.emptyTimelineState).isVisible();
  }

  async isEmptyMapShown() {
    return await this.page.locator(this.selectors.emptyMapState).isVisible();
  }

  async getEmptyStateMessage() {
    const emptyTimeline = await this.isEmptyTimelineShown();
    const emptyMap = await this.isEmptyMapShown();

    if (emptyTimeline) {
      return await this.page.locator(`${this.selectors.emptyTimelineState} p`).textContent();
    }
    if (emptyMap) {
      return await this.page.locator(`${this.selectors.emptyMapState} p`).textContent();
    }
    return null;
  }

  /**
   * Wait Methods
   */
  async waitForTimelineToLoad() {
    await this.page.waitForSelector(this.selectors.timelineView, { state: 'visible', timeout: 10000 });
  }

  async waitForLoadingToFinish() {
    try {
      await this.page.waitForSelector(this.selectors.loadingState, { state: 'hidden', timeout: 10000 });
    } catch {
      // Loading might be very fast
    }
  }

  /**
   * Database Helper Methods
   */
  static async insertTimelineShareLink(dbManager, linkData) {
    const id = linkData.id || '00000000-0000-0000-0000-000000000001';
    const result = await dbManager.client.query(`
      INSERT INTO shared_link (
        id, name, expires_at, password, show_history, user_id, created_at, view_count, history_hours,
        share_type, start_date, end_date, show_current_location, show_photos, custom_map_tile_url
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
      RETURNING *
    `, [
      id,
      linkData.name || 'Test Timeline Share',
      linkData.expires_at,
      linkData.password || null,
      linkData.show_history !== undefined ? linkData.show_history : true, // Timeline shares typically show history
      linkData.user_id,
      linkData.created_at || new Date().toISOString(),
      linkData.view_count || 0,
      24,
      'TIMELINE',
      linkData.start_date,
      linkData.end_date,
      linkData.show_current_location !== undefined ? linkData.show_current_location : true,
      linkData.show_photos || false,
      linkData.custom_map_tile_url || null
    ]);
    console.log(`Inserted timeline share link ${result.rows[0]}`);
    return result.rows[0];
  }

  static async getTimelineStatus(startDate, endDate) {
    const now = new Date();
    const start = new Date(startDate);
    const end = new Date(endDate);

    if (now < start) return 'upcoming';
    if (now > end) return 'completed';
    return 'active';
  }

  static async getViewCount(dbManager, linkId) {
    const result = await dbManager.client.query(
      'SELECT view_count FROM shared_link WHERE id = $1',
      [linkId]
    );
    return result.rows[0] ? parseInt(result.rows[0].view_count) : 0;
  }
}
