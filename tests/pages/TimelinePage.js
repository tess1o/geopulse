import {LoginPage} from './LoginPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';

export class TimelinePage {
  constructor(page) {
    this.page = page;
  }

  /**
   * Check if currently on timeline page
   */
  async isOnTimelinePage() {
    const url = this.page.url();
    return /\/app\/timeline(\/|\?|#|$)/.test(url);
  }

  /**
   * Navigate to timeline page
   */
  async navigate() {
    await this.page.goto('/app/timeline');
  }

  /**
   * Navigate to timeline page with specific date range
   */
  async navigateWithDateRange(startDate, endDate) {
    const startDateStr = `${String(startDate.getMonth() + 1).padStart(2, '0')}/${String(startDate.getDate()).padStart(2, '0')}/${startDate.getFullYear()}`;
    const endDateStr = `${String(endDate.getMonth() + 1).padStart(2, '0')}/${String(endDate.getDate()).padStart(2, '0')}/${endDate.getFullYear()}`;
    
    await this.page.goto(`/app/timeline?start=${startDateStr}&end=${endDateStr}`);
  }

  /**
   * Wait for timeline page to load
   */
  async waitForPageLoad() {
    // Just wait for network to be idle instead of specific URL pattern
    await this.page.waitForLoadState('networkidle');

    // Give a small buffer for any async operations
    await this.page.waitForTimeout(1000);
  }

  /**
   * Common setup: login and navigate to timeline page
   */
  async loginAndNavigate(testUser = TestData.users.existing) {
    const loginPage = new LoginPage(this.page);
    
    await UserFactory.createUser(this.page, testUser);
    await loginPage.navigate();
    await loginPage.login(testUser.email, testUser.password);
    await TestHelpers.waitForNavigation(this.page, '**/app/timeline');
    
    return { loginPage, testUser };
  }

  /**
   * Setup timeline test with data and navigation to date range
   */
  async setupTimelineWithData(dbManager, dataInsertFunction, testUser = TestData.users.existing, dateRange = null) {
    // Login and navigate
    await this.loginAndNavigate(testUser);
    
    // Insert test data
    const user = await dbManager.getUserByEmail(testUser.email);
    const testData = await dataInsertFunction(dbManager, user.id);
    
    // Navigate to specific date range if provided
    if (dateRange) {
      const { startDate, endDate } = dateRange;
      await this.navigateWithDateRange(startDate, endDate);
    } else {
      await this.page.reload();
    }
    
    await this.waitForPageLoad();
    
    return { user, testData };
  }

  /**
   * Setup overnight timeline test with yesterday-today date range
   */
  async setupOvernightTimelineWithData(dbManager, dataInsertFunction, testUser = TestData.users.existing) {
    return await this.setupTimelineWithData(dbManager, dataInsertFunction, testUser, {
      startDate: new Date('2025-09-20'),
      endDate: new Date('2025-09-22')
    });
  }

  /**
   * Wait for timeline content to load
   */
  async waitForTimelineContent() {
    await this.page.waitForSelector('.timeline-content', { timeout: 10000 });
  }

  /**
   * Wait for loading to complete and no data message to appear
   */
  async waitForNoDataMessage() {
    await this.page.waitForSelector('.p-progressspinner', { state: 'detached', timeout: 15000 });
    await this.page.waitForSelector('.loading-messages:has-text("No timeline for the given date range")', { timeout: 10000 });
  }

  /**
   * Get timeline cards by type
   */
  getTimelineCards(type) {
    const selectors = {
      stays: '.timeline-card--stay',
      trips: '.trip-content',
      gaps: '.p-card',
      overnightStays: '.timeline-card--overnight-stay',
      overnightTrips: '.timeline-card--overnight-trip',
      overnightGaps: '.timeline-card--overnight-data-gap'
    };

    if (type === 'trips') {
      return this.page.locator('.trip-content').locator('..');
    } else if (type === 'gaps') {
      return this.page.locator('.p-card').filter({ has: this.page.locator('[class*="gap"], [class*="data-gap"]') });
    } else {
      return this.page.locator(selectors[type] || selectors.stays);
    }
  }

  /**
   * Get moon icons count
   */
  async getMoonIconsCount() {
    const moonIcons = this.page.locator('.timeline-marker .pi-moon');
    return await moonIcons.count();
  }

  /**
   * Get date groups count
   */
  async getDateGroupsCount() {
    const dateGroups = this.page.locator('.date-group');
    return await dateGroups.count();
  }

  /**
   * Verify database timeline data exists
   */
  static async verifyTimelineDataExists(dbManager, userId) {
    const staysResult = await dbManager.client.query(`
      SELECT COUNT(*) as count FROM timeline_stays WHERE user_id = $1
    `, [userId]);
    
    const tripsResult = await dbManager.client.query(`
      SELECT COUNT(*) as count FROM timeline_trips WHERE user_id = $1
    `, [userId]);
    
    const staysCount = parseInt(staysResult.rows[0].count);
    const tripsCount = parseInt(tripsResult.rows[0].count);
    
    return staysCount > 0 || tripsCount > 0;
  }
}