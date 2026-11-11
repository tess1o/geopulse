import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TimelinePlacePicker from './TimelinePlacePicker.vue'
import { useTimelineStore } from '@/stores/timeline'
import { useTripsStore } from '@/stores/trips'
import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'
const buttonStub = { props: ['label'], emits: ['click'], template: '<button type="button" @click="$emit(\'click\')">{{ label }}</button>' }
const inputStub = { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }
const gym = { favoriteId: 3, locationName: 'Gym', latitude: 50, longitude: 24 }
const point = { latitude: 1, longitude: 2 }
let pinia, wrappers
const mountPicker = (props = {}) => {
  const wrapper = mount(TimelinePlacePicker, { attachTo: document.body, props, global: { plugins: [pinia], stubs: {
    Button: buttonStub, InputText: inputStub, MapContainer: { template: '<div class="map"><slot name="overlays" :map="{}" /></div>' },
    TripReconstructionMapOverlay: { name: 'TripReconstructionMapOverlay', props: ['contextPoints', 'activeSegment'], template: '<i />' }
  } } }); wrappers.push(wrapper); return wrapper
}
const click = (wrapper, label) => wrapper.findAll('button').find(button => button.text() === label).trigger('click')
const syncSelection = wrapper => wrapper.setProps({ modelValue: wrapper.emitted('update:modelValue').at(-1)[0] })
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
beforeEach(() => {
  pinia = createPinia(); setActivePinia(pinia); wrappers = []
  vi.spyOn(useTimelineStore(), 'lookupLocation').mockResolvedValue({ favoriteMatches: [], visitMatches: [] })
  vi.spyOn(useTripsStore(), 'getPlanSuggestion').mockResolvedValue({ title: 'Street 5' })
  vi.spyOn(useLocationAnalyticsStore(), 'searchLocations').mockResolvedValue([])
})
afterEach(() => { wrappers.forEach(wrapper => wrapper.unmount()); vi.useRealTimers() })

