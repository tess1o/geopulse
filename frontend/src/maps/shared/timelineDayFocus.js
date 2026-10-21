import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

/**
 * True when a day is focused and none of the items is on it. Uses the timeline list's own day rule, so an
 * overnight stay counts for both days and a day header and the map always agree.
 */
export const isOutsideFocusedDay = (items, focusedDay) => (
  Boolean(focusedDay)
  && !(Array.isArray(items) ? items : []).some((item) => item && timezone.shouldItemAppearOnDate(item, focusedDay))
)
