import { expect } from '@playwright/test';

export class TripWorkspacePage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      pageTitle: '.workspace-page-title',
      map: '.leaflet-container',
      contextMenu: '.p-contextmenu:visible',
      stopRows: '.trip-rail .trip-stop',
      confirmAccept: '.p-confirmdialog-accept-button',
    };
  }

  async navigate(tripId) {
    await this.page.goto(`/app/trips/${tripId}`);
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 10000 });
    await this.page.waitForSelector(this.selectors.map, { timeout: 10000 });
  }

  // The workspace no longer has Overview/Plan tabs: every trip state shows the same
  // summary bar + map + stops rail. Only the rail's contents change.

  async getStopsHeading() {
    return (await this.page.locator('.trip-rail .trip-rail-title').first().textContent()).trim();
  }

  async getStopCount() {
    return this.page.locator(this.selectors.stopRows).count();
  }

  async isSummaryBarVisible() {
    return this.page.locator('.trip-summary-bar').isVisible().catch(() => false);
  }

  async getSummaryMetric(label) {
    const metric = this.page.locator('.trip-summary-metric').filter({ hasText: label }).first();
    await expect(metric).toBeVisible({ timeout: 10000 });
    return (await metric.locator('.trip-summary-value').innerText()).trim();
  }

  /** The Plan/Actual switch only renders once the trip has timeline or path data. */
  lensSwitch() {
    return this.page.locator('.trip-rail-lens');
  }

  async isLensSwitchVisible() {
    return this.lensSwitch().isVisible().catch(() => false);
  }

  async openLens(name) {
    await this.lensSwitch().getByText(name, { exact: true }).click();
  }

  async rightClickMapAtCenter() {
    const map = this.page.locator(this.selectors.map).first();
    await map.waitFor({ state: 'visible', timeout: 10000 });

    const box = await map.boundingBox();
    if (!box) {
      throw new Error('Map is not visible for right-click operation');
    }

    await map.click({
      button: 'right',
      position: {
        x: Math.floor(box.width * 0.5),
        y: Math.floor(box.height * 0.5),
      },
      force: true,
    });
  }

  async openPlanToVisitContextMenu() {
    const map = this.page.locator(this.selectors.map).first();
    await map.waitFor({ state: 'visible', timeout: 10000 });

    const box = await map.boundingBox();
    if (!box) {
      throw new Error('Map is not visible for context menu operation');
    }

    const planAction = this.page
      .locator('.p-contextmenu-item-label', { hasText: 'Plan to visit here' })
      .first();

    const candidateRatios = [
      [0.35, 0.35],
      [0.65, 0.35],
      [0.35, 0.65],
      [0.65, 0.65],
      [0.5, 0.25],
      [0.5, 0.75],
      [0.5, 0.5],
    ];

    for (const [xRatio, yRatio] of candidateRatios) {
      await this.page.keyboard.press('Escape').catch(() => {});

      await map.click({
        button: 'right',
        position: {
          x: Math.floor(box.width * xRatio),
          y: Math.floor(box.height * yRatio),
        },
        force: true,
      });

      const hasPlanAction = await planAction.isVisible({ timeout: 700 }).catch(() => false);
      if (hasPlanAction) {
        return;
      }

      await this.page.mouse.click(8, 8).catch(() => {});
    }

    throw new Error('Failed to open map menu with "Plan to visit here" action');
  }

  async addPlanItemFromMap({ title }) {
    await this.openPlanToVisitContextMenu();
    await this.page.locator('.p-contextmenu-item-label', { hasText: 'Plan to visit here' }).click();

    await this.page.waitForSelector('.p-dialog:visible:has-text("Add Plan Item")', { timeout: 10000 });
    if (title) {
      await this.page.fill('.p-dialog:visible input#planTitle', title);
    }
    await this.page.locator('.p-dialog:visible button:has-text("Create Item")').click();
    await this.page.waitForSelector('.p-dialog:has-text("Add Plan Item")', { state: 'hidden', timeout: 10000 });
  }

  rowByTitle(title) {
    return this.page.locator(this.selectors.stopRows).filter({ hasText: title }).first();
  }

  async editPlannedItem(currentTitle, nextTitle) {
    const row = this.rowByTitle(currentTitle);
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.locator('button:has(.pi-pencil)').click();
    await this.page.waitForSelector('.p-dialog:visible:has-text("Edit Plan Item")', { timeout: 5000 });
    await this.page.fill('.p-dialog:visible input#planTitle', nextTitle);
    await this.page.locator('.p-dialog:visible button:has-text("Update Item")').click();
    await this.page.waitForSelector('.p-dialog:has-text("Edit Plan Item")', { state: 'hidden', timeout: 10000 });
    await expect(this.rowByTitle(nextTitle)).toBeVisible({ timeout: 10000 });
  }

  async deletePlannedItem(title) {
    const row = this.rowByTitle(title);
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.locator('button:has(.pi-trash)').click();
    await this.page.waitForSelector(this.selectors.confirmAccept, { timeout: 5000 });
    await this.page.click(this.selectors.confirmAccept);
    await expect(this.rowByTitle(title)).toHaveCount(0, { timeout: 10000 });
  }

  /** Stops can only be confirmed as visited from the rail; there is no reject/reset action in the UI. */
  async markVisited(title) {
    const row = this.rowByTitle(title);
    await expect(row).toBeVisible({ timeout: 10000 });
    await row.getByRole('button', { name: 'Mark visited' }).click();
  }

  async hasMarkVisitedAction(title) {
    return this.rowByTitle(title).getByRole('button', { name: 'Mark visited' }).isVisible().catch(() => false);
  }

  async getStatusText(title) {
    const row = this.rowByTitle(title);
    await expect(row).toBeVisible({ timeout: 10000 });

    return (await row.locator('.trip-stop-status').innerText()).replace(/\s+/g, ' ').trim();
  }

  /** Status tag plus its subtext (e.g. "Visited" / "Confidence 96%"). */
  async getStopSummaryText(title) {
    const row = this.rowByTitle(title);
    await expect(row).toBeVisible({ timeout: 10000 });
    return (await row.locator('.trip-stop-main').innerText()).replace(/\s+/g, ' ').trim();
  }
}
