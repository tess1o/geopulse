<template>
  <section v-if="hasWeatherInsights" class="weather-insights-section">
    <h3 class="weather-insights-title">
      <i class="fas fa-cloud-sun"></i>
      Weather Along the Way
    </h3>

    <div class="weather-insights-grid">
      <article class="weather-insight-card">
        <div class="weather-card-icon hot">
          <i :class="weatherIcon(weather.hottestTemperature)"></i>
        </div>
        <div class="weather-card-content">
          <div class="weather-card-value">{{ formatSampleTemperature(weather.hottestTemperature) }}</div>
          <div class="weather-card-label">Hottest Moment</div>
          <div class="weather-card-detail">{{ formatSampleDate(weather.hottestTemperature) }}</div>
          <div v-if="formatSampleLocation(weather.hottestTemperature)" class="weather-card-detail muted">
            <i class="pi pi-map-marker"></i>
            {{ formatSampleLocation(weather.hottestTemperature) }}
          </div>
        </div>
      </article>

      <article class="weather-insight-card">
        <div class="weather-card-icon cold">
          <i :class="weatherIcon(weather.coldestTemperature)"></i>
        </div>
        <div class="weather-card-content">
          <div class="weather-card-value">{{ formatSampleTemperature(weather.coldestTemperature) }}</div>
          <div class="weather-card-label">Coldest Moment</div>
          <div class="weather-card-detail">{{ formatSampleDate(weather.coldestTemperature) }}</div>
          <div v-if="formatSampleLocation(weather.coldestTemperature)" class="weather-card-detail muted">
            <i class="pi pi-map-marker"></i>
            {{ formatSampleLocation(weather.coldestTemperature) }}
          </div>
        </div>
      </article>

      <article class="weather-insight-card">
        <div class="weather-card-icon wet">
          <i class="fas fa-cloud-rain"></i>
        </div>
        <div class="weather-card-content">
          <div class="weather-card-value">{{ formatWettestDayPrecipitation(weather.wettestDay) }}</div>
          <div class="weather-card-label">Wettest Day</div>
          <div class="weather-card-detail">{{ formatLocalDate(weather.wettestDay?.date) }}</div>
          <div class="weather-card-detail muted">{{ weather.rainySamplesCount || 0 }} rainy samples</div>
        </div>
      </article>

      <article class="weather-insight-card">
        <div class="weather-card-icon common">
          <i :class="dominantWeatherIcon"></i>
        </div>
        <div class="weather-card-content">
          <div class="weather-card-value">{{ weather.dominantCondition?.label || 'Weather' }}</div>
          <div class="weather-card-label">Most Common Weather</div>
          <div class="weather-card-detail">
            {{ weather.dominantCondition?.samplesCount || 0 }} samples &middot; Avg {{ formatAverageTemperature(weather.averageTemperature) }}
          </div>
          <div v-if="weather.windiestSample?.windSpeed != null" class="weather-card-detail muted">
            Max wind {{ formatWeatherWind(weather.windiestSample.windSpeed) }}
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import dayjs from 'dayjs'
import { useTimezone } from '@/composables/useTimezone'
import {
  formatPrecipitation,
  formatTemperature,
  formatWindSpeed,
  getWeatherCodeInfo
} from '@/utils/weatherDisplay'

const DATE_FORMAT_PATTERNS = {
  MDY: 'MM/DD/YYYY',
  DMY: 'DD/MM/YYYY',
  YMD: 'YYYY-MM-DD'
}

const props = defineProps({
  weather: {
    type: Object,
    default: null
  },
  distanceUnit: {
    type: String,
    default: 'KILOMETERS'
  },
  temperatureUnit: {
    type: String,
    default: 'CELSIUS'
  }
})

const timezone = useTimezone()

const hasWeatherInsights = computed(() => Number(props.weather?.samplesCount || 0) > 0)
const normalizedDistanceUnit = computed(() => props.distanceUnit === 'MILES' ? 'MILES' : 'KILOMETERS')
const normalizedTemperatureUnit = computed(() => props.temperatureUnit === 'FAHRENHEIT' ? 'FAHRENHEIT' : 'CELSIUS')
const dominantWeatherIcon = computed(() => getWeatherCodeInfo(props.weather?.dominantCondition?.weatherCode).icon)

