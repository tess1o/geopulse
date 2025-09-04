import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {LocationSourcesPage} from '../pages/LocationSourcesPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {TestConfig} from '../config/test-config.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';

test.describe('Location Sources Management', () => {
  
  test.describe('Initial State and Source Creation', () => {
    test('should show no location sources initially and allow creating OwnTracks HTTP source', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      // Create user first
      await UserFactory.createUser(page, testUser);
      
      // Login to the app
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Navigate to location sources
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Verify we're on the location sources page
      expect(await locationSourcesPage.isOnLocationSourcesPage()).toBe(true);
      
      // Verify no location sources initially
      expect(await locationSourcesPage.hasNoSources()).toBe(true);
      expect(await locationSourcesPage.getSourceCount()).toBe(0);
      
      // Click on "Add new source"
      await locationSourcesPage.clickAddNewSource();
      await locationSourcesPage.waitForDialog();
      expect(await locationSourcesPage.isDialogVisible()).toBe(true);
      
      // Create OwnTracks HTTP source
      await locationSourcesPage.selectSourceType('OWNTRACKS');
      await locationSourcesPage.fillOwnTracksForm('testuser', 'testpassword', 'HTTP');
      await locationSourcesPage.clickSave();
      
      // Verify source is created
      await locationSourcesPage.waitForSuccessToast();
      await locationSourcesPage.waitForDialogClose();
      expect(await locationSourcesPage.getSourceCount()).toBe(1);
      
      // Verify setup instructions are available
      expect(await locationSourcesPage.areInstructionsVisible()).toBe(true);
      
      // Verify source details
      const sourceType = await locationSourcesPage.getSourceType(0);
      expect(sourceType).toBe('OwnTracks');
      
      const sourceIdentifier = await locationSourcesPage.getSourceIdentifier(0);
      expect(sourceIdentifier).toContain('testuser');
      
      // Verify source exists in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSourceCount = await LocationSourcesPage.getGpsSourceCountFromDb(dbManager, user.id);
      expect(dbSourceCount).toBe(1);
      
      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'OWNTRACKS', 'testuser');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('OWNTRACKS');
      expect(dbSource.username).toBe('testuser');
      expect(dbSource.connection_type).toBe('HTTP');
      expect(dbSource.active).toBe(true);
    });

    test('should create OwnTracks MQTT source', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Create OwnTracks MQTT source using helper method
      await locationSourcesPage.createOwnTracksMqttSource('mqttuser', 'mqttpassword');
      
      expect(await locationSourcesPage.getSourceCount()).toBe(1);
      expect(await locationSourcesPage.areInstructionsVisible()).toBe(true);
      
      const sourceType = await locationSourcesPage.getSourceType(0);
      expect(sourceType).toBe('OwnTracks');
      
      const sourceIdentifier = await locationSourcesPage.getSourceIdentifier(0);
      expect(sourceIdentifier).toContain('mqttuser');
      
      // Verify source exists in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSourceCount = await LocationSourcesPage.getGpsSourceCountFromDb(dbManager, user.id);
      expect(dbSourceCount).toBe(1);
      
      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'OWNTRACKS', 'mqttuser');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('OWNTRACKS');
      expect(dbSource.username).toBe('mqttuser');
      expect(dbSource.connection_type).toBe('MQTT');
      expect(dbSource.active).toBe(true);
    });

    test('should create Overland source', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Create Overland source using helper method
      await locationSourcesPage.createOverlandSource('overland-token-12345');
      
      expect(await locationSourcesPage.getSourceCount()).toBe(1);
      expect(await locationSourcesPage.areInstructionsVisible()).toBe(true);
      
      const sourceType = await locationSourcesPage.getSourceType(0);
      expect(sourceType).toBe('Overland');
      
      const sourceIdentifier = await locationSourcesPage.getSourceIdentifier(0);
      expect(sourceIdentifier).toContain('Token:');
      
      // Verify source exists in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSourceCount = await LocationSourcesPage.getGpsSourceCountFromDb(dbManager, user.id);
      expect(dbSourceCount).toBe(1);
      
      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'OVERLAND', 'overland-token-12345');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('OVERLAND');
      expect(dbSource.token).toBe('overland-token-12345');
      expect(dbSource.active).toBe(true);
    });

    test('should create Dawarich source', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Create Dawarich source using helper method
      await locationSourcesPage.createDawarichSource('dawarich-api-key-67890');
      
      expect(await locationSourcesPage.getSourceCount()).toBe(1);
      expect(await locationSourcesPage.areInstructionsVisible()).toBe(true);
      
      const sourceType = await locationSourcesPage.getSourceType(0);
      expect(sourceType).toBe('Dawarich');
      
      const sourceIdentifier = await locationSourcesPage.getSourceIdentifier(0);
      expect(sourceIdentifier).toContain('API Key:');
      
      // Verify source exists in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSourceCount = await LocationSourcesPage.getGpsSourceCountFromDb(dbManager, user.id);
      expect(dbSourceCount).toBe(1);
      
      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'DAWARICH', 'dawarich-api-key-67890');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('DAWARICH');
      expect(dbSource.token).toBe('dawarich-api-key-67890');
      expect(dbSource.active).toBe(true);
    });

    test('should create Home Assistant source', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();

      // Create Overland source using helper method
      await locationSourcesPage.createHomeAssistantSource('home-assistant-token-12345');

      expect(await locationSourcesPage.getSourceCount()).toBe(1);
      expect(await locationSourcesPage.areInstructionsVisible()).toBe(true);

      const sourceType = await locationSourcesPage.getSourceType(0);
      expect(sourceType).toBe('Home Assistant');

      const sourceIdentifier = await locationSourcesPage.getSourceIdentifier(0);
      expect(sourceIdentifier).toContain('Token:');

      // Verify source exists in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSourceCount = await LocationSourcesPage.getGpsSourceCountFromDb(dbManager, user.id);
      expect(dbSourceCount).toBe(1);

      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'HOME_ASSISTANT', 'home-assistant-token-12345');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('HOME_ASSISTANT');
      expect(dbSource.token).toBe('home-assistant-token-12345');
      expect(dbSource.active).toBe(true);
      expect(dbSource.username).toBeNull();
      expect(dbSource.password_hash).toBe("")
    });

    test('should use quick setup buttons to create sources', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Verify quick setup guide is visible when no sources
      expect(await locationSourcesPage.hasNoSources()).toBe(true);
      
      // Use quick setup for OwnTracks
      await locationSourcesPage.clickQuickSetup('OWNTRACKS');
      await locationSourcesPage.waitForDialog();
      
      // Verify OwnTracks is pre-selected
      expect(await locationSourcesPage.isDialogVisible()).toBe(true);
      
      // Complete the form
      await locationSourcesPage.fillOwnTracksForm('quickuser', 'quickpass', 'HTTP');
      await locationSourcesPage.clickSave();
      await locationSourcesPage.waitForSuccessToast();
      await locationSourcesPage.waitForDialogClose();
      
      expect(await locationSourcesPage.getSourceCount()).toBe(1);
      
      // Verify source exists in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSourceCount = await LocationSourcesPage.getGpsSourceCountFromDb(dbManager, user.id);
      expect(dbSourceCount).toBe(1);
      
      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'OWNTRACKS', 'quickuser');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('OWNTRACKS');
      expect(dbSource.username).toBe('quickuser');
      expect(dbSource.connection_type).toBe('HTTP');
      expect(dbSource.active).toBe(true);
    });
  });

  test.describe('Source Editing', () => {
    test('should edit an existing OwnTracks source', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // First create a source
      await locationSourcesPage.createOwnTracksHttpSource('originaluser', 'originalpass');
      
      // Edit the source
      await locationSourcesPage.clickEditSource(0);
      await locationSourcesPage.waitForDialog();
      
      // Verify edit dialog is open with existing data
      expect(await locationSourcesPage.isDialogVisible()).toBe(true);
      
      // Update the username
      await page.fill('#username', 'updateduser');
      await locationSourcesPage.clickSaveEdit();
      
      await locationSourcesPage.waitForSuccessToast();
      await locationSourcesPage.waitForDialogClose();
      
      // Verify the update in UI
      const sourceIdentifier = await locationSourcesPage.getSourceIdentifier(0);
      expect(sourceIdentifier).toContain('updateduser');
      
      // Verify the update in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'OWNTRACKS', 'updateduser');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('OWNTRACKS');
      expect(dbSource.username).toBe('updateduser');
      expect(dbSource.connection_type).toBe('HTTP');
      expect(dbSource.active).toBe(true);
    });

    test('should edit an existing Overland source', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // First create a source
      await locationSourcesPage.createOverlandSource('original-token');
      
      // Edit the source
      await locationSourcesPage.clickEditSource(0);
      await locationSourcesPage.waitForDialog();
      
      // Update the token
      await page.fill('#token', 'updated-token-12345');
      await locationSourcesPage.clickSaveEdit();
      
      await locationSourcesPage.waitForSuccessToast();
      await locationSourcesPage.waitForDialogClose();
      
      // Verify the update in UI
      const sourceIdentifier = await locationSourcesPage.getSourceIdentifier(0);
      expect(sourceIdentifier).toContain('Token:');
      
      // Verify the update in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'OVERLAND', 'updated-token-12345');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('OVERLAND');
      expect(dbSource.token).toBe('updated-token-12345');
      expect(dbSource.active).toBe(true);
    });

    test('should cancel source editing', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // First create a source
      await locationSourcesPage.createOwnTracksHttpSource('originaluser', 'originalpass');
      
      // Start editing but cancel
      await locationSourcesPage.clickEditSource(0);
      await locationSourcesPage.waitForDialog();
      
      // Change something
      await page.fill('#username', 'shouldnotchange');
      
      // Cancel
      await locationSourcesPage.clickCancel();
      await locationSourcesPage.waitForDialogClose();
      
      // Verify no changes were made in UI
      const sourceIdentifier = await locationSourcesPage.getSourceIdentifier(0);
      expect(sourceIdentifier).toContain('originaluser');
      
      // Verify no changes were made in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'OWNTRACKS', 'originaluser');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('OWNTRACKS');
      expect(dbSource.username).toBe('originaluser');
      expect(dbSource.connection_type).toBe('HTTP');
      expect(dbSource.active).toBe(true);
    });
  });

  test.describe('Source Deletion', () => {
    test('should delete a location source', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // First create a source
      await locationSourcesPage.createOwnTracksHttpSource('deleteuser', 'deletepass');
      expect(await locationSourcesPage.getSourceCount()).toBe(1);
      
      // Delete the source
      await locationSourcesPage.clickDeleteSource(0);
      await locationSourcesPage.confirmDelete();
      
      await locationSourcesPage.waitForSuccessToast();
      
      // Wait for UI to update after deletion
      await page.waitForTimeout(1000);
      
      // Verify source is deleted from UI
      expect(await locationSourcesPage.getSourceCount()).toBe(0);
      expect(await locationSourcesPage.hasNoSources()).toBe(true);
      
      // Verify source is deleted from database with retry logic
      const user = await dbManager.getUserByEmail(testUser.email);
      
      // Wait for source count to become 0 in database
      const dbSourceCount = await LocationSourcesPage.waitForSourceCountInDb(dbManager, user.id, 0);
      expect(dbSourceCount).toBe(0);
      
      // Wait for specific source to be deleted from database
      const dbSource = await LocationSourcesPage.waitForSourceDeletionInDb(dbManager, user.id, 'OWNTRACKS', 'deleteuser');
      expect(dbSource).toBeNull();
    });

    test('should cancel source deletion', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // First create a source
      await locationSourcesPage.createOwnTracksHttpSource('keepuser', 'keeppass');
      expect(await locationSourcesPage.getSourceCount()).toBe(1);
      
      // Start delete but cancel
      await locationSourcesPage.clickDeleteSource(0);
      await locationSourcesPage.cancelDelete();
      
      // Verify source is still there in UI
      expect(await locationSourcesPage.getSourceCount()).toBe(1);
      const sourceIdentifier = await locationSourcesPage.getSourceIdentifier(0);
      expect(sourceIdentifier).toContain('keepuser');
      
      // Verify source is still in database
      const user = await dbManager.getUserByEmail(testUser.email);
      const dbSourceCount = await LocationSourcesPage.getGpsSourceCountFromDb(dbManager, user.id);
      expect(dbSourceCount).toBe(1);
      
      const dbSource = await LocationSourcesPage.getGpsSourceFromDb(dbManager, user.id, 'OWNTRACKS', 'keepuser');
      expect(dbSource).toBeTruthy();
      expect(dbSource.source_type).toBe('OWNTRACKS');
      expect(dbSource.username).toBe('keepuser');
      expect(dbSource.active).toBe(true);
    });
  });

  test.describe('Instructions and Setup', () => {
    test('should show setup instructions after creating a source', async ({page}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Create source
      await locationSourcesPage.createOwnTracksHttpSource('instructuser', 'instructpass');
      
      // Verify instructions are visible
      expect(await locationSourcesPage.areInstructionsVisible()).toBe(true);
      
      // Click instructions button
      await locationSourcesPage.clickInstructions(0);
      
      // Verify instructions card is visible
      await locationSourcesPage.waitForInstructions();
      expect(await locationSourcesPage.areInstructionsVisible()).toBe(true);
    });

    test('should handle multiple source types with different instructions', async ({page}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Create multiple different types of sources
      await locationSourcesPage.createOwnTracksHttpSource('httpuser', 'httppass');
      await locationSourcesPage.createOverlandSource('overland-multi-token');
      
      expect(await locationSourcesPage.getSourceCount()).toBe(2);
      expect(await locationSourcesPage.areInstructionsVisible()).toBe(true);
      
      // Verify both sources are present
      const firstSourceType = await locationSourcesPage.getSourceType(0);
      const secondSourceType = await locationSourcesPage.getSourceType(1);
      
      // Sources might be in any order
      const sourceTypes = [firstSourceType, secondSourceType];
      expect(sourceTypes).toContain('OwnTracks');
      expect(sourceTypes).toContain('Overland');
    });
  });

  test.describe('Form Validation', () => {
    test('should validate required fields for OwnTracks', async ({page}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Try to create source without required fields
      await locationSourcesPage.clickAddNewSource();
      await locationSourcesPage.waitForDialog();
      await locationSourcesPage.selectSourceType('OWNTRACKS');
      
      // Try to save without filling required fields
      await locationSourcesPage.clickSave();
      
      // Check for validation errors
      const usernameError = await page.locator('.error-message').filter({hasText: 'Username is required'});
      const passwordError = await page.locator('.error-message').filter({hasText: 'Password is required'});
      
      expect(await usernameError.isVisible()).toBe(true);
      expect(await passwordError.isVisible()).toBe(true);
    });

    test('should validate required fields for Overland', async ({page}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Try to create source without required fields
      await locationSourcesPage.clickAddNewSource();
      await locationSourcesPage.waitForDialog();
      await locationSourcesPage.selectSourceType('OVERLAND');
      
      // Try to save without token
      await locationSourcesPage.clickSave();
      
      // Check for validation error
      const tokenError = await page.locator('.error-message').filter({hasText: 'Access token is required'});
      expect(await tokenError.isVisible()).toBe(true);
    });

    test('should validate required fields for Dawarich', async ({page}) => {
      const loginPage = new LoginPage(page);
      const locationSourcesPage = new LocationSourcesPage(page);
      const testUser = TestData.users.existing;
      
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await locationSourcesPage.navigate();
      await locationSourcesPage.waitForPageLoad();
      
      // Try to create source without required fields
      await locationSourcesPage.clickAddNewSource();
      await locationSourcesPage.waitForDialog();
      await locationSourcesPage.selectSourceType('DAWARICH');
      
      // Try to save without API key
      await locationSourcesPage.clickSave();
      
      // Check for validation error
      const apiKeyError = await page.locator('.error-message').filter({hasText: 'API key is required'});
      expect(await apiKeyError.isVisible()).toBe(true);
    });
  });
});