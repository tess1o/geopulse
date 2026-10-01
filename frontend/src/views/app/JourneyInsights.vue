<template>
  <AppLayout variant="default">
    <PageContainer :title="t('insights.page.title')" :subtitle="t('insights.page.subtitle')" max-width="large" :loading="isLoading">
      <div v-if="isLoading" class="insights-loading"><ProgressSpinner size="large" /><p>{{ t('insights.loading') }}</p></div>

      <BaseCard v-else-if="!hasAnyData" class="empty-card">
        <i class="pi pi-compass empty-icon"></i><h3>{{ t('insights.empty.title') }}</h3>
        <p>{{ t('insights.empty.description') }}</p>
      </BaseCard>

      <div v-else class="insights-content">
        <section class="journey-hero" aria-labelledby="journey-hero-title">
          <div class="journey-hero-copy">
            <p class="journey-eyebrow"><i class="pi pi-compass"></i> {{ t('insights.hero.eyebrow') }}</p>
            <h2 id="journey-hero-title">{{ t('insights.hero.movementSummary', { distance: formattedTotalDistance }) }}</h2>
          </div>
          <div class="hero-movement" aria-labelledby="movement-title">
            <h3 id="movement-title" class="hero-movement-title"><i class="pi pi-directions"></i> {{ t('insights.hero.movementTitle') }}</h3>
            <div v-if="movementModes.length" class="movement-summary">
              <div class="movement-bar" :aria-label="t('insights.hero.movementAria')"><span v-for="mode in movementModes" :key="mode.key" :style="{ width: `${mode.share}%`, background: mode.color }" :title="`${mode.label}: ${mode.distance} (${mode.share}%)`"></span></div>
              <div class="movement-legend"><span v-for="mode in movementModes" :key="mode.key"><i :style="{ background: mode.color }"></i>{{ mode.label }} <b>{{ mode.share }}%</b><small>{{ mode.distance }}</small></span></div>
            </div>
            <div v-else class="section-placeholder"><i class="pi pi-compass"></i><p>{{ t('insights.hero.noMovement') }}</p></div>
          </div>
        </section>

        <section class="insights-section" aria-labelledby="places-title">
          <h3 id="places-title" class="section-title"><i class="pi pi-globe"></i> {{ t('insights.places.title') }}</h3>
          <div class="places-grid">
            <article class="places-card">
              <div class="places-card-heading"><span><i class="pi pi-flag"></i> {{ t('insights.places.countries') }}</span><b>{{ countriesCount }}</b></div>
              <div v-if="displayedCountries.length" class="places-list">
                <div v-for="country in displayedCountries" :key="country.name" class="place-row">
                  <span v-if="country.flagClass" class="country-flag-img flag" :class="country.flagClass" role="img" :aria-label="t('insights.places.countryFlagAria', { country: country.name })"></span><span v-else class="country-flag-placeholder">🏳️</span><span>{{ country.name }}</span>
                </div>
              </div>
              <p v-else class="no-data">{{ t('insights.places.noCountries') }}</p>
            </article>
            <article class="places-card">
              <div class="places-card-heading"><span><i class="pi pi-map-marker"></i> {{ t('insights.places.cities') }}</span><b>{{ citiesCount }}</b></div>
              <div v-if="displayedCities.length" class="places-list"><div v-for="city in displayedCities" :key="city.name" class="place-row city-row"><i class="pi pi-building city-icon"></i><span>{{ city.name }}</span><small>{{ t('insights.places.visits', { count: city.visits }, city.visits) }}</small></div></div>
              <p v-else class="no-data">{{ t('insights.places.noCities') }}</p>
            </article>
          </div>
        </section>

        <section class="insights-section" aria-labelledby="patterns-title">
          <h3 id="patterns-title" class="section-title"><i class="pi pi-calendar"></i> {{ t('insights.patterns.title') }}</h3>
          <div class="patterns-grid"><article v-for="pattern in patternCards" :key="pattern.label" class="pattern-card"><span>{{ pattern.icon }}</span><div><p class="card-kicker">{{ pattern.label }}</p><strong>{{ pattern.value }}</strong><small v-if="pattern.detail">{{ pattern.detail }}</small></div></article></div>
        </section>

        <JourneyWeatherInsights :weather="weather" :distance-unit="distanceUnit" :temperature-unit="temperatureUnit" />

        <section class="insights-section" aria-labelledby="milestones-title">
          <h3 id="milestones-title" class="section-title"><i class="pi pi-trophy"></i> {{ t('insights.milestones.title') }} <span>{{ earnedAchievementsCount }} / {{ achievementBadges.length }}</span></h3>
          <div v-if="achievementGroups.length" class="achievement-groups">
            <section v-for="group in achievementGroups" :key="group.key" class="achievement-group" :aria-labelledby="`achievement-group-${group.key}`">
              <h4 :id="`achievement-group-${group.key}`" class="achievement-group-title"><span>{{ group.icon }} {{ group.title }}</span><small>{{ group.earnedCount }} / {{ group.badges.length }}</small></h4>
              <div class="milestones-grid">
                <article v-for="badge in group.badges" :key="badge.id" class="milestone-card" :class="{ earned: badge.earned }">
                  <div class="milestone-header"><span class="badge-icon">{{ badge.icon }}</span><span class="milestone-status">{{ badge.earned ? t('insights.milestones.earned') : `${badge.progress}%` }}</span></div>
                  <h5>{{ badgeTitle(badge) }}</h5><p>{{ badgeDescription(badge) }}</p>
                  <template v-if="badge.earned"><small v-if="badge.earnedDate">{{ t('insights.milestones.earnedOn', { date: timezone.formatDate(badge.earnedDate) }) }}</small></template>
                  <template v-else><div class="progress-bar"><span :style="{ width: `${badge.progress}%` }"></span></div><small>{{ badge.progressText }}</small></template>
                </article>
              </div>
            </section>
          </div>
          <div v-else class="section-placeholder"><i class="pi pi-trophy"></i><p>{{ t('insights.milestones.empty') }}</p></div>
        </section>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import dayjs from 'dayjs'
