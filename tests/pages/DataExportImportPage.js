export class DataExportImportPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Tab navigation
      tabs: {
        export: '.export-import-tabs .p-tabmenu-item:has-text("Export Data")',
        import: '.export-import-tabs .p-tabmenu-item:has-text("Import Data")'
      },

      // Export tab selectors
      export: {
        // Format selection
        formatOptions: {
          geopulse: '#geopulse',
          owntracks: '#owntracks'
        },
        formatOptionLabels: {
          geopulse: 'label[for="geopulse"]',
          owntracks: 'label[for="owntracks"]'
        },

        // Data types selection
        dataTypeCheckboxes: '.data-type-checkbox',
        selectAllButton: '.form-section-header .select-all-button, .export-form .select-all-button',

        // Individual data type checkboxes
        dataTypes: {
          rawgps: '#rawgps',
          favorites: '#favorites',
          reversegeocodinglocation: '#reversegeocodinglocation',
          locationsources: '#locationsources',
          userinfo: '#userinfo'
        },

        // Date range
        startDate: '#startDate input',
        endDate: '#endDate input',
        datePresets: {
          last30days: 'button:has-text("Last 30 Days")',
          last90days: 'button:has-text("Last 90 Days")',
          lastyear: 'button:has-text("Last Year")',
          alltime: 'button:has-text("All Time")'
        },

        // Export button
        exportButton: '.export-button',

        // Export job status card
        jobCard: '.job-status-card',
        jobStatus: '.job-status-card .p-tag',
        jobProgress: '.job-status-card .p-progressbar',
        downloadButton: '.job-status-card button:has-text("Download")',
        deleteButton: '.job-status-card button:has-text("Delete")'
      },

      // Import tab selectors
      import: {
        // Format selection
        formatOptions: {
          geopulse: '#import-geopulse',
          owntracks: '#import-owntracks',
          googleTimeline: '#import-google-timeline',
          gpx: '#import-gpx'
        },
        formatOptionLabels: {
          geopulse: 'label[for="import-geopulse"]',
          owntracks: 'label[for="import-owntracks"]',
          googleTimeline: 'label[for="import-google-timeline"]',
          gpx: 'label[for="import-gpx"]'
        },

        // File upload
        fileUpload: '.file-upload-container input[type="file"]',
        fileUploadButton: '.file-upload-container .p-fileupload-choose',

        // Import options - data types
        dataTypeCheckboxes: '.import-data-type input[type="checkbox"]',
        selectAllButton: '.option-group-header .select-all-button',
        dataTypes: {
          rawgps: '#import-rawgps',
          favorites: '#import-favorites',
          reversegeocodinglocation: '#import-reversegeocodinglocation',
          locationsources: '#import-locationsources',
          userinfo: '#import-userinfo'
        },

        // Import options - date filter
        dateFilterCheckbox: '#dateFilter',
        importStartDate: '#importStartDate input',
        importEndDate: '#importEndDate input',

        // Import options - clear data
        clearDataCheckbox: '#clearDataBeforeImport',

        // Import button
        importButton: '.import-button',

        // Import job status card
        jobCard: '.job-status-card',
        jobStatus: '.job-status-card .p-tag',
        jobProgress: '.job-status-card .p-progressbar',
        importSummary: '.import-summary',
        summaryItems: '.summary-item'
      },

      // Common selectors
      toast: '.p-toast',
      toastSuccess: '.p-toast-message-success',
      toastError: '.p-toast-message-error',
      confirmDialog: '.p-confirmdialog',
      confirmDialogAccept: '.p-confirmdialog button:has-text("Delete")',
      confirmDialogReject: '.p-confirmdialog button:has-text("Cancel")'
    };
  }

  /**
   * Navigation & Page State
   */
  async navigate() {
    await this.page.goto('/app/data-export-import');
  }

  async waitForPageLoad() {
    await this.page.waitForURL('**/app/data-export-import**');
    await this.page.waitForLoadState('networkidle');
  }

  async isOnDataExportImportPage() {
    try {
      await this.page.waitForURL('**/app/data-export-import', { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  async switchToTab(tabName) {
    const selector = this.selectors.tabs[tabName.toLowerCase()];
    await this.page.locator(selector).click();
    await this.page.waitForTimeout(500); // Wait for tab transition
  }

  async isTabActive(tabName) {
    const selector = this.selectors.tabs[tabName.toLowerCase()];
    const tab = this.page.locator(selector);
    const classes = await tab.getAttribute('class');
    return classes.includes('p-tabmenu-item-active');
  }

  /**
   * Export Tab Actions
   */
  async selectExportFormat(format) {
    const labelSelector = this.selectors.export.formatOptionLabels[format.toLowerCase()];
    await this.page.locator(labelSelector).click();
    await this.page.waitForTimeout(300);
  }

  async getSelectedExportFormat() {
    // Check which radio button is checked
    for (const [format, selector] of Object.entries(this.selectors.export.formatOptions)) {
      const isChecked = await this.page.locator(selector).isChecked();
      if (isChecked) {
        return format;
      }
    }
    return null;
  }

  async selectDataType(dataType) {
    const selector = this.selectors.export.dataTypes[dataType.toLowerCase()];
    const checkbox = this.page.locator(selector);
    const isChecked = await checkbox.isChecked();
    if (!isChecked) {
      await checkbox.check();
    }
  }

  async deselectDataType(dataType) {
    const selector = this.selectors.export.dataTypes[dataType.toLowerCase()];
    const checkbox = this.page.locator(selector);
    const isChecked = await checkbox.isChecked();
    if (isChecked) {
      await checkbox.uncheck();
    }
  }

  async selectDataTypes(dataTypes) {
    for (const dataType of dataTypes) {
      await this.selectDataType(dataType);
    }
  }

  async clickSelectAllDataTypes() {
    await this.page.locator(this.selectors.export.selectAllButton).first().click();
    await this.page.waitForTimeout(200);
  }

  async getSelectedDataTypes() {
    const selected = [];
    for (const [dataType, selector] of Object.entries(this.selectors.export.dataTypes)) {
      const isChecked = await this.page.locator(selector).isChecked();
      if (isChecked) {
        selected.push(dataType);
      }
    }
    return selected;
  }

  async setDateRange(startDate, endDate) {
    // Clear and fill start date
    await this.page.locator(this.selectors.export.startDate).click();
    await this.page.locator(this.selectors.export.startDate).fill('');
    await this.page.locator(this.selectors.export.startDate).type(startDate);

    // Clear and fill end date
    await this.page.locator(this.selectors.export.endDate).click();
    await this.page.locator(this.selectors.export.endDate).fill('');
    await this.page.locator(this.selectors.export.endDate).type(endDate);

    await this.page.waitForTimeout(300);
  }

  async clickDateRangePreset(preset) {
    // Normalize preset name to match selector keys
    const presetKey = preset.toLowerCase().replace(/\s+/g, '');
    const selector = this.selectors.export.datePresets[presetKey];

    if (!selector) {
      throw new Error(`Unknown date preset: ${preset}. Available presets: ${Object.keys(this.selectors.export.datePresets).join(', ')}`);
    }

    await this.page.locator(selector).click();
    await this.page.waitForTimeout(300);
  }

  async clickStartExport() {
    await this.page.locator(this.selectors.export.exportButton).click();
  }

  async isExportButtonDisabled() {
    return await this.page.locator(this.selectors.export.exportButton).isDisabled();
  }

  async waitForExportJobCard() {
    await this.page.waitForSelector(this.selectors.export.jobCard, { state: 'visible', timeout: 10000 });
  }

  async isExportJobCardVisible() {
    return await this.page.locator(this.selectors.export.jobCard).isVisible();
  }

  async getExportJobStatus() {
    const statusTag = this.page.locator(this.selectors.export.jobStatus);
    await statusTag.waitFor({ state: 'visible' });
    return await statusTag.textContent();
  }

  async waitForExportJobStatus(status, timeout = 30000) {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
      const currentStatus = await this.getExportJobStatus();
      if (currentStatus.toLowerCase().includes(status.toLowerCase())) {
        return true;
      }
      await this.page.waitForTimeout(1000);
    }
    throw new Error(`Export job did not reach status "${status}" within ${timeout}ms`);
  }

  async getExportJobProgress() {
    const progressBar = this.page.locator(this.selectors.export.jobProgress);
    const ariaValueNow = await progressBar.getAttribute('aria-valuenow');
    return parseInt(ariaValueNow) || 0;
  }

  async clickDownloadExport() {
    await this.page.locator(this.selectors.export.downloadButton).click();
  }

  async clickDeleteExport() {
    await this.page.locator(this.selectors.export.deleteButton).click();
  }

  async confirmDeleteExport() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { state: 'visible' });
    await this.page.locator('.p-confirmdialog button:has-text("Delete")').click();
    await this.page.waitForTimeout(500);
  }

  async cancelDeleteExport() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { state: 'visible' });
    await this.page.locator('.p-confirmdialog button:has-text("Cancel")').click();
  }

  /**
   * Import Tab Actions
   */
  async selectImportFormat(format) {
    // Map format names to selector keys
    const formatMap = {
      'geopulse': 'geopulse',
      'owntracks': 'owntracks',
      'google-timeline': 'googleTimeline',
      'gpx': 'gpx'
    };

    const selectorKey = formatMap[format.toLowerCase()] || format.toLowerCase();
    const labelSelector = this.selectors.import.formatOptionLabels[selectorKey];

    if (!labelSelector) {
      throw new Error(`Unknown import format: ${format}. Available formats: ${Object.keys(formatMap).join(', ')}`);
    }

    await this.page.locator(labelSelector).click();
    await this.page.waitForTimeout(300);
  }

  async getSelectedImportFormat() {
    // Check which radio button is checked
    for (const [format, selector] of Object.entries(this.selectors.import.formatOptions)) {
      const isChecked = await this.page.locator(selector).isChecked();
      if (isChecked) {
        return format;
      }
    }
    return null;
  }

  async uploadFile(filePath) {
    const fileInput = this.page.locator(this.selectors.import.fileUpload);
    await fileInput.setInputFiles(filePath);
    await this.page.waitForTimeout(500);
  }

  async selectImportDataType(dataType) {
    const selector = this.selectors.import.dataTypes[dataType.toLowerCase()];
    const checkbox = this.page.locator(selector);
    const isChecked = await checkbox.isChecked();
    if (!isChecked) {
      await checkbox.check();
    }
  }

  async deselectImportDataType(dataType) {
    const selector = this.selectors.import.dataTypes[dataType.toLowerCase()];
    const checkbox = this.page.locator(selector);
    const isChecked = await checkbox.isChecked();
    if (isChecked) {
      await checkbox.uncheck();
    }
  }

  async selectImportDataTypes(dataTypes) {
    for (const dataType of dataTypes) {
      await this.selectImportDataType(dataType);
    }
  }

  async clickSelectAllImportDataTypes() {
    await this.page.locator(this.selectors.import.selectAllButton).click();
    await this.page.waitForTimeout(200);
  }

  async getSelectedImportDataTypes() {
    const selected = [];
    for (const [dataType, selector] of Object.entries(this.selectors.import.dataTypes)) {
      const isChecked = await this.page.locator(selector).isChecked();
      if (isChecked) {
        selected.push(dataType);
      }
    }
    return selected;
  }

  async enableDateFilter(startDate, endDate) {
    // Enable the checkbox
    const checkbox = this.page.locator(this.selectors.import.dateFilterCheckbox);
    const isChecked = await checkbox.isChecked();
    if (!isChecked) {
      await checkbox.check();
      await this.page.waitForTimeout(300);
    }

    // Fill dates
    await this.page.locator(this.selectors.import.importStartDate).click();
    await this.page.locator(this.selectors.import.importStartDate).fill('');
    await this.page.locator(this.selectors.import.importStartDate).type(startDate);

    await this.page.locator(this.selectors.import.importEndDate).click();
    await this.page.locator(this.selectors.import.importEndDate).fill('');
    await this.page.locator(this.selectors.import.importEndDate).type(endDate);

    await this.page.waitForTimeout(300);
  }

  async disableDateFilter() {
    const checkbox = this.page.locator(this.selectors.import.dateFilterCheckbox);
    const isChecked = await checkbox.isChecked();
    if (isChecked) {
      await checkbox.uncheck();
    }
  }

  async enableClearDataBeforeImport() {
    const checkbox = this.page.locator(this.selectors.import.clearDataCheckbox);
    const isChecked = await checkbox.isChecked();
    if (!isChecked) {
      await checkbox.check();
    }
  }

  async disableClearDataBeforeImport() {
    const checkbox = this.page.locator(this.selectors.import.clearDataCheckbox);
    const isChecked = await checkbox.isChecked();
    if (isChecked) {
      await checkbox.uncheck();
    }
  }

  async clickStartImport() {
    await this.page.locator(this.selectors.import.importButton).click();
  }

  async isImportButtonDisabled() {
    return await this.page.locator(this.selectors.import.importButton).isDisabled();
  }

  async waitForImportJobCard() {
    await this.page.waitForSelector(this.selectors.import.jobCard, { state: 'visible', timeout: 10000 });
  }

  async isImportJobCardVisible() {
    return await this.page.locator(this.selectors.import.jobCard).isVisible();
  }

  async getImportJobStatus() {
    const statusTag = this.page.locator(this.selectors.import.jobStatus);
    await statusTag.waitFor({ state: 'visible' });
    return await statusTag.textContent();
  }

  async waitForImportJobStatus(status, timeout = 120000) {
    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
      const currentStatus = await this.getImportJobStatus();
      if (currentStatus.toLowerCase().includes(status.toLowerCase())) {
        return true;
      }
      await this.page.waitForTimeout(2000);
    }
    throw new Error(`Import job did not reach status "${status}" within ${timeout}ms`);
  }

  async getImportJobProgress() {
    const progressBar = this.page.locator(this.selectors.import.jobProgress);
    const ariaValueNow = await progressBar.getAttribute('aria-valuenow');
    return parseInt(ariaValueNow) || 0;
  }

  async getImportSummary() {
    // Check if import summary exists (it may not be sent by backend)
    const summaryExists = await this.page.locator(this.selectors.import.importSummary).isVisible().catch(() => false);

    if (!summaryExists) {
      console.log('Import summary not available (not sent by backend)');
      return null;
    }

    const summaryItems = this.page.locator(this.selectors.import.summaryItems);
    const count = await summaryItems.count();

    const summary = {};
    for (let i = 0; i < count; i++) {
      const text = await summaryItems.nth(i).textContent();

      // Parse text like "1,234 GPS points" or "5 favorite locations"
      const match = text.match(/(\d{1,3}(?:,\d{3})*)\s+(.+)/);
      if (match) {
        const value = parseInt(match[1].replace(/,/g, ''));
        const key = match[2].toLowerCase();

        if (key.includes('gps')) summary.rawGpsPoints = value;
        if (key.includes('timeline')) summary.timelineItems = value;
        if (key.includes('favorite')) summary.favoriteLocations = value;
        if (key.includes('location source')) summary.locationSources = value;
      }
    }

    return summary;
  }

  /**
   * Common Actions
   */
  async waitForSuccessToast(expectedText = null) {
    await this.page.waitForSelector(this.selectors.toastSuccess, { state: 'visible', timeout: 10000 });

    if (expectedText) {
      const toast = this.page.locator(this.selectors.toastSuccess).last();
      const text = await toast.textContent();
      if (!text.toLowerCase().includes(expectedText.toLowerCase())) {
        throw new Error(`Toast message "${text}" does not contain expected text "${expectedText}"`);
      }
    }
  }

  async waitForErrorToast() {
    await this.page.waitForSelector(this.selectors.toastError, { state: 'visible', timeout: 10000 });
  }

  async getToastMessage() {
    const toast = this.page.locator('.p-toast-message-content').last();
    await toast.waitFor({ state: 'visible' });
    return await toast.textContent();
  }

  async waitForToastToDisappear() {
    await this.page.waitForSelector(this.selectors.toast, { state: 'hidden', timeout: 10000 });
  }

  /**
   * Database Verification Helpers (Static Methods)
   */

  /**
   * Note: Export/Import jobs are stored in memory, not in the database.
   * Job verification should be done through the UI or API responses.
   */

  /**
   * Get count of raw GPS points for a user, optionally filtered by date range
   */
  static async getRawGpsPointsCount(dbManager, userId, dateRange = null) {
    let query = 'SELECT COUNT(*) as count FROM gps_points WHERE user_id = $1';
    const params = [userId];

    if (dateRange) {
      query += ' AND timestamp >= $2 AND timestamp <= $3';
      params.push(dateRange.startDate, dateRange.endDate);
    }

    const result = await dbManager.client.query(query, params);
    return parseInt(result.rows[0].count);
  }

  /**
   * Get count of favorite locations for a user
   */
  static async getFavoritesCount(dbManager, userId) {
    const query = 'SELECT COUNT(*) as count FROM favorite_locations WHERE user_id = $1';
    const result = await dbManager.client.query(query, [userId]);
    return parseInt(result.rows[0].count);
  }

  /**
   * Get count of location sources for a user
   */
  static async getLocationSourcesCount(dbManager, userId) {
    const query = 'SELECT COUNT(*) as count FROM gps_source_config WHERE user_id = $1';
    const result = await dbManager.client.query(query, [userId]);
    return parseInt(result.rows[0].count);
  }

  /**
   * Get count of reverse geocoding cache entries for a user
   */
  static async getReverseGeocodingCount(dbManager, userId) {
    const query = 'SELECT COUNT(*) as count FROM reverse_geocoding_cache WHERE user_id = $1';
    const result = await dbManager.client.query(query, [userId]);
    return parseInt(result.rows[0].count);
  }

  /**
   * Insert sample GPS data for testing with reverse geocoding data
   */
  static async insertSampleGpsData(dbManager, userId, count, dateRange = null) {
    const insertedIds = [];

    // Default date range: last 30 days
    const endDate = dateRange?.endDate || new Date();
    const startDate = dateRange?.startDate || new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);

    // Calculate time interval between points
    const timeSpan = new Date(endDate) - new Date(startDate);
    const interval = timeSpan / count;

    for (let i = 0; i < count; i++) {
      // Generate timestamp
      const timestamp = new Date(new Date(startDate).getTime() + (i * interval));
      const timestampStr = timestamp.toISOString().replace('T', ' ').replace('Z', '');

      // Generate random coordinates (around Kyiv, Ukraine as example)
      const lat = 50.4501 + (Math.random() - 0.5) * 0.1; // ~5km variance
      const lon = 30.5234 + (Math.random() - 0.5) * 0.1;

      // Round coordinates to match what will be queried (to avoid precision mismatches)
      const roundedLat = Math.round(lat * 100000) / 100000;
      const roundedLon = Math.round(lon * 100000) / 100000;

      const coordsString = `POINT(${roundedLon} ${roundedLat})`;

      // Insert reverse geocoding location for this specific point to avoid API calls
      await dbManager.client.query(`
        INSERT INTO reverse_geocoding_location (id, request_coordinates, result_coordinates, display_name, provider_name, city, country, created_at, last_accessed_at)
        VALUES (nextval('reverse_geocoding_location_seq'), $1, $1, $2, 'test', 'Kyiv', 'Ukraine', NOW(), NOW())
        ON CONFLICT DO NOTHING
      `, [coordsString, `Test Location ${i + 1}, Kyiv, Ukraine`]);

      // Insert GPS point
      const query = `
        INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
        RETURNING id
      `;

      const result = await dbManager.client.query(query, [
        'test-device',
        userId,
        coordsString,
        timestampStr,
        Math.round((10 + Math.random() * 20) * 10) / 10, // accuracy 10-30m
        Math.max(1, Math.min(100, Math.round(100 - i * (100 / count)))), // battery decreasing
        Math.round((Math.random() * 20) * 100) / 100, // velocity 0-20 m/s
        Math.round((100 + Math.random() * 50) * 10) / 10, // altitude
        'OWNTRACKS',
        timestampStr
      ]);

      insertedIds.push(result.rows[0].id);
    }

    return insertedIds;
  }

  /**
   * Insert sample favorite locations (let database auto-generate IDs)
   */
  static async insertSampleFavorites(dbManager, userId, count) {
    const insertedIds = [];

    const sampleNames = ['Home', 'Work', 'Gym', 'Grocery Store', 'Park', 'Cafe', 'Restaurant', 'Mall'];

    for (let i = 0; i < count; i++) {
      // Generate random coordinates (around Kyiv)
      const lat = 50.4501 + (Math.random() - 0.5) * 0.2;
      const lon = 30.5234 + (Math.random() - 0.5) * 0.2;

      const name = i < sampleNames.length ? sampleNames[i] : `Location ${i + 1}`;

      // Let database auto-generate ID to avoid conflicts during import/export
      const query = `
        INSERT INTO favorite_locations (user_id, name, type, geometry)
        VALUES ($1, $2, $3, ST_GeomFromText($4, 4326))
        RETURNING id
      `;

      const result = await dbManager.client.query(query, [
        userId,
        name,
        'POINT',
        `POINT(${lon} ${lat})`
      ]);

      insertedIds.push(result.rows[0].id);
    }

    return insertedIds;
  }

  /**
   * Insert reverse geocoding data for sample import files to avoid Nominatim API calls
   */
  static async insertReverseGeocodingForSampleFiles(dbManager) {
    const locations = [
      // GPX sample file coordinates (7 points: 5 track + 2 waypoints)
      { lat: 50.4480, lon: 30.5210, name: 'Holosiivskyi District, Kyiv, Ukraine' },
      { lat: 50.4610, lon: 30.5340, name: 'Pechersk District, Kyiv, Ukraine' },
      { lat: 50.4501, lon: 30.5234, name: 'Maidan Nezalezhnosti, Kyiv, Ukraine' },
      { lat: 50.4520, lon: 30.5250, name: 'Khreshchatyk Street, Kyiv, Ukraine' },
      { lat: 50.4540, lon: 30.5270, name: 'European Square, Kyiv, Ukraine' },
      { lat: 50.4560, lon: 30.5290, name: 'Arsenalna Metro, Kyiv, Ukraine' },
      { lat: 50.4590, lon: 30.5320, name: 'Pecherska Lavra, Kyiv, Ukraine' },

      // OwnTracks sample file coordinates (10 points)
      { lat: 50.4510, lon: 30.5240, name: 'Khreshchatyk, Kyiv, Ukraine' },
      { lat: 50.4530, lon: 30.5260, name: 'Maidan Nezalezhnosti Area, Kyiv, Ukraine' },
      { lat: 50.4550, lon: 30.5280, name: 'Pechersk Area, Kyiv, Ukraine' },
      { lat: 50.4570, lon: 30.5300, name: 'Dnipro Embankment, Kyiv, Ukraine' },
      { lat: 50.4580, lon: 30.5310, name: 'Park Road Bridge, Kyiv, Ukraine' },

      // Common test coordinates in Kyiv area
      { lat: 50.45, lon: 30.52, name: 'Shevchenkivskyi District, Kyiv, Ukraine' },
      { lat: 50.46, lon: 30.53, name: 'Pechersk, Kyiv, Ukraine' }
    ];

    for (const location of locations) {
      const coordsString = `POINT(${location.lon} ${location.lat})`;

      try {
        await dbManager.client.query(`
          INSERT INTO reverse_geocoding_location (
            id, request_coordinates, result_coordinates, display_name,
            provider_name, city, country, created_at, last_accessed_at
          )
          VALUES (
            nextval('reverse_geocoding_location_seq'), $1, $1, $2,
            'test', 'Kyiv', 'Ukraine', NOW(), NOW()
          )
          ON CONFLICT DO NOTHING
        `, [coordsString, location.name]);
      } catch (error) {
        // Ignore conflicts - data already exists
      }
    }
  }

  /**
   * Verify data integrity - compare expected vs actual counts
   */
  static async verifyDataIntegrity(dbManager, userId, expectedCounts) {
    const actual = {};

    if (expectedCounts.rawGpsPoints !== undefined) {
      actual.rawGpsPoints = await this.getRawGpsPointsCount(dbManager, userId);
    }

    if (expectedCounts.favoriteLocations !== undefined) {
      actual.favoriteLocations = await this.getFavoritesCount(dbManager, userId);
    }

    if (expectedCounts.locationSources !== undefined) {
      actual.locationSources = await this.getLocationSourcesCount(dbManager, userId);
    }

    if (expectedCounts.reverseGeocodingCache !== undefined) {
      actual.reverseGeocodingCache = await this.getReverseGeocodingCount(dbManager, userId);
    }

    return {
      expected: expectedCounts,
      actual,
      matches: JSON.stringify(expectedCounts) === JSON.stringify(actual)
    };
  }

  /**
   * Wait for GPS points count to reach expected value (with retry)
   */
  static async waitForGpsPointsCount(dbManager, userId, expectedCount, maxAttempts = 20, delayMs = 1000) {
    let actualCount;
    let attempts = 0;

    do {
      if (attempts > 0) {
        await new Promise(resolve => setTimeout(resolve, delayMs));
      }
      actualCount = await this.getRawGpsPointsCount(dbManager, userId);
      attempts++;
      console.log(`GPS count check attempt ${attempts}: expected=${expectedCount}, actual=${actualCount}`);
    } while (actualCount !== expectedCount && attempts < maxAttempts);

    return actualCount;
  }
}
