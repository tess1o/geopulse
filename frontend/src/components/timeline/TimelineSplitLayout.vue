<template>
  <div ref="layoutRef" class="timeline-split-layout" :style="layoutStyle">
    <div class="timeline-split-main" :class="mainClasses">
      <div class="timeline-split-map-pane">
        <slot name="map" />
      </div>

      <div
        ref="sheetRef"
        class="timeline-split-side-pane"
        :class="sheetClasses"
        :style="sheetStyle"
      >
        <div
          v-if="collapsible"
          class="timeline-sheet-handle"
          @pointerdown="handlePointerDown"
        >
          <span class="timeline-sheet-grip"></span>
          <button
            type="button"
            class="timeline-sheet-toggle-button"
            :aria-label="toggleLabel"
            :title="toggleLabel"
            @pointerdown.stop
            @click="cycleSheetState"
          >
            <span class="timeline-sheet-label">{{ sheetLabel }}</span>
            <i class="timeline-sheet-toggle-icon" :class="toggleIcon"></i>
          </button>
          <div
            v-if="showDateNavigation && dateLabel && sheetState !== 'collapsed'"
            class="timeline-sheet-date-nav"
            aria-label="Timeline day navigation"
            @pointerdown.stop
          >
            <button
              type="button"
              class="timeline-sheet-date-nav-button"
              title="Previous day"
              aria-label="Previous day"
              @click="$emit('navigate-date', -1)"
            >
              <i class="pi pi-chevron-left"></i>
            </button>
            <span class="timeline-sheet-date-text">{{ dateLabel }}</span>
            <button
              type="button"
              class="timeline-sheet-date-nav-button"
              title="Next day"
              aria-label="Next day"
              @click="$emit('navigate-date', 1)"
            >
              <i class="pi pi-chevron-right"></i>
            </button>
          </div>
        </div>

        <slot name="side" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'

const props = defineProps({
  collapsible: {
    type: Boolean,
    default: true
  },
  collapsedLabel: {
    type: String,
    default: 'Timeline'
  },
  expandedLabel: {
    type: String,
    default: 'Movement Timeline'
  },
  showDateNavigation: {
    type: Boolean,
    default: false
  },
  dateLabel: {
    type: String,
    default: ''
  },
  initialState: {
    type: String,
    default: 'half',
    validator: (value) => ['collapsed', 'half', 'expanded'].includes(value)
  }
})

const emit = defineEmits(['layout-resize', 'navigate-date'])

const MOBILE_TIMELINE_MEDIA = '(max-width: 768px), (max-height: 520px) and (pointer: coarse)'

const layoutRef = ref(null)
const sheetRef = ref(null)
const sheetState = ref(props.initialState)
const sheetHeight = ref(null)
const isDragging = ref(false)
const didDrag = ref(false)
const dragStartY = ref(0)
const dragStartHeight = ref(0)

const isMobileViewport = () => (
  typeof window !== 'undefined'
  && typeof window.matchMedia === 'function'
  && window.matchMedia(MOBILE_TIMELINE_MEDIA).matches
)

const emitLayoutResize = () => {
  nextTick(() => {
    emit('layout-resize')
  })
}

const getSheetHeights = () => {
  const pageHeight = layoutRef.value?.getBoundingClientRect().height || 0
  const viewportHeight = window.visualViewport?.height || window.innerHeight || 800
  const containerHeight = Math.max(320, Math.min(pageHeight || viewportHeight, viewportHeight))
  const collapsed = 44
  const half = Math.max(collapsed + 80, containerHeight * 0.5)
  const expanded = Math.max(half + 64, containerHeight * 0.88)
  return {
    collapsed,
    half: Math.min(half, containerHeight - 48),
    expanded: Math.min(expanded, containerHeight - 24)
  }
}

const syncSheetHeight = () => {
  if (!props.collapsible || !isMobileViewport()) {
    sheetHeight.value = null
    return
  }

  sheetHeight.value = getSheetHeights()[sheetState.value]
}

const setSheetState = (state) => {
  if (!props.collapsible) {
    return
  }

  sheetState.value = state
  syncSheetHeight()
  emitLayoutResize()
}

const collapseForMobileSelection = () => {
  if (!isMobileViewport() || sheetState.value === 'collapsed') {
    return
  }

  setSheetState('collapsed')
}

