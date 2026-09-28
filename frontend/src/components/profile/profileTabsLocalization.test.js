import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * Ukrainian rendering for the Personal Settings tabs.
 *
 * profileTabsDirtyState.test.js pins the English copy; this file is its counterpart and asserts the
 * same surfaces after a locale switch. Kept separate so the English suite stays readable and the two
 * concerns do not interleave.
 */

vi.hoisted(() => {
  const storage = new Map()
  const shim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: shim })
  Object.defineProperty(window, 'localStorage', { configurable: true, value: shim })
})

vi.mock('@/stores/notifications', () => ({
  useNotificationsStore: () => ({
    fetchPreferences: vi.fn().mockResolvedValue({}),
    updatePreferences: vi.fn().mockResolvedValue({})
  })
}))
vi.mock('@/utils/apiService', () => ({
  default: { get: vi.fn().mockResolvedValue({}), post: vi.fn().mockResolvedValue({}) }
}))
vi.mock('@/stores/notes', () => ({ useNotesStore: () => ({ testMemosConfig: vi.fn() }) }))
vi.mock('@/stores/immich', () => ({ useImmichStore: () => ({ testImmichConnection: vi.fn(), testConnection: vi.fn() }) }))
vi.mock('@/stores/ai', () => ({ useAIStore: () => ({ testConnection: vi.fn() }) }))

// Stubs render the props under test; layout-only children are inert.
const SettingCardStub = {
  props: ['title', 'description', 'details', 'settingId'],
  // `details` is a string or an object rendered as `label: value` rows -- both shapes are exercised.
  computed: {
    detailsText() {
      if (!this.details) return ''
      if (typeof this.details === 'string') return this.details
      return Object.entries(this.details).map(([label, value]) => `${label}: ${value}`).join(' | ')
    }
  },
  template: '<section :data-setting-id="settingId"><h3>{{ title }}</h3><p>{{ description }}</p><p class="details">{{ detailsText }}</p><slot name="control" /></section>'
}
const ButtonStub = { props: ['label'], template: '<button>{{ label }}</button>' }
const SliderControlStub = { props: ['labels', 'suffix'], template: '<div><span v-for="l in (labels || [])" :key="l">{{ l }}</span><span>{{ suffix }}</span></div>' }
// Declares the pass-through attrs the real component accepts, so Vue does not try to patch them onto
// a plain <div> (a declared prop is not rendered as a DOM property).
const DropdownStub = {
  props: ['options', 'placeholder', 'modelValue', 'optionLabel', 'optionValue', 'scrollHeight', 'filter', 'filterMatchMode', 'showClear', 'display', 'disabled', 'invalid', 'size', 'ariaLabel'],
  template: '<div><span v-for="o in (options || [])" :key="o.value">{{ o.label }}</span><span>{{ placeholder }}</span></div>'
}
const Inert = { template: '<div />' }

const globalOptions = {
  stubs: {
    Card: { template: '<div><slot name="content" /></div>' },
    Button: ButtonStub,
    SettingCard: SettingCardStub,
    SliderControl: SliderControlStub,
    Dropdown: DropdownStub,
    MultiSelect: DropdownStub,
    InputSwitch: Inert,
    ToggleSwitch: Inert,
    InputText: Inert,
    InputNumber: Inert,
    Password: Inert,
    Textarea: Inert,
    Message: Inert,
    OidcManagement: Inert,
    ApiTokensManagement: Inert
  }
}

