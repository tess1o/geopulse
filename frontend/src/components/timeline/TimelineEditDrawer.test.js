import { useTimezone } from '@/composables/useTimezone'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TimelineEditDrawer from './TimelineEditDrawer.vue'
import { useTimelineStore } from '@/stores/timeline'

const drawerStub = { name: 'Drawer', props: ['visible', 'header'], emits: ['update:visible', 'show', 'after-hide'], template: '<div><h2>{{ header }}</h2><slot /><slot name="footer" /></div>' }
const buttonStub = { props: ['label', 'type'], emits: ['click'], template: '<button :type="type || \'button\'" @click="$emit(\'click\')">{{ label }}</button>' }
const gym = { favoriteId: 3, geocodingId: null, latitude: null, longitude: null, locationName: 'Gym', source: 'favorite' }
const placePickerStub = {
  name: 'TimelinePlacePicker',
  props: ['modelValue', 'current', 'suggestions', 'lookupAt'],
  emits: ['update:modelValue'],
  template: '<button type="button" @click="$emit(\'update:modelValue\', $attrs.pick || gym)">Pick place</button>',
  setup: () => ({ gym })
}
const datePickerStub = { name: 'DatePicker', props: ['modelValue', 'minDate', 'maxDate'], emits: ['update:modelValue'], template: '<span class="date-picker" />' }
const mountDrawer = (item, props = {}) => mount(TimelineEditDrawer, {
  props: { visible: true, item, ...props },
  global: {
    plugins: [createPinia()],
    stubs: {
      Drawer: drawerStub, Button: buttonStub, InputText: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }, Select: { template: '<select />' },
      TimelinePlacePicker: placePickerStub, DatePicker: datePickerStub
    }
  }
})
const buttonLabel = button => (button.find('.edit-action-text > span').exists()
  ? button.find('.edit-action-text > span') : button).text()
const clickButton = (wrapper, label) => wrapper.findAll('button').find(button => buttonLabel(button) === label).trigger('click')

