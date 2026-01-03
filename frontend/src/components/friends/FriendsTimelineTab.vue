<template>
  <div class="friends-timeline-tab">
    <!-- Main Timeline View - Always render to ensure date picker initializes -->
    <div class="timeline-main">
      <!-- Left Pane: Map -->
      <div class="left-pane">
        <div v-if="isLoading" class="loading-overlay">
          <ProgressSpinner />
          <p>Loading timelines...</p>
        </div>
        <div v-else-if="!hasSelectedUsers" class="empty-message">
          <i class="pi pi-info-circle"></i>
          <p>Select friends to view their timelines</p>
        </div>
        <FriendsTimelineMap
            v-else
            :multi-user-timeline="multiUserTimeline"
            :selected-user-ids="selectedUserIds"
            :user-color-map="userColorMap"
            :selected-item="selectedTimelineItem"
        />
      </div>

      <!-- Right Pane: Controls + Timeline List -->
      <div class="right-pane">
        <FriendsTimelineDatePicker />

        <!-- Empty State: No friends with permission -->
        <div v-if="!isLoading && availableUsers.length === 0" class="empty-state-card">
          <div class="empty-icon">
            <i class="pi pi-calendar"></i>
          </div>
          <h3 class="empty-title">No Shared Timelines</h3>
          <p class="empty-description">
            None of your friends have enabled timeline sharing yet. Ask them to enable it in Friends settings!
          </p>
        </div>

        <template v-else>
          <UserSelectionPanel
              :available-users="availableUsers"
              :selected-user-ids="selectedUserIds"
              @toggle-user="toggleUser"
              @select-all="selectAll"
              @deselect-all="deselectAll"
          />

          <MergedTimelineList
              v-if="hasSelectedUsers"
              :timeline-items="mergedTimelineItems"
              :loading="isLoading"
              @item-click="handleTimelineItemClick"
          />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useFriendsTimelineStore } from '@/stores/friendsTimeline'
import { useDateRangeStore } from '@/stores/dateRange'
import { useToast } from 'primevue/usetoast'
import ProgressSpinner from 'primevue/progressspinner'
import FriendsTimelineMap from './FriendsTimelineMap.vue'
import FriendsTimelineDatePicker from './FriendsTimelineDatePicker.vue'
import UserSelectionPanel from './UserSelectionPanel.vue'
import MergedTimelineList from './MergedTimelineList.vue'

const toast = useToast()
const friendsTimelineStore = useFriendsTimelineStore()
const dateRangeStore = useDateRangeStore()

const { multiUserTimeline, selectedUserIds, userColorMap, isLoading } = storeToRefs(friendsTimelineStore)
const { dateRange } = storeToRefs(dateRangeStore)

const availableUsers = computed(() => friendsTimelineStore.availableUsers)
const mergedTimelineItems = computed(() => friendsTimelineStore.mergedTimelineItems)
const hasSelectedUsers = computed(() => friendsTimelineStore.hasSelectedUsers)

const selectedTimelineItem = ref(null)

// Load timeline data on mount
onMounted(async () => {
  // Wait a tick to ensure FriendsTimelineDatePicker has initialized
  await new Promise(resolve => setTimeout(resolve, 100))

  // Load data if date range is set
  if (dateRange.value && dateRange.value.length === 2) {
    await loadTimelineData()
  }
})

// Watch for date range changes
watch(dateRange, async (newRange) => {
  if (newRange && newRange.length === 2) {
    await loadTimelineData()
  }
}, { deep: true })

async function loadTimelineData() {
  if (!dateRange.value || dateRange.value.length !== 2) {
    return
  }

  const [startTime, endTime] = dateRange.value

  try {
    await friendsTimelineStore.fetchMultiUserTimeline(startTime, endTime)
  } catch (error) {
    console.error('Failed to load multi-user timeline:', error)
    toast.add({
      severity: 'error',
      summary: 'Failed to Load Timelines',
      detail: error.message || 'Could not load friend timelines',
      life: 5000
    })
  }
}

function toggleUser(userId) {
  friendsTimelineStore.toggleUserSelection(userId)
}

function selectAll() {
  friendsTimelineStore.selectAllUsers()
}

function deselectAll() {
  friendsTimelineStore.deselectAllUsers()
}

function handleTimelineItemClick(item) {
  console.log('Timeline item clicked:', item)
  selectedTimelineItem.value = item
}
</script>

<style scoped>
.friends-timeline-tab {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* Empty State Card */
.empty-state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 2rem 1rem;
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  background: var(--gp-primary-light);
  color: var(--gp-primary);
  border-radius: 50%;
  font-size: 1.5rem;
  margin-bottom: 1rem;
}

.empty-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.5rem;
}

.empty-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  line-height: 1.5;
}

/* Main Timeline Layout */
.timeline-main {
  flex: 1;
  display: flex;
  gap: 1rem;
  height: 100%;
  overflow: hidden;
}

.left-pane {
  flex: 7;
  display: flex;
  flex-direction: column;
  min-height: 500px;
  position: relative;
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
}

.right-pane {
  flex: 3;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  overflow-y: auto;
  padding-right: 0.5rem;
}

.loading-overlay,
.empty-message {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--gp-surface-light);
  z-index: 1000;
  gap: 1rem;
}

.empty-message {
  color: var(--gp-text-secondary);
  font-size: 1rem;
}

.empty-message i {
  font-size: 2rem;
}

/* Responsive Design */
@media (max-width: 1024px) {
  .timeline-main {
    flex-direction: column;
    gap: 0.75rem;
  }

  .left-pane,
  .right-pane {
    flex: none;
    width: 100%;
  }

  .left-pane {
    min-height: 450px;
    max-height: 450px;
    height: 450px;
  }
}

@media (max-width: 768px) {
  .timeline-main {
    gap: 0.75rem;
  }

  .left-pane {
    min-height: 400px;
    max-height: 400px;
    height: 400px;
  }

  .right-pane {
    gap: 0.75rem;
    padding-right: 0;
  }
}

@media (max-width: 480px) {
  .timeline-main {
    gap: 0.5rem;
  }

  .left-pane {
    min-height: 350px;
    max-height: 350px;
    height: 350px;
  }

  .right-pane {
    gap: 0.5rem;
  }
}
</style>