import ProgressSpinner from 'primevue/progressspinner'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import JourneyWeatherInsights from '@/components/insights/JourneyWeatherInsights.vue'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useTimezone } from '@/composables/useTimezone'
import { getCountryFlagClass } from '@/utils/countryFlags'
import { formatDistanceRounded } from '@/utils/calculationsHelpers'
import { formatMessageDescriptor } from '@/utils/messageDescriptor'
import { useJourneyInsightsStore } from '@/stores/journeyInsights'
import { useAuthStore } from '@/stores/auth'
import { te } from '@/locales'
import { MOVEMENT_TYPE_COLORS } from '@/utils/movementTypeColors'

// A known Monday, used only to turn an ISO-8601 day-of-week number (1=Monday..7=Sunday, as sent by
// TimePatternService.java) into a weekday name via dayjs's active locale -- the actual date is
// irrelevant, only its weekday.
const REFERENCE_MONDAY = '2024-01-01'

const ACHIEVEMENT_CATEGORIES = [
  // `matches` keys off the backend's locale-neutral badge ids and must stay untouched by translation;
  // only the `titleKey` is copy.
  { key: 'distance', titleKey: 'insights.milestones.groups.distance', icon: '🛣️', matches: /^(total_distance|target_trip_distance|long_hauler|speed_deamon)/ },
  { key: 'exploration', titleKey: 'insights.milestones.groups.exploration', icon: '🗺️', matches: /^(country_visited|cites_visited|local_explorer|local_legend)/ },
  { key: 'modes', titleKey: 'insights.milestones.groups.modes', icon: '🚆', matches: /^(flight_trips|train_trips|daily_driver)/ },
  { key: 'consistency', titleKey: 'insights.milestones.groups.consistency', icon: '🔥', matches: /^(daily_habit|track_data_week|first_month|first_steps|busy_bee)/ },
  { key: 'time', titleKey: 'insights.milestones.groups.time', icon: '🕐', matches: /^time_of_day/ },
  { key: 'weather', titleKey: 'insights.milestones.groups.weather', icon: '🌦️', matches: /^weather_/ }
]

