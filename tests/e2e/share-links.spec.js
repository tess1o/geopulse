import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {ShareLinksPage} from '../pages/ShareLinksPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';

test.describe('Share Links Management', () => {

  test.describe('Initial State and Empty State', () => {
    test('should show empty state when no share links exist', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();
      await shareLinksPage.waitForLoadingComplete();

      // Verify empty state
      expect(await shareLinksPage.hasEmptyState()).toBe(true);

      // Verify database has no share links
      const user = await dbManager.getUserByEmail(testUser.email);
      expect(await ShareLinksPage.countShareLinks(dbManager, user.id)).toBe(0);
    });

    test('should show create button with menu in empty state', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Click create button to open menu (waits for menu to open)
      await shareLinksPage.clickCreateFirstLink();

      // Verify menu items are visible
      const liveLocationMenuItem = page.locator(shareLinksPage.selectors.liveLocationMenuItem);
      const timelineMenuItem = page.locator(shareLinksPage.selectors.timelineMenuItem);

      expect(await liveLocationMenuItem.isVisible()).toBe(true);
      expect(await timelineMenuItem.isVisible()).toBe(true);
    });
  });

  test.describe('Create Live Location Shares', () => {
    test('should create basic live location share with current location only', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Open menu and select Live Location
      await shareLinksPage.clickCreateFirstLink();
      await shareLinksPage.selectLiveLocationShare();
      await shareLinksPage.waitForDialogToOpen();

      // Fill form
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);

      await shareLinksPage.fillLinkForm({
        name: 'Current Location Only',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: false
      });

      await shareLinksPage.submitCreateForm();
      await shareLinksPage.waitForSuccessToast('created');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see the new link
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database
      const links = await ShareLinksPage.getShareLinksByUserId(dbManager, user.id);
      expect(links.length).toBe(1);
      expect(links[0].name).toBe('Current Location Only');
      expect(links[0].show_history).toBe(false);
      expect(links[0].share_type).toBe('LIVE_LOCATION');

      // Verify UI
      expect(await shareLinksPage.hasLiveLocationSharesSection()).toBe(true);
      expect(await shareLinksPage.getLiveLocationSharesCount()).toBeGreaterThanOrEqual(1);
    });

    test('should create live location share with history and hours', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      await shareLinksPage.clickCreateFirstLink();
      await shareLinksPage.selectLiveLocationShare();
      await shareLinksPage.waitForDialogToOpen();

      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 14);

      // Fill form with history enabled
      await page.locator(shareLinksPage.selectors.nameInput).fill('With 48h History');
      await page.locator(shareLinksPage.selectors.withHistoryRadio).click();

      // Set history hours
      await page.locator(shareLinksPage.selectors.historyHoursInput + ' input').fill('48');

      // Set expiration
      const calendarInput = page.locator(`${shareLinksPage.selectors.expiresAtCalendar} input`);
      await calendarInput.click();
      await calendarInput.press('Control+A');
      await calendarInput.press('Backspace');

      const month = String(expiresAt.getMonth() + 1).padStart(2, '0');
      const day = String(expiresAt.getDate()).padStart(2, '0');
      const year = String(expiresAt.getFullYear()).slice(-2);
      const hours = '12';
      const minutes = '00';

      await calendarInput.fill(`${month}/${day}/${year} ${hours}:${minutes}`);
      await calendarInput.press('Tab');
      await page.waitForTimeout(300);

      await shareLinksPage.submitCreateForm();
      await shareLinksPage.waitForSuccessToast('created');
      await shareLinksPage.waitForToastToDisappear();

      // Reload
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database
      const links = await ShareLinksPage.getShareLinksByUserId(dbManager, user.id);
      const createdLink = links.find(l => l.name === 'With 48h History');
      expect(createdLink).toBeTruthy();
      expect(createdLink.show_history).toBe(true);
      expect(createdLink.history_hours).toBe(48);

      // Verify UI shows history
      const historyValue = await shareLinksPage.getLinkSetting('With 48h History', 'Show History');
      expect(historyValue).toContain('48h');
    });

    test('should create password-protected live location share', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      await shareLinksPage.clickCreateFirstLink();
      await shareLinksPage.selectLiveLocationShare();
      await shareLinksPage.waitForDialogToOpen();

      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);

      await shareLinksPage.fillLinkForm({
        name: 'Protected Live Location',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: true,
        password: 'secret123'
      });

      await shareLinksPage.submitCreateForm();
      await shareLinksPage.waitForSuccessToast('created');
      await shareLinksPage.waitForToastToDisappear();

      // Reload
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database
      const links = await ShareLinksPage.getShareLinksByUserId(dbManager, user.id);
      const protectedLink = links.find(l => l.name === 'Protected Live Location');
      expect(protectedLink.password).toBeTruthy();

      // Verify UI shows password protected
      expect(await shareLinksPage.isPasswordProtected('Protected Live Location')).toBe(true);
    });
  });

  test.describe('Create Timeline Shares', () => {
    test('should open timeline share dialog with correct fields', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      await shareLinksPage.clickCreateFirstLink();
      await shareLinksPage.selectTimelineShare();
      await shareLinksPage.waitForTimelineDialogToOpen();

      // Verify dialog is open
      expect(await shareLinksPage.isTimelineDialogVisible()).toBe(true);

      // Verify dialog title
      const dialogTitle = await page.locator('.p-dialog-header .p-dialog-title').textContent();
      expect(dialogTitle).toContain('Timeline Share');

      // Verify key form fields are present
      expect(await page.locator('#name').isVisible()).toBe(true);
      expect(await page.locator('#start-date').isVisible()).toBe(true);
      expect(await page.locator('#end-date').isVisible()).toBe(true);
      expect(await page.locator('#show-current').isVisible()).toBe(true);
      expect(await page.locator('#show-photos').isVisible()).toBe(true);

      // Close dialog
      await page.locator('.p-dialog-close-button').click();
      await page.waitForTimeout(500);
    });

    test('should display timeline shares created via database', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Create timeline share via database
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 7);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      await ShareLinksPage.insertTimelineShareLink(dbManager, {
        id: '12341234-1234-1234-1234-123412341234',
        user_id: user.id,
        name: 'Database Timeline',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString(),
        show_current_location: true,
        show_photos: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify timeline section appears
      expect(await shareLinksPage.hasTimelineSharesSection()).toBe(true);

      // Verify the link is displayed
      const linkCard = await shareLinksPage.getLinkCardByName('Database Timeline');
      expect(await linkCard.isVisible()).toBe(true);
    });
  });

  test.describe('Display and Organization', () => {
    test('should display timeline and live location shares in separate sections', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert both types of shares
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 30);

      await ShareLinksPage.insertShareLink(dbManager, {
        id: '11111111-1111-1111-1111-111111111111',
        user_id: user.id,
        name: 'Live Location Link',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      const timelineStart = new Date();
      timelineStart.setDate(timelineStart.getDate() - 7);
      const timelineEnd = new Date();
      timelineEnd.setDate(timelineEnd.getDate() + 7);

      await ShareLinksPage.insertTimelineShareLink(dbManager, {
        id: '22222222-2222-2222-2222-222222222222',
        user_id: user.id,
        name: 'Timeline Share',
        start_date: timelineStart.toISOString(),
        end_date: timelineEnd.toISOString(),
        expires_at: expiresAt.toISOString()
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify both sections exist
      expect(await shareLinksPage.hasTimelineSharesSection()).toBe(true);
      expect(await shareLinksPage.hasLiveLocationSharesSection()).toBe(true);

      // Verify each link is in the correct section
      const timelineCard = await shareLinksPage.getLinkCardByName('Timeline Share');
      const liveLocationCard = await shareLinksPage.getLinkCardByName('Live Location Link');

      expect(await timelineCard.isVisible()).toBe(true);
      expect(await liveLocationCard.isVisible()).toBe(true);
    });

    test('should separate active and expired links within each section', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert active live location
      const activeExpiry = new Date();
      activeExpiry.setDate(activeExpiry.getDate() + 10);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '33333333-3333-3333-3333-333333333333',
        user_id: user.id,
        name: 'Active Live',
        expires_at: activeExpiry.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      // Insert expired live location
      const expiredDate = new Date();
      expiredDate.setDate(expiredDate.getDate() - 5);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '44444444-4444-4444-4444-444444444444',
        user_id: user.id,
        name: 'Expired Live',
        expires_at: expiredDate.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify live location section has both active and expired subsections
      expect(await shareLinksPage.isLinkActive('Active Live')).toBe(true);
      expect(await shareLinksPage.isLinkExpired('Expired Live')).toBe(true);
    });
  });

  test.describe('Edit Share Links', () => {
    test('should edit live location share name and settings', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 15);
      const insertedLink = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        user_id: user.id,
        name: 'Original Name',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Click edit
      await shareLinksPage.clickEditLink('Original Name');
      await shareLinksPage.waitForDialogToOpen();

      // Change name
      await shareLinksPage.fillLinkForm({
        name: 'Updated Name'
      });

      await shareLinksPage.submitUpdateForm();
      await shareLinksPage.waitForSuccessToast('updated');
      await shareLinksPage.waitForToastToDisappear();

      // Reload
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database
      const updatedLink = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(updatedLink.name).toBe('Updated Name');
    });
  });

  test.describe('Delete Share Links', () => {
    test('should delete live location share', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 15);
      const insertedLink = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        user_id: user.id,
        name: 'Delete Me',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify link exists
      expect(await shareLinksPage.getLiveLocationSharesCount()).toBeGreaterThanOrEqual(1);

      // Delete
      await shareLinksPage.deleteShareLink('Delete Me');
      await shareLinksPage.waitForSuccessToast('deleted');
      await shareLinksPage.waitForToastToDisappear();

      // Reload
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify deletion
      const afterDelete = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(afterDelete).toBeNull();
    });

    test('should delete timeline share', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert timeline share
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 7);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      const insertedLink = await ShareLinksPage.insertTimelineShareLink(dbManager, {
        id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
        user_id: user.id,
        name: 'Delete Timeline',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString()
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Delete
      await shareLinksPage.deleteShareLink('Delete Timeline');
      await shareLinksPage.waitForSuccessToast('deleted');
      await shareLinksPage.waitForToastToDisappear();

      // Reload
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify deletion
      const afterDelete = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(afterDelete).toBeNull();
    });

    test('should show confirmation dialog before deleting', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 15);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
        user_id: user.id,
        name: 'Confirm Delete',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Click delete
      await shareLinksPage.clickDeleteLink('Confirm Delete');
      await shareLinksPage.waitForDeleteDialogToOpen();

      // Verify dialog is visible
      expect(await shareLinksPage.isDeleteDialogVisible()).toBe(true);

      // Cancel
      await shareLinksPage.cancelDelete();
      await shareLinksPage.waitForDeleteDialogToClose();

      // Verify link still exists
      const card = await shareLinksPage.getLinkCardByName('Confirm Delete');
      expect(await card.isVisible()).toBe(true);
    });
  });

  test.describe('Copy to Clipboard', () => {
    test('should copy live location share URL', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await context.grantPermissions(['clipboard-read', 'clipboard-write']);

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 15);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        user_id: user.id,
        name: 'Copy Test',
        expires_at: expiresAt.toISOString(),
        share_type: 'LIVE_LOCATION',
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Click copy
      await shareLinksPage.clickCopyButton('Copy Test');

      // Verify toast
      await shareLinksPage.waitForSuccessToast('copied');
    });

    test('should copy timeline share URL', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await context.grantPermissions(['clipboard-read', 'clipboard-write']);

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert timeline share
      const now = new Date();
      const startDate = new Date(now);
      startDate.setDate(startDate.getDate() - 7);
      const endDate = new Date(now);
      endDate.setDate(endDate.getDate() + 7);
      const expiresAt = new Date(now);
      expiresAt.setDate(expiresAt.getDate() + 30);

      await ShareLinksPage.insertTimelineShareLink(dbManager, {
        id: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
        user_id: user.id,
        name: 'Timeline Copy Test',
        start_date: startDate.toISOString(),
        end_date: endDate.toISOString(),
        expires_at: expiresAt.toISOString()
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Get URL and verify it's a timeline URL
      const url = await shareLinksPage.getLinkUrl('Timeline Copy Test');
      expect(url).toContain('/shared-timeline/');

      // Click copy
      await shareLinksPage.clickCopyButton('Timeline Copy Test');

      // Verify toast
      await shareLinksPage.waitForSuccessToast('copied');
    });
  });
});
