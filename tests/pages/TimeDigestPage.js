export class TimeDigestPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Page elements
      pageContainer: '.page-container',
      pageTitle: 'h1:has-text("Rewind")',
      pageSubtitle: 'text="Explore your location story through time"',

      // Loading state
      loadingSpinner: '.p-progress-spinner',
      loadingText: 'text="Loading your digest..."',

      // Error state
      errorCard: '.error-card',
      errorIcon: '.error-icon',
      errorTitle: '.error-title',
      errorMessage: '.error-message',
      tryAgainButton: 'button:has-text("Try Again")',

      // Empty state
      emptyCard: '.empty-card',
      emptyIcon: '.empty-icon',
      emptyTitle: '.empty-title',
      emptyMessage: '.empty-message',

      // Header controls
      digestHeader: '.digest-header',
      viewModeToggle: '.view-mode-toggle',
      yearSelector: '.year-selector',
      monthSelector: '.month-selector',
      prevButton: 'button:has-text("Previous")',
      nextButton: 'button:has-text("Next")',

      // Digest content
      digestContent: '.digest-content',

      // Metrics section
      digestMetrics: '.digest-metrics',
      metricsTitle: '.metrics-title',
      metricCards: '.metric-card',
      metricValue: '.metric-value',
      metricLabel: '.metric-label',
      metricChange: '.metric-change',

      // Highlights section
      digestHighlights: '.digest-highlights',
      highlightCards: '.highlight-card',
      highlightIcon: '.highlight-icon',
      highlightTitle: '.highlight-title',
      highlightValue: '.highlight-value',
      highlightDate: '.highlight-date',

      // Places section
      digestPlaces: '.digest-places',
      placesList: '.places-list',
      placeItem: '.place-item',
      placeRank: '.place-rank',
      placeInfo: '.place-info',
      placeName: '.place-name',
      placeStats: '.place-stats',

      // Milestones section
      digestMilestones: '.digest-milestones',
      milestoneCard: '.milestone-card',
      milestoneIcon: '.milestone-icon',
      milestoneTitle: '.milestone-title',
      milestoneDescription: '.milestone-description',
      tierBadge: '.tier-badge',

      // Trends/Chart section
      digestTrends: '.digest-trends',
      chartContainer: '.chart-container',
      chartCanvas: 'canvas',
      chartLegend: '.chart-legend'
    }
  }

  /**
   * Check if currently on time digest page
   */
  async isOnTimeDigestPage() {
    try {
      const url = this.page.url();
      const hasDigestContent = await this.page.locator('h1:has-text("Rewind")').isVisible().catch(() => false);
      return url.includes('/app/rewind') || hasDigestContent;
    } catch {
      return false;
    }
  }

  /**
   * Navigate to time digest page
   */
  async navigate(year = null, month = null, viewMode = null) {
    let url = '/app/rewind';
    const params = new URLSearchParams();

    if (viewMode) params.append('viewMode', viewMode);
    if (year) params.append('year', year.toString());
    if (month) params.append('month', month.toString());

    if (params.toString()) {
      url += '?' + params.toString();
    }

    await this.page.goto(url);
  }

  /**
   * Wait for page to load
   */
  async waitForPageLoad() {
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(1000);
  }

  /**
   * Check if page is in loading state
   */
  async isLoading() {
    return await this.page.locator(this.selectors.loadingSpinner).isVisible();
  }

  /**
   * Wait for loading to complete
   */
  async waitForLoadingComplete() {
    try {
      await this.page.waitForSelector(this.selectors.loadingSpinner, { state: 'hidden', timeout: 10000 });
    } catch {
      console.log('Loading spinner not found or disappeared quickly');
    }

    // Wait for either digest content, empty state, or error state
    try {
      await Promise.race([
        this.page.waitForSelector(this.selectors.digestContent, { state: 'visible', timeout: 5000 }),
        this.page.waitForSelector(this.selectors.emptyCard, { state: 'visible', timeout: 5000 }),
        this.page.waitForSelector(this.selectors.errorCard, { state: 'visible', timeout: 5000 })
      ]);
    } catch {
      await this.page.waitForLoadState('networkidle');
    }
  }

  /**
   * Check if page shows error state
   */
  async hasError() {
    return await this.page.locator(this.selectors.errorCard).isVisible();
  }

  /**
   * Get error message
   */
  async getErrorMessage() {
    const errorElement = this.page.locator(this.selectors.errorMessage);
    if (await errorElement.isVisible()) {
      return (await errorElement.textContent()).trim();
    }
    return null;
  }

  /**
   * Check if page shows empty state
   */
  async hasEmptyState() {
    return await this.page.locator(this.selectors.emptyCard).isVisible();
  }

  /**
   * Check if digest content is visible
   */
  async hasDigestContent() {
    return await this.page.locator(this.selectors.digestContent).isVisible();
  }

  // Metrics Methods
  /**
   * Check if metrics section is visible
   */
  async hasMetrics() {
    return await this.page.locator(this.selectors.digestMetrics).isVisible();
  }

  /**
   * Get metrics title
   */
  async getMetricsTitle() {
    const titleElement = this.page.locator(this.selectors.metricsTitle);
    if (await titleElement.isVisible()) {
      return (await titleElement.textContent()).trim();
    }
    return null;
  }

  /**
   * Get all metric values
   */
  async getMetricValues() {
    const metricElements = await this.page.locator(this.selectors.metricCards).all();
    const metrics = [];

    for (const element of metricElements) {
      const label = await element.locator(this.selectors.metricLabel).textContent();
      const value = await element.locator(this.selectors.metricValue).textContent();
      metrics.push({
        label: label.trim(),
        value: value.trim()
      });
    }

    return metrics;
  }

  /**
   * Get specific metric value by label
   */
  async getMetricByLabel(label) {
    const metrics = await this.getMetricValues();
    return metrics.find(m => m.label.toLowerCase().includes(label.toLowerCase()));
  }

  /**
   * Get comparison text
   */
  async getComparisonText() {
    const comparisonElement = this.page.locator(this.selectors.metricChange).first();
    if (await comparisonElement.isVisible()) {
      return (await comparisonElement.textContent()).trim();
    }
    return null;
  }

  // Highlights Methods
  /**
   * Check if highlights section is visible
   */
  async hasHighlights() {
    return await this.page.locator(this.selectors.digestHighlights).isVisible();
  }

  /**
   * Get all highlight texts
   */
  async getHighlights() {
    const highlightElements = await this.page.locator(this.selectors.highlightCards).all();
    const highlights = [];

    for (const element of highlightElements) {
      const title = await element.locator(this.selectors.highlightTitle).textContent();
      const value = await element.locator(this.selectors.highlightValue).textContent();
      highlights.push({
        title: title.trim(),
        value: value.trim()
      });
    }

    return highlights;
  }

  /**
   * Get number of highlights
   */
  async getHighlightsCount() {
    return await this.page.locator(this.selectors.highlightCards).count();
  }

  // Places Methods
  /**
   * Check if places section is visible
   */
  async hasPlaces() {
    return await this.page.locator(this.selectors.digestPlaces).isVisible();
  }

  /**
   * Get all place names
   */
  async getPlaceNames() {
    const placeElements = await this.page.locator(this.selectors.placeItem).all();
    const places = [];

    for (const element of placeElements) {
      const name = await element.locator(this.selectors.placeName).textContent();
      places.push(name.trim());
    }

    return places;
  }

  /**
   * Get place details (name, visits/stats)
   */
  async getPlaceDetails() {
    const placeElements = await this.page.locator(this.selectors.placeItem).all();
    const places = [];

    for (const element of placeElements) {
      const name = await element.locator(this.selectors.placeName).textContent();
      const stats = await element.locator(this.selectors.placeStats).textContent();

      places.push({
        name: name.trim(),
        stats: stats.trim()
      });
    }

    return places;
  }

  /**
   * Get number of places displayed
   */
  async getPlacesCount() {
    return await this.page.locator(this.selectors.placeItem).count();
  }

  // Milestones Methods
  /**
   * Check if milestones section is visible
   */
  async hasMilestones() {
    return await this.page.locator(this.selectors.digestMilestones).isVisible();
  }

  /**
   * Get all milestones
   */
  async getMilestones() {
    const milestoneElements = await this.page.locator(this.selectors.milestoneCard).all();
    const milestones = [];

    for (const element of milestoneElements) {
      const title = await element.locator(this.selectors.milestoneTitle).textContent();
      const description = await element.locator(this.selectors.milestoneDescription).textContent();
      milestones.push({
        title: title.trim(),
        description: description.trim()
      });
    }

    return milestones;
  }

  /**
   * Get number of milestones
   */
  async getMilestonesCount() {
    return await this.page.locator(this.selectors.milestoneCard).count();
  }

  // Trends/Chart Methods
  /**
   * Check if trends section is visible
   */
  async hasTrends() {
    return await this.page.locator(this.selectors.digestTrends).isVisible();
  }

  /**
   * Check if chart is visible
   */
  async hasChart() {
    return await this.page.locator(this.selectors.chartContainer).isVisible();
  }

  // Database Verification Helpers
  /**
   * Verify digest data exists for a given period
   */
  static async verifyDigestDataExists(dbManager, userId, year, month = null) {
    let startDate, endDate;

    if (month) {
      // Monthly digest
      startDate = new Date(year, month - 1, 1);
      endDate = new Date(year, month, 0, 23, 59, 59);
    } else {
      // Yearly digest
      startDate = new Date(year, 0, 1);
      endDate = new Date(year, 11, 31, 23, 59, 59);
    }

    const query = `
      SELECT COUNT(*) as count FROM timeline_stays WHERE user_id = $1 AND timestamp >= $2 AND timestamp <= $3
      UNION ALL
      SELECT COUNT(*) as count FROM timeline_trips WHERE user_id = $1 AND timestamp >= $2 AND timestamp <= $3
    `;
    const result = await dbManager.client.query(query, [userId, startDate, endDate]);
    const stayCount = parseInt(result.rows[0].count);
    const tripCount = parseInt(result.rows[1].count);
    return stayCount > 0 || tripCount > 0;
  }

  /**
   * Get total distance from database for a period
   */
  static async getTotalDistanceFromDb(dbManager, userId, startDate, endDate) {
    const query = `
      SELECT SUM(distance_meters) as total_distance_meters
      FROM timeline_trips
      WHERE user_id = $1
      AND timestamp >= $2
      AND timestamp <= $3
    `;
    const result = await dbManager.client.query(query, [userId, startDate, endDate]);
    return parseInt(result.rows[0].total_distance_meters) || 0;
  }

  /**
   * Get active days count from database
   */
  static async getActiveDaysFromDb(dbManager, userId, startDate, endDate) {
    const query = `
      SELECT COUNT(DISTINCT DATE(timestamp)) as active_days
      FROM (
        SELECT timestamp FROM timeline_stays WHERE user_id = $1 AND timestamp >= $2 AND timestamp <= $3
        UNION ALL
        SELECT timestamp FROM timeline_trips WHERE user_id = $1 AND timestamp >= $2 AND timestamp <= $3
      ) combined
    `;
    const result = await dbManager.client.query(query, [userId, startDate, endDate]);
    return parseInt(result.rows[0].active_days) || 0;
  }

  /**
   * Get trip count from database
   */
  static async getTripCountFromDb(dbManager, userId, startDate, endDate) {
    const query = `
      SELECT COUNT(*) as trip_count
      FROM timeline_trips
      WHERE user_id = $1
      AND timestamp >= $2
      AND timestamp <= $3
    `;
    const result = await dbManager.client.query(query, [userId, startDate, endDate]);
    return parseInt(result.rows[0].trip_count) || 0;
  }

  /**
   * Get top places from database
   */
  static async getTopPlacesFromDb(dbManager, userId, startDate, endDate, limit = 10) {
    const query = `
      SELECT location_name, COUNT(*) as visits
      FROM timeline_stays
      WHERE user_id = $1
      AND timestamp >= $2
      AND timestamp <= $3
      AND location_name IS NOT NULL
      GROUP BY location_name
      ORDER BY visits DESC, location_name
      LIMIT $4
    `;
    const result = await dbManager.client.query(query, [userId, startDate, endDate, limit]);
    return result.rows.map(row => ({
      name: row.location_name,
      visits: parseInt(row.visits)
    }));
  }

  /**
   * Get longest trip from database
   */
  static async getLongestTripFromDb(dbManager, userId, startDate, endDate) {
    const query = `
      SELECT distance_meters, timestamp
      FROM timeline_trips
      WHERE user_id = $1
      AND timestamp >= $2
      AND timestamp <= $3
      ORDER BY distance_meters DESC
      LIMIT 1
    `;
    const result = await dbManager.client.query(query, [userId, startDate, endDate]);
    if (result.rows.length > 0) {
      return {
        distance: parseInt(result.rows[0].distance_meters),
        date: result.rows[0].timestamp
      };
    }
    return null;
  }
}
