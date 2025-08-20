export class RegisterPage {
  constructor(page) {
    this.page = page;
    
    // Selectors
    this.selectors = {
      emailInput: '#email',
      fullNameInput: '#fullName',
      passwordInput: '#password input', // PrimeVue Password component uses nested input
      confirmPasswordInput: '#confirmPassword input',
      registerButton: 'button[type="submit"]',
      errorMessage: '.register-error',
      formTitle: '.form-title',
      passwordError: '.password-error'
    };
  }

  async navigate() {
    await this.page.goto('/register');
    await this.page.waitForLoadState('networkidle');
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.formTitle);
    await this.page.waitForSelector(this.selectors.emailInput);
    await this.page.waitForSelector(this.selectors.fullNameInput);
    await this.page.waitForSelector(this.selectors.passwordInput);
    await this.page.waitForSelector(this.selectors.confirmPasswordInput);
  }

  async fillEmail(email) {
    await this.page.fill(this.selectors.emailInput, email);
  }

  async fillFullName(fullName) {
    await this.page.fill(this.selectors.fullNameInput, fullName);
  }

  async fillPassword(password) {
    await this.page.fill(this.selectors.passwordInput, password);
  }

  async fillConfirmPassword(password) {
    await this.page.fill(this.selectors.confirmPasswordInput, password);
  }

  async clickRegister() {
    await this.page.click(this.selectors.registerButton);
  }

  async clickLoginLink() {
    await this.page.click('a:has-text("Sign in")');
  }

  async register(email, fullName, password, confirmPassword = null) {
    await this.fillEmail(email);
    await this.fillFullName(fullName);
    await this.fillPassword(password);
    await this.fillConfirmPassword(confirmPassword || password);
    await this.clickRegister();
  }

  /**
   * Get registration error message selector for use with ValidationHelpers
   * Usage: ValidationHelpers.getPageErrorMessage(page, registerPage.getErrorSelector())
   */
  getErrorSelector() {
    return this.selectors.errorMessage;
  }

  /**
   * Check if register button is enabled
   */
  async isRegisterButtonEnabled() {
    const button = this.page.locator(this.selectors.registerButton);
    return await button.isEnabled();
  }

  /**
   * Get page title
   */
  async getPageTitle() {
    return await this.page.locator(this.selectors.formTitle).textContent();
  }

  /**
   * Check if currently on register page
   */
  async isOnRegisterPage() {
    try {
      await this.page.waitForURL('**/register', { timeout: 5000 });
      await this.page.waitForSelector(this.selectors.formTitle, { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }
}