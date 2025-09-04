export class JourneyInsightsPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Page elements
      pageContainer: '.page-container',
      pageTitle: 'h1:has-text("Journey Insights")',
      pageSubtitle: 'p:has-text("Discover patterns and achievements from your location data")',
      
      // Loading state
      loadingSpinner: '.insights-loading .p-progress-spinner',
      loadingText: '.insights-loading p:has-text("Loading your journey insights...")',
      
      // Content wrapper
      contentWrapper: '.insights-content-wrapper',
      
      // Sections
      sections: {
        geographic: '.insights-section:has-text("Geographic Adventures")',
        travelStory: '.insights-section:has-text("Your Travel Story")',
        activityPatterns: '.insights-section:has-text("Activity Patterns")',
        milestones: '.insights-section:has-text("Your Journey Milestones")'
      },
      
      // Geographic section
      geographic: {
        section: '.insights-section:has-text("Geographic Adventures")',
        grid: '.geographic-grid',
        countriesCard: '.geographic-card:has-text("Countries Explored")',
        citiesCard: '.geographic-card:has-text("Cities Visited")',
        countriesCount: '.geographic-card:has-text("Countries Explored") .geographic-count',
        citiesCount: '.geographic-card:has-text("Cities Visited") .geographic-count',
        countryItems: '.geographic-card:has-text("Countries Explored") .geographic-item',
        cityItems: '.geographic-card:has-text("Cities Visited") .geographic-item',
        countryFlags: '.country-flag-img',
        countryNames: '.country-name',
        cityNames: '.city-name',
        cityVisits: '.city-visits',
        noDataMessage: '.no-data'
      },
      
      // Travel story section
      travelStory: {
        section: '.insights-section:has-text("Your Travel Story")',
        grid: '.travel-records-grid',
        totalDistanceCard: '.travel-card:has-text("Total Distance Traveled")',
        carDistanceCard: '.travel-card:has-text("Distance by Car")',
        walkDistanceCard: '.travel-card:has-text("Distance Walking")',
        travelIcons: '.travel-icon',
        statNumbers: '.stat-number',
        statLabels: '.stat-label',
        statDetails: '.stat-detail'
      },
      
      // Activity patterns section
      activityPatterns: {
        section: '.insights-section:has-text("Activity Patterns")',
        grid: '.insights-grid-simple',
        monthCard: '.insight-stat-pattern:has-text("Most Active Month")',
        dayCard: '.insight-stat-pattern:has-text("Busiest Day of Week")',
        timeCard: '.insight-stat-pattern:has-text("Most Active Time of Day")',
        patternIcons: '.pattern-icon',
        patternValues: '.pattern-value',
        patternLabels: '.pattern-label',
        patternInsights: '.pattern-insight'
      },
      
      // Milestones section
      milestones: {
        section: '.insights-section:has-text("Your Journey Milestones")',
        grid: '.milestones-grid',
        badges: '.achievement-badge',
        earnedBadges: '.achievement-badge.earned',
        badgeIcons: '.badge-icon',
        badgeTitles: '.badge-title',
        badgeDescriptions: '.badge-description',
        progressBars: '.progress-bar',
        progressFills: '.progress-fill',
        progressTexts: '.progress-text',
        earnedTexts: '.earned-text',
        earnedDates: '.earned-date'
      },
      
      // Empty state
      emptyState: {
        container: '.empty-insights',
        icon: '.empty-icon',
        title: '.empty-title',
        message: '.empty-message'
      }
    }
  }

  /**
   * Check if currently on journey insights page
   */
  async isOnJourneyInsightsPage() {
    try {
      await this.page.waitForURL('**/app/journey-insights', { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Navigate to journey insights page
   */
  async navigate() {
    await this.page.goto('/app/journey-insights');
  }

  /**
   * Wait for journey insights page to load
   */
  async waitForPageLoad() {
    await this.page.waitForURL('**/app/journey-insights**');
    await this.page.waitForLoadState('networkidle');
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
    await this.page.waitForSelector(this.selectors.loadingSpinner, { state: 'hidden' });
    await this.page.waitForSelector(this.selectors.contentWrapper, { state: 'visible' });
  }

  /**
   * Check if page shows empty state
   */
  async hasEmptyState() {
    return await this.page.locator(this.selectors.emptyState.container).isVisible();
  }

  // Geographic section methods
  /**
   * Get countries count from UI
   */
  async getCountriesCount() {
    const countText = await this.page.locator(this.selectors.geographic.countriesCount).textContent();
    return parseInt(countText) || 0;
  }

  /**
   * Get cities count from UI
   */
  async getCitiesCount() {
    const countText = await this.page.locator(this.selectors.geographic.citiesCount).textContent();
    return parseInt(countText) || 0;
  }

  /**
   * Get list of country names from UI
   */
  async getCountryNames() {
    const countryElements = await this.page.locator(this.selectors.geographic.countryNames).all();
    const names = [];
    for (const element of countryElements) {
      const name = await element.textContent();
      names.push(name.trim());
    }
    return names;
  }

  /**
   * Get list of city names from UI
   */
  async getCityNames() {
    const cityElements = await this.page.locator(this.selectors.geographic.cityNames).all();
    const names = [];
    for (const element of cityElements) {
      const name = await element.textContent();
      names.push(name.trim());
    }
    return names;
  }

  /**
   * Check if country flags are displayed
   */
  async hasCountryFlags() {
    const flagCount = await this.page.locator(this.selectors.geographic.countryFlags).count();
    return flagCount > 0;
  }

  /**
   * Check if geographic section shows no data message
   */
  async hasGeographicNoData() {
    return await this.page.locator(this.selectors.geographic.noDataMessage).isVisible();
  }

  // Travel story section methods
  /**
   * Get total distance value from UI
   */
  async getTotalDistance() {
    const totalCard = this.page.locator(this.selectors.travelStory.totalDistanceCard);
    const distanceText = await totalCard.locator('.stat-number').textContent();
    return distanceText.trim();
  }

  /**
   * Get car distance value from UI
   */
  async getCarDistance() {
    const carCard = this.page.locator(this.selectors.travelStory.carDistanceCard);
    const distanceText = await carCard.locator('.stat-number').textContent();
    return distanceText.trim();
  }

  /**
   * Get walking distance value from UI
   */
  async getWalkDistance() {
    const walkCard = this.page.locator(this.selectors.travelStory.walkDistanceCard);
    const distanceText = await walkCard.locator('.stat-number').textContent();
    return distanceText.trim();
  }

  /**
   * Check if travel story section is visible
   */
  async hasTravelStoryData() {
    return await this.page.locator(this.selectors.travelStory.section).isVisible();
  }

  // Activity patterns section methods
  /**
   * Get most active month value
   */
  async getMostActiveMonth() {
    const monthCard = this.page.locator(this.selectors.activityPatterns.monthCard);
    const value = await monthCard.locator('.pattern-value').textContent();
    return value.trim();
  }

  /**
   * Get busiest day of week value
   */
  async getBusiestDayOfWeek() {
    const dayCard = this.page.locator(this.selectors.activityPatterns.dayCard);
    const value = await dayCard.locator('.pattern-value').textContent();
    return value.trim();
  }

  /**
   * Get most active time of day value
   */
  async getMostActiveTime() {
    const timeCard = this.page.locator(this.selectors.activityPatterns.timeCard);
    const value = await timeCard.locator('.pattern-value').textContent();
    return value.trim();
  }

  /**
   * Check if activity patterns section is visible
   */
  async hasActivityPatternsData() {
    return await this.page.locator(this.selectors.activityPatterns.section).isVisible();
  }

  // Milestones section methods
  /**
   * Get total badge count
   */
  async getBadgeCount() {
    return await this.page.locator(this.selectors.milestones.badges).count();
  }

  /**
   * Get earned badge count
   */
  async getEarnedBadgeCount() {
    return await this.page.locator(this.selectors.milestones.earnedBadges).count();
  }

  /**
   * Get badge titles
   */
  async getBadgeTitles() {
    const titleElements = await this.page.locator(this.selectors.milestones.badgeTitles).all();
    const titles = [];
    for (const element of titleElements) {
      const title = await element.textContent();
      titles.push(title.trim());
    }
    return titles;
  }

  /**
   * Check if specific badge is earned by title
   */
  async isBadgeEarned(badgeTitle) {
    const badge = this.page.locator(this.selectors.milestones.badges).filter({hasText: badgeTitle});
    return await badge.locator('.earned-text').isVisible();
  }

  /**
   * Get badge progress percentage by title
   */
  async getBadgeProgress(badgeTitle) {
    const badge = this.page.locator(this.selectors.milestones.badges).filter({hasText: badgeTitle});
    const progressBar = badge.locator('.progress-fill');
    if (await progressBar.isVisible()) {
      const style = await progressBar.getAttribute('style');
      const match = style.match(/width:\s*(\d+)%/);
      return match ? parseInt(match[1]) : 0;
    }
    return 0;
  }

  /**
   * Check if milestones section is visible
   */
  async hasMilestonesData() {
    return await this.page.locator(this.selectors.milestones.section).isVisible();
  }

  // Section visibility methods
  /**
   * Check if all main sections are visible
   */
  async hasAllSections() {
    const geographic = await this.page.locator(this.selectors.sections.geographic).isVisible();
    const travelStory = await this.page.locator(this.selectors.sections.travelStory).isVisible();
    const activityPatterns = await this.page.locator(this.selectors.sections.activityPatterns).isVisible();
    const milestones = await this.page.locator(this.selectors.sections.milestones).isVisible();
    
    return geographic && travelStory && activityPatterns && milestones;
  }

  /**
   * Wait for all sections to load
   */
  async waitForAllSections() {
    await this.page.waitForSelector(this.selectors.sections.geographic, { state: 'visible' });
    await this.page.waitForSelector(this.selectors.sections.travelStory, { state: 'visible' });
    await this.page.waitForSelector(this.selectors.sections.activityPatterns, { state: 'visible' });
    await this.page.waitForSelector(this.selectors.sections.milestones, { state: 'visible' });
  }

  /**
   * Database verification helpers
   */
  
  /**
   * Helper to verify journey insights exist in database
   * This would typically check timeline data exists that can generate insights
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
   * Get sample countries from database for user
   */
  static async getCountriesFromDb(dbManager, userId) {
    const query = `
      SELECT DISTINCT 
        COALESCE(rgl.country, fl.country) as country
      FROM timeline_stays ts
      LEFT JOIN reverse_geocoding_location rgl ON ts.geocoding_id = rgl.id
      LEFT JOIN favorite_locations fl ON ts.favorite_id = fl.id
      WHERE ts.user_id = $1 
        AND (rgl.country IS NOT NULL OR fl.country IS NOT NULL)
      ORDER BY country
    `;
    const result = await dbManager.client.query(query, [userId]);
    return result.rows.map(row => row.country);
  }

  /**
   * Get sample cities from database for user
   */
  static async getCitiesFromDb(dbManager, userId) {
    const query = `
      SELECT 
        COALESCE(rgl.city, fl.city) as city,
        COUNT(*) as visits
      FROM timeline_stays ts
      LEFT JOIN reverse_geocoding_location rgl ON ts.geocoding_id = rgl.id
      LEFT JOIN favorite_locations fl ON ts.favorite_id = fl.id
      WHERE ts.user_id = $1 
        AND (rgl.city IS NOT NULL OR fl.city IS NOT NULL)
      GROUP BY COALESCE(rgl.city, fl.city)
      ORDER BY visits DESC, city
    `;
    const result = await dbManager.client.query(query, [userId]);
    return result.rows.map(row => ({
      name: row.city,
      visits: parseInt(row.visits)
    }));
  }

  /**
   * Calculate expected total distance from database (in km)
   */
  static async getTotalDistanceFromDb(dbManager, userId) {
    const query = `
      SELECT SUM(distance_meters) as total_distance_meters
      FROM timeline_trips 
      WHERE user_id = $1
    `;
    const result = await dbManager.client.query(query, [userId]);
    const totalMeters = parseInt(result.rows[0].total_distance_meters) || 0;
    return Math.round(totalMeters / 1000); // Convert to km
  }

  /**
   * Get distance by transportation type from database (in km)
   */
  static async getDistanceByTransportationFromDb(dbManager, userId, transportationType) {
    const query = `
      SELECT SUM(distance_meters) as distance_meters
      FROM timeline_trips 
      WHERE user_id = $1 AND movement_type = $2
    `;
    const result = await dbManager.client.query(query, [userId, transportationType]);
    const totalMeters = parseInt(result.rows[0].distance_meters) || 0;
    return Math.round(totalMeters / 1000); // Convert to km
  }

  /**
   * Wait for insights data to be processed (with retry logic)
   */
  static async waitForInsightsData(dbManager, userId, maxAttempts = 10, delayMs = 500) {
    let attempts = 0;
    let hasData = false;
    
    do {
      if (attempts > 0) {
        await new Promise(resolve => setTimeout(resolve, delayMs));
      }
      hasData = await this.verifyTimelineDataExists(dbManager, userId);
      attempts++;
      console.log(`Insights data check attempt ${attempts}: hasData=${hasData}`);
    } while (!hasData && attempts < maxAttempts);
    
    return hasData;
  }
}