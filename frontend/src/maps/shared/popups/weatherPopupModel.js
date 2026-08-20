import {
  formatObservedTime,
  formatPrecipitation,
  formatTemperature,
  formatWindSpeed,
  getWeatherCodeInfo
} from '@/utils/weatherDisplay'

export const buildWeatherPopupModel = (sample, {
  distanceUnit = 'KILOMETERS',
  temperatureUnit = 'CELSIUS',
  timezone
} = {}) => {
  const info = getWeatherCodeInfo(sample?.weatherCode)
  const precipitation = formatPrecipitation(sample?.precipitation, distanceUnit)
  const rows = [
    {
      label: 'Observed',
      value: formatObservedTime(sample, timezone)
    },
    {
      label: 'Temperature',
      value: formatTemperature(sample?.temperature, temperatureUnit) || 'n/a'
    },
    precipitation
      ? {
          label: 'Precipitation',
          value: precipitation
        }
      : null,
    {
      label: 'Wind',
      value: formatWindSpeed(sample?.windSpeed, distanceUnit) || 'n/a'
    }
  ].filter(Boolean)

  return {
    title: info.label,
    iconClass: info.icon,
    rows,
    variant: 'compact'
  }
}
