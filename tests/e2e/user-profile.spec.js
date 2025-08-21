import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {UserProfilePage} from '../pages/UserProfilePage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';

test.describe('User Profile Management', () => {
  
  test.describe('Profile Information Tab', () => {
    test('should display user profile information correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      // Login to the app
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Navigate to profile page
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Verify we're on the profile page
      expect(await profilePage.isOnProfilePage()).toBe(true);
      
      // Profile tab should be active by default
      expect(await profilePage.isProfileTabActive()).toBe(true);
      
      // Verify user information is displayed
      expect(await profilePage.getFullNameValue()).toBe(testUser.fullName);
      expect(await profilePage.getEmailValue()).toBe(testUser.email);
      expect(await profilePage.isEmailFieldDisabled()).toBe(true);
      
      // Verify avatar section is visible
      const avatarIndex = await profilePage.getSelectedAvatarIndex();
      expect(avatarIndex).toBeGreaterThanOrEqual(0);
    });

    test('should allow updating profile information', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const newFullName = 'Updated Test User';

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Update full name
      await profilePage.fillProfileForm(newFullName);
      
      // Select a different avatar
      await profilePage.selectAvatar(2);
      
      // Verify save button is enabled when changes are made
      expect(await profilePage.isSaveButtonEnabled()).toBe(true);
      
      // Save changes
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();
      
      // Verify changes are saved
      expect(await profilePage.getFullNameValue()).toBe(newFullName);
      expect(await profilePage.getSelectedAvatarIndex()).toBe(2);
      
      // Verify success message
      const toastMessage = await profilePage.getToastMessage();
      expect(toastMessage).toContain('updated successfully');
    });

    test('should validate profile form inputs', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Try to save with empty full name
      await profilePage.fillProfileForm('');
      await profilePage.saveProfile();
      
      // Should show validation error
      const errorMessage = await profilePage.getProfileErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('required');
      
      // Try with too short name
      await profilePage.fillProfileForm('A');
      await profilePage.saveProfile();
      
      const shortNameError = await profilePage.getProfileErrorMessage();
      expect(shortNameError).toBeTruthy();
      expect(shortNameError).toContain('at least 2 characters');
    });

    test('should allow resetting profile form', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      const originalName = await profilePage.getFullNameValue();
      const originalAvatarIndex = await profilePage.getSelectedAvatarIndex();
      
      // Make some changes
      await profilePage.fillProfileForm('Changed Name');
      await profilePage.selectAvatar((originalAvatarIndex + 1) % 5);
      
      // Reset form
      await profilePage.resetProfile();
      await profilePage.waitForLoading();
      
      // Verify original values are restored
      expect(await profilePage.getFullNameValue()).toBe(originalName);
      expect(await profilePage.getSelectedAvatarIndex()).toBe(originalAvatarIndex);
    });
  });

  test.describe('Security Tab', () => {
    test('should switch to security tab correctly', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Switch to security tab
      await profilePage.switchToSecurityTab();
      
      // Verify security tab is active
      expect(await profilePage.isSecurityTabActive()).toBe(true);
      expect(await profilePage.isProfileTabActive()).toBe(false);
      
      // Verify password form is empty initially
      expect(await profilePage.isPasswordFormEmpty()).toBe(true);
      
      // Verify change password button is initially disabled
      expect(await profilePage.isChangePasswordButtonEnabled()).toBe(false);
    });

    test('should validate password change form', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      await profilePage.switchToSecurityTab();
      
      // Verify button is disabled with empty form
      expect(await profilePage.isChangePasswordButtonEnabled()).toBe(false);
      
      // Fill current password only - button should be enabled but validation should fail
      await profilePage.fillPasswordForm(testUser.password, '', '');
      expect(await profilePage.isChangePasswordButtonEnabled()).toBe(true);
      await profilePage.changePassword();
      
      let errorMessage = await profilePage.getPasswordErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('New password is required');
      
      // Fill with short password
      await profilePage.fillPasswordForm(testUser.password, '123', '123');
      await profilePage.changePassword();
      
      errorMessage = await profilePage.getPasswordErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('at least 6 characters');
      
      // Fill with mismatched passwords
      await profilePage.fillPasswordForm(testUser.password, 'newpassword123', 'differentpassword');
      await profilePage.changePassword();
      
      errorMessage = await profilePage.getPasswordErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('do not match');
    });

    test('should successfully change password with valid inputs', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const newPassword = 'NewPassword123!';

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      await profilePage.switchToSecurityTab();
      
      // Fill password form with valid data
      await profilePage.fillPasswordForm(testUser.password, newPassword, newPassword);
      
      // Verify button is enabled with valid input
      expect(await profilePage.isChangePasswordButtonEnabled()).toBe(true);
      
      // Change password
      await profilePage.changePassword();
      await profilePage.waitForSuccessToast();
      
      // Verify success message
      const toastMessage = await profilePage.getToastMessage();
      expect(toastMessage).toContain('changed successfully');
      
      // Verify form is reset after successful change
      expect(await profilePage.isPasswordFormEmpty()).toBe(true);
    });

    test('should cancel password change', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      await profilePage.switchToSecurityTab();
      
      // Fill password form
      await profilePage.fillPasswordForm('currentpass', 'newpass123', 'newpass123');
      
      // Cancel changes
      await profilePage.cancelPasswordChange();
      
      // Verify form is cleared
      expect(await profilePage.isPasswordFormEmpty()).toBe(true);
    });
  });

  test.describe('Immich Integration Tab', () => {
    test('should switch to immich tab correctly', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Switch to immich tab
      await profilePage.switchToImmichTab();
      
      // Verify immich tab is active
      expect(await profilePage.isImmichTabActive()).toBe(true);
      expect(await profilePage.isProfileTabActive()).toBe(false);
      
      // Verify integration is disabled by default
      expect(await profilePage.isImmichIntegrationEnabled()).toBe(false);
      
      // Verify fields are disabled when integration is off
      expect(await profilePage.areImmichFieldsDisabled()).toBe(true);
    });

    test('should enable immich integration and enable fields', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      await profilePage.switchToImmichTab();
      
      // Enable immich integration
      await profilePage.toggleImmichIntegration();
      
      // Verify integration is enabled
      expect(await profilePage.isImmichIntegrationEnabled()).toBe(true);
      
      // Verify fields are now enabled
      expect(await profilePage.areImmichFieldsDisabled()).toBe(false);
      
      // Verify save button becomes enabled when integration is toggled
      expect(await profilePage.isImmichSaveButtonEnabled()).toBe(true);
    });

    test('should validate immich configuration form', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      await profilePage.switchToImmichTab();
      
      // Enable integration
      await profilePage.toggleImmichIntegration();
      
      // Try to save without required fields
      await profilePage.saveImmichSettings();
      
      let errorMessage = await profilePage.getImmichErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('Server URL is required');
      
      // Fill invalid URL
      await profilePage.fillImmichForm('not-a-url', '');
      await profilePage.saveImmichSettings();
      
      errorMessage = await profilePage.getImmichErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('valid URL');
      
      // Fill valid URL but no API key
      await profilePage.fillImmichForm('https://photos.example.com', '');
      await profilePage.saveImmichSettings();
      
      errorMessage = await profilePage.getImmichErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('API Key is required');
    });

    test('should successfully save immich configuration', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const serverUrl = 'https://photos.example.com';
      const apiKey = 'test-api-key-12345';

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      await profilePage.switchToImmichTab();
      
      // Enable integration and fill valid config
      await profilePage.toggleImmichIntegration();
      await profilePage.fillImmichForm(serverUrl, apiKey);
      
      // Save settings
      await profilePage.saveImmichSettings();
      await profilePage.waitForSuccessToast();
      
      // Verify success message
      const toastMessage = await profilePage.getToastMessage();
      expect(toastMessage).toContain('saved successfully');
      
      // Verify server URL is saved
      expect(await profilePage.getImmichServerUrl()).toBe(serverUrl);
    });

    test('should reset immich form', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      await profilePage.switchToImmichTab();
      
      // Make some changes
      await profilePage.toggleImmichIntegration();
      await profilePage.fillImmichForm('https://test.com', 'test-key');
      
      // Reset form
      await profilePage.resetImmichForm();
      await profilePage.waitForLoading();
      
      // Verify form is reset (integration should be disabled, fields empty)
      expect(await profilePage.isImmichIntegrationEnabled()).toBe(false);
      expect(await profilePage.getImmichServerUrl()).toBe('');
    });

    test('should disable fields when integration is toggled off', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      await profilePage.switchToImmichTab();
      
      // Enable integration first
      await profilePage.toggleImmichIntegration();
      expect(await profilePage.areImmichFieldsDisabled()).toBe(false);
      
      // Disable integration
      await profilePage.toggleImmichIntegration();
      
      // Verify fields are disabled again
      expect(await profilePage.isImmichIntegrationEnabled()).toBe(false);
      expect(await profilePage.areImmichFieldsDisabled()).toBe(true);
    });
  });

  test.describe('Tab Navigation', () => {
    test('should navigate between all tabs correctly', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Should start on profile tab
      expect(await profilePage.isProfileTabActive()).toBe(true);
      
      // Switch to security tab
      await profilePage.switchToSecurityTab();
      expect(await profilePage.isSecurityTabActive()).toBe(true);
      expect(await profilePage.isProfileTabActive()).toBe(false);
      
      // Switch to immich tab
      await profilePage.switchToImmichTab();
      expect(await profilePage.isImmichTabActive()).toBe(true);
      expect(await profilePage.isSecurityTabActive()).toBe(false);
      
      // Switch back to profile tab
      await profilePage.switchToProfileTab();
      expect(await profilePage.isProfileTabActive()).toBe(true);
      expect(await profilePage.isImmichTabActive()).toBe(false);
    });

    test('should preserve form data when switching between tabs', async ({page}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const testName = 'Modified Name';

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Make changes in profile tab
      await profilePage.fillProfileForm(testName);
      
      // Switch to security tab and back
      await profilePage.switchToSecurityTab();
      await profilePage.switchToProfileTab();
      
      // Verify profile data is preserved
      expect(await profilePage.getFullNameValue()).toBe(testName);
      
      // Switch to immich tab
      await profilePage.switchToImmichTab();
      await profilePage.toggleImmichIntegration();
      await profilePage.fillImmichForm('https://test.com', 'test-key');
      
      // Switch to profile and back to immich
      await profilePage.switchToProfileTab();
      await profilePage.switchToImmichTab();
      
      // Verify immich data is preserved
      expect(await profilePage.isImmichIntegrationEnabled()).toBe(true);
      expect(await profilePage.getImmichServerUrl()).toBe('https://test.com');
    });
  });
});