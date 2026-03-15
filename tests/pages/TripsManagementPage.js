import { expect } from '@playwright/test';

export class TripsManagementPage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      pageTitle: 'h1:has-text("Trip Plans")',
      createButton: 'button:has-text("Create Trip Plan")',
      fromLabelButton: 'button:has-text("From Timeline Label")',
      tableRows: '.p-datatable-tbody tr',
      searchInput: '.search-input input, .search-input',
      statusSelect: '.status-select',
      tripNameInput: '.p-dialog:visible input#tripName',
      dateRangeInput: '.p-dialog:visible input[id="tripDateRange_input"], .p-dialog:visible #tripDateRange input, .p-dialog:visible #tripDateRange',
      confirmAccept: '.p-confirmdialog-accept-button',
    };
  }

  async navigate() {
    await this.page.goto('/app/trips');
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 10000 });
    await this.page.waitForSelector('.desktop-table, .p-datatable', { timeout: 10000 });
  }

  rowByTripName(name) {
    return this.page.locator(this.selectors.tableRows).filter({ hasText: name }).first();
  }

  async getVisibleTripNames() {
    const names = [];
    const links = this.page.locator('.trip-name-link');
    const count = await links.count();

    for (let i = 0; i < count; i += 1) {
      names.push((await links.nth(i).innerText()).trim());
    }

    return names;
  }

  async selectDateRangeByIndex(startIndex = 2, endIndex = 6) {
    await this.page.locator(this.selectors.dateRangeInput).first().click({ force: true });
    const pickerPanel = this.page.locator('.p-datepicker-panel:visible').first();
    await pickerPanel.waitFor({ state: 'visible', timeout: 5000 });

    const dayButtons = pickerPanel.locator(
      '.p-datepicker-day-view td .p-datepicker-day[aria-disabled="false"]'
    );
    const availableDays = await dayButtons.count();
    if (availableDays < 2) {
      throw new Error('Not enough selectable days in DatePicker');
    }

    const safeStart = Math.min(startIndex, availableDays - 2);
    const safeEnd = Math.min(Math.max(endIndex, safeStart + 1), availableDays - 1);

    const startButton = dayButtons.nth(safeStart);
    const endButton = dayButtons.nth(safeEnd);

    await startButton.click();
    await endButton.click();
    await this.closeDatePickerPanelIfOpen();
  }

  async closeDatePickerPanelIfOpen() {
    const panel = this.page.locator('.p-datepicker-panel:visible').first();
    const isVisible = await panel.isVisible().catch(() => false);
    if (!isVisible) return;

    const dialogHeader = this.page.locator('.p-dialog:visible .p-dialog-header').first();
    if (await dialogHeader.isVisible().catch(() => false)) {
      await dialogHeader.click({ force: true });
      const hiddenAfterHeaderClick = await panel.waitFor({ state: 'hidden', timeout: 1500 }).then(() => true).catch(() => false);
      if (hiddenAfterHeaderClick) return;
    }

    const dateInput = this.page.locator(this.selectors.dateRangeInput).first();
    if (await dateInput.isVisible().catch(() => false)) {
      await dateInput.click({ force: true });
      const hiddenAfterInputToggle = await panel.waitFor({ state: 'hidden', timeout: 1000 }).then(() => true).catch(() => false);
      if (hiddenAfterInputToggle) return;
    }

    await this.page.mouse.click(8, 8).catch(() => {});
    await panel.waitFor({ state: 'hidden', timeout: 3000 });
  }

  async createTripPlan({ name, notes = '', startIndex = 2, endIndex = 6 }) {
    await this.page.click(this.selectors.createButton);
    await this.page.waitForSelector('.p-dialog:visible:has-text("Create Trip Plan")', { timeout: 5000 });

    await this.page.fill(this.selectors.tripNameInput, name);
    if (notes) {
      await this.page.fill('.p-dialog:visible textarea#tripNotes', notes);
    }
    await this.selectDateRangeByIndex(startIndex, endIndex);
    await this.closeDatePickerPanelIfOpen();

    await this.page.locator('.p-dialog:visible button:has-text("Create Trip")').click();
    await this.page.waitForSelector('.p-dialog:has-text("Create Trip Plan")', { state: 'hidden', timeout: 10000 });
    await expect(this.rowByTripName(name)).toBeVisible({ timeout: 10000 });
  }

  async editTripPlan(currentName, { newName, startIndex = 8, endIndex = 12, randomizeColor = true }) {
    const row = this.rowByTripName(currentName);
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.locator('.trip-actions-row button:has(.pi-pencil)').click();
    await this.page.waitForSelector('.p-dialog:visible:has-text("Edit Trip Plan")', { timeout: 5000 });

    if (newName) {
      await this.page.fill(this.selectors.tripNameInput, newName);
    }

    await this.selectDateRangeByIndex(startIndex, endIndex);
    await this.closeDatePickerPanelIfOpen();

    if (randomizeColor) {
      await this.page.locator('.p-dialog:visible button:has-text("Random")').click();
    }

    await this.page.locator('.p-dialog:visible button:has-text("Update Trip")').click();
    await this.page.waitForSelector('.p-dialog:has-text("Edit Trip Plan")', { state: 'hidden', timeout: 10000 });

    if (newName) {
      await expect(this.rowByTripName(newName)).toBeVisible({ timeout: 10000 });
    }
  }

  async setSearchTerm(value) {
    await this.page.fill(this.selectors.searchInput, value);
    await this.page.waitForTimeout(500);
  }

  async setStatusFilter(label) {
    await this.page.click(this.selectors.statusSelect);
    await this.page.locator('.p-select-option', { hasText: label }).click();
    await this.page.waitForTimeout(500);
  }

  async createTripFromTimelineLabel(labelName) {
    await this.page.click(this.selectors.fromLabelButton);
    await this.page.waitForSelector('.p-dialog:visible:has-text("Create Trip Plan from Timeline Label")', { timeout: 5000 });

    await this.page.locator('.p-dialog:visible .p-select').click();
    await this.page.locator('.p-select-option', { hasText: labelName }).first().click();

    await this.page.locator('.p-dialog:visible button:has-text("Create Trip Plan")').click();
    await this.page.waitForSelector('.p-dialog:has-text("Create Trip Plan from Timeline Label")', { state: 'hidden', timeout: 10000 });
  }

  async openTripPlanner(name) {
    const row = this.rowByTripName(name);
    await expect(row).toBeVisible({ timeout: 10000 });
    await row.locator('.trip-actions-row button:has(.pi-briefcase)').click();
  }

  async unlinkTripFromLabel(name) {
    const row = this.rowByTripName(name);
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.locator('.trip-actions-row button:has(.pi-link)').click();
    await this.page.waitForSelector(this.selectors.confirmAccept, { timeout: 5000 });
    await this.page.click(this.selectors.confirmAccept);
  }

  async deleteTrip(name, mode = 'standalone') {
    const row = this.rowByTripName(name);
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.locator('.trip-actions-row button:has(.pi-trash)').click();

    if (mode === 'standalone') {
      await this.page.waitForSelector(this.selectors.confirmAccept, { timeout: 5000 });
      await this.page.click(this.selectors.confirmAccept);
      return;
    }

    const buttonLabel = mode === 'delete_both' ? 'Delete Trip Plan + Label' : 'Delete Trip Plan Only';
    await this.page.locator(`.p-dialog:visible button:has-text("${buttonLabel}")`).click();
    await this.page.waitForSelector('.p-dialog:has-text("Delete Linked Trip Plan")', { state: 'hidden', timeout: 10000 });
  }
}
