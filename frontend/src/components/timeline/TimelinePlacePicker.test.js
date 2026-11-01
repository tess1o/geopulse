import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TimelinePlacePicker from './TimelinePlacePicker.vue'
import { useTimelineStore } from '@/stores/timeline'
import { useTripsStore } from '@/stores/trips'

const buttonStub = { props: ['label'], emits: ['click'], template: '<button type="button" @click="$emit(\'click\')">{{ label }}</button>' }
const inputStub = {
  props: ['modelValue'], emits: ['update:modelValue'],
  template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}
let pinia

const mountPicker = (props = {}) => mount(TimelinePlacePicker, {
  props,
  global: {
    plugins: [pinia],
    stubs: {
      Button: buttonStub, InputText: inputStub, AutoComplete: { template: '<input />' },
      MapContainer: { template: '<div class="map"><slot name="overlays" :map="{}" /></div>' },
      TripReconstructionMapOverlay: { name: 'TripReconstructionMapOverlay', props: ['contextPoints', 'activeSegment'], template: '<i />' }
    }
  }
})

describe('TimelinePlacePicker', () => {
  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
  })

  it('offers the places known around a point without choosing one', async () => {
    vi.spyOn(useTimelineStore(), 'lookupLocation').mockResolvedValue({
      favoriteMatches: [{ id: 3, name: 'Gym' }],
      visitMatches: [{ geocodingId: 9, name: 'Cafe' }, { favoriteId: 3, name: 'Gym' }]
    })
    const wrapper = mountPicker({
      current: { favoriteId: 1, locationName: 'Home' }, lookupAt: { latitude: 1, longitude: 2 }
    })
    await flushPromises()

    const options = wrapper.findAll('.place-picker-option').map(option => option.text())
    expect(options).toHaveLength(2)
    expect(options[0]).toContain('Gym')
    expect(options[1]).toContain('Cafe')
    expect(wrapper.text()).not.toMatch(/\d+\.\d{3,}/)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await wrapper.findAll('.place-picker-option')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')[0][0]).toMatchObject({ geocodingId: 9, favoriteId: null, locationName: 'Cafe' })
  })

  it('turns a picked point into the favorite there', async () => {
    vi.spyOn(useTimelineStore(), 'lookupLocation').mockResolvedValue({ favoriteMatches: [{ id: 3, name: 'Gym' }], visitMatches: [] })
    vi.spyOn(useTripsStore(), 'getPlanSuggestion').mockResolvedValue({ title: 'Street 5' })
    const wrapper = mountPicker({ point: { latitude: 1, longitude: 2 } })
    await flushPromises()

    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ favoriteId: 3, locationName: 'Gym' })
  })

  it('turns a picked point with no favorite into a new named place', async () => {
    vi.spyOn(useTimelineStore(), 'lookupLocation').mockResolvedValue({ favoriteMatches: [], visitMatches: [] })
    vi.spyOn(useTripsStore(), 'getPlanSuggestion').mockResolvedValue({ title: 'Street 5' })
    const wrapper = mountPicker({ point: { latitude: 1, longitude: 2 } })
    await flushPromises()

    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toEqual({
      favoriteId: null, geocodingId: null, latitude: 1, longitude: 2, locationName: 'Street 5', source: 'custom'
    })
  })

  it('shows the new place as chosen when it replaces the favorite found at the point', async () => {
    vi.spyOn(useTimelineStore(), 'lookupLocation').mockResolvedValue({ favoriteMatches: [{ id: 3, name: 'Gym' }], visitMatches: [] })
    vi.spyOn(useTripsStore(), 'getPlanSuggestion').mockResolvedValue({ title: 'Street 5' })
    const wrapper = mountPicker({ point: { latitude: 1, longitude: 2 } })
    await flushPromises()
    await wrapper.setProps({ modelValue: wrapper.emitted('update:modelValue').at(-1)[0] })
    expect(wrapper.find('.place-picker-new').classes()).not.toContain('is-selected')

    await wrapper.findAll('button').find(button => button.text() === 'Use this new place').trigger('click')
    const chosen = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(chosen).toMatchObject({ source: 'custom', locationName: 'Street 5', favoriteId: null })
    await wrapper.setProps({ modelValue: chosen })
    expect(wrapper.find('.place-picker-new').classes()).toContain('is-selected')
    expect(wrapper.text()).toContain('This new place is selected')

    await wrapper.find('.place-picker-new input').setValue('Street 5, yard')
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ source: 'custom', locationName: 'Street 5, yard' })
  })

  it('marks the visit being split on the map before anything is picked', async () => {
    vi.spyOn(useTimelineStore(), 'lookupLocation').mockResolvedValue({ favoriteMatches: [], visitMatches: [] })
    const wrapper = mountPicker({
      reference: { latitude: 1, longitude: 2, locationName: 'Home' }, referenceCaption: 'Where the visit is now'
    })
    await flushPromises()

    await wrapper.findAll('button').find(button => button.text() === 'Choose on the map').trigger('click')

    const overlay = wrapper.findComponent({ name: 'TripReconstructionMapOverlay' })
    expect(overlay.props('contextPoints')).toEqual([expect.objectContaining({ latitude: 1, longitude: 2, label: '•' })])
    expect(overlay.props('activeSegment')).toBeNull()
    expect(wrapper.find('.place-picker-legend').text()).toContain('Where the visit is now: Home')
  })
})

