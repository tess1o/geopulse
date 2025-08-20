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
    await this.page.waitForURL('**/app/timeline**');
    await this.page.waitForLoadState('networkidle');
  }
}