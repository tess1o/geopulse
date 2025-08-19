export class LoginPage {
  constructor(page) {
    this.page = page;
    
    // Selectors
    this.selectors = {
      emailInput: '#email',
      passwordInput: '#password input', // PrimeVue Password component uses nested input
      loginButton: 'button[type="submit"]',
      registerLink: 'a[href="/register"]',
      errorMessage: '.login-error',
      loadingSpinner: '.p-button-loading',
      formTitle: '.form-title',
      logo: '.app-logo',
    };
  }

  async navigate() {
    await this.page.goto('/login');
    await this.page.waitForLoadState('networkidle');
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.formTitle);
    await this.page.waitForSelector(this.selectors.emailInput);
    await this.page.waitForSelector(this.selectors.passwordInput);
  }

  async fillEmail(email) {
    await this.page.fill(this.selectors.emailInput, email);
  }

  async fillPassword(password) {
    await this.page.fill(this.selectors.passwordInput, password);
  }

  async clickLogin() {
    await this.page.click(this.selectors.loginButton);
  }

  async clickRegisterLink() {
    await this.page.click('a:has-text("Create account")');
  }

  async login(email, password) {
    await this.fillEmail(email);
    await this.fillPassword(password);
    await this.clickLogin();
  }

  async getErrorMessage() {
    const errorElement = this.page.locator(this.selectors.errorMessage);
    if (await errorElement.isVisible()) {
      return await errorElement.textContent();
    }
    return null;
  }

  async isLoading() {
    return await this.page.locator(this.selectors.loadingSpinner).isVisible();
  }

  async waitForErrorMessage() {
    await this.page.waitForSelector(this.selectors.errorMessage, { state: 'visible' });
  }

  async isLoginButtonEnabled() {
    const button = this.page.locator(this.selectors.loginButton);
    return !(await button.getAttribute('disabled'));
  }

  async getPageTitle() {
    return await this.page.locator(this.selectors.formTitle).textContent();
  }

  async isOnLoginPage() {
    try {
      await this.page.waitForURL('**/login', { timeout: 5000 });
      await this.page.waitForSelector(this.selectors.formTitle, { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  // Helper method to check form validation
  async hasFieldError(fieldSelector) {
    const errorSelector = `${fieldSelector} + .error-message`;
    return await this.page.locator(errorSelector).isVisible();
  }

  async getFieldError(fieldSelector) {
    const errorSelector = `${fieldSelector} + .error-message`;
    const errorElement = this.page.locator(errorSelector);
    if (await errorElement.isVisible()) {
      return await errorElement.textContent();
    }
    return null;
  }
}