describe('TimelineEditDrawer action overview', () => {
  it('lets a zero-duration transition be classified, retimed or dismissed, without splitting or merging', async () => {
    const item = { type: 'trip', timestamp: '2026-10-08T10:00:00Z', tripDuration: 0,
      movementType: 'UNKNOWN', latitude: 50, longitude: 30, endLatitude: 50.001, endLongitude: 30.001 }
    const previousItem = { type: 'stay', timestamp: '2026-10-08T09:00:00Z', stayDuration: 3600, locationName: 'Home' }
    const nextItem = { type: 'stay', timestamp: item.timestamp, stayDuration: 3600, locationName: 'Shop' }
    const wrapper = mountDrawer(item, { previousItem, nextItem })
    const store = useTimelineStore()
    vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [] })
    vi.spyOn(store, 'createTimelineEdit').mockResolvedValue({ edit: { id: 1 } })
    expect(wrapper.text()).toContain('Unrecorded transition')
    expect(wrapper.vm.detailActions.map(entry => entry.id)).toEqual(['SET_MOVEMENT_TYPE', 'SET_TIMES'])
    expect(wrapper.vm.structuralActions.map(entry => entry.id)).toEqual(['REMOVE_EVENT'])

    await clickButton(wrapper, 'Movement type')
    wrapper.vm.form.movementType = 'WALK'
    await wrapper.vm.$nextTick()
    await wrapper.find('form').trigger('submit')
    expect(store.createTimelineEdit).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'SET_MOVEMENT_TYPE', anchorStart: item.timestamp, anchorEnd: '2026-10-08T10:00:00.000Z',
      payload: expect.objectContaining({ movementType: 'WALK', sourceType: 'TRIP', sourceStartLatitude: 50, sourceStartLongitude: 30 })
    }))
    await flushPromises()
    await clickButton(wrapper, 'Back')
    await clickButton(wrapper, 'Remove')
    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.text()).toContain('without changing either stay')
    await clickButton(wrapper, 'Review changes')
    await flushPromises()
    await wrapper.find('form').trigger('submit')
    expect(store.createTimelineEdit).toHaveBeenLastCalledWith(expect.objectContaining({
      kind: 'REMOVE_EVENT', payload: expect.objectContaining({ resolution: 'DISMISS' }),
      affectedFrom: previousItem.timestamp, affectedTo: '2026-10-08T11:00:00.000Z'
    }))
    wrapper.unmount()
  })
  it('names the movement selector for keyboard and screen reader users', async () => {
    const wrapper = mountDrawer({ type: 'trip', timestamp: '2026-10-08T10:00:00Z', tripDuration: 3600, movementType: 'CAR' })
    await clickButton(wrapper, 'Movement type')
    expect(wrapper.find('select').attributes('aria-label')).toBe('Movement type')
  })

  beforeEach(() => { vi.spyOn(window, 'confirm').mockReturnValue(true) })
  it('shows stay details and structural actions without permanent tabs', () => {
    const item = { type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home' }
    const wrapper = mountDrawer(item, { mergeNextItem: { ...item, timestamp: '2026-10-08T12:00:00Z' } })
    expect(wrapper.text()).toContain('Location')
    expect(wrapper.text()).toContain('Where this visit happened')
    expect(wrapper.text()).toContain('Visit label')
    expect(wrapper.text()).toContain('A title for this visit only')
    expect(wrapper.text()).toContain('Rename location everywhere')
    expect(wrapper.text()).toContain('Split stay')
    expect(wrapper.text()).toContain('Merge')
    expect(wrapper.text()).toContain('Remove')
  })

  it('shows trip-specific actions', () => {
    const item = { type: 'trip', timestamp: '2026-10-08T10:00:00Z', tripDuration: 3600, movementType: 'CAR' }
    const wrapper = mountDrawer(item, { mergePreviousItem: { ...item, timestamp: '2026-10-08T08:00:00Z' } })
    expect(wrapper.text()).toContain('Movement type')
    expect(wrapper.text()).toContain('Add a stop')
    expect(wrapper.text()).not.toContain('Visit label')
  })

  it.each(['stay', 'trip'])('limits grouped %s activities to supported detail edits', async type => {
    const item = { id: 5, type, timestamp: '2026-10-08T10:00:00Z', endTime: '2026-10-08T11:00:00Z',
      stayDuration: 3600, tripDuration: 3600, movementType: 'CAR', locationName: 'Home' }
    const wrapper = mountDrawer(item)
    wrapper.vm.savedEdits = [{ id: 9, groupId: 'group', kind: type === 'stay' ? 'FORCE_STAY' : 'FORCE_TRIP', status: 'ACTIVE' }]
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.detailActions.map(entry => entry.id)).toEqual(type === 'stay'
      ? ['SET_PLACE', 'SET_VISIT_NAME'] : ['SET_MOVEMENT_TYPE'])
    expect(wrapper.vm.structuralActions.map(entry => entry.id)).toEqual(type === 'stay' ? ['RENAME_PLACE'] : [])
    expect(wrapper.text()).toContain('undo the correction group')
    await wrapper.setProps({ item: { ...item, id: 6 } })
    expect(wrapper.vm.detailActions.map(entry => entry.id)).toContain('SET_TIMES')
    wrapper.unmount()
  })

  it('hands trip splitting to the route-map dialog instead of asking for coordinates', async () => {
    const item = { type: 'trip', timestamp: '2026-10-08T10:00:00Z', tripDuration: 3600, movementType: 'CAR' }
    const wrapper = mountDrawer(item)

    await clickButton(wrapper, 'Add a stop')

    expect(wrapper.emitted('split-trip-with-stay')?.[0]?.[0]).toEqual(item)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Latitude')
  })

  it('keeps only history for gaps, whose correction uses the shared editor', () => {
    const wrapper = mountDrawer({ type: 'dataGap', startTime: '2026-10-08T10:00:00Z', endTime: '2026-10-08T11:00:00Z', durationSeconds: 3600 })
    expect(wrapper.text()).not.toContain('Convert to stay')
    expect(wrapper.text()).not.toContain('Convert to trip')
    expect(wrapper.text()).not.toContain('Remove')
  })



  it('does not offer merge when no compatible neighbour exists', () => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home' })
    expect(wrapper.text()).not.toContain('Merge')
    expect(wrapper.text()).toContain('Remove')
  })

  it.each([
    [{ type: 'stay', favoriteId: 1 }, { type: 'stay', favoriteId: 2 }],
    [{ type: 'trip', movementType: 'CAR' }, { type: 'trip', movementType: 'WALK' }]
  ])('hides incompatible removal merges', async (previousItem, nextItem) => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600 }, { previousItem, nextItem })
    await clickButton(wrapper, 'Remove')
    expect(wrapper.vm.resolutions.map(choice => choice.value)).not.toContain('MERGE')
    expect(wrapper.vm.resolutions.map(choice => choice.value)).toEqual(['EXTEND_PREVIOUS', 'EXTEND_NEXT', 'GAP'])
  })

  it('opens a focused flow and supports Back and Cancel', async () => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home' })
    await clickButton(wrapper, 'Visit label')
    expect(wrapper.text()).toContain('Back')
    expect(wrapper.text()).toContain('Save changes')
    expect(wrapper.text()).toContain('The location keeps its name "Home"')
    await wrapper.findAll('button').find(button => button.text().includes('Back')).trigger('click')
    expect(wrapper.text()).toContain('More changes')
    await clickButton(wrapper, 'Visit label')
    await wrapper.findAll('button').find(button => button.text() === 'Cancel').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('previews a structural edit before submitting it', async () => {
    const item = { type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home', latitude: 1, longitude: 2 }
    const wrapper = mountDrawer(item)
    const store = useTimelineStore()
    vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [{ type: 'STAY', start: item.timestamp, end: '2026-10-08T10:30:00Z', label: 'Dinner', locationName: 'Home' }] })
    vi.spyOn(store, 'createTimelineEdit').mockResolvedValue({ edit: { id: 7 }, timelineJobId: 'job' })

    await clickButton(wrapper, 'Split stay')
    expect(wrapper.text()).toContain('Where were you after this time?')
    await clickButton(wrapper, 'Pick place')
    await clickButton(wrapper, 'Review changes')
    expect(store.previewTimelineEdit).toHaveBeenCalledOnce()
    expect(store.previewTimelineEdit.mock.calls[0][0].payload).toEqual({
      splitAt: expect.any(String), rightFavoriteId: 3
    })
    expect(wrapper.text()).toContain('Visit · Home')
    expect(wrapper.find('.edit-preview').text()).toContain('Dinner')
    await wrapper.find('form').trigger('submit')
    expect(store.createTimelineEdit).toHaveBeenCalledOnce()
    expect(wrapper.emitted('submitted')?.[0]?.[0].edit.id).toBe(7)
  })

  it('keeps the focused flow open when submission fails', async () => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home' })
    const store = useTimelineStore()
    vi.spyOn(store, 'createTimelineEdit').mockRejectedValue(new Error('nope'))
    await clickButton(wrapper, 'Visit label')
    await wrapper.find('input').setValue('Test')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('nope')
    expect(wrapper.text()).toContain('Save changes')
  })

  it('does not split a stay until the second location is chosen', async () => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home' })
    const store = useTimelineStore()
    vi.spyOn(store, 'previewTimelineEdit')
    vi.spyOn(store, 'createTimelineEdit')

    await clickButton(wrapper, 'Split stay')
    await wrapper.find('form').trigger('submit')

    expect(store.previewTimelineEdit).not.toHaveBeenCalled()
    expect(store.createTimelineEdit).not.toHaveBeenCalled()
  })

  it('changes the location by reference, without a stale name or coordinates', async () => {
    const item = { type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home', favoriteId: 1, latitude: 1, longitude: 2 }
    const wrapper = mountDrawer(item)
    const store = useTimelineStore()
    vi.spyOn(store, 'createTimelineEdit').mockResolvedValue({ edit: { id: 8 } })

    await clickButton(wrapper, 'Location')
    expect(wrapper.findComponent({ name: 'TimelinePlacePicker' }).props('current')).toMatchObject({ favoriteId: 1, locationName: 'Home' })
    await clickButton(wrapper, 'Pick place')
    await wrapper.find('form').trigger('submit')

    expect(store.createTimelineEdit).toHaveBeenCalledWith(expect.objectContaining({ kind: 'SET_PLACE', payload: { favoriteId: 3 } }))
  })



  it('explains which trip a merge joins and which stop it removes, and previews it at once', async () => {
    const trip = { type: 'trip', timestamp: '2026-10-08T10:00:00Z', tripDuration: 1200, movementType: 'CAR' }
    const stop = { type: 'stay', timestamp: '2026-10-08T10:20:00Z', stayDuration: 360, locationName: 'Gas station' }
    const nextTrip = { type: 'trip', timestamp: '2026-10-08T10:26:00Z', tripDuration: 1140, movementType: 'CAR' }
    const wrapper = mountDrawer(trip, { nextItem: stop, mergeNextItem: nextTrip })
    const store = useTimelineStore()
    vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [
      { type: 'TRIP', start: trip.timestamp, end: '2026-10-08T10:45:00Z', label: 'CAR' }
    ] })

    expect(wrapper.text()).toContain('Join with a trip a short stop split off')
    await clickButton(wrapper, 'Merge')
    await clickButton(wrapper, 'Review changes')
    await flushPromises()

    expect(wrapper.text()).toContain('Merge with the next trip')
    expect(wrapper.text()).toContain('Removes the 6 minutes stop at Gas station between them.')
    expect(wrapper.find('[role="radio"]').attributes('aria-checked')).toBe('true')
    expect(store.previewTimelineEdit).toHaveBeenCalledWith(expect.objectContaining({
      kind: 'MERGE', payload: { direction: 'NEXT' }
    }))
    expect(wrapper.text()).toContain('Trip · Car')
  })

  it('edits times with the date picker and keeps them exact through the timezone conversion', async () => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home' })
    const store = useTimelineStore()
    vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [] })

    await clickButton(wrapper, 'Split stay')
    const picker = wrapper.findComponent({ name: 'DatePicker' })
    expect(wrapper.find('input[type="datetime-local"]').exists()).toBe(false)
    expect(picker.props('modelValue')).toBeInstanceOf(Date)
    await clickButton(wrapper, 'Pick place')
    await clickButton(wrapper, 'Review changes')
    expect(store.previewTimelineEdit.mock.calls[0][0].payload.splitAt).toBe('2026-10-08T10:30:00.000Z')

    await wrapper.findAll('button').find(button => button.text().includes('Back')).trigger('click')
    await clickButton(wrapper, 'Time')
    await wrapper.findAllComponents({ name: 'DatePicker' })[0].vm.$emit('update:modelValue', new Date(2026, 9, 8, 10, 5))
    await clickButton(wrapper, 'Review changes')
    expect(store.previewTimelineEdit.mock.calls[1][0].payload).toEqual({
      start: '2026-10-08T10:05:00.000Z', end: '2026-10-08T11:00:00.000Z'
    })
  })

  it('lets the split time pass through times outside the visit while it is edited, and blocks applying one', async () => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home' })
    const store = useTimelineStore()
    vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [] })
    vi.spyOn(store, 'createTimelineEdit')

    await clickButton(wrapper, 'Split stay')
    await clickButton(wrapper, 'Pick place')
    const picker = wrapper.findComponent({ name: 'DatePicker' })
    expect(picker.props('minDate')).toBeUndefined()
    expect(picker.props('maxDate')).toBeUndefined()

    // 10:30 with the hour changed first: 09:30, before the visit.
    await picker.vm.$emit('update:modelValue', new Date(picker.props('modelValue').getTime() - 3_600_000))
    expect(wrapper.find('.edit-field-error').exists()).toBe(true)
    expect(wrapper.find('.edit-field-error').text()).toContain('while the visit lasts')
    await wrapper.find('form').trigger('submit')
    expect(store.createTimelineEdit).not.toHaveBeenCalled()

    // Then the minutes: 10:05, inside the visit again.
    await picker.vm.$emit('update:modelValue', new Date(picker.props('modelValue').getTime() + 35 * 60_000))
    expect(wrapper.find('.edit-field-error').exists()).toBe(false)
    await clickButton(wrapper, 'Review changes')
    expect(store.previewTimelineEdit.mock.calls.at(-1)[0].payload.splitAt).toBe('2026-10-08T10:05:00.000Z')
  })
})