// Mode table. Previously positional tuples where index 1 was the label, so a label could be changed
// only by counting array slots; named fields make the label explicit and its key greppable.
// `key` is the backend's locale-neutral distance field name and the i18n key suffix.
const MOVEMENT_MODES = [
  { key: 'byCar', icon: '🚗', color: MOVEMENT_TYPE_COLORS.CAR },
  { key: 'byMotorcycle', icon: '🏍️', color: MOVEMENT_TYPE_COLORS.MOTORCYCLE },
  { key: 'byPublicTransport', icon: '🚌', color: MOVEMENT_TYPE_COLORS.PUBLIC_TRANSPORT },
  { key: 'byWalk', icon: '🚶', color: MOVEMENT_TYPE_COLORS.WALK },
  { key: 'byBicycle', icon: '🚴', color: MOVEMENT_TYPE_COLORS.BICYCLE },
  { key: 'byRunning', icon: '🏃', color: MOVEMENT_TYPE_COLORS.RUNNING },
  { key: 'byTrain', icon: '🚆', color: MOVEMENT_TYPE_COLORS.TRAIN },
  { key: 'byFlight', icon: '✈️', color: MOVEMENT_TYPE_COLORS.FLIGHT },
  { key: 'byBoat', icon: '🚤', color: MOVEMENT_TYPE_COLORS.BOAT },
  { key: 'byUnknown', icon: '🧭', color: MOVEMENT_TYPE_COLORS.UNKNOWN }
]

const timezone = useTimezone()
const { t } = useI18n()
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
  const hour = timePatterns.value.mostActiveHour
  if (hour === null || hour === undefined) return 'N/A'
  // Minute is fixed at :30 -- the backend only buckets activity by hour, so this reads as "somewhere
  // in the {hour} o'clock hour" rather than an exact timestamp.
  return timezone.formatTime(timezone.now().startOf('day').utc().hour(hour).minute(30).toISOString())
})

const mostActiveMonthDisplay = computed(() => {
  const yearMonth = timePatterns.value.mostActiveYearMonth
  if (!yearMonth) return 'N/A'
  return timezone.create(`${yearMonth}-01`).format('MMMM YYYY')
})

const busiestDayDisplay = computed(() => {
  const isoDayOfWeek = timePatterns.value.busiestDayOfWeek
  if (!isoDayOfWeek) return 'N/A'
  return dayjs(REFERENCE_MONDAY).add(isoDayOfWeek - 1, 'day').format('dddd')
})

