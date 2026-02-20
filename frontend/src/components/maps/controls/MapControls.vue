<template>
  <div class="map-controls">
    <div class="control-group">

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

      <div
        v-if="showHeatmap"
        ref="heatmapControlRef"
        class="heatmap-control"
      >
        <button
          @click="toggleHeatmapPopover"
          :class="{ active: heatmapEnabled }"
          :title="heatmapButtonTitle"
          class="control-button"
          :disabled="!map || !heatmapAvailable"
        >
          <i class="pi pi-globe"></i>
        </button>

        <div
          v-if="heatmapPopoverOpen"
          class="heatmap-popover"
          @click.stop
        >
          <div class="heatmap-popover-title">Heatmap</div>
          <button
            class="heatmap-popover-option"
            :class="{ active: heatmapEnabled && heatmapLayer === 'stays' }"
            @click="selectHeatmapLayer('stays')"
          >
            <i class="pi pi-home"></i>
            Stays
          </button>
          <button
            class="heatmap-popover-option"
            :class="{ active: heatmapEnabled && heatmapLayer === 'trips' }"
            @click="selectHeatmapLayer('trips')"
          >
            <i class="pi pi-directions"></i>
            Trips
          </button>
          <button
            v-if="heatmapEnabled"
            class="heatmap-popover-option heatmap-popover-off"
            @click="disableHeatmap"
          >
            <i class="pi pi-times"></i>
            Turn off
          </button>
        </div>
      </div>

      <button
        v-if="showImmichButton"
        @click="handleToggleImmich"
        :class="{ active: showImmich, 'immich-loading': immichLoading }"
        :title="showImmich ? 'Hide Photos' : 'Show Photos'"
        class="control-button"
        :disabled="!map || immichLoading"
      >
        <i class="pi pi-camera"></i>
        <span v-if="immichLoading" class="loading-indicator"></span>
        <!-- Debug info -->
        <div v-if="false" style="position:absolute;top:-20px;left:0;font-size:10px;background:red;color:white;padding:2px;">
          map:{{!!map}} loading:{{immichLoading}} configured:{{immichConfigured}} show:{{showImmich}}
        </div>
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  map: {
    type: Object,
    default: null
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
  showImmich: {
    type: Boolean,
    default: false
  },
  showHeatmap: {
    type: Boolean,
    default: false
  },
  heatmapEnabled: {
    type: Boolean,
    default: false
  },
  heatmapLayer: {
    type: String,
    default: 'stays'
  },
  heatmapAvailable: {
    type: Boolean,
    default: true
  },
  showZoomControls: {
    type: Boolean,
    default: true
  },
  immichConfigured: {
    type: Boolean,
    default: false
  },
  immichLoading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'toggle-favorites',
  'toggle-timeline',
  'toggle-path',
  'toggle-immich',
  'toggle-heatmap',
  'heatmap-layer-change',
  'zoom-to-data'
])

const handleToggleFavorites = () => {
  emit('toggle-favorites', !props.showFavorites)
}

const handleToggleTimeline = () => {
  emit('toggle-timeline', !props.showTimeline)
}

const handleTogglePath = () => {
  emit('toggle-path', !props.showPath)
}

const handleToggleImmich = () => {
  emit('toggle-immich', !props.showImmich)
}

const handleZoomToData = () => {
  emit('zoom-to-data')
}

const heatmapControlRef = ref(null)
const heatmapPopoverOpen = ref(false)

const toggleHeatmapPopover = () => {
  if (!props.heatmapAvailable || !props.map) return
  heatmapPopoverOpen.value = !heatmapPopoverOpen.value
}

const selectHeatmapLayer = (layer) => {
  emit('heatmap-layer-change', layer)
  emit('toggle-heatmap', true)
  heatmapPopoverOpen.value = false
}

const disableHeatmap = () => {
  emit('toggle-heatmap', false)
  heatmapPopoverOpen.value = false
}

const closeHeatmapPopover = () => {
  heatmapPopoverOpen.value = false
}

// Computed properties
const showImmichButton = computed(() => {
  return props.immichConfigured
})

const heatmapButtonTitle = computed(() => {
  if (!props.heatmapAvailable) return 'Select a date range to enable heatmap'
  if (props.heatmapEnabled) return 'Heatmap enabled'
  return 'Show heatmap'
})

const handleDocumentClick = (event) => {
  if (!heatmapPopoverOpen.value) return
  const target = event.target
  const root = heatmapControlRef.value
  if (root && target && !root.contains(target)) {
    closeHeatmapPopover()
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
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

.heatmap-control {
  position: relative;
}

.heatmap-popover {
  position: absolute;
  top: 0;
  right: calc(100% + 8px);
  min-width: 140px;
  background: var(--gp-surface-white, #fff);
  border: 1px solid var(--gp-border-light, rgba(0, 0, 0, 0.1));
  border-radius: var(--gp-radius-medium, 8px);
  box-shadow: var(--gp-shadow-medium, 0 8px 20px rgba(0, 0, 0, 0.12));
  padding: 8px;
  z-index: 1100;
}

.heatmap-popover-title {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--gp-text-secondary, #64748b);
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.heatmap-popover-option {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--gp-text-primary, #0f172a);
  font-size: 0.8125rem;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.heatmap-popover-option + .heatmap-popover-option {
  margin-top: 4px;
}

.heatmap-popover-option:hover {
  background: var(--gp-surface-light, #f8fafc);
}

.heatmap-popover-option.active {
  background: rgba(26, 86, 219, 0.12);
  border-color: rgba(26, 86, 219, 0.2);
  color: var(--gp-primary, #1a56db);
}

.heatmap-popover-off {
  color: var(--gp-text-secondary, #64748b);
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

.p-dark .heatmap-popover {
  background: var(--gp-surface-dark, #1e293b);
  border-color: var(--gp-border-dark, rgba(255, 255, 255, 0.1));
}

.p-dark .heatmap-popover-option {
  color: var(--gp-text-secondary, #cbd5e1);
}

.p-dark .heatmap-popover-option.active {
  color: var(--gp-primary-light, #3b82f6);
  background: rgba(59, 130, 246, 0.16);
  border-color: rgba(59, 130, 246, 0.35);
}

/* Immich button specific styles */
.control-button.immich-loading {
  position: relative;
  pointer-events: none;
}

.loading-indicator {
  position: absolute;
  top: 50%;
  right: 3px;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gp-primary, #1a56db);
  animation: immich-pulse 1.5s infinite;
}

.p-dark .loading-indicator {
  background: var(--gp-primary-light, #3b82f6);
}

@keyframes immich-pulse {
  0%, 100% { 
    opacity: 0.3; 
    transform: translateY(-50%) scale(0.8);
  }
  50% { 
    opacity: 1; 
    transform: translateY(-50%) scale(1);
  }
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .map-controls {
    flex-direction: row;
  }

  .control-group {
    flex-direction: row;
  }

  .heatmap-popover {
    right: 0;
    top: calc(100% + 8px);
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