describe('review safeguards', () => {
  const stay = { id: 5, type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home' }
  it('never saves a structural edit before its result has been reviewed', async () => {
    const wrapper = mountDrawer(stay)
    const store = useTimelineStore()
    vi.spyOn(store, 'createTimelineEdit').mockResolvedValue({ edit: { id: 1 } })
    vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [] })
    await clickButton(wrapper, 'Split stay')
    await clickButton(wrapper, 'Pick place')
    await wrapper.find('form').trigger('submit')
    expect(store.createTimelineEdit).not.toHaveBeenCalled()
    expect(store.previewTimelineEdit).not.toHaveBeenCalled()
    await clickButton(wrapper, 'Review changes')
    await flushPromises()
    expect(store.createTimelineEdit).not.toHaveBeenCalled()
    await wrapper.find('form').trigger('submit')
    expect(store.createTimelineEdit).toHaveBeenCalledOnce()
  })
  it('ignores a stale preview and requires a new review after time changes', async () => {
    const wrapper = mountDrawer(stay)
    const store = useTimelineStore()
    let resolvePreview
    vi.spyOn(store, 'previewTimelineEdit').mockImplementation(() => new Promise(resolve => { resolvePreview = resolve }))
    vi.spyOn(store, 'createTimelineEdit')
    await clickButton(wrapper, 'Time')
    const picker = wrapper.findAllComponents({ name: 'DatePicker' })[0]
    await picker.vm.$emit('update:modelValue', new Date(2026, 9, 8, 10, 5))
    await clickButton(wrapper, 'Review changes')
    await picker.vm.$emit('update:modelValue', new Date(2026, 9, 8, 10, 10))
    resolvePreview({ events: [{ type: 'STAY', label: 'Stale result', start: stay.timestamp, end: '2026-10-08T11:00:00Z' }] })
    await flushPromises()
    expect(wrapper.text()).not.toContain('Stale result')
    await wrapper.find('form').trigger('submit')
    expect(store.createTimelineEdit).not.toHaveBeenCalled()
  })
  it('requires an explicit removal choice', async () => {
    const wrapper = mountDrawer(stay, { previousItem: { ...stay, timestamp: '2026-10-08T09:00:00Z' } })
    const store = useTimelineStore()
    vi.spyOn(store, 'previewTimelineEdit')
    await clickButton(wrapper, 'Remove')
    expect(wrapper.text()).toContain('Choose what should fill the removed time')
    await clickButton(wrapper, 'Review changes')
    expect(store.previewTimelineEdit).not.toHaveBeenCalled()
  })

  it('keeps a changed form when discarding is declined', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = mountDrawer(stay)
    await clickButton(wrapper, 'Visit label')
    await wrapper.find('input').setValue('Test')
    await clickButton(wrapper, 'Cancel')
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(wrapper.find('input').element.value).toBe('Test')
  })
})

