export class LocationSourcesPage {
  constructor(page) {
    this.page = page;
  }

  /**
   * Check if currently on location sources page
   */
  async isOnLocationSourcesPage() {
    try {
      await this.page.waitForURL('**/app/location-sources', { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Navigate to location sources page
   */
  async navigate() {
    await this.page.goto('/app/location-sources');
  }

  /**
   * Wait for location sources page to load
   */
  async waitForPageLoad() {
    await this.page.waitForURL('**/app/location-sources**');
    await this.page.waitForLoadState('networkidle');
  }
}