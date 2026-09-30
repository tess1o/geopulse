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
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectButton from 'primevue/selectbutton'
import FriendsMapTab from './FriendsMapTab.vue'
import FriendsTimelineTab from './FriendsTimelineTab.vue'

const { t } = useI18n()

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
const modeOptions = computed(() => [
  {
    label: t('friends.locationTab.liveLocation'),
    value: 'live',
    icon: 'pi pi-map-marker'
  },
  {
    label: t('friends.locationTab.timelineHistory'),
    value: 'timeline',
    icon: 'pi pi-history'
  }
])
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

/* Dark mode */
.p-dark .mode-toggle-segmented {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .mode-toggle-segmented {
    top: 0.75rem;
    left: 3.5rem;
    padding: 0.2rem;
  }
}

@media (max-width: 480px) {
  .mode-toggle-segmented {
    top: 0.5rem;
    left: 3rem;
  }

  /* Hide text labels on very small screens */
  .toggle-label {
    display: none;
  }
}
</style>
