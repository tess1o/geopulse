import { getCurrentInstance, onBeforeUnmount, unref } from 'vue'

export const LONG_PRESS_CONTEXT_MENU_DURATION_MS = 550
export const LONG_PRESS_CONTEXT_MENU_MOVE_THRESHOLD_PX = 12

const TOUCH_CONTEXT_MENU_DEDUP_MS = 700
const CLICK_SUPPRESSION_MS = 700

export const DEFAULT_LONG_PRESS_CONTEXT_MENU_IGNORED_SELECTOR = [
  'a',
  'button',
  'input',
  'textarea',
  'select',
  '[contenteditable="true"]',
  '[role="button"]',
  '[role="link"]',
  '.p-button',
  '.p-dialog',
  '.p-contextmenu'
].join(',')

const getTimerHost = () => (typeof window === 'undefined' ? globalThis : window)

const isFiniteNumber = (value) => typeof value === 'number' && Number.isFinite(value)

const getScrollOffset = (axis) => {
  if (typeof window === 'undefined') return 0

  if (axis === 'x') {
    return window.scrollX || document.documentElement?.scrollLeft || document.body?.scrollLeft || 0
  }

  return window.scrollY || document.documentElement?.scrollTop || document.body?.scrollTop || 0
}

const getTouchIdentifier = (touch) => touch?.identifier ?? 0

const getTouchCoordinate = (touch, key, fallback = 0) => (
  isFiniteNumber(touch?.[key]) ? touch[key] : fallback
)

const getTouchPoint = (event) => event?.touches?.[0] || event?.changedTouches?.[0] || null

const findTouchById = (touchList, touchId) => {
  if (!touchList || touchId === null) return null

  for (let index = 0; index < touchList.length; index += 1) {
    const touch = touchList[index]
    if (getTouchIdentifier(touch) === touchId) {
      return touch
    }
  }

  return null
}

const hasIgnoredTarget = (target, ignoredSelector) => {
  if (!target || typeof target.closest !== 'function' || !ignoredSelector) return false
  return Boolean(target.closest(ignoredSelector))
}

const preventAndStop = (event) => {
  event?.stopPropagation?.()
  if (event?.cancelable !== false) {
    event?.preventDefault?.()
  }
}

const getPageCoordinate = (touch, pageKey, clientKey, axis) => {
  if (isFiniteNumber(touch?.[pageKey])) {
    return touch[pageKey]
  }

  const clientCoordinate = getTouchCoordinate(touch, clientKey)
  return clientCoordinate + getScrollOffset(axis)
}

const buildSyntheticContextMenuEvent = (touchEvent, touch) => ({
  type: 'contextmenu',
  target: touchEvent?.target || null,
  currentTarget: touchEvent?.currentTarget || null,
  clientX: getTouchCoordinate(touch, 'clientX'),
  clientY: getTouchCoordinate(touch, 'clientY'),
  pageX: getPageCoordinate(touch, 'pageX', 'clientX', 'x'),
  pageY: getPageCoordinate(touch, 'pageY', 'clientY', 'y'),
  screenX: getTouchCoordinate(touch, 'screenX'),
  screenY: getTouchCoordinate(touch, 'screenY'),
  button: 2,
  buttons: 0,
  altKey: Boolean(touchEvent?.altKey),
  ctrlKey: Boolean(touchEvent?.ctrlKey),
  metaKey: Boolean(touchEvent?.metaKey),
  shiftKey: Boolean(touchEvent?.shiftKey),
  timeStamp: Date.now(),
  sourceEvent: touchEvent,
  preventDefault: () => {
    if (touchEvent?.cancelable !== false) {
      touchEvent?.preventDefault?.()
    }
  },
  stopPropagation: () => {
    touchEvent?.stopPropagation?.()
  }
})