it('removes an existing label through its active correction without submitting an empty label', async () => {
  const item = { id: 5, type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home', visitName: 'Test' }
  const wrapper = mountDrawer(item)
  const store = useTimelineStore()
  vi.spyOn(store, 'fetchTimelineEdits').mockResolvedValue([{ id: 9, seq: 1, kind: 'SET_VISIT_NAME', status: 'ACTIVE', applied: true, target: { type: 'STAY', id: 5 }, anchorStart: item.timestamp, anchorEnd: '2026-10-08T11:00:00Z', payload: { visitName: 'Test' } }])
  vi.spyOn(store, 'revertTimelineEdit').mockResolvedValue({ edit: { id: 9 }, timelineJobId: 'job' })
  vi.spyOn(store, 'createTimelineEdit')
  await wrapper.findComponent({ name: 'TimelineChangesList' }).vm.load()
  await flushPromises()
  await clickButton(wrapper, 'Visit label')
  await clickButton(wrapper, 'Remove label')
  await flushPromises()
  expect(store.revertTimelineEdit).toHaveBeenCalledWith(9)
  expect(store.createTimelineEdit).not.toHaveBeenCalled()
  expect(wrapper.emitted('submitted')[0][0].operation).toBe('undo')
})


it('shows distinct split locations even when both visits carry the same label', async () => {
  const item = { id: 5, type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600, locationName: 'Home', visitName: 'Dinner with Alex' }
  const wrapper = mountDrawer(item)
  const store = useTimelineStore()
  vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [
    { type: 'STAY', start: item.timestamp, end: '2026-10-08T10:30:00Z', label: 'Dinner with Alex', locationName: 'Home' },
    { type: 'STAY', start: '2026-10-08T10:30:00Z', end: '2026-10-08T11:00:00Z', label: 'Dinner with Alex', locationName: 'Gym' }
  ] })
  await clickButton(wrapper, 'Split stay')
  await clickButton(wrapper, 'Pick place')
  await clickButton(wrapper, 'Review changes')
  await flushPromises()
  const titles = wrapper.findAll('.edit-preview-event strong').map(title => title.text())
  expect(titles).toEqual(['Home', 'Visit · Home', 'Visit · Gym'])
  expect(wrapper.findAll('.edit-preview-event small').map(label => label.text())).toEqual(['Dinner with Alex', 'Dinner with Alex', 'Dinner with Alex'])
})

