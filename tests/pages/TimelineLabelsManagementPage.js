import { expect } from '@playwright/test';

export class TimelineLabelsManagementPage {
  constructor(page) {
    this.page = page;

    this.selectors = {
      pageTitle: 'h1:has-text("Timeline Labels")',
      createButton: 'button:has-text("Create Label")',
      tableRows: '.p-datatable-tbody tr',
      createDialog: '.p-dialog:has-text("Create Timeline Label")',
      editDialog: '.p-dialog:has-text("Edit Timeline Label")',
      dateRangeInput: '.p-dialog:visible input[id="dateRange_input"], .p-dialog:visible #dateRange input, .p-dialog:visible #dateRange',
      labelNameInput: '.p-dialog:visible input#labelName',
      confirmAccept: '.p-confirmdialog-accept-button',
      tripPlanLink: '.trip-plan-link',
      // Row actions live in a popup Menu opened from the overflow trigger
      rowActionsTrigger: '.actions-inline-row button:has(.pi-ellipsis-v)',
      rowActionsMenu: '.p-menu.p-menu-overlay:visible',
      rowActionsMenuItem: '.p-menu-item:not(.p-disabled)',
      rowActionsMenuItemContent: '.p-menu-item-content',
      rowActionsMenuItemLabel: '.p-menu-item-label',
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

  // The row action menu is a popup rendered on <body> only while open, so it is
  // never a descendant of the row. Enabled items carry data-p-disabled="false";
  // disabled ones have no pointer-events:none and swallow the click silently,
  // so callers must assert the item is enabled instead of relying on the click.
  getRowActionsMenu() {
    return this.page.locator(this.selectors.rowActionsMenu).last();
  }

  rowActionItem(actionLabel) {
    return this.getRowActionsMenu()
      .locator(this.selectors.rowActionsMenuItem)
      .filter({ has: this.page.locator(`.p-menu-item-label:text-is("${actionLabel}")`) });
  }

  async openRowActions(rowOrName) {
    const row = typeof rowOrName === 'string' ? this.rowByLabelName(rowOrName) : rowOrName;
    await expect(row).toBeVisible({ timeout: 10000 });

    await row.locator(this.selectors.rowActionsTrigger).click();
    await this.getRowActionsMenu().waitFor({ state: 'visible', timeout: 5000 });

    return row;
  }

  async isRowActionAvailable(actionLabel) {
    return (await this.rowActionItem(actionLabel).count()) > 0;
  }

  async clickRowAction(rowOrName, actionLabel) {
    await this.openRowActions(rowOrName);

    const item = this.rowActionItem(actionLabel);
    await expect(
      item,
      `Row action "${actionLabel}" is missing or disabled for this label`
    ).toHaveCount(1, { timeout: 5000 });

    await item.locator(this.selectors.rowActionsMenuItemContent).click();
    // Selecting an item closes the overlay; wait so the next step does not race it.
    await this.getRowActionsMenu().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  }

  // The menu items depend on the trips store, which loads in parallel with the
  // labels, so a linked row can still render "Not linked" on first paint.
  async waitForLinkedTripResolved(labelName) {
    const row = this.rowByLabelName(labelName);
    await expect(row).toBeVisible({ timeout: 10000 });
    await expect(row.locator(this.selectors.tripPlanLink)).toContainText(labelName, { timeout: 10000 });
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
    await this.page.fill(this.selectors.labelNameInput, name);
    await this.selectDateRangeByIndex(startIndex, endIndex);
    await this.closeDatePickerPanelIfOpen();
    await this.page.locator('.p-dialog:visible button:has-text("Create")').click();
    await this.page.waitForSelector(this.selectors.createDialog, { state: 'hidden', timeout: 10000 });
    await expect(this.rowByLabelName(name)).toBeVisible({ timeout: 10000 });
  }

  async editLabel(oldName, { newName, startIndex = 8, endIndex = 12, randomizeColor = true }) {
    await this.clickRowAction(oldName, 'Edit Label');
    await this.page.waitForSelector(this.selectors.editDialog, { timeout: 5000 });

    if (newName) {
      await this.page.fill(this.selectors.labelNameInput, newName);
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
    await this.clickRowAction(labelName, 'Create Trip Plan');
  }

  async unlinkLabelFromTrip(labelName) {
    await this.waitForLinkedTripResolved(labelName);
    await this.clickRowAction(labelName, 'Unlink Trip Plan');
    await this.page.waitForSelector(this.selectors.confirmAccept, { timeout: 5000 });
    await this.page.click(this.selectors.confirmAccept);
  }

  async deleteLabel(labelName, mode = 'standalone') {
    if (mode !== 'standalone') {
      await this.waitForLinkedTripResolved(labelName);
    }

    // The menu item is labelled "Delete Label" in both modes; the mode only
    // changes which confirmation the app shows afterwards.
    await this.clickRowAction(labelName, 'Delete Label');

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
