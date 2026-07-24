<template>
  <section v-if="hasWeatherInsights" class="weather-insights-section">
    <h2 class="weather-insights-title">
      <i class="fas fa-cloud-sun"></i>
      Weather Along the Way
    </h2>

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
  measureUnit: {
    type: String,
    default: 'METRIC'
  }
})

const timezone = useTimezone()

const hasWeatherInsights = computed(() => Number(props.weather?.samplesCount || 0) > 0)
const normalizedMeasureUnit = computed(() => props.measureUnit === 'IMPERIAL' ? 'IMPERIAL' : 'METRIC')
const dominantWeatherIcon = computed(() => getWeatherCodeInfo(props.weather?.dominantCondition?.weatherCode).icon)

const weatherIcon = (sample) => getWeatherCodeInfo(sample?.weatherCode).icon

const formatSampleTemperature = (sample) => {
  return formatTemperature(sample?.temperature, normalizedMeasureUnit.value) || 'N/A'
}

const formatAverageTemperature = (temperature) => {
  return formatTemperature(temperature, normalizedMeasureUnit.value) || 'N/A'
}

const formatWeatherWind = (windSpeed) => {
  return formatWindSpeed(windSpeed, normalizedMeasureUnit.value) || 'N/A'
}

const formatWettestDayPrecipitation = (wettestDay) => {
  if (!wettestDay || !Number.isFinite(Number(wettestDay.precipitation))) {
    return 'N/A'
  }
  return formatPrecipitation(wettestDay.precipitation, normalizedMeasureUnit.value)
      || (normalizedMeasureUnit.value === 'IMPERIAL' ? '0 in' : '0 mm')
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
.weather-insights-section {
  margin-bottom: var(--gp-spacing-xxl, 3rem);
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.weather-insights-title {
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

.weather-insights-title i {
  color: var(--gp-primary);
  font-size: 1.25rem;
}

.weather-insights-grid {
  display: grid;
  gap: var(--gp-spacing-xl);
  width: 100%;
  max-width: 1040px;
  margin: 0 auto;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.weather-insight-card {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-lg);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
  min-width: 0;
}

.weather-insight-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-card-hover);
  border-color: var(--gp-primary);
}

.weather-card-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--gp-radius-medium);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--gp-primary);
  background: var(--gp-surface-light);
  font-size: 1.65rem;
}

.weather-card-icon.hot {
  color: #f59e0b;
}

.weather-card-icon.cold {
  color: #38bdf8;
}

.weather-card-icon.wet {
  color: #2563eb;
}

.weather-card-icon.common {
  color: #0891b2;
}

.weather-card-content {
  flex: 1;
  min-width: 0;
}

.weather-card-value {
  color: var(--gp-text-primary);
  font-size: 1.5rem;
  font-weight: 800;
  line-height: 1.15;
  margin-bottom: var(--gp-spacing-xs);
  overflow-wrap: anywhere;
}

.weather-card-label {
  color: var(--gp-text-secondary);
  font-size: 0.95rem;
  font-weight: 650;
  margin-bottom: var(--gp-spacing-xs);
}

.weather-card-detail {
  color: var(--gp-secondary);
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.35;
}

.weather-card-detail.muted {
  color: var(--gp-text-muted);
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

@media (max-width: 768px) {
  .weather-insights-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-md);
  }

  .weather-insight-card {
    align-items: flex-start;
    padding: var(--gp-spacing-md);
  }

  .weather-card-icon {
    width: 44px;
    height: 44px;
    font-size: 1.25rem;
  }

  .weather-card-value {
    font-size: 1.25rem;
  }
}
</style>
