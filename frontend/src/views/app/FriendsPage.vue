<template>
  <AppLayout
    :showInviteFriendButton="true"
    @invite-friend="showInviteDialog = true"
  >
    <PageContainer padding="none" maxWidth="none">
      <div class="friends-page">
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
              v-bind="currentTabBindings"
              :key="activeTab"
              :ref="activeTab === 'live' ? (el) => friendsMapTabRef = el : undefined"
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
import {ref, computed, onMounted, onUnmounted, reactive, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {storeToRefs} from 'pinia'
import {useToast} from 'primevue/usetoast'
import {useConfirm} from 'primevue/useconfirm'
import {useLocationStore} from '@/stores/location'
import {useAuthStore} from '@/stores/auth'
import AutoComplete from 'primevue/autocomplete'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Tab components
import FriendsListTab from '@/components/friends/FriendsListTab.vue'
import FriendsMapTab from '@/components/friends/FriendsMapTab.vue'
import FriendsTimelineTab from '@/components/friends/FriendsTimelineTab.vue'
import InvitationsTab from '@/components/friends/InvitationsTab.vue'

// Store
import {useFriendsStore} from '@/stores/friends'
import friendsService from '@/services/friendsService'

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

// Computed: Filter friends based on what they share with current user
const friendsWithLiveLocation = computed(() => {
  return friends.value?.filter(f => f.friendSharesLiveLocation === true) || []
})

const friendsWithTimeline = computed(() => {
  return friends.value?.filter(f => f.friendSharesTimeline === true) || []
})

const LIVE_FRIEND_FILTER_NONE_QUERY_VALUE = 'none'

const getLiveFriendFilterKey = (friend) => {
  const key = friend?.friendId || friend?.userId || friend?.id || friend?.email
  return key !== null && key !== undefined ? String(key) : null
}

const getFirstQueryValue = (value) => {
  if (Array.isArray(value)) {
    return value[0] ?? null
  }

  return value ?? null
}

const uniqueNormalizedKeys = (keys) => {
  const normalized = []
  const seen = new Set()

  if (!Array.isArray(keys)) {
    return normalized
  }

  keys.forEach((key) => {
    const normalizedKey = key !== null && key !== undefined ? String(key).trim() : null
    if (!normalizedKey || seen.has(normalizedKey)) {
      return
    }

    seen.add(normalizedKey)
    normalized.push(normalizedKey)
  })

  return normalized
}

const filterSelectionByAvailableKeys = (selectedKeys, availableKeys) => {
  const normalizedAvailableKeys = uniqueNormalizedKeys(availableKeys)
  const selectedSet = new Set(uniqueNormalizedKeys(selectedKeys))
  return normalizedAvailableKeys.filter(key => selectedSet.has(key))
}

const parseLiveFriendSelectionFromQuery = (queryValue) => {
  const rawValue = getFirstQueryValue(queryValue)
  if (rawValue === null || rawValue === undefined) {
    return null
  }

  const normalizedValue = String(rawValue).trim()
  if (!normalizedValue) {
    return null
  }

  if (normalizedValue.toLowerCase() === LIVE_FRIEND_FILTER_NONE_QUERY_VALUE) {
    return []
  }

  return uniqueNormalizedKeys(normalizedValue.split(','))
}

const areArraysEqual = (left, right) => {
  if (left.length !== right.length) {
    return false
  }

  return left.every((value, index) => value === right[index])
}

const areQueriesEqual = (left, right) => {
  const leftKeys = Object.keys(left || {}).sort()
  const rightKeys = Object.keys(right || {}).sort()

  if (!areArraysEqual(leftKeys, rightKeys)) {
    return false
  }

  return leftKeys.every((key) => String(left[key]) === String(right[key]))
}

const liveFriendFilterAvailableKeys = computed(() => {
  const keys = []
  const seen = new Set()

  friendsWithLiveLocation.value.forEach((friend) => {
    const key = getLiveFriendFilterKey(friend)
    if (!key || seen.has(key)) {
      return
    }

    seen.add(key)
    keys.push(key)
  })

  return keys
})

const selectedLiveFriendKeysFromQuery = computed(() => {
  const parsedSelection = parseLiveFriendSelectionFromQuery(route.query.friends)
  if (parsedSelection === null) {
    return null
  }

  return filterSelectionByAvailableKeys(parsedSelection, liveFriendFilterAvailableKeys.value)
})

const hasLiveFriendFilterQuery = computed(() => {
  return parseLiveFriendSelectionFromQuery(route.query.friends) !== null
})

// State
const activeTab = ref()
const showInviteDialog = ref(false)

// Tab configuration
const tabItems = computed(() => {
  const tabs = [
    {
      label: 'Live',
      icon: 'pi pi-map-marker',
      key: 'live',
      badge: friendsWithLiveLocation.value?.length > 0 ? friendsWithLiveLocation.value.length : null,
      badgeType: 'success'
    },
    {
      label: 'Timeline',
      icon: 'pi pi-history',
      key: 'timeline',
      badge: friendsWithTimeline.value?.length > 0 ? friendsWithTimeline.value.length : null,
      badgeType: 'success'
    },
    {
      label: 'Friends',
      icon: 'pi pi-users',
      key: 'friends',
      badge: friends.value?.length > 0 ? friends.value.length : null,
      badgeType: 'info'
    }
  ]

  // Only show Invitations tab if there are pending invitations (received or sent)
  const hasInvites = (receivedInvites.value?.length > 0) || (sentInvites.value?.length > 0)
  if (hasInvites) {
    tabs.push({
      label: 'Invitations',
      icon: 'pi pi-envelope',
      key: 'invites',
      badge: receivedInvites.value?.length > 0 ? receivedInvites.value.length : null,
      badgeType: 'danger'
    })
  }

  return tabs
})

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})