const cycleSheetState = () => {
  if (didDrag.value) {
    didDrag.value = false
    return
  }

  const nextState = isMobileViewport()
    ? sheetState.value === 'collapsed'
      ? 'half'
      : sheetState.value === 'half'
        ? 'expanded'
        : 'collapsed'
    : sheetState.value === 'collapsed'
      ? 'half'
      : 'collapsed'
  setSheetState(nextState)
}

const handlePointerMove = (event) => {
  if (!isDragging.value) return
  const heights = getSheetHeights()
  const deltaY = dragStartY.value - event.clientY
  if (Math.abs(deltaY) > 4) {
    didDrag.value = true
  }
  const nextHeight = Math.min(
    heights.expanded,
    Math.max(heights.collapsed, dragStartHeight.value + deltaY)
  )
  sheetHeight.value = nextHeight
}

const handlePointerUp = () => {
  if (!isDragging.value) return

  const heights = getSheetHeights()
  const currentHeight = sheetHeight.value || heights.collapsed
  const candidates = [
    ['collapsed', heights.collapsed],
    ['half', heights.half],
    ['expanded', heights.expanded]
  ]
  const [nearestState] = candidates.reduce((best, candidate) => (
    Math.abs(candidate[1] - currentHeight) < Math.abs(best[1] - currentHeight)
      ? candidate
      : best
  ))

  isDragging.value = false
  setSheetState(nearestState)
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', handlePointerUp)
  window.removeEventListener('pointercancel', handlePointerUp)
}

const handlePointerDown = (event) => {
  if (!props.collapsible || !isMobileViewport()) return
  if (event.pointerType === 'mouse' && event.button !== 0) return

  event.preventDefault()
  isDragging.value = true
  didDrag.value = false
  dragStartY.value = event.clientY
  dragStartHeight.value = sheetRef.value?.getBoundingClientRect().height
    || getSheetHeights()[sheetState.value]

  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', handlePointerUp)
  window.addEventListener('pointercancel', handlePointerUp)
}

const handleViewportResize = () => {
  const mobileViewport = isMobileViewport()
  syncSheetHeight()
  if (!mobileViewport) {
    emitLayoutResize()
  }
}

const sheetClasses = computed(() => ({
  [`timeline-sheet--${sheetState.value}`]: props.collapsible,
  'timeline-sheet--dragging': isDragging.value,
  'timeline-sheet--compact': props.collapsible && sheetState.value === 'collapsed' && !isDragging.value,
  'timeline-split-side-pane--static': !props.collapsible
}))

const mainClasses = computed(() => ({
  'timeline-main--sheet-collapsed': props.collapsible && sheetState.value === 'collapsed'
}))

const sheetLabel = computed(() => (
  sheetState.value === 'collapsed' && !isDragging.value
    ? props.collapsedLabel
    : props.expandedLabel
))

const toggleLabel = computed(() => (
  sheetState.value === 'collapsed'
    ? `Show ${props.expandedLabel.toLowerCase()}`
    : `Collapse ${props.expandedLabel.toLowerCase()}`
))

const toggleIcon = computed(() => (
  sheetState.value === 'collapsed'
    ? 'pi pi-chevron-left'
    : 'pi pi-chevron-right'
))

const sheetStyle = computed(() => (
  sheetHeight.value
    ? { '--timeline-sheet-height': `${sheetHeight.value}px` }
    : {}
))

const layoutStyle = computed(() => (
  sheetHeight.value
    ? { '--timeline-mobile-sheet-height': `${sheetHeight.value}px` }
    : { '--timeline-mobile-sheet-height': '0px' }
))

onMounted(() => {
  syncSheetHeight()
  window.addEventListener('resize', handleViewportResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleViewportResize)
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', handlePointerUp)
  window.removeEventListener('pointercancel', handlePointerUp)
})

defineExpose({
  collapseForMobileSelection,
  setSheetState,
  syncSheetHeight
})
</script>

<style scoped>
.timeline-split-layout {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  overscroll-behavior: contain;
}

.timeline-split-main {
  position: relative;
  flex: 1;
  display: grid;
  grid-template-columns:
    minmax(0, var(--timeline-split-map-column, 1fr))
    minmax(var(--timeline-split-side-min, 320px), var(--timeline-split-side-width, clamp(360px, 32vw, 520px)));
  grid-template-rows: minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
}

