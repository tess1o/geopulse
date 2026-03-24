import { expect } from '@playwright/test';

export class GeofencesPage {
  constructor(page) {
    this.page = page;
    this.selectors = {
      pageTitle: 'h1:has-text("Geofences")',
      map: '#geofence-rule-map',
      tableRows: '.p-datatable-tbody tr',
      successToast: '.p-toast-message-success',
      errorToast: '.p-toast-message-error',
      confirmAccept: '.p-confirmdialog-accept-button',
      confirmReject: '.p-confirmdialog-reject-button',
      bellTrigger: 'button[aria-label="Open notifications inbox"]',
      bellBadge: '.gp-bell-badge',
      bellPanel: '.gp-notification-panel',
      bellListItem: '.gp-notification-item',
      notificationToastSummary: '.gp-notification-toast-summary'
    };
  }

  async navigate() {
    await this.page.goto('/app/geofences');
  }

  async waitForPageLoad() {
    await this.page.waitForURL('**/app/geofences**');
    await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 10000 });
    await this.waitForMapReady();
  }

  async isOnGeofencesPage() {
    try {
      await this.page.waitForSelector(this.selectors.pageTitle, { timeout: 5000 });
      return true;
    } catch {
      return false;
    }
  }

  async waitForMapReady() {
    await this.page.waitForSelector(`${this.selectors.map}.leaflet-container`, { timeout: 10000 });
    await this.page.waitForSelector(`${this.selectors.map} .leaflet-tile-pane`, {
      state: 'attached',
      timeout: 10000
    });
    await this.page.waitForTimeout(1200);
  }

  async switchToTab(tabLabel) {
    const tab = this.page.locator('.p-tabmenu-item').filter({ hasText: tabLabel }).first();
    await tab.click();
    await this.page.waitForTimeout(300);
  }

  async fillRuleName(name) {
    await this.page.locator('input[placeholder="Home area"]').fill(name);
  }

  async openSelectForField(fieldLabel) {
    const normalizedLabel = fieldLabel === 'Subject' ? 'Subjects' : fieldLabel;
    const openOverlay = async () => {
      await this.page.waitForSelector('.p-select-overlay:visible, .p-multiselect-overlay:visible', { timeout: 5000 });
    };

    if (normalizedLabel === 'Enter Template' || normalizedLabel === 'Leave Template') {
      const templateSelect = this.page
        .locator('.rule-sentence-row')
        .nth(normalizedLabel === 'Enter Template' ? 0 : 1)
        .locator('.p-select')
        .first();
      await templateSelect.click();
      await openOverlay();
      return;
    }

    const field = this.page.locator('.field').filter({
      has: this.page.locator('label', { hasText: normalizedLabel })
    }).first();
    await expect(field).toBeVisible({ timeout: 10000 });

    const selectRoot = field.locator('.p-select, .p-multiselect').first();
    await selectRoot.click();
    await openOverlay();
  }

  async selectOptionInOpenSelect(optionText) {
    const option = this.page.locator([
      '.p-select-overlay:visible .p-select-option',
      '.p-multiselect-overlay:visible .p-multiselect-option'
    ].join(', ')).filter({
      hasText: optionText
    }).first();
    await option.click();
    await this.page.waitForTimeout(200);
  }

  async getOpenSelectOptionLabels() {
    const options = this.page.locator([
      '.p-select-overlay:visible .p-select-option',
      '.p-multiselect-overlay:visible .p-multiselect-option'
    ].join(', '));
    return await options.allTextContents();
  }

  async closeOpenSelect() {
    await this.page.keyboard.press('Escape');
    await this.page.waitForTimeout(150);
  }

  async setRuleSubject(subjectLabel) {
    await this.openSelectForField('Subjects');
    await this.selectOptionInOpenSelect(subjectLabel);
  }

  async clickStartRectangleDraw() {
    await this.page.getByRole('button', { name: /Draw Rectangle|Redraw Rectangle/i }).first().click();
  }

  async drawRectangle(startX, startY, endX, endY) {
    const mapContainer = this.page.locator(this.selectors.map);
    const boundingBox = await mapContainer.boundingBox();
    if (!boundingBox) {
      throw new Error('Geofence map is not visible');
    }

    const startAbsX = boundingBox.x + startX;
    const startAbsY = boundingBox.y + startY;
    const endAbsX = boundingBox.x + endX;
    const endAbsY = boundingBox.y + endY;

    await this.page.mouse.move(startAbsX, startAbsY);
    await this.page.waitForTimeout(100);
    await this.page.mouse.down();
    await this.page.waitForTimeout(120);

    const steps = 8;
    for (let i = 1; i <= steps; i += 1) {
      const x = startAbsX + ((endAbsX - startAbsX) * i / steps);
      const y = startAbsY + ((endAbsY - startAbsY) * i / steps);
      await this.page.mouse.move(x, y);
      await this.page.waitForTimeout(20);
    }

    await this.page.mouse.up();
    await this.page.waitForTimeout(450);
  }

  async waitForAreaSelected() {
    await this.page.locator('small.muted-text', { hasText: 'Selected area:' }).waitFor({
      state: 'visible',
      timeout: 10000
    });
  }

  async setRuleCooldown(seconds) {
    const inlineCooldownInput = this.page.locator('.rule-sentence-cooldown-input input').first();
    if (await inlineCooldownInput.isVisible().catch(() => false)) {
      await inlineCooldownInput.fill(String(seconds));
      return;
    }

    const legacyField = this.page.locator('.field').filter({
      has: this.page.locator('label', { hasText: 'Cooldown (seconds)' })
    }).first();
    await legacyField.locator('input').fill(String(seconds));
  }

  async saveRule() {
    const button = this.page.getByRole('button', { name: /Create Rule|Update Rule/ }).first();
    await expect(button).toBeVisible({ timeout: 10000 });
    await expect(button).toBeEnabled({ timeout: 10000 });
    await button.scrollIntoViewIfNeeded();

    const label = ((await button.textContent()) || '').trim();
    const method = label.includes('Update Rule') ? 'PATCH' : 'POST';
    const waitForSubmit = this.page.waitForResponse((response) => {
      const url = response.url();
      return url.includes('/api/geofences/rules')
        && response.request().method() === method;
    }, { timeout: 5000 }).catch(() => null);

    await button.click();

    const submitResponse = await waitForSubmit;
    if (!submitResponse) {
      const validationErrors = await this.page.locator('.error-text:visible').allTextContents();
      const errorMessage = validationErrors.length > 0
        ? validationErrors.join(' | ')
        : 'No visible validation errors.';
      throw new Error(`Rule form submit request was not sent after clicking save. ${errorMessage}`);
    }

    return submitResponse.status();
  }

  async editRule(ruleName) {
    const row = this.page.locator(this.selectors.tableRows).filter({ hasText: ruleName }).first();
    await row.locator('button:has(.pi-pencil)').click();
    await this.page.waitForTimeout(250);
  }

  async deleteRule(ruleName) {
    const row = this.page.locator(this.selectors.tableRows).filter({ hasText: ruleName }).first();
    await row.locator('button:has(.pi-trash)').click();
  }

  async ruleRowExists(ruleName) {
    const row = this.page.locator(this.selectors.tableRows).filter({ hasText: ruleName });
    return await row.count() > 0;
  }

  async waitForRuleRowState(ruleName, shouldExist = true, timeout = 10000) {
    await expect.poll(async () => await this.ruleRowExists(ruleName), { timeout }).toBe(shouldExist);
  }

  async fillTemplateName(name) {
    await this.page.locator('input[placeholder="Telegram Enter Alert"]').fill(name);
  }

  async fillTemplateDestination(destination) {
    const destinationField = await this.getTemplateDestinationField();
    if (!destinationField) {
      return;
    }
    await destinationField.fill(destination);
  }

  async fillTemplateTitle(title) {
    await this.page.locator('input[placeholder*="{{subjectName}} {{eventVerb}} {{geofenceName}}"]').fill(title);
  }

  async fillTemplateBody(body) {
    const field = this.page.locator('.field').filter({
      has: this.page.locator('label', { hasText: 'Body Template' })
    }).first();
    await field.locator('textarea').fill(body);
  }

  async toggleTemplateDefaultEnter() {
    await this.clickToggleByLabel('Default for Enter');
  }

  async saveTemplate() {
    const button = this.page.getByRole('button', { name: /Create Template|Update Template/ }).first();
    await expect(button).toBeVisible({ timeout: 10000 });
    await expect(button).toBeEnabled({ timeout: 10000 });
    await button.scrollIntoViewIfNeeded();

    const label = ((await button.textContent()) || '').trim();
    const method = label.includes('Update Template') ? 'PATCH' : 'POST';
    const waitForSubmit = this.page.waitForResponse((response) => {
      const url = response.url();
      return url.includes('/api/geofences/templates')
        && response.request().method() === method;
    }, { timeout: 5000 }).catch(() => null);

    await button.click();

    const submitResponse = await waitForSubmit;
    if (!submitResponse) {
      const validationErrors = await this.page.locator('.error-text:visible').allTextContents();
      const errorMessage = validationErrors.length > 0
        ? validationErrors.join(' | ')
        : 'No visible validation errors.';
      throw new Error(`Template form submit request was not sent after clicking save. ${errorMessage}`);
    }

    return submitResponse.status();
  }

  async editTemplate(templateName) {
    const row = this.page.locator(this.selectors.tableRows).filter({ hasText: templateName }).first();
    await row.locator('button:has(.pi-pencil)').click();
    await this.page.waitForTimeout(200);
  }

  async deleteTemplate(templateName) {
    const row = this.page.locator(this.selectors.tableRows).filter({ hasText: templateName }).first();
    await row.locator('button:has(.pi-trash)').click();
  }

  async templateRowExists(templateName) {
    const row = this.page.locator(this.selectors.tableRows).filter({ hasText: templateName });
    return await row.count() > 0;
  }

  async waitForTemplateRowState(templateName, shouldExist = true, timeout = 10000) {
    await expect.poll(async () => await this.templateRowExists(templateName), { timeout }).toBe(shouldExist);
  }

  async markAllEventsSeen() {
    await this.page.getByRole('button', { name: 'Mark all seen' }).first().click();
  }

  async expectEventsUnreadCleared(timeout = 10000) {
    const markAllButton = this.page.getByRole('button', { name: 'Mark all seen' }).first();
    await expect.poll(async () => await markAllButton.isDisabled().catch(() => false), { timeout }).toBe(true);

    const unreadTag = this.page.locator([
      '.events-desktop .table-header-left .p-tag',
      '.events-mobile .table-header-left .p-tag'
    ].join(', ')).filter({ hasText: 'unread' }).first();
    await expect(unreadTag).toBeHidden({ timeout: 2000 }).catch(() => {});
  }

  async eventRowExists(text) {
    const eventsDesktopRows = this.page.locator('.events-desktop .p-datatable-tbody tr');
    if (await eventsDesktopRows.count() > 0) {
      const visibleRowMatch = eventsDesktopRows.filter({ hasText: text });
      if (await visibleRowMatch.count() > 0) {
        return true;
      }

      await this.ensureEventsTitleColumnVisible();
      const rowAfterTitleColumn = this.page.locator('.events-desktop .p-datatable-tbody tr').filter({ hasText: text });
      return await rowAfterTitleColumn.count() > 0;
    }

    const mobileCard = this.page.locator('.events-mobile .event-card').filter({ hasText: text });
    return await mobileCard.count() > 0;
  }

  async waitForEventRowState(text, shouldExist = true, timeout = 10000) {
    await expect.poll(async () => await this.eventRowExists(text), { timeout }).toBe(shouldExist);
  }

  async openNotificationBell() {
    await this.page.locator(this.selectors.bellTrigger).click();
    await this.page.waitForSelector(this.selectors.bellPanel, { timeout: 5000 });
  }

  async closeNotificationBell() {
    await this.page.keyboard.press('Escape');
    await this.page.waitForTimeout(150);
  }

  async waitForBellBadgeCount(expectedCount, timeout = 20000) {
    await expect.poll(async () => {
      const badge = this.page.locator(this.selectors.bellBadge);
      if (!await badge.isVisible().catch(() => false)) {
        return 0;
      }
      const text = (await badge.textContent() || '').trim();
      return Number(text || 0);
    }, { timeout }).toBe(expectedCount);
  }

  async expectBellBadgeHidden(timeout = 10000) {
    await expect(this.page.locator(this.selectors.bellBadge)).toBeHidden({ timeout });
  }

  async waitForNotificationToast(summaryText, timeout = 20000) {
    await this.page.waitForSelector(
      `${this.selectors.notificationToastSummary}:has-text("${summaryText}")`,
      { timeout }
    );
  }

  async waitForNotificationItemVisible(title, timeout = 10000) {
    const itemTitle = this.page.locator('.gp-notification-item-title', { hasText: title }).first();
    await expect(itemTitle).toBeVisible({ timeout });
  }

  async waitForNotificationItemHidden(title, timeout = 10000) {
    const itemTitle = this.page.locator('.gp-notification-item-title', { hasText: title }).first();
    await expect(itemTitle).toBeHidden({ timeout });
  }

  async markNotificationSeenByTitle(title) {
    const item = this.page.locator('.gp-notification-item').filter({ hasText: title }).first();
    await expect(item).toBeVisible({ timeout: 10000 });
    await item.getByRole('button', { name: 'Mark seen' }).click();
  }

  async markAllNotificationsSeen() {
    const button = this.page.locator('.gp-notification-panel .gp-notification-footer')
      .getByRole('button', { name: 'Mark all seen' })
      .first();
    await expect(button).toBeVisible({ timeout: 10000 });
    await button.click();
  }

  async waitForSuccessToast(text = null) {
    const locator = text
      ? this.page.locator(this.selectors.successToast).filter({ hasText: text }).first()
      : this.page.locator(this.selectors.successToast).first();
    await locator.waitFor({ state: 'visible', timeout: 10000 });
  }

  async waitForErrorToast(text = null) {
    const locator = text
      ? this.page.locator(this.selectors.errorToast).filter({ hasText: text }).first()
      : this.page.locator(this.selectors.errorToast).first();
    await locator.waitFor({ state: 'visible', timeout: 10000 });
  }

  async acceptConfirmDialog() {
    await this.page.locator(this.selectors.confirmAccept).click();
  }

  async rejectConfirmDialog() {
    await this.page.locator(this.selectors.confirmReject).click();
  }

  async clickToggleByLabel(labelText) {
    const fieldLabel = this.page.locator('.field label', { hasText: labelText }).first();
    if (await fieldLabel.isVisible().catch(() => false)) {
      const field = fieldLabel.locator('xpath=ancestor::div[contains(@class,"field")]').first();
      await field.scrollIntoViewIfNeeded();
      const switchRoot = field.locator('.p-inputswitch, .p-toggleswitch').first();
      if (await switchRoot.isVisible().catch(() => false)) {
        await switchRoot.click();
        return;
      }

      const checkbox = field.locator('input[type="checkbox"]').first();
      await expect(checkbox).toBeAttached({ timeout: 5000 });
      await checkbox.click({ force: true });
      return;
    }

    const logicItem = this.page.locator('.logic-item').filter({ hasText: labelText }).first();
    await expect(logicItem).toBeVisible({ timeout: 10000 });
    await logicItem.scrollIntoViewIfNeeded();
    const logicSwitch = logicItem.locator('.p-inputswitch, .p-toggleswitch').first();
    await expect(logicSwitch).toBeVisible({ timeout: 5000 });
    await logicSwitch.click();
  }

  async ensureEventsTitleColumnVisible() {
    const titleHeader = this.page.locator('.events-desktop .p-datatable-thead th').filter({ hasText: 'Title' }).first();
    if (await titleHeader.isVisible().catch(() => false)) {
      return;
    }

    const columnPicker = this.page.locator('.filter-item--columns .p-multiselect').first();
    if (!await columnPicker.isVisible().catch(() => false)) {
      return;
    }

    await columnPicker.click();
    const titleOption = this.page
      .locator('.p-multiselect-overlay:visible .p-multiselect-option')
      .filter({ hasText: 'Title' })
      .first();
    await expect(titleOption).toBeVisible({ timeout: 5000 });

    const isSelected = await titleOption.evaluate((node) => node.classList.contains('p-multiselect-option-selected'));
    if (!isSelected) {
      await titleOption.click();
    }
    await this.closeOpenSelect();
  }

  async getTemplateDestinationField() {
    const destinationField = this.page.locator([
      'textarea[placeholder*="tgram://TOKEN/CHAT_ID"]',
      'textarea[placeholder*="discord://WEBHOOK_TOKEN"]'
    ].join(', ')).first();
    if (await destinationField.isVisible().catch(() => false)) {
      return destinationField;
    }

    const sendExternalLabel = this.page.locator('label[for="template-send-external"]').first();
    if (await sendExternalLabel.isVisible().catch(() => false)) {
      const sendExternalInput = this.page.locator('#template-send-external').first();
      const isChecked = await sendExternalInput.isChecked().catch(() => false);
      const isDisabled = await sendExternalInput.isDisabled().catch(() => true);
      if (!isChecked && !isDisabled) {
        await sendExternalLabel.click();
      }
      if (await destinationField.isVisible().catch(() => false)) {
        return destinationField;
      }
      await destinationField.waitFor({ state: 'visible', timeout: 2000 }).catch(() => null);
      if (await destinationField.isVisible().catch(() => false)) {
        return destinationField;
      }
    }

    return null;
  }
}
