import { expect } from '@playwright/test';

export class FriendsPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Page elements
      pageTitle: 'h1:has-text("Friends & Connections")',
      pageDescription: 'text="Connect with friends to share location data"',

      // Status Overview Cards
      statusCards: {
        friends: '.status-card:has(.status-label:has-text("Friends"))',
        sentInvites: '.status-card:has(.status-label:has-text("Sent Invites"))',
        receivedInvites: '.status-card:has(.status-label:has-text("Received Invites"))'
      },
      statusNumber: '.status-number',
      statusBadge: '.status-badge',

      // Tabs
      tabs: {
        friends: '.p-tabmenu-item:has(.p-tabmenu-item-label:has-text("My Friends"))',
        map: '.p-tabmenu-item:has(.p-tabmenu-item-label:has-text("Friends Map"))',
        invites: '.p-tabmenu-item:has(.p-tabmenu-item-label:has-text("Invitations"))'
      },
      activeTab: '.p-tabmenu-item.p-tabmenu-item-active',
      tabBadge: '.p-badge',

      // Friends List (My Friends Tab)
      friendsContent: '.friends-content',
      friendsGrid: '.friends-grid',
      friendCard: '.friend-card',
      friendName: '.friend-name',
      friendEmail: '.friend-email',
      friendStatus: '.friend-status',
      friendStatusBadge: '.friend-status .p-badge',
      lastSeen: '.last-seen',
      friendLocation: '.friend-location',
      friendAvatar: '.friend-avatar',

      // Friend Actions
      showOnMapButton: 'button:has([class*="pi-map-marker"])',
      removeFriendButton: 'button:has([class*="pi-trash"])',

      // Invitations Tab
      invitesContent: '.invites-content',
      invitesSection: '.invites-section',

      // Received Invitations
      receivedInvitesSection: '.invites-section:has(.section-title:has-text("Received Invitations"))',
      receivedInvitesList: '.invites-section:has(.section-title:has-text("Received Invitations")) .invites-list',
      acceptAllButton: 'button:has-text("Accept All")',
      rejectAllButton: 'button:has-text("Reject All")',

      // Sent Invitations
      sentInvitesSection: '.invites-section:has(.section-title:has-text("Sent Invitations"))',
      sentInvitesList: '.invites-section:has(.section-title:has-text("Sent Invitations")) .invites-list',
      cancelAllButton: 'button:has-text("Cancel All")',

      // Invitation Items
      inviteItem: '.invite-item',
      inviteEmail: '.invite-email',
      inviteDate: '.invite-date',
      inviteAvatar: '.invite-avatar',
      acceptButton: 'button:has-text("Accept")',
      rejectButton: 'button:has-text("Reject")',
      cancelButton: 'button:has-text("Cancel")',
      pendingBadge: '.p-badge:has-text("Pending")',

      // Invite Friend Dialog
      inviteDialog: '.invite-dialog',
      inviteDialogHeader: '.p-dialog-header:has-text("Invite Friend")',
      friendEmailInput: '#friendEmail',
      sendInvitationButton: 'button:has-text("Send Invitation")',
      cancelDialogButton: 'button:has-text("Cancel")',
      inviteFriendButton: 'button:has-text("Invite Friend")',
      errorMessage: '.error-message',

      // Friends Map Tab
      mapContent: '.friends-map',
      mapCard: '.map-card',
      mapContainer: '.map-container',

      // Empty States
      emptyState: '.empty-state',
      emptyIcon: '.empty-icon',
      emptyTitle: '.empty-title',
      emptyDescription: '.empty-description',

      // Loading States
      loadingSpinner: '.p-progress-spinner',

      // Confirm Dialog
      confirmDialog: '.p-confirmdialog',
      confirmDialogMessage: '.p-confirmdialog-message',
      confirmDialogAccept: '.p-confirmdialog-accept',
      confirmDialogReject: '.p-confirmdialog-reject',

      // Toast Notifications
      toast: '.p-toast',
      toastMessage: '.p-toast-message',
      toastSummary: '.p-toast-summary',
      toastDetail: '.p-toast-detail'
    };
  }

  /**
   * Navigation Methods
   */
  async navigate() {
    await this.page.goto('/app/friends');
  }

  async isOnFriendsPage() {
    try {
      const url = this.page.url();
      const hasTitle = await this.page.locator(this.selectors.pageTitle).isVisible().catch(() => false);
      return url.includes('/app/friends') || hasTitle;
    } catch {
      return false;
    }
  }

  async waitForPageLoad() {
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(1000);
  }

  async waitForLoadingComplete() {
    try {
      await this.page.waitForSelector(this.selectors.loadingSpinner, { state: 'hidden', timeout: 10000 });
    } catch {
      console.log('Loading spinner not found or disappeared quickly');
    }
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Status Cards Methods
   */
  async getFriendsCount() {
    const card = this.page.locator(this.selectors.statusCards.friends);
    const number = await card.locator(this.selectors.statusNumber).textContent();
    return parseInt(number.trim());
  }

  async getSentInvitesCount() {
    const card = this.page.locator(this.selectors.statusCards.sentInvites);
    const number = await card.locator(this.selectors.statusNumber).textContent();
    return parseInt(number.trim());
  }

  async getReceivedInvitesCount() {
    const card = this.page.locator(this.selectors.statusCards.receivedInvites);
    const number = await card.locator(this.selectors.statusNumber).textContent();
    return parseInt(number.trim());
  }

  async clickStatusCard(cardType) {
    await this.page.locator(this.selectors.statusCards[cardType]).click();
  }

  /**
   * Wait for status counts to update
   */
  async waitForCountUpdate(countGetter, expectedValue, timeout = 5000) {
    const startTime = Date.now();
    let currentValue;

    while (Date.now() - startTime < timeout) {
      currentValue = await countGetter();
      if (currentValue === expectedValue) {
        return true;
      }
      await this.page.waitForTimeout(200);
    }

    throw new Error(`Count did not update to ${expectedValue}. Current value: ${currentValue}`);
  }

  async waitForSentInvitesCount(expectedCount) {
    await this.waitForCountUpdate(() => this.getSentInvitesCount(), expectedCount);
  }

  async waitForReceivedInvitesCount(expectedCount) {
    await this.waitForCountUpdate(() => this.getReceivedInvitesCount(), expectedCount);
  }

  async waitForFriendsCount(expectedCount) {
    await this.waitForCountUpdate(() => this.getFriendsCount(), expectedCount);
  }

  /**
   * Tab Navigation Methods
   */
  async switchToTab(tabName) {
    await this.page.locator(this.selectors.tabs[tabName]).click();
    await this.page.waitForTimeout(500);
  }

  async isTabActive(tabName) {
    const tab = this.page.locator(this.selectors.tabs[tabName]);
    const classes = await tab.getAttribute('class');
    return classes && classes.includes('p-tabmenu-item-active');
  }

  async getTabBadgeValue(tabName) {
    const tab = this.page.locator(this.selectors.tabs[tabName]);
    const badge = tab.locator(this.selectors.tabBadge);
    if (await badge.isVisible()) {
      return await badge.textContent();
    }
    return null;
  }

  /**
   * Friends List Methods
   */
  async getFriendsListCount() {
    return await this.page.locator(this.selectors.friendCard).count();
  }

  async getFriendsList() {
    const friendCards = await this.page.locator(this.selectors.friendCard).all();
    const friends = [];

    for (const card of friendCards) {
      const name = await card.locator(this.selectors.friendName).textContent();
      const email = await card.locator(this.selectors.friendEmail).textContent();

      let status = null;
      try {
        const statusBadge = card.locator(this.selectors.friendStatusBadge);
        if (await statusBadge.isVisible()) {
          status = await statusBadge.textContent();
        }
      } catch {}

      let lastSeen = null;
      try {
        lastSeen = await card.locator(this.selectors.lastSeen).textContent();
      } catch {}

      friends.push({
        name: name.trim(),
        email: email.trim(),
        status: status?.trim(),
        lastSeen: lastSeen?.trim()
      });
    }

    return friends;
  }

  async getFriendByEmail(email) {
    const friends = await this.getFriendsList();
    return friends.find(f => f.email === email);
  }

  async removeFriend(friendEmail) {
    const friendCards = await this.page.locator(this.selectors.friendCard).all();

    for (const card of friendCards) {
      const cardEmail = await card.locator(this.selectors.friendEmail).textContent();
      if (cardEmail.trim() === friendEmail) {
        await card.locator(this.selectors.removeFriendButton).click();
        return;
      }
    }

    throw new Error(`Friend with email ${friendEmail} not found`);
  }

  async showFriendOnMap(friendEmail) {
    const friendCards = await this.page.locator(this.selectors.friendCard).all();

    for (const card of friendCards) {
      const cardEmail = await card.locator(this.selectors.friendEmail).textContent();
      if (cardEmail.trim() === friendEmail) {
        await card.locator(this.selectors.showOnMapButton).click();
        return;
      }
    }

    throw new Error(`Friend with email ${friendEmail} not found`);
  }

  async hasEmptyFriendsState() {
    return await this.page.locator(this.selectors.emptyState).isVisible();
  }

  /**
   * Invite Friend Dialog Methods
   */
  async openInviteDialog() {
    await this.page.locator(this.selectors.inviteFriendButton).first().click();
    await this.page.waitForSelector(this.selectors.inviteDialog, { state: 'visible' });
  }

  async isInviteDialogVisible() {
    return await this.page.locator(this.selectors.inviteDialog).isVisible();
  }

  async fillInviteEmail(email) {
    await this.page.locator(this.selectors.friendEmailInput).fill(email);
  }

  async sendInvitation(email) {
    await this.fillInviteEmail(email);
    await this.page.locator(this.selectors.sendInvitationButton).click();
  }

  async closeInviteDialog() {
    await this.page.locator(this.selectors.cancelDialogButton).click();
  }

  async getInviteEmailError() {
    const errorElement = this.page.locator(this.selectors.errorMessage);
    if (await errorElement.isVisible()) {
      return await errorElement.textContent();
    }
    return null;
  }

  /**
   * Invitations Tab Methods
   */
  async getReceivedInvitationsCount() {
    const invites = this.page.locator(this.selectors.receivedInvitesList).locator(this.selectors.inviteItem);
    return await invites.count();
  }

  async getSentInvitationsCount() {
    const invites = this.page.locator(this.selectors.sentInvitesList).locator(this.selectors.inviteItem);
    return await invites.count();
  }

  async getReceivedInvitationsList() {
    const inviteItems = await this.page.locator(this.selectors.receivedInvitesList)
      .locator(this.selectors.inviteItem).all();

    const invitations = [];
    for (const item of inviteItems) {
      const email = await item.locator(this.selectors.inviteEmail).textContent();
      const date = await item.locator(this.selectors.inviteDate).textContent();

      invitations.push({
        name: email.trim(),
        email: email.trim(),
        date: date.trim()
      });
    }

    return invitations;
  }

  async getSentInvitationsList() {
    const inviteItems = await this.page.locator(this.selectors.sentInvitesList)
      .locator(this.selectors.inviteItem).all();

    const invitations = [];
    for (const item of inviteItems) {
      const email = await item.locator(this.selectors.inviteEmail).textContent();
      const date = await item.locator(this.selectors.inviteDate).textContent();

      invitations.push({
        name: email.trim(),
        email: email.trim(),
        date: date.trim()
      });
    }

    return invitations;
  }

  async acceptInvitation(senderEmail) {
    const inviteItems = await this.page.locator(this.selectors.receivedInvitesList)
      .locator(this.selectors.inviteItem).all();

    for (const item of inviteItems) {
      const email = await item.locator(this.selectors.inviteEmail).textContent();
      if (email.trim() === senderEmail) {
        await item.locator(this.selectors.acceptButton).click();
        return;
      }
    }

    throw new Error(`Invitation from ${senderEmail} not found`);
  }

  async rejectInvitation(senderEmail) {
    const inviteItems = await this.page.locator(this.selectors.receivedInvitesList)
      .locator(this.selectors.inviteItem).all();

    for (const item of inviteItems) {
      const email = await item.locator(this.selectors.inviteEmail).textContent();
      if (email.trim() === senderEmail) {
        await item.locator(this.selectors.rejectButton).click();
        return;
      }
    }

    throw new Error(`Invitation from ${senderEmail} not found`);
  }

  async cancelSentInvitation(receiverEmail) {
    const inviteItems = await this.page.locator(this.selectors.sentInvitesList)
      .locator(this.selectors.inviteItem).all();

    for (const item of inviteItems) {
      const email = await item.locator(this.selectors.inviteEmail).textContent();
      if (email.trim() === receiverEmail) {
        await item.locator(this.selectors.cancelButton).click();
        return;
      }
    }

    throw new Error(`Sent invitation to ${receiverEmail} not found`);
  }

  async acceptAllInvitations() {
    await this.page.locator(this.selectors.acceptAllButton).click();
  }

  async rejectAllInvitations() {
    await this.page.locator(this.selectors.rejectAllButton).click();
  }

  async cancelAllSentInvitations() {
    await this.page.locator(this.selectors.cancelAllButton).click();
  }

  async hasReceivedInvitesSection() {
    return await this.page.locator(this.selectors.receivedInvitesSection).isVisible();
  }

  async hasSentInvitesSection() {
    return await this.page.locator(this.selectors.sentInvitesSection).isVisible();
  }

  async hasEmptyInvitationsState() {
    const hasEmptyState = await this.page.locator(this.selectors.emptyState).isVisible();
    const hasReceivedSection = await this.hasReceivedInvitesSection();
    const hasSentSection = await this.hasSentInvitesSection();

    return hasEmptyState && !hasReceivedSection && !hasSentSection;
  }

  /**
   * Confirmation Dialog Methods
   */
  async isConfirmDialogVisible() {
    return await this.page.locator(this.selectors.confirmDialog).isVisible();
  }

  async getConfirmDialogMessage() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { state: 'visible' });
    return await this.page.locator(this.selectors.confirmDialogMessage).textContent();
  }

  async confirmAction() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { state: 'visible' });
    await this.page.locator('.p-confirmdialog button:has-text("Remove")').click();
  }

  async cancelAction() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { state: 'visible' });
    await this.page.locator('.p-confirmdialog button:has-text("Cancel")').click();
  }

  /**
   * Toast Notification Methods
   */
  async waitForSuccessToast(expectedText = null) {
    await this.page.waitForSelector('.p-toast-message-success', { timeout: 5000 });

    if (expectedText) {
      const toastText = await this.page.locator('.p-toast-message-success .p-toast-detail').first().textContent();
      expect(toastText).toContain(expectedText);
    }
  }

  async waitForErrorToast(expectedText = null) {
    await this.page.waitForSelector('.p-toast-message-error', { timeout: 5000 });

    if (expectedText) {
      const toastText = await this.page.locator('.p-toast-message-error .p-toast-detail').first().textContent();
      expect(toastText).toContain(expectedText);
    }
  }

  async waitForToastToDisappear(timeout = 6000) {
    try {
      // Wait for all toasts to disappear
      await this.page.waitForSelector('.p-toast-message', { state: 'hidden', timeout });
    } catch {
      // Toast might have already disappeared
    }
    // Add small delay to ensure toast animations complete
    await this.page.waitForTimeout(300);
  }

  /**
   * Friends Map Methods
   */
  async isFriendsMapVisible() {
    return await this.page.locator(this.selectors.mapContainer).isVisible();
  }

  async hasMapEmptyState() {
    return await this.page.locator(this.selectors.emptyState).isVisible();
  }

  /**
   * Database Helper Methods
   */

  static async insertFriendship(dbManager, userId1, userId2) {
    // Insert bidirectional friendship
    const now = new Date();

    await dbManager.client.query(`
      INSERT INTO user_friends (user_id, friend_id, created_at)
      VALUES ($1, $2, $3), ($2, $1, $3)
    `, [userId1, userId2, now]);
  }

  static async insertInvitation(dbManager, senderId, receiverId, status = 'PENDING') {
    const now = new Date();

    const result = await dbManager.client.query(`
      INSERT INTO friend_invitations (sender_id, receiver_id, status, sent_at)
      VALUES ($1, $2, $3, $4)
      RETURNING id
    `, [senderId, receiverId, status, now]);

    return result.rows[0].id;
  }

  static async verifyFriendshipExists(dbManager, userId1, userId2) {
    const result = await dbManager.client.query(`
      SELECT COUNT(*) as count
      FROM user_friends
      WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1)
    `, [userId1, userId2]);

    return parseInt(result.rows[0].count) === 2; // Should have both directions
  }

  static async verifyFriendshipNotExists(dbManager, userId1, userId2) {
    const result = await dbManager.client.query(`
      SELECT COUNT(*) as count
      FROM user_friends
      WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1)
    `, [userId1, userId2]);

    return parseInt(result.rows[0].count) === 0;
  }

  static async getInvitationStatus(dbManager, invitationId) {
    const result = await dbManager.client.query(`
      SELECT status FROM friend_invitations WHERE id = $1
    `, [invitationId]);

    return result.rows[0]?.status || null;
  }

  static async getInvitationBySenderAndReceiver(dbManager, senderId, receiverId) {
    const result = await dbManager.client.query(`
      SELECT id, status FROM friend_invitations
      WHERE sender_id = $1 AND receiver_id = $2
      ORDER BY sent_at DESC
      LIMIT 1
    `, [senderId, receiverId]);

    return result.rows[0] || null;
  }

  static async countFriends(dbManager, userId) {
    const result = await dbManager.client.query(`
      SELECT COUNT(*) as count FROM user_friends WHERE user_id = $1
    `, [userId]);

    return parseInt(result.rows[0].count);
  }

  static async countReceivedInvitations(dbManager, userId, status = 'PENDING') {
    const result = await dbManager.client.query(`
      SELECT COUNT(*) as count FROM friend_invitations
      WHERE receiver_id = $1 AND status = $2
    `, [userId, status]);

    return parseInt(result.rows[0].count);
  }

  static async countSentInvitations(dbManager, userId, status = 'PENDING') {
    const result = await dbManager.client.query(`
      SELECT COUNT(*) as count FROM friend_invitations
      WHERE sender_id = $1 AND status = $2
    `, [userId, status]);

    return parseInt(result.rows[0].count);
  }

  static async insertFriendWithLocation(dbManager, userId, friendId, latitude, longitude) {
    // Insert friendship
    await this.insertFriendship(dbManager, userId, friendId);

    // Insert GPS point for friend using GpsDataFactory
    const now = new Date().toISOString();
    const gpsPoint = {
      id: Date.now(), // Use timestamp as unique ID
      device_id: 'test-device',
      user_id: friendId,
      coordinates: `POINT (${longitude} ${latitude})`,
      timestamp: now,
      accuracy: 10,
      battery: 100,
      velocity: 0,
      altitude: 0,
      source_type: 'TEST',
      created_at: now
    };

    await dbManager.client.query(`
      INSERT INTO gps_points (id, device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
    `, [
      gpsPoint.id,
      gpsPoint.device_id,
      gpsPoint.user_id,
      gpsPoint.coordinates,
      gpsPoint.timestamp,
      gpsPoint.accuracy,
      gpsPoint.battery,
      gpsPoint.velocity,
      gpsPoint.altitude,
      gpsPoint.source_type,
      gpsPoint.created_at
    ]);
  }
}
