import { t } from '@/locales'

/**
 * Brand names (label) are never translated. `descriptionKey` resolves through the `locationSources.meta.*`
 * catalog; consumers render `t(option.descriptionKey)` (mirrors the `labelKey` pattern used by
 * `movementTypeOptions`/`segmentTypeOptions` elsewhere in the app) rather than a static English string,
 * so descriptions stay reactive to a locale change.
 */
export const LOCATION_SOURCE_OPTIONS = Object.freeze([
  {
    value: 'OWNTRACKS',
    label: 'OwnTracks',
    descriptionKey: 'locationSources.meta.descriptionOwntracks',
    icon: 'pi pi-mobile'
  },
  {
    value: 'GPSLOGGER',
    label: 'GPSLogger',
    descriptionKey: 'locationSources.meta.descriptionGpslogger',
    icon: 'pi pi-compass'
  },
  {
    value: 'OVERLAND',
    label: 'Overland',
    descriptionKey: 'locationSources.meta.descriptionOverland',
    icon: 'pi pi-map'
  },
  {
    value: 'TRACCAR',
    label: 'Traccar',
    descriptionKey: 'locationSources.meta.descriptionTraccar',
    icon: 'pi pi-car'
  },
  {
    value: 'DAWARICH',
    label: 'Dawarich',
    descriptionKey: 'locationSources.meta.descriptionDawarich',
    icon: 'pi pi-key'
  },
  {
    value: 'HOME_ASSISTANT',
    label: 'Home Assistant',
    descriptionKey: 'locationSources.meta.descriptionHomeAssistant',
    icon: 'pi pi-home'
  },
  {
    value: 'COLOTA',
    label: 'Colota',
    descriptionKey: 'locationSources.meta.descriptionColota',
    icon: 'pi pi-map-marker'
  }
])

const LOCATION_SOURCE_META_BY_TYPE = Object.freeze(
  LOCATION_SOURCE_OPTIONS.reduce((acc, option) => {
    acc[option.value] = option
    return acc
  }, {})
)

export const getLocationSourceMeta = (type) => {
  return LOCATION_SOURCE_META_BY_TYPE[type] || {
    value: type,
    label: type,
    descriptionKey: null,
    icon: 'pi pi-question'
  }
}

export const getLocationSourceIcon = (type) => getLocationSourceMeta(type).icon

export const getLocationSourceDisplayName = (type) => getLocationSourceMeta(type).label

/** Resolves `descriptionKey` through the plain (non-composable) `t`, for non-component callers. */
export const getLocationSourceDescription = (type) => {
  const meta = getLocationSourceMeta(type)
  return meta.descriptionKey ? t(meta.descriptionKey) : ''
}

export const getLocationSourceIdentifier = (source) => {
  if (!source) return ''

  if (source.type === 'OWNTRACKS' || source.type === 'GPSLOGGER' || source.type === 'COLOTA') {
    return source.username || t('locationSources.meta.noUsername')
  }
  if (source.type === 'OVERLAND') {
    return source.token ? t('locationSources.meta.tokenPrefix', { token: source.token.substring(0, 8) }) : t('locationSources.meta.noToken')
  }
  if (source.type === 'TRACCAR') {
    const deviceLabel = source.deviceId ? t('locationSources.meta.devicePrefix', { deviceId: source.deviceId }) : t('locationSources.meta.allDevices')
    const tokenLabel = source.token ? t('locationSources.meta.tokenPrefix', { token: source.token.substring(0, 8) }) : t('locationSources.meta.noToken')
    return `${deviceLabel} • ${tokenLabel}`
  }
  if (source.type === 'DAWARICH') {
    return source.token ? t('locationSources.meta.apiKeyPrefix', { token: source.token.substring(0, 8) }) : t('locationSources.meta.noApiKey')
  }
  if (source.type === 'HOME_ASSISTANT') {
    return source.token ? t('locationSources.meta.tokenPrefix', { token: source.token.substring(0, 8) }) : t('locationSources.meta.noToken')
  }

  return t('locationSources.meta.unknownType', { type: source.type })
}
