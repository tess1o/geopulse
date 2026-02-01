export class GpsDataPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Page elements
      pageTitle: 'h1:has-text("GPS Data")',
      loadingIndicator: '.p-progressbar',
      
      // Summary stats cards
      statsGrid: '.stats-grid',
      statCards: '.stat-card',
      totalPointsCard: '.stat-card:has-text("Total GPS Points")',
      pointsTodayCard: '.stat-card:has-text("Points Today")',
      firstPointCard: '.stat-card:has-text("First GPS Point")',
      lastPointCard: '.stat-card:has-text("Latest GPS Point")',
      
      // Export button
      exportButton: 'button:has-text("Export CSV")',
      
      // Date filter controls
      filterSection: '.filter-section',
      dateRangePicker: '.date-picker input',
      clearFilterButton: 'button:has-text("Clear All")',
      
      // GPS Points table
      gpsTable: '.gps-data-table',
      tableRows: '.gps-data-table tbody tr',
      tableHeader: '.table-header',
      tableTitle: '.table-title',
      tableSubtitle: '.table-subtitle',
      emptyState: '.empty-state',
      emptyStateIcon: '.empty-icon',
      emptyStateText: '.empty-state p',
      
      // Table columns
      timestampColumn: '[data-field="timestamp"]',
      locationColumn: 'th:has-text("Location")',
      speedColumn: '[data-field="velocity"]',
      accuracyColumn: '[data-field="accuracy"]',
      altitudeColumn: '[data-field="altitude"]',
      batteryColumn: '[data-field="battery"]',
      sourceColumn: '[data-field="sourceType"]',
      
      // Table cell content
      timestampCells: '.timestamp-cell',
      coordinateCells: '.coordinates-cell',
      sourceTags: '.source-tag',
      
      // Pagination
      paginator: '.p-paginator',
      paginatorInfo: '.p-paginator-current',
      nextPageButton: '.p-paginator-next',
      prevPageButton: '.p-paginator-prev',
      pageButtons: '.p-paginator-page',
      
      // Toast messages
      toast: '.p-toast',
      toastSuccess: '.p-toast-message-success',
      toastError: '.p-toast-message-error'
    }
  }

  /**
   * Check if currently on GPS data page
   */
  async isOnGpsDataPage() {
    try {
      await this.page.waitForURL('**/app/gps-data', { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Navigate to GPS data page
   */
  async navigate() {
    await this.page.goto('/app/gps-data');
  }

  /**
   * Wait for page to load completely
   */
  async waitForPageLoad() {
    await this.page.waitForURL('**/app/gps-data**');
    await this.page.waitForLoadState('networkidle');
    
    // Wait for the page to finish loading (no loading indicators)
    await this.page.waitForSelector(this.selectors.loadingIndicator, { state: 'detached', timeout: 10000 }).catch(() => {});
  }

  /**
   * Get summary statistics values
   */
  async getSummaryStats() {
    const stats = {};
    
    // Total points
    const totalPointsLocator = this.page.locator(this.selectors.totalPointsCard);
    if (await totalPointsLocator.isVisible()) {
      const totalText = await totalPointsLocator.locator('.gp-metric-value').textContent();
      stats.totalPoints = this.parseNumberValue(totalText);
    }
    
    // Points today
    const pointsTodayLocator = this.page.locator(this.selectors.pointsTodayCard);
    if (await pointsTodayLocator.isVisible()) {
      const todayText = await pointsTodayLocator.locator('.gp-metric-value').textContent();
      stats.pointsToday = this.parseNumberValue(todayText);
    }
    
    // First point date
    const firstPointLocator = this.page.locator(this.selectors.firstPointCard);
    if (await firstPointLocator.isVisible()) {
      const firstText = await firstPointLocator.locator('.gp-metric-value').textContent();
      stats.firstPointDate = firstText?.trim();
    }
    
    // Last point date
    const lastPointLocator = this.page.locator(this.selectors.lastPointCard);
    if (await lastPointLocator.isVisible()) {
      const lastText = await lastPointLocator.locator('.gp-metric-value').textContent();
      stats.lastPointDate = lastText?.trim();
    }
    
    return stats;
  }

  /**
   * Parse number value from formatted text (handles commas)
   */
  parseNumberValue(text) {
    if (!text) return 0;
    // Remove commas and other formatting, parse as number
    const cleaned = text.replace(/[,\s]/g, '');
    const parsed = parseInt(cleaned);
    return isNaN(parsed) ? 0 : parsed;
  }

  /**
   * Check if GPS table has data
   */
  async hasGpsData() {
    const emptyState = this.page.locator(this.selectors.emptyState);
    return !(await emptyState.isVisible());
  }

  /**
   * Get number of GPS points currently displayed in table
   */
  async getDisplayedPointsCount() {
    if (!(await this.hasGpsData())) {
      return 0;
    }
    return await this.page.locator(this.selectors.tableRows).count();
  }

  /**
   * Get all GPS points data from current table page
   */
  async getDisplayedGpsPoints() {
    if (!(await this.hasGpsData())) {
      return [];
    }

    const rows = this.page.locator(this.selectors.tableRows);
    const count = await rows.count();
    const points = [];

    for (let i = 0; i < count; i++) {
      const row = rows.nth(i);
      
      // Extract timestamp
      const timestampCell = row.locator('.timestamp-cell');
      const date = await timestampCell.locator('.timestamp-date').textContent();
      const time = await timestampCell.locator('.timestamp-time').textContent();
      
      // Extract coordinates
      const coordinatesCell = row.locator('.coordinates-cell');
      const coordLines = coordinatesCell.locator('.coordinate-line');
      const coordCount = await coordLines.count();
      let lat, lng;
      if (coordCount >= 2) {
        lat = parseFloat(await coordLines.nth(0).textContent());
        lng = parseFloat(await coordLines.nth(1).textContent());
      }
      
      // Extract other data (with null handling)
      const speedText = await this.getTableCellText(row, 2);
      const speed = this.parseSpeed(speedText);
      
      const accuracyText = await this.getTableCellText(row, 3);
      const accuracy = this.parseAccuracy(accuracyText);
      
      const altitudeText = await this.getTableCellText(row, 4);
      const altitude = this.parseAltitude(altitudeText);
      
      const batteryText = await this.getTableCellText(row, 5);
      const battery = this.parseBattery(batteryText);
      
      const sourceTag = row.locator('.source-tag');
      const source = await sourceTag.isVisible() ? await sourceTag.textContent() : null;
      
      points.push({
        timestamp: `${date} ${time}`,
        coordinates: { lat, lng },
        speed,
        accuracy,
        altitude,
        battery,
        source
      });
    }

    return points;
  }

  /**
   * Helper to get table cell text by column index
   */
  async getTableCellText(row, columnIndex) {
    try {
      const cells = row.locator('td');
      if (await cells.count() > columnIndex) {
        return await cells.nth(columnIndex).textContent();
      }
      return null;
    } catch {
      return null;
    }
  }

  /**
   * Parse speed value (handles "km/h" suffix and null values)
   */
  parseSpeed(text) {
    if (!text || text.trim() === '-') return null;
    const match = text.match(/(\d+\.?\d*)/);
    return match ? parseFloat(match[1]) : null;
  }

  /**
   * Parse accuracy value (handles "m" suffix and null values)
   */
  parseAccuracy(text) {
    if (!text || text.trim() === '-') return null;
    const match = text.match(/(\d+\.?\d*)/);
    return match ? parseFloat(match[1]) : null;
  }

  /**
   * Parse altitude value (handles "m" suffix and null values)
   */
  parseAltitude(text) {
    if (!text || text.trim() === '-') return null;
    const match = text.match(/(\d+)/);
    return match ? parseInt(match[1]) : null;
  }

  /**
   * Parse battery value (handles "%" suffix and null values)
   */
  parseBattery(text) {
    if (!text || text.trim() === '-') return null;
    const match = text.match(/(\d+)/);
    return match ? parseInt(match[1]) : null;
  }

  /**
   * Set date range filter
   */
  async setDateRangeFilter(startDate, endDate) {
    // Click on date picker
    await this.page.locator(this.selectors.dateRangePicker).click();
    
    // Wait for date picker to open
    await this.page.waitForSelector('.p-datepicker', { state: 'visible' });
    
    // Select start date
    await this.selectDateInPicker(startDate);
    
    // Select end date  
    await this.selectDateInPicker(endDate);
    
    // Wait for the filter to be applied
    await this.page.waitForTimeout(1000);
  }

  /**
   * Helper to select a date in the date picker
   */
  async selectDateInPicker(date) {
    // Format: date should be a Date object
    const year = date.getFullYear();
    const month = date.getMonth(); // 0-indexed
    const day = date.getDate();
    
    // Navigate to correct month/year if needed
    await this.navigateToMonthYear(year, month);
    
    // Click on the day using aria-label
    const daySelector = `.p-datepicker-calendar td[aria-label="${day}"]`;
    await this.page.locator(daySelector).click();
  }

  /**
   * Navigate to specific month and year in date picker
   */
  async navigateToMonthYear(targetYear, targetMonth) {
    // Get current displayed month/year
    const monthButton = this.page.locator('.p-datepicker-select-month');
    const yearButton = this.page.locator('.p-datepicker-select-year');
    
    // Navigate to correct year first
    let currentYear = parseInt(await yearButton.textContent());
    while (currentYear !== targetYear) {
      if (currentYear < targetYear) {
        await this.page.locator('.p-datepicker-next-button').click();
      } else {
        await this.page.locator('.p-datepicker-prev-button').click();
      }
      await this.page.waitForTimeout(200);
      currentYear = parseInt(await yearButton.textContent());
    }
    
    // Navigate to correct month
    const monthNames = ['January', 'February', 'March', 'April', 'May', 'June',
                       'July', 'August', 'September', 'October', 'November', 'December'];
    const targetMonthName = monthNames[targetMonth];
    
    let currentMonthText = await monthButton.textContent();
    while (currentMonthText !== targetMonthName) {
      const currentMonthIndex = monthNames.indexOf(currentMonthText);
      
      if (currentMonthIndex < targetMonth) {
        await this.page.locator('.p-datepicker-next-button').click();
      } else {
        await this.page.locator('.p-datepicker-prev-button').click();
      }
      await this.page.waitForTimeout(200);
      currentMonthText = await monthButton.textContent();
    }
  }

  /**
   * Clear date range filter
   */
  async clearDateFilter() {
    await this.page.locator(this.selectors.clearFilterButton).click();
    await this.page.waitForTimeout(1000); // Wait for filter to be cleared
  }

  /**
   * Check if date filter is applied
   */
  async hasDateFilter() {
    // Check if the date filter chip is visible (only shown when filter is active)
    const dateFilterChip = this.page.locator('.active-filter-chips .p-chip').filter({ hasText: 'Date:' });
    const chipVisible = await dateFilterChip.isVisible().catch(() => false);
    if (chipVisible) return true;

    // Alternative: check if the "Clear" button next to date picker is visible
    const clearButton = this.page.locator('.filter-controls button:has-text("Clear")');
    return await clearButton.isVisible().catch(() => false);
  }

  /**
   * Get current date filter range text
   */
  async getDateFilterText() {
    const tableSubtitle = this.page.locator(this.selectors.tableSubtitle);
    if (await tableSubtitle.isVisible()) {
      return await tableSubtitle.textContent();
    }
    return null;
  }

  /**
   * Click export CSV button
   */
  async clickExportCsv() {
    await this.page.locator(this.selectors.exportButton).click();
  }

  /**
   * Check if export button is enabled
   */
  async isExportButtonEnabled() {
    const exportBtn = this.page.locator(this.selectors.exportButton);
    return !(await exportBtn.isDisabled());
  }

  /**
   * Check if export button is loading
   */
  async isExportButtonLoading() {
    const exportBtn = this.page.locator(this.selectors.exportButton);
    const loadingIcon = exportBtn.locator('.p-button-loading-icon');
    return await loadingIcon.isVisible();
  }

  /**
   * Wait for export to complete (no loading state)
   */
  async waitForExportComplete() {
    // Wait for loading state to disappear
    await this.page.waitForSelector(`${this.selectors.exportButton} .p-button-loading-icon`, { 
      state: 'detached',
      timeout: 10000 
    });
  }

  /**
   * Navigate to next page in pagination
   */
  async goToNextPage() {
    await this.page.locator(this.selectors.nextPageButton).click();
    await this.page.waitForTimeout(1000); // Wait for page to load
  }

  /**
   * Navigate to previous page in pagination
   */
  async goToPrevPage() {
    await this.page.locator(this.selectors.prevPageButton).click();
    await this.page.waitForTimeout(1000); // Wait for page to load
  }

  /**
   * Get current page info from paginator
   */
  async getPaginatorInfo() {
    const paginatorInfo = this.page.locator(this.selectors.paginatorInfo);
    if (await paginatorInfo.isVisible()) {
      return await paginatorInfo.textContent();
    }
    return null;
  }

  /**
   * Wait for success toast message
   */
  async waitForSuccessToast() {
    await this.page.waitForSelector(this.selectors.toastSuccess, { state: 'visible' });
  }

  /**
   * Wait for error toast message
   */
  async waitForErrorToast() {
    await this.page.waitForSelector(this.selectors.toastError, { state: 'visible' });
  }

  /**
   * Get toast message text
   */
  async getToastMessage() {
    const toast = this.page.locator(`${this.selectors.toast} .p-toast-message-content`);
    await toast.waitFor({ state: 'visible' });
    return await toast.textContent();
  }

  /**
   * Wait for table to reload after filter/pagination change
   */
  async waitForTableReload() {
    // Wait for any loading states to complete
    await this.page.waitForSelector('.p-datatable-loading-overlay', { 
      state: 'detached',
      timeout: 10000 
    }).catch(() => {}); // Ignore if not present
    
    // Wait for network to be idle
    await this.page.waitForLoadState('networkidle');
    
    // Small delay for UI to stabilize
    await this.page.waitForTimeout(500);
  }

  /**
   * Check if table is currently loading
   */
  async isTableLoading() {
    const loadingOverlay = this.page.locator('.p-datatable-loading-overlay');
    return await loadingOverlay.isVisible();
  }
}