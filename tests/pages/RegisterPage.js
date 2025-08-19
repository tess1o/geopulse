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
      loginLink: 'a[href="/login"]',
      errorMessage: '.register-error',
      loadingSpinner: '.p-button-loading',
      formTitle: '.form-title',
      logo: '.app-logo',
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

  async isRegisterButtonEnabled() {
    const button = this.page.locator(this.selectors.registerButton);
    return await button.isEnabled();
  }

  // async isRegisterButtonEnabled() {
  //   const button = this.page.locator(this.selectors.registerButton);
  //   return !(await button.getAttribute('disabled'));
  // }

  async getPageTitle() {
    return await this.page.locator(this.selectors.formTitle).textContent();
  }

  async isOnRegisterPage() {
    try {
      await this.page.waitForURL('**/register', { timeout: 5000 });
      await this.page.waitForSelector(this.selectors.formTitle, { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  // Helper method to check form validation
  async hasFieldError(fieldSelector) {
    const errorSelector = `${fieldSelector} + .error-message, ${fieldSelector} ~ .error-message`;
    return await this.page.locator(errorSelector).isVisible();
  }

  async getFieldError(fieldName) {
    // Map field names to their selectors
    const fieldMap = {
      email: this.selectors.emailInput,
      fullName: this.selectors.fullNameInput,
      password: this.selectors.passwordInput,
      confirmPassword: this.selectors.confirmPasswordInput,
    };

    const fieldSelector = fieldMap[fieldName];
    if (!fieldSelector) return null;

    const errorSelector = `${fieldSelector.replace(' input', '')} + .error-message, ${fieldSelector.replace(' input', '')} ~ .error-message`;
    const errorElement = this.page.locator(errorSelector);
    
    if (await errorElement.isVisible()) {
      return await errorElement.textContent();
    }
    return null;
  }

  async waitForValidationErrors() {
    await this.page.waitForTimeout(500); // Wait for validation to run
  }
}