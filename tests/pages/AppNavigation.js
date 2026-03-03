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
      // Start waiting before clicking to avoid missing very fast responses.
      const logoutResponsePromise = this.page.waitForResponse(
        (resp) => resp.url().includes('/api/auth/logout') && resp.request().method() === 'POST',
        { timeout: 7000 }
      ).catch(() => null);

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

      // Wait for logout call if captured; do not fail on missing response event.
      await logoutResponsePromise;

      // Wait until we are no longer inside authenticated app routes.
      await this.page.waitForURL(
        (url) => !url.pathname.startsWith('/app'),
        { timeout: 10000 }
      ).catch(() => {});

      // Preserve prior side effect for callers relying on this helper.
      await TestHelpers.isHomePage(this.page);

    } catch (error) {
      console.error('❌ Logout failed:', error.message);
      throw new Error(`Failed to logout: ${error.message}`);
    }
  }
}
