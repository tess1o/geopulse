import {TestHelpers} from "../utils/test-helpers.js";

export class AppNavigation {
  constructor(page) {
    this.page = page;
  }

  /**
   * Logout from any authenticated page via the avatar menu.
   */
  async logout() {
    try {
      // Start waiting before clicking to avoid missing very fast responses.
      const logoutResponsePromise = this.page.waitForResponse(
        (resp) => resp.url().includes('/api/v1/auth/sessions/current') && resp.request().method() === 'DELETE',
        { timeout: 7000 }
      ).catch(() => null);

      // Logout lives in the account menu behind the avatar in the top bar.
      const logoutButton = this.page.locator('.gp-user-menu-logout');
      if (!(await logoutButton.isVisible({ timeout: 1000 }).catch(() => false))) {
        await this.page.locator('.gp-user-menu-trigger:visible').first().click();
      }
      await logoutButton.click();

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
