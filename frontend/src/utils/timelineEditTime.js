import dayjs from 'dayjs'
import customParseFormat from 'dayjs/plugin/customParseFormat'
dayjs.extend(customParseFormat)

// Require a unique user-zone instant, unless an existing boundary was left unchanged.
export function timelinePickerToUtc(value, timezone, original = null) {
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) return null
  const wall = dayjs(value).format('YYYY-MM-DDTHH:mm:ss.SSS')
  if (original && timezone.fromUtc(original).format('YYYY-MM-DDTHH:mm:ss.SSS') === wall) return original
  const converted = timezone.createDateTimeFromPicker(value)
  if (!converted || converted.format('YYYY-MM-DDTHH:mm:ss.SSS') !== wall) return null
  const offsets = new Set([-1, 0, 1].map(days => timezone.fromUtc(new Date(converted.valueOf() + days * 86400000).toISOString()).utcOffset()))
  const matches = [...offsets].map(offset => new Date(Date.parse(`${wall}Z`) - offset * 60000).toISOString())
    .filter(instant => timezone.fromUtc(instant).format('YYYY-MM-DDTHH:mm:ss.SSS') === wall)
  return matches.length === 1 ? matches[0] : null
}

// shortcut: PrimeVue 4.5.5 rejects typed 24-hour times; remove when its input parser is fixed.
export function parseTimelinePickerInput(event, timezone, seconds = false) {
  const date = timezone.getPrimeVueDatePickerFormat().replace('mm', 'MM').replace('dd', 'DD').replace('yy', 'YYYY')
  const time = timezone.getTimeFormatPattern(seconds)
  const parsed = dayjs(event.target.value, [`${date} ${time}`, `${date} ${time.replace(/^h:/, 'hh:')}`], true)
  return parsed.isValid() ? parsed.toDate() : null
}
