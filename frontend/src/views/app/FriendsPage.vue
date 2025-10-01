<template>
  <AppLayout>
    <PageContainer padding="none">
      <div class="friends-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Friends & Connections</h1>
              <p class="page-description">
                Connect with friends to share location data and stay connected on the map
              </p>
            </div>
            <Button 
              label="Invite Friend" 
              icon="pi pi-user-plus"
              @click="showInviteDialog = true"
              class="invite-btn"
            />
          </div>
        </div>

        <!-- Status Overview -->
        <div class="status-overview">
          <div class="status-cards">
            <Card class="status-card clickable" @click="switchToTab('friends')">
              <template #content>
                <div class="status-item">
                  <div class="status-icon friends">
                    <i class="pi pi-users"></i>
                  </div>
                  <div class="status-info">
                    <div class="status-number">{{ friends?.length || 0 }}</div>
                    <div class="status-label">Friends</div>
                  </div>
                </div>
              </template>
            </Card>

            <Card class="status-card clickable" @click="switchToTab('invites')">
              <template #content>
                <div class="status-item">
                  <div class="status-icon invites-sent">
                    <i class="pi pi-send"></i>
                  </div>
                  <div class="status-info">
                    <div class="status-number">{{ sentInvites?.length || 0 }}</div>
                    <div class="status-label">Sent Invites</div>
                  </div>
                </div>
              </template>
            </Card>

            <Card class="status-card clickable" @click="switchToTab('invites')">
              <template #content>
                <div class="status-item">
                  <div class="status-icon invites-received">
                    <i class="pi pi-inbox"></i>
                    <Badge 
                      v-if="receivedInvites?.length > 0" 
                      :value="receivedInvites.length"
                      severity="danger"
                      class="status-badge"
                    />
                  </div>
                  <div class="status-info">
                    <div class="status-number">{{ receivedInvites?.length || 0 }}</div>
                    <div class="status-label">Received Invites</div>
                  </div>
                </div>
              </template>
            </Card>
          </div>
        </div>

        <!-- Main Content Tabs -->
        <TabContainer
          :tabs="tabItems"
          :activeIndex="activeTabIndex"
          @tab-change="handleTabChange"
          class="friends-tabs"
        >
          <!-- Friends List Tab -->
          <div v-if="activeTab === 'friends'">
              <div class="friends-content">
                <div v-if="!friends?.length" class="empty-state">
                  <div class="empty-icon">
                    <i class="pi pi-users"></i>
                  </div>
                  <h3 class="empty-title">No Friends Yet</h3>
                  <p class="empty-description">
                    Start building your network by inviting friends to connect and share locations
                  </p>
                  <Button 
                    label="Invite Your First Friend"
                    icon="pi pi-user-plus"
                    @click="showInviteDialog = true"
                  />
                </div>

                <div v-else class="friends-grid">
                  <Card v-for="friend in friends" :key="friend.id" class="friend-card">
                    <template #content>
                      <div class="friend-info">
                        <Avatar 
                          :image="friend.avatar || '/avatars/avatar1.png'"
                          size="large"
                          class="friend-avatar"
                        />
                        <div class="friend-details">
                          <div class="friend-name">{{ friend.fullName }}</div>
                          <div class="friend-email">{{ friend.email }}</div>
                          <div class="friend-status">
                            <Badge 
                              :value="getFriendStatus(friend)"
                              :severity="getFriendStatusSeverity(friend)"
                              class="status-badge"
                            />
                            <span class="last-seen">Last seen: {{ getLastSeenText(friend.lastSeen) }}</span>
                          </div>
                          <div v-if="friend.lastLocation" class="friend-location">
                            <i class="pi pi-map-marker location-icon"></i>
                            <span class="location-text">{{ friend.lastLocation }}</span>
                          </div>
                        </div>
                      </div>
                      
                      <div class="friend-actions">
                        <Button 
                          icon="pi pi-map-marker"
                          size="small"
                          outlined
                          @click="showFriendOnMap(friend)"
                          :disabled="!friend.lastLatitude || !friend.lastLongitude"
                        />
                        <Button 
                          icon="pi pi-trash"
                          size="small"
                          severity="danger"
                          outlined
                          @click="confirmDeleteFriend(friend)"
                        />
                      </div>
                    </template>
                  </Card>
                </div>
              </div>
          </div>

          <!-- Friends Map Tab -->
          <div v-if="activeTab === 'map'">
              <div v-if="!friends?.length" class="empty-state">
                <div class="empty-icon">
                  <i class="pi pi-map"></i>
                </div>
                <h3 class="empty-title">No Friends to Show</h3>
                <p class="empty-description">
                  Add friends to see their locations on the map
                </p>
                <Button 
                  label="Invite Friends"
                  icon="pi pi-user-plus"
                  @click="showInviteDialog = true"
                />
              </div>

              <div v-else-if="!friendsWithLocation.length" class="empty-state">
                <div class="empty-icon">
                  <i class="pi pi-map-marker"></i>
                </div>
                <h3 class="empty-title">No Location Data Available</h3>
                <p class="empty-description">
                  Your friends haven't shared their location data yet. Location sharing happens automatically when they use location tracking apps.
                </p>
                <div class="empty-actions">
                  <Button 
                    label="Refresh"
                    icon="pi pi-refresh"
                    outlined
                    @click="refreshFriendsData"
                    :loading="refreshing"
                  />
                  <Button 
                    label="Invite More Friends"
                    icon="pi pi-user-plus"
                    @click="showInviteDialog = true"
                  />
                </div>
              </div>

              <Card v-else class="map-card">
                <template #content>
                  <div class="map-container">
                    <FriendsMap
                      ref="friendsMapRef"
                      :friends="friendsWithLocation"
                      :key="`friends-map-${activeTab}-${friendsWithLocation.length}`"
                      @friend-located="handleFriendLocated"
                      class="friends-map"
                    />
                  </div>
                </template>
              </Card>
          </div>

          <!-- Invitations Tab -->
          <div v-if="activeTab === 'invites'">
              <div class="invites-content">
                <!-- Received Invites -->
                <Card v-if="receivedInvites?.length > 0" class="invites-section">
                  <template #title>
                    <div class="section-header">
                      <div class="section-title">
                        <i class="pi pi-inbox mr-2"></i>
                        Received Invitations
                      </div>
                      <div class="section-actions">
                        <Button 
                          label="Accept All"
                          size="small"
                          @click="handleAcceptAllInvites"
                          :loading="bulkActionsLoading.acceptAll"
                        />
                        <Button 
                          label="Reject All"
                          size="small"
                          severity="danger"
                          outlined
                          @click="handleRejectAllInvites"
                          :loading="bulkActionsLoading.rejectAll"
                        />
                      </div>
                    </div>
                  </template>
                  <template #content>
                    <div class="invites-list">
                      <div v-for="invite in receivedInvites" :key="invite.id" class="invite-item">
                        <div class="invite-info">
                          <Avatar 
                            :image="invite.senderAvatar || '/avatars/avatar1.png'"
                            size="large"
                            class="invite-avatar"
                          />
                          <div class="invite-details">
                            <div class="invite-email">{{ invite.senderName }}</div>
                            <div class="invite-date">{{ formatDate(invite.createdAt) }}</div>
                          </div>
                        </div>
                        
                        <div class="invite-actions">
                          <Button 
                            label="Accept"
                            icon="pi pi-check"
                            size="small"
                            @click="handleAcceptInvite(invite.id)"
                            :loading="inviteActionsLoading[invite.id]?.accept"
                          />
                          <Button 
                            label="Reject"
                            icon="pi pi-times"
                            size="small"
                            severity="danger"
                            outlined
                            @click="handleRejectInvite(invite.id)"
                            :loading="inviteActionsLoading[invite.id]?.reject"
                          />
                        </div>
                      </div>
                    </div>
                  </template>
                </Card>

                <!-- Sent Invites -->
                <Card v-if="sentInvites?.length > 0" class="invites-section">
                  <template #title>
                    <div class="section-header">
                      <div class="section-title">
                        <i class="pi pi-send mr-2"></i>
                        Sent Invitations
                      </div>
                      <div class="section-actions">
                        <Button 
                          label="Cancel All"
                          size="small"
                          severity="danger"
                          outlined
                          @click="handleCancelAllInvites"
                          :loading="bulkActionsLoading.cancelAll"
                        />
                      </div>
                    </div>
                  </template>
                  <template #content>
                    <div class="invites-list">
                      <div v-for="invite in sentInvites" :key="invite.id" class="invite-item">
                        <div class="invite-info">
                          <Avatar 
                            :image="invite.receiverAvatar || '/avatars/avatar1.png'"
                            size="large"
                            class="invite-avatar"
                          />
                          <div class="invite-details">
                            <div class="invite-email">{{ invite.receiverName }}</div>
                            <div class="invite-date">{{ formatDate(invite.createdAt) }}</div>
                          </div>
                        </div>
                        
                        <div class="invite-actions">
                          <Badge value="Pending" severity="warning" />
                          <Button 
                            label="Cancel"
                            icon="pi pi-times"
                            size="small"
                            severity="danger"
                            outlined
                            @click="handleCancelInvite(invite.id)"
                            :loading="inviteActionsLoading[invite.id]?.cancel"
                          />
                        </div>
                      </div>
                    </div>
                  </template>
                </Card>

                <!-- Empty State for Invites -->
                <div v-if="!receivedInvites?.length && !sentInvites?.length" class="empty-state">
                  <div class="empty-icon">
                    <i class="pi pi-envelope"></i>
                  </div>
                  <h3 class="empty-title">No Pending Invitations</h3>
                  <p class="empty-description">
                    All your invitations have been processed
                  </p>
                </div>
              </div>
          </div>
        </TabContainer>

        <!-- Invite Friend Dialog -->
        <Dialog 
          v-model:visible="showInviteDialog"
          :header="'Invite Friend'"
          modal
          class="invite-dialog"
        >
          <div class="invite-form">
            <div class="form-field">
              <label for="friendEmail" class="form-label">Friend's Email Address</label>
              <InputText 
                id="friendEmail"
                v-model="inviteForm.email"
                placeholder="Enter email address"
                :invalid="!!inviteErrors.email"
                class="w-full"
                @keyup.enter="sendInvite"
              />
              <small v-if="inviteErrors.email" class="error-message">
                {{ inviteErrors.email }}
              </small>
            </div>
          </div>

          <template #footer>
            <div class="dialog-footer">
              <Button label="Cancel" outlined @click="closeInviteDialog" />
              <Button 
                label="Send Invitation"
                @click="sendInvite"
                :loading="inviteLoading"
              />
            </div>
          </template>
        </Dialog>

        <!-- Confirm Delete Dialog -->
        <ConfirmDialog />
        <Toast />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Map component
