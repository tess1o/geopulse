import { TestConfig } from '../config/test-config.js';

export class TestHelpers {
  
  /**
   * Wait for navigation to complete
   * @param {import('@playwright/test').Page} page 
   * @param {string} expectedUrl 
   * @param {number} timeout 
   */
  static async waitForNavigation(page, expectedUrl, timeout = 10000) {
    await page.waitForURL(expectedUrl, { timeout });
    await page.waitForLoadState('networkidle', { timeout });
  }

  static isBackendUnavailableUrl(url) {
    return /\/error(?:\?|$)/.test(url) && url.includes('type=connection');
  }

  static async isBackendUnavailablePage(page) {
    if (this.isBackendUnavailableUrl(page.url())) {
      return true;
    }

    return await page
      .locator('h1:has-text("Backend Unavailable")')
      .isVisible()
      .catch(() => false);
  }

  static async waitForBackendHealthy(page, options = {}) {
    const timeout = options.timeout ?? 30000;
    const interval = options.interval ?? 750;
    const deadline = Date.now() + timeout;
    let lastError = null;

    while (Date.now() < deadline) {
      try {
        const requestTimeout = Math.max(1000, Math.min(5000, deadline - Date.now()));
        const response = await page.request.get(`${TestConfig.API_BASE_URL}/api/health`, {
          timeout: requestTimeout
        });

        if (response.ok()) {
          return true;
        }

        lastError = `status ${response.status()}`;
      } catch (error) {
        lastError = error.message;
      }

      await page.waitForTimeout(Math.min(interval, Math.max(0, deadline - Date.now())));
    }

    throw new Error(`Backend did not become healthy within ${timeout}ms${lastError ? ` (${lastError})` : ''}`);
  }

  /**
   * Extract cookie value by name
   * @param {import('@playwright/test').Page} page 
   * @param {string} cookieName 
   */
  static async getCookieValue(page, cookieName) {
    const cookies = await page.context().cookies();
    const cookie = cookies.find(c => c.name === cookieName);
    return cookie ? cookie.value : null;
  }

  /**
   * Check if user is authenticated by checking for auth cookies
   * @param {import('@playwright/test').Page} page 
   */
  static async isAuthenticated(page) {
    const accessToken = await this.getCookieValue(page, 'access_token');
    return accessToken != null;
  }

  static async isHomePage(page) {
    return await page.url().includes('/app');
  }
}