describe('personal settings tabs in Ukrainian', () => {
  let SecurityTab
  let TimelineDisplayTab
  let NotificationsPreferencesTab
  let ProfileTab

  beforeEach(async () => {
    const { setLocale } = await import('@/composables/useLocale')
    await setLocale('uk', { persist: false })
    SecurityTab = (await import('./SecurityTab.vue')).default
    TimelineDisplayTab = (await import('./TimelineDisplayTab.vue')).default
    NotificationsPreferencesTab = (await import('./NotificationsPreferencesTab.vue')).default
    ProfileTab = (await import('./ProfileTab.vue')).default
  })

  describe('ProfileTab (General)', () => {
    const mountProfile = () => mount(ProfileTab, {
      props: {
        userName: 'Test User',
        userEmail: 'test@example.com',
        userAvatar: '/avatars/avatar1.png',
        userTimezone: 'UTC',
        userDateFormat: 'MDY',
        userTimeFormat: '24h',
        userLanguage: 'uk',
        userDistanceUnit: 'KILOMETERS',
        userTemperatureUnit: 'CELSIUS',
        userDefaultRedirectUrl: ''
      },
      global: globalOptions
    })

    it('translates the group headings and cards', () => {
      const text = mountProfile().text()

      expect(text).toContain('Загальні')
      expect(text).toContain('Профіль')
      expect(text).toContain('Регіональні налаштування')
      expect(text).toContain('Навігація')
      expect(text).toContain('Повне імʼя')
      expect(text).toContain('Зображення профілю')
      expect(text).toContain('Часовий пояс')
      expect(text).toContain('Формат дати')
      expect(text).toContain('Формат часу')
      expect(text).toContain('Одиниця відстані')
      expect(text).toContain('Одиниця температури')
      expect(text).toContain('Головна сторінка за замовчуванням')
      expect(text).toContain('Скинути')
      expect(text).toContain('Зберегти зміни')
    })

    it('translates the option labels', () => {
      const text = mountProfile().text()

      expect(text).toContain('Кілометри (км, м)')
      expect(text).toContain('Милі (миль, фут)')
      expect(text).toContain('Цельсій (°C)')
      expect(text).toContain('DD/MM/YYYY (європейський)')
      expect(text).toContain('24-годинний (13:45)')
    })

    it('reuses the navigation labels for the destination picker', () => {
      // These entries are the same destinations the nav shows, so they share nav.items.* keys rather
      // than restating the names -- this asserts the reuse actually resolves.
      const text = mountProfile().text()

      expect(text).toContain('Хронологія')
      expect(text).toContain('Статистика подорожей')
      expect(text).toContain('Дослідження покриття')
      expect(text).toContain('Власна адреса...')
    })

    it('reports validation errors in Ukrainian', async () => {
      // An empty name is what trips the rule, so mount with none.
      const wrapper = mount(ProfileTab, {
        props: {
          userName: '',
          userEmail: 'test@example.com',
          userAvatar: '/avatars/avatar1.png',
          userTimezone: 'UTC',
          userDateFormat: 'MDY',
          userTimeFormat: '24h',
          userLanguage: 'uk',
          userDistanceUnit: 'KILOMETERS',
          userTemperatureUnit: 'CELSIUS',
          userDefaultRedirectUrl: ''
        },
        global: globalOptions
      })

      await wrapper.find('form').trigger('submit')

      expect(wrapper.text()).toContain('Повне імʼя обовʼязкове')
    })

    it('leaves IANA timezone names in Latin script', () => {
      // Deliberate: these are identifiers, not prose.
      expect(mountProfile().text()).toContain('Europe/Kyiv')
    })
  })

  describe('SecurityTab', () => {
    it('translates the header, password fields and actions', () => {
      const wrapper = mount(SecurityTab, { props: { hasPassword: true }, global: globalOptions })
      const text = wrapper.text()

      expect(text).toContain('Безпека')
      expect(text).toContain('Керуйте паролями, підключеними способами входу та доступом до API.')
      // Conditional headings follow the same branch as the English version.
      expect(text).toContain('Змінити пароль')
      expect(text).toContain('Поточний пароль')
      expect(text).toContain('Новий пароль')
      expect(text).toContain('Підтвердьте новий пароль')
      expect(text).toContain('Скасувати')
      expect(text).toContain('Змінити пароль')
    })

    it('translates the set-password branch', () => {
      const wrapper = mount(SecurityTab, { props: { hasPassword: false }, global: globalOptions })

      expect(wrapper.text()).toContain('Встановити пароль')
      expect(wrapper.find('#currentPassword').exists()).toBe(false)
    })

    it('reports validation errors in Ukrainian', async () => {
      const wrapper = mount(SecurityTab, { props: { hasPassword: true }, global: globalOptions })

      // Submit empty: every rule fires at once.
      await wrapper.find('form').trigger('submit')
      const text = wrapper.text()

      expect(text).toContain('Поточний пароль обовʼязковий')
      expect(text).toContain('Новий пароль обовʼязковий')
    })
  })

  describe('TimelineDisplayTab', () => {
    const mountTimeline = (overrides = {}) => mount(TimelineDisplayTab, {
      props: {
        initialPreferences: {
          mapRenderMode: 'VECTOR',
          pathSimplificationEnabled: true,
          mapMatchingEnabled: false,
          mapMatchingAvailable: false,
          ...overrides
        }
      },
      global: globalOptions
    })

    it('translates the section headings and card copy', () => {
      const text = mountTimeline().text()

      expect(text).toContain('Хронологія та карта')
      expect(text).toContain('Поведінка хронології')
      expect(text).toContain('Відображення карт і джерела')
      expect(text).toContain('Обробка карт')
      expect(text).toContain('Типовий діапазон дат')
      expect(text).toContain('3D-будівлі')
      expect(text).toContain('Спрощення шляху')
      expect(text).toContain('Скинути до типових')
      expect(text).toContain('Зберегти зміни')
    })

    it('translates dropdown options and slider labels', () => {
      const text = mountTimeline().text()

      expect(text).toContain('Сьогодні')
      expect(text).toContain('Останні 7 днів')
      expect(text).toContain('Векторний (MapLibre)')
      expect(text).toContain('Растровий (Leaflet)')
      expect(text).toContain('15 м (збалансовано)')
      expect(text).toContain('0 (без обмежень)')
      // The tolerance details object renders as translated `label: value` rows.
      expect(text).toContain('Менші значення (1-10 м)')
      expect(text).toContain('Більше деталей, більше точок')
    })

    it('translates the unavailable map-matching copy', () => {
      const text = mountTimeline().text()

      expect(text).toContain('Недоступно, доки адміністратор не налаштує сервіс Valhalla.')
      expect(text).toContain('Привʼязка до маршруту')
    })

    it('translates URL validation errors', async () => {
      // Validation runs placeholders -> protocol, so each input reaches a different branch.
      const placeholderCase = mountTimeline({ customMapTileUrl: 'notaurl' })
      await placeholderCase.find('form').trigger('submit')
      expect(placeholderCase.text()).toContain('Адреса має містити заповнювачі {z}, {x} та {y}')

      const protocolCase = mountTimeline({ customMapTileUrl: 'ftp://tiles.example.com/{z}/{x}/{y}.png' })
      await protocolCase.find('form').trigger('submit')
      expect(protocolCase.text()).toContain('Адреса має використовувати протокол HTTP або HTTPS')
    })
  })

  describe('NotificationsPreferencesTab', () => {
    it('translates the event groups and the save action', async () => {
      const wrapper = mount(NotificationsPreferencesTab, { props: { readOnly: false }, global: globalOptions })
      await flushPromises()
      const text = wrapper.text()

      expect(text).toContain('Сповіщення')
      expect(text).toContain('Стан GPS')
      expect(text).toContain('Щомісячні підсумки')
      expect(text).toContain('Оновлення продукту')
      expect(text).toContain('Стежити за надходженням GPS-даних')
      expect(text).toContain('Показувати огляд випуску')
      expect(text).toContain('Зберегти зміни')
    })
  })
})
