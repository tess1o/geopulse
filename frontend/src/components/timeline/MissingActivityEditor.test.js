import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import Editor from './MissingActivityEditor.vue'
import { useTimelineStore } from '@/stores/timeline'
import { useTripsStore } from '@/stores/trips'
import { useTimezone } from '@/composables/useTimezone'
import { timelinePickerToUtc } from '@/utils/timelineEditTime'

vi.mock('@/components/maps/MapContainer.vue', () => ({ default: { name: 'MapContainer', emits: ['map-click', 'map-ready'], data: () => ({ map: {} }), template: '<div class="map-stub"><slot name="overlays" :map="map" :isReady="true" /></div>' } }))
const place = { latitude: 49.82, longitude: 24.01, locationName: 'Cafe', source: 'custom' }
const DateStub = { name: 'ActivityDateTimeField', props: ['modelValue', 'contextDate'], emits: ['update:modelValue'], template: '<input />' }
const SelectStub = { name: 'Select', props: ['modelValue', 'options', 'ariaLabel'], emits: ['update:modelValue'], template: '<select :aria-label="ariaLabel" />' }
const button = { props: ['label', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>' }
const PlaceStub = { name: 'TimelinePlacePicker', props: ['modelValue', 'point', 'lookupAt', 'expanded'], emits: ['update:modelValue', 'preview', 'update:expanded', 'request-map'], template: '<button class="pick-place" @click="$emit(\'update:modelValue\', {latitude:49.82,longitude:24.01,locationName:\'Cafe\',source:\'custom\'})">Pick place</button>' }
const times = { startTime: '2026-09-15T14:24:00Z', endTime: '2026-09-15T14:27:00Z' }
const previewResult = { events: [{ type: 'STAY', start: times.startTime, end: times.endTime, label: 'Cafe', locationName: 'Cafe' }] }
const open = async props => {
  const wrapper = mount(Editor, { props: { visible: false, ...props }, global: { stubs: {
    Dialog: { template: '<div><slot /><slot name="footer" /></div>' }, Button: button,
    InputText: { props: ['modelValue'], template: '<input />' }, ActivityDateTimeField: DateStub, Select: SelectStub, TimelinePlacePicker: PlaceStub, RawGpsPointsLayer: true, TripReconstructionMapOverlay: true
  } } })
  await wrapper.setProps({ visible: true }); await flushPromises(); return wrapper
}
const click = async (wrapper, label) => { await wrapper.findAll('button').find(button => button.text() === label).trigger('click'); await flushPromises() }
const stay = async wrapper => {
  wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?').vm.$emit('update:modelValue', 'STAY')
  await flushPromises(); await wrapper.find('.pick-place').trigger('click'); await flushPromises()
}
beforeEach(() => setActivePinia(createPinia()))
describe('Unified missing activity editor', () => {
  it('shows times and activity choice together without guessing times or confirming a suggested location', async () => {
    const wrapper = await open({ contextStartTime: '2026-09-15T00:00:00Z', locationSuggestion: place })
    expect(wrapper.text()).toContain('What happened?')
    expect(wrapper.findAllComponents(DateStub).map(component => component.props('modelValue'))).toEqual([null, null])
    expect(wrapper.findAllComponents(SelectStub)[0].props('modelValue')).toBeNull()
  })
  it('reviews and saves a correction without calling reconstruction or renaming a source', async () => {
    const store = useTimelineStore(), trips = useTripsStore()
    vi.spyOn(store, 'previewActivityCorrection').mockResolvedValue(previewResult)
    vi.spyOn(store, 'saveActivityCorrection').mockResolvedValue({ groupId: 'group', timelineJobId: 'job' })
    const legacy = vi.spyOn(trips, 'commitReconstruction')
    const wrapper = await open({ suggestedRange: times }); await stay(wrapper)
    await click(wrapper, 'Review changes')
    expect(wrapper.text()).toContain('Only these activities will be added')
    await click(wrapper, 'Save changes')
    expect(store.saveActivityCorrection).toHaveBeenCalledWith(expect.objectContaining({ activities: [expect.objectContaining({ segmentType: 'STAY', startTime: times.startTime, endTime: times.endTime, locationName: 'Cafe' })] }))
    expect(legacy).not.toHaveBeenCalled()
    expect(wrapper.emitted('committed')[0][0].groupId).toBe('group')
  })
  it('keeps the draft after an error and invalidates review when times change', async () => {
    const store = useTimelineStore()
    vi.spyOn(store, 'previewActivityCorrection').mockResolvedValue(previewResult)
    vi.spyOn(store, 'saveActivityCorrection').mockRejectedValue(new Error('Interval occupied'))
    const wrapper = await open({ suggestedRange: times }); await stay(wrapper); await click(wrapper, 'Review changes'); await click(wrapper, 'Save changes')
    expect(wrapper.find('.activity-error').exists()).toBe(true)
    expect(wrapper.findComponent(PlaceStub).props('modelValue')).toEqual(place)
    wrapper.findAllComponents(DateStub)[1].vm.$emit('update:modelValue', new Date('2026-09-15T14:28:00Z'))
    await flushPromises()
    expect(wrapper.find('.activity-review').exists()).toBe(false)
    expect(wrapper.findAll('button').some(button => button.text() === 'Save changes')).toBe(false)
  })
  it('uses two reversed GPS timestamps within the same editor and disables map placement while selecting', async () => {
    const points = [{ ...place, timestamp: times.endTime }, { ...place, timestamp: times.startTime }]
    const wrapper = await open({ recordedPoints: points }); await stay(wrapper); await click(wrapper, 'Use GPS timestamps')
    const selects = wrapper.findAllComponents(SelectStub)
    selects.find(select => select.props('ariaLabel') === 'First GPS point').vm.$emit('update:modelValue', points[0])
    selects.find(select => select.props('ariaLabel') === 'Second GPS point').vm.$emit('update:modelValue', points[1])
    wrapper.findComponent({ name: 'MapContainer' }).vm.$emit('map-click', { latlng: { lat: 1, lng: 2 } })
    await flushPromises()
    expect(wrapper.findComponent(PlaceStub).props('point')).toBeNull()
    await click(wrapper, 'Use these times')
    const values = wrapper.findAllComponents(DateStub).map(component => useTimezone().createDateTimeFromPicker(component.props('modelValue')).utc().toISOString())
    expect(values).toEqual([times.startTime.replace('Z', '.000Z'), times.endTime.replace('Z', '.000Z')])
    expect(wrapper.find('.gps-time-choices').exists()).toBe(false)
  })
  it('uses the only completed gap from the navbar context without making users re-enter its times', async () => {
    const wrapper = await open({ contextStartTime: '2026-09-15T00:00:00Z', availableGaps: [{ id: 1, ...times }] })
    const values = wrapper.findAllComponents(DateStub).map(field => useTimezone().createDateTimeFromPicker(field.props('modelValue')).utc().toISOString())
    expect(values).toEqual([times.startTime.replace('Z', '.000Z'), times.endTime.replace('Z', '.000Z')])
  })
  it('asks which gap to fill when several exist and keeps the calendar date anchored', async () => {
    const gaps = [{ id: 1, ...times }, { id: 2, startTime: '2026-09-15T16:00:00Z', endTime: '2026-09-15T16:30:00Z' }]
    const wrapper = await open({ contextStartTime: '2026-09-15T00:00:00Z', availableGaps: gaps })
    expect(wrapper.findAllComponents(DateStub).map(field => field.props('modelValue'))).toEqual([null, null])
    expect(wrapper.findAllComponents(DateStub)[0].props('contextDate')).toBe('2026-09-15T00:00:00Z')
    wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'Choose a gap on this date').vm.$emit('update:modelValue', gaps[1])
    await flushPromises()
    expect(wrapper.findAllComponents(DateStub)[0].props('modelValue')).toBeInstanceOf(Date)
  })
  it('shows stay and endpoint previews before a place is confirmed', async () => {
    const wrapper = await open({ suggestedRange: times })
    const type = wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?')
    type.vm.$emit('update:modelValue', 'STAY'); await flushPromises()
    wrapper.findComponent({ name: 'MapContainer' }).vm.$emit('map-click', { latlng: { lat: 50, lng: 30 } }); await flushPromises()
    expect(wrapper.findComponent({ name: 'TripReconstructionMapOverlay' }).props('contextPoints')).toContainEqual({ latitude: 50, longitude: 30, label: 'S' })
    expect(wrapper.findComponent(PlaceStub).props('modelValue')).toBeNull()
    type.vm.$emit('update:modelValue', 'TRIP'); await flushPromises()
    const pickers = wrapper.findAllComponents(PlaceStub)
    pickers[0].vm.$emit('preview', { latitude: 50, longitude: 30 })
    pickers[1].vm.$emit('preview', { latitude: 51, longitude: 31 }); await flushPromises()
    expect(wrapper.findComponent({ name: 'TripReconstructionMapOverlay' }).props('contextPoints')).toEqual([
      { latitude: 50, longitude: 30, label: '◇', draggable: false }
    ])
    pickers[0].vm.$emit('preview', null); pickers[1].vm.$emit('preview', null); await flushPromises()
    expect(wrapper.findComponent({ name: 'TripReconstructionMapOverlay' }).props('contextPoints')).toEqual([])
  })

  it('adds route points only in placement mode, supports moving and reordering, and keeps the map steady', async () => {
    const wrapper = await open({ suggestedRange: times })
    wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?').vm.$emit('update:modelValue', 'TRIP')
    await flushPromises()
    const pickers = wrapper.findAllComponents(PlaceStub)
    pickers[0].vm.$emit('update:modelValue', place)
    pickers[1].vm.$emit('update:modelValue', { ...place, latitude: 50, longitude: 25 })
    await flushPromises()
    const instance = { setView: vi.fn(), fitBounds: vi.fn() }
    const map = wrapper.findComponent({ name: 'MapContainer' })
    map.vm.$emit('map-ready', instance)
    await flushPromises()
    instance.setView.mockClear(); instance.fitBounds.mockClear()
    const point = async (lat, lng) => { map.vm.$emit('map-click', { latlng: { lat, lng } }); await flushPromises() }
    await point(49, 24)
    expect(wrapper.find('.route-points').exists()).toBe(false)
    await click(wrapper, 'Add route points')
    await point(49, 24); await point(49.5, 24.5)
    expect(wrapper.findAll('.route-points li')).toHaveLength(2)
    expect(instance.setView).not.toHaveBeenCalled()
    expect(instance.fitBounds).not.toHaveBeenCalled()
    await click(wrapper, 'Done placing points')
    await point(48, 23)
    expect(wrapper.findAll('.route-points li')).toHaveLength(2)
    await click(wrapper, 'Route point 1'); await point(49.1, 24.1)
    const markers = () => wrapper.findComponent({ name: 'TripReconstructionMapOverlay' }).props('contextPoints')
    expect(markers()[1]).toMatchObject({ latitude: 49.1, longitude: 24.1 })
    await wrapper.find('button[aria-label="Move route point 1 later"]').trigger('click'); await flushPromises()
    expect(markers()[1]).toMatchObject({ latitude: 49.5, longitude: 24.5 })
    pickers[1].vm.$emit('request-map'); await flushPromises(); await point(50.1, 25.1)
    expect(pickers[1].props('point')).toEqual({ latitude: 50.1, longitude: 25.1 })
  })

  it('drags stays, endpoints and intermediate points, clearing old place references', async () => {
    const wrapper = await open({ suggestedRange: times })
    const type = wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?')
    await stay(wrapper)
    const overlay = () => wrapper.findComponent({ name: 'TripReconstructionMapOverlay' })
    overlay().vm.$emit('context-point-dragged', { label: 'S', latitude: 50, longitude: 30 }); await flushPromises()
    expect(wrapper.findComponent(PlaceStub).props('modelValue')).toBeNull()
    expect(wrapper.findComponent(PlaceStub).props('point')).toEqual({ latitude: 50, longitude: 30 })
    type.vm.$emit('update:modelValue', 'TRIP'); await flushPromises()
    for (const picker of wrapper.findAllComponents(PlaceStub)) picker.vm.$emit('update:modelValue', place)
    await flushPromises()
    await click(wrapper, 'Add route points')
    wrapper.findComponent({ name: 'MapContainer' }).vm.$emit('map-click', { latlng: { lat: 49, lng: 24 } }); await flushPromises()
    for (const label of ['A', 'B', '1']) {
      overlay().vm.$emit('context-point-dragged', { label, latitude: 51, longitude: 31 }); await flushPromises()
    }
    expect(wrapper.findAllComponents(PlaceStub).map(picker => picker.props('point'))).toEqual([
      { latitude: 51, longitude: 31 }, { latitude: 51, longitude: 31 }
    ])
    expect(wrapper.findAllComponents(PlaceStub).map(picker => picker.props('modelValue'))).toEqual([null, null])
    expect(overlay().props('contextPoints')).toContainEqual({ label: '1', latitude: 51, longitude: 31 })
    await click(wrapper, 'Use GPS timestamps')
    overlay().vm.$emit('context-point-dragged', { label: 'A', latitude: 0, longitude: 0 }); await flushPromises()
    expect(wrapper.findAllComponents(PlaceStub)[0].props('point')).toEqual({ latitude: 51, longitude: 31 })
  })

  it('opens only one trip chooser and preserves review while browsing previews', async () => {
    const wrapper = await open({ suggestedRange: times })
    await stay(wrapper)
    vi.spyOn(useTimelineStore(), 'previewActivityCorrection').mockResolvedValue(previewResult)
    await click(wrapper, 'Review changes')
    wrapper.findComponent(PlaceStub).vm.$emit('preview', { latitude: 51, longitude: 31 }); await flushPromises()
    expect(wrapper.find('.activity-review').exists()).toBe(true)
    const markers = wrapper.findComponent({ name: 'TripReconstructionMapOverlay' }).props('contextPoints')
    expect(markers).toContainEqual({ latitude: place.latitude, longitude: place.longitude, label: 'S' })
    wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?').vm.$emit('update:modelValue', 'TRIP')
    await flushPromises()
    const pickers = wrapper.findAllComponents(PlaceStub)
    expect(pickers.map(picker => picker.props('expanded'))).toEqual([true, false])
    pickers[1].vm.$emit('update:expanded', true); await flushPromises()
    expect(pickers.map(picker => picker.props('expanded'))).toEqual([false, true])
  })

  it('keeps unconfirmed boundary markers passive when they overlap a selected endpoint', async () => {
    const wrapper = await open({ suggestedRange: times, recordedPoints: [
      { ...place, timestamp: times.startTime }, { ...place, timestamp: times.endTime }
    ] })
    wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?').vm.$emit('update:modelValue', 'TRIP')
    await flushPromises()
    wrapper.findAllComponents(PlaceStub)[0].vm.$emit('update:modelValue', place); await flushPromises()
    const markers = wrapper.findComponent({ name: 'TripReconstructionMapOverlay' }).props('contextPoints')
    expect(markers.find(marker => marker.label === 'A').draggable).not.toBe(false)
    expect(markers.find(marker => marker.label === 'B').draggable).toBe(false)
  })

  it('brings the map into view on mobile and returns to the location after picking', async () => {
    const original = window.matchMedia
    window.matchMedia = vi.fn(() => ({ matches: true }))
    try {
      const wrapper = await open({ suggestedRange: times }); await stay(wrapper)
      const mapScroll = vi.fn(), placeScroll = vi.fn()
      wrapper.find('.activity-editor-map').element.scrollIntoView = mapScroll
      const origin = document.createElement('div'); origin.scrollIntoView = placeScroll
      wrapper.findComponent(PlaceStub).vm.$emit('request-map', origin); await flushPromises()
      expect(mapScroll).toHaveBeenCalledWith({ block: 'start', behavior: 'auto' })
      wrapper.findComponent({ name: 'MapContainer' }).vm.$emit('map-click', { latlng: { lat: 50, lng: 30 } }); await flushPromises()
      expect(placeScroll).toHaveBeenCalledWith({ block: 'center', behavior: 'auto' })
    } finally { window.matchMedia = original }
  })

  it('inspects vector GPS groups without placing a stay or route point or changing times', async () => {
    const wrapper = await open({ suggestedRange: times })
    await stay(wrapper)
    const type = wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?')
    const queryRenderedFeatures = vi.fn(() => [{ properties: { groupRaw: '{}' } }])
    const map = wrapper.findComponent({ name: 'MapContainer' })
    map.vm.$emit('map-ready', { queryRenderedFeatures, setView: vi.fn(), fitBounds: vi.fn() })
    await flushPromises()
    const originalTimes = wrapper.findAllComponents(DateStub).map(field => field.props('modelValue'))
    const clickGps = async () => {
      map.vm.$emit('map-click', { point: { x: 10, y: 20 }, latlng: { lat: 1, lng: 2 } })
      await flushPromises()
    }
    await clickGps()
    expect(wrapper.findComponent(PlaceStub).props('point')).toBeNull()
    type.vm.$emit('update:modelValue', 'TRIP'); await flushPromises()
    for (const picker of wrapper.findAllComponents(PlaceStub)) picker.vm.$emit('update:modelValue', place)
    await flushPromises()
    await click(wrapper, 'Add route points')
    await clickGps()
    expect(wrapper.find('.route-points').exists()).toBe(false)
    expect(wrapper.findAllComponents(DateStub).map(field => field.props('modelValue'))).toEqual(originalTimes)
    queryRenderedFeatures.mockReturnValue([{ properties: { raw_count: 9 } }])
    await clickGps()
    expect(wrapper.find('.route-points').exists()).toBe(false)
    queryRenderedFeatures.mockReturnValue([])
    await clickGps()
    expect(wrapper.findAll('.route-points li')).toHaveLength(1)
    await wrapper.setProps({ visible: false })
    expect(wrapper.findComponent({ name: 'RawGpsPointsLayer' }).exists()).toBe(false)
  })

  it('keeps seconds when applying GPS sample boundaries from the map', async () => {
    const points = [{ ...place, timestamp: '2026-09-15T14:24:17Z' }, { ...place, timestamp: '2026-09-15T14:25:47Z' }]
    const wrapper = await open({ recordedPoints: points })
    await click(wrapper, 'Use GPS timestamps')
    const layer = wrapper.findComponent({ name: 'RawGpsPointsLayer' })
    layer.props('selectPoint')(points[1]); layer.props('selectPoint')(points[0])
    await flushPromises()
    await click(wrapper, 'Use these times')
    const values = wrapper.findAllComponents(DateStub).map(field => timelinePickerToUtc(field.props('modelValue'), useTimezone()))
    expect(values.map(value => new Date(value).toISOString())).toEqual(points.map(point => point.timestamp.replace('Z', '.000Z')))
    expect(wrapper.findComponent({ name: 'RawGpsPointsLayer' }).props('selectPoint')).toBeNull()
  })

  it('mounts GPS inspection beside the editor map and outside its clipped viewport', async () => {
    const wrapper = await open({})
    const panel = wrapper.find('.gps-inspector')
    expect(wrapper.findComponent({ name: 'RawGpsPointsLayer' }).props('inspectorContainer')).toBe(panel.element)
    expect(panel.element.closest('.map-stub')).toBeNull()
    expect(panel.attributes('role')).toBe('region')
    wrapper.unmount()
  })

  it('keeps GPS samples unobstructed until an activity is chosen and during timestamp selection', async () => {
    const wrapper = await open({ suggestedRange: times, recordedPoints: [
      { ...place, timestamp: times.startTime }, { ...place, timestamp: times.endTime }
    ] })
    const markers = () => wrapper.findComponent({ name: 'TripReconstructionMapOverlay' }).props('contextPoints')
    expect(markers()).toEqual([])
    expect(wrapper.find('.map-marker-legend').exists()).toBe(false)
    wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?').vm.$emit('update:modelValue', 'TRIP')
    await flushPromises()
    expect(markers().map(marker => marker.label)).toEqual(['A', 'B'])
    await click(wrapper, 'Use GPS timestamps')
    expect(markers()).toEqual([])
    expect(wrapper.find('.map-marker-legend').exists()).toBe(false)
    await click(wrapper, 'Cancel')
    expect(markers().map(marker => marker.label)).toEqual(['A', 'B'])
    wrapper.unmount()
  })

  it('discards unsaved times, places, GPS boundaries, and review when closed and reopened', async () => {
    const recordedPoints = [{ ...place, timestamp: times.startTime }, { ...place, timestamp: times.endTime }]
    const wrapper = await open({ suggestedRange: times, recordedPoints })
    await stay(wrapper)
    const startField = wrapper.findAllComponents(DateStub)[0]
    startField.vm.$emit('update:modelValue', new Date(startField.props('modelValue').getTime() + 77000))
    await flushPromises()
    vi.spyOn(useTimelineStore(), 'previewActivityCorrection').mockResolvedValue(previewResult)
    await click(wrapper, 'Review changes')
    expect(wrapper.find('.activity-review').exists()).toBe(true)
    await click(wrapper, 'Cancel')
    expect(wrapper.emitted('close')).toHaveLength(1)
    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })
    const activityChoice = wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?')
    expect(activityChoice.props('modelValue')).toBeNull()
    expect(wrapper.findComponent(PlaceStub).exists()).toBe(false)
    expect(wrapper.find('.activity-review').exists()).toBe(false)
    expect(wrapper.find('.gps-inspector').element.children).toHaveLength(0)
    const values = wrapper.findAllComponents(DateStub).map(field => timelinePickerToUtc(field.props('modelValue'), useTimezone()))
    expect(values.map(value => new Date(value).toISOString())).toEqual([times.startTime, times.endTime].map(value => value.replace('Z', '.000Z')))
    await click(wrapper, 'Use GPS timestamps')
    const layer = wrapper.findComponent({ name: 'RawGpsPointsLayer' })
    layer.props('selectPoint')(recordedPoints[0])
    await flushPromises()
    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })
    await click(wrapper, 'Use GPS timestamps')
    expect(wrapper.findAllComponents(SelectStub).filter(select => ['First GPS point', 'Second GPS point'].includes(select.props('ariaLabel'))).map(select => select.props('modelValue'))).toEqual([null, null])
    wrapper.unmount()
  })

})
