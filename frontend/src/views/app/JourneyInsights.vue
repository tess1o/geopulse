<template>
  <AppLayout variant="default">
    <PageContainer title="Journey Insights" subtitle="Your location story, all in one place." max-width="large" :loading="isLoading">
      <div v-if="isLoading" class="insights-loading"><ProgressSpinner size="large" /><p>Building your journey insights…</p></div>

      <BaseCard v-else-if="!hasAnyData" class="empty-card">
        <i class="pi pi-compass empty-icon"></i><h3>No Journey Data Available</h3>
        <p>Start tracking your location to unlock insights about your travel patterns and achievements.</p>
      </BaseCard>

      <div v-else class="insights-content">
        <section class="journey-hero" aria-labelledby="journey-hero-title">
          <div class="journey-hero-copy">
            <p class="journey-eyebrow"><i class="pi pi-compass"></i> All-time journey</p>
            <h2 id="journey-hero-title">{{ formattedTotalDistance }} of movement.</h2>
          </div>
          <div class="hero-movement" aria-labelledby="movement-title">
            <h3 id="movement-title" class="hero-movement-title"><i class="pi pi-directions"></i> How you moved</h3>
            <div v-if="movementModes.length" class="movement-summary">
              <div class="movement-bar" aria-label="Distance split by transport type"><span v-for="mode in movementModes" :key="mode.key" :style="{ width: `${mode.share}%`, background: mode.color }" :title="`${mode.label}: ${mode.distance} (${mode.share}%)`"></span></div>
              <div class="movement-legend"><span v-for="mode in movementModes" :key="mode.key"><i :style="{ background: mode.color }"></i>{{ mode.label }} <b>{{ mode.share }}%</b><small>{{ mode.distance }}</small></span></div>
            </div>
            <div v-else class="section-placeholder"><i class="pi pi-compass"></i><p>No movement has been recorded yet.</p></div>
          </div>
        </section>

        <section class="insights-section" aria-labelledby="places-title">
          <h3 id="places-title" class="section-title"><i class="pi pi-globe"></i> Where you've been</h3>
          <div class="places-grid">
            <article class="places-card">
              <div class="places-card-heading"><span><i class="pi pi-flag"></i> Countries explored</span><b>{{ countriesCount }}</b></div>
              <div v-if="displayedCountries.length" class="places-list">
                <div v-for="country in displayedCountries" :key="country.name" class="place-row">
                  <span v-if="country.flagClass" class="country-flag-img flag" :class="country.flagClass" role="img" :aria-label="`${country.name} flag`"></span><span v-else class="country-flag-placeholder">🏳️</span><span>{{ country.name }}</span>
                </div>
              </div>
              <p v-else class="no-data">Start exploring to discover countries!</p>
            </article>
            <article class="places-card">
              <div class="places-card-heading"><span><i class="pi pi-map-marker"></i> Cities visited</span><b>{{ citiesCount }}</b></div>
              <div v-if="displayedCities.length" class="places-list"><div v-for="city in displayedCities" :key="city.name" class="place-row city-row"><i class="pi pi-building city-icon"></i><span>{{ city.name }}</span><small>{{ city.visits }} visits</small></div></div>
              <p v-else class="no-data">Start tracking to discover cities!</p>
            </article>
          </div>
        </section>

        <section class="insights-section" aria-labelledby="patterns-title">
          <h3 id="patterns-title" class="section-title"><i class="pi pi-calendar"></i> Time patterns</h3>
          <div class="patterns-grid"><article v-for="pattern in patternCards" :key="pattern.label" class="pattern-card"><span>{{ pattern.icon }}</span><div><p class="card-kicker">{{ pattern.label }}</p><strong>{{ pattern.value }}</strong><small v-if="pattern.detail">{{ pattern.detail }}</small></div></article></div>
        </section>

        <JourneyWeatherInsights :weather="weather" :distance-unit="distanceUnit" :temperature-unit="temperatureUnit" />

        <section class="insights-section" aria-labelledby="milestones-title">
          <h3 id="milestones-title" class="section-title"><i class="pi pi-trophy"></i> Journey milestones <span>{{ earnedAchievementsCount }} / {{ achievementBadges.length }}</span></h3>
          <div v-if="achievementGroups.length" class="achievement-groups">
            <section v-for="group in achievementGroups" :key="group.key" class="achievement-group" :aria-labelledby="`achievement-group-${group.key}`">
              <h4 :id="`achievement-group-${group.key}`" class="achievement-group-title"><span>{{ group.icon }} {{ group.title }}</span><small>{{ group.earnedCount }} / {{ group.badges.length }}</small></h4>
              <div class="milestones-grid">
                <article v-for="badge in group.badges" :key="badge.id" class="milestone-card" :class="{ earned: badge.earned }">
                  <div class="milestone-header"><span class="badge-icon">{{ badge.icon }}</span><span class="milestone-status">{{ badge.earned ? 'Earned' : `${badge.progress}%` }}</span></div>
                  <h5>{{ badge.title }}</h5><p>{{ badge.description }}</p>
                  <template v-if="badge.earned"><small v-if="badge.earnedDate">Earned {{ timezone.formatDate(badge.earnedDate) }}</small></template>
                  <template v-else><div class="progress-bar"><span :style="{ width: `${badge.progress}%` }"></span></div><small>{{ badge.progressText }}</small></template>
                </article>
              </div>
            </section>
          </div>
          <div v-else class="section-placeholder"><i class="pi pi-trophy"></i><p>Keep exploring to unlock milestones!</p></div>
        </section>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import ProgressSpinner from 'primevue/progressspinner'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import JourneyWeatherInsights from '@/components/insights/JourneyWeatherInsights.vue'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useTimezone } from '@/composables/useTimezone'
