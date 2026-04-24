import { ref } from 'vue'
import { hasValidCoordinates } from '@/maps/tripReconstruction/shared/tripReconstructionMapData'

const normalizePositiveId = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : null
}

const normalizeLocationName = (value) => {
  if (typeof value !== 'string') {
    return ''
  }
  return value.trim()
}

const registerRenameOperation = (operationMap, sourceId, nextName, sourceLabel) => {
  const existingName = operationMap.get(sourceId)
  if (existingName && existingName !== nextName) {
    throw new Error(`Conflicting names for ${sourceLabel} ${sourceId}. Use one name per source.`)
  }
  operationMap.set(sourceId, nextName)
}

export function useTripReconstructionLocationSync({ segments, tripsStore, favoritesStore, geocodingStore }) {
  const stayNameRequestToken = ref(0)

  const clearStayLocationResolution = (segment) => {
    if (!segment || segment.segmentType !== 'STAY') return
    segment.locationName = ''
    segment.locationSourceType = null
    segment.locationFavoriteId = null
    segment.locationFavoriteType = null
    segment.locationGeocodingId = null
    segment.locationResolvedName = null
    segment.locationNameEdited = false
  }

  const locationSourceLabel = (segment) => {
    if (!segment?.locationSourceType) {
      return 'Unknown'
    }

    switch (segment.locationSourceType) {
      case 'favorite-point':
        return 'Favorite point'
      case 'favorite-area':
        return 'Favorite area'
      case 'geocoding':
        return 'Reverse geocoding'
      case 'external-search':
        return 'Search result'
      case 'coordinates':
        return 'Coordinates'
      default:
        return segment.locationSourceType
    }
  }

  const resolveStayLocationName = async (segmentIndex, latitude, longitude) => {
    const segment = segments.value[segmentIndex]
    if (!segment || segment.segmentType !== 'STAY') return
    if (!hasValidCoordinates(latitude, longitude)) return

    const requestToken = ++stayNameRequestToken.value

    try {
      const suggestion = await tripsStore.getPlanSuggestion(latitude, longitude)
      if (requestToken !== stayNameRequestToken.value) {
        return
      }

      const title = suggestion?.title?.trim()
      if (!title) {
        return
      }

      const latestSegment = segments.value[segmentIndex]
      if (!latestSegment || latestSegment.segmentType !== 'STAY') {
        return
      }

      const samePoint = Math.abs(latestSegment.latitude - latitude) < 0.00001
        && Math.abs(latestSegment.longitude - longitude) < 0.00001

      if (!samePoint) {
        return
      }

      if (!latestSegment.locationNameEdited) {
        latestSegment.locationName = title
      }
      latestSegment.locationSourceType = suggestion?.sourceType || 'coordinates'
      latestSegment.locationFavoriteId = normalizePositiveId(suggestion?.favoriteId)
      latestSegment.locationFavoriteType = suggestion?.favoriteType || null
      latestSegment.locationGeocodingId = normalizePositiveId(suggestion?.geocodingId)
      latestSegment.locationResolvedName = title
    } catch {
      // Non-blocking. Segment can still be committed with coordinates.
    }
  }

  const applyFavoriteNameToSegments = (favoriteId, nextName) => {
    const normalizedId = normalizePositiveId(favoriteId)
    const normalizedName = normalizeLocationName(nextName)
    if (normalizedId === null || normalizedName.length === 0) {
      return
    }

    segments.value.forEach((segment) => {
      if (segment.segmentType !== 'STAY') return
      if (normalizePositiveId(segment.locationFavoriteId) !== normalizedId) return

      segment.locationName = normalizedName
      segment.locationResolvedName = normalizedName
      segment.locationNameEdited = false
    })
  }

  const applyGeocodingNameToSegments = (geocodingId, nextName) => {
    const normalizedId = normalizePositiveId(geocodingId)
    const normalizedName = normalizeLocationName(nextName)
    if (normalizedId === null || normalizedName.length === 0) {
      return
    }

    segments.value.forEach((segment) => {
      if (segment.segmentType !== 'STAY') return
      if (normalizePositiveId(segment.locationGeocodingId) !== normalizedId) return

      segment.locationName = normalizedName
      segment.locationResolvedName = normalizedName
      segment.locationNameEdited = false
    })
  }

  const getFavoriteById = (favoriteId) => {
    const points = favoritesStore.favoritePlaces?.points || []
    const areas = favoritesStore.favoritePlaces?.areas || []
    return [...points, ...areas].find((favorite) => Number(favorite.id) === Number(favoriteId)) || null
  }

  const collectSourceRenameOperations = () => {
    const favoriteRenames = new Map()
    const geocodingRenames = new Map()

    segments.value.forEach((segment) => {
      if (segment?.segmentType !== 'STAY') {
        return
      }

      const nextName = normalizeLocationName(segment.locationName)
      if (!nextName || !segment.locationNameEdited) {
        return
      }

      const resolvedName = normalizeLocationName(segment.locationResolvedName)
      if (resolvedName && resolvedName === nextName) {
        return
      }

      const favoriteId = normalizePositiveId(segment.locationFavoriteId)
      if (favoriteId !== null) {
        registerRenameOperation(favoriteRenames, favoriteId, nextName, 'favorite')
        return
      }

      const geocodingId = normalizePositiveId(segment.locationGeocodingId)
      if (geocodingId !== null) {
        registerRenameOperation(geocodingRenames, geocodingId, nextName, 'geocoding')
      }
    })

    return { favoriteRenames, geocodingRenames }
  }

  const syncStayLocationNamesToSources = async () => {
    const { favoriteRenames, geocodingRenames } = collectSourceRenameOperations()
    if (favoriteRenames.size === 0 && geocodingRenames.size === 0) {
      return
    }

    let favoritesReloaded = false

    for (const [favoriteId, nextName] of favoriteRenames.entries()) {
      let favorite = getFavoriteById(favoriteId)
      if (!favorite && !favoritesReloaded) {
        await favoritesStore.fetchFavoritePlaces()
        favoritesReloaded = true
        favorite = getFavoriteById(favoriteId)
      }

      if (!favorite) {
        throw new Error(`Favorite ${favoriteId} not found for rename.`)
      }

      const bounds = favorite.type === 'AREA'
        ? {
          northEastLat: favorite.northEastLat,
          northEastLon: favorite.northEastLon,
          southWestLat: favorite.southWestLat,
          southWestLon: favorite.southWestLon
        }
        : null

      await favoritesStore.editFavorite(
        favorite.id,
        nextName,
        favorite.city,
        favorite.country,
        bounds
      )

      applyFavoriteNameToSegments(favoriteId, nextName)
    }

    for (const [geocodingId, nextName] of geocodingRenames.entries()) {
      const geocoding = await geocodingStore.getGeocodingResult(geocodingId)
      if (!geocoding?.id) {
        throw new Error(`Geocoding ${geocodingId} not found for rename.`)
      }

      const updated = await geocodingStore.updateGeocodingResult(geocodingId, {
        displayName: nextName,
        city: geocoding.city ?? null,
        country: geocoding.country ?? null
      })

      applyGeocodingNameToSegments(geocodingId, updated?.displayName || nextName)
    }
  }

  const resetLocationResolutionState = () => {
    stayNameRequestToken.value = 0
  }

  return {
    clearStayLocationResolution,
    locationSourceLabel,
    resolveStayLocationName,
    syncStayLocationNamesToSources,
    resetLocationResolutionState
  }
}
