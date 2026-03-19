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
    const field = this.page.locator('.field').filter({
      has: this.page.locator('label', { hasText: fieldLabel })
    }).first();
    await field.locator('.p-select').click();
    await this.page.waitForSelector('.p-select-overlay:visible', { timeout: 5000 });
  }

  async selectOptionInOpenSelect(optionText) {
    const option = this.page.locator('.p-select-overlay:visible .p-select-option').filter({
      hasText: optionText
    }).first();
    await option.click();
    await this.page.waitForTimeout(200);
  }

  async getOpenSelectOptionLabels() {
    const options = this.page.locator('.p-select-overlay:visible .p-select-option');
    return await options.allTextContents();
  }

  async closeOpenSelect() {
    await this.page.keyboard.press('Escape');
    await this.page.waitForTimeout(150);
  }

  async setRuleSubject(subjectLabel) {
    await this.openSelectForField('Subject');
    await this.selectOptionInOpenSelect(subjectLabel);
  }

  async clickStartRectangleDraw() {
    await this.page.getByRole('button', { name: 'Draw Rectangle on Map' }).click();
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
    const field = this.page.locator('.field').filter({
      has: this.page.locator('label', { hasText: 'Cooldown (seconds)' })
    }).first();
    await field.locator('input').fill(String(seconds));
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

  async fillTemplateName(name) {
    await this.page.locator('input[placeholder="Telegram Enter Alert"]').fill(name);
  }

  async fillTemplateDestination(destination) {
    const destinationField = this.page.locator('textarea[placeholder*="tgram://TOKEN/CHAT_ID"]').first();
    await destinationField.fill(destination);
  }

  async fillTemplateTitle(title) {
    await this.page.locator('input[placeholder*="{{subjectName}} {{eventVerb}} {{geofenceName}}"]').fill(title);
  }

  async fillTemplateBody(body) {
    const field = this.page.locator('.field.wide').filter({
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

  async markAllEventsSeen() {
    await this.page.getByRole('button', { name: 'Mark all seen' }).first().click();
  }

  async eventRowExists(text) {
    const row = this.page.locator(this.selectors.tableRows).filter({ hasText: text });
    return await row.count() > 0;
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
    const label = this.page.locator('label', { hasText: labelText }).first();
    await expect(label).toBeVisible({ timeout: 10000 });

    const field = label.locator('xpath=ancestor::div[contains(@class,"field")]').first();
    await field.scrollIntoViewIfNeeded();

    const switchRoot = field.locator('.p-inputswitch, .p-toggleswitch').first();
    if (await switchRoot.isVisible().catch(() => false)) {
      await switchRoot.click();
      return;
    }

    const checkbox = field.locator('input[type="checkbox"]').first();
    await expect(checkbox).toBeAttached({ timeout: 5000 });
    await checkbox.click({ force: true });
  }
}
