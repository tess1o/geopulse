import { shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import TimelineContainer from './TimelineContainer.vue'
import { useTimelineStore } from '@/stores/timeline'
const { addToast } = vi.hoisted(() => ({ addToast: vi.fn() }))
vi.mock('../dialogs/TripClassificationDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../dialogs/TimelineEventExplanationDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../dialogs/TripStaySplitDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../dialogs/TripMapMatchingDetailsDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: addToast }) }))
const range = ['2026-10-25T00:00:00Z', '2026-10-26T00:00:00Z']
function setup(props = {}) {
  const pinia = createPinia()
  vi.spyOn(useTimelineStore(pinia), 'fetchTimelineEdits').mockResolvedValue([])
  const wrapper = shallowMount(TimelineContainer, {
    props: { dateRange: range, showTimelineLabels: false, loadNotes: false, loadImmichPhotos: false, ...props },
    global: { plugins: [pinia], stubs: { Timeline: { name: 'Timeline', props: ['value'], template: '<div />' }, RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' }, Button: { template: '<button><slot /></button>' } } }
  })
  return { wrapper }
}

const gap = (id, extra = {}) => ({ id, type: 'dataGap', timestamp: '2026-01-02T12:00:00Z', startTime: '2026-01-02T12:00:00Z', endTime: '2026-12-30T12:00:00Z', durationSeconds: 31276800, ...extra })
const trip = (id, timestamp = '2026-01-01T12:00:00Z') => ({ id, type: 'trip', timestamp, tripDuration: 3600, movementType: 'UNKNOWN' })
const year = ['2026-01-01T00:00:00Z', '2027-01-01T00:00:00Z']
const shownItems = wrapper => wrapper.findAllComponents({ name: 'Timeline' }).flatMap(component => component.props('value'))

describe('Unknown Activity display', () => {
  it('shows gaps by default in shared views', () => {
    const item = gap(1)
    const { wrapper } = setup({ timelineData: [item], dateRange: year, isPublicView: true, readOnly: true })
    expect(shownItems(wrapper)).toContainEqual(item)
    expect(wrapper.findAll('.date-group').length).toBeGreaterThan(300)
  })
  it('hides gap-only dates in a sparse year while preserving UNKNOWN trips and source data', async () => {
    const data = [trip(1), gap(2), trip(3, '2026-12-31T12:00:00Z')]
    const { wrapper } = setup({ timelineData: data, dateRange: year, showUnknownActivity: false })
    expect(wrapper.findAll('.date-group')).toHaveLength(2)
    expect(shownItems(wrapper)).toEqual([data[0], data[2]])
    expect(wrapper.props('timelineData')).toEqual(data)
    await wrapper.setProps({ showUnknownActivity: true })
    expect(shownItems(wrapper)).toContainEqual(data[1])
  })
  it.each([
    { endTime: '2026-01-02T13:00:00Z', durationSeconds: 3600 },
    { endTime: '2026-01-03T13:00:00Z', durationSeconds: 90000 },
    { ongoing: true }, {}
  ])('explains gap-only selections without date headings: %o', extra => {
    const { wrapper } = setup({ timelineData: [gap(1, extra)], dateRange: year, showUnknownActivity: false })
    expect(wrapper.findAll('.date-group')).toHaveLength(0)
    expect(wrapper.text()).toContain('No recorded stays or trips in this period. Unknown Activity is hidden.')
    const settingsButton = wrapper.find('button[to="/app/profile?tab=timeline&setting=showUnknownActivity"]')
    expect(settingsButton.attributes('as')).toBe('router-link')
    expect(settingsButton.attributes('label')).toBe('Timeline Display settings')
  })
  it('keeps no-data messaging and suppresses hidden-gap messaging while loading', async () => {
    const { wrapper } = setup({ timelineData: [], timelineNoData: true, showUnknownActivity: false })
    expect(wrapper.find('.timeline-no-data').isVisible()).toBe(true)
    expect(wrapper.text()).not.toContain('Unknown Activity is hidden.')
    await wrapper.setProps({ timelineData: [gap(1)], timelineNoData: false, timelineDataLoading: true })
    expect(wrapper.text()).not.toContain('Unknown Activity is hidden.')
  })
  it('keeps stays and trips on a mixed single day', () => {
    const stay = { id: 2, type: 'stay', timestamp: '2026-01-02T15:00:00Z', stayDuration: 3600 }
    const data = [gap(1), trip(3, '2026-01-02T13:00:00Z'), stay]
    const { wrapper } = setup({ timelineData: data, dateRange: ['2026-01-02T00:00:00Z', '2026-01-02T23:59:59Z'], showUnknownActivity: false })
    expect(shownItems(wrapper)).toEqual([data[1], stay])
    expect(wrapper.text()).not.toContain('Unknown Activity is hidden.')
  })
  it('filters before pagination and counts only displayed items', async () => {
    const gaps = Array.from({ length: 60 }, (_, i) => gap(i))
    const trips = Array.from({ length: 55 }, (_, i) => trip(100 + i))
    const { wrapper } = setup({ timelineData: [...gaps, ...trips], dateRange: ['2026-01-01T00:00:00Z', '2026-01-01T23:59:59Z'], showUnknownActivity: false })
    expect(shownItems(wrapper)).toEqual(trips.slice(0, 50))
    expect(wrapper.find('.timeline-warning').text()).toContain('55')
    await wrapper.find('.load-more-button').trigger('click')
    expect(shownItems(wrapper)).toEqual(trips)
    expect(wrapper.find('.timeline-warning').exists()).toBe(false)
  })
})
