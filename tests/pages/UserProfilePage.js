export class UserProfilePage {
  constructor(page) {
    this.page = page;
    
    this.selectors = {
      // Tab navigation - using span with data-pc-section="itemlabel"
      profileTab: '[data-pc-section="itemlabel"]:has-text("Profile")',
      securityTab: '[data-pc-section="itemlabel"]:has-text("Security")',
      immichTab: '[data-pc-section="itemlabel"]:has-text("Immich")',
      
      // Profile Information tab selectors
      profile: {
        fullNameInput: '#fullName',
        emailInput: '#email',
        timezoneDropdown: '#timezone',
        timezoneDropdownTrigger: '#timezone .p-select-dropdown, #timezone .p-select-label',
        timezoneOptions: '[role="option"], .p-select-option',
        timezoneLabel: '#timezone .p-select-label',
        saveButton: 'button[type="submit"]:has-text("Save Changes")',
        resetButton: 'button:has-text("Reset")',
        avatarOptions: '.avatar-option',
        selectedAvatar: '.avatar-option.active',
        userAvatar: '.user-avatar',
        errorMessage: '.error-message'
      },
      
      // Security tab selectors
      security: {
        currentPasswordInput: '#currentPassword input',
        newPasswordInput: '#newPassword input',
        confirmPasswordInput: '#confirmPassword input',
        changePasswordButton: 'button[type="submit"]:has-text("Change Password")',
        cancelButton: 'button:has-text("Cancel")',
        errorMessage: '.error-message'
      },
      
      // Immich Integration tab selectors
      immich: {
        enableToggle: '.p-togglebutton',
        serverUrlInput: '#immichServerUrl',
        apiKeyInput: '#immichApiKey input',
        saveButton: 'button[type="submit"]:has-text("Save Settings")',
        resetButton: 'button:has-text("Reset")',
        connectionStatus: '.connection-status',
        statusIndicator: '.status-indicator',
        errorMessage: '.error-message'
      },
      
      // Common elements
      toast: '.p-toast-message',
      toastSuccess: '.p-toast-message-success',
      toastError: '.p-toast-message-error',
      pageTitle: '.page-title'
    };
  }

  /**
   * Navigate to user profile page
   */
  async navigate() {
    await this.page.goto('/app/profile');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Wait for page to load
   */
  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageTitle);
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Check if currently on profile page
   */
  async isOnProfilePage() {
    try {
      await this.page.waitForURL('**/app/profile', { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  // =============================================================================
  // TAB NAVIGATION
  // =============================================================================

  /**
   * Switch to Profile Information tab
   */
  async switchToProfileTab() {
    await this.page.locator(this.selectors.profileTab).click();
    await this.page.waitForTimeout(500); // Wait for tab content to load
  }

  /**
   * Switch to Security tab
   */
  async switchToSecurityTab() {
    await this.page.locator(this.selectors.securityTab).click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Switch to Immich Integration tab
   */
  async switchToImmichTab() {
    await this.page.locator(this.selectors.immichTab).click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Check if Profile Information tab is active
   */
  async isProfileTabActive() {
    // Look for the li element that contains our span - go up two levels: span -> a -> li
    const tabItem = this.page.locator(this.selectors.profileTab).locator('xpath=../..'); 
    const classes = await tabItem.getAttribute('class');
    const dataActive = await tabItem.getAttribute('data-p-active');
    return (classes && classes.includes('p-tabmenu-item-active')) || dataActive === 'true';
  }

  /**
   * Check if Security tab is active
   */
  async isSecurityTabActive() {
    const tabItem = this.page.locator(this.selectors.securityTab).locator('xpath=../..'); 
    const classes = await tabItem.getAttribute('class');
    const dataActive = await tabItem.getAttribute('data-p-active');
    return (classes && classes.includes('p-tabmenu-item-active')) || dataActive === 'true';
  }

  /**
   * Check if Immich Integration tab is active
   */
  async isImmichTabActive() {
    const tabItem = this.page.locator(this.selectors.immichTab).locator('xpath=../..'); 
    const classes = await tabItem.getAttribute('class');
    const dataActive = await tabItem.getAttribute('data-p-active');
    return (classes && classes.includes('p-tabmenu-item-active')) || dataActive === 'true';
  }

  // =============================================================================
  // PROFILE INFORMATION TAB
  // =============================================================================

  /**
   * Fill profile form
   */
  async fillProfileForm(fullName) {
    await this.page.fill(this.selectors.profile.fullNameInput, fullName);
  }

  /**
   * Select avatar by index
   */
  async selectAvatar(index) {
    const avatarOptions = this.page.locator(this.selectors.profile.avatarOptions);
    await avatarOptions.nth(index).click();
  }

  /**
   * Get current full name value
   */
  async getFullNameValue() {
    return await this.page.inputValue(this.selectors.profile.fullNameInput);
  }

  /**
   * Get current email value
   */
  async getEmailValue() {
    return await this.page.inputValue(this.selectors.profile.emailInput);
  }

  /**
   * Check if email field is disabled
   */
  async isEmailFieldDisabled() {
    return await this.page.isDisabled(this.selectors.profile.emailInput);
  }

  /**
   * Get selected avatar index
   */
  async getSelectedAvatarIndex() {
    const avatarOptions = this.page.locator(this.selectors.profile.avatarOptions);
    const count = await avatarOptions.count();
    
    for (let i = 0; i < count; i++) {
      const classes = await avatarOptions.nth(i).getAttribute('class');
      if (classes.includes('active')) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Check if Save Changes button is enabled
   */
  async isSaveButtonEnabled() {
    return !await this.page.isDisabled(this.selectors.profile.saveButton);
  }

  /**
   * Save profile changes
   */
  async saveProfile() {
    await this.page.click(this.selectors.profile.saveButton);
  }

  /**
   * Reset profile form
   */
  async resetProfile() {
    await this.page.click(this.selectors.profile.resetButton);
  }

  /**
   * Get profile validation error message
   */
  async getProfileErrorMessage() {
    try {
      const errorElement = this.page.locator(this.selectors.profile.errorMessage).first();
      if (await errorElement.isVisible({ timeout: 2000 })) {
        return await errorElement.textContent();
      }
      return null;
    } catch {
      return null;
    }
  }

  /**
   * Select timezone from dropdown
   */
  async selectTimezone(timezone) {
    // Click on the dropdown to open it
    await this.page.click(this.selectors.profile.timezoneLabel);
    
    // Wait for dropdown options to appear
    await this.page.waitForSelector(this.selectors.profile.timezoneOptions, { timeout: 10000 });
    
    // Click on the specific timezone option
    const optionSelector = this.page.locator(this.selectors.profile.timezoneOptions).filter({ hasText: timezone });
    await optionSelector.first().click();
    
    // Wait for dropdown to close and selection to be processed
    await this.page.waitForTimeout(1000);
  }

  /**
   * Get currently selected timezone
   */
  async getSelectedTimezone() {
    // Get the displayed value in the dropdown label
    const dropdownLabel = this.page.locator(this.selectors.profile.timezoneLabel);
    const text = await dropdownLabel.textContent();
    
    // If showing placeholder text, return empty or handle accordingly
    if (text === 'Select your timezone') {
      return null;
    }
    
    return text?.trim();
  }

  /**
   * Get timezone value from localStorage
   */
  async getTimezoneFromLocalStorage() {
    const userInfo = await this.page.evaluate(() => {
      const userInfoStr = localStorage.getItem('userInfo');
      return userInfoStr ? JSON.parse(userInfoStr) : null;
    });
    return userInfo?.timezone || null;
  }

  // =============================================================================
  // SECURITY TAB
  // =============================================================================

  /**
   * Fill password change form
   */
  async fillPasswordForm(currentPassword, newPassword, confirmPassword) {
    await this.page.fill(this.selectors.security.currentPasswordInput, currentPassword);
    await this.page.fill(this.selectors.security.newPasswordInput, newPassword);
    await this.page.fill(this.selectors.security.confirmPasswordInput, confirmPassword);
  }

  /**
   * Check if Change Password button is enabled
   */
  async isChangePasswordButtonEnabled() {
    return !await this.page.isDisabled(this.selectors.security.changePasswordButton);
  }

  /**
   * Submit password change
   */
  async changePassword() {
    await this.page.click(this.selectors.security.changePasswordButton);
  }

  /**
   * Cancel password change
   */
  async cancelPasswordChange() {
    await this.page.click(this.selectors.security.cancelButton);
  }

  /**
   * Get password validation error message
   */
  async getPasswordErrorMessage() {
    try {
      const errorElement = this.page.locator(this.selectors.security.errorMessage).first();
      if (await errorElement.isVisible({ timeout: 2000 })) {
        return await errorElement.textContent();
      }
      return null;
    } catch {
      return null;
    }
  }

  /**
   * Check if password form is empty
   */
  async isPasswordFormEmpty() {
    const current = await this.page.inputValue(this.selectors.security.currentPasswordInput);
    const newPassword = await this.page.inputValue(this.selectors.security.newPasswordInput);
    const confirm = await this.page.inputValue(this.selectors.security.confirmPasswordInput);
    
    return !current && !newPassword && !confirm;
  }

  // =============================================================================
  // IMMICH INTEGRATION TAB
  // =============================================================================

  /**
   * Toggle Immich integration
   */
  async toggleImmichIntegration() {
    await this.page.click(this.selectors.immich.enableToggle);
  }

  /**
   * Check if Immich integration is enabled
   */
  async isImmichIntegrationEnabled() {
    const toggle = this.page.locator(this.selectors.immich.enableToggle);
    const classes = await toggle.getAttribute('class');
    return classes.includes('p-highlight') || classes.includes('p-togglebutton-checked');
  }

  /**
   * Fill Immich configuration form
   */
  async fillImmichForm(serverUrl, apiKey) {
    if (serverUrl !== null) {
      await this.page.fill(this.selectors.immich.serverUrlInput, serverUrl);
    }
    if (apiKey !== null) {
      await this.page.fill(this.selectors.immich.apiKeyInput, apiKey);
    }
  }

  /**
   * Get Immich server URL value
   */
  async getImmichServerUrl() {
    return await this.page.inputValue(this.selectors.immich.serverUrlInput);
  }

  /**
   * Check if Immich fields are disabled
   */
  async areImmichFieldsDisabled() {
    const serverUrlDisabled = await this.page.isDisabled(this.selectors.immich.serverUrlInput);
    const apiKeyDisabled = await this.page.isDisabled(this.selectors.immich.apiKeyInput);
    return serverUrlDisabled && apiKeyDisabled;
  }

  /**
   * Check if Save Settings button is enabled
   */
  async isImmichSaveButtonEnabled() {
    return !await this.page.isDisabled(this.selectors.immich.saveButton);
  }

  /**
   * Save Immich settings
   */
  async saveImmichSettings() {
    await this.page.click(this.selectors.immich.saveButton);
  }

  /**
   * Reset Immich form
   */
  async resetImmichForm() {
    await this.page.click(this.selectors.immich.resetButton);
  }

  /**
   * Get Immich validation error message
   */
  async getImmichErrorMessage() {
    try {
      const errorElement = this.page.locator(this.selectors.immich.errorMessage).first();
      if (await errorElement.isVisible({ timeout: 2000 })) {
        return await errorElement.textContent();
      }
      return null;
    } catch {
      return null;
    }
  }

  /**
   * Get Immich connection status
   */
  async getImmichConnectionStatus() {
    try {
      const statusElement = this.page.locator(this.selectors.immich.statusIndicator);
      if (await statusElement.isVisible({ timeout: 2000 })) {
        return await statusElement.textContent();
      }
      return null;
    } catch {
      return null;
    }
  }

  /**
   * Check if connection status shows connected
   */
  async isImmichConnected() {
    const status = await this.getImmichConnectionStatus();
    return status && status.includes('Connected');
  }

  // =============================================================================
  // COMMON UTILITIES
  // =============================================================================

  /**
   * Wait for success toast message
   */
  async waitForSuccessToast() {
    await this.page.waitForSelector(this.selectors.toastSuccess, { timeout: 10000 });
  }

  /**
   * Wait for error toast message
   */
  async waitForErrorToast() {
    await this.page.waitForSelector(this.selectors.toastError, { timeout: 10000 });
  }

  /**
   * Get toast message text
   */
  async getToastMessage() {
    try {
      const toastElement = this.page.locator(`${this.selectors.toast} .p-toast-detail`).first();
      if (await toastElement.isVisible({ timeout: 2000 })) {
        return await toastElement.textContent();
      }
      return null;
    } catch {
      return null;
    }
  }

  /**
   * Wait for any loading to complete
   */
  async waitForLoading() {
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(500);
  }
}