import FriendsMap from '@/components/maps/FriendsMap.vue'

// Store
import { useFriendsStore } from '@/stores/friends'

// Composables
const toast = useToast()
const confirm = useConfirm()
const friendsStore = useFriendsStore()

// Store refs
const { friends, receivedInvites, sentInvitations: sentInvites } = storeToRefs(friendsStore)

// State
const activeTab = ref('friends')
const showInviteDialog = ref(false)

// Tab configuration
const tabItems = computed(() => [
  {
    label: 'My Friends',
    icon: 'pi pi-users',
    key: 'friends',
    badge: friends.value?.length > 0 ? friends.value.length : null,
    badgeType: 'info'
  },
  {
    label: 'Friends Map',
    icon: 'pi pi-map',
    key: 'map'
  },
  {
    label: 'Invitations',
    icon: 'pi pi-envelope',
    key: 'invites',
    badge: totalPendingInvites.value > 0 ? totalPendingInvites.value : null,
    badgeType: 'danger'
  }
])

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})
const inviteLoading = ref(false)
const refreshing = ref(false)
const friendsMapRef = ref(null)

// Form data
const inviteForm = ref({
  email: '',
  message: ''
})

const inviteErrors = ref({})

// Loading states
const inviteActionsLoading = reactive({})
const bulkActionsLoading = reactive({
  acceptAll: false,
  rejectAll: false,
  cancelAll: false
})

