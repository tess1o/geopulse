<template>
  <AppLayout variant="default">
    <PageContainer
      title="Journey Insights" 
      subtitle="Discover patterns and achievements from your location data"
      :loading="isLoading"
    >
    <!-- Loading State -->
    <template v-if="isLoading">
      <div class="insights-loading">
        <ProgressSpinner size="large" />
        <p>Loading your journey insights...</p>
      </div>
    </template>

    <template v-else>
      <div class="insights-content-wrapper">
      <!-- Geographic Section -->
      <div class="insights-section">
        <h2 class="insights-section-title">
          <i class="pi pi-globe"></i>
          Geographic Adventures
        </h2>
        <div class="geographic-grid">
          <!-- Countries Card -->
          <div class="geographic-card">
            <div class="geographic-header">
              <span class="geographic-count">{{ geographic.countries?.length || 0 }}</span>
              <span class="geographic-label">Countries Explored</span>
            </div>
            <div class="geographic-list" v-if="geographic.countries?.length > 0">
              <div 
                v-for="country in displayedCountries" 
                :key="country.name"
                class="geographic-item"
              >
                <img 
                  v-if="country.flagUrl" 
                  :src="country.flagUrl" 
                  :alt="`${country.name} flag`"
                  class="country-flag-img"
                  @error="handleFlagError(country)"
                />
                <span v-else class="country-flag-placeholder">🏳️</span>
                <span class="country-name">{{ country.name }}</span>
              </div>
            </div>
            <div v-else class="no-data">Start exploring to discover countries!</div>
          </div>

          <!-- Cities Card -->
          <div class="geographic-card">
            <div class="geographic-header">
              <span class="geographic-count">{{ geographic.cities?.length || 0 }}</span>
              <span class="geographic-label">Cities Visited</span>
            </div>
            <div class="geographic-list" v-if="geographic.cities?.length > 0">
              <div 
                v-for="city in displayedCities" 
                :key="city.name"
                class="geographic-item city-item"
              >
                <i class="pi pi-map-marker city-icon"></i>
                <span class="city-name">{{ city.name }}</span>
                <span class="city-visits">{{ city.visits }} visits</span>
              </div>
            </div>
            <div v-else class="no-data">Start tracking to discover cities!</div>
          </div>

        </div>
      </div>

      <!-- Travel Records Section -->
      <div class="insights-section">
        <h2 class="insights-section-title">
          <i class="pi pi-chart-line"></i>
          Your Travel Story
        </h2>
        <div class="travel-records-grid">
          <!-- Total Distance -->
          <div class="insight-stat-large travel-card">
            <div class="travel-icon">🛣️</div>
            <div class="stat-number">{{ formatDistance(distanceTraveled?.total || 0) }}</div>
            <div class="stat-label">Total Distance Traveled</div>
            <div class="stat-detail">{{ getTotalDistancePhrase(distanceTraveled?.total || 0) }}</div>
          </div>
          
          <!-- Distance by Car -->
          <div class="insight-stat-large travel-card">
            <div class="travel-icon">🚗</div>
            <div class="stat-number">{{ formatDistance(distanceTraveled?.byCar || 0) }}</div>
            <div class="stat-label">Distance by Car</div>
            <div class="stat-detail">
              {{ getCarPercentage(distanceTraveled) }} - {{ getCarPhrase(distanceTraveled?.byCar || 0) }}
            </div>
          </div>
          
          <!-- Distance Walking -->
          <div class="insight-stat-large travel-card">
            <div class="travel-icon">🚶</div>
            <div class="stat-number">{{ formatDistance(distanceTraveled?.byWalk || 0) }}</div>
            <div class="stat-label">Distance Walking</div>
            <div class="stat-detail">
              {{ getWalkPercentage(distanceTraveled) }} - {{ getWalkPhrase(distanceTraveled?.byWalk || 0) }}
            </div>
          </div>
        </div>
      </div>

      <!-- Activity Patterns Section -->
      <div class="insights-section">
        <h2 class="insights-section-title">
          <i class="pi pi-calendar"></i>
          Activity Patterns
        </h2>
        <div class="insights-grid-simple">
          <div class="insight-stat-pattern enhanced">
            <div class="pattern-icon">📅</div>
            <div class="pattern-content">
              <div class="pattern-value">{{ timePatterns.mostActiveMonth || 'N/A' }}</div>
              <div class="pattern-label">Most Active Month</div>
              <div class="pattern-insight" v-if="timePatterns.monthlyComparison">
                ↳ {{ timePatterns.monthlyComparison }}
              </div>
            </div>
          </div>
          
          <div class="insight-stat-pattern enhanced">
            <div class="pattern-icon">📍</div>
            <div class="pattern-content">
              <div class="pattern-value">{{ timePatterns.busiestDayOfWeek || 'N/A' }}</div>
              <div class="pattern-label">Busiest Day of Week</div>
              <div class="pattern-insight" v-if="timePatterns.dayInsight">
                ↳ {{ timePatterns.dayInsight }}
              </div>
            </div>
          </div>
          
          <div class="insight-stat-pattern enhanced">
            <div class="pattern-icon">🕐</div>
            <div class="pattern-content">
              <div class="pattern-value">{{ localMostActiveTime }}</div>
              <div class="pattern-label">Most Active Time of Day</div>
              <div class="pattern-insight" v-if="timePatterns.timeInsight">
                ↳ {{ timePatterns.timeInsight }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Milestones Section -->
      <div class="insights-section">
        <h2 class="insights-section-title">
          <i class="pi pi-trophy"></i>
          Your Journey Milestones
        </h2>
        <div class="milestones-grid">
          <!-- Achievement Badges -->
          <div 
            v-for="badge in achievementBadges" 
            :key="badge.id"
            class="milestone-card achievement-badge"
            :class="{ 'earned': badge.earned }"
          >
            <div class="badge-icon">{{ badge.icon }}</div>
            <div class="badge-title">{{ badge.title }}</div>
            <div class="badge-description">{{ badge.description }}</div>
            <div class="badge-progress" v-if="!badge.earned">
              <div class="progress-bar">
                <div 
                  class="progress-fill" 
                  :style="{ width: `${badge.progress}%` }"
                ></div>
              </div>
              <span class="progress-text">{{ badge.progressText }}</span>
            </div>
            <div class="badge-earned" v-else>
              <span class="earned-text">Earned!</span>
              <span class="earned-date">{{ badge.earnedDate }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div v-if="!hasAnyData" class="empty-insights">
        <i class="pi pi-compass empty-icon"></i>
        <h3 class="empty-title">No Journey Data Available</h3>
        <p class="empty-message">
          Start tracking your location to unlock insights about your travel patterns and achievements.
        </p>
      </div>
      </div>
    </template>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from "primevue/usetoast"
import ProgressSpinner from 'primevue/progressspinner'
import { useErrorHandler } from '@/composables/useErrorHandler'

// Layout Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'

// Store
import { useJourneyInsightsStore } from '@/stores/journeyInsights'

const toast = useToast()
const journeyInsightsStore = useJourneyInsightsStore()
const { handleErrorWithRetry } = useErrorHandler()

// Store refs
const { insights: journeyInsights, loading: isLoading } = storeToRefs(journeyInsightsStore)

// Local state for UI interactions
const countryFlags = ref(new Map()) // Cache for country flags

// Computed properties
const hasAnyData = computed(() => {
  return journeyInsightsStore.hasData
})

const streakStatusDisplay = computed(() => {
  return journeyInsightsStore.getStreakStatusDisplay
})

// Safe access to store data with defaults
const geographic = computed(() => journeyInsightsStore.geographic)
const timePatterns = computed(() => journeyInsightsStore.timePatterns)
const achievements = computed(() => journeyInsightsStore.achievements)
const distanceTraveled = computed(() => journeyInsightsStore.distance)

// Convert UTC time to user's local timezone
const localMostActiveTime = computed(() => {
  const utcTime = timePatterns.value?.mostActiveTime
  if (!utcTime) return 'N/A'
  
  // Parse the UTC time string (e.g., "3:30 PM")
  try {
    // Convert 12-hour format to 24-hour format for parsing
    const time24 = utcTime.replace(/(\d{1,2}):(\d{2})\s*(AM|PM)/i, (match, hours, minutes, period) => {
      let hour = parseInt(hours, 10)
      if (period.toUpperCase() === 'PM' && hour !== 12) hour += 12
      if (period.toUpperCase() === 'AM' && hour === 12) hour = 0
      return `${hour.toString().padStart(2, '0')}:${minutes}`
    })
    
    // Create a date object with today's date and the UTC time
    const today = new Date()
    const [hours, minutes] = time24.split(':').map(Number)
    const utcDate = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate(), hours, minutes))
    
    // Convert to local time and format back to 12-hour format
    return utcDate.toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    })
  } catch (error) {
    console.error('Error converting time to local timezone:', error)
    return utcTime // fallback to original UTC time
  }
})

