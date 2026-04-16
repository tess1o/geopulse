import maplibregl from 'maplibre-gl'
import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'
import { buildTripHoverTooltipHtml } from '@/maps/shared/popupContentBuilders'
import {
  buildTripHoverContext as buildSharedTripHoverContext,
  projectTripHoverContext,
  resolveTripHoverTiming as resolveSharedTripHoverTiming
} from '@/maps/shared/tripHoverMath'

export const createVectorPathHoverController = ({
  getMap,
  formatDateTimeDisplay
}) => {
  const state = {
    highlightedHoverPopup: null,
    highlightedHoverContext: null,
    highlightedHoverMapHandler: null,
    highlightedHoverBoundMap: null
  }

  const getActiveMap = () => {
    const map = getMap?.()
    return isMapLibreMap(map) ? map : null
  }

  const refreshTripHoverContextProjection = (context, map) => {
    if (!context || !isMapLibreMap(map)) {
      return
    }

    projectTripHoverContext(context, {
      project: (point) => map.project([point.longitude, point.latitude])
    })
  }

  const resolveTripHoverTiming = (context, lngLat, map) => {
    if (!context || !lngLat || !isMapLibreMap(map) || !context.projectedPoints.length) {
      return null
    }

    const resolved = resolveSharedTripHoverTiming(context, lngLat, {
      toProjectedPoint: (cursorLngLat) => map.project([cursorLngLat.lng, cursorLngLat.lat]),
      unproject: ({ x, y }) => {
        const lngLatValue = map.unproject([x, y])
        return {
          latitude: lngLatValue.lat,
          longitude: lngLatValue.lng
        }
      }
    })

    if (!resolved) {
      return null
    }

    return {
      ...resolved,
      snappedLngLat: {
        lng: resolved.snappedPoint.longitude,
        lat: resolved.snappedPoint.latitude
      }
    }
  }

  const buildTripHoverTooltipContent = (trip, hoverTiming) => (
    buildTripHoverTooltipHtml(trip, hoverTiming, { formatDateTimeDisplay })
  )

  const showTripHoverTooltip = (trip, hoverTiming) => {
    const map = getActiveMap()
    if (!map || !hoverTiming) {
      return
    }

    if (!state.highlightedHoverPopup) {
      state.highlightedHoverPopup = new maplibregl.Popup({
        closeButton: false,
        closeOnClick: false,
        closeOnMove: false,
        offset: 14,
        className: 'trip-hover-tooltip-container'
      })
    }

    state.highlightedHoverPopup
      .setLngLat([hoverTiming.snappedLngLat.lng, hoverTiming.snappedLngLat.lat])
      .setHTML(buildTripHoverTooltipContent(trip, hoverTiming))
      .addTo(map)
  }

  const hideTripHoverTooltip = () => {
    if (state.highlightedHoverPopup) {
      state.highlightedHoverPopup.remove()
      state.highlightedHoverPopup = null
    }
  }

  const clearTripHoverState = () => {
    hideTripHoverTooltip()

    if (state.highlightedHoverMapHandler && isMapLibreMap(state.highlightedHoverBoundMap)) {
      state.highlightedHoverBoundMap.off('zoom', state.highlightedHoverMapHandler)
      state.highlightedHoverBoundMap.off('move', state.highlightedHoverMapHandler)
    }

    state.highlightedHoverMapHandler = null
    state.highlightedHoverContext = null
    state.highlightedHoverBoundMap = null
  }

  const syncTripHoverContext = (trip, tripPathPoints) => {
    clearTripHoverState()

    const map = getActiveMap()
    if (!map || !trip || trip.type !== 'trip') {
      return
    }

    state.highlightedHoverContext = buildSharedTripHoverContext(tripPathPoints)
    if (!state.highlightedHoverContext) {
      return
    }

    refreshTripHoverContextProjection(state.highlightedHoverContext, map)
    state.highlightedHoverMapHandler = () => {
      if (state.highlightedHoverContext && isMapLibreMap(map)) {
        refreshTripHoverContextProjection(state.highlightedHoverContext, map)
      }
    }
    state.highlightedHoverBoundMap = map
    map.on('zoom', state.highlightedHoverMapHandler)
    map.on('move', state.highlightedHoverMapHandler)
  }

  const handleHighlightedLineMouseMove = ({
    event,
    trip,
    onBeforeTooltipUpdate
  }) => {
    if (typeof onBeforeTooltipUpdate === 'function') {
      onBeforeTooltipUpdate()
    }

    const map = getActiveMap()
    if (!state.highlightedHoverContext || !trip || !map) {
      hideTripHoverTooltip()
      return
    }

    const hoverTiming = resolveTripHoverTiming(state.highlightedHoverContext, event?.lngLat, map)
    if (!hoverTiming) {
      hideTripHoverTooltip()
      return
    }

    showTripHoverTooltip(trip, hoverTiming)
  }

  return {
    clearTripHoverState,
    hideTripHoverTooltip,
    handleHighlightedLineMouseMove,
    syncTripHoverContext
  }
}
