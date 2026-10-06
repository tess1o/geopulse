import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

// Sortable needs real layout, so the draggable list is stubbed: the test drives its v-model
// and start/end events the way Sortable would at the end of a drag.
vi.mock('vue-draggable-plus', () => ({
  VueDraggable: {
    name: 'VueDraggable',
    props: ['modelValue', 'disabled'],
    emits: ['update:modelValue', 'start', 'end'],
    template: '<div class="draggable-stub" :data-disabled="String(disabled)"><slot /></div>'
  }
}))

const MenuStub = {
  name: 'Menu',
  props: ['model'],
  methods: { toggle() {} },
  template: '<div class="menu-stub" />'
}

const stops = [
  { id: 1, title: 'Museum', plannedDay: '2026-05-01', orderIndex: 0, priority: 'MUST', latitude: 1, longitude: 1 },
  { id: 2, title: 'Park', plannedDay: '2026-05-01', orderIndex: 1, priority: 'OPTIONAL', latitude: 2, longitude: 2 },
  { id: 3, title: 'Cafe', plannedDay: null, orderIndex: 2, priority: 'OPTIONAL' }
]

const mountRail = async (props = {}) => {
  const Rail = (await import('./TripStopsRail.vue')).default
  return mount(Rail, {
    props: { stops, canEdit: true, tripDays: ['2026-05-01', '2026-05-02'], ...props },
    global: {
      stubs: { Menu: MenuStub, TripAddStopPanel: true, ProgressSpinner: true, SelectButton: true },
      directives: { tooltip: {} }
    }
  })
}

