import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

vi.hoisted(() => {
  const storage = new Map()
  const localStorageShim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: localStorageShim })
  Object.defineProperty(window, 'localStorage', { configurable: true, value: localStorageShim })
})

const job = ref(null)
const getJobDiagnostics = vi.fn()
const toastAdd = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { jobId: 'job-1' } }),
  useRouter: () => ({ push: vi.fn() })
}))
vi.mock('@/composables/useTimelineJobProgress', () => ({
  useTimelineJobProgress: () => ({ jobProgress: job, error: ref(null), startPolling: vi.fn(), fetchProgress: vi.fn() })
}))
vi.mock('@/stores/timeline', () => ({ useTimelineStore: () => ({ getJobDiagnostics }) }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: toastAdd }) }))
vi.mock('@/components/ui/layout/AppLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }))
vi.mock('@/components/ui/layout/PageContainer.vue', () => ({ default: { template: '<main><slot /></main>' } }))

const stubs = {
  Card: { template: '<section><slot name="title" /><slot name="content" /></section>' },
  Button: { props: ['label'], emits: ['click'], template: '<button @click="$emit(\'click\')">{{ label }}</button>' },
  Tag: { props: ['value'], template: '<span class="tag">{{ value }}</span>' },
  ProgressBar: true,
  ProgressSpinner: true,
  Message: { template: '<div class="message"><slot /></div>' },
  Dialog: {
    props: ['visible', 'header'],
    template: '<div v-if="visible" class="dialog"><h2>{{ header }}</h2><slot /><slot name="footer" /></div>'
  }
}

const MIB = 1024 * 1024
const step = (phase, fallback, durationMs, extra = {}) => ({
  phase,
  title: { key: `timelineJobs.steps.${phase}`, fallback },
  startedAt: '2026-11-24T10:00:00Z',
  endedAt: '2026-11-24T10:01:00Z',
  durationMs,
  failed: false,
  counters: {},
  memoryEnd: { heapUsedBytes: 50 * MIB, processBytes: 200 * MIB, containerBytes: -1 },
  memoryMax: { heapUsedBytes: 100 * MIB, processBytes: 300 * MIB, containerBytes: -1 },
  ...extra
})

const baseJob = (overrides) => ({
  jobId: 'job-1',
  type: 'FULL_REBUILD',
  status: 'COMPLETED',
  currentStep: { key: 'timelineJobs.progressMessages.completed', fallback: 'Timeline generation completed' },
  progressPercentage: 100,
  startTime: '2026-11-24T10:00:00Z',
  endTime: '2026-11-24T10:00:32Z',
  durationMs: 32000,
  steps: [],
  plannedSteps: ['preparing', 'processingPoints', 'geocoding', 'postProcessingTrips', 'persisting'],
  summary: {},
  resources: { cpuCount: 2, heapMaxBytes: 716 * MIB, containerLimitBytes: 1024 * MIB },
  details: {},
  ...overrides
})

let Page
const mountPage = () => mount(Page, { global: { stubs } })

describe('TimelineJobDetailsPage', () => {
  beforeAll(async () => {
    localStorage.setItem('userInfo', JSON.stringify({ timezone: 'UTC', dateFormat: 'YMD', timeFormat: '24h' }))
    Page = (await import('./TimelineJobDetailsPage.vue')).default
  })

  beforeEach(() => {
    getJobDiagnostics.mockReset()
    toastAdd.mockReset()
  })

  it('shows the steps a running job went through, its live counters and the steps still to come', () => {
    job.value = baseJob({
      status: 'RUNNING',
      endTime: null,
      progressPercentage: 60,
      steps: [
        step('processingPoints', 'Processing GPS points', 20000, { counters: { processedPoints: 8757358, totalPoints: 8757358 } }),
        step('geocoding', 'Naming stays', 4000, {
          endedAt: null,
          counters: { totalLocations: 200, totalResolved: 120, cachedResolved: 100, externalPending: 80 }
        })
      ]
    })

    const text = mountPage().text()

    expect(text).toContain('Processing GPS points')
    expect(text).toContain('Processing GPS Points: 8,757,358 / 8,757,358')
    expect(text).toContain('Naming stays')
    expect(text).toContain('Reverse Geocoding: 120 / 200 locations')
    expect(text).toContain('Pending: 80')
    // Planned steps after the current one, not the ones before it
    expect(text).toContain('Post-processing trips')
    expect(text).toContain('Saving the timeline')
    expect(text).not.toContain('Preparing')
    expect(text).toContain('Pending')
  })

  it('shows what a finished job produced, its limits and which step was slowest and used most memory', () => {
    job.value = baseJob({
      summary: { rebuiltFrom: 'all', gpsPoints: 8757358, stays: 23369, trips: 23791, dataGaps: 491, correctionsApplied: 970, correctionsNotApplied: 0 },
      steps: [
        step('processingPoints', 'Processing GPS points', 20000),
        step('corrections', 'Applying corrections', 3000, {
          memoryMax: { heapUsedBytes: 400 * MIB, processBytes: 900 * MIB, containerBytes: 950 * MIB }
        }),
        step('persisting', 'Saving the timeline', 9000)
      ]
    })

    const wrapper = mountPage()
    const text = wrapper.text()

    expect(text).toContain('Full regeneration')
    expect(text).toContain('8,757,358')
    expect(text).toContain('23,369 stays, 23,791 trips, 491 data gaps')
    expect(text).toContain('970 applied, 0 not applied')
    expect(text).toContain('1 GiB')
    expect(text).toContain('Highest memory: heap 400 MiB, process 900 MiB, container 950 MiB')
    // A value the platform does not report (container memory outside a container) is left out
    expect(text).toContain('Highest memory: heap 100 MiB, process 300 MiB')
    expect(text).not.toContain('container —')
    // No pending steps once the job is done
    expect(text).not.toContain('Post-processing trips')

    const items = wrapper.findAll('.step-item')
    expect(items).toHaveLength(3)
    expect(items[0].text()).toContain('Slowest')
    expect(items[1].text()).toContain('Memory peak')
    expect(items[2].text()).not.toContain('Slowest')
  })

  it('gives short steps a decimal duration and every step its share of the job', () => {
    job.value = baseJob({
      durationMs: 5000,
      steps: [
        step('preparing', 'Preparing', 550, { counters: { totalGpsPoints: 342507 } }),
        step('processingPoints', 'Processing GPS points', 1700),
        step('finalizing', 'Finalizing', 20),
        step('persisting', 'Saving the timeline', 12500)
      ]
    })

    const items = mountPage().findAll('.step-item')

    expect(items[0].find('.step-duration').text()).toBe('0.6s')
    expect(items[0].find('.step-share-label').text()).toBe('11% of the job')
    expect(items[1].find('.step-duration').text()).toBe('1.7s')
    expect(items[2].find('.step-duration').text()).toBe('<0.1s')
    expect(items[2].find('.step-share-label').text()).toBe('<1% of the job')
    expect(items[3].find('.step-duration').text()).toBe('12s')
    // A counter the page does not show leaves no empty box behind
    expect(items[0].find('.step-details').exists()).toBe(false)
  })

  it('leaves out memory values the platform does not report', () => {
    job.value = baseJob({
      steps: [step('persisting', 'Saving the timeline', 1000, {
        memoryMax: { heapUsedBytes: 205 * MIB, processBytes: -1, containerBytes: -1 }
      })]
    })

    const memory = mountPage().find('.step-memory').text()
    expect(memory).toBe('Highest memory: heap 205 MiB')
  })

  it('marks the step a failed job stopped in', () => {
    job.value = baseJob({
      status: 'FAILED',
      errorMessage: 'boom',
      steps: [step('processingPoints', 'Processing GPS points', 20000), step('geocoding', 'Naming stays', 1000, { failed: true })]
    })

    const items = mountPage().findAll('.step-item')
    expect(items[1].classes()).toContain('step-failed')
    expect(items[1].text()).toContain('Failed here')
  })

  it('says when the backend has no memory limit', () => {
    job.value = baseJob({ resources: { cpuCount: 4, heapMaxBytes: 5 * 1024 * MIB, containerLimitBytes: -1 } })

    expect(mountPage().text()).toContain('None (heap up to 5 GiB)')
  })

  it('shows the diagnostics of the job and copies them', async () => {
    job.value = baseJob({ steps: [step('persisting', 'Saving the timeline', 1000)] })
    const diagnostics = { job: { id: 'job-1', type: 'FULL_REBUILD' }, steps: [{ phase: 'persisting' }] }
    getJobDiagnostics.mockResolvedValue(diagnostics)
    const writeText = vi.fn().mockResolvedValue()
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })

    const wrapper = mountPage()
    await wrapper.findAll('button').find((button) => button.text() === 'Copy diagnostics').trigger('click')
    await flushPromises()

    expect(getJobDiagnostics).toHaveBeenCalledWith('job-1')
    const json = wrapper.find('[data-testid="job-diagnostics-json"]')
    expect(JSON.parse(json.text())).toEqual(diagnostics)
    expect(wrapper.text()).toContain('It contains no coordinates, place names, notes or event times.')

    await wrapper.findAll('button').find((button) => button.text() === 'Copy').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith(JSON.stringify(diagnostics, null, 2))
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }))
  })

  it('says when the diagnostics cannot be loaded', async () => {
    job.value = baseJob({})
    getJobDiagnostics.mockRejectedValue(new Error('offline'))

    const wrapper = mountPage()
    await wrapper.findAll('button').find((button) => button.text() === 'Copy diagnostics').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Could not load the diagnostics')
    expect(wrapper.find('[data-testid="job-diagnostics-json"]').exists()).toBe(false)
  })
})
