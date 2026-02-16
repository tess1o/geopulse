import {TestHelpers} from "../utils/test-helpers.js";

export class AppNavigation {
  constructor(page) {
    this.page = page;
  }

  /**
   * Logout from any authenticated page
   * Handles menu opening and logout button clicking
   */
  async logout() {
    try {
      // First check if logout button is already visible
      const logoutButton = this.page.locator('.gp-nav-logout');

      if (await logoutButton.isVisible({ timeout: 2000 })) {
        await logoutButton.click();
      } else {
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
              await menuButton.click();
              await this.page.waitForTimeout(500); // Wait for menu animation

              // Check if logout is now visible
              if (await logoutButton.isVisible({ timeout: 2000 })) {
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
          const logoutByText = this.page.locator('button:has-text("Logout")');
          if (await logoutByText.isVisible({ timeout: 2000 })) {
            await logoutByText.click();
          } else {
            throw new Error('Could not find logout button anywhere');
          }
        }
      }

      // Wait for logout API call to complete
      const response = await this.page.waitForResponse(resp =>
          resp.url().includes('/api/auth/logout') && resp.request().method() === 'POST',
          { timeout: 5000 }
      );
      // Wait for redirect to login page
      await TestHelpers.isHomePage(this.page);

    } catch (error) {
      console.error('❌ Logout failed:', error.message);
      throw new Error(`Failed to logout: ${error.message}`);
    }
  }
}