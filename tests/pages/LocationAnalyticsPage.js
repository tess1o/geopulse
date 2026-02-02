import { expect } from '@playwright/test';

export class LocationAnalyticsPage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      // Page container
      pageContainer: '.page-container',
      pageTitle: 'h1:has-text("Location Analytics")',
      pageSubtitle: 'p:has-text("Explore your visits by city and country")',

      // Header controls
      headerControls: '.header-controls',
      searchContainer: '.search-container',
      searchInput: '.p-autocomplete-input',
      searchDropdown: '.p-autocomplete-panel',
      searchResultItem: '.search-result-item',

      // Tabs
      tabsContainer: '.analytics-tabs',
      citiesTab: 'button:has-text("Cities")',
      countriesTab: 'button:has-text("Countries")',
      activeTab: '.active-tab',

      // Loading states
      progressSpinner: '.p-progress-spinner',
      loadingContainer: '.loading-container',

      // Empty states
      emptyState: '.empty-state',
      emptyIcon: '.empty-icon',

      // Location grid
      locationGrid: '.location-grid',
      locationCard: '.location-card',
      locationIcon: '.location-icon',
      locationName: '.location-name',
      locationCountry: '.location-country',
      locationStats: '.location-stats',
      statItem: '.stat-item',
      statValue: '.stat-value',
      statLabel: '.stat-label',

      // Toast notifications
      successToast: '.p-toast-message-success',
      errorToast: '.p-toast-message-error',
      toastDetail: '.p-toast-detail',
    };
  }

  // ===========================================
  // NAVIGATION
  // ===========================================

  async navigate() {
    await this.page.goto('/app/location-analytics');
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageContainer, { timeout: 10000 });
    await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 5000 });
    await this.page.waitForTimeout(1000); // Give time for data to load
  }

  async isOnLocationAnalyticsPage() {
    try {
      await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  // ===========================================
  // TAB OPERATIONS
  // ===========================================

  async clickCitiesTab() {
    await this.page.locator(this.selectors.citiesTab).click();
    await this.page.waitForTimeout(500);
  }

  async clickCountriesTab() {
    await this.page.locator(this.selectors.countriesTab).click();
    await this.page.waitForTimeout(500);
  }

  async getActiveTabText() {
    const activeTab = this.page.locator(this.selectors.activeTab);
    return await activeTab.textContent();
  }

  async isTabActive(tabName) {
    const activeTabText = await this.getActiveTabText();
    return activeTabText.toLowerCase().includes(tabName.toLowerCase());
  }

  async getCitiesTabCount() {
    const citiesTab = this.page.locator(this.selectors.citiesTab);
    const text = await citiesTab.textContent();
    const match = text.match(/\((\d+)\)/);
    return match ? parseInt(match[1]) : 0;
  }

  async getCountriesTabCount() {
    const countriesTab = this.page.locator(this.selectors.countriesTab);
    const text = await countriesTab.textContent();
    const match = text.match(/\((\d+)\)/);
    return match ? parseInt(match[1]) : 0;
  }

  // ===========================================
  // SEARCH OPERATIONS
  // ===========================================

  async fillSearchInput(query) {
    const searchInput = this.page.locator(this.selectors.searchInput);
    await searchInput.click();
    await searchInput.fill(query);
    await this.page.waitForTimeout(500); // Wait for debounce
  }

  async waitForSearchDropdown() {
    await this.page.waitForSelector(this.selectors.searchDropdown, {
      state: 'visible',
      timeout: 5000
    });
  }

  async isSearchDropdownVisible() {
    try {
      await this.waitForSearchDropdown();
      return true;
    } catch {
      return false;
    }
  }

  async getSearchResults() {
    const results = await this.page.locator(this.selectors.searchResultItem).all();
    const resultData = [];

    for (const result of results) {
      const name = await result.locator('.result-name').textContent();
      const type = await result.locator('.result-type').textContent();
      resultData.push({ name, type });
    }

    return resultData;
  }

  async clickSearchResult(index = 0) {
    const results = this.page.locator(this.selectors.searchResultItem);
    await results.nth(index).click();
    await this.page.waitForTimeout(1000);
  }

  async searchAndSelect(query, index = 0) {
    await this.fillSearchInput(query);
    await this.waitForSearchDropdown();
    await this.clickSearchResult(index);
  }

  // ===========================================
  // LOADING AND EMPTY STATES
  // ===========================================

  async isLoading() {
    return await this.page.locator(this.selectors.loadingContainer).isVisible().catch(() => false);
  }

  async waitForLoadingToComplete() {
    try {
      await this.page.waitForSelector(this.selectors.loadingContainer, {
        state: 'visible',
        timeout: 2000
      });
      await this.page.waitForSelector(this.selectors.loadingContainer, {
        state: 'hidden',
        timeout: 10000
      });
    } catch {
      // Loading might complete too quickly
    }
  }

  async isEmptyStateVisible() {
    return await this.page.locator(this.selectors.emptyState).isVisible().catch(() => false);
  }

  async getEmptyStateText() {
    if (!await this.isEmptyStateVisible()) {
      return null;
    }
    return await this.page.locator(this.selectors.emptyState).textContent();
  }

  // ===========================================
  // LOCATION CARDS
  // ===========================================

  async getLocationCardCount() {
    return await this.page.locator(this.selectors.locationCard).count();
  }

  async getLocationCards() {
    const cards = await this.page.locator(this.selectors.locationCard).all();
    const cardData = [];

    for (const card of cards) {
      const name = await card.locator(this.selectors.locationName).textContent();
      const statsItems = await card.locator(this.selectors.statItem).all();

      const stats = {};
      for (const statItem of statsItems) {
        const label = await statItem.locator(this.selectors.statLabel).textContent();
        const value = await statItem.locator(this.selectors.statValue).textContent();
        stats[label.trim()] = parseInt(value);
      }

      const data = { name: name.trim(), stats };

      // Check if it's a city card (has country)
      const countryElement = card.locator(this.selectors.locationCountry);
      if (await countryElement.count() > 0) {
        data.country = (await countryElement.textContent()).trim();
      }

      cardData.push(data);
    }

    return cardData;
  }

  async getLocationCardByIndex(index) {
    const cards = await this.getLocationCards();
    return cards[index] || null;
  }

  async getLocationCardByName(name) {
    const cards = await this.getLocationCards();
    return cards.find(card => card.name === name) || null;
  }

  async clickLocationCard(index) {
    const cards = this.page.locator(this.selectors.locationCard);
    await cards.nth(index).click();
    await this.page.waitForTimeout(1000);
  }

  async clickLocationCardByName(name) {
    const cards = this.page.locator(this.selectors.locationCard);
    const count = await cards.count();

    for (let i = 0; i < count; i++) {
      const cardName = await cards.nth(i).locator(this.selectors.locationName).textContent();
      if (cardName.trim() === name) {
        await cards.nth(i).click();
        await this.page.waitForTimeout(1000);
        return;
      }
    }

    throw new Error(`Location card with name "${name}" not found`);
  }

  // ===========================================
  // NAVIGATION VERIFICATION
  // ===========================================

  async verifyNavigationToCityDetails(cityName) {
    await this.page.waitForURL(`**/app/location-analytics/city/${encodeURIComponent(cityName)}`, {
      timeout: 10000
    });
    return this.page.url().includes(`/app/location-analytics/city/${encodeURIComponent(cityName)}`);
  }

  async verifyNavigationToCountryDetails(countryName) {
    await this.page.waitForURL(`**/app/location-analytics/country/${encodeURIComponent(countryName)}`, {
      timeout: 10000
    });
    return this.page.url().includes(`/app/location-analytics/country/${encodeURIComponent(countryName)}`);
  }

  // ===========================================
  // TOAST OPERATIONS
  // ===========================================

  async waitForSuccessToast() {
    await this.page.waitForSelector(this.selectors.successToast, {
      state: 'visible',
      timeout: 5000
    });
  }

  async waitForErrorToast() {
    await this.page.waitForSelector(this.selectors.errorToast, {
      state: 'visible',
      timeout: 5000
    });
  }

  async getToastMessage() {
    try {
      const toast = this.page.locator(`${this.selectors.successToast}, ${this.selectors.errorToast}`);
      await toast.waitFor({ state: 'visible', timeout: 3000 });
      return await toast.locator(this.selectors.toastDetail).textContent();
    } catch {
      return null;
    }
  }

  // ===========================================
  // RESPONSIVE BEHAVIOR
  // ===========================================

  async setMobileViewport() {
    await this.page.setViewportSize({ width: 375, height: 667 });
    await this.page.waitForTimeout(500);
  }

  async setTabletViewport() {
    await this.page.setViewportSize({ width: 768, height: 1024 });
    await this.page.waitForTimeout(500);
  }

  async setDesktopViewport() {
    await this.page.setViewportSize({ width: 1920, height: 1080 });
    await this.page.waitForTimeout(500);
  }

  // ===========================================
  // UTILITY METHODS
  // ===========================================

  async reload() {
    await this.page.reload();
    await this.waitForPageLoad();
  }

  async verifyPageTitle() {
    const title = await this.page.locator(this.selectors.pageTitle).textContent();
    return title.includes('Location Analytics');
  }

  async verifyPageSubtitle() {
    const subtitle = await this.page.locator(this.selectors.pageSubtitle).textContent();
    return subtitle.includes('Explore your visits by city and country');
  }

  // ===========================================
  // HELPER: Login and navigate
  // ===========================================

  async loginAndNavigate(userData) {
    const LoginPage = (await import('./LoginPage.js')).LoginPage;
    const TestData = (await import('../fixtures/test-data.js')).TestData;
    const UserFactory = (await import('../utils/user-factory.js')).UserFactory;

    const loginPage = new LoginPage(this.page);
    const testUser = userData || TestData.users.existing;

    await UserFactory.createUser(this.page, testUser);
    await loginPage.navigate();
    await loginPage.login(testUser.email, testUser.password);

    // Wait for redirect to timeline
    await this.page.waitForURL('**/app/timeline', { timeout: 10000 });

    // Navigate to location analytics
    await this.navigate();
    await this.waitForPageLoad();

    return { testUser };
  }
}
