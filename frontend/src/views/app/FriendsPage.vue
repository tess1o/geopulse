x
<template>
  <AppLayout :showInviteFriendButton="true" @invite-friend="showInviteDialog = true">
    <PageContainer padding="none" maxWidth="xlarge">
      <div class="friends-page">
        <!-- Page Header Removed -->
        <!-- Status Overview Removed -->

        <!-- Main Content Tabs -->
        <TabContainer
            :tabs="tabItems"
            :activeIndex="activeTabIndex"
            @tab-change="handleTabChange"
            class="friends-tabs"
        >
          <keep-alive>
            <component
              :is="currentTabComponent"
              :key="activeTab"
              :ref="activeTab === 'map' ? (el) => friendsMapTabRef = el : undefined"
            />
          </keep-alive>
        </TabContainer>

        <!-- Invite Friend Dialog -->
        <Dialog
            v-model:visible="showInviteDialog"
            header="Invite Friend"
            modal
            :style="{ width: '350px', maxWidth: '90vw' }"
        >
          <div class="p-fluid">
            <label for="friendEmail" class="block mb-3 font-semibold text-lg">Friend's Email Address or Name</label>
            <AutoComplete
                fluid
                id="friendEmail"
                v-model="inviteForm.email"
                :suggestions="filteredUsers"
                @complete="searchUsers"
                field="email"
                optionLabel="email"
                placeholder="Enter email address or search users"
                :invalid="!!inviteErrors.email"
                class="w-full mb-2"
                inputClass="p-inputtext-lg"
                @keyup.enter="sendInvite"
            >
              <template #option="slotProps">
                <div class="flex align-items-center">
                  <Avatar
                      :image="slotProps.option.avatar || '/avatars/avatar1.png'"
                      size="small"
                      shape="circle"
                      class="mr-2"
                  />
                  <div>
                    <div class="font-bold">{{ slotProps.option.fullName }}</div>
                    <div class="text-sm text-color-secondary">{{ slotProps.option.email }}</div>
                  </div>
                </div>
              </template>
            </AutoComplete>
            <small v-if="inviteErrors.email" class="p-error block mb-3">
              {{ inviteErrors.email }}
            </small>

            <div class="flex justify-content-end gap-2 mt-5">
              <Button label="Cancel" severity="secondary" outlined size="large" @click="closeInviteDialog"/>
              <Button label="Send Invitation" size="large" @click="sendInvite" :loading="inviteLoading"/>
            </div>
          </div>
        </Dialog>

        <!-- Confirm Delete Dialog -->
        <ConfirmDialog/>
        <Toast/>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import {ref, computed, onMounted, reactive, watch, h} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {storeToRefs} from 'pinia'
import {useToast} from 'primevue/usetoast'
import {useConfirm} from 'primevue/useconfirm'
import {useTimezone} from '@/composables/useTimezone'
import {useLocationStore} from '@/stores/location'
import {useAuthStore} from '@/stores/auth'
import AutoComplete from 'primevue/autocomplete'

const timezone = useTimezone()

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Tab components
import FriendsListTab from '@/components/friends/FriendsListTab.vue'
import FriendsMapTab from '@/components/friends/FriendsMapTab.vue'
import InvitationsTab from '@/components/friends/InvitationsTab.vue'

// Store
import {useFriendsStore} from '@/stores/friends'

// Composables
const toast = useToast()
const confirm = useConfirm()
const friendsStore = useFriendsStore()
const locationStore = useLocationStore()
const authStore = useAuthStore()

const currentUser = ref(null)

// Store refs
const route = useRoute()
const router = useRouter()

// Store refs
const {friends, receivedInvites, sentInvitations: sentInvites} = storeToRefs(friendsStore)

// State
const activeTab = ref()
const showInviteDialog = ref(false)

// Tab configuration
const tabItems = computed(() => [
  {
    label: 'Friends Map',
    icon: 'pi pi-map',
    key: 'map'
  },
  {
    label: 'My Friends',
    icon: 'pi pi-users',
    key: 'friends',
    badge: friends.value?.length > 0 ? friends.value.length : null,
    badgeType: 'info'
  },
  {
    label: 'Invitations',
    icon: 'pi pi-envelope',
    key: 'invites',
    badge: receivedInvites.value?.length > 0 ? receivedInvites.value.length : null,
    badgeType: 'danger'
  }
])

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})

const currentTabComponent = computed(() => {
  const components = {
    friends: {
      component: FriendsListTab,
      props: {
        friends: friends.value
      },
      handlers: {
        onInviteFriend: () => { showInviteDialog.value = true },
        onShowOnMap: showFriendOnMap,
        onDeleteFriend: confirmDeleteFriend
      }
    },
    map: {
      component: FriendsMapTab,
      props: {
        friends: friends.value,
        currentUser: currentUser.value,
        initialFriendEmailToZoom: initialFriendEmailToZoom.value,
        refreshing: refreshing.value,
        loading: dataLoading.value
      },
      handlers: {
        onInviteFriend: () => { showInviteDialog.value = true },
        onRefresh: refreshFriendsData,
        onFriendLocated: handleFriendLocated
      }
    },
    invites: {
      component: InvitationsTab,
      props: {
        receivedInvites: receivedInvites.value,
        sentInvites: sentInvites.value,
        inviteActionsLoading: inviteActionsLoading,
        bulkActionsLoading: bulkActionsLoading
      },
      handlers: {
        onAcceptInvite: handleAcceptInvite,
        onRejectInvite: handleRejectInvite,
        onCancelInvite: handleCancelInvite,
        onAcceptAll: handleAcceptAllInvites,
        onRejectAll: handleRejectAllInvites,
        onCancelAll: handleCancelAllInvites
      }
    }
  }

  const tabConfig = components[activeTab.value]
  if (!tabConfig) return null

  return h(tabConfig.component, { ...tabConfig.props, ...tabConfig.handlers })
})

