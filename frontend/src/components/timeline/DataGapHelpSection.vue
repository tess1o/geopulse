<template>
  <div class="gap-help-section">
    <button
      class="help-toggle"
      @click.stop="toggleHelp"
      :aria-expanded="showHelp"
      :aria-label="t('timeline.dataGapHelp.toggleAriaLabel')"
    >
      {{ showHelp ? '▲' : '▼' }} {{ helpToggleText }}
    </button>

    <Transition name="expand">
      <div v-if="showHelp" class="help-content">
        <p class="help-text">
          <span class="help-icon">ℹ️</span>
          {{ t('timeline.dataGapHelp.explanation') }}
        </p>

        <div v-if="shouldShowRecommendations" class="recommendations">
          <p class="recommendation-header">
            <span class="help-icon">💡</span>
            {{ t('timeline.dataGapHelp.recommendationsHeader', { duration: formatDuration(durationSeconds) }) }}
          </p>
          <ul class="recommendation-list">
            <li v-for="tip in gapRecommendations" :key="tip">
              {{ tip }}
            </li>
          </ul>
        </div>

        <Button
          :label="t('timeline.dataGapHelp.openSettings')"
          icon="pi pi-cog"
          size="small"
          text
          @click.stop="openSettings"
          class="settings-button"
        />
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { formatDuration } from '@/utils/calculationsHelpers'
import Button from 'primevue/button'

const { t } = useI18n()

const props = defineProps({
  durationSeconds: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['navigate-to-settings'])
const router = useRouter()
const showHelp = ref(false)

const helpToggleText = computed(() => {
  // Mobile-friendly shorter text
  return window.innerWidth < 768
    ? t('timeline.dataGapHelp.toggleShort')
    : t('timeline.dataGapHelp.toggleLong')
})

const toggleHelp = () => {
  showHelp.value = !showHelp.value
}

const openSettings = () => {
  router.push('/app/timeline/preferences?tab=gpsgaps')
  emit('navigate-to-settings')
}

// Smart recommendations based on gap duration
const shouldShowRecommendations = computed(() => {
  const durationMinutes = props.durationSeconds / 60
  // Show tips for gaps between 30 minutes and 3 hours
  return durationMinutes >= 30 && durationMinutes <= 180
})

const gapRecommendations = computed(() => {
  const durationMinutes = props.durationSeconds / 60
  const tips = []

  if (durationMinutes >= 30 && durationMinutes < 180) {
    tips.push(t('timeline.dataGapHelp.tips.lowerThreshold'))
  }

  if (durationMinutes >= 45 && durationMinutes < 180) {
    tips.push(t('timeline.dataGapHelp.tips.enableStayInference'))
  }

  if (durationMinutes >= 180) {
    tips.push(t('timeline.dataGapHelp.tips.enableTripInference'))
  }

  return tips
})
</script>

<style scoped>
.gap-help-section {
  margin-top: var(--gp-spacing-sm);
  border-top: 1px solid var(--gp-border-medium);
  padding-top: var(--gp-spacing-sm);
}

.help-toggle {
  background: none;
  border: none;
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
  cursor: pointer;
  padding: var(--gp-spacing-xs) 0;
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  width: 100%;
  text-align: left;
  transition: color 0.2s;
}

.help-toggle:hover {
  color: var(--gp-primary-text);
}

.help-toggle:focus {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.help-content {
  margin-top: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
  background-color: var(--gp-surface-muted);
  border-radius: var(--gp-radius-small);
  font-size: 0.875rem;
  line-height: 1.5;
}

.help-text {
  margin: 0 0 var(--gp-spacing-sm) 0;
  color: var(--gp-text-secondary);
  display: flex;
  gap: var(--gp-spacing-xs);
  align-items: flex-start;
}

.help-icon {
  flex-shrink: 0;
}

.recommendations {
  margin-top: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
  background-color: var(--gp-primary-soft);
  border-radius: var(--gp-radius-small);
  border-left: 3px solid var(--gp-primary);
}

.recommendation-header {
  margin: 0 0 var(--gp-spacing-xs) 0;
  font-weight: 600;
  color: var(--gp-text-primary);
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.recommendation-list {
  margin: 0;
  padding-left: var(--gp-spacing-md);
  color: var(--gp-text-primary);
}

.recommendation-list li {
  margin: var(--gp-spacing-xs) 0;
}

.settings-button {
  margin-top: var(--gp-spacing-sm);
  width: 100%;
}

/* Expand/collapse animation */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease;
  max-height: 500px;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
}

/* Mobile optimizations */
@media (max-width: 768px) {
  .help-toggle {
    font-size: 0.8rem;
    padding: var(--gp-spacing-xs) 0;
  }

  .help-content {
    padding: var(--gp-spacing-xs);
    font-size: 0.8rem;
  }

  .help-text {
    gap: 6px;
  }

  .recommendations {
    padding: var(--gp-spacing-xs);
  }

  .recommendation-header {
    font-size: 0.8rem;
  }

  .recommendation-list {
    padding-left: var(--gp-spacing-sm);
    font-size: 0.75rem;
  }

  .settings-button {
    font-size: 0.8rem;
  }
}
</style>
