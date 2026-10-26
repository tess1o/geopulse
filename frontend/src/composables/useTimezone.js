import {ref} from 'vue'
import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import duration from 'dayjs/plugin/duration'
import isBetween from 'dayjs/plugin/isBetween'
import isSameOrBefore from 'dayjs/plugin/isSameOrBefore'
import isSameOrAfter from 'dayjs/plugin/isSameOrAfter'
import customParseFormat from 'dayjs/plugin/customParseFormat'
import {formatDurationCompact} from '@/utils/calculationsHelpers'
import { readCachedUserProfile } from '@/utils/userProfileCache'
import { t } from '@/locales'

dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.extend(duration)
dayjs.extend(isBetween)
dayjs.extend(isSameOrBefore)
dayjs.extend(isSameOrAfter)
dayjs.extend(customParseFormat)

const DEFAULT_DATE_FORMAT = 'MDY'
const DEFAULT_TIME_FORMAT = '24h'
const DEFAULT_TIME_DISPLAY_MODE = 'profile'
const LOCATION_TIME_DISPLAY_MODE = 'location'
// Largest gap between two UTC offsets (+14 vs -12): a local day can start or end this far from the profile-timezone day.
export const LOCATION_DAY_FETCH_PADDING_HOURS = 26
const DATE_FORMAT_PATTERNS = {
    MDY: 'MM/DD/YYYY',
    DMY: 'DD/MM/YYYY',
    YMD: 'YYYY-MM-DD'
}
const DATE_FORMAT_PRIMEVUE = {
    MDY: 'mm/dd/yy',
    DMY: 'dd/mm/yy',
    YMD: 'yy-mm-dd'
}
const DATE_FORMAT_FIRST_DAY_OF_WEEK = {
    MDY: 0,
    DMY: 1,
    YMD: 1
}

const normalizeDateFormat = (value) => {
    const normalized = String(value || '').trim().toUpperCase()
    return Object.prototype.hasOwnProperty.call(DATE_FORMAT_PATTERNS, normalized)
        ? normalized
        : DEFAULT_DATE_FORMAT
}

const normalizeTimeFormat = (value) => {
    const normalized = String(value || '').trim().toLowerCase()
    return normalized === '12h' ? '12h' : DEFAULT_TIME_FORMAT
}

const normalizeTimeDisplayMode = (value) => {
    const normalized = String(value || '').trim().toLowerCase()
    return normalized === LOCATION_TIME_DISPLAY_MODE ? LOCATION_TIME_DISPLAY_MODE : DEFAULT_TIME_DISPLAY_MODE
}

const getUserTimezoneFromStorage = () => {
    try {
        return readCachedUserProfile().timezone || 'UTC'
    } catch (error) {
        console.warn('Failed to get user timezone from cached profile:', error)
        return 'UTC'
    }
}

const getUserDateFormatFromStorage = () => {
    try {
        return normalizeDateFormat(readCachedUserProfile().dateFormat)
    } catch (error) {
        console.warn('Failed to get user date format from cached profile:', error)
        return DEFAULT_DATE_FORMAT
    }
}

const getUserTimeFormatFromStorage = () => {
    try {
        return normalizeTimeFormat(readCachedUserProfile().timeFormat)
    } catch (error) {
        console.warn('Failed to get user time format from cached profile:', error)
        return DEFAULT_TIME_FORMAT
    }
}

const getUserTimeDisplayModeFromStorage = () => {
    try {
        return normalizeTimeDisplayMode(readCachedUserProfile().timeDisplayMode)
    } catch (error) {
        console.warn('Failed to get user time display mode from cached profile:', error)
        return DEFAULT_TIME_DISPLAY_MODE
    }
}

const userTimezone = ref(getUserTimezoneFromStorage())
const userDateFormat = ref(getUserDateFormatFromStorage())
const userTimeFormat = ref(getUserTimeFormatFromStorage())
const userTimeDisplayMode = ref(getUserTimeDisplayModeFromStorage())
// ISO country of the profile timezone (e.g. UA for Europe/Kyiv), loaded from the server in "location" mode.
// Null means unknown: only UTC offsets then decide which items are labelled.
const locationTimeHomeCountry = ref(null)

// Intl formatters are expensive to create; one per IANA zone is enough.
const zoneNameFormatters = new Map()

const getShortZoneName = (date, zone) => {
    try {
        let formatter = zoneNameFormatters.get(zone)
        if (!formatter) {
            formatter = new Intl.DateTimeFormat('en-US', { timeZone: zone, timeZoneName: 'short' })
            zoneNameFormatters.set(zone, formatter)
        }
        return formatter.formatToParts(date).find((part) => part.type === 'timeZoneName')?.value || zone
    } catch {
        return zone
    }
}

