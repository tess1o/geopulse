import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {ShareLinksPage} from '../pages/ShareLinksPage.js';
import {SharedLocationPage} from '../pages/SharedLocationPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';

// Helper function to create a share link via UI
async function createShareLinkViaUI(page, shareLinksPage, linkData) {
  await shareLinksPage.navigate();
  await shareLinksPage.waitForPageLoad();

  const hasLinks = await shareLinksPage.getActiveLinksCount() > 0;

  if (hasLinks) {
    await shareLinksPage.clickCreateNewLink();
  } else {
    await shareLinksPage.clickCreateFirstLink();
  }

  await shareLinksPage.waitForDialogToOpen();
  await shareLinksPage.fillLinkForm(linkData);
  await shareLinksPage.submitCreateForm();
  await shareLinksPage.waitForSuccessToast('created');
  await shareLinksPage.waitForDialogToClose();

  // Get the link ID from URL
  const linkUrl = await shareLinksPage.getLinkUrl(linkData.name);
  return linkUrl.split('/shared/')[1];
}

test.describe('Shared Link Access', () => {

  test.describe('Guest Access - Public Links (No Password)', () => {
    test('should allow guest to access public link with current location only', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup: Create user, add GPS data, create share link
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create public share link via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Public Current Location',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: false
      });

      // Logout to simulate guest access
      await context.clearCookies();

      // Access shared link as guest
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();

      // Should not show password prompt
      expect(await sharedLocationPage.isPasswordRequired()).toBe(false);

      // Should show location display
      await sharedLocationPage.waitForLocationToLoad();
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);

      // Verify share info
      const shareTitle = await sharedLocationPage.getShareTitle();
      expect(shareTitle).toBe('Public Current Location');

      const scope = await sharedLocationPage.getScope();
      expect(scope).toBe('Current Location Only');

      // Verify map is displayed
      await sharedLocationPage.waitForMapReady();
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);

      // Should have location marker
      expect(await sharedLocationPage.hasLocationMarker()).toBe(true);

      // Should NOT have path layer (history disabled)
      expect(await sharedLocationPage.hasPathLayer()).toBe(false);

      // Map title should indicate current location
      const mapTitle = await sharedLocationPage.getMapTitle();
      expect(mapTitle).toBe('Current Location');

      // Verify view count incremented
      const viewCount = await SharedLocationPage.getViewCount(dbManager, linkId);
      expect(viewCount).toBe(1);
    });

    test('should allow guest to access public link with location history', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup user and GPS data
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 10);

      // Create public share link with history enabled via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Public With History',
        showHistory: true,
        expiresAt: expiresAt,
        hasPassword: false
      });

      // Logout
      await context.clearCookies();

      // Access shared link as guest
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();

      // Should show location display
      await sharedLocationPage.waitForLocationToLoad();
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);

      // Verify scope shows history
      const scope = await sharedLocationPage.getScope();
      expect(scope).toBe('Location History');

      // Verify map with history
      await sharedLocationPage.waitForMapReady();
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);

      // Should have location marker
      expect(await sharedLocationPage.hasLocationMarker()).toBe(true);

      // Should have path layer (history enabled)
      expect(await sharedLocationPage.hasPathLayer()).toBe(true);

      // Map title should indicate history
      const mapTitle = await sharedLocationPage.getMapTitle();
      expect(mapTitle).toContain('History');
    });

    test('should show no data message when user has no GPS points', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup user WITHOUT GPS data
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Create share link via UI (no GPS data for this user)
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Link With No Data',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: false
      });

      // Logout
      await context.clearCookies();

      // Access shared link as guest
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();

      // Should eventually show no data state
      await page.waitForTimeout(2000);

      // Should show no data message
      expect(await sharedLocationPage.isNoDataShown()).toBe(true);
    });
  });

  test.describe('Password-Protected Links', () => {
    test('should prompt for password on password-protected link', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup user and GPS data
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create password-protected share link via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Protected Link',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: true,
        password: 'testpass123'
      });

      // Logout
      await context.clearCookies();

      // Access shared link as guest
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();

      // Should show password prompt
      await sharedLocationPage.waitForPasswordPrompt();
      expect(await sharedLocationPage.isPasswordRequired()).toBe(true);

      // Should not show location yet
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(false);
    });

    test('should reject incorrect password', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create password-protected link via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Protected Link Wrong Password',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: true,
        password: 'correctpass'
      });

      await context.clearCookies();

      // Access link and enter wrong password
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();

      // Submit wrong password
      await sharedLocationPage.submitPassword('wrongpass');
      await page.waitForTimeout(1000);

      // Should show error
      expect(await sharedLocationPage.isErrorShown()).toBe(true);
      const errorMsg = await sharedLocationPage.getErrorMessage();
      expect(errorMsg.toLowerCase()).toContain('password');

      // View count should NOT increment on wrong password
      const viewCount = await SharedLocationPage.getViewCount(dbManager, linkId);
      expect(viewCount).toBe(0);
    });

    test('should grant access with correct password', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup user and GPS data
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create password-protected share link via UI
      await shareLinksPage.navigate();
      await shareLinksPage.waitForPageLoad();

      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);

      await shareLinksPage.clickCreateFirstLink();
      await shareLinksPage.waitForDialogToOpen();

      await shareLinksPage.fillLinkForm({
        name: 'Protected Link Correct Password',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: true,
        password: 'correctpass123'
      });

      await shareLinksPage.submitCreateForm();
      await shareLinksPage.waitForSuccessToast('created');
      await shareLinksPage.waitForDialogToClose();

      // Get the link URL
      const linkUrl = await shareLinksPage.getLinkUrl('Protected Link Correct Password');
      const linkId = linkUrl.split('/shared/')[1];

      await context.clearCookies();

      // Access link and enter correct password
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();

      // Submit correct password
      await sharedLocationPage.submitPassword('correctpass123');

      // Should show location display
      await sharedLocationPage.waitForLocationToLoad();
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);

      // Verify map is displayed
      await sharedLocationPage.waitForMapReady();
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);

      // View count should increment
      await page.waitForTimeout(1000);
      const viewCount = await SharedLocationPage.getViewCount(dbManager, linkId);
      expect(viewCount).toBe(1);
    });

    test('should allow access to password-protected link with history', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 10);

      // Create password-protected link with history via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Protected With History',
        showHistory: true,
        expiresAt: expiresAt,
        hasPassword: true,
        password: 'historypass'
      });

      await context.clearCookies();

      // Access and authenticate
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();
      await sharedLocationPage.submitPassword('historypass');

      // Should show location with history
      await sharedLocationPage.waitForLocationToLoad();
      const scope = await sharedLocationPage.getScope();
      expect(scope).toBe('Location History');

      // Should show path on map
      await sharedLocationPage.waitForMapReady();
      expect(await sharedLocationPage.hasPathLayer()).toBe(true);
    });
  });

  test.describe('Expired Links', () => {
    test('should show error for expired link', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create EXPIRED link (expires in the past)
      const expiredDate = new Date();
      expiredDate.setDate(expiredDate.getDate() - 7);
      const link = await ShareLinksPage.insertShareLink(dbManager, {
        id: '22222222-2222-2222-2222-222222222222',
        user_id: user.id,
        name: 'Expired Link',
        expires_at: expiredDate.toISOString(),
        show_history: false,
        password: null,
        view_count: 0
      });

      await context.clearCookies();

      // Try to access expired link
      await sharedLocationPage.navigateToSharedLink(link.id);
      await sharedLocationPage.waitForPageLoad();

      // Should show error
      await sharedLocationPage.waitForError();
      expect(await sharedLocationPage.isErrorShown()).toBe(true);

      const errorMsg = await sharedLocationPage.getErrorMessage();
      expect(errorMsg.toLowerCase()).toContain('expired');

      // View count should NOT increment
      const viewCount = await SharedLocationPage.getViewCount(dbManager, link.id);
      expect(viewCount).toBe(0);
    });

    test('should show error for non-existent link', async ({page, context}) => {
      const sharedLocationPage = new SharedLocationPage(page);

      await context.clearCookies();

      // Try to access non-existent link
      await sharedLocationPage.navigateToSharedLink('99999999-9999-9999-9999-999999999999');
      await sharedLocationPage.waitForPageLoad();

      // Should show error
      await sharedLocationPage.waitForError();
      expect(await sharedLocationPage.isErrorShown()).toBe(true);

      const errorMsg = await sharedLocationPage.getErrorMessage();
      expect(errorMsg.toLowerCase()).toMatch(/not found|expired/);
    });
  });

  test.describe('View Count Tracking', () => {
    test('should increment view count on each access', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create link via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'View Count Test',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: false
      });

      await context.clearCookies();

      // First access
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForLocationToLoad();
      await page.waitForTimeout(1000);

      let viewCount = await SharedLocationPage.getViewCount(dbManager, linkId);
      expect(viewCount).toBe(1);

      // Second access (reload)
      await page.reload();
      await sharedLocationPage.waitForLocationToLoad();
      await page.waitForTimeout(1000);

      viewCount = await SharedLocationPage.getViewCount(dbManager, linkId);
      expect(viewCount).toBe(2);

      // Third access (new navigation)
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForLocationToLoad();
      await page.waitForTimeout(1000);

      viewCount = await SharedLocationPage.getViewCount(dbManager, linkId);
      expect(viewCount).toBe(3);
    });

    test('should not increment view count on failed password attempts', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create password-protected link via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Password View Count Test',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: true,
        password: 'secretpass'
      });

      await context.clearCookies();

      // Access with wrong password
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForPasswordPrompt();
      await sharedLocationPage.submitPassword('wrongpass');
      await page.waitForTimeout(1000);

      // View count should still be 0
      let viewCount = await SharedLocationPage.getViewCount(dbManager, linkId);
      expect(viewCount).toBe(0);

      // Retry with wrong password again
      await page.reload();
      await sharedLocationPage.waitForPasswordPrompt();
      await sharedLocationPage.submitPassword('wrongpass2');
      await page.waitForTimeout(1000);

      // View count should still be 0
      viewCount = await SharedLocationPage.getViewCount(dbManager, linkId);
      expect(viewCount).toBe(0);

      // Access with correct password
      await page.reload();
      await sharedLocationPage.waitForPasswordPrompt();
      await sharedLocationPage.submitPassword('secretpass');
      await sharedLocationPage.waitForLocationToLoad();
      await page.waitForTimeout(1000);

      // Now view count should be 1
      viewCount = await SharedLocationPage.getViewCount(dbManager, linkId);
      expect(viewCount).toBe(1);
    });
  });

  test.describe('Authorized User Access', () => {
    test('should allow link creator to access their own link', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create link via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Own Link Access',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: false
      });

      // Access own link while logged in
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();

      // Should show location without password prompt
      await sharedLocationPage.waitForLocationToLoad();
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);

      // Should show map
      await sharedLocationPage.waitForMapReady();
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);
    });

    test('should allow authorized user to access another users link', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const user1 = TestData.users.existing;
      const user2 = TestData.users.another;

      // Create user1 with GPS data and share link
      await UserFactory.createUser(page, user1);
      await loginPage.navigate();
      await loginPage.login(user1.email, user1.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const userRecord1 = await dbManager.getUserByEmail(user1.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, userRecord1.id, 5);

      // Create link via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Cross User Access',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: false
      });

      await context.clearCookies();

      // Login as user2
      await UserFactory.createUser(page, user2);
      await loginPage.navigate();
      await loginPage.login(user2.email, user2.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      // Access user1's link as user2
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();

      // Should show location
      await sharedLocationPage.waitForLocationToLoad();
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);

      // Verify it shows user1's name
      const sharedBy = await sharedLocationPage.getSharedBy();
      expect(sharedBy).toBe(user1.fullName);
    });
  });

  test.describe('Refresh Functionality', () => {
    test('should refresh location data when refresh button clicked', async ({page, dbManager, context}) => {
      const loginPage = new LoginPage(page);
      const shareLinksPage = new ShareLinksPage(page);
      const sharedLocationPage = new SharedLocationPage(page);
      const testUser = TestData.users.existing;

      // Setup
      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      const user = await dbManager.getUserByEmail(testUser.email);
      await SharedLocationPage.createGpsPointsForUser(dbManager, user.id, 5);

      // Create link via UI
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);
      const linkId = await createShareLinkViaUI(page, shareLinksPage, {
        name: 'Refresh Test',
        showHistory: false,
        expiresAt: expiresAt,
        hasPassword: false
      });

      await context.clearCookies();

      // Access link
      await sharedLocationPage.navigateToSharedLink(linkId);
      await sharedLocationPage.waitForPageLoad();
      await sharedLocationPage.waitForLocationToLoad();
      await sharedLocationPage.waitForMapReady();

      // Click refresh button
      await sharedLocationPage.clickRefreshButton();
      await sharedLocationPage.waitForRefresh();

      // Should still show location
      expect(await sharedLocationPage.isLocationDisplayed()).toBe(true);
      expect(await sharedLocationPage.isMapDisplayed()).toBe(true);
    });
  });
});
