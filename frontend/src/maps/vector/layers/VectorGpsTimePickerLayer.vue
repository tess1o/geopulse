<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import * as maplibregl from 'maplibre-gl'
import '@/maps/shared/styles/gpsTimePicker.css'
import {
  createFeatureCollection,
  ensureGeoJsonSource,
  ensureLayer,
  getMapLibreSource,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources
} from '@/maps/vector/utils/maplibreLayerUtils'
import { gpsRangeLine, gpsTrackLines, isInGpsRange, nearestSampleUnderClick } from '@/utils/gpsTimePicker'

const props = defineProps({
  map: { type: Object, required: true },
  points: { type: Array, default: () => [] },
  range: { type: Object, default: () => ({ from: null, to: null }) },
  inspectedIndex: { type: Number, default: null },
  hoveredIndex: { type: Number, default: null },
  pinLabels: { type: Object, default: () => ({ from: 'From', to: 'To' }) }
})
const emit = defineEmits(['inspect', 'hover'])

const token = nextLayerToken('gp-gps-picker')
const ids = {
  trackSource: `${token}-track-source`, rangeSource: `${token}-range-source`, pointSource: `${token}-point-source`,
  moving: `${token}-moving`, gaps: `${token}-gaps`, range: `${token}-range`, dots: `${token}-dots`, labels: `${token}-labels`
}
let boundMap = null, pins = [], frame = null, hovered = null, styleLoadHandler = null
const listeners = []

const toLngLat = line => line.map(([latitude, longitude]) => [longitude, latitude])
const lineFeature = (line, kind) => ({ type: 'Feature', geometry: { type: 'LineString', coordinates: toLngLat(line) }, properties: { kind } })
const hasRange = () => Number.isInteger(props.range?.from) && Number.isInteger(props.range?.to)

const pointCollection = () => {
  const priority = new Set([props.inspectedIndex, props.hoveredIndex, props.range?.from, props.range?.to].filter(Number.isInteger))
  return createFeatureCollection(props.points.map(point => {
    const emphasised = point.index === props.inspectedIndex || point.index === props.hoveredIndex
    return {
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [point.longitude, point.latitude] },
      properties: {
        // Lets the editor tell a click on a point from a click on the empty map.
        gpsPickerPoint: 1,
        index: point.index,
        label: String(point.number),
        color: point.color,
        emphasised: emphasised ? 1 : 0,
        dimmed: hasRange() && !emphasised && !isInGpsRange(point.index, props.range) ? 1 : 0,
        // Lower keys win label collisions, so selected and hovered samples always keep their number.
        sortKey: priority.has(point.index) ? -1 : point.index
      }
    }
  }))
}

const trackCollection = () => {
  const { moving, gaps } = gpsTrackLines(props.points)
  return createFeatureCollection([...moving.map(line => lineFeature(line, 'moving')), ...gaps.map(line => lineFeature(line, 'gap'))])
}

const rangeCollection = () => {
  const line = hasRange() ? gpsRangeLine(props.points, props.range.from, props.range.to) : []
  return createFeatureCollection(line.length > 1 ? [lineFeature(line, 'range')] : [])
}

const createPinElement = (boundary, point) => {
  const anchor = document.createElement('div')
  anchor.className = 'gps-picker-pin-anchor'
  const pin = document.createElement('span')
  pin.className = `gps-picker-pin gps-picker-pin--${boundary}`
  pin.textContent = `${props.pinLabels[boundary]} · ${point.number}`
  pin.addEventListener('click', event => { event.stopPropagation(); emit('inspect', point.index) })
  anchor.appendChild(pin)
  return anchor
}

const renderPins = () => {
  pins.forEach(marker => marker.remove())
  pins = []
  if (!isMapLibreMap(boundMap)) return
  ;[['from', props.range?.from], ['to', props.range?.to]].forEach(([boundary, index]) => {
    // `points` may be a narrowed part of the list, so look the point up by its own index.
    const point = Number.isInteger(index) ? props.points.find(candidate => candidate.index === index) : null
    if (!point) return
    pins.push(new maplibregl.Marker({ element: createPinElement(boundary, point), anchor: 'center' })
      .setLngLat([point.longitude, point.latitude]).addTo(boundMap))
  })
}

const updateState = () => {
  frame = null
  if (!isMapLibreMap(boundMap)) return
  getMapLibreSource(boundMap, ids.pointSource)?.setData(pointCollection())
  getMapLibreSource(boundMap, ids.rangeSource)?.setData(rangeCollection())
  renderPins()
}

const scheduleState = () => {
  if (frame !== null) return
  frame = typeof window !== 'undefined' && window.requestAnimationFrame ? window.requestAnimationFrame(updateState) : setTimeout(updateState, 0)
}

// Pins stop their clicks; a canvas click away from every dot clears the selected point.
const handleMapClick = (event) => {
  let features = []
  try { features = boundMap.queryRenderedFeatures?.(event?.point, { layers: [ids.dots] }) || [] } catch { features = [] }
  if (!features.length) emit('inspect', null)
}