export function useTimezone() {
    // --- Core Timezone Management ---

    const setTimezone = (tz) => {
        userTimezone.value = tz
    }

    const getTimezone = () => userTimezone.value

    const setDateFormat = (dateFormat) => {
        userDateFormat.value = normalizeDateFormat(dateFormat)
    }

    const getDateFormat = () => userDateFormat.value

    const setTimeFormat = (timeFormat) => {
        userTimeFormat.value = normalizeTimeFormat(timeFormat)
    }

    const getTimeFormat = () => userTimeFormat.value

    const setTimeDisplayMode = (mode) => {
        userTimeDisplayMode.value = normalizeTimeDisplayMode(mode)
    }

    const getTimeDisplayMode = () => userTimeDisplayMode.value

    const isLocationTimeMode = () => userTimeDisplayMode.value === LOCATION_TIME_DISPLAY_MODE

    /**
     * Request params plus `includeLocationTimezones=true` in "location" mode. In profile mode the params are
     * returned untouched (possibly undefined), so default requests stay exactly as they were.
     */
    const withLocationTimezoneParams = (params) => (
        isLocationTimeMode() ? { ...(params || {}), includeLocationTimezones: true } : params
    )

    const setLocationTimeHomeCountry = (countryCode) => {
        locationTimeHomeCountry.value = countryCode ? String(countryCode).trim().toUpperCase() : null
    }

    const now = () => {
        // Use explicit Date object to avoid browser timezone detection issues (LibreWolf, etc.)
        return dayjs(new Date()).tz(userTimezone.value)
    }

    const fromUtc = (date) => dayjs.utc(date).tz(userTimezone.value)

    const toUtc = (date) => dayjs.tz(date, userTimezone.value).utc()

    // --- Date Creation (always interpret in user timezone) ---

    const create = (date) => dayjs.tz(date, userTimezone.value)

    const createFromCalendar = (calendarDate) => {
        // Convert browser timezone calendar selection to user timezone
        const localDateStr = calendarDate.toLocaleDateString('en-CA') // YYYY-MM-DD
        return dayjs.tz(`${localDateStr}T12:00:00`, userTimezone.value)
    }

    // --- Start/End Boundaries (return UTC for backend) ---

    const startOfDayUtc = (date) =>
        dayjs.tz(date, userTimezone.value).startOf('day').utc().toISOString()

    const endOfDayUtc = (date) =>
        dayjs.tz(date, userTimezone.value).endOf('day').utc().toISOString()

    const startOfWeekUtc = (date) =>
        dayjs.tz(date, userTimezone.value).startOf('week').utc().toISOString()

    const endOfWeekUtc = (date) =>
        dayjs.tz(date, userTimezone.value).endOf('week').utc().toISOString()

    const startOfMonthUtc = (date) =>
        dayjs.tz(date, userTimezone.value).startOf('month').utc().toISOString()

    const endOfMonthUtc = (date) =>
        dayjs.tz(date, userTimezone.value).endOf('month').utc().toISOString()

    // --- Date Range Creation (for API calls) ---

    const createDateRangeUtc = (startDate, endDate) => {
        const start = dayjs.tz(startDate, userTimezone.value).startOf('day').utc().toISOString()
        const end = dayjs.tz(endDate, userTimezone.value).endOf('day').utc().toISOString()
        return {start, end}
    }

    const createDateRangeFromPicker = (pickerStartDate, pickerEndDate) => {
        // Handle browser timezone date picker selections
        const startCalendar = createFromCalendar(pickerStartDate)
        const endCalendar = createFromCalendar(pickerEndDate)
        return createDateRangeUtc(startCalendar, endCalendar)
    }

    const createDateTimeFromPicker = (pickerDate) => {
        if (!(pickerDate instanceof Date) || Number.isNaN(pickerDate.getTime())) {
            return null
        }

        // Rebuild a timezone-naive datetime from picker components and interpret in user timezone.
        // This preserves selected hour/minute even when browser timezone differs from user timezone.
        const year = pickerDate.getFullYear()
        const month = String(pickerDate.getMonth() + 1).padStart(2, '0')
        const day = String(pickerDate.getDate()).padStart(2, '0')
        const hour = String(pickerDate.getHours()).padStart(2, '0')
        const minute = String(pickerDate.getMinutes()).padStart(2, '0')
        const second = String(pickerDate.getSeconds()).padStart(2, '0')
        const millisecond = String(pickerDate.getMilliseconds()).padStart(3, '0')
        const dateTimeString = `${year}-${month}-${day}T${hour}:${minute}:${second}.${millisecond}`

        return dayjs.tz(dateTimeString, userTimezone.value)
    }

    const createDateTimeRangeFromPicker = (pickerStartDate, pickerEndDate) => {
        const startDateTime = createDateTimeFromPicker(pickerStartDate)
        const endDateTime = createDateTimeFromPicker(pickerEndDate)

        return {
            start: startDateTime ? startDateTime.utc().toISOString() : null,
            end: endDateTime ? endDateTime.utc().toISOString() : null
        }
    }

    const parseUrlDate = (dateString, isEndDate = false) => {
        if (!dateString) return null

        const formatsToTry = ['YYYY-MM-DD'] // Preferred URL format (stable/locale-independent)

        const userFormat = DATE_FORMAT_PATTERNS[getDateFormat()]
        if (userFormat && !formatsToTry.includes(userFormat)) {
            formatsToTry.push(userFormat)
        }

        if (!formatsToTry.includes('MM/DD/YYYY')) {
            formatsToTry.push('MM/DD/YYYY') // Legacy URL format for backward compatibility
        }

        for (const formatPattern of formatsToTry) {
            const date = dayjs.tz(dateString, formatPattern, userTimezone.value)
            if (date.isValid()) {
                return isEndDate ? endOfDayUtc(date) : startOfDayUtc(date)
            }
        }

        return null
    }

    const convertUtcRangeToCalendarDates = (utcStart, utcEnd) => {
        // Convert UTC range back to calendar dates for display in date picker
        const startInUserTz = fromUtc(utcStart).startOf('day')
        const endInUserTz = fromUtc(utcEnd).startOf('day')

        // Create Date objects at noon using year/month/date components to avoid timezone conversion issues
        // This ensures the date picker shows the correct date regardless of browser timezone (LibreWolf, etc.)
        const startCalendar = new Date(startInUserTz.year(), startInUserTz.month(), startInUserTz.date(), 12, 0, 0)
        const endCalendar = new Date(endInUserTz.year(), endInUserTz.month(), endInUserTz.date(), 12, 0, 0)

        return [startCalendar, endCalendar]
    }

    // --- Math Operations (in user timezone) ---

    const add = (date, amount, unit) =>
        dayjs.tz(date, userTimezone.value).add(amount, unit)

    const subtract = (date, amount, unit) =>
        dayjs.tz(date, userTimezone.value).subtract(amount, unit)

    const diff = (date1, date2, unit = 'millisecond', precise = false) =>
        dayjs.tz(date1, userTimezone.value).diff(
            dayjs.tz(date2, userTimezone.value),
            unit,
            precise
        )

    const diffInDays = (date1, date2) => diff(date1, date2, 'day')

    const isSameDay = (date1, date2) =>
        dayjs.tz(date1, userTimezone.value).isSame(
            dayjs.tz(date2, userTimezone.value),
            'day'
        )

    const isBefore = (date1, date2) =>
        dayjs.tz(date1, userTimezone.value).isBefore(
            dayjs.tz(date2, userTimezone.value)
        )

    const isAfter = (date1, date2) =>
        dayjs.tz(date1, userTimezone.value).isAfter(
            dayjs.tz(date2, userTimezone.value)
        )

    const isBetweenDates = (date, from, to, inclusive = '()') =>
        dayjs.tz(date, userTimezone.value).isBetween(
            dayjs.tz(from, userTimezone.value),
            dayjs.tz(to, userTimezone.value),
            null,
            inclusive
        )

    // --- Formatting (always in user timezone) ---

    const getTimeFormatPattern = (withSeconds = false) => {
        if (getTimeFormat() === '12h') {
            return withSeconds ? 'h:mm:ss A' : 'h:mm A'
        }
        return withSeconds ? 'HH:mm:ss' : 'HH:mm'
    }

    const format = (date, fmt = 'YYYY-MM-DD HH:mm') =>
        fromUtc(date).format(fmt)

    const formatTime = (date, options = {}) => {
        const withSeconds = options?.withSeconds === true
        return format(date, getTimeFormatPattern(withSeconds))
    }

    const formatDate = (date) => format(date, 'YYYY-MM-DD')

    const formatDateUS = (date) => format(date, 'MM/DD/YYYY')

    const formatDateDisplay = (date) => format(date, DATE_FORMAT_PATTERNS[getDateFormat()])

    const formatDateTimeDisplay = (date, options = {}) => {
        const withSeconds = options?.withSeconds === true
        return `${formatDateDisplay(date)} ${formatTime(date, { withSeconds })}`
    }

    const formatUrlDate = (date) => format(date, 'YYYY-MM-DD')

    // --- Location-local time (opt-in "location" display mode) ---
    // Every *At helper returns exactly the same string as its profile-timezone counterpart unless the user chose
    // the "location" mode AND the item carries a resolved zone.
    //
    // Labels: an item is "foreign" when its zone has a different UTC offset than the profile timezone, or it lies
    // in a different country than the profile timezone (when that country is known). Foreign items always get a
    // zone label; callers with day context (timeline cards) pass forceLabel to label every item of a mixed day.

    const resolvedZoneOf = (locationTimezone) => (
        isLocationTimeMode() && locationTimezone?.timezone ? locationTimezone.timezone : null
    )

    const isLocationTimezoneForeign = (date, locationTimezone) => {
        const zone = resolvedZoneOf(locationTimezone)
        if (!zone || !date) return false
        const instant = dayjs.utc(date)
        if (instant.tz(zone).utcOffset() !== instant.tz(userTimezone.value).utcOffset()) return true
        const homeCountry = locationTimeHomeCountry.value
        const countryCode = locationTimezone.countryCode?.trim().toUpperCase()
        return !!(homeCountry && countryCode && countryCode !== homeCountry)
    }

    /** True when any zone of a stay, trip or data gap is foreign (see above). */
    const isItemForeign = (item) => {
        if (!isLocationTimeMode() || !item) return false
        const start = item.timestamp || item.startTime
        if (!start) return false
        const end = item.endTime
            || dayjs.utc(start).add(item.stayDuration ?? item.tripDuration ?? 0, 'second').toISOString()
        return isLocationTimezoneForeign(start, item.locationTimezone)
            || isLocationTimezoneForeign(start, item.startLocationTimezone)
            || isLocationTimezoneForeign(end, item.endLocationTimezone)
    }

    /** Zone and label to display an instant with, or null to use the profile-timezone formatter unchanged. */
    const resolveDisplay = (date, locationTimezone, forceLabel = false) => {
        const zone = resolvedZoneOf(locationTimezone)
        if (!zone) return null
        const showLabel = forceLabel || isLocationTimezoneForeign(date, locationTimezone)
        if (!showLabel && zone === userTimezone.value) return null
        return { zone, label: showLabel ? getShortZoneName(dayjs.utc(date).toDate(), zone) : '' }
    }

    const formatWithDisplay = (date, display, pattern) => {
        const text = dayjs.utc(date).tz(display.zone).format(pattern)
        return display.label ? `${text} ${display.label}` : text
    }

    const formatTimeAt = (date, locationTimezone, options = {}) => {
        const display = resolveDisplay(date, locationTimezone, options?.forceLabel === true)
        if (!display) return formatTime(date, options)
        return formatWithDisplay(date, display, getTimeFormatPattern(options?.withSeconds === true))
    }

    /** Date only, in the item's zone; no zone label (pair it with formatTimeAt). */
    const formatDateDisplayAt = (date, locationTimezone) => {
        const display = resolveDisplay(date, locationTimezone)
        if (!display) return formatDateDisplay(date)
        return dayjs.utc(date).tz(display.zone).format(DATE_FORMAT_PATTERNS[getDateFormat()])
    }

    const formatDateTimeDisplayAt = (date, locationTimezone, options = {}) => {
        const display = resolveDisplay(date, locationTimezone, options?.forceLabel === true)
        if (!display) return formatDateTimeDisplay(date, options)
        const pattern = `${DATE_FORMAT_PATTERNS[getDateFormat()]} ${getTimeFormatPattern(options?.withSeconds === true)}`
        return formatWithDisplay(date, display, pattern)
    }

    /** Any pattern in the item's zone (no label), e.g. for CSV; profile timezone when there is no zone. */
    const formatInLocationZone = (date, locationTimezone, pattern) => {
        const zone = resolvedZoneOf(locationTimezone)
        return zone ? dayjs.utc(date).tz(zone).format(pattern) : format(date, pattern)
    }

    /** IANA zone an item's times are shown in: its own zone in location mode, else the profile timezone. */
    const getDisplayZoneId = (locationTimezone) => resolvedZoneOf(locationTimezone) || userTimezone.value

    /** Day of week (e.g. "Monday") in the item's zone. */
    const formatWeekdayAt = (date, locationTimezone) => {
        const display = resolveDisplay(date, locationTimezone)
        if (!display) return format(date, 'dddd')
        return dayjs.utc(date).tz(display.zone).format('dddd')
    }

    /**
     * A calendar date (`YYYY-MM-DD`, or a midnight-UTC instant standing for one) in the user's date format,
     * without timezone conversion -- converting would show the previous day west of UTC.
     */
    const formatCalendarDateDisplay = (value) => {
        if (!value) return ''
        const parsed = dayjs.utc(String(value))
        return parsed.isValid() ? parsed.format(DATE_FORMAT_PATTERNS[getDateFormat()]) : String(value)
    }

    /**
     * Hover text explaining where a location-local time came from, so users can include it in bug reports.
     * Empty in profile mode.
     */
    const getLocationTimezoneHint = (locationTimezone) => {
        if (!isLocationTimeMode()) return ''
        if (!locationTimezone) return t('timeline.localTime.notProvided')

        const details = {
            zone: locationTimezone.timezone,
            city: locationTimezone.nearestCity || '?',
            country: locationTimezone.countryCode || '?',
            distance: locationTimezone.distanceKm ?? '?'
        }
        switch (locationTimezone.status) {
            case 'RESOLVED':
                return t('timeline.localTime.resolved', details)
            case 'BEYOND_MAX_DISTANCE':
                return t('timeline.localTime.beyondMaxDistance', details)
            case 'NO_GEONAMES_DATA':
                return t('timeline.localTime.noGeonamesData')
            case 'INVALID_TIMEZONE':
                return t('timeline.localTime.invalidTimezone', details)
            default:
                return t('timeline.localTime.notProvided')
        }
    }

    /** Zone in effect when a timeline item starts (stay location, trip origin, or the place before a data gap). */
    const getItemStartLocationTimezone = (item) => item?.locationTimezone || item?.startLocationTimezone || null

    // --- Local days (location mode) ---
    // In "location" mode a day ends at midnight wherever the user is. Stays are dated in their own zone; trips and
    // data gaps by the local clock at each end (the dates the card shows), but only span two days when they pass
    // midnight on the departure or arrival clock; across the date line they otherwise stay on their departure day.
    // An item spanning several local days is split at midnight on the departure clock when that midnight falls inside
    // it, otherwise on the arrival clock.
    // In profile mode both zones are the profile timezone and the day helpers below keep their original code.

    const getItemStartZone = (item) => getDisplayZoneId(getItemStartLocationTimezone(item))

    const getItemEndZone = (item) => getDisplayZoneId(
        item?.locationTimezone || item?.endLocationTimezone || item?.startLocationTimezone
    )

    const getItemStartInstant = (item) => dayjs.utc(item.timestamp || item.startTime)

    const getItemEndInstant = (item) => (
        item.endTime
            ? dayjs.utc(item.endTime)
            : getItemStartInstant(item).add(item.stayDuration ?? item.tripDuration ?? 0, 'second')
    )

    const nextDateKey = (dateKey) => dayjs.utc(dateKey).add(1, 'day').format('YYYY-MM-DD')

    const computeLocalDaySpan = (item) => {
        const start = getItemStartInstant(item)
        const end = getItemEndInstant(item)
        const startZone = getItemStartZone(item)
        const endZone = getItemEndZone(item)
        const startDate = start.tz(startZone).format('YYYY-MM-DD')
        // An item ending exactly at local midnight does not reach the next day.
        const lastInstant = end.isAfter(start) ? end.subtract(1, 'millisecond') : start
        const endDate = lastInstant.tz(endZone).format('YYYY-MM-DD')
        if (endDate <= startDate) {
            // Arriving on an earlier calendar date (date line) keeps the item on its departure day.
            return { startDate, endDate: startDate }
        }
        // A later arrival date only counts when midnight passes on one of the two clocks during the item: flying
        // Honolulu 14:00 -> Tokyo 17:00 the next day passes neither, so the flight stays on its departure day.
        const nextDay = nextDateKey(startDate)
        const passesMidnight = [startZone, endZone].some((zone) => {
            const midnight = dayjs.tz(nextDay, zone)
            return midnight.isAfter(start) && midnight.isBefore(end)
        })
        return { startDate, endDate: passesMidnight ? endDate : startDate }
    }

    /**
     * Stores each item's local day span as `localDaySpan`, keeping dates monotonic across the sorted items: an item
     * never starts on an earlier date than the previous one ended (e.g. Tokyo -> Honolulu across the date line).
     */
    const annotateLocalDaySpans = (items) => {
        let floor = null
        for (const item of items || []) {
            const span = computeLocalDaySpan(item)
            const startDate = floor && span.startDate < floor ? floor : span.startDate
            const endDate = span.endDate < startDate ? startDate : span.endDate
            item.localDaySpan = { startDate, endDate }
            if (!floor || endDate > floor) floor = endDate
        }
        return items
    }

    /** Local dates ('YYYY-MM-DD') an item belongs to, from `annotateLocalDaySpans` when available. */
    const getItemLocalDaySpan = (item) => item?.localDaySpan || computeLocalDaySpan(item)

    /** Instant and zone at which local day `dateKey` begins, as seen from this item. */
    const getLocalDayBoundary = (item, dateKey) => {
        const start = getItemStartInstant(item)
        const end = getItemEndInstant(item)
        const span = getItemLocalDaySpan(item)
        const startZone = getItemStartZone(item)
        const endZone = getItemEndZone(item)

        if (dateKey <= span.startDate) {
            const midnight = dayjs.tz(dateKey, startZone).utc()
            return { at: midnight.isAfter(start) ? start : midnight, zone: startZone }
        }
        if (dateKey > span.endDate) {
            const midnight = dayjs.tz(dateKey, endZone).utc()
            return { at: midnight.isBefore(end) ? end : midnight, zone: endZone }
        }

        // The day starts while the item is in progress.
        const departure = dayjs.tz(dateKey, startZone).utc()
        if (departure.isAfter(start) && departure.isBefore(end)) {
            return { at: departure, zone: startZone }
        }
        const arrival = dayjs.tz(dateKey, endZone).utc()
        const clamped = arrival.isBefore(start) ? start : (arrival.isAfter(end) ? end : arrival)
        return { at: clamped, zone: endZone }
    }

    /**
     * Local day `dateKey` as seen from this item: `start` is when the day begins, `end` the last millisecond before
     * the next day (like `endOf('day')`), each with the zone its clock is read in.
     */
    const getItemDayBounds = (item, dateKey) => {
        const begin = getLocalDayBoundary(item, dateKey)
        const next = getLocalDayBoundary(item, nextDateKey(dateKey))
        return {
            start: begin.at,
            startZone: begin.zone,
            end: next.at.subtract(1, 'millisecond'),
            endZone: next.zone
        }
    }

    /**
     * UTC window covering local dates firstDate..lastDate for the given sorted, annotated items (pass every loaded item,
     * not only the shown ones: the window never reaches into an item of another date). Null when no item reaches the
     * range, so the caller keeps its profile-timezone range.
     */
    const getLocalDayWindowUtc = (items, firstDate, lastDate) => {
        const list = Array.isArray(items) ? items : []
        const firstIndex = list.findIndex((item) => getItemLocalDaySpan(item).endDate >= firstDate)
        const lastIndex = list.findLastIndex((item) => getItemLocalDaySpan(item).startDate <= lastDate)
        if (firstIndex < 0 || lastIndex < firstIndex) return null

        let start = getItemDayBounds(list[firstIndex], firstDate).start
        const previous = list[firstIndex - 1]
        if (previous && getItemEndInstant(previous).isAfter(start)) {
            // Across the date line the local midnight can come before the previous date's last item ended
            start = getItemEndInstant(previous)
        }

        let end = getItemDayBounds(list[lastIndex], lastDate).end
        const following = list[lastIndex + 1]
        if (following && getItemStartInstant(following).isBefore(end)) {
            end = getItemStartInstant(following).subtract(1, 'millisecond')
        }

        return { start: start.toISOString(), end: end.toISOString() }
    }

    /**
     * Range to request timeline data for when the page shows the profile-timezone days of [start, end]: padded in
     * location mode so items of the same local days in other zones are included; unchanged in profile mode.
     */
    const getLocalDayFetchRange = (start, end) => {
        if (!isLocationTimeMode()) return { start, end }
        return {
            start: dayjs.utc(start).subtract(LOCATION_DAY_FETCH_PADDING_HOURS, 'hour').toISOString(),
            end: dayjs.utc(end).add(LOCATION_DAY_FETCH_PADDING_HOURS, 'hour').toISOString()
        }
    }

    /** True when an item's local day span touches any of the given dates (sorted 'YYYY-MM-DD' keys). */
    const isItemInLocalDates = (item, dateKeys) => {
        if (!dateKeys?.length) return false
        const span = getItemLocalDaySpan(item)
        return span.startDate <= dateKeys[dateKeys.length - 1] && span.endDate >= dateKeys[0]
    }

    const getPrimeVueDatePickerFormat = () => DATE_FORMAT_PRIMEVUE[getDateFormat()]
    const getPrimeVueFirstDayOfWeek = () => DATE_FORMAT_FIRST_DAY_OF_WEEK[getDateFormat()]

    /**
     * The user's date pattern in display tokens (e.g. `MM/DD/YYYY`), for hints that explain typed input.
     */
    const getDateFormatPattern = () => DATE_FORMAT_PATTERNS[getDateFormat()]

    /**
     * The exact text PrimeVue accepts for a typed date range, e.g. `MM/DD/YYYY - MM/DD/YYYY`.
     */
    const getDateRangeInputPlaceholder = () => `${getDateFormatPattern()} - ${getDateFormatPattern()}`

    const formatDateLong = (date) => {
        // Handle YYYY-MM-DD strings directly in user timezone
        const dateObj = dayjs.tz(date, userTimezone.value);
        return dateObj.format('dddd, MMMM D, YYYY');
    }

    const formatDateShort = (date) => format(date, 'MMM D')

    const formatDateWithYear = (date) => format(date, 'MMM D, YYYY')

    const timeAgo = (date) => {
        const dateObj = fromUtc(date)
        const nowObj = now()

        const minutesDiff = nowObj.diff(dateObj, 'minute')
        if (minutesDiff < 0) return formatDateDisplay(date)
        if (minutesDiff < 1) return 'Just now'
        if (minutesDiff < 60) return `${minutesDiff} min ago`

        const hoursDiff = nowObj.diff(dateObj, 'hour')
        if (hoursDiff < 24) return `${hoursDiff} hours ago`

        const daysDiff = nowObj.diff(dateObj, 'day')
        if (daysDiff < 30) return `${daysDiff} days ago`

        return formatDateDisplay(date)
    }

    // --- Timeline-Specific Helpers ---

    const isOvernight = (startUtc, endUtc) => {
        const start = fromUtc(startUtc)
        const end = fromUtc(endUtc)
        return !start.isSame(end, 'day')
    }

    const isOvernightWithDuration = (startUtc, durationSeconds) => {
        const start = fromUtc(startUtc)
        const end = start.add(durationSeconds, 'second')
        return !start.isSame(end, 'day')
    }

    const shouldItemAppearOnDate = (item, dateKey) => {
        if (isLocationTimeMode()) {
            const span = getItemLocalDaySpan(item)
            return span.startDate <= dateKey && dateKey <= span.endDate
        }

        const targetDate = dayjs.tz(dateKey, userTimezone.value)
        const dayStart = targetDate.startOf('day')
        const dayEnd = targetDate.endOf('day')

        // Handle data gaps
        if (item.type === 'dataGap') {
            const gapStart = fromUtc(item.startTime)
            const gapEnd = fromUtc(item.endTime)
            return gapStart.isBefore(dayEnd) && gapEnd.isAfter(dayStart)
        }

        // Handle trips and stays
        const itemStart = fromUtc(item.timestamp)
        let itemEnd

        if (item.type === 'trip') {
            // Trip duration already in seconds
            const durationSeconds = item.tripDuration
            itemEnd = itemStart.add(durationSeconds, 'second')
        } else if (item.type === 'stay') {
            // Stay duration already in seconds
            itemEnd = itemStart.add(item.stayDuration, 'second')
        }

        // Show item on any day it overlaps with (start, middle, or end days)
        return itemStart.isBefore(dayEnd) && itemEnd.isAfter(dayStart)
    }

    const getItemDisplayType = (item, dateKey) => {
        if (isLocationTimeMode()) {
            const span = getItemLocalDaySpan(item)
            const isStart = span.startDate === dateKey
            const isEnd = span.endDate === dateKey
            if (isStart && isEnd) return 'single-day'
            if (isStart) return 'start'
            if (isEnd) return 'end'
            return 'continuation'
        }

        const targetDate = dayjs.tz(dateKey, userTimezone.value).startOf('day')

        let itemStart, itemEnd

        // Get item start and end times based on type
        if (item.type === 'dataGap') {
            itemStart = fromUtc(item.startTime).startOf('day')
            itemEnd = fromUtc(item.endTime).startOf('day')
        } else {
            itemStart = fromUtc(item.timestamp).startOf('day')
            const durationSeconds = item.type === 'trip' ? item.tripDuration : item.stayDuration
            itemEnd = fromUtc(item.timestamp).add(durationSeconds, 'second').startOf('day')
        }

        const isStartDay = itemStart.isSame(targetDate, 'day')
        const isEndDay = itemEnd.isSame(targetDate, 'day')

        if (isStartDay && isEndDay) return 'single-day'
        if (isStartDay) return 'start'
        if (isEndDay) return 'end'
        return 'continuation'
    }

    const shouldShowAsOvernight = (item, dateKey) => {
        if (isLocationTimeMode()) {
            const span = getItemLocalDaySpan(item)
            return span.startDate !== span.endDate
        }

        // First check if item is truly overnight
        let durationSeconds = 0
        if (item.type === 'trip') {
            durationSeconds = item.tripDuration // already in seconds
        } else if (item.type === 'stay') {
            durationSeconds = item.stayDuration // already in seconds
        } else if (item.type === 'dataGap') {
            return isOvernight(item.startTime, item.endTime)
        }

        // Show as overnight if the item duration spans multiple days in user's timezone
        return isOvernightWithDuration(item.timestamp, durationSeconds)
    }

    // --- Duration Formatting (moved to calculationsHelpers.js) ---

    const formatOnThisDayDuration = (item, currentDateString, itemType) => {
        if (isLocationTimeMode()) {
            const itemStart = getItemStartInstant(item)
            const itemEnd = getItemEndInstant(item)
            const bounds = getItemDayBounds(item, currentDateString)
            const startsToday = !itemStart.isBefore(bounds.start)
            const endsToday = !itemEnd.isAfter(bounds.end)
            const thisDayStart = startsToday
                ? itemStart.tz(getItemStartZone(item))
                : bounds.start.tz(bounds.startZone)
            const thisDayEnd = endsToday
                ? itemEnd.tz(getItemEndZone(item))
                : bounds.end.tz(bounds.endZone)

            const timePattern = getTimeFormatPattern(false)
            const durationFormatted = formatDurationCompact(Math.floor(thisDayEnd.diff(thisDayStart) / 1000))
            return `${thisDayStart.format(timePattern)} - ${thisDayEnd.format(timePattern)} (${durationFormatted})`
        }

        const currentDate = dayjs.tz(currentDateString, userTimezone.value)

        // Get item start and end times
        let itemStart, itemEnd

        if (itemType === 'stay') {
            itemStart = fromUtc(item.timestamp)
            itemEnd = item.endTime
                ? fromUtc(item.endTime)
                : itemStart.add(item.stayDuration, 'second')
        } else if (itemType === 'trip') {
            itemStart = fromUtc(item.timestamp)
            itemEnd = itemStart.add(item.tripDuration, 'second') // already in seconds
        } else if (itemType === 'dataGap') {
            itemStart = fromUtc(item.startTime)
            itemEnd = fromUtc(item.endTime)
        }

        // Get day boundaries
        const dayStart = currentDate.startOf('day')
        const dayEnd = currentDate.endOf('day')

        // Calculate actual start/end for this day
        const thisDayStart = itemStart.isBefore(dayStart) ? dayStart : itemStart
        const thisDayEnd = itemEnd.isAfter(dayEnd) ? dayEnd : itemEnd

        // Format time range and duration
        const timePattern = getTimeFormatPattern(false)
        const startTimeStr = thisDayStart.format(timePattern)
        const endTimeStr = thisDayEnd.format(timePattern)
        const durationMs = thisDayEnd.diff(thisDayStart)
        const durationFormatted = formatDurationCompact(Math.floor(durationMs / 1000))

        return `${startTimeStr} - ${endTimeStr} (${durationFormatted})`
    }

    // --- Date Range Helpers ---

    const getDateRangeArray = (startDate, endDate) => {
        const dates = []
        let current = fromUtc(startDate).startOf('day')
        const end = fromUtc(endDate).startOf('day')

        while (current.isSameOrBefore(end)) {
            const dateStr = current.format('YYYY-MM-DD')
            dates.push(dateStr)
            current = current.add(1, 'day')
        }

        return dates
    }

    // --- Group By Operations ---

    const groupByDay = (events) => {
        const groups = {}
        events.forEach(event => {
            const startTime = event.timestamp || event.startTime
            const dayKey = fromUtc(startTime).format('YYYY-MM-DD')
            if (!groups[dayKey]) groups[dayKey] = []
            groups[dayKey].push(event)
        })
        return groups
    }

    // --- Smart Duration Helpers ---

    const getTotalDaysSpanned = (item) => {
        if (isLocationTimeMode()) {
            const span = getItemLocalDaySpan(item)
            return dayjs.utc(span.endDate).diff(dayjs.utc(span.startDate), 'day') + 1
        }

        let itemStart, itemEnd

        if (item.type === 'dataGap') {
            itemStart = fromUtc(item.startTime).startOf('day')
            itemEnd = fromUtc(item.endTime).startOf('day')
        } else {
            itemStart = fromUtc(item.timestamp).startOf('day')
            const durationSeconds = item.type === 'trip' ? item.tripDuration : item.stayDuration
            itemEnd = fromUtc(item.timestamp).add(durationSeconds, 'second').startOf('day')
        }

        return itemEnd.diff(itemStart, 'day') + 1
    }

    const getDayNumber = (item, dateKey) => {
        if (isLocationTimeMode()) {
            return dayjs.utc(dateKey).diff(dayjs.utc(getItemLocalDaySpan(item).startDate), 'day') + 1
        }

        const targetDate = dayjs.tz(dateKey, userTimezone.value).startOf('day')

        let itemStart
        if (item.type === 'dataGap') {
            itemStart = fromUtc(item.startTime).startOf('day')
        } else {
            itemStart = fromUtc(item.timestamp).startOf('day')
        }

        return targetDate.diff(itemStart, 'day') + 1
    }


    // --- Validation ---

    const isValidDate = (date) => {
        if (!date) return false
        return dayjs(date).isValid()
    }

    const isValidDateRange = (dateRange) => {
        if (!Array.isArray(dateRange) || dateRange.length !== 2) return false
        const [start, end] = dateRange
        return isValidDate(start) && isValidDate(end) &&
            dayjs(start).isSameOrBefore(dayjs(end))
    }

    // --- Today Helpers ---

    const getTodayRangeUtc = () => {
        const today = now()
        return {
            start: today.startOf('day').utc().toISOString(),
            end: today.endOf('day').utc().toISOString()
        }
    }

    const getYesterdayRangeUtc = () => {
        const yesterday = now().subtract(1, 'day')
        return {
            start: yesterday.startOf('day').utc().toISOString(),
            end: yesterday.endOf('day').utc().toISOString()
        }
    }

    const getLastWeekRange = () => {
        const nowObj = now()
        const start = nowObj.subtract(7, 'day').startOf('day').utc().toISOString()
        const end = nowObj.endOf('day').utc().toISOString()
        return {start, end}
    }

    const getLastMonthRange = () => {
        const nowObj = now()
        const start = nowObj.subtract(30, 'day').startOf('day').utc().toISOString()
        const end = nowObj.endOf('day').utc().toISOString()
        return {start, end}
    }

    // --- Overnight Timeline Helpers ---

    const getOvernightTimestampText = (item, currentDate, options = {}) => {
        const utcTimestamp = item.timestamp || item.startTime
        // Location mode matches days by the item's local dates; profile mode by the profile timezone.
        const itemStartDate = isLocationTimeMode()
            ? getItemLocalDaySpan(item).startDate
            : fromUtc(utcTimestamp).format('YYYY-MM-DD')
        const isStartDay = itemStartDate === currentDate

        const display = resolveDisplay(utcTimestamp, getItemStartLocationTimezone(item), options?.forceLabel === true)

        if (isStartDay) {
            // Show actual start time on start day
            const pattern = `${DATE_FORMAT_PATTERNS[getDateFormat()]}, ${getTimeFormatPattern(false)}`
            return display ? formatWithDisplay(utcTimestamp, display, pattern) : format(utcTimestamp, pattern)
        } else {
            // Show "Continued from" on other days
            const pattern = `MMM D, ${getTimeFormatPattern(false)}`
            const time = display
                ? formatWithDisplay(utcTimestamp, display, pattern)
                : fromUtc(utcTimestamp).format(pattern)
            return t('timeline.overnight.continuedFrom', { time })
        }
    }

    const getOvernightOnThisDayText = (item, currentDate) => {
        // Determine item type for the existing formatOnThisDayDuration function
        let itemType = 'stay'
        if (item.distanceMeters !== undefined || item.tripDuration !== undefined) {
            itemType = 'trip'
        } else if (item.startTime && item.endTime) {
            itemType = 'dataGap'
        }

        return formatOnThisDayDuration(item, currentDate, itemType)
    }

    return {
        // Core
        userTimezone,
        userDateFormat,
        userTimeFormat,
        userTimeDisplayMode,
        locationTimeHomeCountry,
        setTimezone,
        getTimezone,
        setDateFormat,
        getDateFormat,
        setTimeFormat,
        getTimeFormat,
        setTimeDisplayMode,
        getTimeDisplayMode,
        isLocationTimeMode,
        setLocationTimeHomeCountry,
        withLocationTimezoneParams,
        now,
        fromUtc,
        toUtc,
        create,
        createFromCalendar,

        // Boundaries (UTC for API)
        startOfDayUtc,
        endOfDayUtc,
        startOfWeekUtc,
        endOfWeekUtc,
        startOfMonthUtc,
        endOfMonthUtc,

        // Date Ranges
        createDateRangeUtc,
        createDateRangeFromPicker,
        createDateTimeFromPicker,
        createDateTimeRangeFromPicker,
        parseUrlDate,
        convertUtcRangeToCalendarDates,
        getDateRangeArray,
        getTodayRangeUtc,
        getYesterdayRangeUtc,
        getLastWeekRange,
        getLastMonthRange,

        // Math
        add,
        subtract,
        diff,
        diffInDays,
        isSameDay,
        isBefore,
        isAfter,
        isBetweenDates,

        // Formatting
        format,
        formatDateInTimezone: format,
        formatTime,
        getTimeFormatPattern,
        formatDate,
        formatDateUS,
        formatDateDisplay,
        formatDateTimeDisplay,
        formatDateLong,
        formatDateShort,
        formatDateWithYear,
        formatUrlDate,
        getPrimeVueDatePickerFormat,
        getPrimeVueFirstDayOfWeek,
        getDateFormatPattern,
        getDateRangeInputPlaceholder,
        timeAgo,

        // Location-local time
        isLocationTimezoneForeign,
        isItemForeign,
        formatTimeAt,
        formatDateDisplayAt,
        formatDateTimeDisplayAt,
        formatWeekdayAt,
        formatInLocationZone,
        getDisplayZoneId,
        formatCalendarDateDisplay,
        getLocationTimezoneHint,
        getItemStartLocationTimezone,

        // Local days (location mode)
        annotateLocalDaySpans,
        getItemLocalDaySpan,
        getItemDayBounds,
        getLocalDayWindowUtc,
        getLocalDayFetchRange,
        isItemInLocalDates,

        // Timeline Specific
        isOvernight,
        isOvernightWithDuration,
        shouldItemAppearOnDate,
        getItemDisplayType,
        shouldShowAsOvernight,
        formatOnThisDayDuration,
        getOvernightTimestampText,
        getOvernightOnThisDayText,

        // Smart Duration Helpers
        getTotalDaysSpanned,
        getDayNumber,

        // Grouping
        groupByDay,

        // Validation
        isValidDate,
        isValidDateRange,
        isValidDataRange: isValidDateRange
    }
}