// Computed
const totalPendingInvites = computed(() => 
  (receivedInvites.value?.length || 0) + (sentInvites.value?.length || 0)
)

const friendsWithLocation = computed(() => {
  return friends.value?.filter(friend =>
    friend.lastLatitude && 
    friend.lastLongitude &&
    typeof friend.lastLatitude === 'number' &&
    typeof friend.lastLongitude === 'number' &&
    !isNaN(friend.lastLatitude) &&
    !isNaN(friend.lastLongitude)
  ) || []
})

// Methods
const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
  }
}

const switchToTab = (tabKey) => {
  activeTab.value = tabKey
}

const validateInviteForm = () => {
  inviteErrors.value = {}
  
  if (!inviteForm.value.email?.trim()) {
    inviteErrors.value.email = 'Email address is required'
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(inviteForm.value.email)) {
    inviteErrors.value.email = 'Please enter a valid email address'
  }
  
  return Object.keys(inviteErrors.value).length === 0
}

const sendInvite = async () => {
  if (!validateInviteForm()) return
  
  inviteLoading.value = true
  
  try {
    await friendsStore.sendFriendRequest(inviteForm.value.email.trim())
    
    toast.add({
      severity: 'success',
      summary: 'Invitation Sent',
      detail: `Friend request sent to ${inviteForm.value.email}`,
      life: 3000
    })
    
    closeInviteDialog()
  } catch (error) {
    const errorMessage = error.response?.data?.message || error.message || 'Failed to send invitation'
    toast.add({
      severity: 'error',
      summary: 'Invitation Failed',
      detail: errorMessage,
      life: 5000
    })
  } finally {
    inviteLoading.value = false
  }
}