// Geographic display logic
const displayedCountries = computed(() => {
  const countries = geographic.value?.countries || []

  // Add flag URLs to countries
  return countries.map(country => ({
    ...country,
    flagUrl: countryFlags.value.get(country.name)
  }))
})

const displayedCities = computed(() => {
  return  geographic.value?.cities || []
})

// Achievement badges logic
const achievementBadges = computed(() => {
  const badges = achievements.value?.badges || []
  return badges.map(badge => ({
    ...badge,
    progress: Math.min(100, badge.progress || 0),
    progressText: badge.progressText || `${badge.current || 0}/${badge.target || 0}`
  }))
})

// Methods
const fetchJourneyInsights = async () => {
  try {
    await journeyInsightsStore.fetchJourneyInsights()
    // Fetch flags for countries after getting data
    await fetchCountryFlags()

  } catch (error) {
    console.error('Error fetching journey insights:', error)
    // Use improved error handling with retry capability
    handleErrorWithRetry(error, fetchJourneyInsights)
  }
}

// Countries cache management
const COUNTRIES_CACHE_KEY = 'geopulse_countries_cache'
const CACHE_EXPIRY_KEY = 'geopulse_countries_cache_expiry'
const CACHE_DURATION = 7 * 24 * 60 * 60 * 1000 // 7 days

