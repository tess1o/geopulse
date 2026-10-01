<template>
  <section v-if="hasWeatherInsights" class="weather-insights-section">
    <h3 class="weather-insights-title">
      <i class="fas fa-cloud-sun"></i>
      {{ t('weather.insights.title') }}
    </h3>

    <div class="weather-insights-grid">
      <article class="weather-insight-card">
        <div class="weather-card-icon hot">
          <i :class="weatherIcon(weather.hottestTemperature)"></i>
        </div>
        <div class="weather-card-content">
          <div class="weather-card-value">{{ formatSampleTemperature(weather.hottestTemperature) }}</div>
          <div class="weather-card-label">{{ t('weather.insights.hottestMoment') }}</div>
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
          <div class="weather-card-label">{{ t('weather.insights.coldestMoment') }}</div>
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
          <div class="weather-card-label">{{ t('weather.insights.wettestDay') }}</div>
          <div class="weather-card-detail">{{ formatLocalDate(weather.wettestDay?.date) }}</div>
          <div class="weather-card-detail muted">{{ t('weather.insights.rainySamples', { count: rainySamplesCount }, rainySamplesCount) }}</div>
        </div>
      </article>

      <article class="weather-insight-card">
        <div class="weather-card-icon common">
          <i :class="dominantWeatherIcon"></i>
        </div>
        <div class="weather-card-content">
          <div class="weather-card-value">{{ dominantConditionText }}</div>
          <div class="weather-card-label">{{ t('weather.insights.mostCommon') }}</div>
          <div class="weather-card-detail">
            {{ t('weather.insights.samplesWithAverage', { count: dominantSamplesCount, temperature: formatAverageTemperature(weather.averageTemperature) }, dominantSamplesCount) }}
          </div>
          <div v-if="weather.windiestSample?.windSpeed != null" class="weather-card-detail muted">
            {{ t('weather.insights.maxWind', { speed: formatWeatherWind(weather.windiestSample.windSpeed) }) }}
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import { useTimezone } from '@/composables/useTimezone'
import { te } from '@/locales'
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
const { t } = useI18n()

const hasWeatherInsights = computed(() => Number(props.weather?.samplesCount || 0) > 0)
const normalizedDistanceUnit = computed(() => props.distanceUnit === 'MILES' ? 'MILES' : 'KILOMETERS')
const normalizedTemperatureUnit = computed(() => props.temperatureUnit === 'FAHRENHEIT' ? 'FAHRENHEIT' : 'CELSIUS')
const dominantWeatherIcon = computed(() => getWeatherCodeInfo(props.weather?.dominantCondition?.weatherCode).icon)
const rainySamplesCount = computed(() => Number(props.weather?.rainySamplesCount || 0))
const dominantSamplesCount = computed(() => Number(props.weather?.dominantCondition?.samplesCount || 0))

/**
 * The dominant condition, translated from the locale-neutral `weatherCode`.
 *
 * The backend also sends an English `label`; it is used only when the payload carries no code at all,
 * so a code we recognise always wins. `te()` guards the catalog lookup in the same way it does for
 * backend messages, so an unmapped key degrades to the server's text rather than a dotted key.
 */
const dominantConditionText = computed(() => {
  const condition = props.weather?.dominantCondition
  if (!condition) {
    return t('weather.conditions.unknown')
  }
  if (condition.weatherCode == null) {
    return condition.label || t('weather.conditions.unknown')
  }

  const { key } = getWeatherCodeInfo(condition.weatherCode)
  return te(key) ? t(key) : (condition.label || t('weather.conditions.unknown'))
})

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
  return sample?.observedAt ? timezone.formatDateDisplay(sample.observedAt) : t('weather.insights.dateUnavailable')
}

const formatLocalDate = (date) => {
  if (!date) {
    return t('weather.insights.dateUnavailable')
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
.weather-insights-section { margin-bottom:var(--gp-spacing-xl) }.weather-insights-title { display:flex; align-items:center; gap:.45rem; margin:0 0 var(--gp-spacing-lg); color:var(--gp-text-primary); font-size:1.25rem }.weather-insights-title i { color:var(--gp-primary) }.weather-insights-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:var(--gp-spacing-md) }.weather-insight-card { display:flex; gap:var(--gp-spacing-md); align-items:center; min-width:0; padding:var(--gp-spacing-lg); border:1px solid var(--gp-border); border-radius:14px; background:var(--gp-surface-muted); transition:border-color .2s ease,background .2s ease,transform .2s ease }.weather-insight-card:hover { border-color:var(--gp-primary); background:color-mix(in srgb,var(--gp-primary) 8%,var(--gp-surface-muted)); transform:translateY(-1px) }.weather-card-icon { display:grid; place-items:center; width:2.6rem; height:2.6rem; flex-shrink:0; border-radius:10px; background:var(--gp-surface-card); color:var(--gp-primary); font-size:1.35rem }.weather-card-icon.hot { color:#f59e0b }.weather-card-icon.cold { color:#38bdf8 }.weather-card-icon.wet { color:#2563eb }.weather-card-icon.common { color:#0891b2 }.weather-card-content { min-width:0 }.weather-card-value { margin:0 0 .28rem; overflow-wrap:anywhere; color:var(--gp-text-primary); font-size:1.15rem; font-weight:800; line-height:1.25 }.weather-card-label { margin-bottom:.25rem; color:var(--gp-text-secondary); font-size:.74rem; font-weight:700; letter-spacing:.06em; text-transform:uppercase }.weather-card-detail { color:var(--gp-secondary); font-size:.78rem; font-weight:600; line-height:1.35 }.weather-card-detail.muted { display:flex; align-items:center; gap:var(--gp-spacing-xs); color:var(--gp-text-muted) }@media (max-width:960px) { .weather-insights-grid { grid-template-columns:repeat(2,minmax(0,1fr)) } }@media (max-width:720px) { .weather-insights-grid { grid-template-columns:1fr }.weather-insight-card { padding:var(--gp-spacing-md) } }
</style>