const closeInviteDialog = () => {
  showInviteDialog.value = false
  inviteForm.value = { email: '', message: '' }
  inviteErrors.value = {}
}

const confirmDeleteFriend = (friend) => {
  confirm.require({
    message: `Are you sure you want to remove ${friend.fullName} from your friends?`,
    header: 'Remove Friend',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: 'Cancel',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: 'Remove',
      severity: 'danger'
    },
    accept: () => deleteFriend(friend.friendId)
  })
}

const deleteFriend = async (friendId) => {
  try {
    await friendsStore.deleteFriendship(friendId)
    toast.add({
      severity: 'success',
      summary: 'Friend Removed',
      detail: 'The friend has been removed from your list',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Remove Failed',
      detail: 'Failed to remove friend',
      life: 5000
    })
  }
}

const showFriendOnMap = (friend) => {
  activeTab.value = 'map'
  // Give more time for map to initialize after tab switch
  setTimeout(() => {
    if (friendsMapRef.value) {
      friendsMapRef.value.zoomToFriend(friend)
    } else {
      // Retry after additional delay if map ref not ready
      setTimeout(() => {
        if (friendsMapRef.value) {
          friendsMapRef.value.zoomToFriend(friend)
        }
      }, 500)
    }
  }, 300)
}

const handleAcceptInvite = async (inviteId) => {
  inviteActionsLoading[inviteId] = { accept: true }
  
  try {
    await friendsStore.acceptInvitation(inviteId)
    toast.add({
      severity: 'success',
      summary: 'Invitation Accepted',
      detail: 'You are now friends!',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Accept Failed',
      detail: 'Failed to accept invitation',
      life: 5000
    })
  } finally {
    delete inviteActionsLoading[inviteId]
  }
}

const handleRejectInvite = async (inviteId) => {
  inviteActionsLoading[inviteId] = { reject: true }
  
  try {
    await friendsStore.rejectInvitation(inviteId)
    toast.add({
      severity: 'success',
      summary: 'Invitation Rejected',
      detail: 'The invitation has been rejected',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Reject Failed',
      detail: 'Failed to reject invitation',
      life: 5000
    })
  } finally {
    delete inviteActionsLoading[inviteId]
  }
}

const handleCancelInvite = async (inviteId) => {
  inviteActionsLoading[inviteId] = { cancel: true }
  
  try {
    await friendsStore.cancelInvitation(inviteId)
    toast.add({
      severity: 'success',
      summary: 'Invitation Cancelled',
      detail: 'The invitation has been cancelled',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Cancel Failed',
      detail: 'Failed to cancel invitation',
      life: 5000
    })
  } finally {
    delete inviteActionsLoading[inviteId]
  }
}

const handleAcceptAllInvites = async () => {
  bulkActionsLoading.acceptAll = true
  
  try {
    const inviteIds = receivedInvites.value.map(invite => invite.id)
    await friendsStore.acceptMultipleInvitations(inviteIds)
    toast.add({
      severity: 'success',
      summary: 'All Invitations Accepted',
      detail: `Accepted ${inviteIds.length} invitation(s)`,
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Bulk Accept Failed',
      detail: 'Failed to accept all invitations',
      life: 5000
    })
  } finally {
    bulkActionsLoading.acceptAll = false
  }
}

const handleRejectAllInvites = async () => {
  bulkActionsLoading.rejectAll = true
  
  try {
    const inviteIds = receivedInvites.value.map(invite => invite.id)
    await friendsStore.rejectMultipleInvitations(inviteIds)
    toast.add({
      severity: 'success',
      summary: 'All Invitations Rejected',
      detail: `Rejected ${inviteIds.length} invitation(s)`,
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Bulk Reject Failed',
      detail: 'Failed to reject all invitations',
      life: 5000
    })
  } finally {
    bulkActionsLoading.rejectAll = false
  }
}

