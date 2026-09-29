import { groupItemsByProximity } from './nearbyPointGrouping'

// Violet keeps notes distinct from the teal Stay/Trip stack markers.
export const NOTE_MARKER_COLOR = '#7c3aed'

const toFiniteNumber = (value) => {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

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

export const groupNotesByCoordinate = (notes) => (
  groupItemsByProximity(getRenderableNotes(notes)).map((group) => ({
    latitude: group.latitude,
    longitude: group.longitude,
    notes: group.items
  }))
)

export const getNoteIdentityKey = (note) => (
  `${note?.source || 'note'}-${note?.id || note?.externalId || note?.eventTime || note?.createdAt || ''}`
)

export const createNoteMarkerHtml = (count) => {
  const isStack = count > 1
  return `
    <div class="gp-note-marker ${isStack ? 'gp-note-marker--stack' : ''}">
      <i class="pi pi-file-edit"></i>
      ${isStack ? `<span class="gp-note-marker-count">${count}</span>` : ''}
    </div>
  `.trim()
}