let countriesCache = []

const loadCountries = async () => {
  try {
    // Check if we have valid cached data
    const cachedData = localStorage.getItem(COUNTRIES_CACHE_KEY)
    const cacheExpiry = localStorage.getItem(CACHE_EXPIRY_KEY)
    
    if (cachedData && cacheExpiry && Date.now() < parseInt(cacheExpiry)) {
      countriesCache = JSON.parse(cachedData)
      console.log('Loaded countries from cache')
      return
    }
    
    // Fetch fresh data
    console.log('Fetching countries data from API...')
    const res = await fetch("https://restcountries.com/v3.1/all?fields=name,flags")
    countriesCache = await res.json()
    
    // Cache the data
    localStorage.setItem(COUNTRIES_CACHE_KEY, JSON.stringify(countriesCache))
    localStorage.setItem(CACHE_EXPIRY_KEY, (Date.now() + CACHE_DURATION).toString())
    
    console.log('Countries data cached successfully')
  } catch (error) {
    console.error('Failed to load countries data:', error)
    // Try to use stale cache if available
    const cachedData = localStorage.getItem(COUNTRIES_CACHE_KEY)
    if (cachedData) {
      countriesCache = JSON.parse(cachedData)
      console.log('Using stale cache due to fetch error')
    }
  }
}

