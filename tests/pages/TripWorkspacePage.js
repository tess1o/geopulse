import { expect } from '@playwright/test';

export class TripWorkspacePage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      pageTitle: '.workspace-page-title',
      map: '.leaflet-container',
      contextMenu: '.p-contextmenu:visible',
      tableRows: '.p-datatable-tbody tr',
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

  tabButton(name) {
    return this.page.locator('.workspace-tabs button', { hasText: name });
  }

  async openPlanTab() {
    const tab = this.tabButton('Plan');
    if (await tab.isVisible()) {
      await tab.click();
    }
  }

  async isOverviewTabVisible() {
    return this.tabButton('Overview').isVisible().catch(() => false);
  }

  async isPlanTabVisible() {
    return this.tabButton('Plan').isVisible().catch(() => false);
  }

  async getComparisonCardTitle() {
    return (await this.page.locator('.plan-card .workspace-title').first().innerText()).trim();
  }

  async isPlanningCalloutVisible(expectedText) {
    const callout = this.page.locator('.planning-callout');
    await expect(callout).toBeVisible({ timeout: 10000 });
    if (expectedText) {
      await expect(callout).toContainText(expectedText);
    }
  }

  async isMatchedStayColumnVisible() {
    return this.page.locator('th:has-text("Matched Stay")').isVisible().catch(() => false);
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
    return this.page.locator(this.selectors.tableRows).filter({ hasText: title }).first();
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

  async applyVisitAction(title, action) {
    const row = this.rowByTitle(title);
    await expect(row).toBeVisible({ timeout: 10000 });

    const iconByAction = {
      markVisited: '.pi-check',
      markNotVisited: '.pi-times',
      reset: '.pi-undo',
    };

    await row.locator(`button:has(${iconByAction[action]})`).click();
  }

  async getStatusText(title) {
    const row = this.rowByTitle(title);
    await expect(row).toBeVisible({ timeout: 10000 });

    const statusCellText = await row.locator('td').nth(await this._statusCellIndex()).innerText();
    return statusCellText.replace(/\s+/g, ' ').trim();
  }

  async _statusCellIndex() {
    const hasMatchedStay = await this.isMatchedStayColumnVisible();
    return hasMatchedStay ? 3 : 2;
  }

  async expectMatchedStayEvidence(title, matchedStayName, confidenceBadge = null) {
    const row = this.rowByTitle(title);
    await expect(row).toBeVisible({ timeout: 10000 });
    await expect(row).toContainText(matchedStayName);

    if (confidenceBadge) {
      await expect(row).toContainText(confidenceBadge);
    }
  }
}
