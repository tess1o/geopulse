<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import PanoramaxPreviewPopup from '@/maps/shared/popups/PanoramaxPreviewPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildPanoramaxCoveragePopupModel } from '@/maps/shared/popups/timelinePopupModels'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH
} from '@/maps/shared/popups/mapPopupOptions'
import {
  hasMapLibreSource,
  isMapLibreMap,
  nextLayerToken,
  removeLayers
} from '@/maps/vector/utils/maplibreLayerUtils'

const props = defineProps({
  map: { type: Object, required: true },
  visible: { type: Boolean, default: false },
  endpoint: { type: String, required: true }
})
const emit = defineEmits(['select'])

const token = nextLayerToken('gp-panoramax')
const ids = {
  source: `${token}-source`,
  grid: `${token}-grid`,
  sequences: `${token}-sequences`,
  pictures: `${token}-pictures`
}
let requestId = 0
let coveragePopup = null
let coveragePopupMount = null
let previewPopup = null
let previewPopupMount = null
let previewItemId = null
let previewUrlTemplate = null

const resolveUrl = (href, base) => new URL(href, base).toString()
  .replaceAll('%7B', '{').replaceAll('%7D', '}')

const findVectorSource = (style) => Object.values(style?.sources || {})
  .find((source) => source?.type === 'vector' && Array.isArray(source.tiles) && source.tiles.length)

const parseSequenceId = (value) => {
  try {
    const sequenceIds = Array.isArray(value) ? value : JSON.parse(value || '[]')
    return Array.isArray(sequenceIds) ? sequenceIds.at(-1) || null : null
  } catch {
    return null
  }
}

const clearCoveragePopup = () => {
  coveragePopup = null
  coveragePopupMount?.unmount()
  coveragePopupMount = null
}

const closeCoveragePopup = () => {
  const popup = coveragePopup
  clearCoveragePopup()
  popup?.remove()
}

const clearPreviewPopup = () => {
  previewItemId = null
  previewPopup = null
  previewPopupMount?.unmount()
  previewPopupMount = null
}

const closePreviewPopup = () => {
  const popup = previewPopup
  clearPreviewPopup()
  popup?.remove()
}

const remove = () => {
  if (!isMapLibreMap(props.map)) return
  closeCoveragePopup()
  closePreviewPopup()
  props.map.off?.('click', ids.grid, onGridClick)
  props.map.off?.('click', ids.sequences, onSequenceClick)
  props.map.off?.('click', ids.pictures, onPictureClick)
  props.map.off?.('mouseenter', ids.grid, onPointerEnter)
  props.map.off?.('mouseenter', ids.sequences, onPointerEnter)
  props.map.off?.('mouseenter', ids.pictures, onPointerEnter)
  props.map.off?.('mousemove', ids.grid, onGridMove)
  props.map.off?.('mouseleave', ids.grid, onGridLeave)
  props.map.off?.('mouseleave', ids.sequences, onPointerLeave)
  props.map.off?.('mouseleave', ids.pictures, onPointerLeave)
  removeLayers(props.map, [ids.pictures, ids.sequences, ids.grid])
  if (hasMapLibreSource(props.map, ids.source)) props.map.removeSource(ids.source)
  previewUrlTemplate = null
}

const onPointerEnter = () => { props.map.getCanvas().style.cursor = 'pointer' }
const onPointerLeave = () => { props.map.getCanvas().style.cursor = '' }
const onGridLeave = () => {
  onPointerLeave()
  closePreviewPopup()
}
const onGridClick = (event) => {
  const properties = event.features?.[0]?.properties || {}
  if (properties.best_item) {
    closePreviewPopup()
    emit('select', { pictureId: properties.best_item })
    return
  }
  closeCoveragePopup()
  coveragePopupMount = mountMapPopup(MapInfoPopup, buildPanoramaxCoveragePopupModel(properties))
  const popup = new maplibregl.Popup({
    closeButton: true,
    closeOnClick: true,
    maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH,
    className: getMapPopupVariantClassName('compact', 'gp-panoramax-popup-container')
  })
    .setLngLat(event.lngLat)
    .setDOMContent(coveragePopupMount.element)
    .addTo(props.map)
  popup.on('close', () => {
    if (coveragePopup === popup) clearCoveragePopup()
  })
  coveragePopup = popup
  props.map.easeTo({ center: event.lngLat, zoom: Math.min(props.map.getZoom() + 2, 15) })
}
const onSequenceClick = (event) => emit('select', { sequenceId: event.features?.[0]?.properties?.id || null })
const onPictureClick = (event) => {
  const properties = event.features?.[0]?.properties || {}
  emit('select', {
    pictureId: properties.id || null,
    sequenceId: parseSequenceId(properties.sequences),
    captureTime: properties.ts || null,
    heading: properties.heading || null
  })
}

