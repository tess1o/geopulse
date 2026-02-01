import {test, expect} from '../fixtures/database-fixture.js';
import {ShareLinksPage} from '../pages/ShareLinksPage.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';
import {DateFactory} from '../utils/date-factory.js';
import {ShareLinkFactory} from '../utils/share-link-factory.js';
import {TestConstants} from '../fixtures/test-constants.js';

test.describe('Share Links Management', () => {

    test.describe('Initial State and Empty State', () => {
        test('should show empty state when no share links exist', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);
            await shareLinksPage.waitForLoadingComplete();

            // Verify empty state
            expect(await shareLinksPage.hasEmptyState()).toBe(true);

            // Verify database has no share links
            expect(await ShareLinkFactory.countByUserId(dbManager, user.id)).toBe(0);
        });

        test('should show create button with menu in empty state', async ({page, dbManager}) => {
            const { shareLinksPage } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

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
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Open menu and select Live Location
            await shareLinksPage.clickCreateFirstLink();
            await shareLinksPage.selectLiveLocationShare();
            await shareLinksPage.waitForDialogToOpen();

            // Fill form
            await shareLinksPage.fillLinkForm({
                name: 'Current Location Only',
                showHistory: false,
                expiresAt: DateFactory.futureDate(7),
                hasPassword: false
            });

            await shareLinksPage.submitCreateForm();
            await shareLinksPage.waitForSuccessToast('created');
            await shareLinksPage.waitForToastToDisappear();

            // Reload to see the new link
            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify database
            const links = await ShareLinkFactory.getByUserId(dbManager, user.id);
            expect(links.length).toBe(1);
            expect(links[0].name).toBe('Current Location Only');
            expect(links[0].show_history).toBe(false);
            expect(links[0].share_type).toBe(TestConstants.SHARE_TYPES.LIVE_LOCATION);

            // Verify UI
            expect(await shareLinksPage.hasLiveLocationSharesSection()).toBe(true);
            expect(await shareLinksPage.getLiveLocationSharesCount()).toBeGreaterThanOrEqual(1);
        });

        test('should create live location share with history and hours', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            await shareLinksPage.clickCreateFirstLink();
            await shareLinksPage.selectLiveLocationShare();
            await shareLinksPage.waitForDialogToOpen();

            const expiresAt = DateFactory.futureDate(14);

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
            await page.waitForTimeout(TestConstants.TIMEOUTS.SHORT);

            await shareLinksPage.submitCreateForm();
            await shareLinksPage.waitForSuccessToast('created');
            await shareLinksPage.waitForToastToDisappear();

            // Reload
            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify database
            const links = await ShareLinkFactory.getByUserId(dbManager, user.id);
            const createdLink = links.find(l => l.name === 'With 48h History');
            expect(createdLink).toBeTruthy();
            expect(createdLink.show_history).toBe(true);
            expect(createdLink.history_hours).toBe(48);

            // Verify UI shows history
            const historyValue = await shareLinksPage.getLinkSetting('With 48h History', 'Show History');
            expect(historyValue).toContain('48h');
        });

        test('should create password-protected live location share', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            await shareLinksPage.clickCreateFirstLink();
            await shareLinksPage.selectLiveLocationShare();
            await shareLinksPage.waitForDialogToOpen();

            await shareLinksPage.fillLinkForm({
                name: 'Protected Live Location',
                showHistory: false,
                expiresAt: DateFactory.futureDate(7),
                hasPassword: true,
                password: TestConstants.PASSWORDS.secret123
            });

            await shareLinksPage.submitCreateForm();
            await shareLinksPage.waitForSuccessToast('created');
            await shareLinksPage.waitForToastToDisappear();

            // Reload
            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify database
            const links = await ShareLinkFactory.getByUserId(dbManager, user.id);
            const protectedLink = links.find(l => l.name === 'Protected Live Location');
            expect(protectedLink.password).toBeTruthy();

            // Verify UI shows password protected
            expect(await shareLinksPage.isPasswordProtected('Protected Live Location')).toBe(true);
        });
    });

    test.describe('Create Timeline Shares', () => {
        test('should open timeline share dialog with correct fields', async ({page, dbManager}) => {
            const { shareLinksPage } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

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
            await page.waitForTimeout(TestConstants.TIMEOUTS.SHORT);
        });

        test('should display timeline shares created via database', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Create timeline share via database
            const { startDate, endDate, expiresAt } = DateFactory.ranges.active();

            await ShareLinkFactory.createTimeline(dbManager, user.id, {
                id: '12341234-1234-1234-1234-123412341234',
                name: 'Database Timeline',
                dateRange: { startDate, endDate, expiresAt },
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

        test('should create timeline share via UI', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Create timeline share
            await shareLinksPage.clickCreateFirstLink();
            await shareLinksPage.selectTimelineShare();
            await shareLinksPage.waitForTimelineDialogToOpen();

            const { startDate, endDate, expiresAt } = DateFactory.ranges.active();

            await shareLinksPage.fillTimelineShareForm({
                name: 'UI Created Timeline',
                startDate: startDate,
                endDate: endDate,
                expiresAt: expiresAt,
                showCurrent: true,
                showPhotos: false
            });

            await shareLinksPage.submitTimelineShareForm();

            // Wait for dialog to close (indicating success)
            await shareLinksPage.waitForTimelineDialogToClose();
            await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

            // Reload to see the new link
            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify database
            const links = await ShareLinkFactory.getByUserId(dbManager, user.id);
            const createdLink = links.find(l => l.name === 'UI Created Timeline');
            expect(createdLink).toBeTruthy();
            expect(createdLink.share_type).toBe(TestConstants.SHARE_TYPES.TIMELINE);
            expect(createdLink.show_current_location).toBe(true);
            expect(createdLink.show_photos).toBe(false);

            // Verify UI
            expect(await shareLinksPage.hasTimelineSharesSection()).toBe(true);
            const linkCard = await shareLinksPage.getLinkCardByName('UI Created Timeline');
            expect(await linkCard.isVisible()).toBe(true);
        });

        test('should create password-protected timeline share via UI', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            await shareLinksPage.clickCreateFirstLink();
            await shareLinksPage.selectTimelineShare();
            await shareLinksPage.waitForTimelineDialogToOpen();

            const { startDate, endDate, expiresAt } = DateFactory.ranges.active();

            await shareLinksPage.fillTimelineShareForm({
                name: 'Protected Timeline Share',
                startDate: startDate,
                endDate: endDate,
                expiresAt: expiresAt,
                showCurrent: true,
                showPhotos: true,
                hasPassword: true,
                password: 'timeline123'
            });

            await shareLinksPage.submitTimelineShareForm();

            // Wait for dialog to close (indicating success)
            await shareLinksPage.waitForTimelineDialogToClose();
            await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

            // Reload
            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify database
            const links = await ShareLinkFactory.getByUserId(dbManager, user.id);
            const protectedLink = links.find(l => l.name === 'Protected Timeline Share');
            expect(protectedLink).toBeTruthy();
            expect(protectedLink.password).toBeTruthy();
            expect(protectedLink.show_photos).toBe(true);

            // Verify UI shows password protected
            expect(await shareLinksPage.isPasswordProtected('Protected Timeline Share')).toBe(true);
        });
    });

    test.describe('Display and Organization', () => {
        test('should display timeline and live location shares in separate sections', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert both types of shares
            await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_1,
                name: 'Live Location Link',
                expires_at: DateFactory.futureDate(30).toISOString()
            });

            const { startDate, endDate, expiresAt } = DateFactory.ranges.active();
            await ShareLinkFactory.createTimeline(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_2,
                name: 'Timeline Share',
                dateRange: { startDate, endDate, expiresAt }
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
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert active live location
            await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_3,
                name: 'Active Live',
                expires_at: DateFactory.futureDate(10).toISOString()
            });

            // Insert expired live location
            await ShareLinkFactory.createExpiredLiveLocation(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_4,
                name: 'Expired Live',
                expires_at: DateFactory.pastDate(5).toISOString()
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
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert link
            const insertedLink = await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_A,
                name: 'Original Name',
                expires_at: DateFactory.futureDate(15).toISOString()
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
            const updatedLink = await ShareLinkFactory.getById(dbManager, insertedLink.id);
            expect(updatedLink.name).toBe('Updated Name');
        });

        test('should edit timeline share name and dates', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert timeline share
            const { startDate, endDate, expiresAt } = DateFactory.ranges.active();
            const insertedLink = await ShareLinkFactory.createTimeline(dbManager, user.id, {
                id: 'f0f0f0f0-1111-1111-1111-111111111111',
                name: 'Original Timeline',
                dateRange: { startDate, endDate, expiresAt },
                show_photos: false
            });

            await shareLinksPage.navigate();
            await shareLinksPage.waitForPageLoad();

            // Click edit
            await shareLinksPage.clickEditLink('Original Timeline');
            await shareLinksPage.waitForTimelineDialogToOpen();

            // Change name and settings
            const newStartDate = DateFactory.pastDate(14);
            const newEndDate = DateFactory.futureDate(14);

            await shareLinksPage.fillTimelineShareForm({
                name: 'Updated Timeline',
                startDate: newStartDate,
                endDate: newEndDate,
                showPhotos: true
            });

            await shareLinksPage.submitTimelineShareForm();
            await shareLinksPage.waitForSuccessToast('updated');
            await shareLinksPage.waitForToastToDisappear();

            // Reload
            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify database
            const updatedLink = await ShareLinkFactory.getById(dbManager, insertedLink.id);
            expect(updatedLink.name).toBe('Updated Timeline');
            expect(updatedLink.show_photos).toBe(true);

            // Verify UI
            const linkCard = await shareLinksPage.getLinkCardByName('Updated Timeline');
            expect(await linkCard.isVisible()).toBe(true);
        });

        test('should edit live location to add password protection', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert link without password
            const insertedLink = await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
                id: 'a1a1a1a1-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                name: 'Add Password Test',
                expires_at: DateFactory.futureDate(15).toISOString(),
                password: null
            });

            await shareLinksPage.navigate();
            await shareLinksPage.waitForPageLoad();

            // Verify no password initially
            expect(await shareLinksPage.isPasswordProtected('Add Password Test')).toBe(false);

            // Edit to add password
            await shareLinksPage.clickEditLink('Add Password Test');
            await shareLinksPage.waitForDialogToOpen();

            await shareLinksPage.fillLinkForm({
                hasPassword: true,
                password: TestConstants.PASSWORDS.newpass123
            });

            await shareLinksPage.submitUpdateForm();
            await shareLinksPage.waitForSuccessToast('updated');
            await shareLinksPage.waitForToastToDisappear();

            // Reload
            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify database
            const updatedLink = await ShareLinkFactory.getById(dbManager, insertedLink.id);
            expect(updatedLink.password).toBeTruthy();

            // Verify UI
            expect(await shareLinksPage.isPasswordProtected('Add Password Test')).toBe(true);
        });

        test('should edit live location to enable history', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert link without history
            const insertedLink = await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
                id: 'b1b1b1b1-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
                name: 'Enable History Test',
                expires_at: DateFactory.futureDate(15).toISOString(),
                show_history: false
            });

            await shareLinksPage.navigate();
            await shareLinksPage.waitForPageLoad();

            // Edit to enable history
            await shareLinksPage.clickEditLink('Enable History Test');
            await shareLinksPage.waitForDialogToOpen();

            // Enable history with 72 hours
            await page.locator(shareLinksPage.selectors.withHistoryRadio).click();
            await page.locator(shareLinksPage.selectors.historyHoursInput + ' input').fill('72');

            await shareLinksPage.submitUpdateForm();
            await shareLinksPage.waitForSuccessToast('updated');
            await shareLinksPage.waitForToastToDisappear();

            // Reload
            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify database
            const updatedLink = await ShareLinkFactory.getById(dbManager, insertedLink.id);
            expect(updatedLink.show_history).toBe(true);
            expect(updatedLink.history_hours).toBe(72);

            // Verify UI shows history
            const historyValue = await shareLinksPage.getLinkSetting('Enable History Test', 'Show History');
            expect(historyValue).toContain('72h');
        });
    });

    test.describe('Delete Share Links', () => {
        test('should delete live location share', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert link
            const insertedLink = await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_B,
                name: 'Delete Me',
                expires_at: DateFactory.futureDate(15).toISOString()
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
            const afterDelete = await ShareLinkFactory.getById(dbManager, insertedLink.id);
            expect(afterDelete).toBeNull();
        });

        test('should delete timeline share', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert timeline share
            const { startDate, endDate, expiresAt } = DateFactory.ranges.active();
            const insertedLink = await ShareLinkFactory.createTimeline(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_C,
                name: 'Delete Timeline',
                dateRange: { startDate, endDate, expiresAt }
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
            const afterDelete = await ShareLinkFactory.getById(dbManager, insertedLink.id);
            expect(afterDelete).toBeNull();
        });

        test('should show confirmation dialog before deleting', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert link
            await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_D,
                name: 'Confirm Delete',
                expires_at: DateFactory.futureDate(15).toISOString()
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
            await context.grantPermissions(['clipboard-read', 'clipboard-write']);

            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert link
            await ShareLinkFactory.createLiveLocation(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_E,
                name: 'Copy Test',
                expires_at: DateFactory.futureDate(15).toISOString()
            });

            await shareLinksPage.navigate();
            await shareLinksPage.waitForPageLoad();

            // Click copy
            await shareLinksPage.clickCopyButton('Copy Test');

            // Verify toast
            await shareLinksPage.waitForSuccessToast('copied');
        });

        test('should copy timeline share URL', async ({page, dbManager, context}) => {
            await context.grantPermissions(['clipboard-read', 'clipboard-write']);

            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // Insert timeline share
            const { startDate, endDate, expiresAt } = DateFactory.ranges.active();
            await ShareLinkFactory.createTimeline(dbManager, user.id, {
                id: TestConstants.TEST_UUIDS.LINK_F,
                name: 'Timeline Copy Test',
                dateRange: { startDate, endDate, expiresAt }
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

    test.describe('Full CRUD Lifecycle', () => {
        test('should complete full lifecycle for live location share: create → edit → delete', async ({
                                                                                                          page,
                                                                                                          dbManager
                                                                                                      }) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // STEP 1: CREATE
            await shareLinksPage.clickCreateFirstLink();
            await shareLinksPage.selectLiveLocationShare();
            await shareLinksPage.waitForDialogToOpen();

            await shareLinksPage.fillLinkForm({
                name: 'Lifecycle Test Link',
                showHistory: false,
                expiresAt: DateFactory.futureDate(7),
                hasPassword: false
            });

            await shareLinksPage.submitCreateForm();
            await shareLinksPage.waitForSuccessToast('created');
            await shareLinksPage.waitForToastToDisappear();

            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify creation
            let links = await ShareLinkFactory.getByUserId(dbManager, user.id);
            let createdLink = links.find(l => l.name === 'Lifecycle Test Link');
            expect(createdLink).toBeTruthy();
            expect(createdLink.show_history).toBe(false);

            // STEP 2: EDIT
            await shareLinksPage.clickEditLink('Lifecycle Test Link');
            await shareLinksPage.waitForDialogToOpen();

            await shareLinksPage.fillLinkForm({
                name: 'Updated Lifecycle Link',
                showHistory: true
            });

            // Set history hours
            await page.locator(shareLinksPage.selectors.historyHoursInput + ' input').fill('48');

            await shareLinksPage.submitUpdateForm();
            await shareLinksPage.waitForSuccessToast('updated');
            await shareLinksPage.waitForToastToDisappear();

            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify edit
            const updatedLink = await ShareLinkFactory.getById(dbManager, createdLink.id);
            expect(updatedLink.name).toBe('Updated Lifecycle Link');
            expect(updatedLink.show_history).toBe(true);
            expect(updatedLink.history_hours).toBe(48);

            // STEP 3: DELETE
            await shareLinksPage.deleteShareLink('Updated Lifecycle Link');
            await shareLinksPage.waitForSuccessToast('deleted');
            await shareLinksPage.waitForToastToDisappear();

            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify deletion
            const deletedLink = await ShareLinkFactory.getById(dbManager, createdLink.id);
            expect(deletedLink).toBeNull();

            // Verify UI shows empty state
            expect(await shareLinksPage.hasEmptyState()).toBe(true);
        });

        test('should complete full lifecycle for timeline share: create → edit → delete', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            // STEP 1: CREATE
            await shareLinksPage.clickCreateFirstLink();
            await shareLinksPage.selectTimelineShare();
            await shareLinksPage.waitForTimelineDialogToOpen();

            const { startDate, endDate, expiresAt } = DateFactory.ranges.active();

            await shareLinksPage.fillTimelineShareForm({
                name: 'Timeline Lifecycle',
                startDate: startDate,
                endDate: endDate,
                expiresAt: expiresAt,
                showCurrent: true,
                showPhotos: false
            });

            await shareLinksPage.submitTimelineShareForm();
            await shareLinksPage.waitForTimelineDialogToClose();
            await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);

            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify creation
            let links = await ShareLinkFactory.getByUserId(dbManager, user.id);
            let createdLink = links.find(l => l.name === 'Timeline Lifecycle');
            expect(createdLink).toBeTruthy();
            expect(createdLink.share_type).toBe(TestConstants.SHARE_TYPES.TIMELINE);
            expect(createdLink.show_photos).toBe(false);

            // STEP 2: EDIT
            await shareLinksPage.clickEditLink('Timeline Lifecycle');
            await shareLinksPage.waitForTimelineDialogToOpen();

            await shareLinksPage.fillTimelineShareForm({
                name: 'Updated Timeline Lifecycle',
                showPhotos: true
            });

            await shareLinksPage.submitTimelineShareForm();
            await shareLinksPage.waitForSuccessToast('updated');
            await shareLinksPage.waitForToastToDisappear();

            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify edit
            const updatedLink = await ShareLinkFactory.getById(dbManager, createdLink.id);
            expect(updatedLink.name).toBe('Updated Timeline Lifecycle');
            expect(updatedLink.show_photos).toBe(true);

            // STEP 3: DELETE
            await shareLinksPage.deleteShareLink('Updated Timeline Lifecycle');
            await shareLinksPage.waitForSuccessToast('deleted');
            await shareLinksPage.waitForToastToDisappear();

            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify deletion
            const deletedLink = await ShareLinkFactory.getById(dbManager, createdLink.id);
            expect(deletedLink).toBeNull();
        });
    });

    test.describe('Access Control and Security', () => {
        test('should require authentication to access share management page', async ({page}) => {
            // Try to access share links page without login
            await page.goto('/app/share-links');

            // Should redirect to login
            await page.waitForTimeout(TestConstants.TIMEOUTS.MEDIUM);
            const url = page.url();
            expect(url).toContain('/login');
        });

        test('should only show own share links when logged in', async ({page, dbManager}) => {
            const shareLinksPage = new ShareLinksPage(page);

            // Create two users
            const { ownerData, owner, viewer } =
                await TestSetupHelper.createTwoUsers(page, dbManager, 'user1@test.com', 'user2@test.com');

            // Create share link for user1
            await ShareLinkFactory.createLiveLocation(dbManager, owner.id, {
                id: 'c1c1c1c1-1111-1111-1111-111111111111',
                name: 'User 1 Link'
            });

            // Create share link for user2
            await ShareLinkFactory.createLiveLocation(dbManager, viewer.id, {
                id: 'd1d1d1d1-2222-2222-2222-222222222222',
                name: 'User 2 Link'
            });

            // Login as user1
            await TestSetupHelper.loginExistingUser(page, ownerData);

            await shareLinksPage.navigate();
            await shareLinksPage.waitForPageLoad();

            // User1 should only see their own link
            const user1LinkCard = await shareLinksPage.getLinkCardByName('User 1 Link');
            expect(await user1LinkCard.isVisible()).toBe(true);

            // User1 should NOT see user2's link
            const user2LinkCount = await page.locator('.link-card:has(.link-title:has-text("User 2 Link"))').count();
            expect(user2LinkCount).toBe(0);
        });
    });

    test.describe('Edge Cases and Validation', () => {
        test('should handle very long share names gracefully', async ({page, dbManager}) => {
            const { shareLinksPage, user } = await TestSetupHelper.setupShareLinksTest(page, dbManager);

            await shareLinksPage.clickCreateFirstLink();
            await shareLinksPage.selectLiveLocationShare();
            await shareLinksPage.waitForDialogToOpen();

            // Very long name (200 characters)
            const longName = 'A'.repeat(200);

            await shareLinksPage.fillLinkForm({
                name: longName,
                showHistory: false,
                expiresAt: DateFactory.futureDate(7),
                hasPassword: false
            });

            await shareLinksPage.submitCreateForm();
            await shareLinksPage.waitForSuccessToast('created');
            await shareLinksPage.waitForToastToDisappear();

            await page.reload();
            await shareLinksPage.waitForPageLoad();

            // Verify creation (name should be truncated in DB if there's a limit)
            const links = await ShareLinkFactory.getByUserId(dbManager, user.id);
            expect(links.length).toBeGreaterThan(0);
            expect(links[0].name.length).toBeGreaterThan(0);
        });
    });
});
