<template>
  <div class="map-controls">
    <div class="control-group">
<!--      <button-->
<!--        @click="handleToggleFriends"-->
<!--        :class="{ active: showFriends }"-->
<!--        :title="showFriends ? 'Hide Friends' : 'Show Friends'"-->
<!--        class="control-button"-->
<!--        :disabled="!map"-->
<!--      >-->
<!--        <i class="pi pi-users"></i>-->
<!--      </button>-->

      <button
        @click="handleToggleFavorites"
        :class="{ active: showFavorites }"
        :title="showFavorites ? 'Hide Favorites' : 'Show Favorites'"
        class="control-button"
        :disabled="!map"
      >
        <i class="pi pi-bookmark"></i>
      </button>

      <button
        @click="handleToggleTimeline"
        :class="{ active: showTimeline }"
        :title="showTimeline ? 'Hide Timeline' : 'Show Timeline'"
        class="control-button"
        :disabled="!map"
      >
        <i class="pi pi-clock"></i>
      </button>

      <button
        @click="handleTogglePath"
        :class="{ active: showPath }"
        :title="showPath ? 'Hide Path' : 'Show Path'"
        class="control-button"
        :disabled="!map"
      >
        <i class="pi pi-map"></i>
      </button>
    </div>

    <div v-if="showZoomControls" class="control-group">
      <button
        @click="handleZoomToData"
        title="Zoom to Data"
        class="control-button"
        :disabled="!map"
      >
        <i class="pi pi-map-marker"></i>
      </button>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  map: {
    type: Object,
    default: null
  },
  showFriends: {
    type: Boolean,
    default: false
  },
  showFavorites: {
    type: Boolean,
    default: false
  },
  showTimeline: {
    type: Boolean,
    default: false
  },
  showPath: {
    type: Boolean,
    default: false
  },
  showZoomControls: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits([
  'toggle-friends',
  'toggle-favorites', 
  'toggle-timeline',
  'toggle-path',
  'zoom-to-data'
])

// Event handlers
// const handleToggleFriends = () => {
//   emit('toggle-friends', !props.showFriends)
// }

const handleToggleFavorites = () => {
  emit('toggle-favorites', !props.showFavorites)
}

const handleToggleTimeline = () => {
  emit('toggle-timeline', !props.showTimeline)
}

const handleTogglePath = () => {
  emit('toggle-path', !props.showPath)
}

const handleZoomToData = () => {
  emit('zoom-to-data')
}
</script>

<style scoped>
.map-controls {
  display: flex;
  gap: var(--gp-spacing-sm, 0.5rem);
  flex-direction: column;
}

.control-group {
  display: flex;
  gap: var(--gp-spacing-xs, 0.25rem);
  flex-direction: column;
  background: var(--gp-surface-white, white);
  border-radius: var(--gp-radius-medium, 8px);
  padding: var(--gp-spacing-xs, 0.25rem);
  box-shadow: var(--gp-shadow-medium, 0 4px 8px rgba(0, 0, 0, 0.1));
  border: 1px solid var(--gp-border-light, rgba(0, 0, 0, 0.1));
}

.control-button {
  width: 40px;
  height: 40px;
  background: transparent;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  border-radius: var(--gp-radius-small, 4px);
  transition: all 0.2s ease;
  color: var(--gp-text-secondary, #64748b);
}

.control-button:hover:not(:disabled) {
  background-color: var(--gp-surface-light, #f8fafc);
  color: var(--gp-primary, #1a56db);
  transform: translateY(-1px);
}

.control-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.control-button i {
  font-size: 16px;
  transition: color 0.2s ease;
}

.control-button.active {
  background: var(--gp-primary, #1a56db);
  color: white;
}

.control-button.active:hover {
  background: var(--gp-primary-dark, #1e40af);
  transform: translateY(-1px);
}

.control-button:focus {
  outline: none;
  box-shadow: 0 0 0 2px var(--gp-primary, #1a56db);
}

/* Dark mode */
.p-dark .control-group {
  background: var(--gp-surface-dark, #1e293b);
  border-color: var(--gp-border-dark, rgba(255, 255, 255, 0.1));
}

.p-dark .control-button {
  color: var(--gp-text-secondary, #cbd5e1);
}

.p-dark .control-button:hover:not(:disabled) {
  background-color: var(--gp-surface-darker, #0f172a);
  color: var(--gp-primary-light, #3b82f6);
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .map-controls {
    flex-direction: row;
  }

  .control-group {
    flex-direction: row;
  }

  .control-button {
    width: 35px;
    height: 35px;
  }

  .control-button i {
    font-size: 14px;
  }
}
</style>