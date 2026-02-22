export const LOCATION_SOURCE_OPTIONS = Object.freeze([
  {
    value: 'OWNTRACKS',
    label: 'OwnTracks',
    description: 'Open-source location tracking with HTTP or MQTT connections',
    icon: 'pi pi-mobile'
  },
  {
    value: 'GPSLOGGER',
    label: 'GPSLogger',
    description: 'Android GPSLogger app via HTTP + Basic Auth (OwnTracks-compatible payload)',
    icon: 'pi pi-compass'
  },
  {
    value: 'OVERLAND',
    label: 'Overland',
    description: 'Simple HTTP endpoint with token-based authentication',
    icon: 'pi pi-map'
  },
  {
    value: 'DAWARICH',
    label: 'Dawarich',
    description: 'Privacy-focused location tracking with API key authentication',
    icon: 'pi pi-key'
  },
  {
    value: 'HOME_ASSISTANT',
    label: 'Home Assistant',
    description: 'Integrate with Home Assistant automation for automatic location tracking',
    icon: 'pi pi-home'
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
    description: '',
    icon: 'pi pi-question'
  }
}

export const getLocationSourceIcon = (type) => getLocationSourceMeta(type).icon

export const getLocationSourceDisplayName = (type) => getLocationSourceMeta(type).label

export const getLocationSourceIdentifier = (source) => {
  if (!source) return ''

  if (source.type === 'OWNTRACKS' || source.type === 'GPSLOGGER') {
    return source.username || 'No username'
  }
  if (source.type === 'OVERLAND') {
    return source.token ? `Token: ${source.token.substring(0, 8)}...` : 'No token'
  }
  if (source.type === 'DAWARICH') {
    return source.token ? `API Key: ${source.token.substring(0, 8)}...` : 'No API key'
  }
  if (source.type === 'HOME_ASSISTANT') {
    return source.token ? `Token: ${source.token.substring(0, 8)}...` : 'No token'
  }

  return `Unknown type: ${source.type}`
}