import { getCountryFlagClass } from '@/utils/countryFlags'
import { formatDistanceRounded } from '@/utils/calculationsHelpers'
import { useJourneyInsightsStore } from '@/stores/journeyInsights'
import { useAuthStore } from '@/stores/auth'

const ACHIEVEMENT_CATEGORIES = [
  { key: 'distance', title: 'Distance milestones', icon: '🛣️', matches: /^(total_distance|target_trip_distance|long_hauler|speed_deamon)/ },
  { key: 'exploration', title: 'Exploration', icon: '🗺️', matches: /^(country_visited|cites_visited|local_explorer|local_legend)/ },
  { key: 'modes', title: 'Travel modes', icon: '🚆', matches: /^(flight_trips|train_trips|daily_driver)/ },
  { key: 'consistency', title: 'Consistency streaks', icon: '🔥', matches: /^(daily_habit|track_data_week|first_month|first_steps|busy_bee)/ },
  { key: 'time', title: 'Time of day', icon: '🕐', matches: /^time_of_day/ },
  { key: 'weather', title: 'Weather explorer', icon: '🌦️', matches: /^weather_/ }
]

const timezone = useTimezone()
const journeyInsightsStore = useJourneyInsightsStore()
const authStore = useAuthStore()
const { handleErrorWithRetry } = useErrorHandler()
const { loading: isLoading } = storeToRefs(journeyInsightsStore)
const { distanceUnit, temperatureUnit } = storeToRefs(authStore)

const geographic = computed(() => journeyInsightsStore.geographic)
const timePatterns = computed(() => journeyInsightsStore.timePatterns)
const achievements = computed(() => journeyInsightsStore.achievements)
const distanceTraveled = computed(() => journeyInsightsStore.distance)
const weather = computed(() => journeyInsightsStore.weather)
const hasAnyData = computed(() => journeyInsightsStore.hasData)
const countriesCount = computed(() => geographic.value.countries?.length || 0)
const citiesCount = computed(() => geographic.value.cities?.length || 0)
const formattedTotalDistance = computed(() => formatDistanceRounded((Number(distanceTraveled.value.total) || 0) * 1000))

const localMostActiveTime = computed(() => {
  const utcTime = timePatterns.value.mostActiveTime
  if (!utcTime) return 'N/A'
  try {
    const time24 = utcTime.replace(/(\d{1,2}):(\d{2})\s*(AM|PM)/i, (_, hours, minutes, period) => {
      let hour = Number(hours)
      if (period.toUpperCase() === 'PM' && hour !== 12) hour += 12
      if (period.toUpperCase() === 'AM' && hour === 12) hour = 0
      return `${String(hour).padStart(2, '0')}:${minutes}`
    })
    const [hours, minutes] = time24.split(':').map(Number)
    return timezone.formatTime(timezone.now().startOf('day').utc().hour(hours).minute(minutes).toISOString())
  } catch (error) {
    console.error('Error converting time to local timezone:', error)
    return utcTime
  }
})

