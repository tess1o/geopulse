import {TestHelpers} from "../utils/test-helpers.js";

export class DashboardPage {
  constructor(page) {
    this.page = page;

    // Selectors
    this.selectors = {
      navbar: '.app-navbar',
      userMenu: '.user-menu',
      logoutButton: '[data-testid="logout-button"], button:has-text("Logout")',
      timelineLink: 'a[href="/app/timeline"]',
      locationSourcesLink: 'a[href="/app/location-sources"]',
      dashboardContent: '.dashboard-content',
      navigation: '.app-navigation',
      welcomeMessage: '.welcome-message',
      loadingSpinner: '.loading',
    };
  }

  async isOnTimelinePage() {
    const url = this.page.url();
    return /\/app\/timeline(\/|\?|#|$)/.test(url);
  }

  async isOnLocationSourcesPage() {
    try {
      await this.page.waitForURL('**/app/location-sources', { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  async logout() {
    try {
      // The logout button is in the left sidebar menu, we may need to open it first
      console.log('🔍 Looking for logout button...');

      // First check if logout button is already visible
      const logoutButton = this.page.locator('.gp-nav-logout');

      if (await logoutButton.isVisible({ timeout: 2000 })) {
        console.log('✅ Found logout button, clicking...');
        await logoutButton.click();
      } else {
        // Try to find and click menu toggle/hamburger menu first
        console.log('🔍 Logout not visible, looking for menu toggle...');

        const menuSelectors = [
          'span.pi-bars', // PrimeVue menu icon
          '.p-button:has(.pi-bars)',
          'button:has(.pi-bars)',
          '.sidebar-toggle',
          '.menu-toggle',
          'button[aria-label="Menu"]',
          '.hamburger-menu'
        ];

        let menuOpened = false;
        for (const selector of menuSelectors) {
          try {
            const menuButton = this.page.locator(selector);
            if (await menuButton.isVisible({ timeout: 1000 })) {
              console.log(`✅ Found menu button: ${selector}`);
              await menuButton.click();
              await this.page.waitForTimeout(500); // Wait for menu animation

              // Check if logout is now visible
              if (await logoutButton.isVisible({ timeout: 2000 })) {
                console.log('✅ Menu opened, logout button now visible');
                await logoutButton.click();
                menuOpened = true;
                break;
              }
            }
          } catch (e) {
            // Continue to next selector
          }
        }

        if (!menuOpened) {
          // Last resort: try to click any button with "Logout" text
          console.log('🔍 Trying to find any logout button by text...');
          const logoutByText = this.page.locator('button:has-text("Logout")');
          if (await logoutByText.isVisible({ timeout: 2000 })) {
            await logoutByText.click();
          } else {
            throw new Error('Could not find logout button anywhere');
          }
        }
      }

      // Wait for logout API call to complete
      console.log('⏳ Waiting for logout API call...');
      const response = await this.page.waitForResponse(resp =>
          resp.url().includes('/api/auth/logout') && resp.request().method() === 'POST',
          { timeout: 5000 }
      );
      console.log('✅ Logout API call completed:', response.status());

      // Wait for redirect to login page
      console.log('⏳ Waiting for redirect to login...');
      await TestHelpers.isHomePage(this.page);
      console.log('✅ Successfully logged out');

    } catch (error) {
      console.error('❌ Logout failed:', error.message);
      throw new Error(`Failed to logout: ${error.message}`);
    }
  }

}