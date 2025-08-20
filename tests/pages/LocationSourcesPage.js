export class LocationSourcesPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      addSourceBtn: '[data-tour="add-source-btn"]',
      quickSetupButtons: {
        owntracks: 'button:has-text("Setup OwnTracks")',
        overland: 'button:has-text("Setup Overland")',
        dawarich: 'button:has-text("Setup Dawarich")'
      },
      dialog: {
        container: '.source-dialog',
        header: '.p-dialog-header',
        sourceTypeOptions: {
          owntracks: '.source-type-option:has-text("OwnTracks")',
          overland: '.source-type-option:has-text("Overland")',
          dawarich: '.source-type-option:has-text("Dawarich")'
        },
        connectionTypeOptions: {
          http: '.connection-type-option:has-text("HTTP")',
          mqtt: '.connection-type-option:has-text("MQTT")'
        },
        fields: {
          username: '#username',
          password: '#password input',
          token: '#token',
          apiKey: '#apiKey'
        },
        buttons: {
          cancel: 'button:has-text("Cancel")',
          save: 'button:has-text("Add Source")',
          saveEdit: 'button:has-text("Save Changes")'
        }
      },
      sourceCards: '.source-card',
      sourceActions: {
        instructions: 'button:has-text("Instructions")',
        edit: 'button:has-text("Edit")',
        delete: 'button:has(.pi-trash)'
      },
      instructionsCard: '.instructions-card',
      confirmDialog: '.p-confirmdialog',
      toast: '.p-toast',
      noSourcesMessage: '.quick-setup-guide'
    }
  }

  /**
   * Check if currently on location sources page
   */
  async isOnLocationSourcesPage() {
    try {
      await this.page.waitForURL('**/app/location-sources', { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Navigate to location sources page
   */
  async navigate() {
    await this.page.goto('/app/location-sources');
  }

  /**
   * Wait for location sources page to load
   */
  async waitForPageLoad() {
    await this.page.waitForURL('**/app/location-sources**');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Check if no sources are configured (shows quick setup guide)
   */
  async hasNoSources() {
    return await this.page.locator(this.selectors.noSourcesMessage).isVisible();
  }

  /**
   * Click the "Add New Source" button
   */
  async clickAddNewSource() {
    await this.page.locator(this.selectors.addSourceBtn).click();
  }

  /**
   * Click a quick setup button for specific source type
   */
  async clickQuickSetup(sourceType) {
    const selector = this.selectors.quickSetupButtons[sourceType.toLowerCase()];
    await this.page.locator(selector).click();
  }

  /**
   * Wait for add/edit dialog to appear
   */
  async waitForDialog() {
    await this.page.waitForSelector(this.selectors.dialog.container, { state: 'visible' });
  }

  /**
   * Check if add/edit dialog is visible
   */
  async isDialogVisible() {
    return await this.page.locator(this.selectors.dialog.container).isVisible();
  }

  /**
   * Select source type in dialog
   */
  async selectSourceType(sourceType) {
    const selector = this.selectors.dialog.sourceTypeOptions[sourceType.toLowerCase()];
    await this.page.locator(selector).click();
  }

  /**
   * Select connection type for OwnTracks
   */
  async selectConnectionType(connectionType) {
    const selector = this.selectors.dialog.connectionTypeOptions[connectionType.toLowerCase()];
    await this.page.locator(selector).click();
  }

  /**
   * Fill OwnTracks form fields
   */
  async fillOwnTracksForm(username, password, connectionType = 'HTTP') {
    await this.selectConnectionType(connectionType);
    await this.page.fill(this.selectors.dialog.fields.username, username);
    await this.page.fill(this.selectors.dialog.fields.password, password);
  }

  /**
   * Fill Overland form fields
   */
  async fillOverlandForm(token) {
    await this.page.fill(this.selectors.dialog.fields.token, token);
  }

  /**
   * Fill Dawarich form fields
   */
  async fillDawarichForm(apiKey) {
    await this.page.fill(this.selectors.dialog.fields.apiKey, apiKey);
  }

  /**
   * Click save button in dialog
   */
  async clickSave() {
    await this.page.locator(this.selectors.dialog.buttons.save).click();
  }

  /**
   * Click save changes button in edit dialog
   */
  async clickSaveEdit() {
    await this.page.locator(this.selectors.dialog.buttons.saveEdit).click();
  }

  /**
   * Click cancel button in dialog
   */
  async clickCancel() {
    await this.page.locator(this.selectors.dialog.buttons.cancel).click();
  }

  /**
   * Wait for dialog to close
   */
  async waitForDialogClose() {
    await this.page.waitForSelector(this.selectors.dialog.container, { state: 'hidden' });
  }

  /**
   * Get count of configured sources
   */
  async getSourceCount() {
    return await this.page.locator(this.selectors.sourceCards).count();
  }

  /**
   * Get source cards
   */
  async getSourceCards() {
    return await this.page.locator(this.selectors.sourceCards);
  }

  /**
   * Click edit button for a source by index
   */
  async clickEditSource(sourceIndex = 0) {
    const sourceCard = this.page.locator(this.selectors.sourceCards).nth(sourceIndex);
    await sourceCard.locator(this.selectors.sourceActions.edit).click();
  }

  /**
   * Click delete button for a source by index
   */
  async clickDeleteSource(sourceIndex = 0) {
    const sourceCard = this.page.locator(this.selectors.sourceCards).nth(sourceIndex);
    await sourceCard.locator(this.selectors.sourceActions.delete).click();
  }

  /**
   * Click instructions button for a source by index
   */
  async clickInstructions(sourceIndex = 0) {
    const sourceCard = this.page.locator(this.selectors.sourceCards).nth(sourceIndex);
    await sourceCard.locator(this.selectors.sourceActions.instructions).click();
  }

  /**
   * Wait for instructions card to be visible
   */
  async waitForInstructions() {
    await this.page.waitForSelector(this.selectors.instructionsCard, { state: 'visible' });
  }

  /**
   * Check if instructions are visible
   */
  async areInstructionsVisible() {
    return await this.page.locator(this.selectors.instructionsCard).isVisible();
  }

  /**
   * Confirm delete in confirmation dialog
   */
  async confirmDelete() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { state: 'visible' });
    await this.page.locator('.p-confirmdialog button:has-text("Delete")').click();
  }

  /**
   * Cancel delete in confirmation dialog
   */
  async cancelDelete() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { state: 'visible' });
    await this.page.locator('.p-confirmdialog button:has-text("Cancel")').click();
  }

  /**
   * Wait for success toast message
   */
  async waitForSuccessToast() {
    await this.page.waitForSelector('.p-toast-message-success', { state: 'visible' });
  }

  /**
   * Wait for error toast message
   */
  async waitForErrorToast() {
    await this.page.waitForSelector('.p-toast-message-error', { state: 'visible' });
  }

  /**
   * Get toast message text
   */
  async getToastMessage() {
    const toast = this.page.locator('.p-toast-message-content');
    await toast.waitFor({ state: 'visible' });
    return await toast.textContent();
  }

  /**
   * Get source type from a source card
   */
  async getSourceType(sourceIndex = 0) {
    const sourceCard = this.page.locator(this.selectors.sourceCards).nth(sourceIndex);
    const sourceType = sourceCard.locator('.source-type');
    return await sourceType.textContent();
  }

  /**
   * Get source identifier from a source card
   */
  async getSourceIdentifier(sourceIndex = 0) {
    const sourceCard = this.page.locator(this.selectors.sourceCards).nth(sourceIndex);
    const identifier = sourceCard.locator('.source-identifier');
    return await identifier.textContent();
  }

  /**
   * Complete OwnTracks HTTP source creation flow
   */
  async createOwnTracksHttpSource(username, password) {
    await this.clickAddNewSource();
    await this.waitForDialog();
    await this.selectSourceType('OWNTRACKS');
    await this.fillOwnTracksForm(username, password, 'HTTP');
    await this.clickSave();
    await this.waitForSuccessToast();
    await this.waitForDialogClose();
  }

  /**
   * Complete OwnTracks MQTT source creation flow
   */
  async createOwnTracksMqttSource(username, password) {
    await this.clickAddNewSource();
    await this.waitForDialog();
    await this.selectSourceType('OWNTRACKS');
    await this.fillOwnTracksForm(username, password, 'MQTT');
    await this.clickSave();
    await this.waitForSuccessToast();
    await this.waitForDialogClose();
  }

  /**
   * Complete Overland source creation flow
   */
  async createOverlandSource(token) {
    await this.clickAddNewSource();
    await this.waitForDialog();
    await this.selectSourceType('OVERLAND');
    await this.fillOverlandForm(token);
    await this.clickSave();
    await this.waitForSuccessToast();
    await this.waitForDialogClose();
  }

  /**
   * Complete Dawarich source creation flow
   */
  async createDawarichSource(apiKey) {
    await this.clickAddNewSource();
    await this.waitForDialog();
    await this.selectSourceType('DAWARICH');
    await this.fillDawarichForm(apiKey);
    await this.clickSave();
    await this.waitForSuccessToast();
    await this.waitForDialogClose();
  }

  /**
   * Database verification helpers
   */
  
  /**
   * Get GPS source from database by type and identifier
   */
  static async getGpsSourceFromDb(dbManager, userId, sourceType, identifier) {
    const query = `
      SELECT * FROM gps_source_config 
      WHERE user_id = $1 AND source_type = $2 AND (username = $3 OR token = $3)
    `;
    const result = await dbManager.client.query(query, [userId, sourceType, identifier]);
    return result.rows[0] || null;
  }

  /**
   * Get all GPS sources for user from database
   */
  static async getAllGpsSourcesFromDb(dbManager, userId) {
    const query = `SELECT * FROM gps_source_config WHERE user_id = $1`;
    const result = await dbManager.client.query(query, [userId]);
    return result.rows;
  }

  /**
   * Verify GPS source exists in database
   */
  static async verifyGpsSourceInDb(dbManager, userId, sourceType, identifier, shouldExist = true) {
    const source = await this.getGpsSourceFromDb(dbManager, userId, sourceType, identifier);
    return shouldExist ? source !== null : source === null;
  }

  /**
   * Get GPS source count from database
   */
  static async getGpsSourceCountFromDb(dbManager, userId) {
    const query = `SELECT COUNT(*) as count FROM gps_source_config WHERE user_id = $1`;
    const result = await dbManager.client.query(query, [userId]);
    return parseInt(result.rows[0].count);
  }

  /**
   * Wait for expected source count in database with retry logic
   */
  static async waitForSourceCountInDb(dbManager, userId, expectedCount, maxAttempts = 10, delayMs = 500) {
    let actualCount;
    let attempts = 0;
    
    do {
      if (attempts > 0) {
        await new Promise(resolve => setTimeout(resolve, delayMs));
      }
      actualCount = await this.getGpsSourceCountFromDb(dbManager, userId);
      attempts++;
      console.log(`DB count check attempt ${attempts}: expected=${expectedCount}, actual=${actualCount}`);
    } while (actualCount !== expectedCount && attempts < maxAttempts);
    
    return actualCount;
  }

  /**
   * Wait for source to be deleted from database
   */
  static async waitForSourceDeletionInDb(dbManager, userId, sourceType, identifier, maxAttempts = 10, delayMs = 500) {
    let source;
    let attempts = 0;
    
    do {
      if (attempts > 0) {
        await new Promise(resolve => setTimeout(resolve, delayMs));
      }
      source = await this.getGpsSourceFromDb(dbManager, userId, sourceType, identifier);
      attempts++;
      console.log(`DB deletion check attempt ${attempts}: source exists=${source !== null}`);
    } while (source !== null && attempts < maxAttempts);
    
    return source;
  }
}