export function useLongPressContextMenu(options = {}) {
  const open = options.open || (() => {})
  const disabled = options.disabled
  const ignoredSelector = options.ignoredSelector ?? DEFAULT_LONG_PRESS_CONTEXT_MENU_IGNORED_SELECTOR
  const duration = options.duration ?? LONG_PRESS_CONTEXT_MENU_DURATION_MS
  const movementThreshold = options.movementThreshold ?? LONG_PRESS_CONTEXT_MENU_MOVE_THRESHOLD_PX

  let pressTimerId = null
  let trackedTouchId = null
  let startX = 0
  let startY = 0
  let startEvent = null
  let currentTouch = null
  let pressTriggered = false
  let lastTouchContextMenuOpenAt = 0
  let suppressClickUntil = 0

  const isDisabled = () => Boolean(unref(disabled))

  const clearPressTimer = () => {
    if (pressTimerId === null) return

    getTimerHost().clearTimeout(pressTimerId)
    pressTimerId = null
  }

  const resetGesture = () => {
    clearPressTimer()
    trackedTouchId = null
    startX = 0
    startY = 0
    startEvent = null
    currentTouch = null
    pressTriggered = false
  }

  const openFromEvent = (event) => {
    open(event)
  }

  const triggerLongPress = () => {
    pressTimerId = null

    if (isDisabled() || trackedTouchId === null || !currentTouch) {
      resetGesture()
      return
    }

    pressTriggered = true
    lastTouchContextMenuOpenAt = Date.now()
    suppressClickUntil = lastTouchContextMenuOpenAt + CLICK_SUPPRESSION_MS
    openFromEvent(buildSyntheticContextMenuEvent(startEvent, currentTouch))
  }

  const handleTouchStart = (event) => {
    if (isDisabled() || event?.touches?.length !== 1 || hasIgnoredTarget(event?.target, ignoredSelector)) {
      resetGesture()
      return
    }

    const touch = getTouchPoint(event)
    if (!touch) {
      resetGesture()
      return
    }

    clearPressTimer()
    trackedTouchId = getTouchIdentifier(touch)
    startX = getTouchCoordinate(touch, 'clientX')
    startY = getTouchCoordinate(touch, 'clientY')
    startEvent = event
    currentTouch = touch
    pressTriggered = false

    pressTimerId = getTimerHost().setTimeout(triggerLongPress, duration)
  }

  const handleTouchMove = (event) => {
    if (trackedTouchId === null) return

    if (isDisabled() || event?.touches?.length !== 1) {
      clearPressTimer()
      return
    }

    const touch = findTouchById(event?.touches, trackedTouchId)
    if (!touch) {
      clearPressTimer()
      return
    }

    currentTouch = touch

    const deltaX = Math.abs(getTouchCoordinate(touch, 'clientX', startX) - startX)
    const deltaY = Math.abs(getTouchCoordinate(touch, 'clientY', startY) - startY)
    if (deltaX > movementThreshold || deltaY > movementThreshold) {
      clearPressTimer()
    }
  }

  const handleTouchEnd = (event) => {
    if (trackedTouchId === null) {
      resetGesture()
      return
    }

    const changedTouch = findTouchById(event?.changedTouches, trackedTouchId)
    if (changedTouch) {
      currentTouch = changedTouch
    }

    if (pressTriggered) {
      suppressClickUntil = Date.now() + CLICK_SUPPRESSION_MS
      preventAndStop(event)
    }

    resetGesture()
  }

  const handleContextMenu = (event) => {
    const now = Date.now()
    if (lastTouchContextMenuOpenAt && now - lastTouchContextMenuOpenAt < TOUCH_CONTEXT_MENU_DEDUP_MS) {
      preventAndStop(event)
      clearPressTimer()
      return
    }

    if (isDisabled()) return

    clearPressTimer()

    if (trackedTouchId !== null) {
      pressTriggered = true
      lastTouchContextMenuOpenAt = now
      suppressClickUntil = now + CLICK_SUPPRESSION_MS
    }

    preventAndStop(event)
    openFromEvent(event)
  }

  const shouldSuppressClick = (event) => {
    if (!suppressClickUntil || Date.now() > suppressClickUntil) return false

    suppressClickUntil = 0
    preventAndStop(event)
    return true
  }

  if (getCurrentInstance()) {
    onBeforeUnmount(resetGesture)
  }

  return {
    longPressBindings: {
      onTouchstart: handleTouchStart,
      onTouchmove: handleTouchMove,
      onTouchend: handleTouchEnd,
      onTouchcancel: handleTouchEnd
    },
    handleContextMenu,
    shouldSuppressClick,
    cancelLongPress: resetGesture
  }
}
