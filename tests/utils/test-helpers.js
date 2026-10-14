import { expect } from '@playwright/test';
import { TestConfig } from '../config/test-config.js';

export class TestHelpers {

  /**
   * Close a PrimeVue AutoComplete suggestion list after typing into it, so the list cannot cover the next
   * control the test clicks. The list opens after the component's search delay, so wait for it first; if no
   * list opens, there is nothing to close. Escape only closes the list: the app stops it from also closing
   * an enclosing dialog.
   * @param {import('@playwright/test').Locator} input the AutoComplete's text input
   * @param {number} openTimeout how long to wait for the suggestion list to open
   */
  static async dismissAutocompleteSuggestions(input, openTimeout = 2000) {
    try {
      await expect(input).toHaveAttribute('aria-expanded', 'true', { timeout: openTimeout });
    } catch {
      return;
    }
    await input.press('Escape');
    await expect(input).toHaveAttribute('aria-expanded', 'false');
  }

  /**
   * Show a month in an open PrimeVue DatePicker by picking it in the year and month views. Stepping there
   * with the prev/next arrows costs one click per month between today and the target, so tests on fixed
   * dates got slower every month until they ran past their timeouts; this takes a few clicks whatever
   * the date.
   * @param {import('@playwright/test').Locator} panel the open picker panel, in its date view
   * @param {number} year e.g. 2025
   * @param {number} monthIndex 0-based, like Date#getMonth
   */
  static async navigateDatePickerToMonth(panel, year, monthIndex) {
    await panel.locator('.p-datepicker-select-year').first().click();
    const yearCells = panel.locator('.p-datepicker-year-view .p-datepicker-year');
    await expect(yearCells.first()).toBeVisible();

    // The year view shows one decade; the arrows move a decade at a time.
    for (;;) {
      const years = (await yearCells.allTextContents()).map(text => parseInt(text, 10));
      if (years.includes(year)) break;
      const arrow = year < years[0] ? '.p-datepicker-prev-button' : '.p-datepicker-next-button';
      await panel.locator(arrow).first().click();
      await expect.poll(async () => parseInt(await yearCells.first().textContent(), 10)).not.toBe(years[0]);
    }

    await yearCells.filter({ hasText: String(year) }).first().click();
    // The month view always lists all twelve months, January first.
    await panel.locator('.p-datepicker-month-view .p-datepicker-month').nth(monthIndex).click();
    await expect(panel.locator('.p-datepicker-day-view')).toBeVisible();
  }

  /**
   * Wait for navigation to complete
   * @param {import('@playwright/test').Page} page 
   * @param {string|RegExp|((url: URL) => boolean)} expectedUrl
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
        const response = await page.request.get(`${TestConfig.API_BASE_URL}/api/v1/system/health`, {
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
   * Check if user is authenticated by checking for the session cookie
   * @param {import('@playwright/test').Page} page 
   */
  static async isAuthenticated(page) {
    const session = await this.getCookieValue(page, 'gp_session');
    return session != null;
  }

  static async isHomePage(page) {
    return await page.url().includes('/app');
  }
}