const weatherIcon = (sample) => getWeatherCodeInfo(sample?.weatherCode).icon

const formatSampleTemperature = (sample) => {
  return formatTemperature(sample?.temperature, normalizedTemperatureUnit.value) || 'N/A'
}

const formatAverageTemperature = (temperature) => {
  return formatTemperature(temperature, normalizedTemperatureUnit.value) || 'N/A'
}

const formatWeatherWind = (windSpeed) => {
  return formatWindSpeed(windSpeed, normalizedDistanceUnit.value) || 'N/A'
}

const formatWettestDayPrecipitation = (wettestDay) => {
  if (!wettestDay || !Number.isFinite(Number(wettestDay.precipitation))) {
    return 'N/A'
  }
  return formatPrecipitation(wettestDay.precipitation, normalizedDistanceUnit.value)
      || (normalizedDistanceUnit.value === 'MILES' ? '0 in' : '0 mm')
}

const formatSampleDate = (sample) => {
  return sample?.observedAt ? timezone.formatDateDisplay(sample.observedAt) : 'Date unavailable'
}

const formatLocalDate = (date) => {
  if (!date) {
    return 'Date unavailable'
  }
  const pattern = DATE_FORMAT_PATTERNS[timezone.getDateFormat()] || DATE_FORMAT_PATTERNS.MDY
  return dayjs(date).format(pattern)
}

const formatSampleLocation = (sample) => {
  const latitude = Number(sample?.latitude)
  const longitude = Number(sample?.longitude)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null
  }
  return `${latitude.toFixed(4)}, ${longitude.toFixed(4)}`
}
</script>

<style scoped>
.weather-insights-section { margin-bottom:var(--gp-spacing-xl) }.weather-insights-title { display:flex; align-items:center; gap:.45rem; margin:0 0 var(--gp-spacing-lg); color:var(--gp-text-primary); font-size:1.25rem }.weather-insights-title i { color:var(--gp-primary) }.weather-insights-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:var(--gp-spacing-md) }.weather-insight-card { display:flex; gap:var(--gp-spacing-md); align-items:center; min-width:0; padding:var(--gp-spacing-lg); border:1px solid var(--gp-border-light); border-radius:14px; background:var(--gp-surface-light); transition:border-color .2s ease,background .2s ease,transform .2s ease }.weather-insight-card:hover { border-color:var(--gp-primary); background:color-mix(in srgb,var(--gp-primary) 8%,var(--gp-surface-light)); transform:translateY(-1px) }.weather-card-icon { display:grid; place-items:center; width:2.6rem; height:2.6rem; flex-shrink:0; border-radius:10px; background:var(--gp-surface-white); color:var(--gp-primary); font-size:1.35rem }.weather-card-icon.hot { color:#f59e0b }.weather-card-icon.cold { color:#38bdf8 }.weather-card-icon.wet { color:#2563eb }.weather-card-icon.common { color:#0891b2 }.weather-card-content { min-width:0 }.weather-card-value { margin:0 0 .28rem; overflow-wrap:anywhere; color:var(--gp-text-primary); font-size:1.15rem; font-weight:800; line-height:1.25 }.weather-card-label { margin-bottom:.25rem; color:var(--gp-text-secondary); font-size:.74rem; font-weight:700; letter-spacing:.06em; text-transform:uppercase }.weather-card-detail { color:var(--gp-secondary); font-size:.78rem; font-weight:600; line-height:1.35 }.weather-card-detail.muted { display:flex; align-items:center; gap:var(--gp-spacing-xs); color:var(--gp-text-muted) }.p-dark .weather-card-icon { background:var(--gp-surface-darker) }@media (max-width:960px) { .weather-insights-grid { grid-template-columns:repeat(2,minmax(0,1fr)) } }@media (max-width:720px) { .weather-insights-grid { grid-template-columns:1fr }.weather-insight-card { padding:var(--gp-spacing-md) } }
</style>
