const DAY_ORDER = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

const MONTH_ORDER = {
  January: 0, Jan: 0,
  February: 1, Feb: 1,
  March: 2, Mar: 2,
  April: 3, Apr: 3,
  May: 4,
  June: 5, Jun: 5,
  July: 6, Jul: 6,
  August: 7, Aug: 7,
  September: 8, Sep: 8, Sept: 8,
  October: 9, Oct: 9,
  November: 10, Nov: 10,
  December: 11, Dec: 11
}

const parseSortKeyTimestamp = (sortKey) => {
  if (typeof sortKey !== 'string' || sortKey.trim() === '') return null

  // Date-only keys are interpreted as UTC midnight to avoid local TZ drift.
  if (/^\d{4}-\d{2}-\d{2}$/.test(sortKey)) {
    const timestamp = Date.parse(`${sortKey}T00:00:00Z`)
    return Number.isNaN(timestamp) ? null : timestamp
  }

  const timestamp = Date.parse(sortKey)
  return Number.isNaN(timestamp) ? null : timestamp
}

export const getChartPointKey = (chart, index) => {
  const sortKey = chart?.sortKeys?.[index]
  const label = chart?.labels?.[index]
  return typeof sortKey === 'string' && sortKey.trim() !== ''
    ? sortKey
    : String(label ?? '')
}

export const buildMergedChartAxis = (chartsByType, { viewMode } = {}) => {
  const axisPoints = []
  const seenKeys = new Set()

  Object.values(chartsByType || {}).forEach((chart) => {
    const labels = chart?.labels || []

    labels.forEach((label, index) => {
      const key = getChartPointKey(chart, index)
      if (seenKeys.has(key)) return
      seenKeys.add(key)

      axisPoints.push({
        key,
        label: String(label ?? ''),
        sortKey: chart?.sortKeys?.[index]
      })
    })
  })

  if (axisPoints.length === 0) {
    return { keys: [], labels: [] }
  }

  const allHaveSortableDateKey = axisPoints.every(point => parseSortKeyTimestamp(point.sortKey) !== null)
  if (allHaveSortableDateKey) {
    axisPoints.sort((a, b) => parseSortKeyTimestamp(a.sortKey) - parseSortKeyTimestamp(b.sortKey))
  } else if (viewMode === 'yearly') {
    axisPoints.sort((a, b) => (MONTH_ORDER[a.label] ?? 999) - (MONTH_ORDER[b.label] ?? 999))
  } else {
    const areAllDays = axisPoints.every(point => DAY_ORDER.includes(point.label.toUpperCase()))
    const areAllMonthDays = axisPoints.every(point => /^\d{1,2}[/-]\d{1,2}$/.test(point.label))

    if (areAllDays) {
      axisPoints.sort((a, b) => DAY_ORDER.indexOf(a.label.toUpperCase()) - DAY_ORDER.indexOf(b.label.toUpperCase()))
    } else if (areAllMonthDays) {
      // Legacy fallback when only labels are available.
      axisPoints.sort((a, b) => {
        const [aMonth, aDay] = a.label.split(/[/-]/).map(Number)
        const [bMonth, bDay] = b.label.split(/[/-]/).map(Number)
        return aMonth !== bMonth ? aMonth - bMonth : aDay - bDay
      })
    }
  }

  return {
    keys: axisPoints.map(point => point.key),
    labels: axisPoints.map(point => point.label)
  }
}
