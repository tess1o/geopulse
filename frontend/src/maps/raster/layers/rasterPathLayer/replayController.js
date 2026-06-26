import L from 'leaflet'
import { getTripMovementIconClass } from '@/utils/timelineIconUtils'

export const createRasterPathReplayController = ({
  getMap,
  getLayerHost,
  getHighlightedTrip,
  isVisible,
  onReplayPlaying
}) => {
  const state = {
    replayMarker: null
  }

  const getActiveMap = () => getMap?.() || null
  const getActiveLayerHost = () => getLayerHost?.() || null

  const clearReplayMarker = () => {
    if (!state.replayMarker) {
      return
    }

    getActiveLayerHost()?.removeFromLayer?.(state.replayMarker)
    state.replayMarker = null
  }

  const syncReplayMarker = ({ replayState } = {}) => {
    clearReplayMarker()

    const map = getActiveMap()
    const layerHost = getActiveLayerHost()
    const cursor = replayState?.cursor
    if (!isVisible?.() || !replayState?.enabled || !cursor || !layerHost?.isReady) {
      return
    }

    if (replayState?.playing) {
      onReplayPlaying?.()
    }

    const latitude = Number(cursor.latitude)
    const longitude = Number(cursor.longitude)
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      return
    }

    const highlightedTrip = getHighlightedTrip?.()
    const iconClass = getTripMovementIconClass(
      replayState?.movementType || highlightedTrip?.movementType || 'UNKNOWN'
    )
    const marker = L.marker([latitude, longitude], {
      interactive: false,
      zIndexOffset: 4300,
      icon: L.divIcon({
        className: 'gp-trip-replay-marker',
        html: `<div class="gp-trip-replay-marker-inner"><i class="${iconClass}"></i></div>`,
        iconSize: [38, 38],
        iconAnchor: [19, 19]
      })
    })

    state.replayMarker = marker
    layerHost.addToLayer?.(marker)

    if (replayState?.playing && replayState?.followCamera) {
      map?.panTo?.([latitude, longitude], { animate: false })
    }
  }

  const cleanupReplay = () => {
    clearReplayMarker()
  }

  return {
    clearReplayMarker,
    cleanupReplay,
    syncReplayMarker
  }
}
