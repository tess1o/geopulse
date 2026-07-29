import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useTimelineItemSelection } from './useTimelineItemSelection'

const highlightStore = vi.hoisted(() => ({
  highlightedItem: null,
  clearAllHighlights: vi.fn(() => {
    highlightStore.highlightedItem = null
  }),
  isItemHighlighted: vi.fn((item) => highlightStore.highlightedItem?.timestamp === item?.timestamp),
  setHighlightedItem: vi.fn((item) => {
    highlightStore.highlightedItem = item
  })
}))

vi.mock('@/stores/highlight', () => ({
  useHighlightStore: () => highlightStore
}))

describe('useTimelineItemSelection', () => {
  beforeEach(() => {
    highlightStore.highlightedItem = null
    vi.clearAllMocks()
  })

  it('sets a stay highlight from a card click', () => {
    const stay = { type: 'stay', timestamp: '2026-07-29T10:00:00Z' }
    const { handleTimelineItemClick } = useTimelineItemSelection()

    handleTimelineItemClick(stay)

    expect(highlightStore.setHighlightedItem).toHaveBeenCalledWith(stay)
    expect(highlightStore.clearAllHighlights).not.toHaveBeenCalled()
  })

  it('clears selection when clicking the highlighted item again', () => {
    const trip = { type: 'trip', timestamp: '2026-07-29T11:00:00Z' }
    highlightStore.highlightedItem = trip
    const { handleTimelineItemClick } = useTimelineItemSelection()

    handleTimelineItemClick(trip)

    expect(highlightStore.clearAllHighlights).toHaveBeenCalledTimes(1)
    expect(highlightStore.setHighlightedItem).not.toHaveBeenCalled()
  })

  it('collapses the mobile timeline when selecting a trip', () => {
    const trip = { type: 'trip', timestamp: '2026-07-29T12:00:00Z' }
    const collapseForMobileSelection = vi.fn()
    const { handleTimelineMarkerClick } = useTimelineItemSelection({ collapseForMobileSelection })

    handleTimelineMarkerClick({ timelineItem: trip })

    expect(collapseForMobileSelection).toHaveBeenCalledTimes(1)
    expect(highlightStore.setHighlightedItem).toHaveBeenCalledWith(trip)
  })
})
