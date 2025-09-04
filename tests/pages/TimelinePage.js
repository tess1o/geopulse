export class TimelinePage {
  constructor(page) {
    this.page = page;
  }

  /**
   * Check if currently on timeline page
   */
  async isOnTimelinePage() {
    const url = this.page.url();
    return /\/app\/timeline(\/|\?|#|$)/.test(url);
  }

  /**
   * Navigate to timeline page
   */
  async navigate() {
    await this.page.goto('/app/timeline');
  }

  /**
   * Wait for timeline page to load
   */
  async waitForPageLoad() {
    // Just wait for network to be idle instead of specific URL pattern
    await this.page.waitForLoadState('networkidle');

    // Give a small buffer for any async operations
    await this.page.waitForTimeout(1000);
  }
}