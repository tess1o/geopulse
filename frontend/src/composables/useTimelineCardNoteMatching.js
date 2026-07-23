import { computed, unref } from 'vue'
import { useTimezone } from '@/composables/useTimezone'

export const resolveNoteTimestamp = (note) => note?.eventTime || note?.createdAt || null

const anchoredNoteTypes = new Set(['STAY', 'TRIP'])

const timelineItemAnchorType = (item, durationField) => {
  if (item?.type === 'stay' || durationField === 'stayDuration') {
    return 'STAY'
  }
  if (item?.type === 'trip' || durationField === 'tripDuration') {
    return 'TRIP'
  }
  return null
}

const sameId = (left, right) => left != null && right != null && String(left) === String(right)

export const useTimelineCardNoteMatching = ({
  itemRef,
  notesRef,
  durationField,
  currentDateRef = null,
  clampToCurrentDay = false
}) => {
  const timezone = useTimezone()

  const matchingNotes = computed(() => {
    const item = unref(itemRef)
    const notes = unref(notesRef)
    const currentDate = currentDateRef ? unref(currentDateRef) : null

    if (!item?.timestamp || !Array.isArray(notes) || notes.length === 0) {
      return []
    }

    const itemStart = timezone.fromUtc(item.timestamp)
    if (!itemStart.isValid()) {
      return []
    }

    const itemAnchorType = timelineItemAnchorType(item, durationField)
    const durationSeconds = Math.max(0, Number(item[durationField]) || 0)
    let windowStart = itemStart
    let windowEnd = itemStart.add(durationSeconds, 'second')

    if (clampToCurrentDay) {
      if (!currentDate) {
        return []
      }

      const dayStart = timezone.create(currentDate).startOf('day')
      const dayEnd = dayStart.add(1, 'day')
      windowStart = windowStart.isAfter(dayStart) ? windowStart : dayStart
      windowEnd = windowEnd.isBefore(dayEnd) ? windowEnd : dayEnd
    }

    if (windowEnd.isBefore(windowStart)) {
      return []
    }

    const windowStartMs = windowStart.valueOf()
    const windowEndMs = windowEnd.valueOf()

    return notes.filter((note) => {
      if (anchoredNoteTypes.has(note?.anchorType) && note.anchorId != null) {
        return note.anchorType === itemAnchorType && sameId(note.anchorId, item.id)
      }

      const timestamp = resolveNoteTimestamp(note)
      if (!timestamp) {
        return false
      }

      const noteTime = timezone.fromUtc(timestamp)
      if (!noteTime.isValid()) {
        return false
      }

      const noteMs = noteTime.valueOf()
      return durationSeconds === 0
        ? noteMs === windowStartMs
        : noteMs >= windowStartMs && noteMs < windowEndMs
    })
  })

  const hasMatchingNotes = computed(() => matchingNotes.value.length > 0)

  return {
    matchingNotes,
    hasMatchingNotes
  }
}