const getFlagByLocalName = (localName) => {
  if (!countriesCache.length) {
    console.warn('Countries cache not loaded yet')
    return null
  }
  
  const country = countriesCache.find(c => {
    if (!c.name?.nativeName) return false;
    return Object.values(c.name.nativeName).some(n =>
        n.common.toLowerCase() === localName.toLowerCase() ||
        n.official.toLowerCase() === localName.toLowerCase()
    );
  })

  return country?.flags?.png || null
}

const fetchCountryFlags = async () => {
  // Ensure countries data is loaded first
  if (!countriesCache.length) {
    await loadCountries()
  }
  
  const countries = geographic.value?.countries || []
  
  // Get flags for countries that don't have them cached
  countries
    .filter(country => !countryFlags.value.has(country.name))
    .forEach(country => {
      const flagUrl = getFlagByLocalName(country.name)
      if (flagUrl) {
        countryFlags.value.set(country.name, flagUrl)
      }
    })
}

const handleFlagError = (country) => {
  // Remove failed flag from cache so it can be retried
  countryFlags.value.delete(country.name)
}

// Distance formatting and motivational phrases
const formatDistance = (distance) => {
  if (!distance || distance === 0) return '0 km'
  return `${distance.toLocaleString()} km`
}

const getCarPercentage = (distanceData) => {
  if (!distanceData?.total || distanceData.total === 0) return '(0%)'
  const percentage = Math.round((distanceData.byCar / distanceData.total) * 100)
  return `(${percentage}%)`
}

const getWalkPercentage = (distanceData) => {
  if (!distanceData?.total || distanceData.total === 0) return '(0%)'
  const percentage = Math.round((distanceData.byWalk / distanceData.total) * 100)
  return `(${percentage}%)`
}

// Motivational phrase collections
const totalDistancePhrases = [
  { min: 0, max: 50, phrases: [
    "Every journey begins with a single step!",
    "You're just getting started on your adventure!",
    "Great start to your exploration journey!"
  ]},
  { min: 50, max: 200, phrases: [
    "You're building some great travel momentum!",
    "Nice exploration of your local area!",
    "You're discovering your neighborhood!"
  ]},
  { min: 200, max: 1000, phrases: [
    "You're becoming a real explorer!",
    "That's some serious ground covered!",
    "You're seeing the world around you!"
  ]},
  { min: 1000, max: 5000, phrases: [
    "That's like going around the Earth! (well, a small part of it)",
    "You could have driven across several countries!",
    "Impressive distance coverage!"
  ]},
  { min: 5000, max: 50000, phrases: [
    "You could have crossed continents with that distance!",
    "That's some serious globe-trotting distance!",
    "You're a true travel enthusiast!"
  ]},
  { min: 50000, max: Infinity, phrases: [
    "You could have gone around the Earth multiple times!",
    "That's astronomical travel distance!",
    "You're practically a space traveler!"
  ]}
]

const carPhrases = [
  { min: 0, max: 50, phrases: [
    "Perfect for quick local trips!",
    "Great for nearby adventures!",
    "Local explorer mode activated!"
  ]},
  { min: 50, max: 500, phrases: [
    "You enjoy scenic drives!",
    "Road trip enthusiast in the making!",
    "You love the freedom of the road!"
  ]},
  { min: 500, max: 2000, phrases: [
    "You're a road trip enthusiast!",
    "The highway is your playground!",
    "You've mastered the art of driving!"
  ]},
  { min: 2000, max: Infinity, phrases: [
    "You're basically living on the road!",
    "Professional road warrior status!",
    "The car is your second home!"
  ]}
]

const walkPhrases = [
  { min: 0, max: 10, phrases: [
    "Every step counts - keep it up!",
    "Start small, dream big!",
    "Your walking journey begins!"
  ]},
  { min: 10, max: 50, phrases: [
    "Keep exploring on foot!",
    "You're building healthy habits!",
    "Walking warrior in training!"
  ]},
  { min: 50, max: 200, phrases: [
    "You're a walking enthusiast!",
    "Your feet are your best travel companions!",
    "Impressive pedestrian achievements!"
  ]},
  { min: 200, max: Infinity, phrases: [
    "You've practically walked across countries!",
    "Marathon-level walking achievements!",
    "You're a walking legend!"
  ]}
]

