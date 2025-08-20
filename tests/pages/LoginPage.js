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
  async navigate() {
    await this.page.goto('/login');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Login with email and password
   */
  async login(email, password) {
    await this.page.fill(this.selectors.emailInput, email);
    await this.page.fill(this.selectors.passwordInput, password);
    await this.page.click(this.selectors.loginButton);
  }

  /**
   * Check if currently on login page
   */
  async isOnLoginPage() {
    try {
      await this.page.waitForURL('**/login', { timeout: 5000 });
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