const displayedCountries = computed(() => (geographic.value.countries || []).map((country) => ({ ...country, flagClass: getCountryFlagClass(country.name) })))
const displayedCities = computed(() => geographic.value.cities || [])
const achievementBadges = computed(() => (achievements.value.badges || []).map((badge) => ({ ...badge, progress: Math.min(100, badge.progress || 0), progressText: badge.progressText || `${badge.current || 0}/${badge.target || 0}` })))
const earnedAchievementsCount = computed(() => achievementBadges.value.filter((badge) => badge.earned).length)
const achievementGroups = computed(() => {
  const categorized = ACHIEVEMENT_CATEGORIES.map((category) => ({ ...category, title: t(category.titleKey), badges: achievementBadges.value.filter((badge) => category.matches.test(badge.id)), earnedCount: 0 })).filter((category) => category.badges.length)
  const categorizedIds = new Set(categorized.flatMap((category) => category.badges.map((badge) => badge.id)))
  const otherBadges = achievementBadges.value.filter((badge) => !categorizedIds.has(badge.id))
  if (otherBadges.length) categorized.push({ key: 'other', title: t('insights.milestones.groups.other'), icon: '✨', badges: otherBadges, earnedCount: 0 })
  return categorized.map((category) => ({ ...category, earnedCount: category.badges.filter((badge) => badge.earned).length }))
})
const movementModes = computed(() => {
  const total = Number(distanceTraveled.value.total) || 0
  return MOVEMENT_MODES.map((mode) => ({ ...mode, label: t(`insights.movement.${mode.key}`), value: Number(distanceTraveled.value[mode.key]) || 0 })).filter((mode) => mode.value > 0).map((mode) => ({ ...mode, distance: formatDistanceRounded(mode.value * 1000), share: Math.max(1, Math.round((mode.value / total) * 100)) }))
})
const patternCards = computed(() => [
  { icon: '📅', label: t('insights.patterns.mostActiveMonth'), value: mostActiveMonthDisplay.value, detail: t('insights.patterns.mostActiveMonthDetail') },
  { icon: '📊', label: t('insights.patterns.currentMonth'), value: timezone.format(timezone.now(), 'MMMM YYYY'), detail: formatMessageDescriptor(timePatterns.value.monthlyComparison) },
  { icon: '📍', label: t('insights.patterns.busiestDay'), value: busiestDayDisplay.value, detail: formatMessageDescriptor(timePatterns.value.dayInsight) },
  { icon: '🕐', label: t('insights.patterns.mostActiveTime'), value: localMostActiveTime.value, detail: formatMessageDescriptor(timePatterns.value.timeInsight) }
])
const fetchJourneyInsights = async () => {
  try { await journeyInsightsStore.fetchJourneyInsights() } catch (error) { console.error('Error fetching journey insights:', error); handleErrorWithRetry(error, fetchJourneyInsights) }
}

// The backend serves and persists badge title/description in `user_badges` as English text, so
// translations live in the frontend catalog under `badges.<id>.*` (see locales/en/badges.js) keyed by
// the locale-neutral badge id. te() returns false when neither the active locale nor the fallback has
// the key, which keeps the backend's English text in place instead of a raw dotted key for any badge
// a locale hasn't caught up on yet.
const badgeText = (badge, field, fallback) => {
  const key = `badges.${badge.id}.${field}`
  return te(key) ? t(key) : fallback
}
const badgeTitle = (badge) => badgeText(badge, 'title', badge.title)
const badgeDescription = (badge) => badgeText(badge, 'description', badge.description)

onMounted(fetchJourneyInsights)
</script>