const displayedCountries = computed(() => (geographic.value.countries || []).map((country) => ({ ...country, flagClass: getCountryFlagClass(country.name) })))
const displayedCities = computed(() => geographic.value.cities || [])
const achievementBadges = computed(() => (achievements.value.badges || []).map((badge) => ({ ...badge, progress: Math.min(100, badge.progress || 0), progressText: badge.progressText || `${badge.current || 0}/${badge.target || 0}` })))
const earnedAchievementsCount = computed(() => achievementBadges.value.filter((badge) => badge.earned).length)
const achievementGroups = computed(() => {
  const categorized = ACHIEVEMENT_CATEGORIES.map((category) => ({ ...category, badges: achievementBadges.value.filter((badge) => category.matches.test(badge.id)), earnedCount: 0 })).filter((category) => category.badges.length)
  const categorizedIds = new Set(categorized.flatMap((category) => category.badges.map((badge) => badge.id)))
  const otherBadges = achievementBadges.value.filter((badge) => !categorizedIds.has(badge.id))
  if (otherBadges.length) categorized.push({ key: 'other', title: 'Other achievements', icon: '✨', badges: otherBadges, earnedCount: 0 })
  return categorized.map((category) => ({ ...category, earnedCount: category.badges.filter((badge) => badge.earned).length }))
})
const movementModes = computed(() => {
  const total = Number(distanceTraveled.value.total) || 0
  const definitions = [['byCar', 'Car', '🚗', '#3b82f6'], ['byMotorcycle', 'Motorcycle', '🏍️', '#06b6d4'], ['byWalk', 'Walk', '🚶', '#10b981'], ['byBicycle', 'Bicycle', '🚴', '#f59e0b'], ['byRunning', 'Running', '🏃', '#8b5cf6'], ['byTrain', 'Train', '🚆', '#64748b'], ['byFlight', 'Flight', '✈️', '#ef4444'], ['byBoat', 'Boat', '🚤', '#14b8a6'], ['byUnknown', 'Unclassified', '🧭', '#94a3b8']]
  return definitions.map(([key, label, icon, color]) => ({ key, label, icon, color, value: Number(distanceTraveled.value[key]) || 0 })).filter((mode) => mode.value > 0).map((mode) => ({ ...mode, distance: formatDistanceRounded(mode.value * 1000), share: Math.max(1, Math.round((mode.value / total) * 100)) }))
})
const patternCards = computed(() => [
  { icon: '📅', label: 'Most active month', value: timePatterns.value.mostActiveMonth || 'N/A', detail: 'Your historical peak activity period' },
  { icon: '📊', label: 'Current month', value: timezone.format(timezone.now(), 'MMMM YYYY'), detail: timePatterns.value.monthlyComparison },
  { icon: '📍', label: 'Busiest day', value: timePatterns.value.busiestDayOfWeek || 'N/A', detail: timePatterns.value.dayInsight },
  { icon: '🕐', label: 'Most active time', value: localMostActiveTime.value, detail: timePatterns.value.timeInsight }
])
const fetchJourneyInsights = async () => {
  try { await journeyInsightsStore.fetchJourneyInsights() } catch (error) { console.error('Error fetching journey insights:', error); handleErrorWithRetry(error, fetchJourneyInsights) }
}

onMounted(fetchJourneyInsights)
</script>

