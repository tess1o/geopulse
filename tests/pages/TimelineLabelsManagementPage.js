import { expect } from '@playwright/test';

export class TimelineLabelsManagementPage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      pageTitle: 'h1:has-text("Timeline Labels")',
      createButton: 'button:has-text("Create Label")',
      tableRows: '.p-datatable-tbody tr',
      createDialog: '.p-dialog:has-text("Create Period Tag")',
      editDialog: '.p-dialog:has-text("Edit Period Tag")',
      dateRangeInput: '.p-dialog:visible input[id="dateRange_input"], .p-dialog:visible #dateRange input, .p-dialog:visible #dateRange',
      tagNameInput: '.p-dialog:visible input#tagName',
      confirmAccept: '.p-confirmdialog-accept-button',
      contextMenuOption: '.p-select-option',
    };
  }

  async navigate() {
    await this.page.goto('/app/timeline-labels');
  }

  async waitForPageLoad() {
    await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 10000 });
    await this.page.waitForSelector('.desktop-table', { timeout: 10000 });
  }

  rowByLabelName(labelName) {
    return this.page.locator(this.selectors.tableRows).filter({ hasText: labelName }).first();
  }

  async getVisibleLabelNames() {
    const names = [];
    const rows = this.page.locator(this.selectors.tableRows);
    const count = await rows.count();

    for (let i = 0; i < count; i += 1) {
      const text = await rows.nth(i).locator('td').nth(1).innerText();
      names.push(text.trim());
    }

    return names;
  }

  async openCreateDialog() {
    await this.page.click(this.selectors.createButton);
    await this.page.waitForSelector(this.selectors.createDialog, { timeout: 5000 });
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

  async createLabel({ name, startIndex = 2, endIndex = 6 }) {
    await this.openCreateDialog();
    await this.page.fill(this.selectors.tagNameInput, name);
    await this.selectDateRangeByIndex(startIndex, endIndex);
    await this.closeDatePickerPanelIfOpen();
    await this.page.locator('.p-dialog:visible button:has-text("Create")').click();
    await this.page.waitForSelector(this.selectors.createDialog, { state: 'hidden', timeout: 10000 });
    await expect(this.rowByLabelName(name)).toBeVisible({ timeout: 10000 });
  }

  async editLabel(oldName, { newName, startIndex = 8, endIndex = 12, randomizeColor = true }) {
    const row = this.rowByLabelName(oldName);
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.locator('.actions-inline-row button:has(.pi-pencil)').click();
    await this.page.waitForSelector(this.selectors.editDialog, { timeout: 5000 });

    if (newName) {
      await this.page.fill(this.selectors.tagNameInput, newName);
    }

    await this.selectDateRangeByIndex(startIndex, endIndex);
    await this.closeDatePickerPanelIfOpen();

    if (randomizeColor) {
      await this.page.locator('.p-dialog:visible button:has-text("Random")').click();
    }

    await this.page.locator('.p-dialog:visible button:has-text("Update")').click();
    await this.page.waitForSelector(this.selectors.editDialog, { state: 'hidden', timeout: 10000 });

    if (newName) {
      await expect(this.rowByLabelName(newName)).toBeVisible({ timeout: 10000 });
    }
  }

  async createTripPlanFromLabel(labelName) {
    const row = this.rowByLabelName(labelName);
    await expect(row).toBeVisible({ timeout: 10000 });
    await row.locator('.actions-inline-row button:has(.pi-briefcase)').click();
  }

  async unlinkLabelFromTrip(labelName) {
    const row = this.rowByLabelName(labelName);
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.locator('.actions-inline-row button:has(.pi-link)').click();
    await this.page.waitForSelector(this.selectors.confirmAccept, { timeout: 5000 });
    await this.page.click(this.selectors.confirmAccept);
  }

  async deleteLabel(labelName, mode = 'standalone') {
    const row = this.rowByLabelName(labelName);
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.locator('.actions-inline-row button:has(.pi-trash)').click();

    if (mode === 'standalone') {
      await this.page.waitForSelector(this.selectors.confirmAccept, { timeout: 5000 });
      await this.page.click(this.selectors.confirmAccept);
      return;
    }

    const buttonLabel = mode === 'delete_both' ? 'Delete Label + Trip Plan' : 'Delete Label Only';
    await this.page.locator(`.p-dialog:visible button:has-text("${buttonLabel}")`).click();
    await this.page.waitForSelector('.p-dialog:has-text("Delete Linked Timeline Label")', { state: 'hidden', timeout: 10000 });
  }
}
