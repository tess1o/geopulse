export class DashboardPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Page elements
      pageContainer: '.page-container',
      pageTitle: 'h1:has-text("Dashboard")',
      pageSubtitle: 'text="Overview of your location data and analytics"',
      
      // Loading state
      loadingSpinner: '.p-progress-spinner',
      loadingPlaceholders: '.gp-loading-placeholder',
      
      // Main dashboard sections
      dashboardSections: '.dashboard-section',
      dashboardGrid: '.dashboard-grid',
      
      // Activity Summary Cards (3 periods) - using actual class structure
      activitySummaryCards: {
        selectedPeriod: '.gp-card:has(.gp-card-title:has-text("Selected Period Summary"))',
        sevenDays: '.gp-card:has(.gp-card-title:has-text("7 Days Overview"))',
        thirtyDays: '.gp-card:has(.gp-card-title:has-text("30 Days Overview"))'
      },
      
      // Base cards sections - using actual gp-card class
      topPlacesCards: {
        selectedPeriod: '.gp-card:has(.gp-card-title:has-text("Top Places")):nth-of-type(1)',
        sevenDays: '.gp-card:has(.gp-card-title:has-text("Top Places")):nth-of-type(2)',
        thirtyDays: '.gp-card:has(.gp-card-title:has-text("Top Places")):nth-of-type(3)'
      },
      
      routeAnalysisCards: {
        selectedPeriod: '.gp-card:has(.gp-card-title:has-text("Route Analysis")):nth-of-type(1)',
        sevenDays: '.gp-card:has(.gp-card-title:has-text("Route Analysis")):nth-of-type(2)',
        thirtyDays: '.gp-card:has(.gp-card-title:has-text("Route Analysis")):nth-of-type(3)'
      },
      
      // Metric items within activity cards - using actual gp-metric classes
      metricItems: {
        totalDistance: '.gp-metric-item:has(.gp-metric-label:has-text("Total Distance"))',
        timeMoving: '.gp-metric-item:has(.gp-metric-label:has-text("Time Moving"))',
        dailyAverage: '.gp-metric-item:has(.gp-metric-label:has-text("Daily Average"))',
        averageSpeed: '.gp-metric-item:has(.gp-metric-label:has-text("Average Speed"))',
        mostActiveDay: '.gp-metric-item:has(.gp-metric-label:has-text("Most Active Day"))',
        uniqueLocations: '.gp-metric-item:has(.gp-metric-label:has-text("Unique Locations"))'
      },
      
      // Metric values - using actual gp-metric classes
      metricValues: '.gp-metric-value',
      metricLabels: '.gp-metric-label',
      metricIcons: '.gp-metric-icon',
      
      // Top Places content
      topPlacesContent: '.top-places-content',
      placeItems: '.place-item',
      placeNames: '.place-name',
      placeVisits: '.place-visits',
      
      // Route Analysis content
      routeAnalysisContent: '.route-analysis-content',
      routeMetrics: '.route-metric',
      routeStats: '.route-stat',
      
      // Charts (if visible)
      charts: '.chart-container',
      barCharts: '.bar-chart',
      chartData: '.chart-data',
      
      // Empty state
      emptyState: {
        container: '.empty-dashboard',
        icon: '.empty-icon',
        title: '.empty-title',
        message: '.empty-message'
      }
    }
  }

  /**
   * Check if currently on dashboard page
   */
  async isOnDashboardPage() {
    try {
      const url = this.page.url();
      const hasDashboardContent = await this.page.locator('h1:has-text("Dashboard")').isVisible().catch(() => false);
      return url.includes('/app/dashboard') || hasDashboardContent;
    } catch {
      return false;
    }
  }

  /**
   * Navigate to dashboard page
   */
  async navigate() {
    await this.page.goto('/app/dashboard');
  }

  /**
   * Wait for dashboard page to load
   */
  async waitForPageLoad() {
    // Just wait for network to be idle instead of specific URL pattern
    await this.page.waitForLoadState('networkidle');
    
    // Give a small buffer for any async operations
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
    // Wait for loading spinners to disappear
    try {
      await this.page.waitForSelector(this.selectors.loadingSpinner, { state: 'hidden', timeout: 10000 });
    } catch {
      // Loading spinner might not be visible or might disappear too quickly
      console.log('Loading spinner not found or disappeared quickly');
    }
    
    // Wait for either dashboard sections or empty state to appear
    try {
      await Promise.race([
        this.page.waitForSelector(this.selectors.dashboardSections, { state: 'visible', timeout: 5000 }),
        this.page.waitForSelector(this.selectors.emptyState.container, { state: 'visible', timeout: 5000 })
      ]);
    } catch {
      // If neither appears, just wait for network to be idle
      await this.page.waitForLoadState('networkidle');
    }
  }

  /**
   * Check if page shows empty state
   */
  async hasEmptyState() {
    return await this.page.locator(this.selectors.emptyState.container).isVisible();
  }

  /**
   * Check if any dashboard sections are visible
   */
  async hasDashboardSections() {
    return await this.page.locator(this.selectors.dashboardSections).count() > 0;
  }

  // Activity Summary Cards Methods
  /**
   * Check if activity summary cards are visible
   */
  async hasActivitySummaryCards() {
    const selectedPeriod = await this.page.locator(this.selectors.activitySummaryCards.selectedPeriod).isVisible();
    const sevenDays = await this.page.locator(this.selectors.activitySummaryCards.sevenDays).isVisible();
    const thirtyDays = await this.page.locator(this.selectors.activitySummaryCards.thirtyDays).isVisible();
    
    return selectedPeriod && sevenDays && thirtyDays;
  }

  /**
   * Get metric value from a specific card
   */
  async getMetricValue(cardSelector, metricType) {
    const card = this.page.locator(cardSelector);
    const metricItem = card.locator(this.selectors.metricItems[metricType]);
    const value = await metricItem.locator('.gp-metric-value').textContent();
    return value.trim();
  }

  /**
   * Get total distance from selected period card
   */
  async getSelectedPeriodTotalDistance() {
    return await this.getMetricValue(
      this.selectors.activitySummaryCards.selectedPeriod, 
      'totalDistance'
    );
  }

  /**
   * Get time moving from seven days card
   */
  async getSevenDaysTimeMoving() {
    return await this.getMetricValue(
      this.selectors.activitySummaryCards.sevenDays, 
      'timeMoving'
    );
  }

  /**
   * Get daily average from thirty days card
   */
  async getThirtyDaysDailyAverage() {
    return await this.getMetricValue(
      this.selectors.activitySummaryCards.thirtyDays, 
      'dailyAverage'
    );
  }

  /**
   * Get average speed from any card
   */
  async getAverageSpeed(period = 'selectedPeriod') {
    return await this.getMetricValue(
      this.selectors.activitySummaryCards[period], 
      'averageSpeed'
    );
  }

  /**
   * Get most active day from any card
   */
  async getMostActiveDay(period = 'selectedPeriod') {
    return await this.getMetricValue(
      this.selectors.activitySummaryCards[period], 
      'mostActiveDay'
    );
  }

  // Top Places Methods
  /**
   * Check if top places cards are visible
   */
  async hasTopPlacesCards() {
    const selectedPeriod = await this.page.locator(this.selectors.topPlacesCards.selectedPeriod).isVisible();
    const sevenDays = await this.page.locator(this.selectors.topPlacesCards.sevenDays).isVisible();
    const thirtyDays = await this.page.locator(this.selectors.topPlacesCards.thirtyDays).isVisible();
    
    return selectedPeriod && sevenDays && thirtyDays;
  }

  /**
   * Get place names from a specific period
   */
  async getPlaceNames(period = 'selectedPeriod') {
    const card = this.page.locator(this.selectors.topPlacesCards[period]);
    const placeElements = await card.locator(this.selectors.placeNames).all();
    const names = [];
    for (const element of placeElements) {
      const name = await element.textContent();
      names.push(name.trim());
    }
    return names;
  }

  /**
   * Get place visits count from a specific period
   */
  async getPlaceVisits(period = 'selectedPeriod') {
    const card = this.page.locator(this.selectors.topPlacesCards[period]);
    const visitElements = await card.locator(this.selectors.placeVisits).all();
    const visits = [];
    for (const element of visitElements) {
      const visit = await element.textContent();
      visits.push(visit.trim());
    }
    return visits;
  }

  /**
   * Get number of places displayed
   */
  async getPlacesCount(period = 'selectedPeriod') {
    const card = this.page.locator(this.selectors.topPlacesCards[period]);
    return await card.locator(this.selectors.placeItems).count();
  }

  // Route Analysis Methods
  /**
   * Check if route analysis cards are visible
   */
  async hasRouteAnalysisCards() {
    const selectedPeriod = await this.page.locator(this.selectors.routeAnalysisCards.selectedPeriod).isVisible();
    const sevenDays = await this.page.locator(this.selectors.routeAnalysisCards.sevenDays).isVisible();
    const thirtyDays = await this.page.locator(this.selectors.routeAnalysisCards.thirtyDays).isVisible();
    
    return selectedPeriod && sevenDays && thirtyDays;
  }

  /**
   * Get route statistics from a specific period
   */
  async getRouteStats(period = 'selectedPeriod') {
    const card = this.page.locator(this.selectors.routeAnalysisCards[period]);
    const statElements = await card.locator(this.selectors.routeStats).all();
    const stats = [];
    for (const element of statElements) {
      const stat = await element.textContent();
      stats.push(stat.trim());
    }
    return stats;
  }

  // Chart Methods
  /**
   * Check if charts are visible in activity cards
   */
  async hasCharts() {
    return await this.page.locator(this.selectors.charts).count() > 0;
  }

  /**
   * Check if bar charts are displayed
   */
  async hasBarCharts() {
    return await this.page.locator(this.selectors.barCharts).count() > 0;
  }

  // Date Range Methods
  /**
   * Get the displayed date range from card period
   */
  async getCardPeriod(cardSelector) {
    const card = this.page.locator(cardSelector);
    const periodElement = card.locator('.gp-card-period, .base-card-period');
    if (await periodElement.isVisible()) {
      return (await periodElement.textContent()).trim();
    }
    return '';
  }

  /**
   * Get selected period date range
   */
  async getSelectedPeriodRange() {
    return await this.getCardPeriod(this.selectors.activitySummaryCards.selectedPeriod);
  }

  /**
   * Get seven days date range
   */
  async getSevenDaysRange() {
    return await this.getCardPeriod(this.selectors.activitySummaryCards.sevenDays);
  }

  /**
   * Get thirty days date range
   */
  async getThirtyDaysRange() {
    return await this.getCardPeriod(this.selectors.activitySummaryCards.thirtyDays);
  }

  // Validation Methods
  /**
   * Check if all main dashboard sections are present
   */
  async hasAllMainSections() {
    const activityCards = await this.hasActivitySummaryCards();
    const topPlacesCards = await this.hasTopPlacesCards();
    const routeAnalysisCards = await this.hasRouteAnalysisCards();
    
    return activityCards && topPlacesCards && routeAnalysisCards;
  }

  /**
   * Wait for all main sections to load
   */
  async waitForAllSections() {
    await this.page.waitForSelector(this.selectors.activitySummaryCards.selectedPeriod, { state: 'visible' });
    await this.page.waitForSelector(this.selectors.topPlacesCards.selectedPeriod, { state: 'visible' });
    await this.page.waitForSelector(this.selectors.routeAnalysisCards.selectedPeriod, { state: 'visible' });
  }

  /**
   * Database verification helpers
   */
  
  /**
   * Helper to verify timeline data exists for dashboard statistics
   */
  static async verifyTimelineDataExists(dbManager, userId) {
    const query = `
      SELECT COUNT(*) as count FROM timeline_stays WHERE user_id = $1
      UNION ALL
      SELECT COUNT(*) as count FROM timeline_trips WHERE user_id = $1
    `;
    const result = await dbManager.client.query(query, [userId]);
    const stayCount = parseInt(result.rows[0].count);
    const tripCount = parseInt(result.rows[1].count);
    return stayCount > 0 || tripCount > 0;
  }

  /**
   * Get total distance from database for verification
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
   * Get time moving from database for verification
   */
  static async getTimeMovingFromDb(dbManager, userId, startDate, endDate) {
    const query = `
      SELECT SUM(trip_duration) as total_time_moving
      FROM timeline_trips 
      WHERE user_id = $1 
      AND timestamp >= $2 
      AND timestamp <= $3
    `;
    const result = await dbManager.client.query(query, [userId, startDate, endDate]);
    return parseInt(result.rows[0].total_time_moving) || 0;
  }

  /**
   * Get unique locations count from database
   */
  static async getUniqueLocationsFromDb(dbManager, userId, startDate, endDate) {
    const query = `
      SELECT COUNT(DISTINCT location_name) as unique_locations
      FROM timeline_stays 
      WHERE user_id = $1 
      AND timestamp >= $2 
      AND timestamp <= $3
    `;
    const result = await dbManager.client.query(query, [userId, startDate, endDate]);
    return parseInt(result.rows[0].unique_locations) || 0;
  }

  /**
   * Get top places from database for verification
   */
  static async getTopPlacesFromDb(dbManager, userId, startDate, endDate, limit = 5) {
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
   * Calculate average speed from database
   */
  static async getAverageSpeedFromDb(dbManager, userId, startDate, endDate) {
    const query = `
      SELECT 
        SUM(distance_meters) as total_distance,
        SUM(trip_duration) as total_time
      FROM timeline_trips 
      WHERE user_id = $1 
      AND timestamp >= $2 
      AND timestamp <= $3
      AND trip_duration > 0
    `;
    const result = await dbManager.client.query(query, [userId, startDate, endDate]);
    const totalDistance = parseFloat(result.rows[0].total_distance) || 0;
    const totalTime = parseFloat(result.rows[0].total_time) || 0;
    
    if (totalTime === 0) return 0;
    
    // Convert to km/h: (meters / seconds) * 3.6
    return (totalDistance / totalTime) * 3.6;
  }

  /**
   * Wait for statistics data to be processed (with retry logic)
   */
  static async waitForStatisticsData(dbManager, userId, maxAttempts = 10, delayMs = 500) {
    let attempts = 0;
    let hasData = false;
    
    do {
      if (attempts > 0) {
        await new Promise(resolve => setTimeout(resolve, delayMs));
      }
      hasData = await this.verifyTimelineDataExists(dbManager, userId);
      attempts++;
      console.log(`Statistics data check attempt ${attempts}: hasData=${hasData}`);
    } while (!hasData && attempts < maxAttempts);
    
    return hasData;
  }
}