const getTotalDistancePhrase = (distance) => {
  const range = totalDistancePhrases.find(r => distance >= r.min && distance < r.max)
  if (!range) return "Keep exploring!"
  return range.phrases[Math.floor(Math.random() * range.phrases.length)]
}

const getCarPhrase = (distance) => {
  const range = carPhrases.find(r => distance >= r.min && distance < r.max)
  if (!range) return "Keep driving!"
  return range.phrases[Math.floor(Math.random() * range.phrases.length)]
}

const getWalkPhrase = (distance) => {
  const range = walkPhrases.find(r => distance >= r.min && distance < r.max)
  if (!range) return "Keep walking!"
  return range.phrases[Math.floor(Math.random() * range.phrases.length)]
}

// Watch for countries data changes to fetch flags
watch(() => geographic.value?.countries, (newCountries) => {
  if (newCountries?.length > 0) {
    fetchCountryFlags()
  }
}, { immediate: true })

// Lifecycle
onMounted(async () => {
  // Load countries cache in background
  loadCountries().catch(console.error)
  
  // Fetch journey insights
  await fetchJourneyInsights()
})
</script>

<style scoped>
/* Content Wrapper - Fixed width to prevent layout jumping */
.insights-content-wrapper {
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--gp-spacing-md);
  box-sizing: border-box;
}

/* Loading State */
.insights-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-xxl, 3rem);
  gap: var(--gp-spacing-lg);
}

.insights-loading p {
  color: var(--gp-text-secondary);
  font-size: 1rem;
  margin: 0;
}

/* Section Layout */
.insights-section {
  margin-bottom: var(--gp-spacing-xxl, 3rem);
  width: 100%; /* Ensure consistent width */
  max-width: 100%; /* Prevent overflow */
  box-sizing: border-box; /* Include padding/borders in width */
}

.insights-section:last-child {
  margin-bottom: var(--gp-spacing-xl);
}

.insights-section-title {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-xl);
  padding-bottom: var(--gp-spacing-md);
  border-bottom: 2px solid var(--gp-border-light);
}

.insights-section-title i {
  color: var(--gp-primary);
  font-size: 1.25rem;
}

/* Main Insights Grid */
.insights-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 2fr;
  gap: var(--gp-spacing-xl);
}

.insights-grid-simple {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: var(--gp-spacing-xl);
}

/* Geographic Section */
.geographic-grid {
  display: grid;
  gap: var(--gp-spacing-xl);
  width: 100%;
  max-width: 1040px;
  margin: 0 auto;
  /* Desktop: 2 equal columns */
  grid-template-columns: 1fr 1fr;
}

.geographic-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-lg);
  transition: all 0.3s ease;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  min-height: 280px;
  display: flex;
  flex-direction: column;
}

.geographic-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-card-hover);
  border-color: var(--gp-primary);
}

.geographic-header {
  display: flex;
  align-items: baseline;
  gap: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-md);
}

.geographic-count {
  font-size: 2rem;
  font-weight: 800;
  color: var(--gp-primary);
  line-height: 1;
}

.geographic-label {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.geographic-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  flex: 1; /* Take available space */
  /* Add scrolling for overflow content instead of expanding */
  max-height: 300px;
  overflow-y: auto;
  transition: none; /* Remove transitions that cause jumping */
}
.geographic-item {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  transition: background-color 0.2s ease;
}

.geographic-item:hover {
  background: var(--gp-timeline-blue);
}

.country-flag-img {
  width: 24px;
  height: auto;
  border-radius: var(--gp-radius-small);
  object-fit: cover;
  flex-shrink: 0;
}

