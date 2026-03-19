import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import router from '@/router'

const BROWSER_PREF_KEY = 'gp.notifications.browser.enabled'
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
  return !document.hidden || hasFocus
}

export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    items: [],
    unreadCount: 0,
    isPolling: false,
    initialized: false,
    startupSummaryShown: false,
    knownIds: [],
    pollTimerId: null,
    pollBackoffMs: POLL_INTERVAL_MS,
    visibilityListenerAttached: false,
    browserNotificationsEnabled: false,
    browserNotificationsSupported: canUseBrowserNotifications(),
    browserNotificationWarningShown: false,
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

        // Preference was enabled previously, but permission is no longer granted.
        // Keep UI state accurate so the user can explicitly enable again.
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

    startPolling() {
      if (this.isPolling) {
        return
      }

      this.initPreferences()
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
      if (this.pollTimerId) {
        window.clearTimeout(this.pollTimerId)
        this.pollTimerId = null
      }
      this.detachVisibilityListeners()
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
      this.visibilityListenerAttached = false
    },

    async refresh({
      emitToasts = false,
      emitBrowser = false,
      emitStartupSummary = false
    } = {}) {
      const shouldAnnounce = emitToasts || emitBrowser
      const previousUnreadCount = this.unreadCount
      const [eventsResponse, countResponse] = await Promise.all([
        apiService.get('/geofences/events', { limit: 100, unreadOnly: false }),
        apiService.get('/geofences/events/unread-count')
      ])

      const events = Array.isArray(eventsResponse?.data) ? eventsResponse.data : []
      const unreadCount = Number(countResponse?.data?.count || 0)
      const incomingIds = events.map(event => Number(event.id)).filter(Number.isFinite)
      const knownIdsSet = new Set(this.knownIds)

      if (!this.initialized) {
        this.initialized = true
        incomingIds.forEach(id => knownIdsSet.add(id))
        this.knownIds = Array.from(knownIdsSet).slice(-MAX_TRACKED_IDS)
        this.items = events
        this.unreadCount = unreadCount

        if (emitStartupSummary && unreadCount > 0 && !this.startupSummaryShown) {
          this.startupSummaryShown = true
          this.emitToast({
            summary: 'Unread notifications',
            detail: `You have ${unreadCount} unread geofence notifications.`,
            life: 6500,
            data: { action: 'open-events' }
          })
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

      if (shouldAnnounce) {
        incomingIds.forEach(id => knownIdsSet.add(id))
        this.knownIds = Array.from(knownIdsSet).slice(-MAX_TRACKED_IDS)
      }

      for (const event of newEvents) {
        if (emitToasts) {
          this.emitToast({
            summary: event.title || `Geofence ${event.eventType}`,
            detail: event.message || 'New geofence notification',
            life: 7000,
            data: {
              action: 'open-events',
              eventId: event.id
            }
          })
        }

        if (emitBrowser) {
          this.emitBrowserNotification(event)
        }
      }

      if (emitToasts && newEvents.length === 0 && unreadCount > previousUnreadCount) {
        const delta = unreadCount - previousUnreadCount
        this.emitToast({
          summary: 'New notifications',
          detail: `You have ${delta} new geofence notification${delta > 1 ? 's' : ''}.`,
          life: 6000,
          data: { action: 'open-events' }
        })
      }
    },

    async markSeen(eventId) {
      const normalizedEventId = Number(eventId)
      const existing = this.items.find(item => Number(item.id) === normalizedEventId)
      const wasUnread = existing ? !existing.seen : true

      const response = await apiService.post(`/geofences/events/${eventId}/seen`, {})
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
      await apiService.post('/geofences/events/seen-all', {})
      this.items = this.items.map(item => ({
        ...item,
        seen: true,
        seenAt: item.seenAt || new Date().toISOString()
      }))
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
        const notification = new Notification(event.title || `Geofence ${event.eventType}`, {
          body: event.message || 'New geofence notification',
          tag: `geofence-event-${event.id}`,
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
