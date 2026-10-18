// Turns the explanation the timeline engine stores with each stay, trip and data gap ({ start, notes, end }, each a
// reason { code, params }) into sentences for the timeline cards.

const SECTIONS = ['start', 'notes', 'end']

// Reasons whose sentence depends on a flag rather than a value
const variantCode = (reason) => {
  if (reason.code === 'WATER_TRANSITION') {
    return reason.params?.toWater ? 'WATER_TRANSITION_TO_WATER' : 'WATER_TRANSITION_TO_LAND'
  }
  return reason.code
}

// Seconds, meters and instants become readable text; other values are shown as they are
const formatParams = (params = {}, { formatDuration, formatDistance, formatTime }) => {
  const formatted = {}
  for (const [name, value] of Object.entries(params)) {
    if (value === null || value === undefined) {
      formatted[name] = ''
    } else if (name.endsWith('Seconds')) {
      formatted[name] = formatDuration(Number(value))
    } else if (name.endsWith('Meters')) {
      formatted[name] = formatDistance(Number(value))
    } else if (name === 'start') {
      formatted[name] = formatTime(value)
    } else {
      formatted[name] = value
    }
  }
  return formatted
}

/**
 * The explanation as sentences, start first and end last. A reason without a translation (a code newer than the
 * frontend) is left out rather than shown as a raw code.
 */
export const explanationLines = (explanation, { t, te, formatDuration, formatDistance, formatTime }) => {
  if (!explanation) return []
  const lines = []
  for (const section of SECTIONS) {
    const reasons = section === 'notes' ? (explanation.notes || []) : [explanation[section]].filter(Boolean)
    for (const reason of reasons) {
      const key = `timeline.explanation.${section === 'notes' ? 'note' : section}.${variantCode(reason)}`
      if (!te(key)) continue
      lines.push(t(key, formatParams(reason.params, { formatDuration, formatDistance, formatTime })))
    }
  }
  return lines
}
