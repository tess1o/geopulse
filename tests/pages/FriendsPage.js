import { expect } from '@playwright/test';

export class FriendsPage {
  constructor(page) {
    this.page = page;
  }

  // Selectors
  get selectors() {
    return {
      // Tabs - Using PrimeVue TabMenu component
      tabs: {
        live: '.p-tabmenu-item:has(.p-tabmenu-item-label:has-text("Live"))',
        timeline: '.p-tabmenu-item:has(.p-tabmenu-item-label:has-text("Timeline"))',
        friends: '.p-tabmenu-item:has(.p-tabmenu-item-label:has-text("Friends"))',
        invites: '.p-tabmenu-item:has(.p-tabmenu-item-label:has-text("Invitations"))'
      },
      activeTab: '.p-tabmenu-item.p-tabmenu-item-active',
      tabBadge: '.p-badge',

      // Friends List Tab (All friends with permission toggles)
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

      // Friend Sharing Status (What friend shares with you - read-only)
      friendSharingStatus: '.friend-sharing-status',
      sharingStatusItems: '.sharing-status-items',
      statusItem: '.status-item',

      // Friend Permissions (What you share with friend - editable)
      friendPermissions: '.friend-permissions',
      permissionItem: '.permission-item',
      permissionSwitch: '.permission-switch',
      liveLocationSwitch: '.permission-item:has-text("Live Location") .permission-switch',
      timelineSwitch: '.permission-item:has-text("Timeline History") .permission-switch',

      // Friend Actions
      liveButton: 'button:has-text("Live")',
      timelineButton: 'button:has-text("Timeline")',
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

      // Invite Friend Dialog - uses AutoComplete now
      inviteDialog: '.p-dialog',
      inviteDialogHeader: '.p-dialog-header:has-text("Invite Friend")',
      friendEmailInput: '#friendEmail input[type="text"]',
      friendEmailAutocomplete: '#friendEmail',
      sendInvitationButton: 'button:has-text("Send Invitation")',
      cancelDialogButton: 'button:has-text("Cancel")',
      inviteFriendButton: 'button:has-text("Invite Friend")',
      errorMessage: '.p-error',

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
      confirmDialogAccept: '.p-confirmdialog-accept-button',
      confirmDialogReject: '.p-confirmdialog-reject-button',

      // Toast Notifications
      toast: '.p-toast',
      toastMessage: '.p-toast-message',
      toastSummary: '.p-toast-summary',
      toastDetail: '.p-toast-detail',

      // Friends Timeline Tab
      friendsTimelineTab: '.friends-timeline-tab',
      dateRangePicker: '.date-range-picker',
      timelineDatePicker: '.p-datepicker',
      timelineDatePickerInput: '.p-datepicker-input',
      userSelectionPanel: '.user-selection-panel',
      userItem: '.user-item',
      userCheckbox: '.p-checkbox',
      selectAllButton: 'button:has-text("All")',
      deselectAllButton: 'button:has-text("None")',
      mergedTimelineList: '.merged-timeline-list',
      timelineItem: '.friend-timeline-card',
      timelineStayItem: '.friend-timeline-card--stay',
      timelineTripItem: '.friend-timeline-card--trip',
      timelineMap: '.friends-timeline-map',
      emptyTimelineMessage: '.empty-state-card'
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
      return url.includes('/app/friends');
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

  async isInvitesTabVisible() {
    try {
      return await this.page.locator(this.selectors.tabs.invites).isVisible({ timeout: 1000 });
    } catch {
      return false;
    }
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

  async showFriendOnLiveMap(friendEmail) {
    const friendCards = await this.page.locator(this.selectors.friendCard).all();

    for (const card of friendCards) {
      const cardEmail = await card.locator(this.selectors.friendEmail).textContent();
      if (cardEmail.trim() === friendEmail) {
        await card.locator(this.selectors.liveButton).click();
        return;
      }
    }

    throw new Error(`Friend with email ${friendEmail} not found`);
  }

  async showFriendTimeline(friendEmail) {
    const friendCards = await this.page.locator(this.selectors.friendCard).all();

    for (const card of friendCards) {
      const cardEmail = await card.locator(this.selectors.friendEmail).textContent();
      if (cardEmail.trim() === friendEmail) {
        await card.locator(this.selectors.timelineButton).click();
        return;
      }
    }

    throw new Error(`Friend with email ${friendEmail} not found`);
  }

  async hasEmptyFriendsState() {
    return await this.page.locator(this.selectors.emptyState).isVisible();
  }

  /**
   * Friend Permission Methods
   */
  async toggleLiveLocationPermission(friendEmail) {
    const friendCards = await this.page.locator(this.selectors.friendCard).all();

    for (const card of friendCards) {
      const cardEmail = await card.locator(this.selectors.friendEmail).textContent();
      if (cardEmail.trim() === friendEmail) {
        await card.locator(this.selectors.liveLocationSwitch).locator('input').click();
        return;
      }
    }

    throw new Error(`Friend with email ${friendEmail} not found`);
  }

  async toggleTimelinePermission(friendEmail) {
    const friendCards = await this.page.locator(this.selectors.friendCard).all();

    for (const card of friendCards) {
      const cardEmail = await card.locator(this.selectors.friendEmail).textContent();
      if (cardEmail.trim() === friendEmail) {
        await card.locator(this.selectors.timelineSwitch).locator('input').click();
        return;
      }
    }

    throw new Error(`Friend with email ${friendEmail} not found`);
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
      // Backend currently returns email in the name field
      const displayedName = await item.locator(this.selectors.inviteEmail).textContent();
      const date = await item.locator(this.selectors.inviteDate).textContent();

      invitations.push({
        name: displayedName.trim(),
        email: displayedName.trim(), // Same as name since backend returns email
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
      // Backend currently returns email in the name field
      const displayedName = await item.locator(this.selectors.inviteEmail).textContent();
      const date = await item.locator(this.selectors.inviteDate).textContent();

      invitations.push({
        name: displayedName.trim(),
        email: displayedName.trim(), // Same as name since backend returns email
        date: date.trim()
      });
    }

    return invitations;
  }

  async acceptInvitation(senderNameOrEmail) {
    const inviteItems = await this.page.locator(this.selectors.receivedInvitesList)
      .locator(this.selectors.inviteItem).all();

    for (const item of inviteItems) {
      // Note: Despite the CSS class name, this displays what the backend sends as senderName
      // Currently the backend returns email address here
      const displayedText = await item.locator(this.selectors.inviteEmail).textContent();
      if (displayedText.trim() === senderNameOrEmail) {
        await item.locator(this.selectors.acceptButton).click();
        return;
      }
    }

    throw new Error(`Invitation from ${senderNameOrEmail} not found`);
  }

  async rejectInvitation(senderNameOrEmail) {
    const inviteItems = await this.page.locator(this.selectors.receivedInvitesList)
      .locator(this.selectors.inviteItem).all();

    for (const item of inviteItems) {
      // Note: Despite the CSS class name, this displays what the backend sends as senderName
      // Currently the backend returns email address here
      const displayedText = await item.locator(this.selectors.inviteEmail).textContent();
      if (displayedText.trim() === senderNameOrEmail) {
        await item.locator(this.selectors.rejectButton).click();
        return;
      }
    }

    throw new Error(`Invitation from ${senderNameOrEmail} not found`);
  }

  async cancelSentInvitation(receiverNameOrEmail) {
    const inviteItems = await this.page.locator(this.selectors.sentInvitesList)
      .locator(this.selectors.inviteItem).all();

    for (const item of inviteItems) {
      // Note: Despite the CSS class name, this displays what the backend sends as receiverName
      // Currently the backend returns email address here
      const displayedText = await item.locator(this.selectors.inviteEmail).textContent();
      if (displayedText.trim() === receiverNameOrEmail) {
        await item.locator(this.selectors.cancelButton).click();
        return;
      }
    }

    throw new Error(`Sent invitation to ${receiverNameOrEmail} not found`);
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

  async confirmPermissionChange() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { state: 'visible' });
    // Permission dialog uses different button text
    await this.page.locator(this.selectors.confirmDialogAccept).click();
  }

  async cancelAction() {
    await this.page.waitForSelector(this.selectors.confirmDialog, { state: 'visible' });
    await this.page.locator(this.selectors.confirmDialogReject).click();
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
   * Timeline Tab Methods
   */
  async isTimelineMapVisible() {
    return await this.page.locator('.friends-timeline-map .leaflet-container').isVisible();
  }

  async getTimelineItemsCount() {
    return await this.page.locator('.friend-timeline-card').count();
  }

  async selectAllTimelineFriends() {
    const selectAllButton = this.page.locator('button:has-text("All")');
    if (await selectAllButton.isVisible()) {
      await selectAllButton.click();
    }
  }

  async deselectAllTimelineFriends() {
    const deselectAllButton = this.page.locator('button:has-text("None")');
    if (await deselectAllButton.isVisible()) {
      await deselectAllButton.click();
    }
  }

  async openDatePicker() {
    const dateInput = this.page.locator('.p-datepicker-input').first();
    if (await dateInput.isVisible()) {
      await dateInput.click();
      await this.page.waitForTimeout(500);
    }
  }

  async isDatePickerVisible() {
    return await this.page.locator('.p-datepicker').isVisible();
  }

  async getUserSelectionPanelCount() {
    return await this.page.locator('.user-item').count();
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