.country-flag-placeholder {
  font-size: 1.25rem;
  width: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.country-name,
.city-name {
  font-weight: 500;
  color: var(--gp-text-primary);
  flex: 1;
}

.city-icon {
  color: var(--gp-secondary);
  font-size: 0.875rem;
}

.city-visits {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  font-weight: 500;
}

.view-more-btn {
  background: none;
  border: none;
  color: var(--gp-primary);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  padding: var(--gp-spacing-sm) 0;
  text-align: left;
  transition: color 0.2s ease;
  width: 90px; /* Fixed width to prevent size changes */
  white-space: nowrap; /* Keep text on one line */
}

.view-more-btn:hover {
  color: var(--gp-primary-dark);
}

/* Most Explored Location Card */
.most-explored-card {
  /* Same styling as other geographic cards */
}

.location-showcase {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-lg);
  padding: var(--gp-spacing-md);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.location-icon {
  width: 48px;
  height: 48px;
  background: var(--gp-timeline-blue);
  border-radius: var(--gp-radius-medium);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.location-icon i {
  font-size: 1.5rem;
  color: var(--gp-primary);
}

.location-info {
  flex: 1;
}

.location-name {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
  line-height: 1.2;
}

.location-subtitle {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.location-metrics {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.metric-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-small);
}

.metric-icon {
  color: var(--gp-primary);
  font-size: 0.875rem;
  width: 16px;
  flex-shrink: 0;
}

.metric-label {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
  flex: 1;
}

.metric-value {
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  font-weight: 600;
}

.no-data {
  text-align: center;
  padding: var(--gp-spacing-lg);
  color: var(--gp-text-muted);
  font-style: italic;
}

/* Travel Records Layout */
.travel-records-grid {
  display: grid;
  gap: var(--gp-spacing-xl);
  width: 100%;
  max-width: 1040px;
  margin: 0 auto;
  /* Desktop: 3 equal columns */
  grid-template-columns: repeat(3, 1fr);
}

/* Travel Cards */
.travel-card {
  position: relative;
  text-align: center;
  padding: var(--gp-spacing-xl) var(--gp-spacing-lg);
  min-height: 240px; /* Fixed height */
  max-height: 240px; /* Prevent expansion */
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden; /* Prevent content overflow */
}

.travel-icon {
  font-size: 3rem;
  margin-bottom: var(--gp-spacing-md);
  display: block;
  line-height: 1;
}

.travel-card .stat-number {
  margin-bottom: var(--gp-spacing-sm);
}

.travel-card .stat-label {
  margin-bottom: var(--gp-spacing-sm);
}

.travel-card .stat-detail {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
  line-height: 1.4;
  font-style: italic;
  min-height: 40px; /* Fixed minimum height for consistent layout */
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}

/* Stat Containers */
.insight-stat-large,
.insight-stat-medium,
.insight-stat-wide,
.insight-stat-highlight {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  text-align: center;
  transition: all 0.3s ease;
}

.insight-stat-large:hover,
.insight-stat-medium:hover,
.insight-stat-wide:hover,
.insight-stat-highlight:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-card-hover);
  border-color: var(--gp-primary);
}

/* Highlighted Total Distance Card */
.insight-stat-highlight {
  background: linear-gradient(135deg, var(--gp-primary) 0%, #3b82f6 100%);
  color: white;
  border-color: var(--gp-primary);
  padding: var(--gp-spacing-xxl);
}


.insight-stat-wide {
  grid-column: span 1;
}

/* Numbers and Text */
.stat-number {
  font-size: 3rem;
  font-weight: 800;
  color: var(--gp-primary);
  line-height: 1;
  margin-bottom: var(--gp-spacing-sm);
}

.stat-number-medium {
  font-size: 2.25rem;
  font-weight: 700;
  color: var(--gp-primary);
  line-height: 1.1;
  margin-bottom: var(--gp-spacing-sm);
}

.stat-number-xl {
  font-size: 3.5rem;
  font-weight: 900;
  color: white;
  line-height: 0.9;
  margin-bottom: var(--gp-spacing-sm);
}


.stat-label {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
}

.stat-label-large {
  font-size: 1.25rem;
  font-weight: 700;
  color: white;
  margin-bottom: var(--gp-spacing-xs);
}


.stat-detail {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.stat-detail-large {
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.9);
  font-weight: 500;
}

/* Activity Patterns */
.insight-stat-pattern {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  transition: all 0.3s ease;
}

.insight-stat-pattern:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-card-hover);
  border-color: var(--gp-secondary);
}

