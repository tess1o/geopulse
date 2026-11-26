import { expect } from '@playwright/test';
import { TestHelpers } from '../utils/test-helpers.js';
import { TestConfig } from '../config/test-config.js';

export class LoginPage {
  constructor(page) {
    this.page = page;
    
    // Only selectors that are actually used
    this.selectors = {
      emailInput: '#email',
      passwordInput: '#password input', // PrimeVue Password component uses nested input
      loginButton: 'button[type="submit"]',
      errorMessage: '.login-error',
    };
  }

  /**
   * Navigate to login page
   */
  async navigate(options = {}) {
    await TestHelpers.waitForBackendHealthy(this.page, {
      timeout: options.backendHealthTimeout ?? 30000
    });
    await this.page.goto('/login');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Login with email and password
   */
  async login(email, password) {
    await this.page.fill(this.selectors.emailInput, email);
    await this.page.fill(this.selectors.passwordInput, password);
    const loginButton = this.page.locator(this.selectors.loginButton);
    await expect(loginButton).toBeEnabled({ timeout: 10000 });
    await loginButton.click();
  }

  /**
   * Sign in through the session API instead of the form. The session cookies land in the browser
   * context, so the next page.goto opens the app signed in. Only the sign-in specs need the form.
   * Pass landing to open that page afterwards (the form lands on /app/timeline).
   */
  async loginViaApi(email, password, { landing = null } = {}) {
    await TestHelpers.waitForBackendHealthy(this.page);
    const response = await this.page.request.post(TestConfig.API_ENDPOINTS.login, {
      data: { email, password },
    });
    expect(response.ok(), `API sign-in for ${email} returned ${response.status()}`).toBe(true);

    if (landing) {
      await this.page.goto(landing);
      await TestHelpers.waitForNavigation(this.page, `**${landing}`, 30000);
    }
  }

  /**
   * Check if currently on login page
   */
  async isOnLoginPage() {
    try {
      // Match on the path only: guarded routes redirect to /login?redirect=<original path>
      await this.page.waitForURL(url => url.pathname === '/login', { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Get login error message selector for use with ValidationHelpers
   * Usage: ValidationHelpers.getPageErrorMessage(page, loginPage.getErrorSelector())
   */
  getErrorSelector() {
    return this.selectors.errorMessage;
  }
}
