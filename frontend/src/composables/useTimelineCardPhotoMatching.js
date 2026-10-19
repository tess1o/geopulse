import { computed, unref } from 'vue'
import { useTimezone } from '@/composables/useTimezone'

const resolvePhotoTimestamp = (photo) => {
  return photo?.takenAt || photo?.createdAt || null
}

export const useTimelineCardPhotoMatching = ({
  itemRef,
  immichPhotosRef,
  durationField,
  currentDateRef = null,
  clampToCurrentDay = false
}) => {
  const timezone = useTimezone()

  const matchingPhotos = computed(() => {
    const item = unref(itemRef)
    const immichPhotos = unref(immichPhotosRef)
    const currentDate = currentDateRef ? unref(currentDateRef) : null

    if (!item?.timestamp || !Array.isArray(immichPhotos) || immichPhotos.length === 0) {
      return []
    }

    const itemStart = timezone.fromUtc(item.timestamp)
    if (!itemStart.isValid()) {
      return []
    }

    const durationSeconds = Math.max(0, Number(item[durationField]) || 0)
    let windowStart = itemStart
    let windowEnd = itemStart.add(durationSeconds, 'second')

    if (clampToCurrentDay) {
      if (!currentDate) {
        return []
      }

      // Location time mode: the item's local day; profile mode: the profile-timezone day
      const localBounds = timezone.isLocationTimeMode() ? timezone.getItemDayBounds(item, currentDate) : null
      const dayStart = localBounds ? localBounds.start : timezone.create(currentDate).startOf('day')
      const dayEnd = localBounds ? localBounds.end : dayStart.endOf('day')
      windowStart = windowStart.isAfter(dayStart) ? windowStart : dayStart
      windowEnd = windowEnd.isBefore(dayEnd) ? windowEnd : dayEnd
    }

    if (windowEnd.isBefore(windowStart)) {
      return []
    }

    const windowStartMs = windowStart.valueOf()
    const windowEndMs = windowEnd.valueOf()

    return immichPhotos.filter((photo) => {
      const photoTimestamp = resolvePhotoTimestamp(photo)
      if (!photoTimestamp) {
        return false
      }

      const photoTime = timezone.fromUtc(photoTimestamp)
      if (!photoTime.isValid()) {
        return false
      }

      const photoMs = photoTime.valueOf()
      return photoMs >= windowStartMs && photoMs <= windowEndMs
    })
  })

  const hasMatchingPhotos = computed(() => matchingPhotos.value.length > 0)

  return {
    matchingPhotos,
    hasMatchingPhotos
  }
}