.pattern-icon {
  font-size: 2.5rem;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-timeline-blue);
  border-radius: var(--gp-radius-medium);
  flex-shrink: 0;
}

.pattern-content {
  flex: 1;
}

.pattern-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
}

.pattern-label {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
}

/* Enhanced Activity Patterns */
.insight-stat-pattern.enhanced {
  padding: var(--gp-spacing-lg);
}

.pattern-insight {
  font-size: 0.875rem;
  color: var(--gp-secondary);
  font-weight: 500;
  margin-top: var(--gp-spacing-xs);
  font-style: italic;
}

/* Achievement Badges */
.milestones-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--gp-spacing-xl);
}

.achievement-badge {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-lg);
  text-align: center;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.achievement-badge:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-card-hover);
}

.achievement-badge.earned {
  border-color: var(--gp-success);
  background: linear-gradient(135deg, var(--gp-surface-white) 0%, rgba(16, 185, 129, 0.05) 100%);
}

.achievement-badge.earned::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--gp-success);
}

.badge-icon {
  font-size: 3rem;
  margin-bottom: var(--gp-spacing-md);
  display: block;
}

.badge-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-sm);
}

.badge-description {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  margin-bottom: var(--gp-spacing-lg);
  line-height: 1.4;
}

.badge-progress {
  margin-top: var(--gp-spacing-md);
}

.progress-bar {
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-small);
  height: 8px;
  overflow: hidden;
  margin-bottom: var(--gp-spacing-sm);
}

.progress-fill {
  background: linear-gradient(90deg, var(--gp-primary) 0%, var(--gp-secondary) 100%);
  height: 100%;
  border-radius: var(--gp-radius-small);
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  font-weight: 500;
}

.badge-earned {
  margin-top: var(--gp-spacing-md);
}

.earned-text {
  display: block;
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-success);
  margin-bottom: var(--gp-spacing-xs);
}

.earned-date {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
}


/* Empty State */
.empty-insights {
  text-align: center;
  padding: var(--gp-spacing-xxl, 4rem) var(--gp-spacing-lg);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-large);
  border: 1px solid var(--gp-border-light);
}

.empty-icon {
  font-size: 4rem;
  color: var(--gp-text-muted);
  margin-bottom: var(--gp-spacing-lg);
  display: block;
}

.empty-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-md);
}

.empty-message {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  margin: 0;
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
  line-height: 1.6;
}

