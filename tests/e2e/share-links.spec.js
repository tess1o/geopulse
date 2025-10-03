import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {ShareLinksPage} from '../pages/ShareLinksPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';

test.describe('Share Links Page', () => {

  test.describe('Initial State and Empty Data', () => {
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

      // Verify we're on the share links page
      expect(await shareLinksPage.isOnShareLinksPage()).toBe(true);

      // Check empty state
      expect(await shareLinksPage.hasEmptyState()).toBe(true);

      // Verify database has no share links
      const user = await dbManager.getUserByEmail(testUser.email);
      expect(await ShareLinksPage.countShareLinks(dbManager, user.id)).toBe(0);
    });

    test('should show create button in empty state', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify "Create Your First Link" button exists in empty state
      const createFirstButton = page.locator(shareLinksPage.selectors.createFirstLinkButton);
      expect(await createFirstButton.isVisible()).toBe(true);
    });
  });

  test.describe('Create Share Link', () => {
    test('should create basic share link with current location only', async ({page, dbManager}) => {
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

      // Verify initial count is 0
      const initialCount = await ShareLinksPage.countShareLinks(dbManager, user.id);
      expect(initialCount).toBe(0);

      // Click create button in empty state
      await shareLinksPage.clickCreateFirstLink();
      await shareLinksPage.waitForDialogToOpen();

      // Verify dialog is visible
      expect(await shareLinksPage.isCreateDialogVisible()).toBe(true);

      // Fill form with basic data
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7); // 7 days from now

      await shareLinksPage.fillLinkForm({
        name: 'My Test Link',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: false
      });

      await shareLinksPage.submitCreateForm();

      // Wait for success toast
      await shareLinksPage.waitForSuccessToast('created');
      await shareLinksPage.waitForToastToDisappear();

      // Verify dialog closed
      await shareLinksPage.waitForDialogToClose();

      // Reload to see the new link
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database change
      const finalCount = await ShareLinksPage.countShareLinks(dbManager, user.id);
      expect(finalCount).toBe(1);

      const links = await ShareLinksPage.getShareLinksByUserId(dbManager, user.id);
      expect(links.length).toBe(1);
      expect(links[0].name).toBe('My Test Link');
      expect(links[0].show_history).toBe(false);
      expect(links[0].password).toBeNull();

      // Verify UI shows the link
      expect(await shareLinksPage.hasActiveLinksSection()).toBe(true);
      expect(await shareLinksPage.getActiveLinksCount()).toBe(1);
    });

    test('should create share link with history enabled', async ({page, dbManager}) => {
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
      await shareLinksPage.waitForDialogToOpen();

      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 30);

      await shareLinksPage.fillLinkForm({
        name: 'Link With History',
        showHistory: true,
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
      expect(links[0].name).toBe('Link With History');
      expect(links[0].show_history).toBe(true);

      // Verify UI shows correct setting
      expect(await shareLinksPage.showsHistory('Link With History')).toBe(true);
    });

    test('should create share link with password protection', async ({page, dbManager}) => {
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
      await shareLinksPage.waitForDialogToOpen();

      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 14);

      await shareLinksPage.fillLinkForm({
        name: 'Protected Link',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: true,
        password: 'secure123'
      });

      await shareLinksPage.submitCreateForm();
      await shareLinksPage.waitForSuccessToast('created');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see the new link
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database - password should be stored (hashed in real app)
      const links = await ShareLinksPage.getShareLinksByUserId(dbManager, user.id);
      expect(links.length).toBe(1);
      expect(links[0].name).toBe('Protected Link');
      expect(links[0].password).toBeTruthy(); // Password should exist

      // Verify UI shows password protected
      expect(await shareLinksPage.isPasswordProtected('Protected Link')).toBe(true);
    });

    test('should create share link with all options enabled', async ({page, dbManager}) => {
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
      await shareLinksPage.waitForDialogToOpen();

      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 60);

      await shareLinksPage.fillLinkForm({
        name: 'Full Featured Link',
        showHistory: true,
        expiresAt: expiresAt,
        hasPassword: true,
        password: 'myPassword456'
      });

      await shareLinksPage.submitCreateForm();
      await shareLinksPage.waitForSuccessToast('created');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see the new link
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database has all settings
      const links = await ShareLinksPage.getShareLinksByUserId(dbManager, user.id);
      expect(links.length).toBe(1);
      expect(links[0].name).toBe('Full Featured Link');
      expect(links[0].show_history).toBe(true);
      expect(links[0].password).toBeTruthy();
      expect(links[0].expires_at).toBeTruthy();

      // Verify UI shows all settings
      expect(await shareLinksPage.showsHistory('Full Featured Link')).toBe(true);
      expect(await shareLinksPage.isPasswordProtected('Full Featured Link')).toBe(true);
    });

    test('should create link from header button when links exist', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert existing link
      const expiresAt1 = new Date();
      expiresAt1.setDate(expiresAt1.getDate() + 7);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '11111111-1111-1111-1111-111111111111',
        user_id: user.id,
        name: 'Existing Link',
        expires_at: expiresAt1.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Should see existing link
      expect(await shareLinksPage.getActiveLinksCount()).toBe(1);

      // Click "Create New Link" button in header
      await shareLinksPage.clickCreateNewLink();
      await shareLinksPage.waitForDialogToOpen();

      const expiresAt2 = new Date();
      expiresAt2.setDate(expiresAt2.getDate() + 10);

      await shareLinksPage.fillLinkForm({
        name: 'Second Link',
        showHistory: true,
        expiresAt: expiresAt2,
        hasPassword: false
      });

      await shareLinksPage.submitCreateForm();
      await shareLinksPage.waitForSuccessToast('created');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see both links
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database has both links
      const finalCount = await ShareLinksPage.countShareLinks(dbManager, user.id);
      expect(finalCount).toBe(2);

      // Verify UI shows both links
      expect(await shareLinksPage.getActiveLinksCount()).toBe(2);
    });
  });

  test.describe('Display Active Links', () => {
    test('should display active links with correct information', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert active link
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 30);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '22222222-2222-2222-2222-222222222222',
        user_id: user.id,
        name: 'Active Test Link',
        expires_at: expiresAt.toISOString(),
        show_history: true,
        password: 'testpass',
        view_count: 5
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify active link is displayed
      expect(await shareLinksPage.hasActiveLinksSection()).toBe(true);
      expect(await shareLinksPage.getActiveLinksCount()).toBe(1);

      // Verify link details
      const linkCard = await shareLinksPage.getLinkCardByName('Active Test Link');
      expect(await linkCard.isVisible()).toBe(true);

      // Verify status tag
      expect(await shareLinksPage.isLinkActive('Active Test Link')).toBe(true);

      // Verify settings
      expect(await shareLinksPage.isPasswordProtected('Active Test Link')).toBe(true);
      expect(await shareLinksPage.getViewCount('Active Test Link')).toBe(5);
      expect(await shareLinksPage.showsHistory('Active Test Link')).toBe(true);

      // Verify share URL is displayed
      const shareUrl = await shareLinksPage.getLinkUrl('Active Test Link');
      expect(shareUrl).toContain('/shared/22222222-2222-2222-2222-222222222222');

      // Verify action buttons exist
      const editButton = linkCard.locator(shareLinksPage.selectors.editButton);
      const deleteButton = linkCard.locator(shareLinksPage.selectors.deleteButton);
      expect(await editButton.isVisible()).toBe(true);
      expect(await deleteButton.isVisible()).toBe(true);
    });

    test('should display multiple active links', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert multiple active links
      const expiresAt1 = new Date();
      expiresAt1.setDate(expiresAt1.getDate() + 10);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '33333333-3333-3333-3333-333333333333',
        user_id: user.id,
        name: 'Link One',
        expires_at: expiresAt1.toISOString(),
        show_history: false
      });

      const expiresAt2 = new Date();
      expiresAt2.setDate(expiresAt2.getDate() + 20);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '44444444-4444-4444-4444-444444444444',
        user_id: user.id,
        name: 'Link Two',
        expires_at: expiresAt2.toISOString(),
        show_history: true
      });

      const expiresAt3 = new Date();
      expiresAt3.setDate(expiresAt3.getDate() + 30);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '55555555-5555-5555-5555-555555555555',
        user_id: user.id,
        name: 'Link Three',
        expires_at: expiresAt3.toISOString(),
        show_history: false,
        password: 'pass123'
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify all active links are displayed
      expect(await shareLinksPage.getActiveLinksCount()).toBe(3);

      // Verify each link is visible
      const link1 = await shareLinksPage.getLinkCardByName('Link One');
      const link2 = await shareLinksPage.getLinkCardByName('Link Two');
      const link3 = await shareLinksPage.getLinkCardByName('Link Three');

      expect(await link1.isVisible()).toBe(true);
      expect(await link2.isVisible()).toBe(true);
      expect(await link3.isVisible()).toBe(true);
    });
  });

  test.describe('Display Expired Links', () => {
    test('should display expired links separately', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert expired link (expires in the past)
      const expiredDate = new Date();
      expiredDate.setDate(expiredDate.getDate() - 5); // 5 days ago
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '66666666-6666-6666-6666-666666666666',
        user_id: user.id,
        name: 'Expired Link',
        expires_at: expiredDate.toISOString(),
        show_history: false
      });

      // Insert active link
      const activeDate = new Date();
      activeDate.setDate(activeDate.getDate() + 10);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '77777777-7777-7777-7777-777777777777',
        user_id: user.id,
        name: 'Active Link',
        expires_at: activeDate.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify both sections exist
      expect(await shareLinksPage.hasActiveLinksSection()).toBe(true);
      expect(await shareLinksPage.hasExpiredLinksSection()).toBe(true);

      // Verify counts
      expect(await shareLinksPage.getActiveLinksCount()).toBe(1);
      expect(await shareLinksPage.getExpiredLinksCount()).toBe(1);

      // Verify expired link has correct tag
      expect(await shareLinksPage.isLinkExpired('Expired Link')).toBe(true);
      expect(await shareLinksPage.isLinkActive('Active Link')).toBe(true);

      // Verify expired link doesn't have Edit button
      const expiredCard = await shareLinksPage.getLinkCardByName('Expired Link');
      const editButtons = await expiredCard.locator(shareLinksPage.selectors.editButton).count();
      expect(editButtons).toBe(0);

      // But should have Delete button
      const deleteButton = expiredCard.locator(shareLinksPage.selectors.deleteButton);
      expect(await deleteButton.isVisible()).toBe(true);
    });

    test('should display only expired section when all links are expired', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert only expired links
      const expiredDate1 = new Date();
      expiredDate1.setDate(expiredDate1.getDate() - 10);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '88888888-8888-8888-8888-888888888888',
        user_id: user.id,
        name: 'Old Link 1',
        expires_at: expiredDate1.toISOString(),
        show_history: false
      });

      const expiredDate2 = new Date();
      expiredDate2.setDate(expiredDate2.getDate() - 3);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '99999999-9999-9999-9999-999999999999',
        user_id: user.id,
        name: 'Old Link 2',
        expires_at: expiredDate2.toISOString(),
        show_history: true
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify only expired section exists
      expect(await shareLinksPage.hasExpiredLinksSection()).toBe(true);
      expect(await shareLinksPage.getExpiredLinksCount()).toBe(2);

      // Active section should not exist (or have 0 links)
      const activeCount = await shareLinksPage.getActiveLinksCount();
      expect(activeCount).toBe(0);
    });
  });

  test.describe('Edit Share Link', () => {
    test('should edit link name', async ({page, dbManager}) => {
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
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Click edit button
      await shareLinksPage.clickEditLink('Original Name');
      await shareLinksPage.waitForDialogToOpen();

      // Verify edit dialog is shown
      expect(await shareLinksPage.isEditDialogVisible()).toBe(true);

      // Change name
      await shareLinksPage.fillLinkForm({
        name: 'Updated Name'
      });

      await shareLinksPage.submitUpdateForm();
      await shareLinksPage.waitForSuccessToast('updated');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see updated link
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database change
      const updatedLink = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(updatedLink.name).toBe('Updated Name');

      // Verify UI shows updated name
      const updatedCard = await shareLinksPage.getLinkCardByName('Updated Name');
      expect(await updatedCard.isVisible()).toBe(true);
    });

    test('should edit link to enable history', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert link without history
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 20);
      const insertedLink = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        user_id: user.id,
        name: 'No History Link',
        expires_at: expiresAt.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify initial state
      expect(await shareLinksPage.showsHistory('No History Link')).toBe(false);

      // Edit to enable history
      await shareLinksPage.clickEditLink('No History Link');
      await shareLinksPage.waitForDialogToOpen();

      await shareLinksPage.fillLinkForm({
        showHistory: true
      });

      await shareLinksPage.submitUpdateForm();
      await shareLinksPage.waitForSuccessToast('updated');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see updated link
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database change
      const updatedLink = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(updatedLink.show_history).toBe(true);

      // Verify UI shows updated setting
      expect(await shareLinksPage.showsHistory('No History Link')).toBe(true);
    });

    test('should edit link to add password protection', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert link without password
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 25);
      const insertedLink = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
        user_id: user.id,
        name: 'Unprotected Link',
        expires_at: expiresAt.toISOString(),
        show_history: false,
        password: null
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify initial state
      expect(await shareLinksPage.isPasswordProtected('Unprotected Link')).toBe(false);

      // Edit to add password
      await shareLinksPage.clickEditLink('Unprotected Link');
      await shareLinksPage.waitForDialogToOpen();

      await shareLinksPage.fillLinkForm({
        hasPassword: true,
        password: 'newPassword123'
      });

      await shareLinksPage.submitUpdateForm();
      await shareLinksPage.waitForSuccessToast('updated');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see updated link
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database change
      const updatedLink = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(updatedLink.password).toBeTruthy();

      // Verify UI shows password protected
      expect(await shareLinksPage.isPasswordProtected('Unprotected Link')).toBe(true);
    });

    test('should edit link to change expiration date', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert link
      const originalExpiry = new Date();
      originalExpiry.setDate(originalExpiry.getDate() + 7);
      const insertedLink = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
        user_id: user.id,
        name: 'Expiry Test Link',
        expires_at: originalExpiry.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Edit to change expiration
      const newExpiry = new Date();
      newExpiry.setDate(newExpiry.getDate() + 60);

      await shareLinksPage.clickEditLink('Expiry Test Link');
      await shareLinksPage.waitForDialogToOpen();

      await shareLinksPage.fillLinkForm({
        expiresAt: newExpiry
      });

      await shareLinksPage.submitUpdateForm();
      await shareLinksPage.waitForSuccessToast('updated');
      await shareLinksPage.waitForToastToDisappear();

      // Verify database change - link should still exist and be updated
      const updatedLink = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(updatedLink).toBeTruthy();
      expect(updatedLink.expires_at).toBeTruthy();

      // The exact date might differ due to timezone handling, but it should be different from original
      expect(updatedLink.expires_at).not.toBe(originalExpiry.toISOString());

      // Reload to verify in UI
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify link still visible in UI
      const linkCard = await shareLinksPage.getLinkCardByName('Expiry Test Link');
      expect(await linkCard.isVisible()).toBe(true);
    });

    test('should edit multiple fields at once', async ({page, dbManager}) => {
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
      expiresAt.setDate(expiresAt.getDate() + 10);
      const insertedLink = await ShareLinksPage.insertShareLink(dbManager, {
        id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        user_id: user.id,
        name: 'Multi Edit Link',
        expires_at: expiresAt.toISOString(),
        show_history: false,
        password: null
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Edit multiple fields
      const newExpiry = new Date();
      newExpiry.setDate(newExpiry.getDate() + 45);

      await shareLinksPage.clickEditLink('Multi Edit Link');
      await shareLinksPage.waitForDialogToOpen();

      await shareLinksPage.fillLinkForm({
        name: 'Fully Updated Link',
        showHistory: true,
        expiresAt: newExpiry,
        hasPassword: true,
        password: 'complexPass789'
      });

      await shareLinksPage.submitUpdateForm();
      await shareLinksPage.waitForSuccessToast('updated');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see updated link
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify all database changes
      const updatedLink = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(updatedLink.name).toBe('Fully Updated Link');
      expect(updatedLink.show_history).toBe(true);
      expect(updatedLink.password).toBeTruthy();

      // Verify UI shows all updates
      expect(await shareLinksPage.showsHistory('Fully Updated Link')).toBe(true);
      expect(await shareLinksPage.isPasswordProtected('Fully Updated Link')).toBe(true);
    });
  });

  test.describe('Delete Share Link', () => {
    test('should show confirmation dialog when deleting link', async ({page, dbManager}) => {
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
        id: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
        user_id: user.id,
        name: 'Link To Delete',
        expires_at: expiresAt.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Click delete button
      await shareLinksPage.clickDeleteLink('Link To Delete');
      await shareLinksPage.waitForDeleteDialogToOpen();

      // Verify confirmation dialog is shown
      expect(await shareLinksPage.isDeleteDialogVisible()).toBe(true);

      // Verify dialog contains link name
      const dialogMessage = page.locator(shareLinksPage.selectors.deleteMessage);
      const messageText = await dialogMessage.textContent();
      expect(messageText).toContain('Link To Delete');
    });

    test('should delete link when confirmed', async ({page, dbManager}) => {
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
        id: '10101010-1010-1010-1010-101010101010',
        user_id: user.id,
        name: 'Delete Me',
        expires_at: expiresAt.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify link exists in database and UI
      const beforeDelete = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(beforeDelete).toBeTruthy();
      expect(await shareLinksPage.getActiveLinksCount()).toBe(1);

      // Delete link
      await shareLinksPage.deleteShareLink('Delete Me');
      await shareLinksPage.waitForSuccessToast('deleted');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see updated state
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database deletion
      const afterDelete = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(afterDelete).toBeNull();

      // Verify UI shows empty state
      const finalCount = await ShareLinksPage.countShareLinks(dbManager, user.id);
      expect(finalCount).toBe(0);
      expect(await shareLinksPage.hasEmptyState()).toBe(true);
    });

    test('should not delete link when cancelled', async ({page, dbManager}) => {
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
        id: '20202020-2020-2020-2020-202020202020',
        user_id: user.id,
        name: 'Keep Me',
        expires_at: expiresAt.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Click delete button
      await shareLinksPage.clickDeleteLink('Keep Me');
      await shareLinksPage.waitForDeleteDialogToOpen();

      // Cancel deletion
      await shareLinksPage.cancelDelete();
      await shareLinksPage.waitForDeleteDialogToClose();

      // Verify link still exists in database
      const afterCancel = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(afterCancel).toBeTruthy();

      // Verify UI still shows the link
      expect(await shareLinksPage.getActiveLinksCount()).toBe(1);
      const linkCard = await shareLinksPage.getLinkCardByName('Keep Me');
      expect(await linkCard.isVisible()).toBe(true);
    });

    test('should delete expired link', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert expired link
      const expiredDate = new Date();
      expiredDate.setDate(expiredDate.getDate() - 7);
      const insertedLink = await ShareLinksPage.insertShareLink(dbManager, {
        id: '30303030-3030-3030-3030-303030303030',
        user_id: user.id,
        name: 'Expired Delete',
        expires_at: expiredDate.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify expired link is shown
      expect(await shareLinksPage.getExpiredLinksCount()).toBe(1);

      // Delete expired link
      await shareLinksPage.deleteShareLink('Expired Delete');
      await shareLinksPage.waitForSuccessToast('deleted');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see updated state
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify database deletion
      const afterDelete = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(afterDelete).toBeNull();

      // Verify UI shows empty state
      expect(await shareLinksPage.hasEmptyState()).toBe(true);
    });

    test('should delete one of multiple links', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert multiple links
      const expiresAt1 = new Date();
      expiresAt1.setDate(expiresAt1.getDate() + 10);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '40404040-4040-4040-4040-404040404040',
        user_id: user.id,
        name: 'Keep This',
        expires_at: expiresAt1.toISOString(),
        show_history: false
      });

      const expiresAt2 = new Date();
      expiresAt2.setDate(expiresAt2.getDate() + 20);
      const linkToDelete = await ShareLinksPage.insertShareLink(dbManager, {
        id: '50505050-5050-5050-5050-505050505050',
        user_id: user.id,
        name: 'Delete This',
        expires_at: expiresAt2.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify initial count
      expect(await shareLinksPage.getActiveLinksCount()).toBe(2);

      // Delete one link
      await shareLinksPage.deleteShareLink('Delete This');
      await shareLinksPage.waitForSuccessToast('deleted');
      await shareLinksPage.waitForToastToDisappear();

      // Reload to see updated state
      await page.reload();
      await shareLinksPage.waitForPageLoad();

      // Verify only one link deleted
      const deletedLink = await ShareLinksPage.getShareLinkById(dbManager, linkToDelete.id);
      expect(deletedLink).toBeNull();

      const remainingCount = await ShareLinksPage.countShareLinks(dbManager, user.id);
      expect(remainingCount).toBe(1);

      // Verify UI shows remaining link
      expect(await shareLinksPage.getActiveLinksCount()).toBe(1);
      const remainingCard = await shareLinksPage.getLinkCardByName('Keep This');
      expect(await remainingCard.isVisible()).toBe(true);
    });
  });

  test.describe('Copy to Clipboard', () => {
    test('should show success toast when copying link URL', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      // Grant clipboard permissions
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
        id: '60606060-6060-6060-6060-606060606060',
        user_id: user.id,
        name: 'Copy Test Link',
        expires_at: expiresAt.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Click copy button
      await shareLinksPage.clickCopyButton('Copy Test Link');

      // Verify success toast appears
      await shareLinksPage.waitForSuccessToast('copied');
    });
  });

  test.describe('Dialog Cancellation', () => {
    test('should cancel create dialog without creating link', async ({page, dbManager}) => {
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

      // Open create dialog
      await shareLinksPage.clickCreateFirstLink();
      await shareLinksPage.waitForDialogToOpen();

      // Fill some data
      await shareLinksPage.fillLinkForm({
        name: 'Cancelled Link'
      });

      // Cancel dialog
      await shareLinksPage.cancelDialog();
      await shareLinksPage.waitForDialogToClose();

      // Verify no link was created in database
      const count = await ShareLinksPage.countShareLinks(dbManager, user.id);
      expect(count).toBe(0);

      // Verify empty state still shown
      expect(await shareLinksPage.hasEmptyState()).toBe(true);
    });

    test('should cancel edit dialog without updating link', async ({page, dbManager}) => {
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
        id: '70707070-7070-7070-7070-707070707070',
        user_id: user.id,
        name: 'Original Name',
        expires_at: expiresAt.toISOString(),
        show_history: false
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Open edit dialog
      await shareLinksPage.clickEditLink('Original Name');
      await shareLinksPage.waitForDialogToOpen();

      // Make changes
      await shareLinksPage.fillLinkForm({
        name: 'Changed Name',
        showHistory: true
      });

      // Cancel dialog
      await shareLinksPage.cancelDialog();
      await shareLinksPage.waitForDialogToClose();

      // Verify link was NOT updated in database
      const unchangedLink = await ShareLinksPage.getShareLinkById(dbManager, insertedLink.id);
      expect(unchangedLink.name).toBe('Original Name');
      expect(unchangedLink.show_history).toBe(false);

      // Verify UI still shows original name
      const linkCard = await shareLinksPage.getLinkCardByName('Original Name');
      expect(await linkCard.isVisible()).toBe(true);
    });
  });

  test.describe('View Count Display', () => {
    test('should display correct view count', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert link with specific view count
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 15);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '80808080-8080-8080-8080-808080808080',
        user_id: user.id,
        name: 'Popular Link',
        expires_at: expiresAt.toISOString(),
        show_history: false,
        view_count: 42
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify view count is displayed
      const viewCount = await shareLinksPage.getViewCount('Popular Link');
      expect(viewCount).toBe(42);
    });

    test('should display zero view count for new links', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);

      // Insert link with zero views
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 15);
      await ShareLinksPage.insertShareLink(dbManager, {
        id: '90909090-9090-9090-9090-909090909090',
        user_id: user.id,
        name: 'New Link',
        expires_at: expiresAt.toISOString(),
        show_history: false,
        view_count: 0
      });

      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      // Verify view count shows 0
      const viewCount = await shareLinksPage.getViewCount('New Link');
      expect(viewCount).toBe(0);
    });
  });
});
