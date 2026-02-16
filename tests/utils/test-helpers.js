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