/* Dark Mode */
.p-dark .insights-section-title {
  color: var(--gp-text-primary);
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .insight-stat-large,
.p-dark .insight-stat-medium,
.p-dark .insight-stat-wide,
.p-dark .insight-stat-pattern,
.p-dark .milestone-card {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .insight-stat-highlight {
  background: linear-gradient(135deg, var(--gp-primary) 0%, #3b82f6 100%);
  color: white;
  border-color: var(--gp-primary);
}

.p-dark .stat-label,
.p-dark .pattern-value,
.p-dark .milestone-label {
  color: var(--gp-text-primary);
}

.p-dark .stat-detail,
.p-dark .pattern-label,
.p-dark .milestone-date {
  color: var(--gp-text-secondary);
}

.p-dark .pattern-icon {
  background: rgba(30, 64, 175, 0.2);
}

.p-dark .empty-insights {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .empty-title {
  color: var(--gp-text-primary);
}

.p-dark .empty-message {
  color: var(--gp-text-secondary);
}

/* Dark mode for geographic cards */
.p-dark .geographic-card {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .location-showcase,
.p-dark .metric-row {
  background: var(--gp-surface-darker);
}

.p-dark .location-icon {
  background: rgba(30, 64, 175, 0.2);
}

.p-dark .location-name {
  color: var(--gp-text-primary);
}

.p-dark .location-subtitle {
  color: var(--gp-text-secondary);
}

.p-dark .metric-label {
  color: var(--gp-text-secondary);
}

.p-dark .metric-value {
  color: var(--gp-text-primary);
}

.p-dark .travel-card .stat-detail {
  color: var(--gp-text-secondary);
}

/* Responsive Design */
@media (max-width: 1200px) {
  .insights-grid {
    grid-template-columns: 1fr 1fr;
  }
  
  .insight-stat-wide {
    grid-column: span 2;
  }
  
  .geographic-grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--gp-spacing-lg);
  }
  
  .travel-records-grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--gp-spacing-lg);
  }
}

@media (max-width: 768px) {
  .insights-content-wrapper {
    padding: 0 var(--gp-spacing-sm);
  }

  .insights-section {
    margin-bottom: var(--gp-spacing-xl);
  }

  .insights-section-title {
    font-size: 1.25rem;
    margin-bottom: var(--gp-spacing-lg);
  }

  /* Mobile: Single column for all grids */
  .insights-grid,
  .insights-grid-simple,
  .geographic-grid,
  .travel-records-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-lg);
    max-width: 100%;
  }

  /* Mobile card adjustments */
  .geographic-card {
    min-height: 260px; /* Slightly smaller on mobile */
    padding: var(--gp-spacing-md);
  }

  .geographic-list {
    max-height: 160px; /* Smaller scroll area on mobile */
  }

  .travel-card {
    min-height: 200px;
    max-height: 200px;
    padding: var(--gp-spacing-md);
  }

  /* Card padding adjustments for mobile */
  .insight-stat-large,
  .insight-stat-medium,
  .insight-stat-wide,
  .insight-stat-highlight {
    padding: var(--gp-spacing-md);
  }

  .stat-number {
    font-size: 2.5rem;
  }

  .stat-number-xl {
    font-size: 2.75rem;
  }

  .insight-stat-pattern {
    flex-direction: column;
    text-align: center;
    gap: var(--gp-spacing-md);
    padding: var(--gp-spacing-md);
  }

  .pattern-icon {
    width: 50px;
    height: 50px;
    font-size: 2rem;
  }

  .milestones-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-lg);
  }

  /* Better mobile view-more button */
  .view-more-btn {
    background: var(--gp-surface-light);
    border-radius: var(--gp-radius-small);
    padding: var(--gp-spacing-sm);
    text-align: center;
    margin-top: var(--gp-spacing-xs);
    min-width: auto;
  }
}

@media (max-width: 480px) {
  .insights-content-wrapper {
    padding: 0 var(--gp-spacing-xs);
  }

  .geographic-card,
  .travel-card,
  .insight-stat-large,
  .insight-stat-medium,
  .insight-stat-wide,
  .insight-stat-highlight,
  .achievement-badge {
    padding: var(--gp-spacing-sm);
  }

  .geographic-card {
    min-height: 240px;
  }

  .geographic-list {
    max-height: 140px;
  }

  .travel-card {
    min-height: 180px;
    max-height: 180px;
  }

  .travel-icon {
    font-size: 2.5rem;
  }

  .stat-number {
    font-size: 2rem;
  }

  .stat-number-medium {
    font-size: 1.75rem;
  }

  .stat-number-xl {
    font-size: 2.25rem;
  }

  .empty-icon {
    font-size: 3rem;
  }

  .empty-title {
    font-size: 1.25rem;
  }
}
</style>