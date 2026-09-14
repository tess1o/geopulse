export class JourneyInsightsPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Page elements
      pageContainer: '.gp-page-container',
      pageTitle: 'h1:has-text("Journey Insights")',
      pageSubtitle: 'p:has-text("Your location story, all in one place.")',
      
      // Loading state
      loadingSpinner: '.insights-loading .p-progress-spinner',
      loadingText: '.insights-loading p:has-text("Building your journey insights…")',
      
      // Content wrapper
      contentWrapper: '.insights-content',
      
      // Sections
      sections: {
        geographic: 'section:has(#places-title)',
        travelStory: '.journey-hero',
        activityPatterns: 'section:has(#patterns-title)',
        milestones: 'section:has(#milestones-title)'
      },
      
      // Geographic section
      geographic: {
        section: 'section:has(#places-title)',
        grid: '.places-grid',
        countriesCard: '.places-card:has-text("Countries explored")',
        citiesCard: '.places-card:has-text("Cities visited")',
        countriesCount: '.places-card:has-text("Countries explored") .places-card-heading > b',
        citiesCount: '.places-card:has-text("Cities visited") .places-card-heading > b',
        countryItems: '.places-card:has-text("Countries explored") .place-row',
        cityItems: '.places-card:has-text("Cities visited") .city-row',
        countryFlags: '.country-flag-img',
        countryNames: '.places-card:has-text("Countries explored") .place-row > span:last-child',
        cityNames: '.places-card:has-text("Cities visited") .city-row > span',
        cityVisits: '.city-row small',
        noDataMessage: '.places-card .no-data'
      },
      
      // Travel story section
      travelStory: {
        section: '.journey-hero',
        grid: '.movement-legend',
        totalDistance: '.journey-hero h2',
        movementModes: '.movement-legend > span'
      },
      
      // Activity patterns section
      activityPatterns: {
        section: 'section:has(#patterns-title)',
        grid: '.patterns-grid',
        monthCard: '.pattern-card:has-text("Most active month")',
        dayCard: '.pattern-card:has-text("Busiest day")',
        timeCard: '.pattern-card:has-text("Most active time")',
        patternValues: 'strong'
      },
      
      // Milestones section
      milestones: {
        section: 'section:has(#milestones-title)',
        grid: '.milestones-grid',
        badges: '.milestone-card',
        earnedBadges: '.milestone-card.earned',
        badgeIcons: '.badge-icon',
        badgeTitles: '.milestone-card h5',
        badgeDescriptions: '.milestone-card p',
        progressBars: '.progress-bar',
        progressFills: '.progress-bar > span',
        earnedTexts: '.milestone-status:has-text("Earned")'
      },
      
      // Empty state
      emptyState: {
        container: '.empty-card',
        icon: '.empty-icon',
        title: '.empty-card h3',
        message: '.empty-card p'
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
    await Promise.race([
      this.page.waitForSelector(this.selectors.contentWrapper, { state: 'visible' }),
      this.page.waitForSelector(this.selectors.emptyState.container, { state: 'visible' })
    ]);
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
    const distanceText = await this.page.locator(this.selectors.travelStory.totalDistance).textContent();
    return distanceText.trim();
  }

  /**
   * Get car distance value from UI
   */
  async getCarDistance() {
    return this.getMovementDistance('Car');
  }

  /**
   * Get walking distance value from UI
   */
  async getWalkDistance() {
    return this.getMovementDistance('Walk');
  }

  async getMovementDistance(label) {
    const mode = this.page.locator(this.selectors.travelStory.movementModes)
      .filter({hasText: new RegExp(`^${label}\\s`)});
    const distanceText = await mode.locator('small').textContent();
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
    const value = await monthCard.locator(this.selectors.activityPatterns.patternValues).textContent();
    return value.trim();
  }

  /**
   * Get busiest day of week value
   */
  async getBusiestDayOfWeek() {
    const dayCard = this.page.locator(this.selectors.activityPatterns.dayCard);
    const value = await dayCard.locator(this.selectors.activityPatterns.patternValues).textContent();
    return value.trim();
  }

  /**
   * Get most active time of day value
   */
  async getMostActiveTime() {
    const timeCard = this.page.locator(this.selectors.activityPatterns.timeCard);
    const value = await timeCard.locator(this.selectors.activityPatterns.patternValues).textContent();
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
    return await badge.locator(this.selectors.milestones.earnedTexts).isVisible();
  }

  /**
   * Get badge progress percentage by title
   */
  async getBadgeProgress(badgeTitle) {
    const badge = this.page.locator(this.selectors.milestones.badges).filter({hasText: badgeTitle});
    const progressBar = badge.locator(this.selectors.milestones.progressFills);
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