const inviteLoading = ref(false)
const refreshing = ref(false)
const dataLoading = ref(true)
const friendsMapTabRef = ref(null)
const initialFriendEmailToZoom = ref(null)
const filteredUsers = ref(null)

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

// Methods
const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab && selectedTab.key !== activeTab.value) {
    router.push({name: 'Friends', params: {tab: selectedTab.key}})
  }
}

const switchToTab = (tabKey) => {
  if (tabKey !== activeTab.value) {
    router.push({name: 'Friends', params: {tab: tabKey}})
  }
}

watch(() => route.params.tab, (newTab) => {
  const validTabs = ['map', 'friends', 'invites']
  if (validTabs.includes(newTab)) {
    activeTab.value = newTab
  } else {
    // If tab is invalid or not present, default to 'map' and update URL
    router.replace({name: 'Friends', params: {tab: 'map'}})
  }
}, {immediate: true})

watch(() => route.query.friend, (newFriendEmail) => {
  initialFriendEmailToZoom.value = newFriendEmail || null
}, {immediate: true})

const validateInviteForm = () => {
  inviteErrors.value = {}

  const emailToValidate = typeof inviteForm.value.email === 'object' && inviteForm.value.email !== null
      ? inviteForm.value.email.email
      : inviteForm.value.email

  if (!emailToValidate?.trim()) {
    inviteErrors.value.email = 'Email address is required'
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailToValidate)) {
    inviteErrors.value.email = 'Please enter a valid email address'
  }

  return Object.keys(inviteErrors.value).length === 0
}

const sendInvite = async () => {
  if (!validateInviteForm()) return

  inviteLoading.value = true

  const emailToSend = typeof inviteForm.value.email === 'object' && inviteForm.value.email !== null
      ? inviteForm.value.email.email
      : inviteForm.value.email

  try {
    await friendsStore.sendFriendRequest(emailToSend.trim())

    toast.add({
      severity: 'success',
      summary: 'Invitation Sent',
      detail: `Friend request sent to ${emailToSend}`,
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
  inviteForm.value = {email: '', message: ''}
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
  router.replace({
    name: 'Friends',
    params: {tab: 'map'},
    query: {friend: friend.email}
  }).then(() => {
    // Give more time for map to initialize after tab switch
    setTimeout(() => {
      if (friendsMapTabRef.value) {
        friendsMapTabRef.value.zoomToFriend(friend)
      } else {
        // Retry after additional delay if map ref not ready
        setTimeout(() => {
          if (friendsMapTabRef.value) {
            friendsMapTabRef.value.zoomToFriend(friend)
          }
        }, 500)
      }
    }, 300)
  })
}

const handleAcceptInvite = async (inviteId) => {
  inviteActionsLoading[inviteId] = {accept: true}

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
  inviteActionsLoading[inviteId] = {reject: true}

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
  inviteActionsLoading[inviteId] = {cancel: true}

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

const refreshFriendsData = async () => {
  refreshing.value = true

  try {
    // Execute both async calls in parallel (same as onMounted)
    const [_, lastPosition] = await Promise.all([
      friendsStore.refreshAllFriendsData(),
      locationStore.getLastKnownPosition()
    ])

    if (lastPosition) {
      const user = authStore.user
      currentUser.value = {
        ...user,
        latitude: lastPosition.lat,
        longitude: lastPosition.lon,
        timestamp: lastPosition.timestamp
      }
    }

    toast.add({
      severity: 'success',
      summary: 'Data Refreshed',
      detail: 'Friends data and locations have been updated',
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

const searchUsers = async (event) => {
  try {
    const users = await friendsStore.searchUsersToInvite(event.query)
    filteredUsers.value = users.map(user => ({
      fullName: user.fullName,
      email: user.email,
      avatar: user.avatar
    }))
  } catch (error) {
    console.error('Error searching users:', error)
    filteredUsers.value = []
  }
}

// Lifecycle
onMounted(async () => {
  try {
    // Execute both async calls in parallel
    const [_, lastPosition] = await Promise.all([
      friendsStore.refreshAllFriendsData(),
      locationStore.getLastKnownPosition()
    ])

    if (lastPosition) {
      const user = authStore.user
      currentUser.value = {
        ...user,
        latitude: lastPosition.lat,
        longitude: lastPosition.lon,
        timestamp: lastPosition.timestamp
      }
    }

  } catch (error) {
    console.error('Error loading friends page data:', error)
    toast.add({
      severity: 'error',
      summary: 'Loading Failed',
      detail: 'Failed to load page data',
      life: 5000
    })
  } finally {
    dataLoading.value = false
  }
})
</script>

<style scoped>
.friends-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0;
  box-sizing: border-box;
  width: 100%;
  min-height: auto;
  border-radius: var(--gp-radius-large);
}

.friends-page * {
  box-sizing: border-box;
}

/* Tabs */
  .friends-tabs {
  margin-bottom: 2rem;
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

:deep(.gp-page-container) {
  margin: 0;
  padding: 0;
}

:deep(.gp-tab-menu) {
  border-radius: 0;
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
}
</style>