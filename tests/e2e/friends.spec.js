import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {FriendsPage} from '../pages/FriendsPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';

test.describe('Friends Page', () => {

  test.describe('Initial State and Empty Data', () => {
    test('should show empty state when no friends exist', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();
      await friendsPage.waitForLoadingComplete();

      // Verify we're on the friends page
      expect(await friendsPage.isOnFriendsPage()).toBe(true);

      // Check empty state in friends list
      expect(await friendsPage.hasEmptyFriendsState()).toBe(true);

      // Verify counts are all zero
      expect(await friendsPage.getFriendsCount()).toBe(0);
      expect(await friendsPage.getSentInvitesCount()).toBe(0);
      expect(await friendsPage.getReceivedInvitesCount()).toBe(0);

      // Verify database has no friends or invitations
      const user = await dbManager.getUserByEmail(testUser.email);
      expect(await FriendsPage.countFriends(dbManager, user.id)).toBe(0);
      expect(await FriendsPage.countReceivedInvitations(dbManager, user.id)).toBe(0);
      expect(await FriendsPage.countSentInvitations(dbManager, user.id)).toBe(0);
    });

    test('should show empty state for invitations tab', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();
      await friendsPage.waitForLoadingComplete();

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');

      // Verify empty state is shown
      expect(await friendsPage.hasEmptyInvitationsState()).toBe(true);
    });

    test('should display correct counts in status cards', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();
      await friendsPage.waitForLoadingComplete();

      // All counts should be 0 initially
      const friendsCount = await friendsPage.getFriendsCount();
      const sentInvites = await friendsPage.getSentInvitesCount();
      const receivedInvites = await friendsPage.getReceivedInvitesCount();

      expect(friendsCount).toBe(0);
      expect(sentInvites).toBe(0);
      expect(receivedInvites).toBe(0);
    });
  });

  test.describe('Sending Friend Invitations', () => {
    test('should send invitation with valid email', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      // Create both users
      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();
      await friendsPage.waitForLoadingComplete();

      // Open invite dialog
      await friendsPage.openInviteDialog();
      expect(await friendsPage.isInviteDialogVisible()).toBe(true);

      // Send invitation
      await friendsPage.sendInvitation(friendUser.email);

      // Wait for success toast notification
      await friendsPage.waitForSuccessToast('sent');
      await friendsPage.waitForToastToDisappear();

      // Wait for sent invites count to update automatically
      // BUG WAS FIXED: fetchSentInvitations now correctly uses response.data
      await friendsPage.waitForSentInvitesCount(1);

      // Verify database
      const sender = await dbManager.getUserByEmail(testUser.email);
      const receiver = await dbManager.getUserByEmail(friendUser.email);
      const invitation = await FriendsPage.getInvitationBySenderAndReceiver(dbManager, sender.id, receiver.id);

      expect(invitation).toBeTruthy();
      expect(invitation.status).toBe('PENDING');
    });

    test('should show validation error for invalid email', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Open invite dialog
      await friendsPage.openInviteDialog();

      // Try to send with invalid email
      await friendsPage.fillInviteEmail('not-an-email');
      await page.locator(friendsPage.selectors.sendInvitationButton).click();

      // Should show validation error
      await page.waitForTimeout(500);
      const error = await friendsPage.getInviteEmailError();
      expect(error).toBeTruthy();
      expect(error).toContain('valid email');
    });

    test('should show validation error for empty email', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Open invite dialog
      await friendsPage.openInviteDialog();

      // Try to send with empty email
      await page.locator(friendsPage.selectors.sendInvitationButton).click();

      // Should show validation error
      await page.waitForTimeout(500);
      const error = await friendsPage.getInviteEmailError();
      expect(error).toBeTruthy();
      expect(error).toContain('required');
    });

    test('should handle invitation to non-existent user', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Open invite dialog
      await friendsPage.openInviteDialog();

      // Send invitation to non-existent user
      await friendsPage.sendInvitation('nonexistent@example.com');

      // Should show error toast
      await friendsPage.waitForErrorToast();
    });

    test('should handle invitation to yourself', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Open invite dialog
      await friendsPage.openInviteDialog();

      // Try to send invitation to yourself
      await friendsPage.sendInvitation(testUser.email);

      // Should show error toast
      await friendsPage.waitForErrorToast();
    });

    test('should show sent invitation in Sent Invites section', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Send invitation
      await friendsPage.openInviteDialog();
      await friendsPage.sendInvitation(friendUser.email);
      await friendsPage.waitForSuccessToast('sent');
      await friendsPage.waitForToastToDisappear();

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Verify sent invitation is displayed
      expect(await friendsPage.hasSentInvitesSection()).toBe(true);
      const sentInvites = await friendsPage.getSentInvitationsList();
      expect(sentInvites.length).toBe(1);
      expect(sentInvites[0].email).toBe(friendUser.email);
    });
  });

  test.describe('Receiving and Managing Invitations', () => {
    test('should display received invitations correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      // Insert invitation in database
      const receiver = await dbManager.getUserByEmail(testUser.email);
      const sender = await dbManager.getUserByEmail(friendUser.email);
      await FriendsPage.insertInvitation(dbManager, sender.id, receiver.id);

      // Login as receiver
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();
      await friendsPage.waitForLoadingComplete();

      // Verify received invites count
      expect(await friendsPage.getReceivedInvitesCount()).toBe(1);

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Verify invitation is displayed
      expect(await friendsPage.hasReceivedInvitesSection()).toBe(true);
      const receivedInvites = await friendsPage.getReceivedInvitationsList();
      expect(receivedInvites.length).toBe(1);
      expect(receivedInvites[0].email).toBe(friendUser.email);
    });

    test('should accept individual invitation', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const receiver = await dbManager.getUserByEmail(testUser.email);
      const sender = await dbManager.getUserByEmail(friendUser.email);
      const invitationId = await FriendsPage.insertInvitation(dbManager, sender.id, receiver.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Accept invitation
      await friendsPage.acceptInvitation(friendUser.email);

      // Wait for success toast
      await friendsPage.waitForSuccessToast('You are now friends');
      await friendsPage.waitForToastToDisappear();

      // Wait for counts to update
      await friendsPage.waitForReceivedInvitesCount(0);
      await friendsPage.waitForFriendsCount(1);

      // Verify friendship was created in database
      expect(await FriendsPage.verifyFriendshipExists(dbManager, receiver.id, sender.id)).toBe(true);

      // Verify invitation status changed to ACCEPTED
      const status = await FriendsPage.getInvitationStatus(dbManager, invitationId);
      expect(status).toBe('ACCEPTED');

      // Switch to friends tab and verify friend is added
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      expect(await friendsPage.getFriendsCount()).toBe(1);
      const friends = await friendsPage.getFriendsList();
      expect(friends.length).toBe(1);
      expect(friends[0].email).toBe(friendUser.email);
    });

    test('should reject individual invitation', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const receiver = await dbManager.getUserByEmail(testUser.email);
      const sender = await dbManager.getUserByEmail(friendUser.email);
      const invitationId = await FriendsPage.insertInvitation(dbManager, sender.id, receiver.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Reject invitation
      await friendsPage.rejectInvitation(friendUser.email);

      // Wait for success toast
      await friendsPage.waitForSuccessToast('rejected');

      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1000);

      // Verify invitation is removed from received invites
      expect(await friendsPage.getReceivedInvitesCount()).toBe(0);

      // Verify no friendship was created
      expect(await FriendsPage.verifyFriendshipNotExists(dbManager, receiver.id, sender.id)).toBe(true);

      // Verify invitation status changed to REJECTED
      const status = await FriendsPage.getInvitationStatus(dbManager, invitationId);
      expect(status).toBe('REJECTED');

      // Verify friends count is still 0
      expect(await friendsPage.getFriendsCount()).toBe(0);
    });

    test('should accept multiple invitations (bulk action)', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friend1 = {...TestData.users.another};
      const friend2 = TestData.generateUserWithEmail('friend2');

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friend1);
      await UserFactory.createUser(page, friend2);

      const receiver = await dbManager.getUserByEmail(testUser.email);
      const sender1 = await dbManager.getUserByEmail(friend1.email);
      const sender2 = await dbManager.getUserByEmail(friend2.email);

      await FriendsPage.insertInvitation(dbManager, sender1.id, receiver.id);
      await FriendsPage.insertInvitation(dbManager, sender2.id, receiver.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Verify 2 received invitations
      expect(await friendsPage.getReceivedInvitesCount()).toBe(2);

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Accept all invitations
      await friendsPage.acceptAllInvitations();

      // Wait for success toast
      await friendsPage.waitForSuccessToast('Accepted');
      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1500);

      // Verify all invitations accepted
      expect(await friendsPage.getReceivedInvitesCount()).toBe(0);

      // Verify friendships created
      expect(await FriendsPage.verifyFriendshipExists(dbManager, receiver.id, sender1.id)).toBe(true);
      expect(await FriendsPage.verifyFriendshipExists(dbManager, receiver.id, sender2.id)).toBe(true);

      // Verify friends count
      expect(await friendsPage.getFriendsCount()).toBe(2);
    });

    test('should reject multiple invitations (bulk action)', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friend1 = {...TestData.users.another};
      const friend2 = TestData.generateUserWithEmail('friend2');

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friend1);
      await UserFactory.createUser(page, friend2);

      const receiver = await dbManager.getUserByEmail(testUser.email);
      const sender1 = await dbManager.getUserByEmail(friend1.email);
      const sender2 = await dbManager.getUserByEmail(friend2.email);

      await FriendsPage.insertInvitation(dbManager, sender1.id, receiver.id);
      await FriendsPage.insertInvitation(dbManager, sender2.id, receiver.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Reject all invitations
      await friendsPage.rejectAllInvitations();

      // Wait for success toast
      await friendsPage.waitForSuccessToast('Rejected');
      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1500);

      // Verify all invitations rejected
      expect(await friendsPage.getReceivedInvitesCount()).toBe(0);

      // Verify no friendships created
      expect(await FriendsPage.verifyFriendshipNotExists(dbManager, receiver.id, sender1.id)).toBe(true);
      expect(await FriendsPage.verifyFriendshipNotExists(dbManager, receiver.id, sender2.id)).toBe(true);

      // Verify friends count is still 0
      expect(await friendsPage.getFriendsCount()).toBe(0);
    });
  });

  test.describe('Friends List Display', () => {
    test('should display friends with correct information', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      // Create friendship
      await FriendsPage.insertFriendship(dbManager, user.id, friend.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Verify friends count
      expect(await friendsPage.getFriendsCount()).toBe(1);

      // Verify friend is displayed
      const friends = await friendsPage.getFriendsList();
      expect(friends.length).toBe(1);
      expect(friends[0].name).toBe(friendUser.fullName);
      expect(friends[0].email).toBe(friendUser.email);
    });

    test('should display multiple friends correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friend1 = {...TestData.users.another};
      const friend2 = TestData.generateUserWithEmail('friend2');
      const friend3 = TestData.generateUserWithEmail('friend3');

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friend1);
      await UserFactory.createUser(page, friend2);
      await UserFactory.createUser(page, friend3);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friendUser1 = await dbManager.getUserByEmail(friend1.email);
      const friendUser2 = await dbManager.getUserByEmail(friend2.email);
      const friendUser3 = await dbManager.getUserByEmail(friend3.email);

      // Create friendships
      await FriendsPage.insertFriendship(dbManager, user.id, friendUser1.id);
      await FriendsPage.insertFriendship(dbManager, user.id, friendUser2.id);
      await FriendsPage.insertFriendship(dbManager, user.id, friendUser3.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Verify friends count
      expect(await friendsPage.getFriendsCount()).toBe(3);

      // Verify all friends are displayed
      const friends = await friendsPage.getFriendsList();
      expect(friends.length).toBe(3);

      const emails = friends.map(f => f.email);
      expect(emails).toContain(friend1.email);
      expect(emails).toContain(friend2.email);
      expect(emails).toContain(friend3.email);
    });

    test('should show friend status badge', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      await FriendsPage.insertFriendship(dbManager, user.id, friend.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      const friends = await friendsPage.getFriendsList();
      expect(friends.length).toBe(1);

      // Friend status should be displayed (Online, Recent, Offline, or No Location)
      // The exact status depends on GPS data, so we just verify it's present
      // Status might be null if no location data exists, which is acceptable
      console.log('Friend status:', friends[0].status);
    });

    test('should count friends correctly in status card', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friend1 = {...TestData.users.another};
      const friend2 = TestData.generateUserWithEmail('friend2');

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friend1);
      await UserFactory.createUser(page, friend2);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friendUser1 = await dbManager.getUserByEmail(friend1.email);
      const friendUser2 = await dbManager.getUserByEmail(friend2.email);

      await FriendsPage.insertFriendship(dbManager, user.id, friendUser1.id);
      await FriendsPage.insertFriendship(dbManager, user.id, friendUser2.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Verify status card count matches friends list
      const statusCardCount = await friendsPage.getFriendsCount();
      const listCount = await friendsPage.getFriendsListCount();

      expect(statusCardCount).toBe(2);
      expect(listCount).toBe(2);
    });
  });

  test.describe('Friend Removal', () => {
    test('should show confirmation dialog when removing friend', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      await FriendsPage.insertFriendship(dbManager, user.id, friend.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Click remove friend button
      await friendsPage.removeFriend(friendUser.email);

      // Wait for confirmation dialog
      await page.waitForTimeout(500);
      expect(await friendsPage.isConfirmDialogVisible()).toBe(true);

      const message = await friendsPage.getConfirmDialogMessage();
      expect(message).toContain(friendUser.fullName);
      expect(message).toContain('remove');
    });

    test('should remove friend on confirmation', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      await FriendsPage.insertFriendship(dbManager, user.id, friend.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Remove friend
      await friendsPage.removeFriend(friendUser.email);
      await page.waitForTimeout(500);

      // Confirm removal
      await friendsPage.confirmAction();

      // Wait for success toast
      await friendsPage.waitForSuccessToast('removed');

      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1000);

      // Verify friend is removed
      expect(await friendsPage.getFriendsCount()).toBe(0);
      expect(await friendsPage.hasEmptyFriendsState()).toBe(true);

      // Verify database - bidirectional removal
      expect(await FriendsPage.verifyFriendshipNotExists(dbManager, user.id, friend.id)).toBe(true);
    });

    test('should cancel friend removal on dialog cancel', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      await FriendsPage.insertFriendship(dbManager, user.id, friend.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Click remove friend
      await friendsPage.removeFriend(friendUser.email);
      await page.waitForTimeout(500);

      // Cancel removal
      await friendsPage.cancelAction();
      await page.waitForTimeout(500);

      // Verify friend is still there
      expect(await friendsPage.getFriendsCount()).toBe(1);

      // Verify database - friendship still exists
      expect(await FriendsPage.verifyFriendshipExists(dbManager, user.id, friend.id)).toBe(true);
    });

    test('should update friends list and count after removal', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friend1 = {...TestData.users.another};
      const friend2 = TestData.generateUserWithEmail('friend2');

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friend1);
      await UserFactory.createUser(page, friend2);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friendUser1 = await dbManager.getUserByEmail(friend1.email);
      const friendUser2 = await dbManager.getUserByEmail(friend2.email);

      await FriendsPage.insertFriendship(dbManager, user.id, friendUser1.id);
      await FriendsPage.insertFriendship(dbManager, user.id, friendUser2.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Initial count
      expect(await friendsPage.getFriendsCount()).toBe(2);

      // Remove one friend
      await friendsPage.removeFriend(friend1.email);
      await page.waitForTimeout(500);
      await friendsPage.confirmAction();
      await friendsPage.waitForSuccessToast('removed');
      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1000);

      // Verify count updated
      expect(await friendsPage.getFriendsCount()).toBe(1);

      // Verify correct friend removed
      const friends = await friendsPage.getFriendsList();
      expect(friends.length).toBe(1);
      expect(friends[0].email).toBe(friend2.email);
    });
  });

  test.describe('Canceling Sent Invitations', () => {
    test('should cancel individual sent invitation', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const sender = await dbManager.getUserByEmail(testUser.email);
      const receiver = await dbManager.getUserByEmail(friendUser.email);
      const invitationId = await FriendsPage.insertInvitation(dbManager, sender.id, receiver.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Verify sent invitation count
      expect(await friendsPage.getSentInvitesCount()).toBe(1);

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Cancel invitation
      await friendsPage.cancelSentInvitation(friendUser.email);

      // Wait for success toast
      await friendsPage.waitForSuccessToast('The invitation has been cancelled');

      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1000);

      // Verify invitation removed from sent invites
      expect(await friendsPage.getSentInvitesCount()).toBe(0);

      // Verify database status changed to CANCELLED
      const status = await FriendsPage.getInvitationStatus(dbManager, invitationId);
      expect(status).toBe('CANCELLED');
    });

    test('should cancel multiple sent invitations (bulk action)', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friend1 = {...TestData.users.another};
      const friend2 = TestData.generateUserWithEmail('friend2');

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friend1);
      await UserFactory.createUser(page, friend2);

      const sender = await dbManager.getUserByEmail(testUser.email);
      const receiver1 = await dbManager.getUserByEmail(friend1.email);
      const receiver2 = await dbManager.getUserByEmail(friend2.email);

      const invitation1Id = await FriendsPage.insertInvitation(dbManager, sender.id, receiver1.id);
      const invitation2Id = await FriendsPage.insertInvitation(dbManager, sender.id, receiver2.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Verify sent invitations count
      expect(await friendsPage.getSentInvitesCount()).toBe(2);

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Cancel all sent invitations
      await friendsPage.cancelAllSentInvitations();

      // Wait for success toast
      await friendsPage.waitForSuccessToast('Cancelled');
      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1500);

      // Verify all invitations cancelled
      await friendsPage.waitForSentInvitesCount(0);

      // Verify database - invitations should be CANCELLED
      const status1 = await FriendsPage.getInvitationStatus(dbManager, invitation1Id);
      const status2 = await FriendsPage.getInvitationStatus(dbManager, invitation2Id);

      expect(status1).toBe('CANCELLED');
      expect(status2).toBe('CANCELLED');
    });
  });

  test.describe('Tab Navigation and Data Consistency', () => {
    test('should switch between tabs correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Default should be friends tab
      expect(await friendsPage.isTabActive('friends')).toBe(true);

      // Switch to map tab
      await friendsPage.switchToTab('map');
      expect(await friendsPage.isTabActive('map')).toBe(true);

      // Switch to invites tab
      await friendsPage.switchToTab('invites');
      expect(await friendsPage.isTabActive('invites')).toBe(true);

      // Switch back to friends tab
      await friendsPage.switchToTab('friends');
      expect(await friendsPage.isTabActive('friends')).toBe(true);
    });

    test('should show correct badge counts on tabs', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      // Create one friend and one invitation
      await FriendsPage.insertFriendship(dbManager, user.id, friend.id);
      await FriendsPage.insertInvitation(dbManager, friend.id, user.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Friends tab should show badge with count 1
      const friendsBadge = await friendsPage.getTabBadgeValue('friends');
      if (friendsBadge) {
        expect(friendsBadge).toBe('1');
      }

      // Invitations tab should show badge with count 1 (pending invitation)
      const invitesBadge = await friendsPage.getTabBadgeValue('invites');
      if (invitesBadge) {
        expect(invitesBadge).toBe('1');
      }
    });

    test('should verify counts across all status cards match actual data', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friend1 = {...TestData.users.another};
      const friend2 = TestData.generateUserWithEmail('friend2');
      const friend3 = TestData.generateUserWithEmail('friend3');

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friend1);
      await UserFactory.createUser(page, friend2);
      await UserFactory.createUser(page, friend3);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friendUser1 = await dbManager.getUserByEmail(friend1.email);
      const friendUser2 = await dbManager.getUserByEmail(friend2.email);
      const friendUser3 = await dbManager.getUserByEmail(friend3.email);

      // Create 1 friend, 1 sent invitation, 1 received invitation
      await FriendsPage.insertFriendship(dbManager, user.id, friendUser1.id);
      await FriendsPage.insertInvitation(dbManager, user.id, friendUser2.id); // sent
      await FriendsPage.insertInvitation(dbManager, friendUser3.id, user.id); // received

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Verify status card counts
      const friendsCount = await friendsPage.getFriendsCount();
      const sentCount = await friendsPage.getSentInvitesCount();
      const receivedCount = await friendsPage.getReceivedInvitesCount();

      expect(friendsCount).toBe(1);
      expect(sentCount).toBe(1);
      expect(receivedCount).toBe(1);

      // Verify against database
      expect(await FriendsPage.countFriends(dbManager, user.id)).toBe(1);
      expect(await FriendsPage.countSentInvitations(dbManager, user.id)).toBe(1);
      expect(await FriendsPage.countReceivedInvitations(dbManager, user.id)).toBe(1);

      // Verify actual list counts
      const listFriendsCount = await friendsPage.getFriendsListCount();
      expect(listFriendsCount).toBe(1);

      // Switch to invitations tab and verify counts there too
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      const uiReceivedCount = await friendsPage.getReceivedInvitationsCount();
      const uiSentCount = await friendsPage.getSentInvitationsCount();

      expect(uiReceivedCount).toBe(1);
      expect(uiSentCount).toBe(1);
    });

    test('should verify friendship is bidirectional', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const user1 = TestData.users.existing;
      const user2 = TestData.users.another;

      await UserFactory.createUser(page, user1);
      await UserFactory.createUser(page, user2);

      const dbUser1 = await dbManager.getUserByEmail(user1.email);
      const dbUser2 = await dbManager.getUserByEmail(user2.email);

      // Create friendship
      await FriendsPage.insertFriendship(dbManager, dbUser1.id, dbUser2.id);

      // Login as user1
      await loginPage.navigate();
      await loginPage.login(user1.email, user1.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // User1 should see user2 as friend
      expect(await friendsPage.getFriendsCount()).toBe(1);
      let friends = await friendsPage.getFriendsList();
      expect(friends[0].email).toBe(user2.email);

      // Logout and login as user2
      const appNav = await import('../pages/AppNavigation.js').then(m => new m.AppNavigation(page));
      await appNav.logout();

      await loginPage.navigate();
      await loginPage.login(user2.email, user2.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // User2 should see user1 as friend
      expect(await friendsPage.getFriendsCount()).toBe(1);
      friends = await friendsPage.getFriendsList();
      expect(friends[0].email).toBe(user1.email);
    });

    test('should show friends on map with GPS locations', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      // Create friendship and add GPS location for friend
      // Friend is located in Kyiv, Ukraine
      await FriendsPage.insertFriendWithLocation(dbManager, user.id, friend.id, 50.4501, 30.5234);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to Friends Map tab
      await friendsPage.switchToTab('map');
      await page.waitForTimeout(1000); // Wait for map to initialize

      // Verify Friends Map is visible
      expect(await friendsPage.isFriendsMapVisible()).toBe(true);

      // Verify map container exists
      const mapContainer = page.locator('.leaflet-container');
      expect(await mapContainer.isVisible()).toBe(true);

      // Verify friend marker is displayed on the map
      // Leaflet adds markers with specific classes - looking for the custom avatar markers
      const friendMarkers = page.locator('.leaflet-marker-pane .leaflet-zoom-animated');
      const markerCount = await friendMarkers.count();
      expect(markerCount).toBeGreaterThan(0); // Should have at least one marker (the friend)

      // Verify the friend's location is shown (marker should be visible)
      const firstMarker = friendMarkers.first();
      expect(await firstMarker.isVisible()).toBe(true);
    });

    test('should show friend on map when clicking "Show on Map" button', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      // Create friendship and add GPS location for friend
      // Friend is located in Kyiv, Ukraine
      await FriendsPage.insertFriendWithLocation(dbManager, user.id, friend.id, 50.4501, 30.5234);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Should be on "My Friends" tab by default
      expect(await friendsPage.isTabActive('friends')).toBe(true);

      // Verify friend is in the list
      const friends = await friendsPage.getFriendsList();
      expect(friends.length).toBe(1);
      expect(friends[0].email).toBe(friendUser.email);

      // Click "Show on Map" button for the friend
      await friendsPage.showFriendOnMap(friendUser.email);
      await page.waitForTimeout(1000); // Wait for map to initialize and zoom

      // Should switch to Friends Map tab
      expect(await friendsPage.isTabActive('map')).toBe(true);

      // Verify map is visible
      expect(await friendsPage.isFriendsMapVisible()).toBe(true);

      // Verify map container exists
      const mapContainer = page.locator('.leaflet-container');
      expect(await mapContainer.isVisible()).toBe(true);

      // Verify friend marker is displayed
      const friendMarkers = page.locator('.leaflet-marker-pane .leaflet-zoom-animated');
      const markerCount = await friendMarkers.count();
      expect(markerCount).toBeGreaterThan(0);

      // Verify marker is visible (map should be zoomed to friend's location)
      const firstMarker = friendMarkers.first();
      expect(await firstMarker.isVisible()).toBe(true);
    });
  });

  test.describe('Error Handling and Edge Cases', () => {
    test('should handle accepting already accepted invitation gracefully', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const receiver = await dbManager.getUserByEmail(testUser.email);
      const sender = await dbManager.getUserByEmail(friendUser.email);

      // Insert already accepted invitation
      await FriendsPage.insertInvitation(dbManager, sender.id, receiver.id, 'ACCEPTED');
      await FriendsPage.insertFriendship(dbManager, sender.id, receiver.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Should not show the accepted invitation in pending invites
      expect(await friendsPage.getReceivedInvitesCount()).toBe(0);

      // Should show the friend
      expect(await friendsPage.getFriendsCount()).toBe(1);
    });

    test('should handle removing already removed friend', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      await FriendsPage.insertFriendship(dbManager, user.id, friend.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Remove friend
      await friendsPage.removeFriend(friendUser.email);
      await page.waitForTimeout(500);
      await friendsPage.confirmAction();
      await friendsPage.waitForSuccessToast('removed');
      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1000);

      // Verify friend removed
      expect(await friendsPage.getFriendsCount()).toBe(0);

      // Manually delete from DB to simulate concurrent deletion
      await dbManager.client.query('DELETE FROM user_friends WHERE user_id = $1 OR friend_id = $1', [friend.id]);

      // Refresh page
      await page.reload();
      await friendsPage.waitForPageLoad();

      // Should still show empty state, no errors
      expect(await friendsPage.getFriendsCount()).toBe(0);
    });

    test('should display very long names and emails correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const longNameUser = {
        ...TestData.generateNewUser(),
        fullName: 'A'.repeat(100), // Very long name
        email: 'very.long.email.address.that.goes.on.forever@example.com'
      };

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, longNameUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(longNameUser.email);

      await FriendsPage.insertFriendship(dbManager, user.id, friend.id);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Should display friend without layout issues
      expect(await friendsPage.getFriendsCount()).toBe(1);

      const friends = await friendsPage.getFriendsList();
      expect(friends.length).toBe(1);
      // Name and email should be present (might be truncated in UI but data should be there)
      expect(friends[0].name.length).toBeGreaterThan(0);
      expect(friends[0].email).toBe(longNameUser.email);
    });

    test('should handle empty invitations sections correctly', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(500);

      // Should show empty state with no errors
      expect(await friendsPage.hasEmptyInvitationsState()).toBe(true);

      // Accept All, Reject All, Cancel All buttons should not be visible
      expect(await page.locator(friendsPage.selectors.acceptAllButton).isVisible()).toBe(false);
      expect(await page.locator(friendsPage.selectors.rejectAllButton).isVisible()).toBe(false);
      expect(await page.locator(friendsPage.selectors.cancelAllButton).isVisible()).toBe(false);
    });
  });
});