const handleCancelAllInvites = async () => {
  bulkActionsLoading.cancelAll = true

  try {
    const inviteIds = sentInvites.value.map(invite => invite.id)
    // Cancel each invitation individually
    await Promise.all(inviteIds.map(id => friendsStore.cancelInvitation(id)))
    toast.add({
      severity: 'success',
      summary: 'All Invitations Cancelled',
      detail: `Cancelled ${inviteIds.length} invitation(s)`,
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Bulk Cancel Failed',
      detail: 'Failed to cancel all invitations',
      life: 5000
    })
  } finally {
    bulkActionsLoading.cancelAll = false
  }
}

const handleFriendLocated = (friend) => {
}

// Utility functions
const getFriendStatus = (friend) => {
  if (!friend.lastSeen) return 'No Location'
  
  const lastSeen = timezone.fromUtc(friend.lastSeen)
  const now = timezone.now()
  const diffMinutes = now.diff(lastSeen, 'minute')
  
  if (diffMinutes < 5) return 'Online'
  if (diffMinutes < 60) return 'Recent'
  return 'Offline'
}

const getFriendStatusSeverity = (friend) => {
  const status = getFriendStatus(friend)
  switch (status) {
    case 'Online': return 'success'
    case 'Recent': return 'warning'
    default: return 'secondary'
  }
}

const getLastSeenText = (lastSeen) => {
  if (!lastSeen) return 'Never'
  
  const date = timezone.fromUtc(lastSeen)
  const now = timezone.now()
  const diffMinutes = now.diff(date, 'minute')
  
  if (diffMinutes < 1) return 'Just now'
  if (diffMinutes < 60) return `${Math.floor(diffMinutes)}m ago`
  if (diffMinutes < 1440) return `${Math.floor(diffMinutes / 60)}h ago`
  return `${Math.floor(diffMinutes / 1440)}d ago`
}

const refreshFriendsData = async () => {
  refreshing.value = true
  
  try {
    await friendsStore.refreshAllFriendsData()
    toast.add({
      severity: 'success',
      summary: 'Data Refreshed',
      detail: 'Friends data has been updated',
      life: 3000
    })
  } catch (error) {
    console.error('Error refreshing friends data:', error)
    toast.add({
      severity: 'error',
      summary: 'Refresh Failed',
      detail: 'Failed to refresh friends data',
      life: 5000
    })
  } finally {
    refreshing.value = false
  }
}

const formatDate = (dateString) => {
  return timezone.format(dateString, 'MMM D, YYYY')
}

// Lifecycle
onMounted(async () => {
  try {
    await friendsStore.refreshAllFriendsData()
  } catch (error) {
    console.error('Error loading friends data:', error)
    toast.add({
      severity: 'error',
      summary: 'Loading Failed',
      detail: 'Failed to load friends data',
      life: 5000
    })
  }
})
</script>

<style scoped>
.friends-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1rem;
  box-sizing: border-box;
  width: 100%;
  min-height: auto;
}

.friends-page * {
  box-sizing: border-box;
}

/* Page Header */
.page-header {
  margin-bottom: 2rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 2rem;
}

.header-text {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.page-description {
  font-size: 1.1rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.5;
}

/* Status Overview */
.status-overview {
  margin-bottom: 2rem;
}

.status-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
  width: 100%;
  box-sizing: border-box;
}

.status-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  transition: all 0.2s ease;
}

.status-card.clickable {
  cursor: pointer;
}

.status-card:hover {
  box-shadow: var(--gp-shadow-medium);
  border-color: var(--gp-primary-light);
}

.status-card.clickable:hover {
  background: var(--gp-primary-light);
  border-color: var(--gp-primary);
}

.status-item {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.status-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  font-size: 1.25rem;
  color: white;
  position: relative;
  flex-shrink: 0;
}

.status-icon.friends {
  background: var(--gp-primary);
}

.status-icon.invites-sent {
  background: var(--gp-warning);
}

.status-icon.invites-received {
  background: var(--gp-success);
}

.status-badge {
  position: absolute !important;
  top: -0.25rem;
  right: -0.25rem;
}

.status-info {
  flex: 1;
}

.status-number {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
}

.status-label {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
}

/* Tabs */
.friends-tabs {
  margin-bottom: 2rem;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 3rem 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-large);
  margin: 2rem 0;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 4rem;
  height: 4rem;
  background: var(--gp-primary-light);
  color: var(--gp-primary);
  border-radius: 50%;
  font-size: 2rem;
  margin-bottom: 1rem;
}

