import { useHighlightStore } from '@/stores/highlight'

const normalizeTimelineItem = (itemOrEvent) => {
  if (!itemOrEvent) {
    return null
  }

  return itemOrEvent.timelineItem
    || itemOrEvent.tripData
    || itemOrEvent.item
    || itemOrEvent
}

export function useTimelineItemSelection(options = {}) {
  const highlightStore = useHighlightStore()

  const clearTimelineSelection = () => {
    highlightStore.clearAllHighlights()
  }

  const selectTimelineItem = (itemOrEvent) => {
    const item = normalizeTimelineItem(itemOrEvent)
    if (!item) {
      return null
    }

    if (highlightStore.isItemHighlighted(item)) {
      clearTimelineSelection()
      return item
    }

    if (item.type === 'trip') {
      options.collapseForMobileSelection?.()
    }

    highlightStore.setHighlightedItem(item)
    return item
  }

  return {
    clearTimelineSelection,
    normalizeTimelineItem,
    selectTimelineItem,
    handleTimelineItemClick: selectTimelineItem,
    handleTimelineMarkerClick: selectTimelineItem,
    handleHighlightedPathClick: selectTimelineItem
  }
}