describe('place response ordering', () => {
  beforeEach(() => { pinia = createPinia(); setActivePinia(pinia) })
  it('keeps the newer point when an earlier lookup finishes last', async () => {
    let resolveOld
    const lookup = vi.spyOn(useTimelineStore(), 'lookupLocation')
      .mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValue({ favoriteMatches: [{ id: 2, name: 'New place' }], visitMatches: [] })
    vi.spyOn(useTripsStore(), 'getPlanSuggestion').mockResolvedValue({ title: 'New street' })
    const wrapper = mountPicker({ point: { latitude: 1, longitude: 2 } })
    await wrapper.setProps({ point: { latitude: 3, longitude: 4 } })
    await flushPromises()
    expect(lookup).toHaveBeenCalledTimes(2)
    resolveOld({ favoriteMatches: [{ id: 1, name: 'Old place' }], visitMatches: [] })
    await flushPromises()
    expect(wrapper.text()).not.toContain('Old place')
    expect(wrapper.emitted('update:modelValue').at(-1)[0].favoriteId).toBe(2)
  })
  it('does not replace an explicit choice with a pending map lookup', async () => {
    let resolveLookup
    vi.spyOn(useTimelineStore(), 'lookupLocation').mockImplementation(() => new Promise(resolve => { resolveLookup = resolve }))
    vi.spyOn(useTripsStore(), 'getPlanSuggestion').mockResolvedValue({ title: 'Map street' })
    const wrapper = mountPicker({ point: { latitude: 1, longitude: 2 } })
    await flushPromises()
    wrapper.vm.choose({ favoriteId: 9, locationName: 'User choice', source: 'favorite' })
    resolveLookup({ favoriteMatches: [{ id: 2, name: 'Map favorite' }], visitMatches: [] })
    await flushPromises()
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ favoriteId: 9, locationName: 'User choice' })
  })
  it('keeps newer search results when an older request completes last', async () => {
    let resolveOld
    const search = vi.spyOn(useLocationAnalyticsStore(), 'searchLocations')
      .mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValue([{ category: 'favorite', id: 2, displayName: 'New place' }])
    const wrapper = mountPicker()
    const old = wrapper.vm.search({ query: 'Old' })
    await wrapper.vm.search({ query: 'New' })
    resolveOld([{ category: 'favorite', id: 1, displayName: 'Old place' }])
    await old
    expect(search).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.searchResults.map(result => result.id)).toEqual([2])
  })
})
