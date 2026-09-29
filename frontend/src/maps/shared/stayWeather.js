import { t } from '@/locales'
import {
  formatPrecipitation,
  formatTemperature,
  formatWindSpeed,
  getWeatherSamplesForTimelineItem,
  summarizeWeatherSamples
} from '@/utils/weatherDisplay'

/**
 * The backend samples a stay's weather at the stay's own coordinates (see
 * WeatherSamplingPolicy.forStay), so those samples would always render right
 * on top of the stay marker. Instead they are claimed by the stay and shown
 * as a badge on its marker; only the remaining (trip) samples stay on the
 * weather layer.
 *
 * Returns { weatherByTimelineIndex: Map<index, samples[]>, remainingSamples }.
 * Stays are matched exactly like the timeline sidebar does, so the badge and
 * the sidebar card always describe the same samples.
 */
export const partitionWeatherSamplesByStay = (timelineItems, samples) => {
  const weatherByTimelineIndex = new Map()
  if (!Array.isArray(samples) || samples.length === 0) {
    return { weatherByTimelineIndex, remainingSamples: [] }
  }

  const claimed = new Set()
  ;(Array.isArray(timelineItems) ? timelineItems : []).forEach((item, index) => {
    if (item?.type !== 'stay') {
      return
    }

    const matched = getWeatherSamplesForTimelineItem(item, samples)
    if (matched.length > 0) {
      weatherByTimelineIndex.set(index, matched)
      matched.forEach((sample) => claimed.add(sample))
    }
  })

  return {
    weatherByTimelineIndex,
    remainingSamples: samples.filter((sample) => !claimed.has(sample))
  }
}

/**
 * Display strings for a summarizeWeatherSamples() result, formatted the same
 * way as the timeline sidebar's weather summary.
 */
export const describeWeatherSummary = (summary, {
  temperatureUnit = 'CELSIUS',
  distanceUnit = 'KILOMETERS'
} = {}) => {
  if (!summary) {
    return null
  }

  const temperatureText = formatTemperature(summary.avgTemperature, temperatureUnit)
  const min = formatTemperature(summary.minTemperature, temperatureUnit)
  const max = formatTemperature(summary.maxTemperature, temperatureUnit)
  const rangeText = summary.sampleCount > 1 && min && max && min !== max ? `${min}-${max}` : ''

  return {
    icon: summary.icon,
    severity: summary.severity,
    conditionLabel: summary.conditionKey ? t(summary.conditionKey) : summary.condition,
    temperatureText: temperatureText || '',
    rangeText,
    precipitationText: formatPrecipitation(summary.precipitationTotal, distanceUnit) || '',
    windText: formatWindSpeed(summary.maxWindSpeed, distanceUnit) || ''
  }
}

export const buildStayWeatherDisplay = (samples, units = {}) => (
  describeWeatherSummary(summarizeWeatherSamples(samples || []), units)
)

/**
 * Weather display for a timeline layer item (annotated with __timelineIndex),
 * given the partition's weatherByTimelineIndex map; null when it has none.
 */
export const getTimelineItemWeatherDisplay = (weatherByTimelineIndex, item, units) => {
  const samples = weatherByTimelineIndex?.get?.(item?.__timelineIndex)
  return samples?.length ? buildStayWeatherDisplay(samples, units) : null
}

/** One-line text, e.g. "Rain · 12°C · range 9°C-14°C". */
export const formatWeatherDisplayTitle = (display) => [
  display.conditionLabel,
  display.temperatureText,
  display.rangeText ? t('weather.summary.titleRange', { range: display.rangeText }) : null,
  display.precipitationText ? t('weather.summary.titlePrecipitation', { precipitation: display.precipitationText }) : null
].filter(Boolean).join(' · ')

/** Popup section (MapInfoPopup `sections` shape) for a stay's weather. */
export const buildWeatherPopupSection = (display) => ({
  key: 'weather',
  title: display.conditionLabel,
  rows: [
    {
      label: t('weather.summary.temperatureLabel'),
      value: display.temperatureText || t('weather.summary.notAvailable')
    },
    display.rangeText ? { label: t('weather.summary.rangeLabel'), value: display.rangeText } : null,
    display.precipitationText ? { label: t('weather.summary.precipitationLabel'), value: display.precipitationText } : null,
    { label: t('weather.summary.windLabel'), value: display.windText || t('weather.summary.notAvailable') }
  ].filter(Boolean)
})

/**
 * Small badge tucked into the stay marker's bottom-right corner. It stays
 * inside the marker's collision footprint, so it never adds a new overlap.
 * Clicks bubble to the marker, which opens the popup with full details.
 */
export const createStayWeatherBadgeElement = (display) => {
  const badge = document.createElement('span')
  badge.className = `gp-stay-weather-badge gp-stay-weather-badge--${display.severity || 'cloud'}`
  badge.title = formatWeatherDisplayTitle(display)
  badge.setAttribute('aria-label', badge.title)

  const icon = document.createElement('i')
  icon.className = display.icon
  icon.setAttribute('aria-hidden', 'true')
  badge.appendChild(icon)
  return badge
}
