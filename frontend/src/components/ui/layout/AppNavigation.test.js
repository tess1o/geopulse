import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { useFriendsStore } from '@/stores/friends'

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
vi.mock('@/composables/useErrorHandler', () => ({ useErrorHandler: () => ({ handleError: vi.fn() }) }))

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
  BaseButton: { template: '<button />' }
}

describe('AppNavigation', () => {
  let AppNavigation
  let pinia

  beforeEach(async () => {
    pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().$patch({ user: { fullName: 'Test User', role: 'USER' } })
    useFriendsStore().$patch({ receivedInvites: [{ id: 1 }, { id: 2 }] })
    vi.spyOn(useFriendsStore(), 'fetchReceivedInvitations').mockResolvedValue()
    AppNavigation = (await import('./AppNavigation.vue')).default
  })

  it('groups destinations in task order without an authenticated Home item', () => {
    const wrapper = mount(AppNavigation, { global: { plugins: [pinia], stubs } })
    const sections = wrapper.findAllComponents({ name: 'NavigationSection' })

    expect(sections.map((section) => section.props('title'))).toEqual([
      'Timeline', 'Explore', 'Organize & Share', 'Data'
    ])
    expect(sections.map((section) => section.props('items').map((item) => item.label))).toEqual([
      ['Timeline', 'Dashboard', 'Timeline Labels', 'Trip Plans'],
      ['Location Analytics', 'Journey Insights', 'Rewind', 'Coverage Explorer', 'AI Assistant'],
      ['Favorites', 'Geofences', 'Friends', 'Share Links'],
      ['Location Sources', 'GPS Data', 'Geocoding', 'Export / Import']
    ])

    const items = sections.flatMap((section) => section.props('items'))
    expect(items.some((item) => item.label === 'Home')).toBe(false)
    expect(items.map((item) => item.to)).toEqual([
      '/app/timeline', '/app/dashboard', '/app/timeline-labels', '/app/trips',
      '/app/location-analytics', '/app/journey-insights', '/app/rewind', '/app/coverage', '/app/ai/chat',
      '/app/favorites-management', '/app/geofences', '/app/friends', '/app/share-links',
      '/app/location-sources', '/app/gps-data', '/app/geocoding-management', '/app/data-export-import'
    ])
    expect(items.find((item) => item.label === 'Friends').badge).toBe(2)
  })

  it('leaves account and settings destinations to the avatar menu', () => {
    const wrapper = mount(AppNavigation, { global: { plugins: [pinia], stubs } })
    const routes = wrapper.findAllComponents({ name: 'NavigationSection' })
      .flatMap((section) => section.props('items').map((item) => item.to))

    for (const route of ['/app/profile', '/app/timeline/preferences', '/app/notifications', '/app/help']) {
      expect(routes).not.toContain(route)
    }
    expect(routes.some((route) => route.startsWith('/app/admin'))).toBe(false)
    expect(wrapper.text()).not.toContain('Logout')
  })

  it('lists every admin page in one flat Administration group for admins', () => {
    useAuthStore().$patch({ user: { fullName: 'Admin User', role: 'ADMIN', canViewAdmin: true, adminReadOnly: true } })
    const wrapper = mount(AppNavigation, { global: { plugins: [pinia], stubs } })
    const sections = wrapper.findAllComponents({ name: 'NavigationSection' })
    const admin = sections.at(-1)

    expect(sections).toHaveLength(5)
    expect(admin.props('title')).toBe('Administration')
    expect(admin.props('items').map((item) => item.to)).toEqual([
      '/app/admin', '/app/admin/settings', '/app/admin/users', '/app/admin/invitations',
      '/app/admin/oidc-providers', '/app/admin/backups', '/app/admin/timeline-regeneration-campaigns',
      '/app/admin/audit-logs'
    ])
    expect(admin.props('items').find((item) => item.key === 'admin-audit-logs').disabled).toBe(true)
  })

  it('renders the Ukrainian labels once the locale is switched', async () => {
    const { setLocale } = await import('@/composables/useLocale')
    await setLocale('uk', { persist: false })

    const wrapper = mount(AppNavigation, { global: { plugins: [pinia], stubs } })
    const sections = wrapper.findAllComponents({ name: 'NavigationSection' })

    expect(sections.map((section) => section.props('title'))).toEqual([
      'Хронологія', 'Дослідження', 'Організація та обмін', 'Дані'
    ])
    expect(sections[0].props('items').map((item) => item.label)).toEqual([
      'Хронологія', 'Дашборд', 'Мітки хронології', 'Плани поїздок'
    ])
    expect(sections[3].props('items').map((item) => item.label)).toContain('Джерела локацій')

    // The stable keys and routes must be untouched by the switch: they are identities, not copy.
    const items = sections.flatMap((section) => section.props('items'))
    expect(items.find((item) => item.label === 'Друзі').key).toBe('friends')
    expect(items.find((item) => item.key === 'journey-insights').to).toBe('/app/journey-insights')
  })
})