describe('timeline time requests', () => {
  const timezone = useTimezone()
  beforeEach(() => { timezone.setTimezone('Europe/Kyiv'); timezone.setDateFormat('YMD'); timezone.setTimeFormat('24h') })
  afterEach(() => { timezone.setTimezone('UTC'); timezone.setDateFormat('MDY') })
  it.each(['2026-03-29 03:30', '2026-10-25 03:30'])('blocks review and save for uncertain typed time %s', async value => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-25T00:30:00Z', stayDuration: 7200, locationName: 'Home' })
    const store = useTimelineStore()
    vi.spyOn(store, 'previewTimelineEdit')
    vi.spyOn(store, 'createTimelineEdit')
    await clickButton(wrapper, 'Time')
    wrapper.vm.onTimeInput('start', { target: { value } })
    await flushPromises()
    expect(wrapper.vm.validInput).toBe(false)
    expect(wrapper.find('[role="status"]').text()).not.toBe('')
    await wrapper.vm.loadPreview()
    await wrapper.vm.apply()
    expect(store.previewTimelineEdit).not.toHaveBeenCalled()
    expect(store.createTimelineEdit).not.toHaveBeenCalled()
  })
  it('keeps the original repeated-hour instant and milliseconds when only the end changes', async () => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-25T00:30:00.123Z', stayDuration: 7200, locationName: 'Home' })
    const store = useTimelineStore()
    vi.spyOn(store, 'previewTimelineEdit').mockResolvedValue({ events: [] })
    vi.spyOn(store, 'createTimelineEdit').mockResolvedValue({ edit: { id: 9 }, timelineJobId: 'job' })
    await clickButton(wrapper, 'Time')
    wrapper.findAllComponents({ name: 'DatePicker' })[1].vm.$emit('update:modelValue', new Date(2026, 9, 25, 5, 0))
    await flushPromises()
    await clickButton(wrapper, 'Review changes')
    await wrapper.find('form').trigger('submit')
    expect(store.createTimelineEdit.mock.calls[0][0].payload).toEqual({ start: '2026-10-25T00:30:00.123Z', end: '2026-10-25T03:00:00.000Z' })
  })
  it('blocks duplicate saves while the accepted request is pending', async () => {
    const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-25T00:30:00Z', stayDuration: 7200, locationName: 'Home' })
    const store = useTimelineStore()
    let resolveSave
    vi.spyOn(store, 'createTimelineEdit').mockImplementation(() => new Promise(resolve => { resolveSave = resolve }))
    await clickButton(wrapper, 'Visit label')
    await wrapper.find('input').setValue('Only once')
    await wrapper.find('form').trigger('submit')
    await wrapper.find('form').trigger('submit')
    expect(store.createTimelineEdit).toHaveBeenCalledOnce()
    resolveSave({ edit: { id: 10 }, timelineJobId: 'job' })
    await flushPromises()
    expect(wrapper.emitted('submitted')).toHaveLength(1)
  })
})

it('returns keyboard focus to the connected opener after the drawer closes', async () => {
  const opener = document.createElement('button')
  document.body.append(opener); opener.focus()
  const wrapper = mountDrawer({ type: 'stay', timestamp: '2026-10-08T10:00:00Z', stayDuration: 3600 })
  wrapper.findComponent({ name: 'Drawer' }).vm.$emit('show')
  opener.blur()
  wrapper.findComponent({ name: 'Drawer' }).vm.$emit('after-hide')
  expect(document.activeElement).toBe(opener)
  wrapper.unmount(); opener.remove()
})
