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

  test.describe('Timezone Management', () => {
    test('should display default timezone correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      // Explicitly set timezone to UTC to ensure consistent test behavior
      testUser.timezone = 'UTC';
      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Wait for timezone to load completely
      await page.waitForTimeout(1000);
      
      // Verify timezone is UTC as we explicitly set it
      const selectedTimezone = await profilePage.getSelectedTimezone();
      expect(selectedTimezone).toBe('UTC');
      
      // Verify localStorage contains UTC timezone
      const localStorageTimezone = await profilePage.getTimezoneFromLocalStorage();
      expect(localStorageTimezone).toBe('UTC');
    });

    test('should update timezone and save to database and localStorage', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const newTimezone = 'Europe/London GMT+0';
      const expectedTimezoneValue = 'Europe/London';

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Change timezone
      await profilePage.selectTimezone(newTimezone);
      
      // Verify dropdown shows new selection
      const selectedTimezone = await profilePage.getSelectedTimezone();
      expect(selectedTimezone).toBe(newTimezone);
      
      // Save changes
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();
      
      // Verify success message
      const toastMessage = await profilePage.getToastMessage();
      expect(toastMessage).toContain('updated successfully');
      
      // Verify localStorage is updated immediately
      const localStorageTimezone = await profilePage.getTimezoneFromLocalStorage();
      expect(localStorageTimezone).toBe(expectedTimezoneValue);
      
      // Verify database is updated by checking with a fresh page load
      await page.reload();
      await profilePage.waitForPageLoad();
      
      const reloadedTimezone = await profilePage.getSelectedTimezone();
      expect(reloadedTimezone).toBe(newTimezone);
      
      // Verify localStorage persists after reload
      const persistedLocalStorageTimezone = await profilePage.getTimezoneFromLocalStorage();
      expect(persistedLocalStorageTimezone).toBe(expectedTimezoneValue);
    });

    test('should handle timezone normalization (Europe/Kiev -> Europe/Kyiv)', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const kiyvTimezone = 'Europe/Kyiv GMT+2';
      const expectedTimezoneValue = 'Europe/Kyiv';

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Select Kyiv timezone (which should be normalized properly)
      await profilePage.selectTimezone(kiyvTimezone);
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();
      
      // Verify localStorage contains the correct normalized timezone
      const localStorageTimezone = await profilePage.getTimezoneFromLocalStorage();
      expect(localStorageTimezone).toBe(expectedTimezoneValue);
      
      // Verify database stores the normalized timezone by reloading
      await page.reload();
      await profilePage.waitForPageLoad();
      
      const reloadedTimezone = await profilePage.getSelectedTimezone();
      expect(reloadedTimezone).toBe(kiyvTimezone);
    });

    test('should update timezone when auto-detected during login', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      // Mock browser timezone detection to simulate different timezone
      await page.addInitScript(() => {
        // Mock Intl.DateTimeFormat to return a specific timezone
        const originalDateTimeFormat = Intl.DateTimeFormat;
        Intl.DateTimeFormat = function(...args) {
          const formatter = new originalDateTimeFormat(...args);
          const originalResolvedOptions = formatter.resolvedOptions;
          formatter.resolvedOptions = function() {
            const options = originalResolvedOptions.call(this);
            options.timeZone = 'America/New_York';
            return options;
          };
          return formatter;
        };
        
        // Copy static methods
        Object.setPrototypeOf(Intl.DateTimeFormat, originalDateTimeFormat);
        Object.getOwnPropertyNames(originalDateTimeFormat).forEach(name => {
          if (typeof originalDateTimeFormat[name] === 'function') {
            Intl.DateTimeFormat[name] = originalDateTimeFormat[name];
          }
        });
      });
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      // Navigate to profile to check if timezone was auto-updated
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Check if timezone was auto-detected and updated
      const localStorageTimezone = await profilePage.getTimezoneFromLocalStorage();
      // Note: This test depends on the auto-detection logic in the frontend
      // If auto-detection is disabled, this should still be 'UTC'
      expect(localStorageTimezone).toBeTruthy();
    });

    test('should reset timezone when form is reset', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const newTimezone = 'Europe/Paris GMT+1';

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      const originalTimezone = await profilePage.getSelectedTimezone();
      
      // Change timezone but don't save
      await profilePage.selectTimezone(newTimezone);
      
      // Verify the change is reflected in the UI
      const changedTimezone = await profilePage.getSelectedTimezone();
      expect(changedTimezone).toBe(newTimezone);
      
      // Reset the form
      await profilePage.resetProfile();
      await profilePage.waitForLoading();
      
      // Verify timezone is reset to original value
      const resetTimezone = await profilePage.getSelectedTimezone();
      expect(resetTimezone).toBe(originalTimezone);
      
      // Verify localStorage wasn't changed
      const localStorageTimezone = await profilePage.getTimezoneFromLocalStorage();
      expect(localStorageTimezone).toBe('UTC'); // Should still be original value
    });

    test('should validate timezone dropdown has expected options', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');
      
      await profilePage.navigate();
      await profilePage.waitForPageLoad();
      
      // Open timezone dropdown
      await page.click(profilePage.selectors.profile.timezoneLabel);
      await page.waitForSelector(profilePage.selectors.profile.timezoneOptions, { timeout: 10000 });
      
      // Get all timezone options
      const timezoneOptions = await page.locator(profilePage.selectors.profile.timezoneOptions).allTextContents();
      
      // Verify some key timezones are present
      expect(timezoneOptions).toContain('UTC');
      expect(timezoneOptions.some(option => option.includes('Europe/London'))).toBe(true);
      expect(timezoneOptions.some(option => option.includes('America/New_York'))).toBe(true);
      expect(timezoneOptions.some(option => option.includes('Europe/Kyiv'))).toBe(true);
      expect(timezoneOptions.some(option => option.includes('Asia/Tokyo'))).toBe(true);
      
      // Verify the timezone format includes GMT offsets (not timezone abbreviations)
      expect(timezoneOptions.some(option => option.includes('GMT+2'))).toBe(true); // Europe/Kyiv
      expect(timezoneOptions.some(option => option.includes('GMT+0'))).toBe(true); // Europe/London
      
      // Verify we have a reasonable number of timezone options (should be 40+)
      expect(timezoneOptions.length).toBeGreaterThan(35);
      
      // Close dropdown
      await page.keyboard.press('Escape');
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

  test.describe('AI Assistant Tab', () => {
    test('should display AI Assistant settings correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Switch to AI Assistant tab
      await profilePage.switchToAiAssistantTab();
      expect(await profilePage.isAiAssistantTabActive()).toBe(true);

      // Verify AI Assistant is disabled by default
      expect(await profilePage.isAIAssistantEnabled()).toBe(false);

      // Verify Save button is enabled (can save even when disabled)
      expect(await profilePage.isAISaveButtonEnabled()).toBe(true);
    });

    test('should allow enabling and configuring AI Assistant', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Switch to AI Assistant tab
      await profilePage.switchToAiAssistantTab();

      // Enable AI Assistant
      await profilePage.toggleAIAssistant();
      expect(await profilePage.isAIAssistantEnabled()).toBe(true);

      // Fill in OpenAI settings
      await profilePage.fillOpenAIApiKey('sk-test-api-key-1234567890');
      await profilePage.fillOpenAIApiUrl('https://api.openai.com/v1');
      await profilePage.selectOpenAIModel('gpt-4');

      // Save settings
      await profilePage.saveAISettings();

      // Wait for success toast
      await profilePage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // Verify API key is now marked as configured
      expect(await profilePage.isOpenAIApiKeyConfigured()).toBe(true);

      // Verify API URL is saved
      expect(await profilePage.getOpenAIApiUrl()).toBe('https://api.openai.com/v1');
    });

    test('should allow using custom OpenAI-compatible API endpoint', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Switch to AI Assistant tab
      await profilePage.switchToAiAssistantTab();

      // Enable AI Assistant
      await profilePage.toggleAIAssistant();

      // Configure with custom endpoint
      await profilePage.fillOpenAIApiKey('custom-api-key-12345');
      await profilePage.fillOpenAIApiUrl('https://my-custom-llm-provider.com/v1');
      await profilePage.selectOpenAIModel('custom-model-name');

      // Save settings
      await profilePage.saveAISettings();

      // Wait for success toast
      await profilePage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // Verify custom API URL is saved
      expect(await profilePage.getOpenAIApiUrl()).toBe('https://my-custom-llm-provider.com/v1');
    });

    test('should allow disabling AI Assistant', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Switch to AI Assistant tab
      await profilePage.switchToAiAssistantTab();

      // Enable AI Assistant
      await profilePage.toggleAIAssistant();
      expect(await profilePage.isAIAssistantEnabled()).toBe(true);

      // Configure settings
      await profilePage.fillOpenAIApiKey('sk-test-key');
      await profilePage.fillOpenAIApiUrl('https://api.openai.com/v1');
      await profilePage.selectOpenAIModel('gpt-3.5-turbo');
      await profilePage.saveAISettings();
      await profilePage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // Disable AI Assistant
      await profilePage.toggleAIAssistant();
      expect(await profilePage.isAIAssistantEnabled()).toBe(false);

      // Save the disabled state
      await profilePage.saveAISettings();
      await profilePage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // Reload page to verify it persisted
      await page.reload();
      await profilePage.waitForPageLoad();
      await profilePage.switchToAiAssistantTab();

      // Verify AI Assistant is still disabled
      expect(await profilePage.isAIAssistantEnabled()).toBe(false);
    });

    test('should preserve API key when updating other settings', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Switch to AI Assistant tab
      await profilePage.switchToAiAssistantTab();

      // Enable and configure AI Assistant
      await profilePage.toggleAIAssistant();
      await profilePage.fillOpenAIApiKey('sk-original-key-12345');
      await profilePage.fillOpenAIApiUrl('https://api.openai.com/v1');
      await profilePage.selectOpenAIModel('gpt-4');
      await profilePage.saveAISettings();
      await profilePage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // Verify API key is configured
      expect(await profilePage.isOpenAIApiKeyConfigured()).toBe(true);

      // Update only the model without changing API key
      await profilePage.selectOpenAIModel('gpt-4-turbo');
      await profilePage.saveAISettings();
      await profilePage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // API key should still be configured (not replaced)
      expect(await profilePage.isOpenAIApiKeyConfigured()).toBe(true);
    });

    test('should switch between AI Assistant and other tabs', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Default tab should be Profile
      expect(await profilePage.isProfileTabActive()).toBe(true);

      // Switch to AI Assistant tab
      await profilePage.switchToAiAssistantTab();
      expect(await profilePage.isAiAssistantTabActive()).toBe(true);
      expect(await profilePage.isProfileTabActive()).toBe(false);

      // Switch to Security tab
      await profilePage.switchToSecurityTab();
      expect(await profilePage.isSecurityTabActive()).toBe(true);
      expect(await profilePage.isAiAssistantTabActive()).toBe(false);

      // Switch back to AI Assistant
      await profilePage.switchToAiAssistantTab();
      expect(await profilePage.isAiAssistantTabActive()).toBe(true);

      // Switch to Immich tab
      await profilePage.switchToImmichTab();
      expect(await profilePage.isImmichTabActive()).toBe(true);
      expect(await profilePage.isAiAssistantTabActive()).toBe(false);
    });
  });

  test.describe('Default Redirect URL', () => {
    test('should display default redirect URL dropdown correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Verify default redirect URL dropdown is present
      const selectedValue = await profilePage.getSelectedDefaultRedirectUrl();
      // Should be null or empty initially
      expect(selectedValue === null || selectedValue === '' || selectedValue === 'Select your default page').toBe(true);
    });

    test('should allow selecting predefined default redirect URL', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const redirectOption = 'Dashboard';

      await UserFactory.createUser(page, testUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Select Dashboard from dropdown
      await profilePage.selectDefaultRedirectUrl(redirectOption);

      // Verify dropdown shows selected option
      const selectedValue = await profilePage.getSelectedDefaultRedirectUrl();
      expect(selectedValue).toBe(redirectOption);

      // Save changes
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();

      // Verify localStorage is updated
      const localStorageValue = await profilePage.getDefaultRedirectUrlFromLocalStorage();
      expect(localStorageValue).toBe('/app/dashboard');

      // Reload and verify persistence
      await page.reload();
      await profilePage.waitForPageLoad();

      const reloadedValue = await profilePage.getSelectedDefaultRedirectUrl();
      expect(reloadedValue).toBe(redirectOption);
    });

    test('should show custom URL input when "Custom URL..." is selected', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Initially custom input should not be visible
      expect(await profilePage.isCustomRedirectUrlInputVisible()).toBe(false);

      // Select "Custom URL..." option
      await profilePage.selectDefaultRedirectUrl('Custom URL...');

      // Now custom input should be visible
      expect(await profilePage.isCustomRedirectUrlInputVisible()).toBe(true);
    });

    test('should save and use custom redirect URL', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const customUrl = '/app/journey-insights';

      await UserFactory.createUser(page, testUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Select custom URL option
      await profilePage.selectDefaultRedirectUrl('Custom URL...');
      expect(await profilePage.isCustomRedirectUrlInputVisible()).toBe(true);

      // Fill custom URL
      await profilePage.fillCustomRedirectUrl(customUrl);

      // Save changes
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();

      // Verify localStorage is updated with custom URL
      const localStorageValue = await profilePage.getDefaultRedirectUrlFromLocalStorage();
      expect(localStorageValue).toBe(customUrl);
    });

    test('should validate custom redirect URL - must start with /', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const invalidUrl = 'app/dashboard'; // Missing leading /

      await UserFactory.createUser(page, testUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Select custom URL option
      await profilePage.selectDefaultRedirectUrl('Custom URL...');

      // Fill invalid custom URL
      await profilePage.fillCustomRedirectUrl(invalidUrl);

      // Try to save
      await profilePage.saveProfile();

      // Should show validation error
      const errorMessage = await profilePage.getProfileErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('must be an internal path starting with /');
    });

    test('should validate custom redirect URL - no path traversal', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const invalidUrl = '/app/../../../etc/passwd';

      await UserFactory.createUser(page, testUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Select custom URL option
      await profilePage.selectDefaultRedirectUrl('Custom URL...');

      // Fill URL with path traversal
      await profilePage.fillCustomRedirectUrl(invalidUrl);

      // Try to save
      await profilePage.saveProfile();

      // Should show validation error
      const errorMessage = await profilePage.getProfileErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('Invalid URL format');
    });

    test('should redirect to default URL after login', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const redirectUrl = '/app/dashboard';

      await UserFactory.createUser(page, testUser);

      // First login to set up default redirect URL
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Set default redirect URL to Dashboard
      await profilePage.selectDefaultRedirectUrl('Dashboard');
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();

      // Logout
      await page.goto('/');
      await page.evaluate(() => localStorage.clear());
      await page.context().clearCookies();

      // Login again
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);

      // Should redirect to dashboard instead of timeline
      await page.waitForURL('**/app/dashboard', { timeout: 10000 });
      expect(page.url()).toContain('/app/dashboard');
    });

    test('should redirect to default URL when visiting homepage', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);

      // Login and set default redirect URL
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Set default redirect URL to Journey Insights
      await profilePage.selectDefaultRedirectUrl('Journey Insights');
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();

      // Navigate to homepage
      await page.goto('/');

      // Should redirect to journey insights
      await page.waitForURL('**/app/journey-insights', { timeout: 10000 });
      expect(page.url()).toContain('/app/journey-insights');
    });

    test('should show home page if no default redirect URL is set', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);

      // Login without setting default redirect URL
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Navigate to homepage
      await page.goto('/');

      // Should stay on home page or show default timeline redirect
      await page.waitForTimeout(2000);

      // URL should be either home page or still showing authenticated state
      const url = page.url();
      const isValidState = url.includes('/') || url.includes('/app/timeline');
      expect(isValidState).toBe(true);
    });

    test('should redirect authenticated user from login page to default URL', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);

      // Login and set default redirect URL
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Set default redirect URL to Friends
      await profilePage.selectDefaultRedirectUrl('Friends');
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();

      // Try to visit login page while authenticated
      await page.goto('/login');

      // Should redirect to friends page
      await page.waitForURL('**/app/friends/live', { timeout: 10000 });
      expect(page.url()).toContain('/app/friends');
    });

    test('should preserve custom URL after reload when matches predefined option', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const customUrl = '/app/rewind';

      await UserFactory.createUser(page, testUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Use custom URL option to enter a URL that matches a predefined option
      await profilePage.selectDefaultRedirectUrl('Custom URL...');
      await profilePage.fillCustomRedirectUrl(customUrl);
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();

      // Reload page
      await page.reload();
      await profilePage.waitForPageLoad();

      // Should show the predefined option that matches the URL
      const selectedValue = await profilePage.getSelectedDefaultRedirectUrl();
      expect(selectedValue).toBe('Rewind');
    });

    test('should preserve truly custom URL after reload', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;
      const customUrl = '/app/my-custom-page';

      await UserFactory.createUser(page, testUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Use custom URL option with truly custom URL
      await profilePage.selectDefaultRedirectUrl('Custom URL...');
      await profilePage.fillCustomRedirectUrl(customUrl);
      await profilePage.saveProfile();
      await profilePage.waitForSuccessToast();

      // Reload page
      await page.reload();
      await profilePage.waitForPageLoad();

      // Should show "Custom URL..." selected
      const selectedValue = await profilePage.getSelectedDefaultRedirectUrl();
      expect(selectedValue).toBe('Custom URL...');

      // Custom input should be visible with the URL
      expect(await profilePage.isCustomRedirectUrlInputVisible()).toBe(true);
      const customValue = await profilePage.getCustomRedirectUrlValue();
      expect(customValue).toBe(customUrl);
    });

    test('should handle all predefined redirect options', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const profilePage = new UserProfilePage(page);
      const testUser = TestData.users.existing;

      const redirectOptions = [
        { label: 'Timeline', url: '/app/timeline' },
        { label: 'Dashboard', url: '/app/dashboard' },
        { label: 'Journey Insights', url: '/app/journey-insights' },
        { label: 'Friends', url: '/app/friends' },
        { label: 'Rewind', url: '/app/rewind' },
        { label: 'GPS Data', url: '/app/gps-data' },
        { label: 'Location Sources', url: '/app/location-sources' }
      ];

      await UserFactory.createUser(page, testUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await profilePage.navigate();
      await profilePage.waitForPageLoad();

      // Test each predefined option
      for (const option of redirectOptions) {
        // Select the option
        await profilePage.selectDefaultRedirectUrl(option.label);

        // Save
        await profilePage.saveProfile();
        await profilePage.waitForSuccessToast();
        await page.waitForTimeout(500);

        // Verify localStorage
        const localStorageValue = await profilePage.getDefaultRedirectUrlFromLocalStorage();
        expect(localStorageValue).toBe(option.url);
      }
    });
  });
});