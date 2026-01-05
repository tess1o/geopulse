<template>
  <div class="friends-location-tab">
    <div class="mode-content">
      <!-- Segmented Control - Top Right -->
      <div class="mode-toggle-segmented">
        <SelectButton
          v-model="viewMode"
          :options="modeOptions"
          optionLabel="label"
          optionValue="value"
          class="mode-toggle-compact"
        >
          <template #option="slotProps">
            <i :class="slotProps.option.icon"></i>
            <span class="toggle-label">{{ slotProps.option.label }}</span>
          </template>
        </SelectButton>
      </div>

      <!-- Live Location Mode -->
      <FriendsMapTab
        v-if="viewMode === 'live'"
        :friends="friends"
        :current-user="currentUser"
        :initial-friend-email-to-zoom="initialFriendEmailToZoom"
        :refreshing="refreshing"
        :loading="loading"
        @invite-friend="$emit('invite-friend')"
        @refresh="$emit('refresh')"
        @friend-located="$emit('friend-located', $event)"
        @show-all="$emit('show-all')"
      />

      <!-- Timeline History Mode -->
      <FriendsTimelineTab v-else-if="viewMode === 'timeline'" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import SelectButton from 'primevue/selectbutton'
import FriendsMapTab from './FriendsMapTab.vue'
import FriendsTimelineTab from './FriendsTimelineTab.vue'

const props = defineProps({
  friends: {
    type: Array,
    default: () => []
  },
  currentUser: {
    type: Object,
    default: null
  },
  initialFriendEmailToZoom: {
    type: String,
    default: null
  },
  refreshing: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['invite-friend', 'refresh', 'friend-located', 'show-all'])

// View mode state
const viewMode = ref('live')

// Mode options
const modeOptions = [
  {
    label: 'Live Location',
    value: 'live',
    icon: 'pi pi-map-marker'
  },
  {
    label: 'Timeline History',
    value: 'timeline',
    icon: 'pi pi-history'
  }
]
</script>

<style scoped>
.friends-location-tab {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* Mode Content - Now takes full height */
.mode-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* Segmented Control Container - Top Left */
.mode-toggle-segmented {
  position: absolute;
  top: 1rem;
  left: 4rem;
  z-index: 1000;
  background: var(--gp-surface-white);
  border-radius: var(--gp-radius-medium);
  padding: 0.25rem;
  box-shadow: var(--gp-shadow-medium);
  border: 1px solid var(--gp-border-light);
}

/* Compact Toggle Buttons */
.mode-toggle-compact {
  display: flex;
  gap: 0.25rem;
}

:deep(.mode-toggle-compact .p-selectbutton) {
  background: transparent;
}

:deep(.mode-toggle-compact .p-button) {
  padding: 0.5rem 0.875rem !important;
  font-weight: 500 !important;
  font-size: 0.875rem !important;
  border: none !important;
  background: transparent !important;
  color: var(--gp-text-secondary) !important;
  border-radius: var(--gp-radius-small) !important;
  transition: all 0.2s ease;
  white-space: nowrap;
}

:deep(.mode-toggle-compact .p-button:hover) {
  background: var(--gp-surface-light) !important;
  color: var(--gp-text-primary) !important;
}

:deep(.mode-toggle-compact .p-button.p-highlight) {
  background: var(--gp-primary) !important;
  color: white !important;
  font-weight: 600 !important;
}

:deep(.mode-toggle-compact .p-button i) {
  margin-right: 0.375rem;
  font-size: 0.875rem;
}

/* Dark mode */
.p-dark .mode-toggle-segmented {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark :deep(.mode-toggle-compact .p-button) {
  color: var(--gp-text-secondary) !important;
}

.p-dark :deep(.mode-toggle-compact .p-button:hover) {
  background: var(--gp-surface-light) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark :deep(.mode-toggle-compact .p-button.p-highlight) {
  background: var(--gp-primary) !important;
  color: white !important;
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .mode-toggle-segmented {
    top: 0.75rem;
    left: 3.5rem;
    padding: 0.2rem;
  }

  :deep(.mode-toggle-compact .p-button) {
    padding: 0.375rem 0.625rem !important;
    font-size: 0.8rem !important;
  }

  :deep(.mode-toggle-compact .p-button i) {
    margin-right: 0.25rem;
    font-size: 0.8rem;
  }
}

@media (max-width: 480px) {
  .mode-toggle-segmented {
    top: 0.5rem;
    left: 3rem;
  }

  /* Hide text labels on very small screens */
  :deep(.mode-toggle-compact .p-button .toggle-label) {
    display: none;
  }

  :deep(.mode-toggle-compact .p-button i) {
    margin-right: 0;
    font-size: 1rem;
  }

  :deep(.mode-toggle-compact .p-button) {
    padding: 0.5rem !important;
  }
}
</style>