<style scoped>
.insights-content { width:100%; margin:0 auto }.insights-loading,.empty-card { display:grid; place-items:center; min-height:22rem; text-align:center }.insights-loading { gap:var(--gp-spacing-lg); color:var(--gp-text-secondary) }.empty-card { max-width:34rem; margin:0 auto; padding:var(--gp-spacing-xxl) }.empty-icon { font-size:3.25rem; color:var(--gp-primary); margin-bottom:var(--gp-spacing-md) }.empty-card h3,.empty-card p { margin:0 }.empty-card p { color:var(--gp-text-secondary); margin-top:var(--gp-spacing-sm) }
.journey-hero { position:relative; overflow:hidden; padding:clamp(1.5rem,4vw,2.25rem); margin-bottom:var(--gp-spacing-xl); border:1px solid color-mix(in srgb,var(--gp-primary) 28%,var(--gp-border)); border-radius:20px; background:linear-gradient(128deg,color-mix(in srgb,var(--gp-primary) 16%,var(--gp-surface-card)),var(--gp-surface-card) 58%); box-shadow:var(--gp-shadow-card) }.journey-hero::after { content:''; position:absolute; width:18rem; height:18rem; right:-7rem; top:-11rem; border-radius:50%; background:color-mix(in srgb,var(--gp-secondary) 22%,transparent); pointer-events:none }.journey-hero-copy { position:relative; z-index:1 }.journey-eyebrow,.section-title,.places-card-heading { display:flex; align-items:center; gap:.45rem }.journey-eyebrow { margin:0 0 .7rem; font-size:.78rem; font-weight:800; letter-spacing:.09em; text-transform:uppercase; color:var(--gp-primary) }.journey-hero h2 { margin:0; font-size:clamp(2rem,4.6vw,4rem); line-height:.98; letter-spacing:-.055em; color:var(--gp-text-primary) }.card-kicker { font-size:.74rem; font-weight:700; color:var(--gp-text-secondary); text-transform:uppercase; letter-spacing:.06em }
.hero-movement { position:relative; z-index:1; margin-top:var(--gp-spacing-xl) }.hero-movement-title { display:flex; align-items:center; gap:.45rem; margin:0 0 var(--gp-spacing-md); color:var(--gp-text-primary); font-size:1rem }.hero-movement-title i { color:var(--gp-primary) }.movement-bar { display:flex; overflow:hidden; height:.7rem; border-radius:999px; background:var(--gp-border-subtle) }.movement-bar span { min-width:2px }.movement-legend { display:flex; flex-wrap:wrap; gap:.6rem 1rem; margin-top:.7rem; color:var(--gp-text-secondary); font-size:.8rem }.movement-legend span { display:inline-flex; align-items:center; gap:.32rem }.movement-legend i { width:.55rem; height:.55rem; border-radius:50%; flex-shrink:0 }.movement-legend b { color:var(--gp-text-primary) }.movement-legend small { color:var(--gp-text-muted) }
.insights-section { margin-bottom:var(--gp-spacing-xl) }.section-title { margin:0 0 var(--gp-spacing-lg); font-size:1.25rem; color:var(--gp-text-primary) }.section-title > i { color:var(--gp-primary) }.section-title span { color:var(--gp-text-secondary); font-size:.875rem; font-weight:500 }.movement-grid,.patterns-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:var(--gp-spacing-md) }.movement-card,.pattern-card,.places-card,.milestone-card { border:1px solid var(--gp-border); border-radius:14px; background:var(--gp-surface-muted); transition:border-color .2s ease,background .2s ease,transform .2s ease }.movement-card:hover,.pattern-card:hover,.places-card:hover,.milestone-card:hover { border-color:var(--gp-primary); background:color-mix(in srgb,var(--gp-primary) 8%,var(--gp-surface-muted)); transform:translateY(-1px) }.movement-card,.pattern-card { display:flex; gap:var(--gp-spacing-md); align-items:center; padding:var(--gp-spacing-lg) }.movement-icon,.pattern-card > span { display:grid; place-items:center; width:2.6rem; height:2.6rem; border-radius:10px; background:var(--gp-surface-card); font-size:1.35rem; flex-shrink:0 }.movement-card strong,.pattern-card strong { display:block; color:var(--gp-text-primary); font-size:1.15rem; line-height:1.25 }.movement-card small,.pattern-card small,.milestone-card small { display:block; margin-top:.25rem; color:var(--gp-text-secondary); font-size:.78rem }.card-kicker { margin:0 0 .28rem }.places-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:var(--gp-spacing-lg) }.places-card { padding:var(--gp-spacing-lg); min-width:0 }.places-card-heading { justify-content:space-between; padding-bottom:var(--gp-spacing-md); border-bottom:1px solid var(--gp-border); color:var(--gp-text-primary); font-weight:700 }.places-card-heading i { color:var(--gp-primary) }.places-card-heading b { color:var(--gp-primary); font-size:1.25rem }.places-list { display:grid; gap:var(--gp-spacing-sm); max-height:18.75rem; overflow-y:auto; padding-top:var(--gp-spacing-md) }.place-row { display:flex; align-items:center; gap:var(--gp-spacing-sm); min-width:0; padding:var(--gp-spacing-sm); border-radius:10px; background:var(--gp-surface-card); color:var(--gp-text-primary); font-weight:500 }.place-row > span:last-child { overflow:hidden; text-overflow:ellipsis; white-space:nowrap }.city-row i { color:var(--gp-secondary) }.city-row small { margin-left:auto; color:var(--gp-text-secondary); white-space:nowrap }.country-flag-img.flag { width:24px; height:16px; border-radius:var(--gp-radius-small); flex-shrink:0; box-shadow:0 0 0 1px var(--gp-border) }.country-flag-placeholder { width:24px; font-size:1.25rem; text-align:center; flex-shrink:0 }.no-data { margin:var(--gp-spacing-xl) 0 0; color:var(--gp-text-muted); text-align:center; font-style:italic }.section-placeholder { display:grid; place-items:center; min-height:9rem; border:1px dashed var(--gp-border); border-radius:14px; color:var(--gp-text-muted); text-align:center }.section-placeholder i { font-size:1.5rem }.section-placeholder p { margin:.5rem 0 0 }.milestones-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(13.75rem,1fr)); gap:var(--gp-spacing-md) }.milestone-card { padding:var(--gp-spacing-lg); position:relative; overflow:hidden }.milestone-card.earned { border-color:var(--gp-success); background:color-mix(in srgb,var(--gp-success) 7%,var(--gp-surface-muted)) }.milestone-header { display:flex; align-items:flex-start; justify-content:space-between; gap:var(--gp-spacing-sm); margin-bottom:var(--gp-spacing-md) }.badge-icon { font-size:2rem; line-height:1 }.milestone-status { padding:.25rem .5rem; border-radius:999px; background:var(--gp-surface-card); color:var(--gp-text-secondary); font-size:.7rem; font-weight:700; text-transform:uppercase }.earned .milestone-status { background:var(--gp-success-soft); color:var(--gp-success-text) }.milestone-card h4 { margin:0 0 var(--gp-spacing-xs); color:var(--gp-text-primary); font-size:1rem }.milestone-card p { min-height:2.5em; margin:0; color:var(--gp-text-secondary); font-size:.84rem; line-height:1.4 }.progress-bar { height:.45rem; overflow:hidden; margin-top:var(--gp-spacing-md); border-radius:999px; background:var(--gp-border-subtle) }.progress-bar span { display:block; height:100%; border-radius:inherit; background:var(--gp-primary) }.city-icon { display:grid!important; place-items:center; width:24px; height:16px; flex-shrink:0; border-radius:var(--gp-radius-small); background:var(--gp-timeline-blue); font-size:.75rem }.achievement-groups { display:grid; gap:var(--gp-spacing-xl) }.achievement-group-title { display:flex; align-items:center; justify-content:space-between; gap:var(--gp-spacing-md); margin:0 0 var(--gp-spacing-md); padding-bottom:var(--gp-spacing-sm); border-bottom:1px solid var(--gp-border); color:var(--gp-text-primary); font-size:1rem }.achievement-group-title small { color:var(--gp-text-secondary); font-size:.78rem; font-weight:600 }.milestone-card h5 { margin:0 0 var(--gp-spacing-xs); color:var(--gp-text-primary); font-size:1rem }.milestone-card.earned { border-color:var(--gp-warning); background:color-mix(in srgb,var(--gp-warning) 10%,var(--gp-surface-muted)); box-shadow:0 0 1.25rem color-mix(in srgb,var(--gp-warning) 22%,transparent) }.earned .milestone-status { background:color-mix(in srgb,var(--gp-warning) 18%,var(--gp-surface-muted)); color:var(--gp-warning) }.progress-bar { height:.65rem; background:color-mix(in srgb,var(--gp-primary) 18%,var(--gp-border-subtle)) }.progress-bar span { background:linear-gradient(90deg,var(--gp-primary),var(--gp-secondary)) }
@media (max-width:960px) { .movement-grid,.patterns-grid { grid-template-columns:repeat(2,minmax(0,1fr)) } }@media (max-width:720px) { .journey-hero { padding:1.35rem }.journey-hero h2 { font-size:2.55rem }.places-grid,.movement-grid,.patterns-grid { grid-template-columns:1fr }.empty-card { padding:var(--gp-spacing-xl) } }@media (prefers-reduced-motion:reduce) { * { transition:none!important } }
</style>
