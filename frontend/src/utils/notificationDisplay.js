import { t } from '@/locales'

const NOTIFICATION_SOURCE_CONFIG = {
  GEOFENCE: {
    sourceLabelKey: 'notifications.display.sources.geofence',
    icon: 'pi pi-map-marker',
    severity: 'info',
    route: '/app/geofences?tab=events',
    actionLabelKey: 'notifications.display.actions.openGeofenceEvents'
  },
  TIMELINE: {
    sourceLabelKey: 'notifications.display.sources.timeline',
    icon: 'pi pi-history',
    severity: 'info',
    route: '/app/timeline/jobs',
    actionLabelKey: 'notifications.display.actions.viewTimelineStatus'
  },
  IMPORT: {
    sourceLabelKey: 'notifications.display.sources.import',
    icon: 'pi pi-upload',
    severity: 'success',
    route: '/app/data-export-import?tab=import',
    actionLabelKey: 'notifications.display.actions.openImports'
  },
  EXPORT: {
    sourceLabelKey: 'notifications.display.sources.export',
    icon: 'pi pi-download',
    severity: 'success',
    route: '/app/data-export-import?tab=export',
    actionLabelKey: 'notifications.display.actions.openExports'
  },
  FRIEND_INVITE: {
    sourceLabelKey: 'notifications.display.sources.friendInvite',
    icon: 'pi pi-users',
    severity: 'secondary',
    route: '/app/friends/live',
    actionLabelKey: 'notifications.display.actions.openFriends'
  },
  WEATHER: {
    sourceLabelKey: 'notifications.display.sources.weather',
    icon: 'pi pi-cloud',
    severity: 'warn',
    route: '/app/admin/dashboard',
    actionLabelKey: 'notifications.display.actions.openAdminDashboard'
  },
  BACKUP_HEALTH: {
    sourceLabelKey: 'notifications.display.sources.backupHealth',
    icon: 'pi pi-database',
    severity: 'warn',
    route: '/app/admin/backups',
    actionLabelKey: 'notifications.display.actions.openBackupSettings'
  },
  GPS_HEALTH: {
    sourceLabelKey: 'notifications.display.sources.gpsHealth',
    icon: 'pi pi-map-marker',
    severity: 'warn',
    route: '/app/notifications',
    actionLabelKey: 'notifications.display.actions.openNotifications'
  },
  PRODUCT: {
    sourceLabelKey: 'notifications.display.sources.product',
    icon: 'pi pi-sparkles',
    severity: 'info',
    route: '/app/notifications',
    actionLabelKey: 'notifications.display.actions.openNotifications'
  },
  REWIND: {
    sourceLabelKey: 'notifications.display.sources.rewind',
    icon: 'pi pi-history',
    severity: 'info',
    route: '/app/rewind',
    actionLabelKey: 'notifications.display.actions.openRewind'
  }
}