const currentTabConfig = computed(() => {
  const components = {
    live: {
      component: FriendsMapTab,
      props: {
        friends: friendsWithLiveLocation.value,  // Only friends who shared live location
        selectedFriendKeys: selectedLiveFriendKeysFromQuery.value || [],
        useDefaultSelection: !hasLiveFriendFilterQuery.value,
        currentUser: currentUser.value,
        initialFriendEmailToZoom: initialFriendEmailToZoom.value,
        refreshing: refreshing.value,
        loading: dataLoading.value,
        friendTrails: friendLocationTrails.value,
        showFriendLocationTrails: showFriendLocationTrails.value
      },
      handlers: {
        onInviteFriend: () => { showInviteDialog.value = true },
        onRefresh: refreshFriendsData,
        onFriendLocated: handleFriendLocated,
        onShowAll: handleShowAll,
        onToggleTrails: handleToggleFriendLocationTrails,
        onSelectionChange: handleLiveFriendSelectionChange
      }
    },
    timeline: {
      component: FriendsTimelineTab,
      props: {
        friends: friendsWithTimeline.value  // Only friends who shared timeline
      },
      handlers: {}
    },
    friends: {
      component: FriendsListTab,
      props: {
        friends: friends.value  // All friends (for managing permissions)
      },
      handlers: {
        onInviteFriend: () => { showInviteDialog.value = true },
        onShowOnMap: showFriendOnLiveMap,
        onShowTimeline: showFriendTimeline,
        onDeleteFriend: confirmDeleteFriend
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

  return components[activeTab.value] || null
})

const currentTabComponent = computed(() => {
  return currentTabConfig.value?.component || null
})

const currentTabBindings = computed(() => {
  const config = currentTabConfig.value
  if (!config) return {}

  return {
    ...(config.props || {}),
    ...(config.handlers || {})
  }
})

const inviteLoading = ref(false)
const refreshing = ref(false)
const dataLoading = ref(true)
const friendsMapTabRef = ref(null)
const initialFriendEmailToZoom = ref(null)
const filteredUsers = ref(null)
const LIVE_TAB_POLL_INTERVAL_MS = 15 * 1000 // 15 seconds
let liveTabPollIntervalId = null
let liveTabPollInFlight = false

// Form data
const inviteForm = ref({
  email: '',
  message: ''
})

const FRIEND_TRAIL_WINDOW_MINUTES = 60
const FRIEND_TRAIL_TOAST_COOLDOWN_MS = 15000
const showFriendLocationTrails = ref(true)
const friendLocationTrails = ref({})
const lastFriendTrailSuccessToastAt = ref(0)
const lastFriendTrailErrorToastAt = ref(0)

const inviteErrors = ref({})

// Loading states
const inviteActionsLoading = reactive({})
const bulkActionsLoading = reactive({
  acceptAll: false,
  rejectAll: false,
  cancelAll: false
})
let friendTrailFetchInFlight = false

const updateLiveFriendFilterQuery = (selectedKeys, availableKeys) => {
  const routeTab = typeof route.params.tab === 'string' ? route.params.tab : null
  const targetTab = ['live', 'timeline', 'friends', 'invites'].includes(routeTab) ? routeTab : 'live'
  const normalizedAvailableKeys = uniqueNormalizedKeys(availableKeys)
  const normalizedSelection = filterSelectionByAvailableKeys(selectedKeys, normalizedAvailableKeys)

  let nextFriendsQueryValue
  if (normalizedSelection.length === normalizedAvailableKeys.length) {
    nextFriendsQueryValue = undefined
  } else if (normalizedSelection.length === 0) {
    nextFriendsQueryValue = LIVE_FRIEND_FILTER_NONE_QUERY_VALUE
  } else {
    nextFriendsQueryValue = normalizedSelection.join(',')
  }

  const nextQuery = { ...route.query }

  if (nextFriendsQueryValue === undefined) {
    delete nextQuery.friends
  } else {
    nextQuery.friends = nextFriendsQueryValue
  }

  const focusedFriendEmail = getFirstQueryValue(nextQuery.friend)
  if (focusedFriendEmail) {
    const focusedFriend = friendsWithLiveLocation.value.find(friend => friend.email === focusedFriendEmail)
    const focusedFriendKey = focusedFriend ? getLiveFriendFilterKey(focusedFriend) : null
    const hasActiveFilter = nextFriendsQueryValue !== undefined

    if (!hasActiveFilter || !focusedFriendKey || normalizedSelection.includes(focusedFriendKey)) {
      // Keep focused friend query when it matches current filter.
    } else {
      delete nextQuery.friend
    }
  }

  if (areQueriesEqual(route.query, nextQuery)) {
    return
  }

  router.replace({
    name: 'Friends',
    params: { tab: targetTab },
    query: nextQuery
  })
}

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
  const validTabs = ['live', 'timeline', 'friends', 'invites']

  // Check if the requested tab is valid
  if (validTabs.includes(newTab)) {
    // Special case: if trying to access 'invites' but there are no invites, redirect to 'live'
    if (newTab === 'invites') {
      const hasInvites = (receivedInvites.value?.length > 0) || (sentInvites.value?.length > 0)
      if (!hasInvites) {
        router.replace({name: 'Friends', params: {tab: 'live'}})
        return
      }
    }
    activeTab.value = newTab
  } else {
    // If tab is invalid or not present, default to 'live' and update URL
    router.replace({name: 'Friends', params: {tab: 'live'}})
  }
}, {immediate: true})

watch(() => route.query.friend, (newFriendEmail) => {
  initialFriendEmailToZoom.value = newFriendEmail || null
}, {immediate: true})

watch([() => route.query.friends, liveFriendFilterAvailableKeys], ([queryValue, availableKeys]) => {
  const parsedSelection = parseLiveFriendSelectionFromQuery(queryValue)
  if (parsedSelection === null) {
    return
  }

  const normalizedSelection = filterSelectionByAvailableKeys(parsedSelection, availableKeys)
  if (areArraysEqual(parsedSelection, normalizedSelection)) {
    return
  }

  updateLiveFriendFilterQuery(normalizedSelection, availableKeys)
}, { immediate: true })

// Watch for when all invites are cleared while on the invites tab
watch([receivedInvites, sentInvites], () => {
  const hasInvites = (receivedInvites.value?.length > 0) || (sentInvites.value?.length > 0)
  if (!hasInvites && activeTab.value === 'invites') {
    // If we're on the invites tab and there are no more invites, switch to live
    router.replace({name: 'Friends', params: {tab: 'live'}})
  }
})

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

const showFriendOnLiveMap = (friend) => {
  router.replace({
    name: 'Friends',
    params: {tab: 'live'},
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

const showFriendTimeline = (friend) => {
  router.replace({
    name: 'Friends',
    params: {tab: 'timeline'},
    query: {friend: friend.email}
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
  const friendEmail = friend?.email
  if (!friendEmail) {
    return
  }

  if (route.params.tab === 'live' && route.query.friend === friendEmail) {
    return
  }

  router.replace({
    name: 'Friends',
    params: {tab: 'live'},
    query: {
      ...route.query,
      friend: friendEmail
    }
  })
}

const handleShowAll = () => {
  const nextQuery = { ...route.query }
  delete nextQuery.friend

  router.replace({
    name: 'Friends',
    params: {tab: 'live'},
    query: nextQuery
  })
}

const handleLiveFriendSelectionChange = (payload = {}) => {
  const selectedKeys = Array.isArray(payload.selectedKeys) ? payload.selectedKeys : []
  const availableKeys = Array.isArray(payload.availableKeys)
      ? payload.availableKeys
      : liveFriendFilterAvailableKeys.value

  updateLiveFriendFilterQuery(selectedKeys, availableKeys)
}

const handleToggleFriendLocationTrails = (value) => {
  const shouldShowTrails = typeof value === 'boolean' ? value : !showFriendLocationTrails.value
  showFriendLocationTrails.value = shouldShowTrails

  if (shouldShowTrails) {
    showFriendTrailVisibleToast(friendLocationTrails.value)
  }
}

const updateCurrentUserFromLastPosition = (lastPosition) => {
  if (!lastPosition) return

  const user = authStore.user
  currentUser.value = {
    ...user,
    latitude: lastPosition.lat,
    longitude: lastPosition.lon,
    timestamp: lastPosition.timestamp
  }
}

const getFriendLocationKey = (friend) => {
  return friend?.friendId || friend?.userId || friend?.id
}

const extractTrailsByFriend = (response) => {
  if (!response) return {}

  const nestedData = response?.data?.data
  if (nestedData && typeof nestedData === 'object' && !Array.isArray(nestedData)) {
    return nestedData
  }

  const directData = response?.data
  if (directData && typeof directData === 'object' && !Array.isArray(directData)) {
    return directData
  }

  if (typeof response === 'object' && !Array.isArray(response)) {
    return response
  }

  return {}
}

const shouldShowFriendTrailToast = (toastType) => {
  const now = Date.now()
  if (toastType === 'success') {
    if (now - lastFriendTrailSuccessToastAt.value < FRIEND_TRAIL_TOAST_COOLDOWN_MS) {
      return false
    }
    lastFriendTrailSuccessToastAt.value = now
    return true
  }

  if (now - lastFriendTrailErrorToastAt.value < FRIEND_TRAIL_TOAST_COOLDOWN_MS) {
    return false
  }
  lastFriendTrailErrorToastAt.value = now
  return true
}

const showFriendTrailVisibleToast = (trails) => {
  if (!shouldShowFriendTrailToast('success')) {
    return
  }

  const friendIds = Object.keys(trails || {})
  const friendsWithPoints = friendIds.filter(friendId => Array.isArray(trails[friendId]) && trails[friendId].length > 0)
  const totalPoints = friendsWithPoints.reduce((sum, friendId) => sum + trails[friendId].length, 0)

  if (totalPoints > 0) {
    toast.add({
      severity: 'success',
      summary: 'Location Trails Enabled',
      detail: `Showing ${totalPoints} points across ${friendsWithPoints.length} friend trail(s)`,
      life: 3500
    })
    return
  }

  toast.add({
    severity: 'info',
    summary: 'Location Trails Enabled',
    detail: `No trail points found for the last ${FRIEND_TRAIL_WINDOW_MINUTES} minutes`,
    life: 3500
  })
}

const showFriendTrailErrorToast = (error) => {
  if (!shouldShowFriendTrailToast('error')) {
    return
  }

  const errorMessage = error?.response?.data?.message || error?.message || 'Failed to load friend location trails'
  toast.add({
    severity: 'error',
    summary: 'Trail Loading Failed',
    detail: errorMessage,
    life: 5000
  })
}

const refreshFriendLocationTrails = async ({ notifyOnError = false } = {}) => {
  if (!friendsWithLiveLocation.value || friendsWithLiveLocation.value.length === 0) {
    friendLocationTrails.value = {}
    return {}
  }

  if (friendTrailFetchInFlight) {
    return friendLocationTrails.value
  }

  friendTrailFetchInFlight = true

  try {
    const trails = {}
    friendsWithLiveLocation.value.forEach((friend) => {
      const key = getFriendLocationKey(friend)
      if (key) {
        trails[String(key)] = []
      }
    })

    const response = await friendsService.getFriendsLocationTrails(FRIEND_TRAIL_WINDOW_MINUTES)
    const trailsByFriend = extractTrailsByFriend(response)
    Object.entries(trailsByFriend).forEach(([friendId, points]) => {
      trails[String(friendId)] = Array.isArray(points) ? points : []
    })

    friendLocationTrails.value = trails
    return trails
  } catch (error) {
    if (notifyOnError) {
      showFriendTrailErrorToast(error)
    }
    return friendLocationTrails.value
  } finally {
    friendTrailFetchInFlight = false
  }
}

const refreshLiveMapData = async () => {
  const [_, lastPosition] = await Promise.all([
    friendsStore.fetchFriends(),
    locationStore.getLastKnownPosition()
  ])

  if (activeTab.value === 'live') {
    await refreshFriendLocationTrails()
  }

  updateCurrentUserFromLastPosition(lastPosition)
}

const refreshFriendsData = async () => {
  refreshing.value = true

  try {
    // Execute both async calls in parallel (same as onMounted)
    const [_, lastPosition] = await Promise.all([
      friendsStore.refreshAllFriendsData(),
      locationStore.getLastKnownPosition()
    ])

    if (activeTab.value === 'live') {
      await refreshFriendLocationTrails({ notifyOnError: true })
    }

    updateCurrentUserFromLastPosition(lastPosition)

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

const stopLiveTabPolling = () => {
  if (liveTabPollIntervalId) {
    clearInterval(liveTabPollIntervalId)
    liveTabPollIntervalId = null
  }
}

const canPollLiveTab = () => {
  if (typeof document !== 'undefined' && document.visibilityState !== 'visible') {
    return false
  }

  return activeTab.value === 'live' && !dataLoading.value && !refreshing.value
}

const pollLiveTabData = async () => {
  if (!canPollLiveTab() || liveTabPollInFlight) {
    return
  }

  liveTabPollInFlight = true

  try {
    await refreshLiveMapData()
  } catch (error) {
    console.error('Error polling live friends map data:', error)
  } finally {
    liveTabPollInFlight = false
  }
}

const ensureLiveTabPolling = () => {
  if (!canPollLiveTab()) {
    stopLiveTabPolling()
    return
  }

  if (liveTabPollIntervalId) {
    return
  }

  liveTabPollIntervalId = setInterval(() => {
    pollLiveTabData()
  }, LIVE_TAB_POLL_INTERVAL_MS)
}

const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') {
    ensureLiveTabPolling()
    pollLiveTabData()
    return
  }

  stopLiveTabPolling()
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
    // Fetch fresh user profile from server
    await authStore.fetchCurrentUserProfile()

    // Execute both async calls in parallel
    const [_, lastPosition] = await Promise.all([
      friendsStore.refreshAllFriendsData(),
      locationStore.getLastKnownPosition()
    ])

    updateCurrentUserFromLastPosition(lastPosition)

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
    ensureLiveTabPolling()
  }

  document.addEventListener('visibilitychange', handleVisibilityChange)
})

watch([activeTab, dataLoading], () => {
  ensureLiveTabPolling()

  if (activeTab.value === 'live' && !dataLoading.value && !refreshing.value && !friendTrailFetchInFlight) {
    refreshFriendLocationTrails()
  }
}, { immediate: true })

onUnmounted(() => {
  stopLiveTabPolling()
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
})
</script>

<style scoped>
.friends-page {
  max-width: 100%;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
  width: 100%;
  min-height: auto;
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

  /* Make tabs fit in one row on mobile */
  :deep(.p-tabmenu-nav) {
    flex-wrap: nowrap;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  :deep(.p-tabmenuitem) {
    flex-shrink: 0;
    min-width: fit-content;
  }

  :deep(.p-menuitem-link) {
    padding: 0.75rem 1rem;
    font-size: 0.9rem;
  }

  :deep(.p-menuitem-icon) {
    font-size: 1rem;
    margin-right: 0.375rem;
  }
}

@media (max-width: 480px) {
  :deep(.p-menuitem-link) {
    padding: 0.625rem 0.75rem;
    font-size: 0.85rem;
  }

  :deep(.p-menuitem-icon) {
    font-size: 0.9rem;
    margin-right: 0.25rem;
  }

  :deep(.p-menuitem-text) {
    white-space: nowrap;
  }
}
</style>
