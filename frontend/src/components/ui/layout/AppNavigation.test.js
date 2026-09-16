import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { useFriendsStore } from '@/stores/friends'
import { useNotificationsStore } from '@/stores/notifications'

vi.hoisted(() => {
  const storage = new Map()
  const localStorageShim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: localStorageShim })
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: () => 'blob:test' })
})

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/router', () => ({ default: { push: vi.fn() } }))
vi.mock('@/composables/useThemeMode', () => ({
  useThemeMode: () => ({ themeMode: { value: 'light' }, themeModes: { LIGHT: 'light', DARK: 'dark' } })
}))
vi.mock('@/composables/useErrorHandler', () => ({ useErrorHandler: () => ({ handleError: vi.fn() }) }))
vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn().mockResolvedValue({ version: 'test' }) } }))

const Drawer = {
  props: ['visible'],
  methods: { close() {} },
  template: '<div><slot name="container" :closeCallback="close" /></div>'
}

const NavigationSection = {
  name: 'NavigationSection',
  props: ['title', 'items'],
  template: '<section><h2>{{ title }}</h2><span v-for="item in items" :key="item.key">{{ item.label }}</span></section>'
}

const stubs = {
  Drawer,
  NavigationSection,
  BaseButton: { template: '<button />' },
  DarkModeSwitcher: { template: '<button />' }
}

describe('AppNavigation', () => {
  let AppNavigation
  let pinia

  beforeEach(async () => {
    pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().$patch({ user: { fullName: 'Test User', role: 'USER' } })
    useFriendsStore().$patch({ receivedInvites: [{ id: 1 }, { id: 2 }] })
    useNotificationsStore().$patch({ unreadCount: 3 })
    vi.spyOn(useFriendsStore(), 'fetchReceivedInvitations').mockResolvedValue()
    AppNavigation = (await import('./AppNavigation.vue')).default
  })

  it('groups destinations in task order without an authenticated Home item', () => {
    const wrapper = mount(AppNavigation, { global: { plugins: [pinia], stubs } })
    const sections = wrapper.findAllComponents({ name: 'NavigationSection' })

    expect(sections.map((section) => section.props('title'))).toEqual([
      'Timeline', 'Explore', 'Organize & Share', 'Settings & Data'
    ])
    expect(sections.map((section) => section.props('items').map((item) => item.label))).toEqual([
      ['Timeline', 'Dashboard', 'Timeline Labels', 'Trip Plans'],
      ['Location Analytics', 'Journey Insights', 'Rewind', 'Coverage Explorer', 'AI Assistant'],
      ['Favorites', 'Geofences', 'Friends', 'Share Links'],
      ['Profile', 'Notifications', 'Location Sources', 'Timeline Preferences', 'GPS Data', 'Geocoding', 'Export / Import', 'Help & Support']
    ])

    const items = sections.flatMap((section) => section.props('items'))
    expect(items.some((item) => item.label === 'Home')).toBe(false)
    expect(items.map((item) => item.to)).toEqual([
      '/app/timeline', '/app/dashboard', '/app/timeline-labels', '/app/trips',
      '/app/location-analytics', '/app/journey-insights', '/app/rewind', '/app/coverage', '/app/ai/chat',
      '/app/favorites-management', '/app/geofences', '/app/friends', '/app/share-links',
      '/app/profile', '/app/notifications', '/app/location-sources', '/app/timeline/preferences',
      '/app/gps-data', '/app/geocoding-management', '/app/data-export-import', '/app/help'
    ])
    expect(items.find((item) => item.label === 'Friends').badge).toBe(2)
    expect(items.find((item) => item.label === 'Notifications').badge).toBe(3)
  })
})
