const WEATHER_CODE_INFO = [
  { codes: [0], label: 'Clear', icon: 'fas fa-sun', severity: 'clear' },
  { codes: [1, 2], label: 'Partly cloudy', icon: 'fas fa-cloud-sun', severity: 'cloud' },
  { codes: [3], label: 'Cloudy', icon: 'fas fa-cloud', severity: 'cloud' },
  { codes: [45, 48], label: 'Fog', icon: 'fas fa-smog', severity: 'fog' },
  { codes: [51, 53, 55, 56, 57], label: 'Drizzle', icon: 'fas fa-cloud-rain', severity: 'rain' },
  { codes: [61, 63, 65, 66, 67], label: 'Rain', icon: 'fas fa-cloud-showers-heavy', severity: 'rain' },
  { codes: [71, 73, 75, 77], label: 'Snow', icon: 'fas fa-snowflake', severity: 'snow' },
  { codes: [80, 81, 82], label: 'Rain showers', icon: 'fas fa-cloud-showers-heavy', severity: 'rain' },
  { codes: [85, 86], label: 'Snow showers', icon: 'fas fa-snowflake', severity: 'snow' },
  { codes: [95, 96, 99], label: 'Storm', icon: 'fas fa-cloud-bolt', severity: 'storm' }
]

const DEFAULT_CODE_INFO = { label: 'Weather', icon: 'fas fa-cloud', severity: 'cloud' }
const HOURLY_SAMPLE_TOLERANCE_MS = 60 * 60 * 1000
const STAY_WEATHER_RADIUS_KM = 25

export function getWeatherCodeInfo(code) {
  const numericCode = Number(code)
  return WEATHER_CODE_INFO.find(entry => entry.codes.includes(numericCode)) || DEFAULT_CODE_INFO
}

export function summarizeWeatherSamples(samples = []) {
  const cleanSamples = samples.filter(sample => sample && sample.observedAt)
  if (cleanSamples.length === 0) {
    return null
  }

  const temperatures = cleanSamples.map(sample => Number(sample.temperature)).filter(Number.isFinite)
  const precipitation = cleanSamples.map(sample => Number(sample.precipitation)).filter(Number.isFinite)
  const winds = cleanSamples.map(sample => Number(sample.windSpeed)).filter(Number.isFinite)
  const codes = cleanSamples.map(sample => Number(sample.weatherCode)).filter(Number.isFinite)
  const code = dominantWeatherCode(codes)
  const codeInfo = getWeatherCodeInfo(code)

  return {
    sampleCount: cleanSamples.length,
    weatherCode: code,
    condition: codeInfo.label,
    icon: codeInfo.icon,
    severity: codeInfo.severity,
    avgTemperature: average(temperatures),
    minTemperature: minOrNull(temperatures),
    maxTemperature: maxOrNull(temperatures),
    precipitationTotal: sum(precipitation),
    maxWindSpeed: maxOrNull(winds),
    samples: cleanSamples
  }
}

export function getWeatherSamplesForTimelineItem(item, samples = []) {
  if (!item || !Array.isArray(samples) || samples.length === 0) {
    return []
  }

  const window = getTimelineItemWindow(item)
  if (!window) {
    return []
  }

  const candidates = samples
    .map(sample => ({
      sample,
      observedMs: sample?.observedAt ? new Date(sample.observedAt).getTime() : NaN
    }))
    .filter(candidate => (
      Number.isFinite(candidate.observedMs) &&
      isWeatherSampleNearTimelineItem(candidate.sample, item)
    ))

  const exactMatches = candidates
    .filter(candidate => isObservedInsideWindow(candidate.observedMs, window))
    .map(candidate => candidate.sample)

  if (exactMatches.length > 0) {
    return exactMatches
  }

  const fallbackMatches = candidates
    .map(candidate => ({
      ...candidate,
      distanceMs: distanceToWindowMs(candidate.observedMs, window)
    }))
    .filter(candidate => candidate.distanceMs <= HOURLY_SAMPLE_TOLERANCE_MS)
    .sort((left, right) => left.distanceMs - right.distanceMs || left.observedMs - right.observedMs)

  if (fallbackMatches.length === 0) {
    return []
  }

  const nearestObservedMs = fallbackMatches[0].observedMs
  return fallbackMatches
    .filter(candidate => candidate.observedMs === nearestObservedMs)
    .map(candidate => candidate.sample)
}

export function isWeatherSampleInTimelineItem(sample, item, itemWindow = null) {
  const window = itemWindow || getTimelineItemWindow(item)
  if (!sample || !window) {
    return false
  }

  const observedMs = sample?.observedAt ? new Date(sample.observedAt).getTime() : NaN
  if (!Number.isFinite(observedMs) || distanceToWindowMs(observedMs, window) > HOURLY_SAMPLE_TOLERANCE_MS) {
    return false
  }

  return isWeatherSampleNearTimelineItem(sample, item)
}

