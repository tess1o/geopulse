import {LoginPage} from './LoginPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';

export class TimelineReportsPage {
  constructor(page) {
    this.page = page;
  }

  /**
   * Check if currently on timeline reports page
   */
  async isOnTimelineReportsPage() {
    const url = this.page.url();
    return /\/app\/timeline-reports(\/|\?|#|$)/.test(url);
  }

  /**
   * Navigate to timeline reports page
   */
  async navigate() {
    await this.page.goto('/app/timeline-reports');
  }

  /**
   * Navigate to timeline reports page with specific date range
   */
  async navigateWithDateRange(startDate, endDate) {
    const startDateStr = `${String(startDate.getMonth() + 1).padStart(2, '0')}/${String(startDate.getDate()).padStart(2, '0')}/${startDate.getFullYear()}`;
    const endDateStr = `${String(endDate.getMonth() + 1).padStart(2, '0')}/${String(endDate.getDate()).padStart(2, '0')}/${endDate.getFullYear()}`;

    await this.page.goto(`/app/timeline-reports?start=${startDateStr}&end=${endDateStr}`);
  }

  /**
   * Wait for timeline reports page to load
   */
  async waitForPageLoad() {
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(1000);
  }

  /**
   * Common setup: login and navigate to timeline reports page
   */
  async loginAndNavigate(testUser = TestData.users.existing) {
    const loginPage = new LoginPage(this.page);

    await UserFactory.createUser(this.page, testUser);
    await loginPage.navigate();
    await loginPage.login(testUser.email, testUser.password);

    // Wait for automatic redirect after login (goes to /app/timeline by default)
    await TestHelpers.waitForNavigation(this.page, '**/app/timeline');

    // Now navigate to timeline reports
    await this.navigate();
    await this.waitForPageLoad();

    return { loginPage, testUser };
  }

  /**
   * Setup timeline reports test with data and navigation to date range
   */
  async setupWithData(dbManager, dataInsertFunction, testUser = TestData.users.existing, dateRange = null) {
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
   * Wait for content to load (no loading spinner)
   */
  async waitForContentLoaded() {
    // Wait for loading spinner to disappear
    await this.page.waitForSelector('.p-progress-spinner', { state: 'hidden', timeout: 10000 }).catch(() => {
      // Spinner might not appear if loading is fast
    });
    await this.page.waitForTimeout(500);
  }

  /**
   * Get the current active tab
   */
  async getActiveTab() {
    const activeTabElement = this.page.locator('.data-tables-tabs .p-tabmenu-item.p-tabmenu-item-active .p-tabmenu-item-label');
    return await activeTabElement.textContent();
  }

  /**
   * Switch to a specific tab by name
   */
  async switchToTab(tabName) {
    const tabButton = this.page.locator(`.data-tables-tabs .p-tabmenu-item:has-text("${tabName}")`);
    await tabButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Get quick stats values
   */
  async getQuickStats() {
    const statsContainer = this.page.locator('.quick-stats');
    const statItems = statsContainer.locator('.stat-item');
    const count = await statItems.count();

    const stats = {};
    for (let i = 0; i < count; i++) {
      const item = statItems.nth(i);
      const label = await item.locator('.gp-metric-label').textContent();
      const value = await item.locator('.gp-metric-value').textContent();
      stats[label.trim()] = parseInt(value.trim());
    }

    return stats;
  }

  /**
   * Get the date range display text
   */
  async getDateRangeText() {
    const dateRangeElement = this.page.locator('.date-range-info span');
    return await dateRangeElement.textContent();
  }

  /**
   * Click Export All Data button
   */
  async clickExportAllData() {
    const exportButton = this.page.locator('button:has-text("Export All Data")');
    await exportButton.click();
  }

  /**
   * Click individual table export button
   */
  async clickTableExport() {
    const exportButton = this.page.locator('.export-button:has-text("Export CSV")');
    await exportButton.click();
  }

  /**
   * Get table row count
   */
  async getTableRowCount() {
    const rows = this.page.locator('.p-datatable-tbody tr');
    return await rows.count();
  }

  /**
   * Get table data from current tab
   */
  async getTableData() {
    const rows = this.page.locator('.p-datatable-tbody tr');
    const count = await rows.count();

    const data = [];
    for (let i = 0; i < count; i++) {
      const row = rows.nth(i);
      const cells = row.locator('td');
      const cellCount = await cells.count();

      const rowData = [];
      for (let j = 0; j < cellCount; j++) {
        const cellText = await cells.nth(j).textContent();
        rowData.push(cellText.trim());
      }
      data.push(rowData);
    }

    return data;
  }

  /**
   * Search in table
   */
  async searchInTable(searchTerm) {
    const searchInput = this.page.locator('.search-input');
    await searchInput.fill(searchTerm);
    await this.page.waitForTimeout(500);
  }

  /**
   * Check if no data state is visible
   */
  async isNoDataStateVisible() {
    const noDataCard = this.page.locator('.no-data-card');
    return await noDataCard.isVisible().catch(() => false);
  }

  /**
   * Get no data message text
   */
  async getNoDataMessage() {
    const noDataMessage = this.page.locator('.no-data-card .no-data-message');
    return await noDataMessage.textContent();
  }

  /**
   * Check if loading state is visible
   */
  async isLoadingStateVisible() {
    const loadingPlaceholder = this.page.locator('.gp-loading-placeholder');
    return await loadingPlaceholder.isVisible().catch(() => false);
  }

  /**
   * Get table count display text (e.g., "23 stays")
   */
  async getTableCountText() {
    const countElement = this.page.locator('.table-count');
    return await countElement.textContent();
  }

  /**
   * Select a duration filter option
   */
  async selectDurationFilter(filterLabel) {
    const durationFilter = this.page.locator('.duration-filter');
    await durationFilter.click();
    await this.page.waitForTimeout(300);

    const option = this.page.locator(`.p-select-option:has-text("${filterLabel}")`);
    await option.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Clear all filters
   */
  async clearFilters() {
    // Clear search
    const searchInput = this.page.locator('.search-input');
    await searchInput.fill('');

    // Clear duration filter if it has a value
    const durationFilterClear = this.page.locator('.duration-filter .p-select-clear-icon');
    if (await durationFilterClear.isVisible().catch(() => false)) {
      await durationFilterClear.click();
    }

    await this.page.waitForTimeout(500);
  }

  /**
   * Click on a table row
   */
  async clickTableRow(rowIndex) {
    const rows = this.page.locator('.p-datatable-tbody tr');
    await rows.nth(rowIndex).click();
  }

  /**
   * Check if table has pagination
   */
  async hasPagination() {
    const paginator = this.page.locator('.p-paginator');
    return await paginator.isVisible().catch(() => false);
  }

  /**
   * Get current page number
   */
  async getCurrentPage() {
    const currentPageButton = this.page.locator('.p-paginator-page.p-paginator-page-active');
    const pageText = await currentPageButton.textContent();
    return parseInt(pageText.trim());
  }

  /**
   * Navigate to next page
   */
  async goToNextPage() {
    const nextButton = this.page.locator('.p-paginator-next');
    await nextButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Navigate to previous page
   */
  async goToPreviousPage() {
    const prevButton = this.page.locator('.p-paginator-prev');
    await prevButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Verify database data consistency
   */
  static async verifyTimelineDataExists(dbManager, userId) {
    const result = await dbManager.client.query(
      'SELECT COUNT(*) as count FROM timeline_stays WHERE user_id = $1',
      [userId]
    );
    return result.rows[0].count > 0;
  }
}
