<template>
  <div>
    <!-- Welcome Message -->
    <div 
      v-if="showWelcomeMessage"
      class="welcome-message"
    >
      <h4 class="welcome-title">{{ t('ui.onboardingTour.welcomeTitle') }}</h4>
      <p class="welcome-description">{{ t('ui.onboardingTour.welcomeDescription') }}</p>
      <div class="welcome-actions">
        <button
          @click="startTour"
          class="btn btn-primary"
        >
          {{ t('ui.onboardingTour.startTour') }}
        </button>
        <button
          @click="dismissWelcomeMessage"
          class="btn btn-secondary"
        >
          {{ t('ui.onboardingTour.exploreOnMyOwn') }}
        </button>
      </div>
    </div>

    <!-- Tour Steps -->
    <div
      v-if="showTourStep"
      :style="tourStepStyle"
      class="tour-step-container"
    >
      <div class="tour-step-card">
        <!-- Close button -->
        <button
          @click="finishTour"
          class="tour-close-btn"
          :title="t('ui.onboardingTour.closeTour')"
        >
          ×
        </button>

        <h4 class="tour-step-title">{{ currentStep.title }}</h4>
        <p class="tour-step-description">{{ currentStep.description }}</p>

        <!-- Progress indicator -->
        <div class="tour-progress">
          <div
            v-for="(step, index) in tourSteps"
            :key="index"
            class="tour-progress-bar"
            :class="{ 'tour-progress-active': index <= currentStepIndex }"
          ></div>
        </div>

        <div class="tour-step-footer">
          <span class="tour-step-counter">{{ t('ui.onboardingTour.stepCounter', { current: currentStepIndex + 1, total: tourSteps.length }) }}</span>
          <div class="tour-step-actions">
            <button
              v-if="currentStepIndex > 0"
              @click="previousStep"
              class="btn btn-outline"
            >
              {{ t('ui.onboardingTour.previous') }}
            </button>
            <button
              @click="nextStep"
              class="btn btn-primary"
            >
              {{ currentStepIndex === tourSteps.length - 1 ? t('ui.onboardingTour.getStarted') : t('ui.onboardingTour.next') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useOnboardingStore } from '@/stores/onboarding'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()

// Stores
const onboardingStore = useOnboardingStore()
const authStore = useAuthStore()

// State
const showWelcomeMessage = ref(false)
const showTourStep = ref(false)
const currentStepIndex = ref(0)

// Tour steps
const tourSteps = computed(() => [
  {
    title: t('ui.onboardingTour.steps.welcome.title'),
    description: t('ui.onboardingTour.steps.welcome.description'),
    target: '.location-sources-header'
  },
  {
    title: t('ui.onboardingTour.steps.addSource.title'),
    description: t('ui.onboardingTour.steps.addSource.description'),
    target: '[data-tour="add-source-btn"]'
  },
  {
    title: t('ui.onboardingTour.steps.followInstructions.title'),
    description: t('ui.onboardingTour.steps.followInstructions.description'),
    target: '.quick-setup-guide'
  },
  {
    title: t('ui.onboardingTour.steps.allSet.title'),
    description: t('ui.onboardingTour.steps.allSet.description'),
    target: '.location-sources-header'
  }
])

// Computed
const currentStep = computed(() => tourSteps.value[currentStepIndex.value])

const tourStepStyle = computed(() => {
  const target = document.querySelector(currentStep.value.target)
  if (target) {
    const rect = target.getBoundingClientRect()
    return {
      position: 'fixed',
      top: `${rect.bottom + 10}px`,
      left: `${rect.left}px`,
      zIndex: '999999'
    }
  }
  return {
    position: 'fixed',
    top: '100px',
    left: '100px',
    zIndex: '999999'
  }
})

// Methods
const startTour = () => {
  showWelcomeMessage.value = false
  showTourStep.value = true
  currentStepIndex.value = 0
  onboardingStore.startTour()
}

const nextStep = () => {
  if (currentStepIndex.value < tourSteps.value.length - 1) {
    currentStepIndex.value++
  } else {
    finishTour()
  }
}

const previousStep = () => {
  if (currentStepIndex.value > 0) {
    currentStepIndex.value--
  }
}

const finishTour = () => {
  showTourStep.value = false
  onboardingStore.completeTour()
}

const dismissWelcomeMessage = () => {
  showWelcomeMessage.value = false
  onboardingStore.completeTour()
}

const initializeOnboarding = () => {
  if (authStore.user) {
    onboardingStore.initializeOnboarding(authStore.user)
    
    // Show welcome message for new users who haven't seen the tour
    if (onboardingStore.shouldShowTour()) {
      showWelcomeMessage.value = true
    }
  }
}

// Lifecycle
onMounted(() => {
  nextTick(() => {
    initializeOnboarding()
  })
})

// Expose methods
defineExpose({
  startTour,
  initializeOnboarding
})
</script>

<style scoped>
/* Welcome Message */
.welcome-message {
  background: var(--p-primary-600);
  color: var(--gp-primary-contrast);
  padding: 1.25rem;
  margin: 1.25rem 0;
  border-radius: var(--p-border-radius-md);
  border: 2px solid var(--p-primary-700);
}

.welcome-title {
  margin: 0 0 0.625rem 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: inherit;
}

.welcome-description {
  margin: 0 0 1rem 0;
  font-size: 0.9rem;
  line-height: 1.4;
  color: inherit;
  opacity: 0.95;
}

.welcome-actions {
  display: flex;
  gap: 0.75rem;
}

/* Tour Step Container */
.tour-step-container {
  position: fixed;
  z-index: 999999;
}

.tour-step-card {
  background: var(--gp-surface-card);
  border: 2px solid var(--gp-primary-text);
  border-radius: var(--p-border-radius-md);
  box-shadow: var(--gp-shadow-large);
  max-width: 350px;
  padding: 1.25rem;
  position: relative;
}

.tour-close-btn {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  background: transparent;
  border: none;
  color: var(--gp-text-secondary);
  font-size: 1.125rem;
  cursor: pointer;
  width: 1.5rem;
  height: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--p-border-radius-sm);
  transition: all 0.2s ease;
}

