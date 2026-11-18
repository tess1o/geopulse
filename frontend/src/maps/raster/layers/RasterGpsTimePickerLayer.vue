<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import L from 'leaflet'
import '@/maps/shared/styles/gpsTimePicker.css'
import { gpsRangeLine, gpsTrackLines, isInGpsRange, nearestSampleUnderClick, selectLabelledIndices } from '@/utils/gpsTimePicker'

const props = defineProps({
  map: { type: Object, required: true },
  points: { type: Array, default: () => [] },
  range: { type: Object, default: () => ({ from: null, to: null }) },
  inspectedIndex: { type: Number, default: null },
  hoveredIndex: { type: Number, default: null },
  pinLabels: { type: Object, default: () => ({ from: 'From', to: 'To' }) }
})
const emit = defineEmits(['inspect', 'hover'])

const TRACK_PANE = 'gpsPickerTrack'
const DOT_PANE = 'gpsPickerDots'
let boundMap = null, trackRenderer = null, dotRenderer = null
let trackLayers = [], rangeLine = null, dots = new Map(), labels = [], pins = []
// `points` may be a narrowed part of the list, so everything is keyed by each point's own index.
let pointsByIndex = new Map()

const ensurePane = (map, name, zIndex) => {
  const pane = map.getPane(name) || map.createPane(name)
  pane.style.zIndex = String(zIndex)
  return pane
}

const removeAll = list => { list.forEach(layer => layer.remove()); return [] }

const hasRange = () => Number.isInteger(props.range?.from) && Number.isInteger(props.range?.to)
const isEmphasised = index => index === props.inspectedIndex || index === props.hoveredIndex

const dotStyle = point => {
  const emphasised = isEmphasised(point.index)
  const dimmed = hasRange() && !isInGpsRange(point.index, props.range) && !emphasised
  return {
    radius: emphasised ? 8 : 5,
    color: emphasised ? '#0f172a' : '#ffffff',
    weight: emphasised ? 3 : 1.5,
    fillColor: point.color,
    fillOpacity: dimmed ? 0.3 : 0.95,
    opacity: dimmed ? 0.4 : 1
  }
}

const renderLabels = () => {
  labels = removeAll(labels)
  const map = boundMap
  if (!map || !props.points.length) return
  const bounds = map.getBounds().pad(0.1)
  const items = props.points
    .filter(point => bounds.contains([point.latitude, point.longitude]))
    .map(point => ({ index: point.index, ...map.latLngToContainerPoint([point.latitude, point.longitude]) }))
  const priority = [props.inspectedIndex, props.hoveredIndex, props.range?.from, props.range?.to].filter(Number.isInteger)
  selectLabelledIndices(items, { priority }).forEach(index => {
    const point = pointsByIndex.get(index)
    const emphasised = isEmphasised(index)
    labels.push(L.marker([point.latitude, point.longitude], {
      icon: L.divIcon({
        className: 'gps-picker-label-icon',
        html: `<span class="gps-picker-label${emphasised ? ' is-emphasised' : ''}" style="--gps-picker-color:${point.color}">${point.number}</span>`,
        iconSize: [0, 0],
        iconAnchor: [0, 0]
      }),
      interactive: false,
      keyboard: false,
      zIndexOffset: emphasised ? 2000 : 0
    }).addTo(map))
  })
}

const renderPins = () => {
  pins = removeAll(pins)
  if (!boundMap) return
  ;[['from', props.range?.from], ['to', props.range?.to]].forEach(([boundary, index]) => {
    const point = Number.isInteger(index) ? pointsByIndex.get(index) : null
    if (!point) return
    const marker = L.marker([point.latitude, point.longitude], {
      icon: L.divIcon({
        className: 'gps-picker-pin-icon',
        html: `<div class="gps-picker-pin-anchor"><span class="gps-picker-pin gps-picker-pin--${boundary}">${props.pinLabels[boundary]} · ${point.number}</span></div>`,
        iconSize: [0, 0],
        iconAnchor: [0, 0]
      }),
      keyboard: false,
      zIndexOffset: 3000,
      bubblingMouseEvents: false
    }).addTo(boundMap)
    marker.on('click', () => emit('inspect', point.index))
    pins.push(marker)
  })
}

