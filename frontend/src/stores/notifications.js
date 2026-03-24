import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import router from '@/router'
import { readUserSnapshot } from '@/utils/authSnapshotStorage'

const BROWSER_PREF_KEY = 'gp.notifications.browser.enabled'
const BACKLOG_WATERMARK_PREFIX = 'gp.notifications.backlog.watermark.'
const POLL_INTERVAL_MS = 10000
const MAX_BACKOFF_MS = 60000
const MAX_TRACKED_IDS = 2000

function canUseBrowserNotifications() {
  return typeof window !== 'undefined' && 'Notification' in window
}

function shouldEmitInAppToasts() {
  if (typeof document === 'undefined') {
    return true
  }
  const hasFocus = typeof document.hasFocus === 'function' ? document.hasFocus() : false
  return !document.hidden && hasFocus
}

export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    items: [],
    unreadCount: 0,
    isPolling: false,
    initialized: false,
    knownIds: [],
    pollTimerId: null,
    pollBackoffMs: POLL_INTERVAL_MS,
    visibilityListenerAttached: false,
    browserNotificationsEnabled: false,
    browserNotificationsSupported: canUseBrowserNotifications(),
    browserNotificationWarningShown: false,
    _onVisibilityChange: null,
    _onWindowFocus: null,
    currentUserId: null,
    backlogWatermark: null,
    _toastHandler: null
  }),

  getters: {
    unreadItems: (state) => state.items.filter(item => !item.seen),
    hasUnread: (state) => state.unreadCount > 0
  },

  actions: {
    initPreferences() {
      if (typeof window === 'undefined') {
        return
      }
      const stored = window.localStorage.getItem(BROWSER_PREF_KEY)
      if (stored === '1') {
        if (!this.browserNotificationsSupported || typeof Notification === 'undefined') {
          this.browserNotificationsEnabled = false
          this.persistBrowserPreference(false)
          return
        }

        if (Notification.permission === 'granted') {
          this.browserNotificationsEnabled = true
          return
        }

        this.browserNotificationsEnabled = false
        this.persistBrowserPreference(false)
      } else if (stored === '0') {
        this.browserNotificationsEnabled = false
      }
    },

    setToastHandler(handler) {
      this._toastHandler = handler
    },

    clearToastHandler() {
      this._toastHandler = null
    },

    resolveCurrentUserId() {
      return readUserSnapshot()?.id || null
    },

    getBacklogWatermarkKey(userId) {
      if (!userId) {
        return null
      }
      return `${BACKLOG_WATERMARK_PREFIX}${userId}`
    },

    loadBacklogWatermark() {
      this.currentUserId = this.resolveCurrentUserId()
      this.backlogWatermark = null

      if (typeof window === 'undefined' || !this.currentUserId) {
        return
      }

      const key = this.getBacklogWatermarkKey(this.currentUserId)
      const raw = key ? window.localStorage.getItem(key) : null
      if (!raw) {
        return
      }

      const parsed = Number(raw)
      if (Number.isFinite(parsed) && parsed > 0) {
        this.backlogWatermark = parsed
      }
    },

    advanceBacklogWatermark(candidateId) {
      const parsed = Number(candidateId)
      if (!Number.isFinite(parsed) || parsed <= 0) {
        return
      }

      const nextValue = this.backlogWatermark && this.backlogWatermark > parsed
        ? this.backlogWatermark
        : parsed

      this.backlogWatermark = nextValue

      if (typeof window === 'undefined' || !this.currentUserId) {
        return
      }

      const key = this.getBacklogWatermarkKey(this.currentUserId)
      if (key) {
        window.localStorage.setItem(key, String(nextValue))
      }
    },

    clearBacklogWatermarkForCurrentUser() {
      if (typeof window === 'undefined') {
        this.backlogWatermark = null
        this.currentUserId = null
        return
      }

      const userId = this.currentUserId || this.resolveCurrentUserId()
      const key = this.getBacklogWatermarkKey(userId)
      if (key) {
        window.localStorage.removeItem(key)
      }

      this.backlogWatermark = null
      this.currentUserId = null
    },

    startPolling() {
      if (this.isPolling) {
        return
      }

      this.initPreferences()
      this.loadBacklogWatermark()
      this.isPolling = true
      this.pollBackoffMs = POLL_INTERVAL_MS
      this.ensureVisibilityListeners()

      this.refresh({
        emitToasts: false,
        emitStartupSummary: true
      }).finally(() => {
        this.scheduleNextPoll(POLL_INTERVAL_MS)
      })
    },

    stopPolling() {
      this.isPolling = false
      if (this.pollTimerId && typeof window !== 'undefined') {
        window.clearTimeout(this.pollTimerId)
        this.pollTimerId = null
      }
      this.detachVisibilityListeners()
    },

    resetSessionState({ clearBacklogWatermark = false } = {}) {
      this.stopPolling()

      if (clearBacklogWatermark) {
        this.clearBacklogWatermarkForCurrentUser()
      }

      this.items = []
      this.unreadCount = 0
      this.initialized = false
      this.knownIds = []
      this.pollBackoffMs = POLL_INTERVAL_MS
      this.browserNotificationWarningShown = false
    },

    scheduleNextPoll(delayMs = POLL_INTERVAL_MS) {
      if (!this.isPolling || typeof window === 'undefined') {
        return
      }

      if (this.pollTimerId) {
        window.clearTimeout(this.pollTimerId)
      }

      this.pollTimerId = window.setTimeout(async () => {
        try {
          const emitToasts = shouldEmitInAppToasts()
          await this.refresh({
            emitToasts,
            emitBrowser: true,
            emitStartupSummary: false
          })
          this.pollBackoffMs = POLL_INTERVAL_MS
          this.scheduleNextPoll(POLL_INTERVAL_MS)
        } catch (error) {
          this.pollBackoffMs = Math.min(this.pollBackoffMs * 2, MAX_BACKOFF_MS)
          this.scheduleNextPoll(this.pollBackoffMs)
        }
      }, delayMs)
    },

    ensureVisibilityListeners() {
      if (this.visibilityListenerAttached || typeof window === 'undefined') {
        return
      }

      this._onVisibilityChange = () => {
        if (typeof document !== 'undefined' && !document.hidden) {
          this.refresh({
            emitToasts: false,
            emitBrowser: false,
            emitStartupSummary: false
          }).catch(() => {})
        }
      }

      this._onWindowFocus = () => {
        this.refresh({
          emitToasts: false,
          emitBrowser: false,
          emitStartupSummary: false
        }).catch(() => {})
      }

      document.addEventListener('visibilitychange', this._onVisibilityChange)
      window.addEventListener('focus', this._onWindowFocus)
      this.visibilityListenerAttached = true
    },

    detachVisibilityListeners() {
      if (!this.visibilityListenerAttached || typeof window === 'undefined') {
        return
      }

      if (this._onVisibilityChange) {
        document.removeEventListener('visibilitychange', this._onVisibilityChange)
      }
      if (this._onWindowFocus) {
        window.removeEventListener('focus', this._onWindowFocus)
      }
      this._onVisibilityChange = null
      this._onWindowFocus = null
      this.visibilityListenerAttached = false
    },

    async fetchNotifications({ limit = 100 } = {}) {
      const params = { limit }
      const response = await apiService.get('/notifications', params)
      return Array.isArray(response?.data) ? response.data : []
    },

    async fetchUnreadCount() {
      const response = await apiService.get('/notifications/unread-count')
      const count = Number(response?.data?.count || 0)
      const latestUnreadIdRaw = response?.data?.latestUnreadId
      const latestUnreadId = Number.isFinite(Number(latestUnreadIdRaw))
        ? Number(latestUnreadIdRaw)
        : null

      return {
        count,
        latestUnreadId
      }
    },

    async refresh({
      emitToasts = false,
      emitBrowser = false,
      emitStartupSummary = false
    } = {}) {
      const [events, countInfo] = await Promise.all([
        this.fetchNotifications({ limit: 100 }),
        this.fetchUnreadCount()
      ])

      const unreadCount = Number(countInfo?.count || 0)
      const latestUnreadId = Number.isFinite(Number(countInfo?.latestUnreadId))
        ? Number(countInfo.latestUnreadId)
        : null

      const incomingIds = events
        .map(event => Number(event.id))
        .filter(Number.isFinite)

      const knownIdsSet = new Set(this.knownIds)
      const nextKnownIdsSet = new Set(knownIdsSet)
      incomingIds.forEach(id => nextKnownIdsSet.add(id))

      if (!this.initialized) {
        this.initialized = true
        this.knownIds = Array.from(nextKnownIdsSet).slice(-MAX_TRACKED_IDS)
        this.items = events
        this.unreadCount = unreadCount

        const startupBaselineId = latestUnreadId
          || (incomingIds.length > 0 ? Math.max(...incomingIds) : null)

        if (emitStartupSummary && unreadCount > 0 && Number.isFinite(startupBaselineId)) {
          const shouldShowSummary = this.backlogWatermark == null || startupBaselineId > this.backlogWatermark
          if (shouldShowSummary) {
            this.emitToast({
              summary: 'Unread notifications',
              detail: `You have ${unreadCount} unread notifications.`,
              life: 6500,
              data: { action: 'open-events' }
            })
            this.advanceBacklogWatermark(startupBaselineId)
          }
        }

        return
      }

      const newEvents = events
        .filter(event => Number.isFinite(Number(event.id)) && !knownIdsSet.has(Number(event.id)))
        .sort((a, b) => {
          const aTs = new Date(a.occurredAt || 0).getTime()
          const bTs = new Date(b.occurredAt || 0).getTime()
          return aTs - bTs
        })

      this.items = events
      this.unreadCount = unreadCount
      this.knownIds = Array.from(nextKnownIdsSet).slice(-MAX_TRACKED_IDS)

      for (const event of newEvents) {
        if (emitToasts) {
          this.emitToast({
            summary: event.title || this.fallbackTitle(event),
            detail: event.message || 'New notification',
            life: 7000,
            data: {
              action: 'open-events',
              notificationId: event.id
            }
          })
          this.advanceBacklogWatermark(event.id)
        }

        if (emitBrowser) {
          this.emitBrowserNotification(event)
        }
      }
    },

    fallbackTitle(event) {
      if (event?.source && event?.type) {
        return `${event.source}: ${event.type}`
      }
      if (event?.source) {
        return `${event.source} notification`
      }
      return 'New notification'
    },

    async markSeen(notificationId) {
      const normalizedId = Number(notificationId)
      const existing = this.items.find(item => Number(item.id) === normalizedId)
      const wasUnread = existing ? !existing.seen : false

      const response = await apiService.post(`/notifications/${notificationId}/seen`, {})
      const updated = response?.data || null
      if (updated) {
        this.items = this.items.map(item => Number(item.id) === Number(updated.id) ? updated : item)
      }
      if (wasUnread && this.unreadCount > 0) {
        this.unreadCount = Math.max(0, this.unreadCount - 1)
      }
      return updated
    },

    async markAllSeen() {
      await apiService.post('/notifications/seen-all', {})
      this.items = this.items.map(item => {
        return {
          ...item,
          seen: true,
          seenAt: item.seenAt || new Date().toISOString()
        }
      })
      this.unreadCount = 0
    },

    async setBrowserNotificationsEnabled(enabled) {
      if (!this.browserNotificationsSupported) {
        this.browserNotificationsEnabled = false
        this.persistBrowserPreference(false)
        return false
      }

      if (!enabled) {
        this.browserNotificationsEnabled = false
        this.persistBrowserPreference(false)
        return true
      }

      try {
        if (Notification.permission === 'granted') {
          this.browserNotificationsEnabled = true
          this.persistBrowserPreference(true)
          return true
        }

        if (Notification.permission === 'denied') {
          this.browserNotificationsEnabled = false
          this.persistBrowserPreference(false)
          this.emitToast({
            summary: 'Browser notifications blocked',
            detail: 'Enable notification permission in your browser settings to use desktop alerts.',
            life: 6000
          })
          return false
        }

        const permission = await Notification.requestPermission()
        const granted = permission === 'granted'
        this.browserNotificationsEnabled = granted
        this.browserNotificationWarningShown = false
        this.persistBrowserPreference(granted)
        return granted
      } catch (error) {
        this.browserNotificationsEnabled = false
        this.persistBrowserPreference(false)
        return false
      }
    },

    persistBrowserPreference(enabled) {
      if (typeof window === 'undefined') {
        return
      }
      window.localStorage.setItem(BROWSER_PREF_KEY, enabled ? '1' : '0')
    },

    emitToast(payload) {
      if (typeof this._toastHandler === 'function') {
        this._toastHandler(payload)
      }
    },

    emitBrowserNotification(event) {
      if (!this.browserNotificationsSupported || !this.browserNotificationsEnabled) {
        return
      }
      if (typeof Notification === 'undefined') {
        return
      }

      if (Notification.permission !== 'granted') {
        if (Notification.permission === 'denied') {
          this.browserNotificationsEnabled = false
          this.persistBrowserPreference(false)
        }

        if (!this.browserNotificationWarningShown) {
          this.browserNotificationWarningShown = true
          this.emitToast({
            summary: 'Browser notification not sent',
            detail: 'Browser permission is not granted. Enable Browser alerts from the bell menu again.',
            life: 6500
          })
        }
        return
      }

      try {
        const notification = new Notification(event.title || this.fallbackTitle(event), {
          body: event.message || 'New notification',
          tag: `notification-${event.id}`,
          renotify: false
        })

        notification.onclick = () => {
          window.focus()
          this.openEventsView()
          notification.close()
        }
      } catch (error) {
        if (!this.browserNotificationWarningShown) {
          this.browserNotificationWarningShown = true
          this.emitToast({
            summary: 'Browser notification failed',
            detail: 'Your browser or OS blocked desktop alerts. In-app notifications are still active.',
            life: 6500
          })
        }
      }
    },

    handleToastClick(data) {
      if (data?.action === 'open-events') {
        this.openEventsView()
      }
    },

    openEventsView() {
      void router.push({
        path: '/app/geofences',
        query: { tab: 'events' }
      }).catch(() => {})
    }
  }
})
