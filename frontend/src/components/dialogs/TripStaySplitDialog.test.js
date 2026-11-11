import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))

import TripStaySplitDialog from './TripStaySplitDialog.vue'
import { useTimelineStore } from '@/stores/timeline'
import { useTimelinePreferencesStore } from '@/stores/timelinePreferences'
import { useTimezone } from '@/composables/useTimezone'

const START = Date.parse('2026-10-08T10:00:00Z')
// A straight route with a point every kilometre and five minutes, so a click picks exactly one point.
const points = Array.from({ length: 7 }, (_, index) => ({
  latitude: 50 + index * 0.009, longitude: 30, timestamp: new Date(START + index * 300_000).toISOString()
}))
const trip = { id: 4, timestamp: points[0].timestamp, tripDuration: 1800, movementType: 'CAR' }
const placePickerStub = {
  name: 'TimelinePlacePicker',
  props: ['modelValue', 'point'],
  emits: ['update:modelValue'],
  template: '<button type="button" class="pick" @click="$emit(\'update:modelValue\', { favoriteId: 3, locationName: \'Gas station\' })">pick</button>'
}
const mapStub = { name: 'MapContainer', emits: ['map-click'], template: '<div class="map"><slot name="overlays" :map="null" /></div>' }

describe('TripStaySplitDialog', () => {
  let store

  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    store = useTimelineStore()
    const preferences = useTimelinePreferencesStore()
    vi.spyOn(preferences, 'fetchTimelinePreferences').mockResolvedValue({})
    vi.spyOn(store, 'fetchTripPath').mockResolvedValue({ points, segments: [] })
  })

  // Opened after mounting, as the timeline does.
  const mountDialog = () => mount(TripStaySplitDialog, {
    props: { visible: false, trip: null },
    global: {
      stubs: {
        Dialog: { template: '<section><slot /><slot name="footer" /></section>' },
        Button: { props: ['label', 'disabled'], emits: ['click'], template: '<button type="button" class="action" :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>' },
        DatePicker: true, Message: { template: '<p><slot /></p>' }, Tag: true,
        MapContainer: mapStub, PathLayer: true, TripReconstructionMapOverlay: true,
        TimelinePlacePicker: placePickerStub
      }
    }
  })

  it.each([false, true])('previews and saves a short trip with a stop near an endpoint (origin: %s)', async (nearOrigin) => {
    const path = [
      ['19:49:38', nearOrigin ? 50 : 50.0056],
      ['19:50:01', 50.0002], ['19:53:49', 50.0003],
      ['19:55:42', nearOrigin ? 50.0056 : 50]
    ].map(([time, latitude]) => ({ timestamp: `2026-11-05T${time}Z`, latitude, longitude: 30 }))
    store.fetchTripPath.mockResolvedValue({ points: path })
    vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [
      { type: 'TRIP', start: path[0].timestamp, end: path[1].timestamp },
      { type: 'STAY', start: path[1].timestamp, end: path[2].timestamp },
      { type: 'TRIP', start: path[2].timestamp, end: path[3].timestamp }
    ] })
    vi.spyOn(store, 'createTimelineEdit').mockResolvedValue({ edit: { id: 12 } })
    const wrapper = mountDialog()
    await wrapper.setProps({ visible: true, trip: { id: 4, timestamp: path[0].timestamp, tripDuration: 364 } })
    await flushPromises()
    wrapper.findComponent({ name: 'MapContainer' }).vm.$emit('map-click', path[1])
    await flushPromises()

    const timezone = useTimezone()
    const pickers = wrapper.findAllComponents({ name: 'DatePicker' })
    for (const [index, point] of [path[1], path[2]].entries()) {
      const local = timezone.fromUtc(point.timestamp)
      pickers[index].vm.$emit('update:modelValue', new Date(local.year(), local.month(), local.date(), local.hour(), local.minute(), local.second()))
    }
    await wrapper.find('.pick').trigger('click')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    const request = store.previewTimelineEdit.mock.calls.at(-1)[0]
    expect(request.anchorStart).toBe(new Date(path[1].timestamp).toISOString())
    expect(request.anchorEnd).toBe(new Date(path[2].timestamp).toISOString())
    const save = wrapper.findAll('.action').find(button => button.text() === 'Save Split')
    expect(save.attributes('disabled')).toBeUndefined()
    await save.trigger('click')
    await flushPromises()
    expect(store.createTimelineEdit).toHaveBeenCalledWith(request)
    expect(wrapper.emitted('split')[0][0].edit.id).toBe(12)
    wrapper.unmount()
  })

  it('saves the stop as one unified timeline edit with the chosen place by reference', async () => {
    vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [
      { type: 'TRIP', start: points[0].timestamp, end: points[3].timestamp, label: 'CAR' },
      { type: 'STAY', start: points[3].timestamp, end: points[3].timestamp, label: 'Gas station' },
      { type: 'TRIP', start: points[3].timestamp, end: points[6].timestamp, label: 'CAR' }
    ] })
    vi.spyOn(store, 'createTimelineEdit').mockResolvedValue({ edit: { id: 12 }, timelineJobId: 'job' })
    const legacySplit = vi.spyOn(store, 'splitTripWithStay')
    const wrapper = mountDialog()
    await wrapper.setProps({ visible: true, trip })
    await flushPromises()

    wrapper.findComponent({ name: 'MapContainer' }).vm.$emit('map-click', { lat: points[3].latitude, lng: 30 })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'TimelinePlacePicker' }).props('point')).toMatchObject({ latitude: points[3].latitude })
    expect(wrapper.text()).not.toContain(String(points[3].latitude))

    await wrapper.find('.pick').trigger('click')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(store.previewTimelineEdit).toHaveBeenCalled()
    const request = store.previewTimelineEdit.mock.calls.at(-1)[0]
    expect(request).toMatchObject({
      kind: 'FORCE_STAY', affectedFrom: trip.timestamp, payload: { favoriteId: 3 }
    })
    expect(Date.parse(request.anchorStart)).toBeGreaterThan(START)
    expect(Date.parse(request.anchorEnd)).toBeGreaterThan(Date.parse(request.anchorStart))

    await wrapper.findAll('.action').find(button => button.text() === 'Save Split').trigger('click')
    await flushPromises()
    expect(store.createTimelineEdit).toHaveBeenCalledWith(request)
    expect(legacySplit).not.toHaveBeenCalled()
    expect(wrapper.emitted('split')[0][0].edit.id).toBe(12)
  })
})