describe('TripStopsRail', () => {
  it('numbers stops in plan order', async () => {
    const wrapper = await mountRail()
    expect(wrapper.findAll('.trip-stop-sequence').map((el) => el.text())).toEqual(['1', '2', '3'])
  })

  it('keeps empty trip days as hidden drop targets', async () => {
    const wrapper = await mountRail()
    const groups = wrapper.findAll('.trip-rail-group')

    // 2026-05-01, the empty 2026-05-02, and unscheduled.
    expect(groups).toHaveLength(3)
    expect(groups[1].classes()).toContain('trip-rail-group--empty')
  })

  it('emits the full new order when a stop is dragged to another day', async () => {
    const wrapper = await mountRail()
    const lists = wrapper.findAllComponents({ name: 'VueDraggable' })

    lists[0].vm.$emit('start')
    // Park moves from 2026-05-01 to the empty 2026-05-02.
    lists[0].vm.$emit('update:modelValue', [stops[0]])
    lists[1].vm.$emit('update:modelValue', [stops[1]])
    lists[0].vm.$emit('end')
    await nextTick()
    await nextTick()

    expect(wrapper.emitted('reorder')).toEqual([[
      [
        { id: 1, plannedDay: '2026-05-01' },
        { id: 2, plannedDay: '2026-05-02' },
        { id: 3, plannedDay: null }
      ]
    ]])
  })

  it('does not emit when a drag ends where it started', async () => {
    const wrapper = await mountRail()
    const lists = wrapper.findAllComponents({ name: 'VueDraggable' })

    lists[0].vm.$emit('start')
    lists[0].vm.$emit('end')
    await nextTick()
    await nextTick()

    expect(wrapper.emitted('reorder')).toBeUndefined()
  })

  it('puts every stop action behind one menu', async () => {
    const wrapper = await mountRail()
    await wrapper.find('.trip-stop-menu').trigger('click')

    const items = wrapper.findComponent({ name: 'Menu' }).props('model').filter((item) => !item.separator)
    items.forEach((item) => item.command())

    expect(wrapper.emitted('visit-override').map(([payload]) => payload.action))
      .toEqual(['CONFIRM_VISITED', 'REJECT_VISIT', 'RESET_TO_AUTO'])
    expect(wrapper.emitted('visit-override')[0][0].stop.id).toBe(1)
    expect(wrapper.emitted('edit-stop')[0][0].id).toBe(1)
    expect(wrapper.emitted('delete-stop')[0][0].id).toBe(1)
    // Nothing manual to reset on this stop.
    expect(items[2].disabled).toBe(true)
  })

  it('opens the editor on double-click', async () => {
    const wrapper = await mountRail()
    await wrapper.find('.trip-stop-main').trigger('dblclick')
    expect(wrapper.emitted('edit-stop')[0][0].id).toBe(1)
  })

  it('is read-only for viewers', async () => {
    const wrapper = await mountRail({ canEdit: false })

    expect(wrapper.find('.trip-stop-menu').exists()).toBe(false)
    expect(wrapper.find('.trip-stop-grip').exists()).toBe(false)
    expect(wrapper.findAll('.draggable-stub').every((el) => el.attributes('data-disabled') === 'true')).toBe(true)
    // No drop targets either: only the days that have stops.
    expect(wrapper.findAll('.trip-rail-group')).toHaveLength(2)

    await wrapper.find('.trip-stop-main').trigger('dblclick')
    expect(wrapper.emitted('edit-stop')).toBeUndefined()
  })

  it('draws the travel from the previous stop as a connector', async () => {
    const wrapper = await mountRail({
      legsByStop: new Map([
        [2, { fromItemId: 1, toItemId: 2, routed: true, resolvedMode: 'WALK', distanceMeters: 1200, durationSeconds: 900 }],
        [3, { fromItemId: 2, toItemId: 3, routed: false, resolvedMode: 'STRAIGHT', distanceMeters: 470000 }]
      ])
    })

    const cards = wrapper.findAll('.trip-stop')
    expect(cards[0].find('.trip-stop-leg').exists()).toBe(false)
    expect(cards[1].find('.trip-stop-leg i').classes()).toContain('fa-walking')
    expect(cards[2].find('.trip-stop-leg').text()).toContain('straight line')
    // The connector sits outside the card body.
    expect(cards[1].find('.trip-stop-card .trip-stop-leg').exists()).toBe(false)
  })

  it('skips placeholder legs that have no distance yet', async () => {
    const wrapper = await mountRail({
      legsByStop: new Map([[2, { fromItemId: 1, toItemId: 2, routed: false, coordinates: [] }]])
    })
    expect(wrapper.find('.trip-stop-leg').exists()).toBe(false)
  })

  it('labels days with their place in the trip and sums the travel within each day', async () => {
    const wrapper = await mountRail({
      legsByStop: new Map([
        [2, { fromItemId: 1, toItemId: 2, routed: true, resolvedMode: 'WALK', distanceMeters: 1200, durationSeconds: 900 }],
        // Arrives in another group, so it does not count towards day 1.
        [3, { fromItemId: 2, toItemId: 3, routed: true, resolvedMode: 'DRIVE', distanceMeters: 9000, durationSeconds: 600 }]
      ])
    })

    const header = wrapper.find('.trip-rail-group-header')
    expect(header.find('.trip-rail-group-title').text()).toMatch(/^Day 1 · Fri,? 1 May$|^Day 1 · Fri, May 1$/)
    expect(header.find('.trip-rail-group-meta').text()).toBe('2 stops · 1 km')
  })

  it('shows a status only when it says something', async () => {
    const wrapper = await mountRail({
      stops: [
        stops[0],                                                       // planned with a location: nothing
        { ...stops[1], visitConfidence: 0.82 },                          // needs review
        { ...stops[2] },                                                 // no location
        { id: 4, title: 'Bridge', plannedDay: null, orderIndex: 3, latitude: 3, longitude: 3, manualOverrideState: 'REJECTED' }
      ]
    })

    const cards = wrapper.findAll('.trip-stop')
    expect(cards.map((card) => card.attributes('data-visit-status'))).toEqual(['planned', 'review', 'planned', 'missed'])
    expect(cards[0].find('.trip-stop-status').exists()).toBe(false)
    expect(cards[1].find('.trip-stop-status').text()).toBe('Needs review · 82%')
    expect(cards[2].find('.trip-stop-status').text()).toBe('No location')
    expect(cards[3].find('.trip-stop-status').text()).toBe('Missed')
  })

  it('shows priority as a badge colour and a star, not a label', async () => {
    const wrapper = await mountRail({
      stops: [
        ...stops.slice(0, 2),
        { ...stops[2], priority: 'MUST', isVisited: true, visitConfidence: 0.96 }
      ]
    })
    const badges = wrapper.findAll('.trip-stop-sequence')

    expect(badges.map((badge) => badge.classes().find((c) => c.startsWith('trip-stop-sequence--'))))
      .toEqual(['trip-stop-sequence--must', 'trip-stop-sequence--optional', 'trip-stop-sequence--visited'])
    // Only must-visit stops get the star, inline in the title so it never wraps alone.
    expect(wrapper.findAll('.trip-stop').map((card) => card.find('.trip-stop-title .trip-stop-must').exists()))
      .toEqual([true, false, true])
    expect(wrapper.findAll('.trip-stop-title-row').map((row) => row.text()).join(' ')).not.toMatch(/Must|Optional/)
    // The meaning, including the visit and its confidence, is still available to screen readers.
    expect(badges[0].attributes('aria-label')).toBe('Stop 1 · Must visit')
    expect(badges[2].attributes('aria-label')).toBe('Stop 3 · Must visit · Visited (96%)')
  })

  it('offers a collapse button only when the page can collapse it', async () => {
    const plain = await mountRail()
    expect(plain.find('.trip-rail-collapse').exists()).toBe(false)

    const wrapper = await mountRail({ collapsible: true })
    const button = wrapper.find('.trip-rail-collapse')
    expect(button.attributes('aria-label')).toBe('Collapse stops')
    await button.trigger('click')
    expect(wrapper.emitted('collapse')).toHaveLength(1)
  })
})