function isWeatherSampleNearTimelineItem(sample, item) {
  if (item.type === 'stay') {
    const itemLat = Number(item.latitude)
    const itemLon = Number(item.longitude)
    const sampleLat = Number(sample.latitude)
    const sampleLon = Number(sample.longitude)
    if (![itemLat, itemLon, sampleLat, sampleLon].every(Number.isFinite)) {
      return true
    }
    return haversineKm(itemLat, itemLon, sampleLat, sampleLon) <= STAY_WEATHER_RADIUS_KM
  }

  return item.type === 'trip'
}

export function formatTemperature(value, measureUnit = 'METRIC') {
  if (!Number.isFinite(Number(value))) {
    return null
  }
  const numeric = Number(value)
  if (measureUnit === 'IMPERIAL') {
    return `${Math.round((numeric * 9 / 5) + 32)}°F`
  }
  return `${Math.round(numeric)}°C`
}

export function formatWindSpeed(value, measureUnit = 'METRIC') {
  if (!Number.isFinite(Number(value))) {
    return null
  }
  const numeric = Number(value)
  if (measureUnit === 'IMPERIAL') {
    return `${Math.round(numeric * 0.621371)} mph`
  }
  return `${Math.round(numeric)} km/h`
}

export function formatPrecipitation(value, measureUnit = 'METRIC') {
  if (!Number.isFinite(Number(value))) {
    return null
  }
  const numeric = Number(value)
  if (numeric <= 0) {
    return null
  }
  if (measureUnit === 'IMPERIAL') {
    const inches = numeric / 25.4
    if (inches < 0.01) {
      return '<0.01 in'
    }
    return `${inches.toFixed(inches >= 0.1 ? 1 : 2)} in`
  }
  if (numeric < 0.1) {
    return '<0.1 mm'
  }
  return `${numeric.toFixed(numeric >= 10 ? 0 : 1)} mm`
}

export function formatObservedTime(sample, timezone) {
  if (!sample?.observedAt) {
    return ''
  }
  if (timezone?.formatDateDisplay && timezone?.formatTime) {
    return `${timezone.formatDateDisplay(sample.observedAt)} ${timezone.formatTime(sample.observedAt)}`
  }
  return new Date(sample.observedAt).toLocaleString()
}

export function buildWeatherSampleTitle(sample, measureUnit = 'METRIC', timezone = null) {
  const info = getWeatherCodeInfo(sample?.weatherCode)
  const precipitation = formatPrecipitation(sample?.precipitation, measureUnit)
  const parts = [
    formatObservedTime(sample, timezone),
    info.label,
    formatTemperature(sample?.temperature, measureUnit),
    precipitation ? `precip ${precipitation}` : null,
    sample?.windSpeed != null ? `wind ${formatWindSpeed(sample.windSpeed, measureUnit)}` : null
  ].filter(Boolean)
  return parts.join(' · ')
}

function getTimelineItemWindow(item) {
  const start = item.timestamp || item.startTime
  if (!start) {
    return null
  }
  const startMs = new Date(start).getTime()
  if (!Number.isFinite(startMs)) {
    return null
  }

  const durationSeconds = Number(item.stayDuration ?? item.tripDuration ?? 0)
  const endMs = durationSeconds > 0
    ? startMs + durationSeconds * 1000
    : startMs + 60 * 60 * 1000

  return { startMs, endMs }
}

function isObservedInsideWindow(observedMs, window) {
  return observedMs >= window.startMs && observedMs <= window.endMs
}

function distanceToWindowMs(observedMs, window) {
  if (isObservedInsideWindow(observedMs, window)) {
    return 0
  }
  return Math.min(Math.abs(observedMs - window.startMs), Math.abs(observedMs - window.endMs))
}

function dominantWeatherCode(codes) {
  if (codes.length === 0) {
    return null
  }
  const counts = new Map()
  codes.forEach(code => counts.set(code, (counts.get(code) || 0) + 1))
  return [...counts.entries()]
    .sort((left, right) => right[1] - left[1] || right[0] - left[0])[0][0]
}

function average(values) {
  return values.length === 0 ? null : sum(values) / values.length
}

function sum(values) {
  return values.reduce((total, value) => total + value, 0)
}

function minOrNull(values) {
  return values.length === 0 ? null : Math.min(...values)
}

function maxOrNull(values) {
  return values.length === 0 ? null : Math.max(...values)
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const radiusKm = 6371
  const dLat = toRadians(lat2 - lat1)
  const dLon = toRadians(lon2 - lon1)
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) *
    Math.sin(dLon / 2) ** 2
  return 2 * radiusKm * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

function toRadians(value) {
  return value * Math.PI / 180
}