const NOTIFICATION_TYPE_CONFIG = {
  GEOFENCE_ENTER: {
    typeLabelKey: 'notifications.display.types.geofenceEnter',
    route: '/app/geofences?tab=events',
    actionLabelKey: 'notifications.display.actions.openGeofenceEvents'
  },
  GEOFENCE_LEAVE: {
    typeLabelKey: 'notifications.display.types.geofenceLeave',
    route: '/app/geofences?tab=events',
    actionLabelKey: 'notifications.display.actions.openGeofenceEvents'
  },
  TIMELINE_REGENERATION_REQUIRED: {
    titleKey: 'notifications.display.types.timelineRegenerationRequired',
    typeLabelKey: 'notifications.display.types.timelineRegenerationRequired',
    icon: 'pi pi-refresh',
    severity: 'info',
    route: '/app/timeline/jobs',
    actionLabelKey: 'notifications.display.actions.viewTimelineStatus'
  },
  IMPORT_COMPLETED: {
    typeLabelKey: 'notifications.display.types.importCompleted',
    severity: 'success',
    route: '/app/data-export-import?tab=import',
    actionLabelKey: 'notifications.display.actions.openImports'
  },
  IMPORT_FAILED: {
    typeLabelKey: 'notifications.display.types.importFailed',
    severity: 'danger',
    route: '/app/data-export-import?tab=import',
    actionLabelKey: 'notifications.display.actions.openImports'
  },
  EXPORT_COMPLETED: {
    typeLabelKey: 'notifications.display.types.exportCompleted',
    severity: 'success',
    route: '/app/data-export-import?tab=export',
    actionLabelKey: 'notifications.display.actions.openExports'
  },
  EXPORT_FAILED: {
    typeLabelKey: 'notifications.display.types.exportFailed',
    severity: 'danger',
    route: '/app/data-export-import?tab=export',
    actionLabelKey: 'notifications.display.actions.openExports'
  },
  FRIEND_INVITE_RECEIVED: {
    typeLabelKey: 'notifications.display.types.friendInviteReceived',
    route: '/app/friends/invites',
    actionLabelKey: 'notifications.display.actions.openInvitations'
  },
  FRIEND_INVITE_ACCEPTED: {
    typeLabelKey: 'notifications.display.types.friendInviteAccepted',
    route: '/app/friends/live',
    actionLabelKey: 'notifications.display.actions.openFriends'
  },
  WEATHER_QUOTA_REACHED: {
    typeLabelKey: 'notifications.display.types.weatherQuotaReached',
    icon: 'pi pi-exclamation-triangle',
    severity: 'warn',
    route: '/app/admin/dashboard',
    actionLabelKey: 'notifications.display.actions.openAdminDashboard'
  },
  WEATHER_QUOTA_RESTORED: {
    typeLabelKey: 'notifications.display.types.weatherQuotaRestored',
    icon: 'pi pi-check-circle',
    severity: 'success',
    route: '/app/admin/dashboard',
    actionLabelKey: 'notifications.display.actions.openAdminDashboard'
  },
  GPS_HEALTH_INCIDENT_OPENED: {
    typeLabelKey: 'notifications.display.types.gpsHealthIncidentOpened', icon: 'pi pi-exclamation-triangle', severity: 'warn', route: '/app/notifications', actionLabelKey: 'notifications.display.actions.openNotifications'
  },
  GPS_HEALTH_INCIDENT_RESOLVED: {
    typeLabelKey: 'notifications.display.types.gpsHealthIncidentResolved', icon: 'pi pi-check-circle', severity: 'success', route: '/app/notifications', actionLabelKey: 'notifications.display.actions.openNotifications'
  },
  PRODUCT_RELEASE_AVAILABLE: {
    typeLabelKey: 'notifications.display.types.productReleaseAvailable', icon: 'pi pi-sparkles', severity: 'info', route: '/app/notifications', actionLabelKey: 'notifications.display.actions.openNotifications'
  },
  REWIND_READY: {
    typeLabelKey: 'notifications.display.types.rewindReady', icon: 'pi pi-history', severity: 'info', route: '/app/rewind', actionLabelKey: 'notifications.display.actions.openRewind'
  }
}

const TARGET_ROUTE_CONFIG = [
  {
    prefix: '/app/timeline/jobs',
    actionLabelKey: 'notifications.display.actions.viewTimelineStatus'
  },
  {
    prefix: '/app/geofences',
    actionLabelKey: 'notifications.display.actions.openGeofenceEvents'
  },
  {
    prefix: '/app/data-export-import?tab=import',
    actionLabelKey: 'notifications.display.actions.openImports'
  },
  {
    prefix: '/app/data-export-import?tab=export',
    actionLabelKey: 'notifications.display.actions.openExports'
  },
  {
    prefix: '/app/friends/invites',
    actionLabelKey: 'notifications.display.actions.openInvitations'
  },
  {
    prefix: '/app/friends',
    actionLabelKey: 'notifications.display.actions.openFriends'
  },
  {
    prefix: '/app/admin/dashboard',
    actionLabelKey: 'notifications.display.actions.openAdminDashboard'
  }
]

const humanizeToken = (value, fallback) => {
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
  const fallbackLabel = t('notifications.titleFallback')

  return {
    title: (typeConfig.titleKey && t(typeConfig.titleKey)) || notification?.title || (typeConfig.typeLabelKey && t(typeConfig.typeLabelKey)) || humanizeToken(notification?.type, fallbackLabel),
    sourceLabel: (sourceConfig.sourceLabelKey && t(sourceConfig.sourceLabelKey)) || humanizeToken(notification?.source, fallbackLabel),
    typeLabel: (typeConfig.typeLabelKey && t(typeConfig.typeLabelKey)) || humanizeToken(notification?.type, fallbackLabel),
    icon: typeConfig.icon || sourceConfig.icon || 'pi pi-bell',
    severity: typeConfig.severity || sourceConfig.severity || 'secondary',
    route,
    actionLabel: (targetRouteConfig.actionLabelKey && t(targetRouteConfig.actionLabelKey))
      || (typeConfig.actionLabelKey && t(typeConfig.actionLabelKey))
      || (sourceConfig.actionLabelKey && t(sourceConfig.actionLabelKey))
      || t('notifications.display.actions.openNotification')
  }
}

export const resolveNotificationRoute = (notification = {}) => {
  return resolveNotificationDisplay(notification).route || '/app/notifications'
}
