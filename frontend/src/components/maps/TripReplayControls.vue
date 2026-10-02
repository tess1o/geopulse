<template>
  <div class="trip-replay-controls-root">
    <div
      v-if="showBar"
      class="trip-replay-bar"
      :class="{ 'trip-replay-bar--compact': compact }"
      @mousedown.stop
      @touchstart.stop
      @click.stop
    >
      <!-- The highlighted trip's summary, so the map shows one card, not a pill stacked on the bar. -->
      <div v-if="summary" class="trip-replay-summary">
        <span class="trip-replay-summary-icon"><i :class="summary.iconClass"></i></span>
        <span class="trip-replay-summary-title">{{ summary.title }}</span>
        <span class="trip-replay-summary-meta">{{ summary.metaItems.join(' · ') }}</span>
      </div>

      <div class="trip-replay-bar-main">
        <button
          type="button"
          class="trip-replay-btn"
          :title="isPlaying ? t('maps.tripReplay.pause') : t('maps.tripReplay.play')"
          @click="$emit('toggle-playback')"
        >
          <i :class="isPlaying ? 'pi pi-pause' : 'pi pi-play'"></i>
        </button>

        <button
          type="button"
          class="trip-replay-btn"
          :title="t('maps.tripReplay.stop')"
          @click="$emit('stop')"
        >
          <i class="pi pi-stop"></i>
        </button>

        <span class="trip-replay-time">{{ elapsedLabel }}</span>

        <input
          class="trip-replay-slider"
          type="range"
          min="0"
          max="1000"
          step="1"
          :value="sliderValue"
          @input="$emit('slider-input', $event)"
        />

        <span class="trip-replay-time">{{ durationLabel }}</span>
      </div>

      <div class="trip-replay-bar-options">
        <div class="trip-replay-speeds">
          <button
            v-for="speed in speedPresets"
            :key="speed"
            type="button"
            class="trip-replay-speed-btn"
            :class="{ active: speedMultiplier === speed }"
            :title="t('maps.tripReplay.setSpeed', { speed })"
            @click="$emit('set-speed', speed)"
          >
            {{ speed }}x
          </button>
        </div>

        <button
          type="button"
          class="trip-replay-toggle-btn"
          :class="{ active: followCamera }"
          :title="t('maps.tripReplay.followCamera')"
          @click="$emit('toggle-follow-camera')"
        >
          <i class="pi pi-compass"></i>
          {{ t('maps.tripReplay.follow') }}
        </button>

        <button
          v-if="show3dToggle"
          type="button"
          class="trip-replay-toggle-btn"
          :class="{ active: enable3d }"
          :title="t('maps.tripReplay.enable3d')"
          @click="$emit('toggle-3d')"
        >
          <i class="pi pi-box"></i>
          3D
        </button>
      </div>

      <button
        type="button"
        class="trip-replay-btn trip-replay-dismiss-btn"
        :title="t('maps.tripReplay.hideControls')"
        :aria-label="t('maps.tripReplay.hideControls')"
        @click="$emit('dismiss')"
      >
        <i class="pi pi-times"></i>
      </button>
    </div>

    <div
      v-if="showRestoreButton"
      class="trip-replay-restore"
      :class="{ 'trip-replay-restore--compact': compact }"
      @mousedown.stop
      @touchstart.stop
      @click.stop
    >
      <button
        type="button"
        class="trip-replay-restore-btn"
        :title="t('maps.tripReplay.showControls')"
        :aria-label="t('maps.tripReplay.showControls')"
        @click="$emit('restore')"
      >
        <i class="pi pi-play-circle"></i>
        {{ t('maps.tripReplay.replay') }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps({
  showBar: {
    type: Boolean,
    default: false
  },
  showRestoreButton: {
    type: Boolean,
    default: false
  },
  isPlaying: {
    type: Boolean,
    default: false
  },
  elapsedLabel: {
    type: String,
    default: '00:00'
  },
  durationLabel: {
    type: String,
    default: '00:00'
  },
  sliderValue: {
    type: Number,
    default: 0
  },
  speedPresets: {
    type: Array,
    default: () => []
  },
  speedMultiplier: {
    type: Number,
    default: 1
  },
  followCamera: {
    type: Boolean,
    default: true
  },
  enable3d: {
    type: Boolean,
    default: false
  },
  show3dToggle: {
    type: Boolean,
    default: false
  },
  compact: {
    type: Boolean,
    default: false
  },
  // { iconClass, title, metaItems: string[] } of the highlighted trip, shown as the bar's header.
  summary: {
    type: Object,
    default: null
  }
})

defineEmits([
  'toggle-playback',
  'stop',
  'slider-input',
  'set-speed',
  'toggle-follow-camera',
  'toggle-3d',
  'dismiss',
  'restore'
])
</script>

<style scoped>
.trip-replay-controls-root {
  display: contents;
  --trip-replay-bottom-offset: 2.35rem;
  --trip-replay-restore-right: 0.75rem;
  --trip-replay-width: min(900px, calc(100% - 1.25rem));
  --trip-replay-gap: 0.5rem;
  --trip-replay-padding: 0.6rem 2.95rem 0.7rem 0.7rem;
  --trip-replay-radius: 0.75rem;
  --trip-replay-main-flex: 1 1 440px;
  --trip-replay-main-min-width: 260px;
  --trip-replay-main-gap: 0.5rem;
  --trip-replay-options-gap: 0.45rem;
  --trip-replay-speeds-gap: 0.3rem;
  --trip-replay-btn-size: 2rem;
  --trip-replay-speed-min-width: 2.5rem;
  --trip-replay-speed-height: 2rem;
  --trip-replay-speed-padding: 0 0.45rem;
  --trip-replay-speed-font-size: 0.8rem;
  --trip-replay-toggle-height: 2rem;
  --trip-replay-toggle-padding: 0 0.55rem;
  --trip-replay-toggle-font-size: 0.78rem;
  --trip-replay-slider-min-width: 140px;
  --trip-replay-time-min-width: 2.9rem;
  --trip-replay-time-font-size: 0.8rem;
  --trip-replay-dismiss-inset: 0.45rem;
  --trip-replay-restore-height: 2.2rem;
  --trip-replay-restore-padding: 0 0.75rem;
  --trip-replay-restore-font-size: 0.78rem;
}

.trip-replay-bar {
  position: absolute;
  left: 50%;
  bottom: calc(var(--trip-replay-bottom-offset) + env(safe-area-inset-bottom));
  transform: translateX(-50%);
  width: var(--trip-replay-width);
  z-index: 920;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--trip-replay-gap);
  padding: var(--trip-replay-padding);
  border-radius: var(--trip-replay-radius);
  border: 1px solid var(--gp-border-medium);
  background: color-mix(in srgb, var(--gp-surface-card) 95%, transparent);
  box-shadow: var(--gp-shadow-large);
  backdrop-filter: blur(2px);
}

