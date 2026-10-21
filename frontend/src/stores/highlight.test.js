import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useHighlightStore } from '@/stores/highlight'
import { useTimezone } from '@/composables/useTimezone'

const trip = (timestamp, tripDuration = 1800) => ({ type: 'trip', timestamp, tripDuration })
const stay = (timestamp, stayDuration = 3600) => ({ type: 'stay', timestamp, stayDuration })

describe('highlight store day focus', () => {
  const tz = useTimezone()

  beforeEach(() => {
    setActivePinia(createPinia())
    tz.setTimezone('UTC')
  })

  afterEach(() => {
    tz.setTimezone('UTC')
  })

  it('keeps the focused day when an item from that day is selected', () => {
    const store = useHighlightStore()
    store.focusDay('2026-10-13')

    store.setHighlightedItem(trip('2026-10-13T08:00:00Z'))

    expect(store.focusedDay).toBe('2026-10-13')
    expect(store.highlightedTrip).toBeTruthy()

    store.clearAllHighlights()
    expect(store.focusedDay).toBe('2026-10-13')
  })

  it('ends the day focus when a trip or stay from another day is selected', () => {
    const store = useHighlightStore()
    store.focusDay('2026-10-13')
    store.setHighlightedItem(trip('2026-10-14T08:00:00Z'))
    expect(store.focusedDay).toBeNull()
    expect(store.highlightedTrip).toBeTruthy()

    store.focusDay('2026-10-13')
    store.setHighlightedItem(stay('2026-10-12T08:00:00Z'))
    expect(store.focusedDay).toBeNull()
  })

  it('counts an overnight stay as part of both days', () => {
    const store = useHighlightStore()
    store.focusDay('2026-10-14')

    store.setHighlightedItem(stay('2026-10-13T20:00:00Z', 12 * 3600))

    expect(store.focusedDay).toBe('2026-10-14')
  })

  it('drops a selection from another day when a day is focused', () => {
    const store = useHighlightStore()
    store.setHighlightedItem(trip('2026-10-14T08:00:00Z'))

    store.focusDay('2026-10-13')
    expect(store.highlightedTrip).toBeNull()

    store.setHighlightedItem(trip('2026-10-13T08:00:00Z'))
    store.focusDay('2026-10-13')
    expect(store.highlightedTrip).toBeTruthy()
  })

  it('toggles the focused day', () => {
    const store = useHighlightStore()
    store.toggleFocusedDay('2026-10-13')
    expect(store.focusedDay).toBe('2026-10-13')
    store.toggleFocusedDay('2026-10-14')
    expect(store.focusedDay).toBe('2026-10-14')
    store.toggleFocusedDay('2026-10-14')
    expect(store.focusedDay).toBeNull()
  })
})
