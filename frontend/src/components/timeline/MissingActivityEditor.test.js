import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import Editor from './MissingActivityEditor.vue'
import { useTimelineStore } from '@/stores/timeline'
import { useTimezone } from '@/composables/useTimezone'
import { timelinePickerToUtc } from '@/utils/timelineEditTime'

vi.mock('@/components/maps/MapContainer.vue', () => ({ default: { name: 'MapContainer', emits: ['map-click', 'map-ready'], data: () => ({ map: {} }), template: '<div class="map-stub"><slot name="overlays" :map="map" :isReady="true" /></div>' } }))
const place = { latitude: 49.82, longitude: 24.01, locationName: 'Cafe', source: 'custom' }
const DateStub = { name: 'ActivityDateTimeField', props: ['modelValue', 'contextDate'], emits: ['update:modelValue'], template: '<input />' }
const SelectStub = { name: 'Select', props: ['modelValue', 'options', 'ariaLabel'], emits: ['update:modelValue'], template: '<select :aria-label="ariaLabel" />' }
const button = { props: ['label', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>' }
const PlaceStub = { name: 'TimelinePlacePicker', props: ['modelValue', 'point', 'lookupAt', 'expanded'], emits: ['update:modelValue', 'preview', 'update:expanded', 'request-map'], template: '<button class="pick-place" @click="$emit(\'update:modelValue\', {latitude:49.82,longitude:24.01,locationName:\'Cafe\',source:\'custom\'})">Pick place</button>' }
const PickerLayerStub = { name: 'GpsTimePickerLayer', props: ['points', 'range', 'inspectedIndex', 'hoveredIndex'], emits: ['inspect', 'hover'], template: '<div class="picker-layer" />' }
const PickerPanelStub = { name: 'GpsTimePickerPanel', props: ['points', 'range', 'startTime', 'endTime', 'visible', 'timesEditable', 'placeActions', 'otherRanges', 'inspectedIndex', 'hoveredIndex'], emits: ['update:range', 'inspect', 'hover', 'expand', 'place'], template: '<div class="picker-panel" />' }
const panel = wrapper => wrapper.findComponent({ name: 'GpsTimePickerPanel' })
const times = { startTime: '2026-09-15T14:24:00Z', endTime: '2026-09-15T14:27:00Z' }
const previewResult = { events: [{ type: 'STAY', start: times.startTime, end: times.endTime, label: 'Cafe', locationName: 'Cafe' }] }
const open = async props => {
  const wrapper = mount(Editor, { props: { visible: false, ...props }, global: { stubs: {
    Dialog: { template: '<div><slot /><slot name="footer" /></div>' }, Button: button,
    InputText: { props: ['modelValue'], template: '<input />' }, ActivityDateTimeField: DateStub, Select: SelectStub, TimelinePlacePicker: PlaceStub, RawGpsPointsLayer: true, TripReconstructionMapOverlay: true,
    GpsTimePickerLayer: PickerLayerStub, GpsTimePickerPanel: PickerPanelStub
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
  it('reviews a rebuild of an existing interval as a replace request', async () => {
    const store = useTimelineStore()
    const preview = vi.spyOn(store, 'previewCorrections').mockResolvedValue(previewResult)
    const wrapper = await open({ rebuildRange: { startTime: times.startTime, endTime: times.endTime, type: 'trip' } })
    expect(wrapper.text()).toContain('Describe what happened inside this time range')
    expect(wrapper.text()).toContain('What happened?')
    expect(wrapper.findAll('button').some(button => button.text() === 'Add another activity')).toBe(true)
    const endpoints = wrapper.findAll('.pick-place')
    expect(endpoints).toHaveLength(2)
    for (const endpoint of endpoints) await endpoint.trigger('click')
    await click(wrapper, 'Review changes')
    expect(preview).toHaveBeenCalledWith(expect.objectContaining({
      contextStart: times.startTime,
      contextEnd: times.endTime,
      activities: [expect.objectContaining({ segmentType: 'TRIP', startTime: times.startTime, endTime: times.endTime })]
    }))
  })
  it('reviews a rebuild of a stay as a stay, not a trip', async () => {
    const store = useTimelineStore()
    const preview = vi.spyOn(store, 'previewCorrections').mockResolvedValue(previewResult)
    const wrapper = await open({ rebuildRange: { startTime: times.startTime, endTime: times.endTime, type: 'stay',
      latitude: 49.82, longitude: 24.01, label: 'Home' } })
    await click(wrapper, 'Review changes')
    expect(preview).toHaveBeenCalledWith(expect.objectContaining({ activities: [expect.objectContaining({
      segmentType: 'STAY', latitude: 49.82, longitude: 24.01, locationName: 'Home' })] }))
  })
  it('reviews and saves a whole-trip route edit', async () => {
    const store = useTimelineStore()
    vi.spyOn(store, 'fetchTripPath').mockResolvedValue({ points: [] })
    const preview = vi.spyOn(store, 'previewCorrections').mockResolvedValue({ events: [], token: 'token', warnings: [] })
    const save = vi.spyOn(store, 'saveCorrections').mockResolvedValue({ groupId: 'group', edits: [{ id: 1 }], timelineJobId: 'job' })
    const routeTarget = { startTime: times.startTime, endTime: '2026-09-15T15:00:00Z', movementType: 'CAR',
      startLatitude: 49.82, startLongitude: 24.01, endLatitude: 49.83, endLongitude: 24.03 }
    const wrapper = await open({ routeTarget })
    expect(wrapper.text()).toContain('Drag the endpoints or add route points')
    await click(wrapper, 'Review changes')
    expect(preview).toHaveBeenCalledWith(expect.objectContaining({ op: 'SET_ROUTE', anchorStart: expect.stringContaining('2026-09-15T14:24'),
      payload: expect.objectContaining({ waypoints: [{ latitude: 49.82, longitude: 24.01 }, { latitude: 49.83, longitude: 24.03 }] }) }))
    await click(wrapper, 'Save changes')
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ op: 'SET_ROUTE', previewToken: 'token' }))
  })
  it('reviews and saves a correction without calling reconstruction or renaming a source', async () => {
    const store = useTimelineStore()
    vi.spyOn(store, 'previewCorrections').mockResolvedValue(previewResult)
    vi.spyOn(store, 'saveCorrections').mockResolvedValue({ groupId: 'group', timelineJobId: 'job' })
    const wrapper = await open({ suggestedRange: times }); await stay(wrapper)
    await click(wrapper, 'Review changes')
    expect(wrapper.text()).toContain('Only these activities will be added')
    await click(wrapper, 'Save changes')
    expect(store.saveCorrections).toHaveBeenCalledWith(expect.objectContaining({ activities: [expect.objectContaining({ segmentType: 'STAY', startTime: times.startTime, endTime: times.endTime, locationName: 'Cafe' })] }))
    expect(wrapper.emitted('committed')[0][0].groupId).toBe('group')
  })
  it('keeps the draft after an error and invalidates review when times change', async () => {
    const store = useTimelineStore()
    vi.spyOn(store, 'previewCorrections').mockResolvedValue(previewResult)
    vi.spyOn(store, 'saveCorrections').mockRejectedValue(new Error('Interval occupied'))
    const wrapper = await open({ suggestedRange: times }); await stay(wrapper); await click(wrapper, 'Review changes'); await click(wrapper, 'Save changes')
    expect(wrapper.find('.activity-error').exists()).toBe(true)
    expect(wrapper.findComponent(PlaceStub).props('modelValue')).toEqual(place)
    wrapper.findAllComponents(DateStub)[1].vm.$emit('update:modelValue', new Date('2026-09-15T14:28:00Z'))
    await flushPromises()
    expect(wrapper.find('.activity-review').exists()).toBe(false)
    expect(wrapper.findAll('button').some(button => button.text() === 'Save changes')).toBe(false)
  })
  it('shows recorded points from the start and writes a chosen point straight into From and To', async () => {
    const points = [{ ...place, timestamp: times.endTime }, { ...place, timestamp: times.startTime }]
    const wrapper = await open({ recordedPoints: points }); await stay(wrapper)
    // No GPS mode to switch on: the points, list and strip are there as soon as the dialog opens.
    expect(wrapper.findAll('button').some(button => button.text() === 'Use GPS timestamps')).toBe(false)
    expect(panel(wrapper).props('points').map(point => point.timestamp)).toEqual([times.startTime, times.endTime])
    panel(wrapper).vm.$emit('update:range', { from: 0, to: 1 })
    wrapper.findComponent({ name: 'MapContainer' }).vm.$emit('map-click', { latlng: { lat: 1, lng: 2 } })
    await flushPromises()
    expect(wrapper.findComponent(PlaceStub).props('point')).toBeNull()
    const values = wrapper.findAllComponents(DateStub).map(component => useTimezone().createDateTimeFromPicker(component.props('modelValue')).utc().toISOString())
    expect(values).toEqual([times.startTime.replace('Z', '.000Z'), times.endTime.replace('Z', '.000Z')])
    expect(panel(wrapper).props('range')).toEqual({ from: 0, to: 1 })
    expect([panel(wrapper).props('startTime'), panel(wrapper).props('endTime')]).toEqual([Date.parse(times.startTime), Date.parse(times.endTime)])
  })
  it('marks the activity times already chosen on the nearest GPS points when the picker opens', async () => {
    const points = [{ ...place, timestamp: '2026-09-15T14:23:30Z' }, { ...place, timestamp: '2026-09-15T14:25:00Z' }, { ...place, timestamp: '2026-09-15T14:27:40Z' }]
    const wrapper = await open({ suggestedRange: times, recordedPoints: points })
    // 14:24:00 is 30 s from the first point and 14:27:00 is 40 s from the last one.
    expect(panel(wrapper).props('range')).toEqual({ from: 0, to: 2 })
    expect(panel(wrapper).props('startTime')).toBe(Date.parse(times.startTime))
  })

  it('narrows the list and the map to From and To when From moves later, keeping context around it', async () => {
    // A point every 10 minutes from midnight to noon; the gap is 06:00-07:00.
    const points = Array.from({ length: 73 }, (_, index) => ({ ...place, timestamp: new Date(Date.UTC(2026, 8, 15, 0, index * 10)).toISOString() }))
    const wrapper = await open({ suggestedRange: { startTime: '2026-09-15T06:00:00Z', endTime: '2026-09-15T07:00:00Z' }, recordedPoints: points })
    const layerTimes = () => wrapper.findComponent({ name: 'GpsTimePickerLayer' }).props('points').map(point => point.timestamp.slice(11, 16))
    // Five points of context on each side, since ten minutes would be just one.
    expect(panel(wrapper).props('visible')).toEqual({ from: 31, to: 47 })
    expect([layerTimes()[0], layerTimes().at(-1)]).toEqual(['05:10', '07:50'])
    panel(wrapper).vm.$emit('expand', 'before')
    await flushPromises()
    expect(layerTimes()[0]).toBe('00:00')
    // Moving From to 09:00 (past To) narrows again from 08:10 and forgets the expanded side.
    panel(wrapper).vm.$emit('update:range', { from: 54, to: null })
    await flushPromises()
    expect(panel(wrapper).props('visible')).toEqual({ from: 49, to: 72 })
    expect(layerTimes()[0]).toBe('08:10')
    wrapper.unmount()
  })

  it('clears a typed To that a later GPS From would cross', async () => {
    const points = ['14:00:00', '14:10:00', '14:20:00'].map(time => ({ ...place, timestamp: `2026-09-15T${time}Z` }))
    const wrapper = await open({ suggestedRange: { startTime: '2026-09-15T13:58:00Z', endTime: '2026-09-15T14:05:00Z' }, recordedPoints: points })
    // 14:05 is five minutes from any point, so it is a typed time rather than a point.
    expect(panel(wrapper).props('range')).toEqual({ from: 0, to: null })
    panel(wrapper).vm.$emit('update:range', { from: 1, to: null })
    await flushPromises()
    const [from, to] = wrapper.findAllComponents(DateStub).map(field => field.props('modelValue'))
    expect(new Date(timelinePickerToUtc(from, useTimezone())).toISOString()).toBe('2026-09-15T14:10:00.000Z')
    expect(to).toBeNull()
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
    const mapClick = async () => { wrapper.findComponent({ name: 'MapContainer' }).vm.$emit('map-click', { latlng: { lat: 50, lng: 30 } }); await flushPromises() }
    // The map places the stay only after Choose on the map, then stops picking.
    await mapClick()
    expect(wrapper.findComponent(PlaceStub).props('point')).toBeNull()
    wrapper.findComponent(PlaceStub).vm.$emit('request-map'); await flushPromises()
    expect(wrapper.find('.map-target-banner').text()).toContain('Click the map or a GPS point to set where you stayed.')
    await mapClick()
    expect(wrapper.find('.map-target-banner').exists()).toBe(false)
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
  })

  it('opens only one trip chooser and preserves review while browsing previews', async () => {
    const wrapper = await open({ suggestedRange: times })
    await stay(wrapper)
    vi.spyOn(useTimelineStore(), 'previewCorrections').mockResolvedValue(previewResult)
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

  it('places at a GPS point, not under it, when a vector click lands on one while picking on the map', async () => {
    const points = [{ latitude: 49.9, longitude: 24.2, timestamp: times.startTime }, { latitude: 49.91, longitude: 24.21, timestamp: times.endTime }]
    const wrapper = await open({ suggestedRange: times, recordedPoints: points })
    await stay(wrapper)
    const queryRenderedFeatures = vi.fn(() => [{ properties: { gpsPickerPoint: 1, index: 1 } }])
    const map = wrapper.findComponent({ name: 'MapContainer' })
    map.vm.$emit('map-ready', { queryRenderedFeatures, setView: vi.fn(), fitBounds: vi.fn() })
    await flushPromises()
    wrapper.findComponent(PlaceStub).vm.$emit('request-map'); await flushPromises()
    map.vm.$emit('map-click', { point: { x: 10, y: 20 }, latlng: { lat: 1, lng: 2 } })
    wrapper.findComponent({ name: 'GpsTimePickerLayer' }).vm.$emit('inspect', 1)
    await flushPromises()
    expect(wrapper.findComponent(PlaceStub).props('point')).toEqual({ latitude: 49.91, longitude: 24.21 })
    expect(panel(wrapper).props('inspectedIndex')).toBe(1)
    await wrapper.setProps({ visible: false })
    expect(wrapper.findComponent({ name: 'GpsTimePickerLayer' }).exists()).toBe(false)
  })

  it('offers the place this activity needs on a selected point', async () => {
    const points = [{ latitude: 49.9, longitude: 24.2, timestamp: times.startTime }, { latitude: 49.91, longitude: 24.21, timestamp: times.endTime }]
    const wrapper = await open({ suggestedRange: times, recordedPoints: points })
    expect(panel(wrapper).props('placeActions')).toEqual([])
    const type = wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?')
    type.vm.$emit('update:modelValue', 'STAY'); await flushPromises()
    expect(panel(wrapper).props('placeActions').map(action => action.label)).toEqual(['Use as location'])
    panel(wrapper).vm.$emit('place', 'stay', 0); await flushPromises()
    expect(wrapper.findComponent(PlaceStub).props('point')).toEqual({ latitude: 49.9, longitude: 24.2 })
    type.vm.$emit('update:modelValue', 'TRIP'); await flushPromises()
    expect(panel(wrapper).props('placeActions').map(action => action.label)).toEqual(['Use as start', 'Use as destination'])
    panel(wrapper).vm.$emit('place', 'end', 1); await flushPromises()
    expect(wrapper.findAllComponents(PlaceStub)[1].props('point')).toEqual({ latitude: 49.91, longitude: 24.21 })
    wrapper.findAllComponents(PlaceStub)[0].vm.$emit('request-map'); await flushPromises()
    expect(panel(wrapper).props('placeActions').map(action => action.label)).toEqual(['Use as start'])
  })

  it('shows the other activities\u2019 times faintly on the strip', async () => {
    const points = [{ ...place, timestamp: times.startTime }, { ...place, timestamp: times.endTime }]
    const wrapper = await open({ suggestedRange: times, recordedPoints: points })
    await click(wrapper, 'Add another activity')
    expect(panel(wrapper).props('otherRanges')).toEqual([{ start: Date.parse(times.startTime), end: Date.parse(times.endTime) }])
  })
  it('keeps seconds when applying GPS sample boundaries from the map', async () => {
    const points = [{ ...place, timestamp: '2026-09-15T14:24:17Z' }, { ...place, timestamp: '2026-09-15T14:25:47Z' }]
    const wrapper = await open({ recordedPoints: points })
    const layer = wrapper.findComponent({ name: 'GpsTimePickerLayer' })
    layer.vm.$emit('inspect', 1); layer.vm.$emit('hover', 0)
    await flushPromises()
    expect(panel(wrapper).props('inspectedIndex')).toBe(1)
    layer.vm.$emit('inspect', null)
    await flushPromises()
    expect(panel(wrapper).props('inspectedIndex')).toBeNull()
    expect(panel(wrapper).props('hoveredIndex')).toBe(0)
    expect(layer.props('hoveredIndex')).toBe(0)
    panel(wrapper).vm.$emit('update:range', { from: 0, to: 1 })
    await flushPromises()
    expect(layer.props('range')).toEqual({ from: 0, to: 1 })
    const values = wrapper.findAllComponents(DateStub).map(field => timelinePickerToUtc(field.props('modelValue'), useTimezone()))
    expect(values.map(value => new Date(value).toISOString())).toEqual(points.map(point => point.timestamp.replace('Z', '.000Z')))
  })
  it('puts the GPS panel beside the map, and explains when there are no points', async () => {
    const wrapper = await open({ recordedPoints: [{ ...place, timestamp: times.startTime }] })
    expect(panel(wrapper).element.closest('.map-stub')).toBeNull()
    expect(wrapper.find('.gps-map-workspace').classes()).toContain('is-picking')
    expect(wrapper.findComponent({ name: 'RawGpsPointsLayer' }).exists()).toBe(false)
    expect(wrapper.find('.gps-no-points').exists()).toBe(false)
    await wrapper.setProps({ recordedPoints: [] })
    expect(panel(wrapper).exists()).toBe(false)
    expect(wrapper.find('.gps-no-points').text()).toBe('No GPS points are visible here. Enter the times directly instead.')
    wrapper.unmount()
  })
  it('shows places and GPS points together on one map', async () => {
    const wrapper = await open({ suggestedRange: times, recordedPoints: [
      { ...place, timestamp: times.startTime }, { ...place, timestamp: times.endTime }
    ] })
    const markers = () => wrapper.findComponent({ name: 'TripReconstructionMapOverlay' }).props('contextPoints')
    expect(markers()).toEqual([])
    expect(wrapper.find('.map-marker-legend').exists()).toBe(false)
    wrapper.findAllComponents(SelectStub).find(select => select.props('ariaLabel') === 'What happened?').vm.$emit('update:modelValue', 'TRIP')
    await flushPromises()
    expect(markers().map(marker => marker.label)).toEqual(['A', 'B'])
    expect(wrapper.findComponent({ name: 'GpsTimePickerLayer' }).props('points')).toHaveLength(2)
    expect(wrapper.find('.map-marker-legend').exists()).toBe(true)
    wrapper.unmount()
  })
  it('discards unsaved times, places, GPS boundaries, and review when closed and reopened', async () => {
    const recordedPoints = [{ ...place, timestamp: times.startTime }, { ...place, timestamp: times.endTime }]
    const wrapper = await open({ suggestedRange: times, recordedPoints })
    await stay(wrapper)
    const startField = wrapper.findAllComponents(DateStub)[0]
    startField.vm.$emit('update:modelValue', new Date(startField.props('modelValue').getTime() + 77000))
    await flushPromises()
    vi.spyOn(useTimelineStore(), 'previewCorrections').mockResolvedValue(previewResult)
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
    const values = wrapper.findAllComponents(DateStub).map(field => timelinePickerToUtc(field.props('modelValue'), useTimezone()))
    expect(values.map(value => new Date(value).toISOString())).toEqual([times.startTime, times.endTime].map(value => value.replace('Z', '.000Z')))
    expect(panel(wrapper).props('range')).toEqual({ from: 0, to: 1 })
    panel(wrapper).vm.$emit('update:range', { from: 1, to: null })
    await flushPromises()
    expect(panel(wrapper).props('range')).toEqual({ from: 1, to: null })
    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })
    // Reopening restores the gap's times, so the picker marks those points again.
    expect(panel(wrapper).props('range')).toEqual({ from: 0, to: 1 })
    wrapper.unmount()
  })

})

it('can use a recorded GPS point as a route vertex without changing the travel times', async () => {
  const store = useTimelineStore()
  vi.spyOn(store, 'fetchTripPath').mockResolvedValue({ points: [] })
  const preview = vi.spyOn(store, 'previewCorrections').mockResolvedValue({ events: [], token: 'review' })
  const wrapper = await open({ routeTarget: { startTime: times.startTime, endTime: times.endTime,
    movementType: 'WALK', startLatitude: 49.82, startLongitude: 24.01, endLatitude: 49.83, endLongitude: 24.03 },
    recordedPoints: [{ latitude: 49.825, longitude: 24.025, timestamp: '2026-09-15T14:25:00Z' }] })
  // A route's times are fixed, so the panel offers places only.
  expect(panel(wrapper).props('timesEditable')).toBe(false)
  await click(wrapper, 'Add route points')
  expect(panel(wrapper).props('placeActions').map(action => action.key)).toEqual(['via'])
  wrapper.findComponent({ name: 'GpsTimePickerLayer' }).vm.$emit('inspect', 0)
  await flushPromises()
  await click(wrapper, 'Done placing points')
  await click(wrapper, 'Review changes')
  expect(preview).toHaveBeenCalledWith(expect.objectContaining({ op: 'SET_ROUTE',
    anchorStart: new Date(times.startTime).toISOString(), anchorEnd: new Date(times.endTime).toISOString(),
    payload: { waypoints: [{ latitude: 49.82, longitude: 24.01 }, { latitude: 49.825, longitude: 24.025 }, { latitude: 49.83, longitude: 24.03 }] } }))
  wrapper.unmount()
})