.timeline-split-map-pane {
  display: flex;
  margin-top: 0.5rem;
  margin-left: 0.5rem;
  margin-right: 1rem;
  height: auto;
  max-height: 82vh;
  min-height: 0;
  min-width: 0;
  flex-direction: column;
}

.timeline-split-side-pane {
  display: flex;
  flex-direction: column;
  overflow: hidden !important;
  height: auto;
  min-height: 0;
  min-width: 320px;
  border-radius: var(--gp-radius-medium);
}

.timeline-sheet-handle {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  width: 100%;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.35rem 2.75rem 0.4rem var(--gp-spacing-md);
  border: none;
  border-bottom: 2px solid var(--gp-primary-light);
  background: transparent;
  color: var(--gp-primary);
  font: inherit;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
}

.timeline-sheet-handle:hover {
  background: var(--gp-surface-light);
}

.timeline-sheet-toggle-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-basis: 100%;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-weight: inherit;
  cursor: pointer;
}

.timeline-sheet-toggle-button:focus-visible,
.timeline-sheet-date-nav-button:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.timeline-sheet-grip {
  display: none;
}

.timeline-sheet-label {
  line-height: 1.2;
}

.timeline-sheet-date-nav {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-basis: 100%;
  gap: var(--gp-spacing-sm);
  max-width: 100%;
}

.timeline-sheet-date-text {
  flex: 0 0 clamp(10rem, 38vw, 13rem);
  width: clamp(10rem, 38vw, 13rem);
  min-width: 0;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.timeline-sheet-date-nav-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 1.5rem;
  width: 1.5rem;
  height: 1.5rem;
  padding: 0;
  border: 1px solid var(--gp-border-medium);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-white);
  color: var(--gp-text-secondary);
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.timeline-sheet-date-nav-button:hover {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: #fff;
}

.timeline-sheet-date-nav-button i {
  font-size: 0.7rem;
}

.timeline-sheet-toggle-icon {
  position: absolute;
  right: var(--gp-spacing-md);
  font-size: 0.9rem;
}

.timeline-split-side-pane :deep(.timeline-container) {
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
  max-height: 100%;
  overflow: hidden;
}

.timeline-split-side-pane :deep(.timeline-content) {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.timeline-split-side-pane :deep(.timeline-header) {
  display: none;
}

.timeline-main--sheet-collapsed .timeline-split-map-pane {
  margin-right: 0.5rem;
}

.timeline-main--sheet-collapsed {
  grid-template-columns: minmax(0, 1fr);
}

.timeline-main--sheet-collapsed .timeline-split-side-pane {
  position: absolute;
  top: calc(1rem + env(safe-area-inset-top));
  right: calc(5.5rem + env(safe-area-inset-right));
  z-index: 950;
  flex: none;
  width: min(12rem, calc(100% - 7rem));
  height: 44px;
  min-width: 0;
  min-height: 0;
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: 999px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.18);
}

.timeline-main--sheet-collapsed .timeline-sheet-handle {
  height: 100%;
  min-height: 44px;
  border-bottom: none;
  border-radius: inherit;
  font-size: 0.9rem;
}

.timeline-main--sheet-collapsed .timeline-sheet-toggle-icon {
  right: var(--gp-spacing-sm);
}

.timeline-sheet--compact :deep(.timeline-container) {
  display: none;
}

.p-dark .timeline-sheet-handle:hover {
  background: var(--gp-surface-dark);
}

.p-dark .timeline-sheet-handle {
  border-bottom-color: var(--gp-border-medium);
}

.p-dark .timeline-sheet-date-nav-button {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-secondary);
}

