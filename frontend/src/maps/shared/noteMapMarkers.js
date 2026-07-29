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

export const createNoteMarkerHtml = (count) => {
  const isStack = count > 1
  return `
    <div class="gp-note-marker ${isStack ? 'gp-note-marker--stack' : ''}">
      <i class="pi pi-file-edit"></i>
      ${isStack ? `<span class="gp-note-marker-count">${count}</span>` : ''}
    </div>
  `.trim()
}
