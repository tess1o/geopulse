import faker from 'faker';

export class TestHelpers {
  
  /**
   * Generate random user data for testing
   * @returns {Object} User data object
   */
  static generateUserData() {
    return {
      email: faker.internet.email().toLowerCase(),
      fullName: faker.name.findName(),
      password: 'TestPassword123!',
    };
  }

  /**
   * Generate existing test user credentials
   * @returns {Object} Test user credentials
   */
  static getTestUserCredentials() {
    return {
      email: 'testuser@example.com',
      password: 'password123',
      fullName: 'Test User',
    };
  }

  /**
   * Wait for a specific amount of time
   * @param {number} ms - Milliseconds to wait
   */
  static async wait(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Generate a unique email address for testing
   * @param {string} prefix - Email prefix
   * @returns {string} Unique email
   */
  static generateUniqueEmail(prefix = 'test') {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(7);
    return `${prefix}_${timestamp}_${random}@example.com`;
  }

  /**
   * Wait for element to be visible and stable
   * @param {import('@playwright/test').Page} page 
   * @param {string} selector 
   * @param {number} timeout 
   */
  static async waitForElementStable(page, selector, timeout = 10000) {
    await page.waitForSelector(selector, { state: 'visible', timeout });
    await page.waitForTimeout(100); // Small delay for element stability
  }

  /**
   * Clear and type text into input field
   * @param {import('@playwright/test').Page} page 
   * @param {string} selector 
   * @param {string} text 
   */
  static async clearAndType(page, selector, text) {
    await page.click(selector);
    await page.keyboard.press('Control+A');
    await page.keyboard.type(text);
  }

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
   * Check if element contains text
   * @param {import('@playwright/test').Page} page 
   * @param {string} selector 
   * @param {string} text 
   * @returns {Promise<boolean>}
   */
  static async elementContainsText(page, selector, text) {
    try {
      const element = await page.locator(selector);
      const content = await element.textContent();
      return content.includes(text);
    } catch {
      return false;
    }
  }

  /**
   * Wait for API response and return data
   * @param {import('@playwright/test').Page} page 
   * @param {string} urlPattern 
   * @param {number} timeout 
   */
  static async waitForApiResponse(page, urlPattern, timeout = 10000) {
    const responsePromise = page.waitForResponse(
      response => response.url().includes(urlPattern) && response.status() === 200,
      { timeout }
    );
    return await responsePromise;
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
    return accessToken != null
  }

  static async isHomePage(page) {
    console.log('URL: ', await page.url());
    return await page.url().includes('/app');
  }
}