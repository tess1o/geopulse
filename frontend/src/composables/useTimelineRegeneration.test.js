import { ref } from 'vue'
import { useTimelineRegeneration } from './useTimelineRegeneration'

const mocks = vi.hoisted(() => ({
  toastAdd: vi.fn(),
  confirmRequire: vi.fn(),
  checkActiveJob: vi.fn(),
  startPolling: vi.fn(),
  stopPolling: vi.fn()
}))

vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mocks.toastAdd })
}))

vi.mock('primevue/useconfirm', () => ({
  useConfirm: () => ({ require: mocks.confirmRequire })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('@/composables/useTimelineJobCheck', () => ({
  useTimelineJobCheck: () => ({ checkActiveJob: mocks.checkActiveJob })
}))

vi.mock('@/composables/useTimelineJobProgress', () => ({
  useTimelineJobProgress: () => ({
    jobProgress: ref(null),
    error: ref(null),
    startPolling: mocks.startPolling,
    stopPolling: mocks.stopPolling,
    fetchProgress: vi.fn(),
    reset: vi.fn()
  })
}))

vi.mock('@/utils/apiErrorDetail', () => ({
  formatApiErrorDetail: (error, fallback) => fallback
}))

const options = {
  modalType: 'preferences',
  successMessage: 'Started',
  errorMessage: 'Failed',
  noJobMessage: 'Saved, regenerating in the background'
}

const toastSeverities = () => mocks.toastAdd.mock.calls.map(([toast]) => toast.severity)

describe('useTimelineRegeneration.withTimelineRegeneration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.checkActiveJob.mockResolvedValue({ hasActiveJob: false, jobId: null })
  })

  it('tracks the returned job', async () => {
    const onSuccess = vi.fn()
    const { withTimelineRegeneration, currentJobId, timelineRegenerationVisible } = useTimelineRegeneration()

    await withTimelineRegeneration(() => Promise.resolve('job-1'), { ...options, onSuccess })

    expect(currentJobId.value).toBe('job-1')
    expect(timelineRegenerationVisible.value).toBe(true)
    expect(toastSeverities()).toEqual(['success'])
    expect(onSuccess).toHaveBeenCalledOnce()
  })

  it('treats a saved change without a job as success, not an error', async () => {
    const onSuccess = vi.fn()
    const { withTimelineRegeneration, currentJobId, timelineRegenerationVisible } = useTimelineRegeneration()

    await withTimelineRegeneration(() => Promise.resolve(null), { ...options, onSuccess })

    expect(currentJobId.value).toBeNull()
    expect(timelineRegenerationVisible.value).toBe(false)
    expect(mocks.toastAdd).toHaveBeenCalledOnce()
    expect(mocks.toastAdd.mock.calls[0][0]).toMatchObject({ severity: 'info', detail: options.noJobMessage })
    expect(onSuccess).toHaveBeenCalledOnce()
  })

  it('ignores a second call while the first is still starting', async () => {
    let resolveAction
    const action = vi.fn(() => new Promise(resolve => { resolveAction = resolve }))
    const { withTimelineRegeneration, currentJobId } = useTimelineRegeneration()

    const first = withTimelineRegeneration(action, options)
    const second = withTimelineRegeneration(action, options)
    await second
    await vi.waitFor(() => expect(action).toHaveBeenCalled())
    resolveAction('job-1')
    await first

    expect(action).toHaveBeenCalledOnce()
    expect(mocks.checkActiveJob).toHaveBeenCalledOnce()
    expect(currentJobId.value).toBe('job-1')
    expect(toastSeverities()).toEqual(['success'])
  })

  it('reports a failed action and allows another attempt', async () => {
    const { withTimelineRegeneration, timelineRegenerationVisible } = useTimelineRegeneration()

    await withTimelineRegeneration(() => Promise.reject(new Error('boom')), options)
    await withTimelineRegeneration(() => Promise.resolve('job-2'), options)

    expect(toastSeverities()).toEqual(['error', 'success'])
    expect(timelineRegenerationVisible.value).toBe(true)
  })
})