.trip-replay-summary {
  flex: 1 1 100%;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  min-height: var(--trip-replay-btn-size);
  color: var(--gp-text-primary);
}

.trip-replay-summary-icon {
  flex: 0 0 1.6rem;
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-primary);
  color: #ffffff;
  font-size: 0.78rem;
}

.trip-replay-summary-title {
  flex: 0 0 auto;
  font-size: 0.85rem;
  font-weight: 700;
  white-space: nowrap;
}

.trip-replay-summary-meta {
  min-width: 0;
  overflow: hidden;
  color: var(--gp-text-secondary);
  font-size: 0.78rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-replay-bar-main {
  flex: var(--trip-replay-main-flex);
  display: flex;
  align-items: center;
  gap: var(--trip-replay-main-gap);
  min-width: var(--trip-replay-main-min-width);
}

.trip-replay-bar-options {
  flex: 0 1 auto;
  display: flex;
  align-items: center;
  gap: var(--trip-replay-options-gap);
}

.trip-replay-speeds {
  display: flex;
  align-items: center;
  gap: var(--trip-replay-speeds-gap);
}

.trip-replay-btn,
.trip-replay-speed-btn,
.trip-replay-toggle-btn {
  border: 1px solid var(--gp-border-medium);
  background: var(--gp-surface-muted);
  color: var(--gp-text-primary);
  cursor: pointer;
  border-radius: 0.5rem;
  transition: background-color 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.trip-replay-btn {
  width: var(--trip-replay-btn-size);
  height: var(--trip-replay-btn-size);
  display: flex;
  align-items: center;
  justify-content: center;
}

.trip-replay-dismiss-btn {
  position: absolute;
  top: var(--trip-replay-dismiss-inset);
  right: var(--trip-replay-dismiss-inset);
  z-index: 2;
  flex-shrink: 0;
}

.trip-replay-btn:hover,
.trip-replay-speed-btn:hover,
.trip-replay-toggle-btn:hover {
  background: var(--gp-surface-emphasis);
}

.trip-replay-speed-btn {
  min-width: var(--trip-replay-speed-min-width);
  height: var(--trip-replay-speed-height);
  padding: var(--trip-replay-speed-padding);
  font-size: var(--trip-replay-speed-font-size);
  font-weight: 600;
}

.trip-replay-toggle-btn {
  height: var(--trip-replay-toggle-height);
  padding: var(--trip-replay-toggle-padding);
  font-size: var(--trip-replay-toggle-font-size);
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.trip-replay-slider {
  flex: 1;
  min-width: var(--trip-replay-slider-min-width);
  accent-color: var(--gp-primary);
}

.trip-replay-time {
  min-width: var(--trip-replay-time-min-width);
  font-size: var(--trip-replay-time-font-size);
  font-weight: 700;
  color: var(--gp-text-secondary);
  font-variant-numeric: tabular-nums;
}

.trip-replay-restore {
  position: absolute;
  right: calc(var(--trip-replay-restore-right) + env(safe-area-inset-right));
  bottom: calc(var(--trip-replay-bottom-offset) + env(safe-area-inset-bottom));
  z-index: 920;
}

.trip-replay-restore-btn {
  border: 1px solid var(--gp-border-medium);
  background: var(--gp-surface-muted);
  color: var(--gp-text-primary);
  cursor: pointer;
  border-radius: 999px;
  height: var(--trip-replay-restore-height);
  padding: var(--trip-replay-restore-padding);
  font-size: var(--trip-replay-restore-font-size);
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.trip-replay-bar--compact {
  --trip-replay-bottom-offset: 2.25rem;
  --trip-replay-width: min(720px, calc(100% - 2rem));
  --trip-replay-gap: 0.35rem;
  --trip-replay-padding: 0.45rem 2.35rem 0.5rem 0.5rem;
  --trip-replay-radius: 0.55rem;
  --trip-replay-main-flex: 1 1 360px;
  --trip-replay-main-min-width: 220px;
  --trip-replay-main-gap: 0.35rem;
  --trip-replay-options-gap: 0.3rem;
  --trip-replay-speeds-gap: 0.2rem;
  --trip-replay-btn-size: 1.65rem;
  --trip-replay-speed-min-width: 2.05rem;
  --trip-replay-speed-height: 1.65rem;
  --trip-replay-speed-padding: 0 0.32rem;
  --trip-replay-speed-font-size: 0.72rem;
  --trip-replay-toggle-height: 1.65rem;
  --trip-replay-toggle-padding: 0 0.42rem;
  --trip-replay-toggle-font-size: 0.72rem;
  --trip-replay-slider-min-width: 110px;
  --trip-replay-time-min-width: 2.55rem;
  --trip-replay-time-font-size: 0.72rem;
  --trip-replay-dismiss-inset: 0.4rem;
}

.trip-replay-restore--compact {
  --trip-replay-bottom-offset: 2.25rem;
  --trip-replay-restore-height: 1.8rem;
  --trip-replay-restore-padding: 0 0.55rem;
  --trip-replay-restore-font-size: 0.72rem;
}

.trip-replay-speed-btn.active,
.trip-replay-toggle-btn.active {
  border-color: var(--gp-primary-hover);
  background: var(--gp-primary);
  color: var(--gp-primary-contrast);
}

@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .trip-replay-bar {
    --trip-replay-bottom-offset: calc(var(--timeline-mobile-sheet-height, 44px) + 2.25rem);
    --trip-replay-width: calc(100% - 0.75rem - env(safe-area-inset-left) - env(safe-area-inset-right));
    --trip-replay-padding: 0.5rem 2.7rem 0.5rem 0.55rem;
    --trip-replay-gap: 0.4rem;
    --trip-replay-main-flex: 1 1 100%;
    --trip-replay-main-min-width: 0;
    --trip-replay-main-gap: 0.35rem;
    --trip-replay-speed-min-width: 0;
    --trip-replay-speed-height: 2.05rem;
    --trip-replay-speed-padding: 0;
    --trip-replay-speed-font-size: 0.74rem;
    --trip-replay-toggle-height: 2.05rem;
    --trip-replay-toggle-padding: 0 0.5rem;
    --trip-replay-toggle-font-size: 0.72rem;
    --trip-replay-dismiss-inset: 0.34rem;
  }

  .trip-replay-bar-options {
    width: 100%;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto auto;
    align-items: stretch;
    gap: 0.35rem;
  }

  .trip-replay-speeds {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(2.2rem, 1fr));
    gap: 0.25rem;
  }

  .trip-replay-speed-btn {
    width: 100%;
  }

  .trip-replay-toggle-btn {
    min-width: 4.3rem;
    justify-content: center;
    white-space: nowrap;
  }

  .trip-replay-restore {
    --trip-replay-restore-right: 0.55rem;
    --trip-replay-bottom-offset: calc(var(--timeline-mobile-sheet-height, 44px) + 2.25rem);
  }

  .trip-replay-bar--compact {
    --trip-replay-bottom-offset: 2.35rem;
  }

  .trip-replay-restore--compact {
    --trip-replay-bottom-offset: 2.35rem;
  }

  .trip-replay-restore-btn {
    --trip-replay-restore-height: 2.05rem;
    --trip-replay-restore-padding: 0 0.62rem;
    --trip-replay-restore-font-size: 0.72rem;
  }
}
</style>