.empty-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.empty-description {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  margin: 0 0 1.5rem 0;
  max-width: 400px;
  line-height: 1.5;
}

.empty-actions {
  display: flex;
  gap: 1rem;
  justify-content: center;
  flex-wrap: wrap;
}

/* Friends Grid */
.friends-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
  box-sizing: border-box;
  width: 100%;
}

.friend-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  transition: all 0.2s ease;
}

.friend-card:hover {
  box-shadow: var(--gp-shadow-medium);
  border-color: var(--gp-primary-light);
}

.friend-info {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}

.friend-avatar {
  flex-shrink: 0;
}

.friend-details {
  flex: 1;
  min-width: 0;
}

.friend-name {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
}

.friend-email {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin-bottom: 0.5rem;
  word-break: break-word;
}

.friend-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.last-seen {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.friend-location {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.5rem;
}

.location-icon {
  font-size: 0.75rem;
  color: var(--gp-primary);
}

.location-text {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  line-height: 1.2;
}

.friend-actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

/* Map */
.map-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  margin: 0;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.map-card :deep(.p-card-content) {
  padding: 0 !important;
}

.map-container {
  height: 500px;
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  position: relative;
}

.friends-map {
  width: 100%;
  height: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

/* Invites */
.invites-content {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.invites-section {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.section-title {
  display: flex;
  align-items: center;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.section-actions {
  display: flex;
  gap: 0.5rem;
}

.invites-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.invite-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
}

.invite-info {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex: 1;
  min-width: 0;
}

.invite-avatar {
  flex-shrink: 0;
}

.invite-details {
  flex: 1;
  min-width: 0;
}

.invite-email {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin-bottom: 0.25rem;
  word-break: break-word;
}

.invite-date {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.invite-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

/* Dialog */
.invite-dialog {
  min-width: 400px;
}

.invite-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-label {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-size: 0.9rem;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

/* Tab Styling */
:deep(.p-tabs-nav) {
  background: var(--gp-surface-white);
  border-bottom: 1px solid var(--gp-border-light);
}

:deep(.p-tabs-tab) {
  border: none;
  background: transparent;
  color: var(--gp-text-secondary);
  font-weight: 500;
  padding: 1rem 1.5rem;
  transition: all 0.2s ease;
}

:deep(.p-tabs-tab:hover) {
  background: var(--gp-surface-light);
  color: var(--gp-text-primary);
}

:deep(.p-tabs-tab.p-highlight) {
  background: transparent;
  color: var(--gp-primary);
  border-bottom: 2px solid var(--gp-primary);
  font-weight: 600;
}

:deep(.p-tabs-panels) {
  background: transparent;
  padding: 0;
}

:deep(.p-tabs-panel) {
  padding: 2rem 0 0 0;
}

/* Input and Button Styling */
:deep(.p-inputtext),
:deep(.p-textarea) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.75rem 1rem;
  transition: all 0.2s ease;
}

:deep(.p-inputtext:focus),
:deep(.p-textarea:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  transition: all 0.2s ease;
}

:deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
}

:deep(.p-button:not(.p-button-outlined):hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  box-shadow: var(--gp-shadow-medium);
}

/* Responsive Design */
@media (max-width: 768px) {
  .friends-page {
    padding: 0 0.5rem;
  }
  
  .page-title {
    font-size: 1.5rem;
  }
  
  .header-content {
    flex-direction: column;
    align-items: stretch;
  }
  
  .status-cards {
    grid-template-columns: repeat(3, 1fr);
    gap: 0.5rem;
  }
  
  .status-icon {
    width: 2rem;
    height: 2rem;
    font-size: 1rem;
  }
  
  .status-number {
    font-size: 1.25rem;
  }
  
  .status-label {
    font-size: 0.75rem;
  }
  
  .friends-grid {
    grid-template-columns: 1fr;
  }
  
  .section-header {
    flex-direction: column;
    align-items: stretch;
  }
  
  .invite-item {
    flex-direction: column;
    gap: 1rem;
    align-items: stretch;
  }
  
  .invite-actions {
    justify-content: center;
  }
  
  .map-container {
    height: 400px;
    margin: 0;
    border-radius: var(--gp-radius-small);
  }
  
  .map-card {
    margin: 0;
    padding: 0;
  }
  
  .map-card :deep(.p-card-content) {
    padding: 0;
  }
  
  :deep(.p-tabs-tab) {
    padding: 0.75rem 1rem;
    font-size: 0.9rem;
  }
}
</style>