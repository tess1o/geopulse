import { flushPromises, mount } from '@vue/test-utils'
import AdminDashboardPage from './AdminDashboardPage.vue'

vi.hoisted(() => {
  // maplibre-gl is pulled in transitively and needs this to exist under jsdom.
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: () => 'blob:test' })
})

const router = vi.hoisted(() => ({ push: vi.fn() }))
const mocks = vi.hoisted(() => ({ getDashboardStats: vi.fn() }))

vi.mock('vue-router', () => ({ useRouter: () => router }))
vi.mock('@/stores/admin', () => ({ useAdminStore: () => ({ getDashboardStats: mocks.getDashboardStats }) }))
// AppLayout transitively pulls in the whole map stack (maplibre, panoramax); stub the module so it never loads.
vi.mock('@/components/ui/layout/AppLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }))

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    router.push.mockReset()
    vi.setSystemTime(new Date('2026-09-08T12:00:00Z'))
    mocks.getDashboardStats.mockResolvedValue({
      totalUsers: 4,
      activeUsers24h: 2,
      totalGpsPoints: 1500,
      gpsActivity24h: 250,
      health: {
        ingestion: { latestReceivedAt: '2026-08-28T23:06:00Z' }, backup: { latestBackupAt: '2026-09-08T00:00:00Z' }, security: { warnings: [] },
        geocoding: { status: 'CIRCUIT_OPEN', providers: [{ name: 'Nominatim', displayName: 'Nominatim', primary: true, status: 'CIRCUIT_OPEN', circuitBreakerObservedOpenAt: '2026-09-08T11:00:00Z' }] },
      },
      weatherStatus: {},
    })
  })

  afterEach(() => vi.useRealTimers())

  it('shows stale GPS and reverse-geocoding health without Timeline Processing', async () => {
    const wrapper = mount(AdminDashboardPage, {
      global: {
        stubs: {
          AppLayout: { template: '<div><slot /></div>' }, Card: { template: '<div><slot name="content" /></div>' },
          Tag: { props: ['value'], template: '<span>{{ value }}</span>' }, Button: { props: ['label'], template: '<button @click="$emit(\'click\')">{{ label }}</button>' }, Breadcrumb: true, Skeleton: true, Message: true, DemoReadOnlyBanner: true,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('STALE')
    expect(wrapper.text()).toContain('Reverse Geocoding')
    expect(wrapper.text()).toContain('CIRCUIT OPEN')
    expect(wrapper.text()).toContain('NO FALLBACK')
    expect(wrapper.text()).toContain('Nominatim')
    expect(wrapper.text()).not.toContain('Timeline Processing')
    expect(wrapper.text()).toContain('2 items need attention')

    await wrapper.findAll('button').find(button => button.text() === 'Open geocoding settings').trigger('click')
    expect(router.push).toHaveBeenCalledWith('/app/admin/settings?tab=geocoding')
  })
})