const renderRange = () => {
  rangeLine?.remove()
  rangeLine = null
  const line = hasRange() ? gpsRangeLine(props.points, props.range.from, props.range.to) : []
  if (line.length > 1) {
    rangeLine = L.polyline(line, { pane: TRACK_PANE, renderer: trackRenderer, color: '#2563eb', weight: 6, opacity: 0.55, interactive: false }).addTo(boundMap)
  }
}

const restyle = () => {
  if (!boundMap) return
  dots.forEach((dot, index) => dot.setStyle(dotStyle(pointsByIndex.get(index))))
  ;[props.inspectedIndex, props.hoveredIndex].filter(Number.isInteger).forEach(index => dots.get(index)?.bringToFront())
  renderRange()
  renderPins()
  renderLabels()
}

const clear = () => {
  trackLayers = removeAll(trackLayers)
  dots.forEach(dot => dot.remove())
  dots = new Map()
  labels = removeAll(labels)
  pins = removeAll(pins)
  rangeLine?.remove()
  rangeLine = null
}

// Dots and pins do not bubble clicks to the map, so a map click is a click on an empty place.
const handleMapClick = () => emit('inspect', null)

const detach = () => {
  clear()
  if (boundMap) boundMap.off('moveend zoomend', renderLabels)
  boundMap?.off('click', handleMapClick)
  trackRenderer?.remove()
  dotRenderer?.remove()
  trackRenderer = null
  dotRenderer = null
  boundMap = null
}

// Largest dot radius plus the canvas click tolerance: how far from its centre a dot still takes a click.
const DOT_REACH = 8 + 4

// The canvas hands the click to the dot drawn last; pick the sample under the click nearest to where it landed.
// Leaflet reports a small circle marker's own centre as the event's containerPoint, so the position comes from the
// original mouse event.
const sampleUnderClick = (event, clicked) => {
  const click = event?.originalEvent && boundMap?.mouseEventToContainerPoint
    ? boundMap.mouseEventToContainerPoint(event.originalEvent) : null
  if (!click || !boundMap?.latLngToContainerPoint) return clicked
  const under = props.points
    .map(point => {
      const { x, y } = boundMap.latLngToContainerPoint([point.latitude, point.longitude])
      return { index: point.index, x, y }
    })
    .filter(candidate => Math.hypot(candidate.x - click.x, candidate.y - click.y) <= DOT_REACH)
  return nearestSampleUnderClick(under, click) ?? clicked
}

const render = () => {
  const map = props.map
  if (!map?.getPane) { detach(); return }
  if (boundMap !== map) {
    detach()
    boundMap = map
    ensurePane(map, TRACK_PANE, 405)
    ensurePane(map, DOT_PANE, 420)
    trackRenderer = L.canvas({ pane: TRACK_PANE, padding: 0.5 })
    dotRenderer = L.canvas({ pane: DOT_PANE, padding: 0.5, tolerance: 4 })
    map.on('moveend zoomend', renderLabels)
    map.on('click', handleMapClick)
  }
  clear()
  pointsByIndex = new Map(props.points.map(point => [point.index, point]))
  const { moving, gaps } = gpsTrackLines(props.points)
  trackLayers = [
    ...moving.map(line => L.polyline(line, { pane: TRACK_PANE, renderer: trackRenderer, color: '#475569', weight: 2, opacity: 0.75, interactive: false })),
    ...gaps.map(line => L.polyline(line, { pane: TRACK_PANE, renderer: trackRenderer, color: '#475569', weight: 2, opacity: 0.6, dashArray: '4 6', interactive: false }))
  ].map(layer => layer.addTo(map))
  props.points.forEach(point => {
    const dot = L.circleMarker([point.latitude, point.longitude], { ...dotStyle(point), pane: DOT_PANE, renderer: dotRenderer, bubblingMouseEvents: false })
    // Canvas paths ignore bubblingMouseEvents, so stop the click before it reaches the map's empty-click handler.
    dot.on('click', event => { L.DomEvent.stopPropagation(event); emit('inspect', sampleUnderClick(event, point.index)) })
    dot.on('mouseover', () => emit('hover', point.index))
    dot.on('mouseout', () => emit('hover', null))
    dots.set(point.index, dot.addTo(map))
  })
  restyle()
}

watch(() => [props.map, props.points], render, { immediate: true })
watch(() => [props.range?.from, props.range?.to, props.inspectedIndex, props.hoveredIndex], restyle)

onBeforeUnmount(detach)
</script>
