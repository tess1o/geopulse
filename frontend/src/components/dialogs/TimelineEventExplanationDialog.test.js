import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const fetchTimelineEventExplanation = vi.fn()
const adapter = { initialize: vi.fn(), render: vi.fn(), highlight: vi.fn(), fitTo: vi.fn(), destroy: vi.fn() }
const push = vi.fn()

vi.mock('@/stores/timeline', () => ({ useTimelineStore: () => ({ fetchTimelineEventExplanation }) }))
vi.mock('@/maps/explanation/runtime/createExplanationMapAdapter', () => ({
  createExplanationMapAdapter: vi.fn(() => adapter)
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({
    formatTimeAt: value => String(value).slice(11, 16),
    formatDateTimeDisplayAt: value => String(value).slice(0, 16).replace('T', ' ')
  })
}))

import TimelineEventExplanationDialog from './TimelineEventExplanationDialog.vue'

const stayDetails = {
  kind: 'STAY',
  id: 7,
  startTime: '2026-10-08T17:00:00Z',
  endTime: '2026-10-08T18:02:00Z',
  radiusMeters: 50,
  centre: { latitude: 49.84, longitude: 24.03 },
  location: { latitude: 49.84, longitude: 24.03 },
  settings: { staypointRadiusMeters: 50, staypointMinDurationMinutes: 7, staypointMaxAccuracyThreshold: 60 },
  explanation: {
    start: { code: 'ARRIVAL', params: { stopSeconds: 240, points: 1, stopSpeedKmh: 9 } },
    notes: [
      { code: 'CONFIRMED', params: { radiusMeters: 50, minMinutes: 7 } },
      { code: 'MERGED', params: { stays: 2 } }
    ],
    end: { code: 'MOVED_AWAY', params: { distanceMeters: 177, radiusMeters: 50 } }
  },
  points: [
    { timestamp: '2026-10-08T17:00:00Z', latitude: 49.84, longitude: 24.03, part: 'EVENT', used: true, reasons: ['ARRIVAL'] },
    { timestamp: '2026-10-08T17:30:00Z', latitude: 49.8402, longitude: 24.03, part: 'EVENT', used: false, accuracyMeters: 180, reasons: [] },
    { timestamp: '2026-10-08T18:03:00Z', latitude: 49.842, longitude: 24.03, part: 'AFTER', used: true, reasons: ['MOVED_AWAY'] }
  ],
  filtered: { inEvent: 1, inContext: 0, thresholdMeters: 60 },
  ingestionFilters: [{ sourceType: 'OWNTRACKS', name: 'phone', maxAccuracyMeters: 100 }],
  totalEventPoints: 2,
  truncated: false
}

const ButtonStub = {
  props: ['label'],
  emits: ['click'],
  template: '<button type="button" class="stub-button" @click="$emit(\'click\')">{{ label }}</button>'
}

const mountDialog = (props) => mount(TimelineEventExplanationDialog, {
  props: { visible: true, ...props },
  global: {
    stubs: {
      Dialog: { props: ['header'], template: '<section><h2>{{ header }}</h2><slot /><footer><slot name="footer" /></footer></section>' },
      Button: ButtonStub,
      Message: { template: '<div class="stub-message"><slot /></div>' },
      ProgressSpinner: true,
      MapContainer: {
        emits: ['map-ready'],
        mounted() { this.$emit('map-ready', {}) },
        template: '<div class="stub-map"></div>'
      }
    }
  }
})

describe('TimelineEventExplanationDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchTimelineEventExplanation.mockResolvedValue(stayDetails)
  })

  it('tells how the stay was decided, with times, settings and what was left out', async () => {
    const wrapper = mountDialog({ kind: 'stay', item: { id: 7, type: 'stay' } })
    await flushPromises()

    expect(fetchTimelineEventExplanation).toHaveBeenCalledWith('stay', 7)
    expect(wrapper.find('h2').text()).toBe('Why this stay?')
    const steps = wrapper.findAll('.explanation-step').map(step => step.text())
    expect(steps[0]).toContain('You arrived at 17:00')
    expect(steps[steps.length - 1]).toContain('Ended at 18:03')
    expect(steps[steps.length - 1]).toContain('Stay Detection Radius')

    // The merge is unusual, so it is pointed out above the map as well
    expect(wrapper.findAll('.stub-message').map(message => message.text()).join(' ')).toContain('were merged into this one')
    expect(wrapper.text()).toContain('1 of 2 GPS points were left out')
    expect(wrapper.text()).toContain('GPS Accuracy Threshold')
    expect(wrapper.text()).toContain('OWNTRACKS · phone')

    expect(adapter.render).toHaveBeenCalledTimes(1)
    expect(adapter.render.mock.calls[0][0].circle.radiusMeters).toBe(50)
  })

  it('shows the points of a step on the map when it is selected, and all again when it is selected twice', async () => {
    const wrapper = mountDialog({ kind: 'stay', item: { id: 7, type: 'stay' } })
    await flushPromises()

    const endStep = wrapper.findAll('.explanation-step-button').at(-1)
    await endStep.trigger('click')
    expect(adapter.highlight).toHaveBeenLastCalledWith([2])
    expect(adapter.fitTo).toHaveBeenLastCalledWith([[49.842, 24.03], [49.842, 24.03]])

    await endStep.trigger('click')
    expect(adapter.highlight).toHaveBeenLastCalledWith(null)
  })

  it('opens a setting in timeline preferences', async () => {
    const wrapper = mountDialog({ kind: 'stay', item: { id: 7, type: 'stay' } })
    await flushPromises()

    await wrapper.find('.explanation-step-settings a').trigger('click')
    expect(push).toHaveBeenCalledWith({
      path: '/app/timeline/preferences',
      query: { tab: 'staypoints', setting: 'staypointVelocityThreshold' }
    })
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('links a trip to its classification, and only a trip', async () => {
    fetchTimelineEventExplanation.mockResolvedValue({ ...stayDetails, kind: 'TRIP', explanation: { start: { code: 'LEFT_STAY', params: {} } } })
    const trip = { id: 9, type: 'trip' }
    const tripDialog = mountDialog({ kind: 'trip', item: trip })
    await flushPromises()

    const classification = tripDialog.findAll('.stub-button').find(button => button.text() === 'Why this movement type?')
    await classification.trigger('click')
    expect(tripDialog.emitted('show-classification')[0]).toEqual([trip])

    const stayDialog = mountDialog({ kind: 'stay', item: { id: 7, type: 'stay' } })
    await flushPromises()
    expect(stayDialog.text()).not.toContain('Why this movement type?')
  })

  it('shows the error when the explanation cannot be loaded', async () => {
    fetchTimelineEventExplanation.mockRejectedValue(new Error('boom'))
    const wrapper = mountDialog({ kind: 'dataGap', item: { id: 3, type: 'dataGap' } })
    await flushPromises()

    expect(wrapper.find('.stub-message').exists()).toBe(true)
    expect(adapter.render).not.toHaveBeenCalled()
  })
})
