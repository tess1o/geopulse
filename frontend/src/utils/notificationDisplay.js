const NOTIFICATION_SOURCE_CONFIG = {
  GEOFENCE: {
    sourceLabel: 'Geofence',
    icon: 'pi pi-map-marker',
    severity: 'info',
    route: '/app/geofences?tab=events',
    actionLabel: 'Open Geofence Events'
  },
  TIMELINE: {
    sourceLabel: 'Timeline',
    icon: 'pi pi-history',
    severity: 'info',
    route: '/app/timeline/jobs',
    actionLabel: 'View Timeline Status'
  },
  IMPORT: {
    sourceLabel: 'Import',
    icon: 'pi pi-upload',
    severity: 'success',
    route: '/app/data-export-import?tab=import',
    actionLabel: 'Open Imports'
  },
  EXPORT: {
    sourceLabel: 'Export',
    icon: 'pi pi-download',
    severity: 'success',
    route: '/app/data-export-import?tab=export',
    actionLabel: 'Open Exports'
  },
  FRIEND_INVITE: {
    sourceLabel: 'Friends',
    icon: 'pi pi-users',
    severity: 'secondary',
    route: '/app/friends/live',
    actionLabel: 'Open Friends'
  },
  WEATHER: {
    sourceLabel: 'Weather',
    icon: 'pi pi-cloud',
    severity: 'warn',
    route: '/app/admin/dashboard',
    actionLabel: 'Open Admin Dashboard'
  }
}

const NOTIFICATION_TYPE_CONFIG = {
  GEOFENCE_ENTER: {
    typeLabel: 'Geofence enter',
    route: '/app/geofences?tab=events',
    actionLabel: 'Open Geofence Events'
  },
  GEOFENCE_LEAVE: {
    typeLabel: 'Geofence leave',
    route: '/app/geofences?tab=events',
    actionLabel: 'Open Geofence Events'
  },
  TIMELINE_REGENERATION_REQUIRED: {
    title: 'Timeline refresh scheduled',
    typeLabel: 'Timeline refresh scheduled',
    icon: 'pi pi-refresh',
    severity: 'info',
    route: '/app/timeline/jobs',
    actionLabel: 'View Timeline Status'
  },
  IMPORT_COMPLETED: {
    typeLabel: 'Import completed',
    severity: 'success',
    route: '/app/data-export-import?tab=import',
    actionLabel: 'Open Imports'
  },
  IMPORT_FAILED: {
    typeLabel: 'Import failed',
    severity: 'danger',
    route: '/app/data-export-import?tab=import',
    actionLabel: 'Open Imports'
  },
  EXPORT_COMPLETED: {
    typeLabel: 'Export completed',
    severity: 'success',
    route: '/app/data-export-import?tab=export',
    actionLabel: 'Open Exports'
  },
  EXPORT_FAILED: {
    typeLabel: 'Export failed',
    severity: 'danger',
    route: '/app/data-export-import?tab=export',
    actionLabel: 'Open Exports'
  },
  FRIEND_INVITE_RECEIVED: {
    typeLabel: 'Friend invitation',
    route: '/app/friends/invites',
    actionLabel: 'Open Invitations'
  },
  FRIEND_INVITE_ACCEPTED: {
    typeLabel: 'Friend accepted',
    route: '/app/friends/live',
    actionLabel: 'Open Friends'
  },
  WEATHER_QUOTA_REACHED: {
    typeLabel: 'Weather quota reached',
    icon: 'pi pi-exclamation-triangle',
    severity: 'warn',
    route: '/app/admin/dashboard',
    actionLabel: 'Open Admin Dashboard'
  },
  WEATHER_QUOTA_RESTORED: {
    typeLabel: 'Weather quota restored',
    icon: 'pi pi-check-circle',
    severity: 'success',
    route: '/app/admin/dashboard',
    actionLabel: 'Open Admin Dashboard'
  }
}

const TARGET_ROUTE_CONFIG = [
  {
    prefix: '/app/timeline/jobs',
    actionLabel: 'View Timeline Status'
  },
  {
    prefix: '/app/geofences',
    actionLabel: 'Open Geofence Events'
  },
  {
    prefix: '/app/data-export-import?tab=import',
    actionLabel: 'Open Imports'
  },
  {
    prefix: '/app/data-export-import?tab=export',
    actionLabel: 'Open Exports'
  },
  {
    prefix: '/app/friends/invites',
    actionLabel: 'Open Invitations'
  },
  {
    prefix: '/app/friends',
    actionLabel: 'Open Friends'
  },
  {
    prefix: '/app/admin/dashboard',
    actionLabel: 'Open Admin Dashboard'
  }
]

const humanizeToken = (value, fallback = '') => {
  if (!value) {
    return fallback
  }
  return String(value)
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map(part => `${part.charAt(0).toUpperCase()}${part.slice(1)}`)
    .join(' ')
}

const targetRouteFor = (notification) => {
  const targetRoute = notification?.metadata?.targetRoute
  if (typeof targetRoute === 'string' && targetRoute.startsWith('/app/')) {
    return targetRoute
  }
  return null
}

const targetRouteConfigFor = (route) => {
  if (!route) {
    return {}
  }
  return TARGET_ROUTE_CONFIG.find(config => route.startsWith(config.prefix)) || {}
}

export const resolveNotificationDisplay = (notification = {}) => {
  const sourceConfig = NOTIFICATION_SOURCE_CONFIG[notification?.source] || {}
  const typeConfig = NOTIFICATION_TYPE_CONFIG[notification?.type] || {}
  const targetRoute = targetRouteFor(notification)
  const targetRouteConfig = targetRouteConfigFor(targetRoute)
  const route = targetRoute || typeConfig.route || sourceConfig.route || '/app/notifications'

  return {
    title: typeConfig.title || notification?.title || typeConfig.typeLabel || humanizeToken(notification?.type, 'Notification'),
    sourceLabel: sourceConfig.sourceLabel || humanizeToken(notification?.source, 'Notification'),
    typeLabel: typeConfig.typeLabel || humanizeToken(notification?.type, 'Notification'),
    icon: typeConfig.icon || sourceConfig.icon || 'pi pi-bell',
    severity: typeConfig.severity || sourceConfig.severity || 'secondary',
    route,
    actionLabel: targetRouteConfig.actionLabel || typeConfig.actionLabel || sourceConfig.actionLabel || 'Open Notification'
  }
}

export const resolveNotificationRoute = (notification = {}) => {
  return resolveNotificationDisplay(notification).route || '/app/notifications'
}