<style scoped>
.insights-content { width:100%; margin:0 auto }.insights-loading,.empty-card { display:grid; place-items:center; min-height:22rem; text-align:center }.insights-loading { gap:var(--gp-spacing-lg); color:var(--gp-text-secondary) }.empty-card { max-width:34rem; margin:0 auto; padding:var(--gp-spacing-xxl) }.empty-icon { font-size:3.25rem; color:var(--gp-primary); margin-bottom:var(--gp-spacing-md) }.empty-card h3,.empty-card p { margin:0 }.empty-card p { color:var(--gp-text-secondary); margin-top:var(--gp-spacing-sm) }
.journey-hero { position:relative; overflow:hidden; padding:clamp(1.5rem,4vw,2.25rem); margin-bottom:var(--gp-spacing-xl); border:1px solid color-mix(in srgb,var(--gp-primary) 28%,var(--gp-border-light)); border-radius:20px; background:linear-gradient(128deg,color-mix(in srgb,var(--gp-primary) 16%,var(--gp-surface-white)),var(--gp-surface-white) 58%); box-shadow:var(--gp-shadow-card) }.journey-hero::after { content:''; position:absolute; width:18rem; height:18rem; right:-7rem; top:-11rem; border-radius:50%; background:color-mix(in srgb,var(--gp-secondary) 22%,transparent); pointer-events:none }.journey-hero-copy { position:relative; z-index:1 }.journey-eyebrow,.section-title,.places-card-heading { display:flex; align-items:center; gap:.45rem }.journey-eyebrow { margin:0 0 .7rem; font-size:.78rem; font-weight:800; letter-spacing:.09em; text-transform:uppercase; color:var(--gp-primary) }.journey-hero h2 { margin:0; font-size:clamp(2rem,4.6vw,4rem); line-height:.98; letter-spacing:-.055em; color:var(--gp-text-primary) }.card-kicker { font-size:.74rem; font-weight:700; color:var(--gp-text-secondary); text-transform:uppercase; letter-spacing:.06em }
.hero-movement { position:relative; z-index:1; margin-top:var(--gp-spacing-xl) }.hero-movement-title { display:flex; align-items:center; gap:.45rem; margin:0 0 var(--gp-spacing-md); color:var(--gp-text-primary); font-size:1rem }.hero-movement-title i { color:var(--gp-primary) }.movement-bar { display:flex; overflow:hidden; height:.7rem; border-radius:999px; background:var(--gp-border-subtle) }.movement-bar span { min-width:2px }.movement-legend { display:flex; flex-wrap:wrap; gap:.6rem 1rem; margin-top:.7rem; color:var(--gp-text-secondary); font-size:.8rem }.movement-legend span { display:inline-flex; align-items:center; gap:.32rem }.movement-legend i { width:.55rem; height:.55rem; border-radius:50%; flex-shrink:0 }.movement-legend b { color:var(--gp-text-primary) }.movement-legend small { color:var(--gp-text-muted) }
.insights-section { margin-bottom:var(--gp-spacing-xl) }.section-title { margin:0 0 var(--gp-spacing-lg); font-size:1.25rem; color:var(--gp-text-primary) }.section-title > i { color:var(--gp-primary) }.section-title span { color:var(--gp-text-secondary); font-size:.875rem; font-weight:500 }.movement-grid,.patterns-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:var(--gp-spacing-md) }.movement-card,.pattern-card,.places-card,.milestone-card { border:1px solid var(--gp-border-light); border-radius:14px; background:var(--gp-surface-light); transition:border-color .2s ease,background .2s ease,transform .2s ease }.movement-card:hover,.pattern-card:hover,.places-card:hover,.milestone-card:hover { border-color:var(--gp-primary); background:color-mix(in srgb,var(--gp-primary) 8%,var(--gp-surface-light)); transform:translateY(-1px) }.movement-card,.pattern-card { display:flex; gap:var(--gp-spacing-md); align-items:center; padding:var(--gp-spacing-lg) }.movement-icon,.pattern-card > span { display:grid; place-items:center; width:2.6rem; height:2.6rem; border-radius:10px; background:var(--gp-surface-white); font-size:1.35rem; flex-shrink:0 }.movement-card strong,.pattern-card strong { display:block; color:var(--gp-text-primary); font-size:1.15rem; line-height:1.25 }.movement-card small,.pattern-card small,.milestone-card small { display:block; margin-top:.25rem; color:var(--gp-text-secondary); font-size:.78rem }.card-kicker { margin:0 0 .28rem }.places-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:var(--gp-spacing-lg) }.places-card { padding:var(--gp-spacing-lg); min-width:0 }.places-card-heading { justify-content:space-between; padding-bottom:var(--gp-spacing-md); border-bottom:1px solid var(--gp-border-light); color:var(--gp-text-primary); font-weight:700 }.places-card-heading i { color:var(--gp-primary) }.places-card-heading b { color:var(--gp-primary); font-size:1.25rem }.places-list { display:grid; gap:var(--gp-spacing-sm); max-height:18.75rem; overflow-y:auto; padding-top:var(--gp-spacing-md) }.place-row { display:flex; align-items:center; gap:var(--gp-spacing-sm); min-width:0; padding:var(--gp-spacing-sm); border-radius:10px; background:var(--gp-surface-white); color:var(--gp-text-primary); font-weight:500 }.place-row > span:last-child { overflow:hidden; text-overflow:ellipsis; white-space:nowrap }.city-row i { color:var(--gp-secondary) }.city-row small { margin-left:auto; color:var(--gp-text-secondary); white-space:nowrap }.country-flag-img.flag { width:24px; height:16px; border-radius:var(--gp-radius-small); flex-shrink:0; box-shadow:0 0 0 1px var(--gp-border-light) }.country-flag-placeholder { width:24px; font-size:1.25rem; text-align:center; flex-shrink:0 }.no-data { margin:var(--gp-spacing-xl) 0 0; color:var(--gp-text-muted); text-align:center; font-style:italic }.section-placeholder { display:grid; place-items:center; min-height:9rem; border:1px dashed var(--gp-border-light); border-radius:14px; color:var(--gp-text-muted); text-align:center }.section-placeholder i { font-size:1.5rem }.section-placeholder p { margin:.5rem 0 0 }.milestones-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(13.75rem,1fr)); gap:var(--gp-spacing-md) }.milestone-card { padding:var(--gp-spacing-lg); position:relative; overflow:hidden }.milestone-card.earned { border-color:var(--gp-success); background:color-mix(in srgb,var(--gp-success) 7%,var(--gp-surface-light)) }.milestone-header { display:flex; align-items:flex-start; justify-content:space-between; gap:var(--gp-spacing-sm); margin-bottom:var(--gp-spacing-md) }.badge-icon { font-size:2rem; line-height:1 }.milestone-status { padding:.25rem .5rem; border-radius:999px; background:var(--gp-surface-white); color:var(--gp-text-secondary); font-size:.7rem; font-weight:700; text-transform:uppercase }.earned .milestone-status { background:var(--gp-success-light); color:var(--gp-success-dark) }.milestone-card h4 { margin:0 0 var(--gp-spacing-xs); color:var(--gp-text-primary); font-size:1rem }.milestone-card p { min-height:2.5em; margin:0; color:var(--gp-text-secondary); font-size:.84rem; line-height:1.4 }.progress-bar { height:.45rem; overflow:hidden; margin-top:var(--gp-spacing-md); border-radius:999px; background:var(--gp-border-subtle) }.progress-bar span { display:block; height:100%; border-radius:inherit; background:var(--gp-primary) }.p-dark .journey-hero { background:linear-gradient(128deg,color-mix(in srgb,var(--gp-primary) 25%,var(--gp-surface-dark)),var(--gp-surface-dark) 64%) }.p-dark .movement-icon,.p-dark .pattern-card > span,.p-dark .place-row,.p-dark .milestone-status { background:var(--gp-surface-darker) }
.city-icon { display:grid!important; place-items:center; width:24px; height:16px; flex-shrink:0; border-radius:var(--gp-radius-small); background:var(--gp-timeline-blue); font-size:.75rem }.achievement-groups { display:grid; gap:var(--gp-spacing-xl) }.achievement-group-title { display:flex; align-items:center; justify-content:space-between; gap:var(--gp-spacing-md); margin:0 0 var(--gp-spacing-md); padding-bottom:var(--gp-spacing-sm); border-bottom:1px solid var(--gp-border-light); color:var(--gp-text-primary); font-size:1rem }.achievement-group-title small { color:var(--gp-text-secondary); font-size:.78rem; font-weight:600 }.milestone-card h5 { margin:0 0 var(--gp-spacing-xs); color:var(--gp-text-primary); font-size:1rem }.milestone-card.earned { border-color:var(--gp-warning); background:color-mix(in srgb,var(--gp-warning) 10%,var(--gp-surface-light)); box-shadow:0 0 1.25rem color-mix(in srgb,var(--gp-warning) 22%,transparent) }.earned .milestone-status { background:color-mix(in srgb,var(--gp-warning) 18%,var(--gp-surface-light)); color:var(--gp-warning) }.progress-bar { height:.65rem; background:color-mix(in srgb,var(--gp-primary) 18%,var(--gp-border-subtle)) }.progress-bar span { background:linear-gradient(90deg,var(--gp-primary),var(--gp-secondary)) }
@media (max-width:960px) { .movement-grid,.patterns-grid { grid-template-columns:repeat(2,minmax(0,1fr)) } }@media (max-width:720px) { .journey-hero { padding:1.35rem }.journey-hero h2 { font-size:2.55rem }.places-grid,.movement-grid,.patterns-grid { grid-template-columns:1fr }.empty-card { padding:var(--gp-spacing-xl) } }@media (prefers-reduced-motion:reduce) { * { transition:none!important } }
</style>