.tour-close-btn:hover {
  background: var(--gp-surface-hover);
  color: var(--gp-text-primary);
}

.tour-step-title {
  margin: 0 0 0.625rem 0;
  padding-right: 1.25rem;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.tour-step-description {
  margin: 0 0 1rem 0;
  color: var(--gp-text-secondary);
  line-height: 1.5;
  font-size: 0.9rem;
}

/* Progress Indicator */
.tour-progress {
  display: flex;
  gap: 0.25rem;
  margin-bottom: 1rem;
}

.tour-progress-bar {
  width: 1.875rem;
  height: 0.25rem;
  border-radius: var(--p-border-radius-xs);
  background: var(--gp-border);
  transition: background-color 0.3s ease;
}

.tour-progress-active {
  background: var(--gp-primary);
}

/* Tour Footer */
.tour-step-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tour-step-counter {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.tour-step-actions {
  display: flex;
  gap: 0.5rem;
}

/* Button Styles */
.btn {
  border: none;
  padding: 0.5rem 1rem;
  border-radius: var(--p-border-radius-sm);
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
  transition: all 0.2s ease;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn-primary {
  background: var(--p-primary-600);
  color: var(--gp-primary-contrast);
  border: 2px solid var(--gp-primary-contrast);
}

.btn-primary:hover {
  background: var(--p-primary-700);
  transform: translateY(-1px);
}

.btn-secondary {
  background: transparent;
  color: var(--gp-primary-contrast);
  border: 1px solid var(--gp-primary-contrast);
}

.btn-secondary:hover {
  background: rgba(255, 255, 255, 0.1);
}

.btn-outline {
  background: transparent;
  color: var(--gp-text-secondary);
  border: 1px solid var(--gp-border-medium);
}

.btn-outline:hover {
  background: var(--gp-surface-hover);
  color: var(--gp-text-primary);
  border-color: var(--p-surface-400);
}

/* Responsive Design */
@media (max-width: 768px) {
  .welcome-actions {
    flex-direction: column;
    align-items: stretch;
  }
  
  .tour-step-card {
    max-width: 300px;
    padding: 1rem;
  }
  
  .tour-step-footer {
    flex-direction: column;
    gap: 0.75rem;
    align-items: stretch;
  }
  
  .tour-step-actions {
    justify-content: center;
  }
}
</style>