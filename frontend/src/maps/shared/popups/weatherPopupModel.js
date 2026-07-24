import {
  formatObservedTime,
  formatPrecipitation,
  formatTemperature,
  formatWindSpeed,
  getWeatherCodeInfo
} from '@/utils/weatherDisplay'

export const buildWeatherPopupModel = (sample, { unit = 'METRIC', timezone } = {}) => {
  const info = getWeatherCodeInfo(sample?.weatherCode)
  const precipitation = formatPrecipitation(sample?.precipitation, unit)
  const rows = [
    {
      label: 'Observed',
      value: formatObservedTime(sample, timezone)
    },
    {
      label: 'Temperature',
      value: formatTemperature(sample?.temperature, unit) || 'n/a'
    },
    precipitation
      ? {
          label: 'Precipitation',
          value: precipitation
        }
      : null,
    {
      label: 'Wind',
      value: formatWindSpeed(sample?.windSpeed, unit) || 'n/a'
    }
  ].filter(Boolean)

  return {
    title: info.label,
    iconClass: info.icon,
    rows,
    variant: 'compact'
  }
}
