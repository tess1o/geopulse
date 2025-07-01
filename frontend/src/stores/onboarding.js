import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useOnboardingStore = defineStore('onboarding', () => {
  // State
  const isFirstLogin = ref(false)
  const hasSeenTour = ref(false)
  const tourStep = ref(0)
  const isTourActive = ref(false)

  // Tour steps configuration
  const tourSteps = ref([
    {
      id: 'location-sources-intro',
      title: 'Welcome to GeoPulse!',
      content: 'First, let\'s set up your location sources so we can start tracking your journey.',
      target: '.location-sources-header',
      placement: 'bottom'
    },
    {
      id: 'add-source-button',
      title: 'Add Your First Location Source',
      content: 'Click here to add OwnTracks or Overland as your location source.',
      target: '[data-tour="add-source-btn"]',
      placement: 'left'
    },
    {
      id: 'setup-instructions',
      title: 'Follow Setup Instructions',
      content: 'Once you add a source, you\'ll see detailed setup instructions with your unique endpoint and token.',
      target: '.quick-setup-guide',
      placement: 'top'
    },
    {
      id: 'timeline-navigation',
      title: 'View Your Timeline',
      content: 'After setting up location sources, visit your Timeline to see your location history.',
      target: '[data-tour="timeline-tab"]',
      placement: 'bottom'
    }
  ])

  // Actions
  const initializeOnboarding = (user) => {
    // Check if this is a first-time user (could be based on registration date, flags, etc.)
    const now = new Date()
    const userCreated = new Date(user.createdAt || user.created_at)
    const timeDiff = now - userCreated
    const oneHour = 60 * 60 * 1000 // 1 hour in milliseconds
    
    // Consider user "new" if account was created within the last hour
    isFirstLogin.value = timeDiff < oneHour
    
    // Check localStorage for tour completion
    const tourCompleted = localStorage.getItem('geopulse-tour-completed')
    hasSeenTour.value = tourCompleted === 'true'
  }

  const startTour = () => {
    isTourActive.value = true
    tourStep.value = 0
  }

  const nextTourStep = () => {
    if (tourStep.value < tourSteps.value.length - 1) {
      tourStep.value++
    } else {
      completeTour()
    }
  }

  const previousTourStep = () => {
    if (tourStep.value > 0) {
      tourStep.value--
    }
  }

  const completeTour = () => {
    isTourActive.value = false
    hasSeenTour.value = true
    tourStep.value = 0
    
    // Persist tour completion
    localStorage.setItem('geopulse-tour-completed', 'true')
  }

  const skipTour = () => {
    completeTour()
  }

  const restartTour = () => {
    hasSeenTour.value = false
    localStorage.removeItem('geopulse-tour-completed')
    startTour()
  }

  const shouldShowTour = () => {
    return isFirstLogin.value && !hasSeenTour.value
  }

  // Getters
  const currentStep = () => {
    return tourSteps.value[tourStep.value] || null
  }

  const isLastStep = () => {
    return tourStep.value === tourSteps.value.length - 1
  }

  const isFirstStep = () => {
    return tourStep.value === 0
  }

  const getTourProgress = () => {
    return {
      current: tourStep.value + 1,
      total: tourSteps.value.length,
      percentage: Math.round(((tourStep.value + 1) / tourSteps.value.length) * 100)
    }
  }

  return {
    // State
    isFirstLogin,
    hasSeenTour,
    tourStep,
    isTourActive,
    tourSteps,
    
    // Actions
    initializeOnboarding,
    startTour,
    nextTourStep,
    previousTourStep,
    completeTour,
    skipTour,
    restartTour,
    shouldShowTour,
    
    // Getters
    currentStep,
    isLastStep,
    isFirstStep,
    getTourProgress
  }
})