describe('location chooser', () => {
  it('shows three deduplicated nearby choices without selecting one, then collapses on selection', async () => {
    const wrapper = mountPicker({ suggestions: [gym, gym, ...[4, 5, 6].map(id => ({ ...gym, favoriteId: id, locationName: `Place ${id}` }))] })
    expect(wrapper.findAll('.place-picker-option')).toHaveLength(3)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.find('.place-picker-option').trigger('click'); await syncSelection(wrapper)
    expect(wrapper.find('.place-picker-selected').text()).toContain('Gym')
    expect(wrapper.find('input').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('New place')
    expect(wrapper.text()).not.toContain('Use this new place')
    await click(wrapper, 'Change')
    expect(wrapper.find('input').exists()).toBe(true)
    await wrapper.find('input').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('input').exists()).toBe(false)
    expect(wrapper.props('modelValue').favoriteId).toBe(3)
  })
  it('supports keyboard highlighting and clears the preview without changing the selection', async () => {
    const wrapper = mountPicker({ suggestions: [gym], modelValue: { ...gym, favoriteId: 1, locationName: 'Home' } })
    await click(wrapper, 'Change'); await wrapper.find('input').trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(wrapper.find('.place-picker-option').element)
    expect(wrapper.emitted('preview').at(-1)[0]).toEqual(gym)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.find('.place-picker-option').trigger('mouseleave')
    expect(wrapper.emitted('preview').at(-1)[0]).toBeNull()
    await click(wrapper, 'Cancel')
    expect(wrapper.props('modelValue').locationName).toBe('Home')
  })
  it('replaces nearby choices with search results and selects their coordinates', async () => {
    vi.useFakeTimers()
    const search = useLocationAnalyticsStore().searchLocations.mockResolvedValue([{ category: 'geocoding', id: 8, displayName: 'Station', latitude: 51, longitude: 25 }])
    const wrapper = mountPicker({ suggestions: [gym] })
    await wrapper.find('input').setValue('Station'); await vi.advanceTimersByTimeAsync(250); await flushPromises()
    expect(search).toHaveBeenCalledWith('Station', 'place', { redirectOnServerError: false })
    expect(wrapper.findAll('.place-picker-option').map(option => option.text())).toEqual(['Station'])
    await wrapper.find('.place-picker-option').trigger('click')
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ geocodingId: 8, latitude: 51, longitude: 25 })
  })
  it('shows an empty result with map placement still available', async () => {
    vi.useFakeTimers(); const wrapper = mountPicker({ externalMap: true, allowMap: false })
    await wrapper.find('input').setValue('Unknown'); await vi.advanceTimersByTimeAsync(250); await flushPromises()
    expect(wrapper.text()).toContain('No matching places')
    await click(wrapper, 'Choose on the map')
    expect(wrapper.emitted('request-map')).toHaveLength(1)
  })
  it('keeps the current choice and map action available after a search error', async () => {
    vi.useFakeTimers()
    useLocationAnalyticsStore().searchLocations.mockRejectedValue(new Error('Search unavailable'))
    const wrapper = mountPicker({ modelValue: gym, externalMap: true, allowMap: false })
    await click(wrapper, 'Change'); await wrapper.find('input').setValue('Station')
    await vi.advanceTimersByTimeAsync(250); await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('Search unavailable')
    expect(wrapper.props('modelValue')).toEqual(gym)
    await click(wrapper, 'Choose on the map')
    expect(wrapper.emitted('request-map')).toHaveLength(1)
  })
  it('selects coordinates immediately while address lookup is pending, never snapping to a nearby favorite', async () => {
    const pending = deferred(); useTripsStore().getPlanSuggestion.mockReturnValue(pending.promise)
    useTimelineStore().lookupLocation.mockResolvedValue({ favoriteMatches: [{ id: 3, name: 'Gym', latitude: 50, longitude: 24 }] })
    const wrapper = mountPicker({ point })
    expect(wrapper.emitted('update:modelValue')[0][0]).toEqual({ ...point, locationName: 'Selected location', favoriteId: null, geocodingId: null, source: 'custom' })
    await flushPromises(); pending.resolve({ title: 'Street 5' }); await flushPromises()
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ ...point, favoriteId: null, locationName: 'Street 5' })
  })
  it('offers the geocoded place separately from the exact map pin', async () => {
    useTripsStore().getPlanSuggestion.mockResolvedValue({ title: 'Street 5', geocodingId: 19 })
    const wrapper = mountPicker({ point })
    await flushPromises(); await syncSelection(wrapper)
    expect(wrapper.props('modelValue')).toMatchObject({ ...point, geocodingId: null })
    await click(wrapper, 'Use linked place: Street 5')
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ geocodingId: 19, source: 'known' })
  })
  it('keeps usable coordinates when address lookup fails', async () => {
    useTripsStore().getPlanSuggestion.mockRejectedValue(new Error('offline'))
    const wrapper = mountPicker({ point }); await flushPromises(); await syncSelection(wrapper)
    expect(wrapper.find('.place-picker-selected').text()).toContain('Selected location')
    expect(wrapper.props('modelValue')).toMatchObject(point)
  })
  it('ignores an old address after a newer point or explicit place was chosen', async () => {
    const old = deferred(), latest = deferred()
    useTripsStore().getPlanSuggestion.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)
    const wrapper = mountPicker({ point }); await wrapper.setProps({ point: { latitude: 3, longitude: 4 } })
    latest.resolve({ title: 'Latest street' }); await flushPromises()
    old.resolve({ title: 'Old street' }); await flushPromises()
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ latitude: 3, locationName: 'Latest street' })
    const pending = deferred(); useTripsStore().getPlanSuggestion.mockReturnValueOnce(pending.promise)
    await wrapper.setProps({ point: { latitude: 5, longitude: 6 } }); wrapper.vm.choose(gym)
    pending.resolve({ title: 'Too late' }); await flushPromises()
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ favoriteId: 3, locationName: 'Gym' })
  })
  it('ignores pending address responses after unmount', async () => {
    const pending = deferred(); useTripsStore().getPlanSuggestion.mockReturnValue(pending.promise)
    const update = vi.fn()
    const wrapper = mountPicker({ point, 'onUpdate:modelValue': update }); wrapper.unmount(); pending.resolve({ title: 'Too late' }); await flushPromises()
    expect(update).toHaveBeenCalledTimes(1)
  })
  it('ignores out-of-order search responses', async () => {
    const old = deferred()
    useLocationAnalyticsStore().searchLocations.mockReturnValueOnce(old.promise).mockResolvedValueOnce([{ category: 'favorite', id: 2, name: 'New' }])
    const wrapper = mountPicker(); const first = wrapper.vm.search({ query: 'Old' }); await wrapper.vm.search({ query: 'New' })
    old.resolve([{ category: 'favorite', id: 1, name: 'Old' }]); await first
    expect(wrapper.vm.searchResults.map(result => result.id)).toEqual([2])
  })
  it('keeps current and reference location context in edit and split screens', async () => {
    const wrapper = mountPicker({ current: gym, reference: gym, referenceCaption: 'Original visit' })
    await click(wrapper, 'Choose on the map')
    const overlay = wrapper.findComponent({ name: 'TripReconstructionMapOverlay' })
    expect(overlay.props('contextPoints')).toEqual([expect.objectContaining({ ...gym, label: '•' })])
    expect(overlay.props('activeSegment')).toBeNull()
    expect(wrapper.find('.place-picker-current').text()).toContain('Gym')
    expect(wrapper.find('.place-picker-legend').text()).toContain('Original visit: Gym')
  })
})
