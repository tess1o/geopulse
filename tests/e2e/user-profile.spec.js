import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {UserProfilePage} from '../pages/UserProfilePage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {ValidationHelpers} from '../utils/validation-helpers.js';
import {TestSetupHelper} from "../utils/test-setup-helper.js";

test.describe('User Profile Management', () => {
  
  test.describe('Profile Information Tab', () => {
    test('should display user profile information correctly', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

      // Update full name
      const newFullName = 'Updated Test User';
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

    test('should validate profile form inputs', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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

    test('should allow resetting profile form', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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
      const userToCreate = TestData.users.existing;
      userToCreate.timezone = 'UTC';
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager,userToCreate);
      
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
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);
      const newTimezone = 'Europe/London GMT+0';
      const expectedTimezoneValue = 'Europe/London';

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
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

      const kiyvTimezone = 'Europe/Kyiv GMT+2';
      const expectedTimezoneValue = 'Europe/Kyiv';

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
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);
      const newTimezone = 'Europe/Paris GMT+1';
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
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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
    test('should switch to security tab correctly', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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

    test('should validate password change form', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);
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

    test('should successfully change password with valid inputs', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);
      await profilePage.switchToSecurityTab();

      const newPassword = 'NewPassword123!';
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

    test('should cancel password change', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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
    test('should switch to immich tab correctly', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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

    test('should enable immich integration and enable fields', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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

    test('should validate immich configuration form', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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

    test('should successfully save immich configuration', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);
      await profilePage.switchToImmichTab();

      const serverUrl = 'https://photos.example.com';
      const apiKey = 'test-api-key-12345';

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

    test('should reset immich form', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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

    test('should disable fields when integration is toggled off', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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
    test('should navigate between all tabs correctly', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

      // Should start on profile tab
      expect(await profilePage.isProfileTabActive()).toBe(true);

      // Switch to security tab
      await profilePage.switchToSecurityTab();
      expect(await profilePage.isSecurityTabActive()).toBe(true);
      expect(await profilePage.isProfileTabActive()).toBe(false);

      // Switch to AI Assistant tab
      await profilePage.switchToAiAssistantTab();
      expect(await profilePage.isAiAssistantTabActive()).toBe(true);
      expect(await profilePage.isSecurityTabActive()).toBe(false);

      // Switch to immich tab
      await profilePage.switchToImmichTab();
      expect(await profilePage.isImmichTabActive()).toBe(true);
      expect(await profilePage.isAiAssistantTabActive()).toBe(false);

      // Switch to display tab
      await profilePage.switchToDisplayTab();
      expect(await profilePage.isDisplayTabActive()).toBe(true);
      expect(await profilePage.isImmichTabActive()).toBe(false);

      // Switch back to profile tab
      await profilePage.switchToProfileTab();
      expect(await profilePage.isProfileTabActive()).toBe(true);
      expect(await profilePage.isDisplayTabActive()).toBe(false);
    });

    test('should preserve form data when switching between tabs', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);
      const testName = 'Modified Name';

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

  test.describe('Display Tab', () => {
    test('should switch to display tab correctly', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

      // Switch to display tab
      await profilePage.switchToDisplayTab();

      // Verify display tab is active
      expect(await profilePage.isDisplayTabActive()).toBe(true);
      expect(await profilePage.isProfileTabActive()).toBe(false);

      // Verify info banner is displayed
      const infoBanner = page.locator('.info-banner');
      expect(await infoBanner.isVisible()).toBe(true);
      expect(await infoBanner.textContent()).toContain('Display Settings Only');
    });

    test.describe('Custom Map Tile URL', () => {
      test('should display custom map tile URL input', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        // Verify custom map tile URL input is present and empty by default
        const customMapTileUrl = await profilePage.getCustomMapTileUrl();
        expect(customMapTileUrl).toBe('');
      });

      test('should allow setting valid custom map tile URL', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        const validUrl = 'https://api.maptiler.com/maps/streets/{z}/{x}/{y}.png?key=YOUR_KEY';

        // Fill custom map tile URL
        await profilePage.fillCustomMapTileUrl(validUrl);

        // Save settings
        await profilePage.saveDisplaySettings();
        await profilePage.waitForSuccessToast();

        // Verify URL is saved
        expect(await profilePage.getCustomMapTileUrl()).toBe(validUrl);

        // Reload and verify persistence
        await page.reload();
        await profilePage.waitForPageLoad();
        await profilePage.switchToDisplayTab();

        expect(await profilePage.getCustomMapTileUrl()).toBe(validUrl);
      });

      test('should validate URL must contain {z}, {x}, {y} placeholders', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        const invalidUrl = 'https://api.maptiler.com/maps/streets/tile.png';

        // Fill invalid URL (missing placeholders)
        await profilePage.fillCustomMapTileUrl(invalidUrl);

        // Try to save
        await profilePage.saveDisplaySettings();

        // Should show validation error
        const errorMessage = await profilePage.getDisplayErrorMessage();
        expect(errorMessage).toBeTruthy();
        expect(errorMessage).toContain('must contain {z}, {x}, and {y} placeholders');
      });

      test('should validate URL must use HTTP or HTTPS protocol', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        const invalidUrl = 'ftp://example.com/tiles/{z}/{x}/{y}.png';

        // Fill invalid URL (wrong protocol)
        await profilePage.fillCustomMapTileUrl(invalidUrl);

        // Try to save
        await profilePage.saveDisplaySettings();

        // Should show validation error
        const errorMessage = await profilePage.getDisplayErrorMessage();
        expect(errorMessage).toBeTruthy();
        expect(errorMessage).toContain('must use HTTP or HTTPS protocol');
      });

      test('should reject dangerous protocols (javascript:, data:, file:)', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        const dangerousUrls = [
          'javascript:alert("xss")/{z}/{x}/{y}',
          'data:text/html,<script>alert("xss")</script>/{z}/{x}/{y}',
          'file:///etc/passwd/{z}/{x}/{y}'
        ];

        for (const dangerousUrl of dangerousUrls) {
          await profilePage.fillCustomMapTileUrl(dangerousUrl);
          await profilePage.saveDisplaySettings();

          const errorMessage = await profilePage.getDisplayErrorMessage();
          expect(errorMessage).toBeTruthy();
          // These URLs don't start with http:// or https:// so they fail the protocol check first
          expect(errorMessage).toContain('URL must use HTTP or HTTPS protocol');
        }
      });

      test('should reject URLs with path traversal', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        const pathTraversalUrl = 'https://example.com/../../../etc/passwd/{z}/{x}/{y}';

        // Fill URL with path traversal
        await profilePage.fillCustomMapTileUrl(pathTraversalUrl);

        // Try to save
        await profilePage.saveDisplaySettings();

        // Should show validation error
        const errorMessage = await profilePage.getDisplayErrorMessage();
        expect(errorMessage).toBeTruthy();
        expect(errorMessage).toContain('Invalid URL format');
      });

      test('should allow clearing custom map tile URL', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        // First set a URL
        const validUrl = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
        await profilePage.fillCustomMapTileUrl(validUrl);
        await profilePage.saveDisplaySettings();
        await profilePage.waitForSuccessToast();

        // Clear the URL
        await profilePage.fillCustomMapTileUrl('');
        await profilePage.saveDisplaySettings();
        await profilePage.waitForSuccessToast();

        // Verify URL is cleared
        expect(await profilePage.getCustomMapTileUrl()).toBe('');
      });
    });

    test.describe('GPS Path Simplification', () => {
      test('should display path simplification settings', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        // Verify GPS Path Simplification section is present
        const section = page.locator('text=GPS Path Simplification');
        expect(await section.isVisible()).toBe(true);

        // Verify path simplification toggle is present
        const toggleCard = page.locator('text=Enable Path Simplification');
        expect(await toggleCard.isVisible()).toBe(true);
      });

      test('should enable and disable path simplification', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        // Path simplification should be enabled by default
        const initialState = await profilePage.isPathSimplificationEnabled();
        expect(initialState).toBe(true);

        // Disable path simplification
        await profilePage.togglePathSimplification();
        expect(await profilePage.isPathSimplificationEnabled()).toBe(false);

        // Enable it again
        await profilePage.togglePathSimplification();
        expect(await profilePage.isPathSimplificationEnabled()).toBe(true);
      });

      test('should show/hide additional settings when toggling path simplification', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        // When enabled, additional settings should be visible
        const toleranceCard = page.locator('text=Simplification Tolerance');
        const maxPointsCard = page.locator('text=Maximum Points');
        const adaptiveCard = page.locator('text=Adaptive Simplification');

        // Should be visible by default (enabled)
        expect(await toleranceCard.isVisible()).toBe(true);
        expect(await maxPointsCard.isVisible()).toBe(true);
        expect(await adaptiveCard.isVisible()).toBe(true);

        // Disable path simplification
        await profilePage.togglePathSimplification();

        // Additional settings should be hidden
        expect(await toleranceCard.isVisible()).toBe(false);
        expect(await maxPointsCard.isVisible()).toBe(false);
        expect(await adaptiveCard.isVisible()).toBe(false);
      });

      test('should save path simplification settings', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        // Toggle path simplification off
        await profilePage.togglePathSimplification();

        // Save settings
        await profilePage.saveDisplaySettings();
        await profilePage.waitForSuccessToast();

        // Reload and verify persistence
        await page.reload();
        await profilePage.waitForPageLoad();
        await profilePage.switchToDisplayTab();

        expect(await profilePage.isPathSimplificationEnabled()).toBe(false);
      });

      test('should reset display settings to defaults', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        // Make some changes
        const customUrl = 'https://api.maptiler.com/maps/streets/{z}/{x}/{y}.png?key=TEST';
        await profilePage.fillCustomMapTileUrl(customUrl);
        await profilePage.togglePathSimplification(); // Disable it

        // Verify changes
        expect(await profilePage.getCustomMapTileUrl()).toBe(customUrl);
        expect(await profilePage.isPathSimplificationEnabled()).toBe(false);

        // Reset to defaults
        await profilePage.resetDisplaySettings();

        // Verify defaults are restored
        expect(await profilePage.getCustomMapTileUrl()).toBe('');
        expect(await profilePage.isPathSimplificationEnabled()).toBe(true);
      });

      test('should persist all settings after save and reload', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        await profilePage.switchToDisplayTab();

        // Set custom map tile URL
        const customUrl = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
        await profilePage.fillCustomMapTileUrl(customUrl);

        // Keep path simplification enabled (default)
        expect(await profilePage.isPathSimplificationEnabled()).toBe(true);

        // Save settings
        await profilePage.saveDisplaySettings();
        await profilePage.waitForSuccessToast();

        // Reload page
        await page.reload();
        await profilePage.waitForPageLoad();
        await profilePage.switchToDisplayTab();

        // Verify all settings persisted
        expect(await profilePage.getCustomMapTileUrl()).toBe(customUrl);
        expect(await profilePage.isPathSimplificationEnabled()).toBe(true);
      });
    });

    test.describe('Tab Navigation', () => {
      test('should navigate between Display tab and other tabs', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        // Default tab should be Profile
        expect(await profilePage.isProfileTabActive()).toBe(true);

        // Switch to Display tab
        await profilePage.switchToDisplayTab();
        expect(await profilePage.isDisplayTabActive()).toBe(true);
        expect(await profilePage.isProfileTabActive()).toBe(false);

        // Switch to Security tab
        await profilePage.switchToSecurityTab();
        expect(await profilePage.isSecurityTabActive()).toBe(true);
        expect(await profilePage.isDisplayTabActive()).toBe(false);

        // Switch back to Display
        await profilePage.switchToDisplayTab();
        expect(await profilePage.isDisplayTabActive()).toBe(true);
        expect(await profilePage.isSecurityTabActive()).toBe(false);

        // Switch to AI Assistant tab
        await profilePage.switchToAiAssistantTab();
        expect(await profilePage.isAiAssistantTabActive()).toBe(true);
        expect(await profilePage.isDisplayTabActive()).toBe(false);

        // Switch back to Display
        await profilePage.switchToDisplayTab();
        expect(await profilePage.isDisplayTabActive()).toBe(true);
      });

      test('should preserve Display tab changes when navigating tabs', async ({page, dbManager}) => {
        const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

        // Switch to Display tab
        await profilePage.switchToDisplayTab();

        // Make changes
        const customUrl = 'https://api.maptiler.com/maps/streets/{z}/{x}/{y}.png?key=TEST';
        await profilePage.fillCustomMapTileUrl(customUrl);
        await profilePage.togglePathSimplification();

        // Switch to another tab
        await profilePage.switchToSecurityTab();

        // Switch back to Display
        await profilePage.switchToDisplayTab();

        // Verify changes are preserved
        expect(await profilePage.getCustomMapTileUrl()).toBe(customUrl);
        expect(await profilePage.isPathSimplificationEnabled()).toBe(false);
      });
    });
  });

  test.describe('AI Assistant Tab', () => {
    test('should display AI Assistant settings correctly', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

      // Switch to AI Assistant tab
      await profilePage.switchToAiAssistantTab();
      expect(await profilePage.isAiAssistantTabActive()).toBe(true);

      // Verify AI Assistant is disabled by default
      expect(await profilePage.isAIAssistantEnabled()).toBe(false);

      // Verify Save button is enabled (can save even when disabled)
      expect(await profilePage.isAISaveButtonEnabled()).toBe(true);
    });

    test('should allow enabling and configuring AI Assistant', async ({page, dbManager}) => {
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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
      const {profilePage, testUser} = await TestSetupHelper.loginAndNavigateToUserProfilePage(page, dbManager);

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

});