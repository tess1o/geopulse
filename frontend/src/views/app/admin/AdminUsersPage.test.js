import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AdminUsersPage from './AdminUsersPage.vue'

vi.hoisted(() => {
  // maplibre-gl is pulled in transitively and needs this to exist under jsdom.
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: () => 'blob:test' })
})

const router = vi.hoisted(() => ({ push: vi.fn() }))
const mocks = vi.hoisted(() => ({ getUsers: vi.fn(), retryUserDeletion: vi.fn(), toast: vi.fn() }))

vi.mock('vue-router', () => ({ useRouter: () => router }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: mocks.toast }) }))
vi.mock('@/stores/admin', () => ({
  useAdminStore: () => ({ getUsers: mocks.getUsers, retryUserDeletion: mocks.retryUserDeletion })
}))
// AppLayout transitively pulls in the whole map stack (maplibre, panoramax); stub the module so it never loads.
vi.mock('@/components/ui/layout/AppLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }))

const user = (overrides = {}) => ({
  id: 'user-1',
  email: 'deleted@example.com',
  fullName: 'Deleted User',
  role: 'USER',
  active: true,
  gpsPointsCount: 1200,
  createdAt: '2026-01-01T00:00:00Z',
  deletion: null,
  ...overrides
})

const page = (items) => ({ items, totalElements: items.length })

const mountPage = () => mount(AdminUsersPage, {
  global: {
    stubs: {
      DataTable: {
        props: ['value'],
        template: '<div class="table"><div v-for="row in value" :key="row.id" class="row"><slot /></div></div>'
      },
      Column: true,
      Breadcrumb: true,
      Dialog: true,
      DemoReadOnlyBanner: true,
      InputText: true
    },
    directives: { tooltip: {} }
  }
})

describe('AdminUsersPage user deletion', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
    mocks.getUsers.mockReset()
    mocks.retryUserDeletion.mockReset()
    mocks.toast.mockReset()
  })

  afterEach(() => vi.useRealTimers())

  it('shows a running deletion and refreshes until it is done', async () => {
    mocks.getUsers
      .mockResolvedValueOnce(page([user({
        gpsPointsCount: null,
        deletion: { status: 'RUNNING', phase: 'GPS_POINTS', deletedGpsPoints: 20000 }
      })]))
      .mockResolvedValueOnce(page([]))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('Deleting')
    expect(wrapper.text()).toContain('Deleting GPS points')
    expect(wrapper.text()).toContain('GPS points deleted')
    expect(mocks.getUsers).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(5000)
    await flushPromises()
    expect(mocks.getUsers).toHaveBeenCalledTimes(2)

    // Nothing is being deleted any more: no further refreshes.
    await vi.advanceTimersByTimeAsync(20000)
    expect(mocks.getUsers).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('stops refreshing when the page is left while a refresh is in flight', async () => {
    const running = () => page([user({
      gpsPointsCount: null,
      deletion: { status: 'RUNNING', phase: 'GPS_POINTS', deletedGpsPoints: 2 }
    })])
    let finishRefresh
    mocks.getUsers
      .mockResolvedValueOnce(running())
      .mockImplementationOnce(() => new Promise(resolve => { finishRefresh = resolve }))
      .mockResolvedValue(running())

    const wrapper = mountPage()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(5000)
    expect(mocks.getUsers).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    finishRefresh(running())
    await flushPromises()
    await vi.advanceTimersByTimeAsync(20000)

    expect(mocks.getUsers).toHaveBeenCalledTimes(2)
  })

  it('does not refresh for a failed deletion and retries it on request', async () => {
    const failed = user({
      gpsPointsCount: null,
      deletion: { status: 'FAILED', phase: 'LOCATION_DATA', deletedGpsPoints: 0, lastError: 'boom' }
    })
    mocks.getUsers.mockResolvedValue(page([failed]))
    mocks.retryUserDeletion.mockResolvedValue({ status: 'RUNNING', phase: 'LOCATION_DATA', deletedGpsPoints: 0 })

    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('Deletion failed')

    await vi.advanceTimersByTimeAsync(20000)
    expect(mocks.getUsers).toHaveBeenCalledTimes(1)

    await wrapper.find('button[aria-label="Retry deletion"]').trigger('click')
    await flushPromises()
    expect(mocks.retryUserDeletion).toHaveBeenCalledWith('user-1')
    expect(wrapper.text()).toContain('Deleting')

    await vi.advanceTimersByTimeAsync(5000)
    expect(mocks.getUsers).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
