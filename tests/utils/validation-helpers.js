export class ValidationHelpers {
  /**
   * Get browser validation message from input element
   * @param {import('@playwright/test').Page} page 
   * @param {string} selector - Input element selector
   * @returns {Promise<string>}
   */
  static async getBrowserValidationMessage(page, selector) {
    const element = page.locator(selector);
    return await element.evaluate(el => el.validationMessage);
  }

  /**
   * Check if input element has specific browser validation message
   * @param {import('@playwright/test').Page} page 
   * @param {string} selector - Input element selector
   * @param {string} expectedMessage - Expected validation message (partial match)
   * @returns {Promise<boolean>}
   */
  static async hasValidationMessage(page, selector, expectedMessage) {
    const validationMessage = await this.getBrowserValidationMessage(page, selector);
    return validationMessage.includes(expectedMessage);
  }

  /**
   * Wait for and verify browser validation message
   * @param {import('@playwright/test').Page} page 
   * @param {string} selector - Input element selector
   * @param {string} expectedMessage - Expected validation message (partial match)
   * @param {number} timeout - Timeout in milliseconds
   */
  static async waitForValidationMessage(page, selector, expectedMessage, timeout = 5000) {
    await page.waitForFunction(
      ({ selector, expectedMessage }) => {
        const element = document.querySelector(selector);
        return element && element.validationMessage.includes(expectedMessage);
      },
      { selector, expectedMessage },
      { timeout }
    );
  }

  /**
   * Check if element has custom validation error (not browser validation)
   * @param {import('@playwright/test').Page} page 
   * @param {string} selector - Error element selector
   * @returns {Promise<boolean>}
   */
  static async hasCustomValidationError(page, selector) {
    try {
      const errorElement = page.locator(selector);
      return await errorElement.isVisible({ timeout: 2000 });
    } catch {
      return false;
    }
  }

  /**
   * Get custom validation error message text
   * @param {import('@playwright/test').Page} page 
   * @param {string} selector - Error element selector
   * @returns {Promise<string|null>}
   */
  static async getCustomValidationError(page, selector) {
    try {
      const errorElement = page.locator(selector);
      if (await errorElement.isVisible({ timeout: 2000 })) {
        return await errorElement.textContent();
      }
      return null;
    } catch {
      return null;
    }
  }
}