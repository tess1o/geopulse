import {
  formatObservedTime,
  formatPrecipitation,
  formatTemperature,
  formatWindSpeed,
  getWeatherCodeInfo
} from '@/utils/weatherDisplay'
import { t } from '@/locales'

export const buildWeatherPopupModel = (sample, {
  distanceUnit = 'KILOMETERS',
  temperatureUnit = 'CELSIUS',
  timezone
} = {}) => {
  const info = getWeatherCodeInfo(sample?.weatherCode)
  const conditionLabel = t(info.key)
  const precipitation = formatPrecipitation(sample?.precipitation, distanceUnit)
  const rows = [
    {
      label: t('maps.popups.weather.observed'),
      value: formatObservedTime(sample, timezone)
    },
    {
      label: t('maps.popups.weather.temperature'),
      value: formatTemperature(sample?.temperature, temperatureUnit) || t('maps.popups.common.notAvailable')
    },
    precipitation
      ? {
          label: t('maps.popups.weather.precipitation'),
          value: precipitation
        }
      : null,
    {
      label: t('maps.popups.weather.wind'),
      value: formatWindSpeed(sample?.windSpeed, distanceUnit) || t('maps.popups.common.notAvailable')
    }
  ].filter(Boolean)

  return {
    title: conditionLabel,
    iconClass: info.icon,
    rows,
    variant: 'compact'
  }
}