// Every dot under the click is in the event; take the sample nearest to where it landed, not the one drawn on top.
const sampleUnderClick = event => {
  const clicked = event?.features?.[0]?.properties?.index
  if (!event?.point || !boundMap?.project) return clicked
  const under = (event.features || [])
    .filter(feature => Number.isInteger(feature.properties?.index) && feature.geometry?.type === 'Point')
    .map(feature => {
      const { x, y } = boundMap.project(feature.geometry.coordinates)
      return { index: feature.properties.index, x, y }
    })
  return nearestSampleUnderClick(under, event.point) ?? clicked
}

const registerEvents = () => {
  const map = boundMap
  map.on('click', handleMapClick)
  const featureIndex = event => event?.features?.[0]?.properties?.index
  const on = (event, handler) => { map.on(event, ids.dots, handler); listeners.push([event, handler]) }
  on('click', event => {
    event.preventDefault?.()
    const index = sampleUnderClick(event)
    if (Number.isInteger(index)) emit('inspect', index)
  })
  on('mousemove', event => {
    map.getCanvas().style.cursor = 'pointer'
    const index = featureIndex(event)
    if (Number.isInteger(index) && index !== hovered) { hovered = index; emit('hover', index) }
  })
  on('mouseleave', () => {
    map.getCanvas().style.cursor = ''
    hovered = null
    emit('hover', null)
  })
}

const unregisterEvents = () => {
  if (isMapLibreMap(boundMap)) {
    boundMap.off('click', handleMapClick)
    listeners.forEach(([event, handler]) => boundMap.off(event, ids.dots, handler))
  }
  listeners.length = 0
}

const renderLayers = () => {
  const map = boundMap
  if (!isMapLibreMap(map)) return
  ensureGeoJsonSource(map, ids.trackSource, trackCollection())
  ensureGeoJsonSource(map, ids.rangeSource, rangeCollection())
  ensureGeoJsonSource(map, ids.pointSource, pointCollection())
  ensureLayer(map, { id: ids.moving, type: 'line', source: ids.trackSource, filter: ['==', ['get', 'kind'], 'moving'],
    paint: { 'line-color': '#475569', 'line-width': 2, 'line-opacity': 0.75 } })
  ensureLayer(map, { id: ids.gaps, type: 'line', source: ids.trackSource, filter: ['==', ['get', 'kind'], 'gap'],
    paint: { 'line-color': '#475569', 'line-width': 2, 'line-opacity': 0.6, 'line-dasharray': [2, 3] } })
  ensureLayer(map, { id: ids.range, type: 'line', source: ids.rangeSource,
    paint: { 'line-color': '#2563eb', 'line-width': 6, 'line-opacity': 0.55 } })
  ensureLayer(map, { id: ids.dots, type: 'circle', source: ids.pointSource,
    layout: { 'circle-sort-key': ['get', 'emphasised'] },
    paint: {
      'circle-color': ['get', 'color'],
      'circle-radius': ['case', ['==', ['get', 'emphasised'], 1], 8, 5],
      'circle-stroke-color': ['case', ['==', ['get', 'emphasised'], 1], '#0f172a', '#ffffff'],
      'circle-stroke-width': ['case', ['==', ['get', 'emphasised'], 1], 3, 1.5],
      'circle-opacity': ['case', ['==', ['get', 'dimmed'], 1], 0.3, 0.95],
      'circle-stroke-opacity': ['case', ['==', ['get', 'dimmed'], 1], 0.4, 1]
    } })
  ensureLayer(map, { id: ids.labels, type: 'symbol', source: ids.pointSource,
    layout: {
      'text-field': ['get', 'label'],
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
      'text-size': 11,
      'text-anchor': 'bottom-left',
      'text-offset': [0.5, -0.3],
      'symbol-sort-key': ['get', 'sortKey'],
      'text-padding': 3
    },
    paint: {
      'text-color': ['case', ['==', ['get', 'emphasised'], 1], '#0f172a', ['get', 'color']],
      'text-halo-color': '#ffffff',
      'text-halo-width': 2,
      'text-opacity': ['case', ['==', ['get', 'dimmed'], 1], 0.45, 1]
    } })
  unregisterEvents()
  registerEvents()
  renderPins()
}

const detach = () => {
  unregisterEvents()
  if (frame !== null) {
    if (typeof window !== 'undefined' && window.cancelAnimationFrame) window.cancelAnimationFrame(frame)
    else clearTimeout(frame)
    frame = null
  }
  pins.forEach(marker => marker.remove())
  pins = []
  if (isMapLibreMap(boundMap)) {
    if (styleLoadHandler) boundMap.off('style.load', styleLoadHandler)
    try { boundMap.getCanvas().style.cursor = '' } catch { /* map already torn down */ }
    removeLayers(boundMap, [ids.labels, ids.dots, ids.range, ids.gaps, ids.moving])
    removeSources(boundMap, [ids.pointSource, ids.rangeSource, ids.trackSource])
  }
  styleLoadHandler = null
  boundMap = null
}

watch(() => [props.map, props.points], () => {
  if (!isMapLibreMap(props.map)) { detach(); return }
  if (boundMap !== props.map) {
    detach()
    boundMap = props.map
    styleLoadHandler = () => renderLayers()
    boundMap.on('style.load', styleLoadHandler)
  }
  renderLayers()
}, { immediate: true })
watch(() => [props.range?.from, props.range?.to, props.inspectedIndex, props.hoveredIndex], scheduleState)

onBeforeUnmount(detach)
</script>
