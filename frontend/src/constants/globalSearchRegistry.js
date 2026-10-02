import { PROFILE_SETTINGS_SEARCH_INDEX } from '@/constants/profileSettingsSearchIndex'
import { t } from '@/locales'
import {
  TIMELINE_PREFERENCE_LABELS,
  TIMELINE_PREFERENCE_TAB_BY_KEY
} from '@/constants/timelinePreferencesMetadata'
import { SETTING_METADATA } from '@/constants/adminSettingsMetadata'

const ADMIN_TAB_BY_PREFIX = [
  { prefix: 'auth.', tab: 'authentication', tabLabelKey: 'ui.globalSearch.registry.tabAuthentication' },
  { prefix: 'geocoding.', tab: 'geocoding', tabLabelKey: 'ui.globalSearch.registry.tabGeocoding' },
  { prefix: 'weather.', tab: 'weather', tabLabelKey: 'ui.globalSearch.registry.tabWeather' },
  { prefix: 'poi.', tab: 'poi', tabLabelKey: 'ui.globalSearch.registry.tabPlaceDiscovery' },
  { prefix: 'map-matching.', tab: 'map-matching', tabLabelKey: 'ui.globalSearch.registry.tabMapMatching' },
  { prefix: 'ai.', tab: 'ai', tabLabelKey: 'ui.globalSearch.registry.tabAiAssistant' },
  { prefix: 'import.', tab: 'import', tabLabelKey: 'ui.globalSearch.registry.tabImport' },
  { prefix: 'export.', tab: 'export', tabLabelKey: 'ui.globalSearch.registry.tabExport' },
  { prefix: 'backup.', tab: 'backup', tabLabelKey: 'ui.globalSearch.registry.tabBackup' },
  { prefix: 'system.notifications.', tab: 'notifications', tabLabelKey: 'ui.globalSearch.registry.tabNotifications' },
  { prefix: 'system.', tab: 'system', tabLabelKey: 'ui.globalSearch.registry.tabSystem' }
]

const PAGE_KEYWORDS = {
  '/app/timeline': ['history', 'movement', 'travel'],
  '/app/timeline-reports': ['report', 'tables', 'stays', 'trips', 'data gaps'],
  '/app/dashboard': ['summary', 'overview', 'stats'],
  '/app/profile': ['account', 'user', 'preferences'],
  '/app/timeline/preferences': ['detection', 'classification', 'timeline settings'],
  '/app/location-analytics': ['places', 'cities', 'countries', 'analytics'],
  '/app/admin/settings': ['system settings', 'admin settings', 'configuration']
}

const TIMELINE_TAB_LABEL_KEYS = {
  staypoints: 'ui.globalSearch.registry.timelineTabStayPoints',
  trips: 'ui.globalSearch.registry.timelineTabTrips',
  gpsgaps: 'ui.globalSearch.registry.timelineTabGpsGaps',
  merging: 'ui.globalSearch.registry.timelineTabMerging'
}

const toSearchItemId = (prefix, key) => `${prefix}:${key}`

const tabMetaFromSettingKey = (key) => {
  const match = ADMIN_TAB_BY_PREFIX.find((entry) => key.startsWith(entry.prefix))
  return match || { tab: 'system', tabLabelKey: 'ui.globalSearch.registry.tabSystem' }
}

const SETTINGS_PAGE_BUILDERS = {
  profile: (_isAdmin) => buildProfileSettingsIndex(),
  timeline: (_isAdmin) => buildTimelineSettingsIndex(),
  admin: (isAdmin) => buildAdminSettingsIndex(isAdmin)
}

export const buildTimelineSettingsIndex = () => {
  return Object.entries(TIMELINE_PREFERENCE_LABELS).map(([settingKey]) => ({
    tab: TIMELINE_PREFERENCE_TAB_BY_KEY[settingKey] || 'staypoints',
    id: toSearchItemId('timeline', settingKey),
    kind: 'setting',
    title: t(`timelinePreferences.page.settings.${settingKey}`),
    subtitle: t('ui.globalSearch.registry.subtitleTimelinePreferences', {
      tab: t(TIMELINE_TAB_LABEL_KEYS[TIMELINE_PREFERENCE_TAB_BY_KEY[settingKey] || 'staypoints'])
    }),
    icon: 'pi pi-sliders-h',
    to: '/app/timeline/preferences',
    setting: settingKey,
    keywords: [settingKey.replace(/[A-Z]/g, ' $&').toLowerCase()]
  }))
}

export const buildProfileSettingsIndex = () => {
  return PROFILE_SETTINGS_SEARCH_INDEX.map((setting) => ({
    id: toSearchItemId('profile', setting.id),
    kind: 'setting',
    // `titleKey` is preferred where an entry has been migrated. Reading t() during this map is what
    // makes the enclosing computed re-evaluate on a locale change. Entries without a key keep their
    // literal title until they are migrated.
    title: setting.titleKey ? t(setting.titleKey) : setting.title,
    subtitle: t('ui.globalSearch.registry.subtitleProfile', { subtitle: setting.subtitle }),
    icon: 'pi pi-user',
    to: '/app/profile',
    tab: setting.tab,
    setting: setting.id,
    keywords: setting.keywords || []
  }))
}

export const buildAdminSettingsIndex = (isAdmin) => {
  if (!isAdmin) return []

  return Object.entries(SETTING_METADATA).map(([key, metadata]) => {
    const tabMeta = tabMetaFromSettingKey(key)

    return {
      id: toSearchItemId('admin', key),
      kind: 'setting',
      title: metadata.label || key,
      subtitle: t('ui.globalSearch.registry.subtitleSystemSettings', { tab: t(tabMeta.tabLabelKey) }),
      icon: 'pi pi-cog',
      to: '/app/admin/settings',
      tab: tabMeta.tab,
      setting: key,
      requiresAdmin: true,
      keywords: [
        key,
        metadata.description || ''
      ].filter(Boolean)
    }
  })
}

export const buildPageIndex = (routes, isAdmin) => {
  const uniqueByPath = new Map()

  routes
    .filter((route) => route.path?.startsWith('/app'))
    .filter((route) => !route.path.includes(':'))
    .filter((route) => route.path !== '/app')
    .filter((route) => !!(route.meta?.title || route.name))
    .filter((route) => {
      if (!route.path.startsWith('/app/admin')) return true
      return isAdmin
    })
    .forEach((route) => {
      if (uniqueByPath.has(route.path)) return

      uniqueByPath.set(route.path, {
      id: toSearchItemId('page', route.path),
      kind: 'page',
      title: route.meta?.title || route.name || route.path,
      subtitle: route.path.startsWith('/app/admin') ? t('ui.globalSearch.registry.subtitleAdministration') : t('ui.globalSearch.registry.subtitlePage'),
      icon: route.path.startsWith('/app/admin') ? 'pi pi-shield' : 'pi pi-compass',
      to: route.path,
      requiresAdmin: route.path.startsWith('/app/admin'),
      keywords: PAGE_KEYWORDS[route.path] || []
      })
    })

  return Array.from(uniqueByPath.values())
}

export const buildSettingsIndex = (isAdmin) => {
  return [
    ...buildProfileSettingsIndex(),
    ...buildTimelineSettingsIndex(),
    ...buildAdminSettingsIndex(isAdmin)
  ]
}

export const buildSettingsIndexForPage = (pageKey, isAdmin) => {
  const builder = SETTINGS_PAGE_BUILDERS[pageKey]
  if (!builder) return []
  return builder(isAdmin)
}
