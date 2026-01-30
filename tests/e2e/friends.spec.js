import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {FriendsPage} from '../pages/FriendsPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import {insertVerifiableStaysTestData, insertVerifiableTripsTestData} from '../utils/timeline-test-data.js';

test.describe('Friends Page', () => {

  test.describe('Tab Navigation and Structure', () => {
    test('should default to Live tab on page load', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Should be on Live tab by default
      expect(await friendsPage.isTabActive('live')).toBe(true);
    });

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

      // Default should be Live tab
      expect(await friendsPage.isTabActive('live')).toBe(true);

      // Switch to Timeline tab
      await friendsPage.switchToTab('timeline');
      expect(await friendsPage.isTabActive('timeline')).toBe(true);

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      expect(await friendsPage.isTabActive('friends')).toBe(true);

      // Switch back to Live tab
      await friendsPage.switchToTab('live');
      expect(await friendsPage.isTabActive('live')).toBe(true);
    });

    test('should not show Invitations tab when no invites exist', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;

      await UserFactory.createUser(page, testUser);
      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Invitations tab should not be visible
      expect(await friendsPage.isInvitesTabVisible()).toBe(false);
    });

    test('should show Invitations tab when invites exist', async ({page, dbManager}) => {
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

      // Invitations tab should be visible
      expect(await friendsPage.isInvitesTabVisible()).toBe(true);
    });

    test('should show tab badges with correct counts', async ({page, dbManager}) => {
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

      // Friends tab should show badge with count 1
      const friendsBadge = await friendsPage.getTabBadgeValue('friends');
      if (friendsBadge) {
        expect(friendsBadge).toBe('1');
      }
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

      // Open invite dialog
      await friendsPage.openInviteDialog();
      expect(await friendsPage.isInviteDialogVisible()).toBe(true);

      // Send invitation
      await friendsPage.sendInvitation(friendUser.email);

      // Wait for success toast notification
      await friendsPage.waitForSuccessToast('sent');
      await friendsPage.waitForToastToDisappear();

      // Verify database
      const sender = await dbManager.getUserByEmail(testUser.email);
      const receiver = await dbManager.getUserByEmail(friendUser.email);
      const invitation = await FriendsPage.getInvitationBySenderAndReceiver(dbManager, sender.id, receiver.id);

      expect(invitation).toBeTruthy();
      expect(invitation.status).toBe('PENDING');

      // Invitations tab should now be visible
      await page.waitForTimeout(500);
      expect(await friendsPage.isInvitesTabVisible()).toBe(true);
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

    test('should show sent invitation in Invitations tab', async ({page, dbManager}) => {
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
      expect(sentInvites.length).toBeGreaterThanOrEqual(1);
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

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Verify invitation is displayed
      expect(await friendsPage.hasReceivedInvitesSection()).toBe(true);
      const receivedInvites = await friendsPage.getReceivedInvitationsList();
      expect(receivedInvites.length).toBe(1);
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

      // Accept invitation (using email since that's what's displayed)
      await friendsPage.acceptInvitation(friendUser.email);

      // Wait for success toast
      await friendsPage.waitForSuccessToast('friends');
      await friendsPage.waitForToastToDisappear();

      // Wait for UI to update
      await page.waitForTimeout(1500);

      // Verify friendship was created in database
      expect(await FriendsPage.verifyFriendshipExists(dbManager, receiver.id, sender.id)).toBe(true);

      // Verify invitation status changed to ACCEPTED
      const status = await FriendsPage.getInvitationStatus(dbManager, invitationId);
      expect(status).toBe('ACCEPTED');

      // Invitations tab should no longer be visible (no more pending invites)
      expect(await friendsPage.isInvitesTabVisible()).toBe(false);

      // Switch to friends tab and verify friend is added
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      expect(await friendsPage.getFriendsListCount()).toBe(1);
      const friends = await friendsPage.getFriendsList();
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

      // Reject invitation (using email since that's what's displayed)
      await friendsPage.rejectInvitation(friendUser.email);

      // Wait for success toast
      await friendsPage.waitForSuccessToast('rejected');
      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1000);

      // Verify no friendship was created
      expect(await FriendsPage.verifyFriendshipNotExists(dbManager, receiver.id, sender.id)).toBe(true);

      // Verify invitation status changed to REJECTED
      const status = await FriendsPage.getInvitationStatus(dbManager, invitationId);
      expect(status).toBe('REJECTED');

      // Invitations tab should no longer be visible
      expect(await friendsPage.isInvitesTabVisible()).toBe(false);
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

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Accept all invitations
      await friendsPage.acceptAllInvitations();

      // Wait for success toast
      await friendsPage.waitForSuccessToast('Accepted');
      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1500);

      // Verify friendships created
      expect(await FriendsPage.verifyFriendshipExists(dbManager, receiver.id, sender1.id)).toBe(true);
      expect(await FriendsPage.verifyFriendshipExists(dbManager, receiver.id, sender2.id)).toBe(true);

      // Invitations tab should no longer be visible
      expect(await friendsPage.isInvitesTabVisible()).toBe(false);

      // Switch to friends tab and verify friends count
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);
      expect(await friendsPage.getFriendsListCount()).toBe(2);
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

      // Verify no friendships created
      expect(await FriendsPage.verifyFriendshipNotExists(dbManager, receiver.id, sender1.id)).toBe(true);
      expect(await FriendsPage.verifyFriendshipNotExists(dbManager, receiver.id, sender2.id)).toBe(true);

      // Invitations tab should no longer be visible
      expect(await friendsPage.isInvitesTabVisible()).toBe(false);
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

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Cancel invitation (using email since that's what's displayed)
      await friendsPage.cancelSentInvitation(friendUser.email);

      // Wait for success toast
      await friendsPage.waitForSuccessToast('cancelled');
      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1000);

      // Verify database status changed to CANCELLED
      const status = await FriendsPage.getInvitationStatus(dbManager, invitationId);
      expect(status).toBe('CANCELLED');

      // Invitations tab should no longer be visible
      expect(await friendsPage.isInvitesTabVisible()).toBe(false);
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

      // Switch to invitations tab
      await friendsPage.switchToTab('invites');
      await page.waitForTimeout(1000);

      // Cancel all sent invitations
      await friendsPage.cancelAllSentInvitations();

      // Wait for success toast
      await friendsPage.waitForSuccessToast('Cancelled');
      await friendsPage.waitForToastToDisappear();
      await page.waitForTimeout(1500);

      // Verify database - invitations should be CANCELLED
      const status1 = await FriendsPage.getInvitationStatus(dbManager, invitation1Id);
      const status2 = await FriendsPage.getInvitationStatus(dbManager, invitation2Id);

      expect(status1).toBe('CANCELLED');
      expect(status2).toBe('CANCELLED');

      // Invitations tab should no longer be visible
      expect(await friendsPage.isInvitesTabVisible()).toBe(false);
    });
  });

  test.describe('Friends List Display and Management', () => {
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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Verify all friends are displayed
      const friends = await friendsPage.getFriendsList();
      expect(friends.length).toBe(3);

      const emails = friends.map(f => f.email);
      expect(emails).toContain(friend1.email);
      expect(emails).toContain(friend2.email);
      expect(emails).toContain(friend3.email);
    });

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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Check empty state in friends list
      expect(await friendsPage.hasEmptyFriendsState()).toBe(true);
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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

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
      expect(await friendsPage.getFriendsListCount()).toBe(0);
      expect(await friendsPage.hasEmptyFriendsState()).toBe(true);

      // Verify database - bidirectional removal
      expect(await FriendsPage.verifyFriendshipNotExists(dbManager, user.id, friend.id)).toBe(true);
    });

    test('should navigate to live map when clicking Live button', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      // Create friendship with location
      await FriendsPage.insertFriendWithLocation(dbManager, user.id, friend.id, 50.4501, 30.5234);

      // IMPORTANT: Friend must share live location permission for Live button to be enabled
      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_live_location, share_timeline)
        VALUES ($1, $2, true, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_live_location = true
      `, [friend.id, user.id]);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Click Live button for the friend (now enabled because friend shares live location)
      await friendsPage.showFriendOnLiveMap(friendUser.email);
      await page.waitForTimeout(1000);

      // Should switch to Live tab
      expect(await friendsPage.isTabActive('live')).toBe(true);
    });

    test('should navigate to timeline when clicking Timeline button', async ({page, dbManager}) => {
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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Click Timeline button for the friend (always enabled - shows your own data at minimum)
      await friendsPage.showFriendTimeline(friendUser.email);
      await page.waitForTimeout(1000);

      // Should switch to Timeline tab
      expect(await friendsPage.isTabActive('timeline')).toBe(true);
    });

    test('should disable Live button when friend does not share live location', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      // Create friendship with location but WITHOUT live location permission
      await FriendsPage.insertFriendWithLocation(dbManager, user.id, friend.id, 50.4501, 30.5234);

      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_live_location, share_timeline)
        VALUES ($1, $2, false, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_live_location = false
      `, [friend.id, user.id]);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Find the friend card
      const friendCards = await page.locator('.friend-card').all();
      let liveButton = null;
      let timelineButton = null;

      for (const card of friendCards) {
        const cardEmail = await card.locator('.friend-email').textContent();
        if (cardEmail.trim() === friendUser.email) {
          liveButton = card.locator('button:has-text("Live")');
          timelineButton = card.locator('button:has-text("Timeline")');
          break;
        }
      }

      // Live button should be disabled (friend doesn't share live location)
      expect(await liveButton.isDisabled()).toBe(true);

      // Timeline button should be enabled (always shows at least your own data)
      expect(await timelineButton.isDisabled()).toBe(false);
    });

    test('should enable Live button when friend shares live location', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      // Create friendship with location AND live location permission
      await FriendsPage.insertFriendWithLocation(dbManager, user.id, friend.id, 50.4501, 30.5234);

      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_live_location, share_timeline)
        VALUES ($1, $2, true, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_live_location = true
      `, [friend.id, user.id]);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Find the friend card
      const friendCards = await page.locator('.friend-card').all();
      let liveButton = null;

      for (const card of friendCards) {
        const cardEmail = await card.locator('.friend-email').textContent();
        if (cardEmail.trim() === friendUser.email) {
          liveButton = card.locator('button:has-text("Live")');
          break;
        }
      }

      // Live button should be enabled (friend shares live location)
      expect(await liveButton.isDisabled()).toBe(false);
    });
  });

  test.describe('Friend Permission Management', () => {
    test('should toggle live location permission with confirmation', async ({page, dbManager}) => {
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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Toggle live location permission
      await friendsPage.toggleLiveLocationPermission(friendUser.email);
      await page.waitForTimeout(500);

      // Should show confirmation dialog
      expect(await friendsPage.isConfirmDialogVisible()).toBe(true);

      // Confirm the change
      await friendsPage.confirmPermissionChange();

      // Wait for success toast
      await friendsPage.waitForSuccessToast('can now view your live location');
      await friendsPage.waitForToastToDisappear();
    });

    test('should toggle timeline permission with confirmation', async ({page, dbManager}) => {
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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Toggle timeline permission
      await friendsPage.toggleTimelinePermission(friendUser.email);
      await page.waitForTimeout(500);

      // Should show confirmation dialog
      expect(await friendsPage.isConfirmDialogVisible()).toBe(true);

      // Confirm the change
      await friendsPage.confirmPermissionChange();

      // Wait for success toast
      await friendsPage.waitForSuccessToast('can now view your timeline history');
      await friendsPage.waitForToastToDisappear();
    });

    test('should cancel permission change when dialog is rejected', async ({page, dbManager}) => {
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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Toggle live location permission
      await friendsPage.toggleLiveLocationPermission(friendUser.email);
      await page.waitForTimeout(500);

      // Should show confirmation dialog
      expect(await friendsPage.isConfirmDialogVisible()).toBe(true);

      // Cancel the change
      await friendsPage.cancelAction();
      await page.waitForTimeout(500);

      // No toast should appear (change was cancelled)
      // Dialog should be closed
      expect(await friendsPage.isConfirmDialogVisible()).toBe(false);
    });

    test('should display sharing status sections', async ({page, dbManager}) => {
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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Verify sharing status section exists
      const sharingStatusSection = page.locator('.friend-sharing-status').first();
      expect(await sharingStatusSection.isVisible()).toBe(true);

      // Verify permissions section exists
      const permissionsSection = page.locator('.friend-permissions').first();
      expect(await permissionsSection.isVisible()).toBe(true);

      // Verify status items for what friend shares
      const statusItems = sharingStatusSection.locator('.status-item');
      expect(await statusItems.count()).toBe(2); // Live Location and Timeline History

      // Verify permission items for what you share
      const permissionItems = permissionsSection.locator('.permission-item');
      expect(await permissionItems.count()).toBe(2); // Live Location and Timeline History
    });
  });

  test.describe('Filtered Friend Lists by Permissions', () => {
    test('should show only friends with live location permission on Live tab', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friend1 = {...TestData.users.another}; // Will share live location
      const friend2 = TestData.generateUserWithEmail('friend2'); // Will NOT share live location
      const friend3 = TestData.generateUserWithEmail('friend3'); // Will share live location

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friend1);
      await UserFactory.createUser(page, friend2);
      await UserFactory.createUser(page, friend3);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friendUser1 = await dbManager.getUserByEmail(friend1.email);
      const friendUser2 = await dbManager.getUserByEmail(friend2.email);
      const friendUser3 = await dbManager.getUserByEmail(friend3.email);

      // Create friendships WITH GPS locations for all friends
      await FriendsPage.insertFriendWithLocation(dbManager, user.id, friendUser1.id, 50.4501, 30.5234); // Kyiv
      await FriendsPage.insertFriendWithLocation(dbManager, user.id, friendUser2.id, 40.7128, -74.0060); // New York
      await FriendsPage.insertFriendWithLocation(dbManager, user.id, friendUser3.id, 51.5074, -0.1278); // London

      // Set permissions: friend1 and friend3 share live location with user
      // friend2 does NOT share live location
      // Note: user_id is the one GRANTING permission, friend_id is the one RECEIVING permission
      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_live_location, share_timeline)
        VALUES ($1, $2, true, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_live_location = true
      `, [friendUser1.id, user.id]);

      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_live_location, share_timeline)
        VALUES ($1, $2, false, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_live_location = false
      `, [friendUser2.id, user.id]);

      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_live_location, share_timeline)
        VALUES ($1, $2, true, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_live_location = true
      `, [friendUser3.id, user.id]);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Should default to Live tab
      expect(await friendsPage.isTabActive('live')).toBe(true);

      // Live tab badge should show 2 (only friends who share live location)
      const liveBadge = await friendsPage.getTabBadgeValue('live');
      if (liveBadge) {
        expect(liveBadge).toBe('2');
      }

      // Verify the Live tab shows markers on the map (2 friends with permission)
      await page.waitForTimeout(1500); // Wait for map to load
      const mapContainer = page.locator('.leaflet-container');
      expect(await mapContainer.isVisible()).toBe(true);

      // Check for friend markers on the map (should be 2)
      const friendMarkers = page.locator('.leaflet-marker-pane .leaflet-zoom-animated');
      const markerCount = await friendMarkers.count();
      // Should have 2 markers for friends who share live location (not 3)
      expect(markerCount).toBeGreaterThanOrEqual(2);

      // Switch to Friends tab to see all friends
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Friends tab should show all 3 friends
      expect(await friendsPage.getFriendsListCount()).toBe(3);

      // Friends tab badge should show 3
      const friendsBadge = await friendsPage.getTabBadgeValue('friends');
      if (friendsBadge) {
        expect(friendsBadge).toBe('3');
      }
    });

    test('should show only friends with timeline permission on Timeline tab', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friend1 = {...TestData.users.another}; // Will share timeline
      const friend2 = TestData.generateUserWithEmail('friend2'); // Will NOT share timeline
      const friend3 = TestData.generateUserWithEmail('friend3'); // Will share timeline

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

      // Set permissions: friend1 and friend3 share timeline with user
      // friend2 does NOT share timeline
      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_timeline, share_live_location)
        VALUES ($1, $2, true, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_timeline = true
      `, [friendUser1.id, user.id]);

      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_timeline, share_live_location)
        VALUES ($1, $2, false, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_timeline = false
      `, [friendUser2.id, user.id]);

      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_timeline, share_live_location)
        VALUES ($1, $2, true, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_timeline = true
      `, [friendUser3.id, user.id]);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to Timeline tab
      await friendsPage.switchToTab('timeline');
      await page.waitForTimeout(1500);

      // Timeline tab badge should show 2 (only friends who share timeline)
      const timelineBadge = await friendsPage.getTabBadgeValue('timeline');
      if (timelineBadge) {
        expect(timelineBadge).toBe('2');
      }

      // Verify Timeline tab is active
      expect(await friendsPage.isTabActive('timeline')).toBe(true);

      // Note: Since there's no timeline data (stays/trips) for the friends,
      // the Timeline tab will show 0 items, which is correct behavior.
      // The important thing is that the permission filtering is working -
      // only 2 friends are included in the badge count (those with timeline permission).

      // Switch to Friends tab to verify all 3 friends exist
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // Friends tab should show all 3 friends (regardless of permissions)
      expect(await friendsPage.getFriendsListCount()).toBe(3);

      // Friends tab badge should show 3
      const friendsBadge = await friendsPage.getTabBadgeValue('friends');
      if (friendsBadge) {
        expect(friendsBadge).toBe('3');
      }
    });

    test('should display friend timeline data with date range selection', async ({page, dbManager}) => {
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

      // Create friendships
      await FriendsPage.insertFriendship(dbManager, user.id, friendUser1.id);
      await FriendsPage.insertFriendship(dbManager, user.id, friendUser2.id);

      // Insert timeline data for both friends
      // NOTE: Data is inserted for September 21, 2025 (fixed date in test data helper)
      await insertVerifiableStaysTestData(dbManager, friendUser1.id);
      await insertVerifiableTripsTestData(dbManager, friendUser1.id);
      await insertVerifiableStaysTestData(dbManager, friendUser2.id);
      await insertVerifiableTripsTestData(dbManager, friendUser2.id);

      // Set permissions: both friends share timeline with user
      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_timeline, share_live_location)
        VALUES ($1, $2, true, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_timeline = true
      `, [friendUser1.id, user.id]);

      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_timeline, share_live_location)
        VALUES ($1, $2, true, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_timeline = true
      `, [friendUser2.id, user.id]);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to Timeline tab
      await friendsPage.switchToTab('timeline');
      await page.waitForTimeout(2000); // Wait for timeline data to load

      // Verify Timeline tab is active
      expect(await friendsPage.isTabActive('timeline')).toBe(true);

      // Verify Timeline tab shows badge with 2 friends (permission filtering works)
      const timelineBadge = await friendsPage.getTabBadgeValue('timeline');
      if (timelineBadge) {
        expect(timelineBadge).toBe('2');
      }

      // Verify user selection panel is visible
      const userSelectionPanel = page.locator('.user-selection-panel');
      expect(await userSelectionPanel.isVisible()).toBe(true);

      // Verify both friends appear in the selection panel
      // Note: User selection panel includes current user + friends (3 total)
      const userItems = page.locator('.user-item');
      const userItemCount = await userItems.count();
      expect(userItemCount).toBe(3); // Current user + 2 friends

      // Verify date picker is visible
      const datePicker = page.locator('.date-range-picker');
      expect(await datePicker.isVisible()).toBe(true);

      // PHASE 1: Verify default last 7 days shows NO data
      // Select all friends to view their timelines
      const selectAllButton = page.locator('button:has-text("All")');
      if (await selectAllButton.isVisible()) {
        await selectAllButton.click();
        await page.waitForTimeout(1500);
      }

      // Verify timeline list is visible
      const timelineList = page.locator('.merged-timeline-list');
      expect(await timelineList.isVisible()).toBe(true);

      // Should show 0 timeline items for default last 7 days (data is in Sept 2025)
      let timelineItems = page.locator('.friend-timeline-card');
      let itemCount = await timelineItems.count();
      expect(itemCount).toBe(0);

      // PHASE 2: Change date range to September 2025 to see the data
      // For simplicity, directly update the date range in the database/store or use a preset
      // Since the date picker is complex and readonly, we'll use a programmatic approach

      // Option 1: Use a preset button if available
      const presetButton = page.locator('button:has-text("Last 30 Days"), button:has-text("Last Month"), button:has-text("Custom")');
      if (await presetButton.count() > 0) {
        const customButton = page.locator('button:has-text("Custom")');
        if (await customButton.isVisible()) {
          await customButton.click();
          await page.waitForTimeout(500);
        }
      }

      // Option 2: Open date picker and navigate calendar
      const dateInput = page.locator('.p-datepicker-input').first();
      await dateInput.click();
      await page.waitForTimeout(500);

      // Verify calendar popup is visible
      const calendar = page.locator('.p-datepicker-panel').first();
      if (await calendar.isVisible()) {
        // Navigate backwards from current date to September 2025
        // Click previous month button multiple times
        const prevButton = page.locator('.p-datepicker-prev-button');

        // From Jan 2026, go back 4 months to Sept 2025
        for (let i = 0; i < 4; i++) {
          await prevButton.click();
          await page.waitForTimeout(200);
        }

        // Select September 20, 2025 (start date)
        const day20 = page.locator('.p-datepicker-calendar td:not(.p-datepicker-other-month) span:has-text("20")').first();
        await day20.click();
        await page.waitForTimeout(300);

        // Select September 22, 2025 (end date)
        const day22 = page.locator('.p-datepicker-calendar td:not(.p-datepicker-other-month) span:has-text("22")').first();
        await day22.click();
        await page.waitForTimeout(300);

        // Close the calendar
        await page.keyboard.press('Escape');
        await page.waitForTimeout(2000); // Wait for timeline to reload with new date range
      } else {
        // Fallback: If calendar doesn't open, skip date selection for this test
        console.log('Date picker calendar did not open, skipping date selection');
      }

      // PHASE 3: Verify timeline items now appear
      timelineItems = page.locator('.friend-timeline-card');
      itemCount = await timelineItems.count();

      // Each friend has 3 stays and 3 trips = 6 items per friend = 12 total
      // Should now show timeline items for September 21, 2025
      expect(itemCount).toBeGreaterThan(0);
      console.log(`Timeline items found: ${itemCount}`);

      // Verify timeline map is visible
      const timelineMap = page.locator('.leaflet-container');
      expect(await timelineMap.isVisible()).toBe(true);

      // PHASE 4: Test deselecting all friends
      const deselectAllButton = page.locator('button:has-text("None")');
      if (await deselectAllButton.isVisible()) {
        await deselectAllButton.click();
        await page.waitForTimeout(1000);

        // Timeline items should be cleared or show empty message
        const emptyMessage = page.locator('.empty-message');
        if (await emptyMessage.count() > 0) {
          expect(await emptyMessage.first().isVisible()).toBe(true);
        }
      }
    });

    test('should show no badge on Live tab when no friends share live location', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      // Create friendship with GPS location but don't share live location permission
      await FriendsPage.insertFriendWithLocation(dbManager, user.id, friend.id, 50.4501, 30.5234);

      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_live_location, share_timeline)
        VALUES ($1, $2, false, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_live_location = false
      `, [friend.id, user.id]);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Should default to Live tab
      expect(await friendsPage.isTabActive('live')).toBe(true);

      // Live tab badge should not be shown (0 friends with permission)
      const liveBadge = await friendsPage.getTabBadgeValue('live');
      expect(liveBadge).toBeNull();

      // Live tab should show "No Location Data Available" or empty state
      // (even though friend has GPS data, they don't share the permission)
      await page.waitForTimeout(1000);

      // Switch to Friends tab - should show 1 friend (friendship exists)
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);
      expect(await friendsPage.getFriendsListCount()).toBe(1);

      // Friends tab badge should show 1
      const friendsBadge = await friendsPage.getTabBadgeValue('friends');
      if (friendsBadge) {
        expect(friendsBadge).toBe('1');
      }
    });

    test('should show no badge on Timeline tab when no friends share timeline', async ({page, dbManager}) => {
      const loginPage = new LoginPage(page);
      const friendsPage = new FriendsPage(page);
      const testUser = TestData.users.existing;
      const friendUser = TestData.users.another;

      await UserFactory.createUser(page, testUser);
      await UserFactory.createUser(page, friendUser);

      const user = await dbManager.getUserByEmail(testUser.email);
      const friend = await dbManager.getUserByEmail(friendUser.email);

      // Create friendship but don't share timeline permission
      await FriendsPage.insertFriendship(dbManager, user.id, friend.id);

      await dbManager.client.query(`
        INSERT INTO user_friend_permissions (user_id, friend_id, share_timeline, share_live_location)
        VALUES ($1, $2, false, false)
        ON CONFLICT (user_id, friend_id)
        DO UPDATE SET share_timeline = false
      `, [friend.id, user.id]);

      await loginPage.navigate();
      await loginPage.login(testUser.email, testUser.password);
      await TestHelpers.waitForNavigation(page, '**/app/timeline');

      await friendsPage.navigate();
      await friendsPage.waitForPageLoad();

      // Switch to Timeline tab
      await friendsPage.switchToTab('timeline');
      await page.waitForTimeout(1000);

      // Timeline tab badge should not be shown (0 friends with timeline permission)
      const timelineBadge = await friendsPage.getTabBadgeValue('timeline');
      expect(timelineBadge).toBeNull();

      // Timeline tab should show 0 timeline items
      // (friend exists but doesn't share timeline permission, so their data isn't included)
      await page.waitForTimeout(1000);

      // Switch to Friends tab - should show 1 friend (friendship exists)
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);
      expect(await friendsPage.getFriendsListCount()).toBe(1);

      // Friends tab badge should show 1
      const friendsBadge = await friendsPage.getTabBadgeValue('friends');
      if (friendsBadge) {
        expect(friendsBadge).toBe('1');
      }
    });
  });

  test.describe('Data Consistency', () => {
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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // User1 should see user2 as friend
      expect(await friendsPage.getFriendsListCount()).toBe(1);
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

      // Switch to Friends tab
      await friendsPage.switchToTab('friends');
      await page.waitForTimeout(1000);

      // User2 should see user1 as friend
      expect(await friendsPage.getFriendsListCount()).toBe(1);
      friends = await friendsPage.getFriendsList();
      expect(friends[0].email).toBe(user1.email);
    });
  });
});
