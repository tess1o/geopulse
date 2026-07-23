import { escapeHtml } from '@/maps/shared/popupContentBuilders'

const toFiniteNumber = (value) => {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

const getCoordinateKey = (latitude, longitude) => (
  `${latitude.toFixed(6)}|${longitude.toFixed(6)}`
)

export const getRenderableNotes = (notes) => (
  Array.isArray(notes)
    ? notes
      .map((note) => ({
        ...note,
        latitude: toFiniteNumber(note.latitude),
        longitude: toFiniteNumber(note.longitude)
      }))
      .filter((note) => note.latitude !== null && note.longitude !== null)
    : []
)

export const groupNotesByCoordinate = (notes) => {
  const grouped = new Map()

  getRenderableNotes(notes).forEach((note) => {
    const key = getCoordinateKey(note.latitude, note.longitude)
    if (!grouped.has(key)) {
      grouped.set(key, {
        latitude: note.latitude,
        longitude: note.longitude,
        notes: []
      })
    }
    grouped.get(key).notes.push(note)
  })

  return Array.from(grouped.values())
}

export const getNoteIdentityKey = (note) => (
  `${note?.source || 'note'}-${note?.id || note?.externalId || note?.eventTime || note?.createdAt || ''}`
)

const getNoteTimestamp = (note) => note.eventTime || note.createdAt || note.updatedAt || null

const formatNoteTime = (note, timezone) => {
  const timestamp = getNoteTimestamp(note)
  if (!timestamp) return ''
  return `${timezone.formatDateDisplay(timestamp)} ${timezone.formatTime(timestamp)}`
}

const getNoteSourceLabel = (note) => {
  if (note.source === 'MEMOS') return 'Memos'
  return 'GeoPulse'
}

const normalizeText = (value) => String(value || '')
  .replace(/[#*_`>\[\]()]/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

const getNoteSnippet = (note) => note.snippet || note.contentMarkdown || ''

const getNoteTitle = (note) => {
  const fallback = note.source === 'MEMOS' ? 'Memos note' : 'GeoPulse note'
  const title = String(note.title || '').trim()
  if (!title) {
    return fallback
  }

  if (note.source === 'MEMOS') {
    const normalizedTitle = normalizeText(title)
    const normalizedSnippet = normalizeText(getNoteSnippet(note))
    if (!normalizedTitle || normalizedTitle === normalizedSnippet || normalizedSnippet.startsWith(normalizedTitle)) {
      return fallback
    }
  }

  return title
}

const buildSingleNoteHtml = (note, timezone) => {
  const title = getNoteTitle(note)
  const timeText = formatNoteTime(note, timezone)
  const snippet = getNoteSnippet(note)
  const externalLink = note.externalUrl
    ? `<a class="gp-note-popup-link" href="${escapeHtml(note.externalUrl)}" target="_blank" rel="noopener noreferrer">Open in Memos</a>`
    : ''

  return `
    <div class="gp-note-popup-list-item">
      <div class="gp-note-popup-title">${escapeHtml(title)}</div>
      <div class="gp-note-popup-meta">
        <span>${escapeHtml(getNoteSourceLabel(note))}</span>
        ${timeText ? `<span>${escapeHtml(timeText)}</span>` : ''}
      </div>
      ${snippet ? `<div class="gp-note-popup-snippet">${escapeHtml(snippet)}</div>` : ''}
      ${externalLink}
    </div>
  `.trim()
}

export const buildNotePopupHtml = (notes, timezone) => {
  const safeNotes = Array.isArray(notes) ? notes : []
  if (safeNotes.length === 0) {
    return '<div class="gp-note-popup">No notes</div>'
  }

  if (safeNotes.length === 1) {
    return `<div class="gp-note-popup">${buildSingleNoteHtml(safeNotes[0], timezone)}</div>`
  }

  const noteItems = safeNotes
    .slice()
    .sort((left, right) => Date.parse(getNoteTimestamp(right) || 0) - Date.parse(getNoteTimestamp(left) || 0))
    .map((note) => buildSingleNoteHtml(note, timezone))
    .join('')

  return `
    <div class="gp-note-popup">
      <div class="gp-note-popup-title">${safeNotes.length} notes here</div>
      <div class="gp-note-popup-list">${noteItems}</div>
    </div>
  `.trim()
}

export const createNoteMarkerHtml = (count) => {
  const isStack = count > 1
  return `
    <div class="gp-note-marker ${isStack ? 'gp-note-marker--stack' : ''}">
      <i class="pi pi-file-edit"></i>
      ${isStack ? `<span class="gp-note-marker-count">${count}</span>` : ''}
    </div>
  `.trim()
}
