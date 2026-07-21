import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, unref } from 'vue'

export const DEFAULT_PULL_TO_REFRESH_IGNORED_SELECTOR = [
  'a',
  'button',
  'input',
  'textarea',
  'select',
  '[contenteditable="true"]',
  '[role="button"]',
  '.p-dialog',
  '.p-datepicker',
  '.p-datepicker-panel',
  '.p-dropdown-panel',
  '.p-multiselect-panel',
  '.p-select-overlay',
  '.p-paginator',
  '.p-checkbox',
  '.p-datatable-thead',
  '.p-datatable-header'
].join(',')

const getTouchPoint = (event) => event.touches?.[0] || event.changedTouches?.[0] || null

const defaultIsStandalonePwa = () => {
  if (typeof window === 'undefined') return false

  const standaloneMedia = typeof window.matchMedia === 'function'
    ? window.matchMedia('(display-mode: standalone)').matches
    : false

  return standaloneMedia || window.navigator?.standalone === true
}

const defaultIsTouchPrimary = () => {
  if (typeof window === 'undefined') return false

  const coarsePointer = typeof window.matchMedia === 'function'
    ? window.matchMedia('(pointer: coarse)').matches
    : false

  return coarsePointer || window.navigator?.maxTouchPoints > 0
}

const defaultGetScrollTop = () => {
  if (typeof window === 'undefined') return 0
  return window.scrollY || document.documentElement?.scrollTop || document.body?.scrollTop || 0
}

const hasIgnoredTarget = (target, ignoredSelector) => {
  if (!target || typeof target.closest !== 'function' || !ignoredSelector) return false
  return Boolean(target.closest(ignoredSelector))
}

export function usePullToRefresh(options = {}) {
  const threshold = options.threshold ?? 72
  const activationDistance = options.activationDistance ?? 8
  const maxPull = options.maxPull ?? 96
  const horizontalTolerance = options.horizontalTolerance ?? 36
  const ignoredSelector = options.ignoredSelector ?? DEFAULT_PULL_TO_REFRESH_IGNORED_SELECTOR
  const onRefresh = options.onRefresh || (async () => {})
  const isStandalonePwa = options.isStandalonePwa || defaultIsStandalonePwa
  const isTouchPrimary = options.isTouchPrimary || defaultIsTouchPrimary
  const getScrollTop = options.getScrollTop || defaultGetScrollTop
  const listenOnDocument = options.listenOnDocument ?? false

  const pullDistance = ref(0)
  const pullState = ref('idle')
  const isPullRefreshing = ref(false)
  const pullError = ref(null)

  let tracking = false
  let active = false
  let startY = 0
  let startX = 0

  const isPullEnabled = computed(() => {
    const configured = options.enabled === undefined ? true : unref(options.enabled)
    return Boolean(configured && isStandalonePwa() && isTouchPrimary())
  })

  const resetGesture = () => {
    tracking = false
    active = false
    startY = 0
    startX = 0
    pullDistance.value = 0
    if (!isPullRefreshing.value && pullState.value !== 'error') {
      pullState.value = 'idle'
    }
  }

  const cancelGesture = () => {
    pullState.value = 'idle'
    resetGesture()
  }

  const refresh = async () => {
    if (isPullRefreshing.value) return

    isPullRefreshing.value = true
    pullState.value = 'refreshing'
    pullError.value = null

    try {
      await onRefresh()
      pullState.value = 'idle'
    } catch (error) {
      pullError.value = error
      pullState.value = 'error'
    } finally {
      isPullRefreshing.value = false
      pullDistance.value = 0
    }
  }

  const handleTouchStart = (event) => {
    if (!isPullEnabled.value || isPullRefreshing.value) return
    if (event.touches?.length !== 1 || getScrollTop() > 0) return
    if (hasIgnoredTarget(event.target, ignoredSelector)) return

    const point = getTouchPoint(event)
    if (!point) return

    tracking = true
    active = false
    startY = point.clientY
    startX = point.clientX
    pullDistance.value = 0
    pullState.value = 'idle'
    pullError.value = null
  }

  const handleTouchMove = (event) => {
    if (!tracking || isPullRefreshing.value) return

    const point = getTouchPoint(event)
    if (!point) return

    const deltaY = point.clientY - startY
    const deltaX = Math.abs(point.clientX - startX)

    if (deltaY <= 0 || getScrollTop() > 0) {
      cancelGesture()
      return
    }

    if (deltaX > horizontalTolerance && deltaX > deltaY) {
      cancelGesture()
      return
    }

    if (deltaY < activationDistance) return

    active = true
    pullDistance.value = Math.min(deltaY, maxPull)
    pullState.value = pullDistance.value >= threshold ? 'ready' : 'pulling'

    if (event.cancelable) {
      event.preventDefault()
    }
  }

  const handleTouchEnd = () => {
    if (!tracking) return

    const shouldRefresh = active && pullDistance.value >= threshold
    tracking = false
    active = false
    startY = 0
    startX = 0

    if (shouldRefresh) {
      void refresh()
      return
    }

    pullDistance.value = 0
    pullState.value = 'idle'
  }

  const pullRefreshBindings = {
    onTouchstart: handleTouchStart,
    onTouchmove: handleTouchMove,
    onTouchend: handleTouchEnd,
    onTouchcancel: cancelGesture
  }

  const addDocumentListeners = () => {
    if (!listenOnDocument || typeof document === 'undefined') return

    document.addEventListener('touchstart', handleTouchStart, { passive: true })
    document.addEventListener('touchmove', handleTouchMove, { passive: false })
    document.addEventListener('touchend', handleTouchEnd, { passive: true })
    document.addEventListener('touchcancel', cancelGesture, { passive: true })
  }

  const removeDocumentListeners = () => {
    if (!listenOnDocument || typeof document === 'undefined') return

    document.removeEventListener('touchstart', handleTouchStart)
    document.removeEventListener('touchmove', handleTouchMove)
    document.removeEventListener('touchend', handleTouchEnd)
    document.removeEventListener('touchcancel', cancelGesture)
  }

  if (getCurrentInstance()) {
    onMounted(addDocumentListeners)
    onBeforeUnmount(removeDocumentListeners)
  }

  return {
    isPullEnabled,
    isPullRefreshing,
    pullDistance,
    pullError,
    pullState,
    pullRefreshBindings,
    refresh
  }
}
