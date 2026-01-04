<template>
  <div class="friends-location-tab">
    <!-- Mode Toggle -->
    <div class="mode-toggle-container">
      <SelectButton
        v-model="viewMode"
        :options="modeOptions"
        optionLabel="label"
        optionValue="value"
        class="mode-toggle"
      >
        <template #option="slotProps">
          <i :class="slotProps.option.icon"></i>
          <span>{{ slotProps.option.label }}</span>
        </template>
      </SelectButton>
    </div>

    <!-- Live Location Mode -->
    <div v-if="viewMode === 'live'" class="mode-content">
      <FriendsMapTab
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
    </div>

    <!-- Timeline History Mode -->
    <div v-if="viewMode === 'timeline'" class="mode-content">
      <FriendsTimelineTab />
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
  margin-top: calc(-1 * var(--gp-spacing-xl)); /* Offset parent gp-tab-content padding */
}

/* Mode Toggle */
.mode-toggle-container {
  padding: 1rem 0;
  display: flex;
  justify-content: center;
  border-bottom: 1px solid var(--gp-border-light);
  background: var(--gp-surface-white);
}

.mode-toggle {
  display: flex;
  gap: 0.5rem;
}

:deep(.p-selectbutton .p-button) {
  padding: 0.75rem 1.5rem !important;
  font-weight: 500 !important;
  border: 1px solid #dee2e6 !important;
  background: white !important;
  color: #6c757d !important;
  transition: all 0.2s ease;
}

:deep(.p-selectbutton .p-button:hover) {
  background: #f8f9fa !important;
  border-color: var(--primary-color) !important;
  color: #495057 !important;
}

:deep(.p-selectbutton .p-button.p-highlight) {
  background: var(--primary-color) !important;
  border-color: var(--primary-color) !important;
  color: white !important;
  font-weight: 600 !important;
}

/* Dark mode */
.p-dark :deep(.p-selectbutton .p-button) {
  border-color: #495057 !important;
  background: #343a40 !important;
  color: #adb5bd !important;
}

.p-dark :deep(.p-selectbutton .p-button:hover) {
  background: #495057 !important;
  border-color: var(--primary-color) !important;
  color: #f8f9fa !important;
}

.p-dark :deep(.p-selectbutton .p-button.p-highlight) {
  background: var(--primary-color) !important;
  border-color: var(--primary-color) !important;
  color: white !important;
}

:deep(.p-selectbutton .p-button i) {
  margin-right: 0.5rem;
}

/* Mode Content */
.mode-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .mode-toggle-container {
    padding: 0.75rem 0;
  }

  :deep(.p-selectbutton .p-button) {
    padding: 0.625rem 1rem;
    font-size: 0.9rem;
  }

  :deep(.p-selectbutton .p-button i) {
    margin-right: 0.375rem;
  }
}

@media (max-width: 480px) {
  :deep(.p-selectbutton .p-button span) {
    display: none;
  }

  :deep(.p-selectbutton .p-button i) {
    margin-right: 0;
    font-size: 1.1rem;
  }

  :deep(.p-selectbutton .p-button) {
    padding: 0.625rem 0.875rem;
  }
}
</style>
