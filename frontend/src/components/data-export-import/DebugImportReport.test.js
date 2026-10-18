import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DebugImportReport from './DebugImportReport.vue'

const stubs = {
  Button: {
    name: 'Button',
    props: ['label'],
    emits: ['click'],
    template: '<button @click="$emit(\'click\')">{{ label }}</button>'
  },
  Tag: { props: ['value'], template: '<span class="tag">{{ value }}</span>' }
}

const mountReport = (report) => mount(DebugImportReport, { props: { report }, global: { stubs } })

const trip = (start, durationSeconds, movementType) => ({
  type: 'TRIP',
  start,
  end: new Date(new Date(start).getTime() + durationSeconds * 1000).toISOString(),
  durationSeconds,
  movementType
})

const baseReport = (comparison, extra = {}) => ({
  exportVersion: '1.2',
  exporterAppVersion: '2.0.0',
  importerAppVersion: '2.0.0',
  timezone: 'UTC',
  timezoneApplied: true,
  timelineConfigApplied: true,
  issue: { description: 'Drive home is missing', time: '2026-10-08T12:30:00Z' },
  requestedRange: { start: '2026-10-08T00:00:00Z', end: '2026-10-08T23:59:59Z' },
  imported: { gpsPoints: 120, favoriteLocations: 0, favoriteAreas: 5, geocodingLocations: 3 },
  gpsSources: [],
  warnings: [],
  comparison,
  ...extra
})

describe('DebugImportReport', () => {
  it('says the timeline was reproduced when every event matches', () => {
    const event = trip('2026-10-08T05:49:32Z', 1063, 'CAR')
    const wrapper = mountReport(baseReport({
      available: true, matches: true, matching: 1, different: 0, missing: 0, extra: 0,
      from: '2026-10-08T00:00:00Z', to: '2026-10-08T23:59:59Z',
      rows: [{ status: 'MATCH', expected: event, actual: event }]
    }))

    expect(wrapper.text()).toContain('Timeline reproduced')
    expect(wrapper.text()).toContain('Drive home is missing')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.find('.report-diff').exists()).toBe(false)
  })

  it('highlights what differs and lists the warnings', () => {
    const wrapper = mountReport(baseReport({
      available: true, matches: false, matching: 0, different: 1, missing: 0, extra: 0,
      from: '2026-10-08T00:00:00Z', to: '2026-10-08T23:59:59Z',
      rows: [{
        status: 'DIFFERENT',
        expected: trip('2026-10-08T05:49:32Z', 1663, 'CAR'),
        actual: trip('2026-10-08T05:49:32Z', 1063, 'CAR')
      }]
    }, {
      warnings: [
        { code: 'GPS_FILTERS_ACTIVE', params: {} },
        { code: 'TIMELINE_DIFFERS', params: { different: 1, missing: 0, extra: 0 } }
      ]
    }))

    expect(wrapper.text()).toContain('Timeline differs')
    // Duration and end time differ, the movement type does not
    expect(wrapper.findAll('.report-diff').map(node => node.text())).toContain('27m 43s')
    expect(wrapper.findAll('.report-diff').some(node => node.text() === 'CAR')).toBe(false)
    const warnings = wrapper.findAll('.report-warning').map(node => node.text())
    expect(warnings[0]).toContain('differs from what the user saw')
    expect(warnings[1]).toContain('GPS sources filter points')
  })

  it('opens the day the user reported', async () => {
    const wrapper = mountReport(baseReport({ available: false, rows: [] }))

    expect(wrapper.text()).toContain('Nothing to compare')
    await wrapper.findComponent({ name: 'Button' }).trigger('click')

    expect(wrapper.emitted('open-timeline')).toEqual([[{ start: '2026-10-08', end: '2026-10-08' }]])
  })
})
