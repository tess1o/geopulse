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

  /**
   * Wait for custom validation error to appear
   * @param {import('@playwright/test').Page} page 
   * @param {string} selector - Error element selector
   * @param {number} timeout - Timeout in milliseconds
   */
  static async waitForCustomValidationError(page, selector, timeout = 5000) {
    await page.waitForSelector(selector, { state: 'visible', timeout });
  }

  /**
   * Check if field has validation error (checks multiple error patterns)
   * @param {import('@playwright/test').Page} page 
   * @param {string} fieldSelector - Input field selector
   * @returns {Promise<boolean>}
   */
  static async hasFieldError(page, fieldSelector) {
    // Try different error selector patterns commonly used
    const errorSelectors = [
      `${fieldSelector} + .error-message`,
      `${fieldSelector} ~ .error-message`,
      `${fieldSelector.replace(' input', '')} + .error-message`,
      `${fieldSelector.replace(' input', '')} ~ .error-message`,
    ];

    for (const selector of errorSelectors) {
      if (await this.hasCustomValidationError(page, selector)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Wait for validation to complete (generic wait)
   * @param {number} ms - Milliseconds to wait
   */
  static async waitForValidation(ms = 500) {
    // Simple wait for validation logic to execute
    await new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Get page-specific error message (login errors, registration errors, etc.)
   * @param {import('@playwright/test').Page} page 
   * @param {string} errorSelector - Error element selector (e.g., '.login-error', '.register-error')
   * @returns {Promise<string|null>}
   */
  static async getPageErrorMessage(page, errorSelector) {
    try {
      const errorElement = page.locator(errorSelector);
      if (await errorElement.isVisible({ timeout: 2000 })) {
        return await errorElement.textContent();
      }
      return null;
    } catch {
      return null;
    }
  }

  /**
   * Wait for page-specific error message to appear
   * @param {import('@playwright/test').Page} page 
   * @param {string} errorSelector - Error element selector
   * @param {number} timeout - Timeout in milliseconds
   */
  static async waitForPageErrorMessage(page, errorSelector, timeout = 5000) {
    await page.waitForSelector(errorSelector, { state: 'visible', timeout });
  }
}