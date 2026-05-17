import { mount, flushPromises } from '@vue/test-utils'
import MobilePage from './MobilePage.vue'
import apiService from '@/utils/apiService'

vi.mock('@/utils/apiService', () => ({
  default: {
    get: vi.fn(),
    logoutStrict: vi.fn(),
  },
}))

describe('MobilePage', () => {
  const originalLocation = window.location
  const originalClose = window.close
  const originalVisibilityState = Object.getOwnPropertyDescriptor(document, 'visibilityState')
  let assignMock
  let closeMock

  beforeEach(() => {
    vi.clearAllMocks()
    assignMock = vi.fn()
    closeMock = vi.fn()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        ...originalLocation,
        assign: assignMock,
      },
    })
    Object.defineProperty(window, 'close', {
      configurable: true,
      value: closeMock,
    })
  })

  afterEach(() => {
    vi.useRealTimers()

    if (originalVisibilityState) {
      Object.defineProperty(document, 'visibilityState', originalVisibilityState)
    }

    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    })
    Object.defineProperty(window, 'close', {
      configurable: true,
      value: originalClose,
    })
  })

  it('redirects immediately using deeplinkUrl returned by the mobile auth endpoint', async () => {
    apiService.get.mockResolvedValue({
      data: {
        code: 'generated-code',
        deeplinkUrl: 'app://auth/code/exchange',
      },
    })
    apiService.logoutStrict.mockResolvedValue(undefined)

    const wrapper = mount(MobilePage)
    await flushPromises()

    expect(apiService.get).toHaveBeenCalledWith('/auth/mobile')
    expect(apiService.logoutStrict).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Opening the app...')
    expect(assignMock).toHaveBeenCalledWith('app://auth/code/exchange?code=generated-code')
    expect(wrapper.find('.mobile-spinner').exists()).toBe(true)
  })

  it('shows an error message when deeplinkUrl is missing from the payload', async () => {
    apiService.get.mockResolvedValue({
      data: {
        code: 'generated-code',
      },
    })

    const wrapper = mount(MobilePage)
    await flushPromises()

    expect(wrapper.text()).toContain('Mobile authentication payload was not returned.')
    expect(apiService.logoutStrict).not.toHaveBeenCalled()
    expect(assignMock).not.toHaveBeenCalled()
    expect(wrapper.find('.mobile-spinner').exists()).toBe(false)
  })

  it('shows timeout message when app opening does not switch tab', async () => {
    vi.useFakeTimers()
    apiService.get.mockResolvedValue({
      data: {
        code: 'generated-code',
        deeplinkUrl: 'app://auth/code/exchange',
      },
    })
    apiService.logoutStrict.mockResolvedValue(undefined)

    const wrapper = mount(MobilePage)
    await flushPromises()

    await vi.advanceTimersByTimeAsync(4000)

    expect(wrapper.text()).toContain('Opening the app timed out. Please return to the app and try again.')
    expect(wrapper.find('.mobile-spinner').exists()).toBe(false)
  })

  it('clears timeout when page becomes hidden', async () => {
    vi.useFakeTimers()
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => 'hidden',
    })
    apiService.get.mockResolvedValue({
      data: {
        code: 'generated-code',
        deeplinkUrl: 'app://auth/code/exchange',
      },
    })
    apiService.logoutStrict.mockResolvedValue(undefined)

    const wrapper = mount(MobilePage)
    await flushPromises()

    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(4000)

    expect(wrapper.text()).toContain('Opening the app...')
    expect(wrapper.find('.mobile-spinner').exists()).toBe(true)
  })

  it('tries to close page 10 seconds after opening timeout', async () => {
    vi.useFakeTimers()
    apiService.get.mockResolvedValue({
      data: {
        code: 'generated-code',
        deeplinkUrl: 'app://auth/code/exchange',
      },
    })
    apiService.logoutStrict.mockResolvedValue(undefined)

    mount(MobilePage)
    await flushPromises()

    await vi.advanceTimersByTimeAsync(4000)
    expect(closeMock).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(10000)
    expect(closeMock).toHaveBeenCalledTimes(1)
  })

  it('cleans scheduled timers on unmount and never calls close', async () => {
    vi.useFakeTimers()
    apiService.get.mockResolvedValue({
      data: {
        code: 'generated-code',
        deeplinkUrl: 'app://auth/code/exchange',
      },
    })
    apiService.logoutStrict.mockResolvedValue(undefined)

    const wrapper = mount(MobilePage)
    await flushPromises()

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(20000)

    expect(closeMock).not.toHaveBeenCalled()
  })

  it('does not redirect when browser logout fails before deeplink handoff', async () => {
    apiService.get.mockResolvedValue({
      data: {
        code: 'generated-code',
        deeplinkUrl: 'app://auth/code/exchange',
      },
    })
    apiService.logoutStrict.mockRejectedValue(new Error('logout failed'))

    const wrapper = mount(MobilePage)
    await flushPromises()

    expect(apiService.logoutStrict).toHaveBeenCalled()
    expect(assignMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Failed to complete mobile authentication handoff.')
    expect(wrapper.find('.mobile-spinner').exists()).toBe(false)
  })
})
