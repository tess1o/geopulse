const toFiniteCoordinate = (value) => {
  const numericValue = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

export const buildGoogleMapsUrl = (latitude, longitude) => {
  const lat = toFiniteCoordinate(latitude)
  const lon = toFiniteCoordinate(longitude)

  if (lat === null || lon === null) {
    return null
  }

  return `https://www.google.com/maps?q=${lat},${lon}`
}