.p-dark .timeline-main--sheet-collapsed .timeline-split-side-pane {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .timeline-split-main {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 0;
    height: 100%;
    overflow: hidden;
    overscroll-behavior: contain;
  }

  .timeline-split-map-pane {
    position: absolute;
    inset: 0;
    flex: none;
    width: 100%;
    height: 100%;
    min-height: 0;
    max-height: none;
    margin: 0;
  }

  .timeline-split-side-pane {
    position: absolute;
    left: env(safe-area-inset-left);
    right: env(safe-area-inset-right);
    bottom: 0;
    z-index: 1050;
    flex: none;
    width: auto;
    height: var(--timeline-sheet-height, 168px);
    max-height: calc(100% - 24px);
    min-height: 0;
    margin: 0;
    overflow: hidden !important;
    background: var(--gp-surface-white);
    border: 1px solid var(--gp-border-light);
    border-bottom: none;
    border-radius: 16px 16px 0 0;
    box-shadow: 0 -10px 28px rgba(15, 23, 42, 0.18);
    transition: height 0.22s ease;
    will-change: height, transform;
  }

  .timeline-main--sheet-collapsed .timeline-split-map-pane {
    margin: 0;
  }

  .timeline-main--sheet-collapsed .timeline-split-side-pane {
    position: absolute;
    top: auto;
    left: env(safe-area-inset-left);
    right: env(safe-area-inset-right);
    bottom: 0;
    z-index: 1050;
    width: auto;
    height: var(--timeline-sheet-height, 168px);
    min-width: 320px;
    background: var(--gp-surface-white);
    border-bottom: none;
    border-radius: 16px 16px 0 0;
    box-shadow: 0 -10px 28px rgba(15, 23, 42, 0.18);
    transform: none;
  }

  .timeline-sheet--compact {
    left: 50%;
    right: auto;
    bottom: calc(0.55rem + env(safe-area-inset-bottom));
    width: min(16rem, calc(100% - 2rem - env(safe-area-inset-left) - env(safe-area-inset-right)));
    height: var(--timeline-sheet-height, 44px);
    max-height: none;
    min-width: 0;
    border: 1px solid var(--gp-border-light);
    border-radius: 999px;
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.22);
    transform: translateX(-50%);
  }

  .timeline-sheet--compact :deep(.timeline-container) {
    display: none;
  }

  .timeline-sheet--dragging {
    transition: none;
  }

  .timeline-sheet-handle {
    position: relative;
    display: flex;
    width: 100%;
    min-height: 44px;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    padding: 0.45rem 2.25rem 0.5rem;
    border: none;
    border-bottom: 1px solid var(--gp-border-light);
    background: var(--gp-surface-white);
    color: var(--gp-text-secondary);
    font: inherit;
    font-size: 0.85rem;
    font-weight: 700;
    touch-action: none;
    cursor: grab;
  }

  .timeline-sheet-toggle-icon {
    display: none;
  }

  .timeline-sheet--compact .timeline-sheet-handle {
    height: 100%;
    min-height: 44px;
    padding: 0 1rem;
    border-bottom: none;
    border-radius: inherit;
  }

  .timeline-sheet--compact .timeline-sheet-toggle-button {
    width: 100%;
  }

  .timeline-sheet--dragging .timeline-sheet-handle {
    cursor: grabbing;
  }

  .timeline-sheet-grip {
    display: block;
    position: absolute;
    top: 7px;
    width: 42px;
    height: 4px;
    border-radius: 999px;
    background: var(--gp-border-medium);
  }

  .timeline-sheet-label {
    padding-top: 8px;
  }

  .timeline-sheet-date-nav {
    flex-basis: 100%;
    gap: var(--gp-spacing-xs);
  }

  .timeline-sheet-date-text {
    flex-basis: clamp(10rem, 52vw, 14rem);
    width: clamp(10rem, 52vw, 14rem);
    font-size: 0.8rem;
  }

  .timeline-sheet--compact .timeline-sheet-label {
    padding-top: 0;
    font-size: 0.8rem;
  }

  .timeline-sheet--compact .timeline-sheet-grip {
    position: static;
    flex: 0 0 36px;
    width: 36px;
  }

  .timeline-split-side-pane :deep(.timeline-content) {
    padding: 0 var(--gp-spacing-sm) var(--gp-spacing-md);
  }

  .p-dark .timeline-split-side-pane,
  .p-dark .timeline-sheet-handle {
    background: var(--gp-surface-dark);
    border-color: var(--gp-border-dark);
  }

  .p-dark .timeline-sheet-grip {
    background: var(--gp-border-medium);
  }
}

@media (min-width: 768px) and (max-width: 1024px) {
  .timeline-split-map-pane {
    max-height: 76vh;
  }
}

@media (min-width: 1024px) and (max-width: 1280px) {
  .timeline-split-map-pane {
    max-height: 80vh;
  }
}

@media (min-width: 1280px) and (max-width: 1599px) {
  .timeline-split-map-pane {
    max-height: 82vh;
  }
}

@media (min-width: 1600px) {
  .timeline-split-map-pane {
    max-height: 86vh;
  }
}
</style>