const onGridMove = (event) => {
  const properties = event.features?.[0]?.properties || {}
  const bestItem = properties.best_item
  onPointerEnter()
  if (!bestItem || !previewUrlTemplate || previewItemId === bestItem) return

  closePreviewPopup()
  previewItemId = bestItem
  previewPopupMount = mountMapPopup(PanoramaxPreviewPopup, {
    thumbnailUrl: previewUrlTemplate.replace('{id}', encodeURIComponent(bestItem))
  })
  const popup = new maplibregl.Popup({
    closeButton: false,
    closeOnClick: false,
    maxWidth: 'min(430px, calc(100vw - 32px))',
    offset: 14,
    className: getMapPopupVariantClassName('wide', 'gp-panoramax-popup-container')
  })
    .setLngLat(event.lngLat)
    .setDOMContent(previewPopupMount.element)
    .addTo(props.map)
  previewPopup = popup
}

const install = (descriptor) => {
  if (!props.visible || !isMapLibreMap(props.map) || hasMapLibreSource(props.map, ids.source)) return
  props.map.addSource(ids.source, {
    type: 'vector',
    tiles: descriptor.tiles,
    minzoom: descriptor.minzoom,
    maxzoom: descriptor.maxzoom
  })
  previewUrlTemplate = descriptor.previewUrlTemplate
  const v2 = descriptor.version === 2
  props.map.addLayer(v2
    ? { id: ids.grid, type: 'fill', source: ids.source, 'source-layer': 'grid', maxzoom: 14.9,
      layout: { 'fill-sort-key': ['get', 'coef'] },
      paint: { 'fill-color': ['interpolate', ['linear'], ['get', 'coef'], 0, '#ffffff', 0.1, '#BA68C8', 0.25, '#BA68C8', 0.5, '#9C27B0', 0.75, '#7B1FA2', 1, '#4A148C'], 'fill-opacity': 0.5 } }
    : { id: ids.grid, type: 'circle', source: ids.source, 'source-layer': 'grid', maxzoom: 6,
      paint: { 'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, 2, 6, 7], 'circle-color': '#e65100', 'circle-opacity': 0.7 } })
  props.map.addLayer({ id: ids.sequences, type: 'line', source: ids.source, 'source-layer': 'sequences', minzoom: v2 ? 15 : 6,
    paint: { 'line-color': v2 ? '#54278f' : '#ff6f00', 'line-width': ['interpolate', ['linear'], ['zoom'], 7, 1, 15, 5], 'line-opacity': 0.9 } })
  props.map.addLayer({ id: ids.pictures, type: 'circle', source: ids.source, 'source-layer': 'pictures', minzoom: 15,
    paint: { 'circle-color': v2 ? '#54278f' : '#ff6f00', 'circle-radius': ['interpolate', ['linear'], ['zoom'], 15, 4, 20, 9], 'circle-stroke-color': '#fff', 'circle-stroke-width': 1.5 } })
  props.map.on('click', ids.grid, onGridClick)
  props.map.on('click', ids.sequences, onSequenceClick)
  props.map.on('click', ids.pictures, onPictureClick)
  ;[ids.grid, ids.sequences, ids.pictures].forEach((id) => {
    props.map.on('mouseenter', id, onPointerEnter)
    props.map.on('mouseleave', id, id === ids.grid ? onGridLeave : onPointerLeave)
  })
  if (v2) props.map.on('mousemove', ids.grid, onGridMove)
}

const load = async () => {
  remove()
  if (!props.visible || !isMapLibreMap(props.map) || !props.endpoint) return
  const currentRequest = ++requestId
  try {
    const response = await fetch(props.endpoint)
    const catalog = await response.json()
    const links = Array.isArray(catalog?.links) ? catalog.links : []
    const href = links.find((link) => link.rel === 'xyz' && String(link.type || '').includes('mapbox-vector-tile'))?.href
    if (!response.ok || !href || currentRequest !== requestId) return
    const styleLinks = links.filter((link) => link.rel === 'xyz-style' && link.href)
    const styles = await Promise.all(styleLinks.map(async (link) => {
      try {
        const styleResponse = await fetch(resolveUrl(link.href, props.endpoint))
        return styleResponse.ok ? styleResponse.json() : null
      } catch {
        return null
      }
    }))
    if (currentRequest !== requestId) return
    const v2Style = styles.find((style) => Number(style?.metadata?.['panoramax:tiles_version']) === 2 && findVectorSource(style))
    const source = findVectorSource(v2Style)
    const previewHref = links.find((link) => link.rel === 'item-preview' && link.href)?.href
    let descriptor = null
    try {
      if (source) {
        descriptor = {
          version: 2,
          tiles: source.tiles.map((tile) => resolveUrl(tile, props.endpoint)),
          minzoom: source.minzoom ?? 0,
          maxzoom: source.maxzoom ?? 17,
          previewUrlTemplate: null
        }
      }
    } catch {
      // A malformed optional v2 style must not disable a compatible v1 endpoint.
    }
    if (descriptor && previewHref) {
      try {
        descriptor.previewUrlTemplate = resolveUrl(previewHref, props.endpoint)
      } catch {
        // Coverage remains usable when the endpoint does not provide a valid thumbnail URL.
      }
    }
    install(descriptor || { version: 1, tiles: [resolveUrl(href, props.endpoint)], minzoom: 0, maxzoom: 15, previewUrlTemplate: null })
  } catch {
    // A configured external provider failing must not affect the Timeline map.
  }
}

watch(() => [props.visible, props.endpoint, props.map], load, { immediate: true })
onBeforeUnmount(() => { requestId += 1; remove() })
</script>
